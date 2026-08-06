package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.BankConnectionException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.BankAccountRepository;
import com.Finn.everything_app.repository.BankConnectionRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.service.bank.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Holt Kontodaten von der Bank und macht daraus eigene Buchungen.
 *
 * <p>Die schwierige Stelle ist nicht der Abruf, sondern die <strong>Deduplizierung</strong>: derselbe
 * Umsatz kommt bei jedem Lauf erneut mit, weil das Abrufenster sich absichtlich ueberlappt. Der
 * Schluessel dafuer ist {@link FinanceTransaction#getExternalId()}, und er wird hier gebaut - nicht
 * von der Bank uebernommen. Drei Vorkehrungen greifen ineinander:
 *
 * <ol>
 *   <li><strong>Das Konto steckt im Schluessel.</strong> Die {@code entryReference} der Bank ist laut
 *       Spezifikation nur innerhalb eines Kontos eindeutig; zwei Konten desselben Instituts duerfen
 *       dieselbe tragen. Ohne das Praefix wuerde die zweite Buchung stillschweigend verschluckt.</li>
 *   <li><strong>Fehlt die Referenz, wird gerechnet.</strong> PSD2 schreibt sie nur als optional vor.
 *       Der Ersatz ist ein Hash aus Datum, Betrag, Gegenpartei und Verwendungszweck.</li>
 *   <li><strong>Nur gebuchte Umsaetze.</strong> Vorgemerkte tragen meist keine Referenz, aendern
 *       Betrag und Datum noch und kaemen beim Buchen ein zweites Mal an - als zweite Buchung.</li>
 * </ol>
 *
 * <p>Ein Fehler im Ablauf darf nie stumm bleiben: er landet als {@code lastSyncError} an der
 * Verbindung, damit die Oberflaeche erklaeren kann, warum keine neuen Buchungen ankommen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankSyncService {

    /** Fenster, in dem ein Callback zu seinem State passen muss. */
    private static final int STATE_VALIDITY_MINUTES = 10;

    /** Ohne bekannten letzten Lauf: so weit zurueck, wie die meisten Institute ohnehin hergeben. */
    private static final int DEFAULT_LOOKBACK_DAYS = 90;

    private final BankDataProvider provider;
    private final BankConnectionRepository connectionRepository;
    private final BankAccountRepository accountRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionCategorizer categorizer;
    private final ContractDetectionService contractDetection;
    private final EnableBankingProperties properties;

    /**
     * Ergebnis eines Laufs.
     *
     * @param warnings Dinge, die kein Abbruch sind, aber erklaert werden muessen - ein
     *                 uebersprungenes Fremdwaehrungskonto etwa sieht sonst wie Datenverlust aus.
     */
    public record SyncSummary(int accounts, int imported, int skipped, List<String> warnings) {

        static SyncSummary empty() {
            return new SyncSummary(0, 0, 0, List.of());
        }

        SyncSummary plus(SyncSummary other) {
            List<String> merged = new ArrayList<>(warnings);
            merged.addAll(other.warnings());
            return new SyncSummary(accounts + other.accounts(), imported + other.imported(),
                    skipped + other.skipped(), merged);
        }
    }

    // ==================== Zustimmung ====================

    /**
     * Legt eine Verbindung im Zustand {@link BankConnectionStatus#PENDING} an und liefert die URL,
     * auf die der Browser des Nutzers geschickt wird.
     *
     * <p>Der {@code authState} ist die einzige Verbindung zwischen dem spaeteren Callback und dem
     * Nutzer - der Redirect aus dem Browser bringt kein JWT mit.
     */
    @Transactional
    public String startAuthorization(Long userId, String aspspName, String aspspCountry) {
        String country = (aspspCountry == null || aspspCountry.isBlank()) ? "DE" : aspspCountry.toUpperCase();

        AspspInfo aspsp = provider.listAspsps(country).stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(aspspName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unbekanntes Institut: " + aspspName + " (" + country + ")"));

        if (!aspsp.redirectSupported()) {
            // Volksbanken und Raiffeisenbanken koennen nur DECOUPLED/EMBEDDED. Ein Redirect wuerde
            // in einem leeren Browserfenster enden - lieber vorher sagen, dass es nicht geht.
            throw new BadRequestException(aspsp.name()
                    + " unterstützt keine Anmeldung über den Browser und wird deshalb noch nicht unterstützt.");
        }

        BankConnection connection = new BankConnection();
        connection.setUser(userRepository.getReferenceById(userId));
        connection.setAspspName(aspsp.name());
        connection.setAspspCountry(country);
        connection.setStatus(BankConnectionStatus.PENDING);
        connection.setAuthState(UUID.randomUUID().toString());
        connectionRepository.save(connection);

        // Die Bank deckelt die Gueltigkeit ohnehin - hier schon ablesen statt annehmen.
        int days = Math.min(properties.getConsentDays(), aspsp.maxConsentDays());
        AuthStart start = provider.startAuth(new AuthRequest(
                aspsp.name(), country, connection.getAuthState(),
                properties.getRedirectUrl(), LocalDate.now().plusDays(days)));

        return start.authUrl();
    }

    /**
     * Loest den Code aus dem Callback ein und legt die Konten an.
     *
     * <p>Der anschliessende Erst-Import laeuft <em>nicht</em> hier, sondern als eigener Aufruf: die
     * volle Historie gibt es nur unmittelbar nach der Zustimmung, aber sie in dieselbe Transaktion
     * zu ziehen wuerde bedeuten, dass ein Fehler im Import auch die frisch angelegte Verbindung
     * wieder verwirft - und damit die einzige Gelegenheit auf diese Historie.
     *
     * @return die eingeloeste Verbindung
     */
    @Transactional
    public BankConnection completeAuthorization(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new BadRequestException("Der Rücksprung von der Bank war unvollständig.");
        }

        BankConnection connection = connectionRepository.findByAuthState(state)
                .orElseThrow(() -> new BadRequestException(
                        "Diese Anmeldung ist unbekannt oder wurde bereits abgeschlossen."));

        if (connection.getCreatedAt() == null
                || connection.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(STATE_VALIDITY_MINUTES))) {
            throw new BadRequestException("Die Anmeldung hat zu lange gedauert. Bitte erneut starten.");
        }

        SessionResult session = provider.redeemSession(code);

        connection.setSessionId(session.sessionId());
        connection.setValidUntil(session.validUntil());
        connection.setStatus(BankConnectionStatus.ACTIVE);
        connection.setLastSyncError(null);
        // Einmalig verwendbar: ein zweiter Aufruf mit demselben State findet nichts mehr.
        connection.setAuthState(null);
        connectionRepository.save(connection);

        Long userId = connection.getUser().getId();
        int stored = 0;
        for (SessionResult.ProviderAccount account : session.accounts()) {
            if (upsertAccount(userId, connection, account)) {
                stored++;
            }
        }

        if (stored == 0) {
            connection.setStatus(BankConnectionStatus.FAILED);
            connection.setLastSyncError("Keine Konten freigegeben");
            connectionRepository.save(connection);
            // Kein Fehler der Schnittstelle: im eingeschraenkten Produktionsbetrieb entfernt Enable
            // Banking nicht verlinkte Konten stillschweigend und antwortet trotzdem mit 200.
            throw new BankConnectionException(
                    "Die Bank hat kein Konto freigegeben. Im eingeschränkten Produktionsbetrieb müssen "
                            + "die eigenen Konten vorher im Enable-Banking-Control-Panel verknüpft werden.");
        }

        return connection;
    }

    /** @return {@code true}, wenn das Konto gespeichert wurde */
    private boolean upsertAccount(Long userId, BankConnection connection,
                                  SessionResult.ProviderAccount incoming) {
        if (incoming.identificationHash() == null || incoming.identificationHash().isBlank()) {
            // Ohne stabilen Schluessel gaebe es bei jeder Neu-Autorisierung ein zweites Konto samt
            // vollstaendiger Buchungshistorie. Lieber gar nicht anlegen.
            log.warn("Konto ohne identification_hash von {} - wird übersprungen", connection.getAspspName());
            return false;
        }

        BankAccount account = accountRepository
                .findByUserIdAndIdentificationHash(userId, incoming.identificationHash())
                .orElseGet(() -> {
                    BankAccount fresh = new BankAccount();
                    fresh.setUser(userRepository.getReferenceById(userId));
                    fresh.setIdentificationHash(incoming.identificationHash());
                    fresh.setSyncEnabled(true);
                    return fresh;
                });

        // Beides wandert bei jeder Zustimmung neu: die UID gilt nur fuer diese Sitzung, und nach
        // einer Neu-Autorisierung haengt dasselbe Konto an einer neuen Verbindung.
        account.setAccountUid(incoming.uid());
        account.setConnection(connection);
        account.setIban(trim(incoming.iban(), 34));
        account.setCurrency(incoming.currency() == null ? "EUR" : incoming.currency().toUpperCase());
        if (account.getDisplayName() == null || account.getDisplayName().isBlank()) {
            // Ein vom Nutzer vergebener Name ueberlebt die Neu-Autorisierung.
            account.setDisplayName(trim(displayNameOf(incoming), 200));
        }

        accountRepository.save(account);
        return true;
    }

    private String displayNameOf(SessionResult.ProviderAccount account) {
        if (account.displayName() != null && !account.displayName().isBlank()) {
            return account.displayName();
        }
        if (account.iban() != null && account.iban().length() > 4) {
            return "Konto ••" + account.iban().substring(account.iban().length() - 4);
        }
        return "Konto";
    }

    // ==================== Sync ====================

    /** Alle aktiven Verbindungen eines Nutzers - das ist der vom Nutzer ausgeloeste Abruf. */
    public SyncSummary syncUser(Long userId, PsuContext psu) {
        SyncSummary total = SyncSummary.empty();
        for (BankConnection connection : connectionRepository.findByUserIdAndStatus(
                userId, BankConnectionStatus.ACTIVE)) {
            total = total.plus(syncConnection(connection.getId(), psu, false));
        }
        return total;
    }

    /**
     * Ein Lauf ueber alle Konten einer Verbindung.
     *
     * <p>Transaktional trotz der HTTP-Aufrufe darin: die Alternative waere, den Fehlerzustand in
     * einer zweiten Transaktion zu schreiben, und genau dabei geht er verloren, wenn der Prozess
     * dazwischen endet. {@code open-in-view} haelt in dieser Anwendung ohnehin eine Verbindung ueber
     * den gesamten Request.
     *
     * @param deepBackfill nur beim Erst-Import nach der Zustimmung sinnvoll (siehe
     *                     {@link BankDataProvider#fetchTransactions})
     */
    @Transactional
    public SyncSummary syncConnection(Long connectionId, PsuContext psu, boolean deepBackfill) {
        BankConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BadRequestException("Unbekannte Bankverbindung: " + connectionId));

        Long userId = connection.getUser().getId();
        List<BankAccount> accounts = accountRepository.findByConnectionId(connectionId);
        SyncSummary summary = SyncSummary.empty();

        try {
            TransactionCategorizer.RuleSet ruleSet = categorizer.loadRules(userId);
            List<String> warnings = new ArrayList<>();

            for (BankAccount account : accounts) {
                if (!Boolean.TRUE.equals(account.getSyncEnabled())) {
                    continue;
                }
                if (account.getAccountUid() == null || account.getAccountUid().isBlank()) {
                    warnings.add(account.getDisplayName() + ": von der Bank nicht freigegeben");
                    continue;
                }
                if (!"EUR".equalsIgnoreCase(account.getCurrency())) {
                    // Die Prognose hat nirgends einen Umrechnungspfad; ein Fremdwaehrungskonto
                    // wuerde die Summen still verfaelschen.
                    warnings.add(account.getDisplayName() + ": Fremdwährung " + account.getCurrency()
                            + " wird nicht unterstützt");
                    continue;
                }
                summary = summary.plus(syncAccount(userId, account, connection, ruleSet, psu, deepBackfill));
            }

            summary = summary.plus(new SyncSummary(0, 0, 0, warnings));

            connection.setLastSyncAt(LocalDateTime.now());
            connection.setLastSyncError(null);
            connection.setStatus(BankConnectionStatus.ACTIVE);

        } catch (BankConnectionException e) {
            connection.setLastSyncError(trim(e.getMessage(), 500));
            if (e.isConsentGone()) {
                connection.setStatus(BankConnectionStatus.EXPIRED);
            } else if (e.isRateLimited()) {
                // Kein Defekt, sondern eine Vorgabe der Bank: die Verbindung bleibt aktiv, und die
                // Oberflaeche zeigt weiterhin den Stand des letzten erfolgreichen Laufs.
                log.info("Abruflimit für {} erreicht", connection.getAspspName());
            } else {
                connection.setStatus(BankConnectionStatus.FAILED);
            }
            log.warn("Sync für Verbindung {} fehlgeschlagen: {}", connectionId, e.getMessage());
        } catch (Exception e) {
            connection.setLastSyncError(trim("Unerwarteter Fehler: " + e.getMessage(), 500));
            connection.setStatus(BankConnectionStatus.FAILED);
            log.error("Sync für Verbindung {} abgebrochen", connectionId, e);
        }

        connectionRepository.save(connection);

        if (summary.imported() > 0) {
            // Erst wenn neue Buchungen da sind, kann sich am Vertragsbild etwas geaendert haben.
            contractDetection.detectForUser(userId);
        }
        return summary;
    }

    private SyncSummary syncAccount(Long userId, BankAccount account, BankConnection connection,
                                    TransactionCategorizer.RuleSet ruleSet, PsuContext psu,
                                    boolean deepBackfill) {
        LocalDate from = fetchStart(connection, deepBackfill);
        List<BankTx> fetched = provider.fetchTransactions(account.getAccountUid(), from, deepBackfill, psu);

        List<BankTx> booked = fetched.stream()
                .filter(BankTx::booked)
                .filter(tx -> dateOf(tx) != null)
                // Feste Reihenfolge, damit der Ordnungsindex im Hash-Ersatzschluessel zwischen zwei
                // Laeufen derselbe bleibt - die Reihenfolge der Bank ist nirgends zugesichert.
                .sorted(Comparator.comparing(this::dateOf)
                        .thenComparing(tx -> nullSafe(tx.counterparty()))
                        .thenComparingDouble(BankTx::amount)
                        .thenComparing(tx -> nullSafe(tx.description())))
                .toList();

        int skipped = fetched.size() - booked.size();
        if (booked.isEmpty()) {
            updateBalance(account, psu);
            return new SyncSummary(1, 0, skipped, List.of());
        }

        // Der tiefe Erst-Import liefert bewusst mehr, als angefragt wurde ("laengste Historie") -
        // der Bekannt-Abgleich muss deshalb bis zur aeltesten tatsaechlich gelieferten Buchung reichen.
        LocalDate earliest = booked.stream().map(this::dateOf).min(LocalDate::compareTo).orElse(from);
        Set<String> known = new HashSet<>(
                transactionRepository.findExternalIdsSince(userId, earliest.isBefore(from) ? earliest : from));

        Map<String, Integer> ordinals = new HashMap<>();
        List<FinanceTransaction> toSave = new ArrayList<>();

        for (BankTx tx : booked) {
            String externalId = externalId(account, tx, ordinals);
            // add() liefert false, wenn der Schluessel schon bekannt ist - das deckt sowohl bereits
            // importierte Buchungen ab als auch Dubletten innerhalb desselben Abrufs.
            if (!known.add(externalId)) {
                skipped++;
                continue;
            }
            toSave.add(toEntity(userId, account, tx, externalId, ruleSet));
        }

        if (!toSave.isEmpty()) {
            transactionRepository.saveAll(toSave);
        }
        updateBalance(account, psu);

        log.debug("Konto {}: {} neu, {} übersprungen", account.getId(), toSave.size(), skipped);
        return new SyncSummary(1, toSave.size(), skipped, List.of());
    }

    private LocalDate fetchStart(BankConnection connection, boolean deepBackfill) {
        LocalDate today = LocalDate.now();
        if (deepBackfill) {
            return today.minusDays(properties.getBackfillDays());
        }
        if (connection.getLastSyncAt() == null) {
            return today.minusDays(DEFAULT_LOOKBACK_DAYS);
        }
        // Ueberlappung: Banken buchen Umsaetze auch mehrere Tage rueckwirkend ein.
        return connection.getLastSyncAt().toLocalDate().minusDays(properties.getSyncOverlapDays());
    }

    private void updateBalance(BankAccount account, PsuContext psu) {
        List<BankBalance> balances = provider.fetchBalances(account.getAccountUid(), psu);
        balances.stream()
                .min(Comparator.comparingInt(BankBalance::preferenceRank))
                .ifPresent(balance -> {
                    account.setCurrentBalance(round(balance.amount()));
                    account.setBalanceUpdatedAt(LocalDateTime.now());
                    accountRepository.save(account);
                });
    }

    // ==================== Verwalten ====================

    public List<BankConnection> getConnections(Long userId) {
        return connectionRepository.findByUserId(userId);
    }

    public List<BankAccount> getAccounts(Long userId) {
        return accountRepository.findByUserId(userId);
    }

    /** Der Nutzer darf genau zwei Dinge am Konto aendern: ob es abgerufen wird und wie es heisst. */
    @Transactional
    public BankAccount updateAccount(Long userId, Long accountId, Boolean syncEnabled, String displayName) {
        BankAccount account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BadRequestException("Unbekanntes Konto: " + accountId));

        if (syncEnabled != null) {
            account.setSyncEnabled(syncEnabled);
        }
        if (displayName != null && !displayName.isBlank()) {
            account.setDisplayName(trim(displayName, 200));
        }
        return accountRepository.save(account);
    }

    /**
     * Trennt eine Verbindung samt ihrer Konten.
     *
     * <p>Die importierten Buchungen bleiben: sie sind die Historie des Nutzers, nicht Eigentum der
     * Bank. Sie verlieren nur ihren Kontobezug - ohne dieses Loesen scheitert das Loeschen an der
     * Fremdschluesselbedingung, denn {@code FinanceTransaction.bankAccount} traegt bewusst kein
     * Cascade.
     *
     * <p>Die {@code externalId} bleibt ebenfalls stehen. Verbindet der Nutzer dasselbe Konto erneut,
     * traegt es denselben {@code identificationHash} und damit denselben Schluesselpraefix - die
     * alten Buchungen werden also nicht ein zweites Mal importiert.
     */
    @Transactional
    public void disconnect(Long userId, Long connectionId) {
        BankConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new BadRequestException("Unbekannte Bankverbindung: " + connectionId));

        List<BankAccount> accounts = accountRepository.findByConnectionId(connectionId);
        for (BankAccount account : accounts) {
            List<FinanceTransaction> linked = transactionRepository.findByBankAccountId(account.getId());
            linked.forEach(tx -> tx.setBankAccount(null));
            transactionRepository.saveAll(linked);
        }
        accountRepository.deleteAll(accounts);
        connectionRepository.delete(connection);
    }

    // ==================== Abbildung ====================

    private FinanceTransaction toEntity(Long userId, BankAccount account, BankTx tx,
                                        String externalId, TransactionCategorizer.RuleSet ruleSet) {
        FinanceTransaction entity = new FinanceTransaction();
        entity.setUser(userRepository.getReferenceById(userId));
        entity.setBankAccount(account);
        entity.setExternalId(externalId);
        entity.setSource(TransactionSource.BANK);
        entity.setCategoryLocked(false);
        entity.setAmount(round(tx.amount()));
        entity.setType(tx.income() ? TransactionType.INCOME.toLegacy() : TransactionType.EXPENSE.toLegacy());
        entity.setTransactionDate(dateOf(tx));
        entity.setValueDate(tx.valueDate());
        entity.setCounterparty(trim(tx.counterparty(), 300));
        entity.setDescription(trim(descriptionOf(tx), 500));
        entity.setPaymentMethod("Bank");
        // Vorbelegung, falls keine Regel greift - die Spalte ist NOT NULL.
        entity.setCategory(TransactionCategorizer.FALLBACK_CATEGORY);
        categorizer.categorize(entity, ruleSet);
        return entity;
    }

    /** Ein leerer Verwendungszweck ist ueblich (Kartenzahlungen); die Spalte ist aber NOT NULL. */
    private String descriptionOf(BankTx tx) {
        if (tx.description() != null && !tx.description().isBlank()) {
            return tx.description();
        }
        if (tx.counterparty() != null && !tx.counterparty().isBlank()) {
            return tx.counterparty();
        }
        return "Buchung";
    }

    /** Buchungsdatum ist massgeblich; manche Institute liefern nur die Wertstellung. */
    private LocalDate dateOf(BankTx tx) {
        return tx.bookingDate() != null ? tx.bookingDate() : tx.valueDate();
    }

    /**
     * Baut den Dedup-Schluessel.
     *
     * <p>Der Praefix aus dem {@code identificationHash} des Kontos ist der Kern der Sache: ohne ihn
     * wuerde dieselbe {@code entryReference} unter zwei Konten desselben Instituts kollidieren und
     * die zweite Buchung verschwinden.
     *
     * <p>Ohne Referenz wird gerechnet. Der Ordnungsindex trennt echte Doppelbuchungen (zweimal
     * derselbe Kaffee am selben Tag) voneinander. Er ist die schwaechste Stelle des Verfahrens:
     * liefert die Bank an einem Tag beim naechsten Lauf eine Buchung mehr, verschieben sich die
     * Indizes. Deshalb greift er nur dort, wo die Bank ueberhaupt keine Referenz mitschickt.
     */
    private String externalId(BankAccount account, BankTx tx, Map<String, Integer> ordinals) {
        String prefix = account.getIdentificationHash().length() > 8
                ? account.getIdentificationHash().substring(0, 8)
                : account.getIdentificationHash();

        if (tx.entryReference() != null && !tx.entryReference().isBlank()) {
            return trim(prefix + ":" + tx.entryReference().trim(), 200);
        }

        String fingerprint = String.join("|",
                String.valueOf(dateOf(tx)),
                String.format(Locale.ROOT, "%.2f", tx.amount()),
                nullSafe(tx.currency()),
                nullSafe(tx.counterparty()),
                nullSafe(tx.description()));
        int ordinal = ordinals.merge(fingerprint, 1, Integer::sum) - 1;

        return prefix + ":h:" + sha256(fingerprint + "|" + ordinal);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 ist in jeder JRE vorhanden; der Fall existiert nur wegen der checked Exception.
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }

    // ==================== Hilfen ====================

    /** Alle Institute eines Landes, aufsteigend nach Name - die Liste hat mehrere hundert Eintraege. */
    public List<AspspInfo> listAspsps(String country) {
        return provider.listAspsps(country == null || country.isBlank() ? "DE" : country.toUpperCase())
                .stream()
                .sorted(Comparator.comparing(AspspInfo::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /** Kennzeichnung fuer die Oberflaeche: im Demo-Betrieb sind alle Zahlen erfunden. */
    public boolean isDemoProvider() {
        return "demo".equals(provider.providerName());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
