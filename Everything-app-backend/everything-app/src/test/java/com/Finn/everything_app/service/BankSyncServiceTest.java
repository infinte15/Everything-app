package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.service.bank.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Der Sync gegen den Demo-Provider - die einzige Moeglichkeit, die Kette ohne Bankzugang zu pruefen.
 *
 * <p>Der Schwerpunkt liegt auf der Deduplizierung. Ein Fehler darin faellt im Betrieb erst nach
 * Wochen auf, und dann steht die halbe Historie doppelt in der Datenbank: der zweite Lauf muss
 * nachweislich nichts anlegen.
 */
@SpringBootTest
@Transactional
class BankSyncServiceTest {

    @Autowired BankSyncService bankSyncService;
    @Autowired BankDataProvider provider;
    @Autowired BankConnectionRepository connectionRepository;
    @Autowired BankAccountRepository accountRepository;
    @Autowired FinanceTransactionRepository transactionRepository;
    @Autowired ContractRepository contractRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;

    @BeforeEach
    void setUp() {
        nutzer = anlegen("sync-nutzer");
    }

    @Test
    void derDemoProviderIstImTestAktiv() {
        // Liefe hier der Live-Client, würden die folgenden Tests echte HTTP-Aufrufe absetzen.
        assertEquals("demo", provider.providerName());
    }

    @Test
    void zustimmungLegtVerbindungUndKontoAn() {
        BankConnection verbindung = verbinden();

        assertEquals(BankConnectionStatus.ACTIVE, verbindung.getStatus());
        assertNull(verbindung.getAuthState(), "der State ist einmalig verwendbar");
        assertNotNull(verbindung.getSessionId());

        List<BankAccount> konten = accountRepository.findByConnectionId(verbindung.getId());
        assertEquals(1, konten.size());
        assertNotNull(konten.get(0).getIdentificationHash(), "ohne stabilen Schlüssel keine Identität");
    }

    @Test
    void erstImportHoltHistorieUndSetztDenSaldo() {
        BankConnection verbindung = verbinden();

        BankSyncService.SyncSummary ergebnis =
                bankSyncService.syncConnection(verbindung.getId(), null, true);

        assertTrue(ergebnis.imported() > 200,
                "14 Monate Historie erwartet, waren: " + ergebnis.imported());

        BankAccount konto = accountRepository.findByConnectionId(verbindung.getId()).get(0);
        assertNotNull(konto.getCurrentBalance());
        assertNotNull(konto.getBalanceUpdatedAt());
    }

    @Test
    void zweiterSyncLegtKeineDuplikateAn() {
        BankConnection verbindung = verbinden();
        bankSyncService.syncConnection(verbindung.getId(), null, true);
        long nachErstImport = transactionRepository.findByUserId(nutzer.getId()).size();

        BankSyncService.SyncSummary zweiter =
                bankSyncService.syncConnection(verbindung.getId(), null, true);

        assertEquals(0, zweiter.imported(), "der zweite Lauf darf nichts Neues anlegen");
        assertTrue(zweiter.skipped() > 0, "und muss die bekannten Buchungen als übersprungen zählen");
        assertEquals(nachErstImport, transactionRepository.findByUserId(nutzer.getId()).size());
    }

    @Test
    void importierteBuchungenTragenHerkunftUndGegenpartei() {
        BankConnection verbindung = verbinden();
        bankSyncService.syncConnection(verbindung.getId(), null, true);

        List<FinanceTransaction> buchungen = transactionRepository.findByUserId(nutzer.getId());
        assertTrue(buchungen.stream().allMatch(t -> t.getSource() == TransactionSource.BANK));
        assertTrue(buchungen.stream().allMatch(t -> t.getExternalId() != null));
        assertTrue(buchungen.stream().allMatch(t -> t.getCounterparty() != null));
        assertTrue(buchungen.stream().allMatch(t -> t.getBankAccount() != null));
        assertTrue(buchungen.stream().noneMatch(t -> Boolean.TRUE.equals(t.getCategoryLocked())),
                "die Automatik darf ihre eigenen Buchungen weiter anfassen");
    }

    @Test
    void derExternalIdEnthaeltDasKonto() {
        // Die entry_reference der Bank ist nur innerhalb eines Kontos eindeutig. Ohne das Präfix
        // würde dieselbe Referenz unter zwei Konten kollidieren und die zweite Buchung verschwinden.
        BankConnection verbindung = verbinden();
        bankSyncService.syncConnection(verbindung.getId(), null, true);

        BankAccount konto = accountRepository.findByConnectionId(verbindung.getId()).get(0);
        String praefix = konto.getIdentificationHash().substring(0, 8);

        assertTrue(transactionRepository.findByUserId(nutzer.getId()).stream()
                        .allMatch(t -> t.getExternalId().startsWith(praefix + ":")),
                "jede importierte Buchung trägt das Konto im Schlüssel");
    }

    @Test
    void syncErkenntVertraege() {
        BankConnection verbindung = verbinden();
        bankSyncService.syncConnection(verbindung.getId(), null, true);

        List<Contract> vertraege = contractRepository.findByUserIdOrderByNameAsc(nutzer.getId());

        assertTrue(vertraege.size() >= 4,
                "Gehalt, Miete und die Abos erwartet, waren: " + namen(vertraege));
        assertTrue(vertraege.stream().anyMatch(c -> c.getDirection() == TransactionType.INCOME),
                "das Gehalt muss als Einnahme erkannt werden: " + namen(vertraege));
        assertTrue(vertraege.stream().anyMatch(c -> c.getFrequency() == ContractFrequency.SEMIANNUAL),
                "die halbjährliche Versicherung fehlt: " + namen(vertraege));
    }

    @Test
    void abgeschaltetesKontoWirdNichtAbgerufen() {
        BankConnection verbindung = verbinden();
        BankAccount konto = accountRepository.findByConnectionId(verbindung.getId()).get(0);
        bankSyncService.updateAccount(nutzer.getId(), konto.getId(), false, null);

        BankSyncService.SyncSummary ergebnis =
                bankSyncService.syncConnection(verbindung.getId(), null, true);

        assertEquals(0, ergebnis.accounts());
        assertEquals(0, ergebnis.imported());
    }

    @Test
    void fremdwaehrungskontoWirdMitHinweisUebersprungen() {
        BankConnection verbindung = verbinden();
        BankAccount konto = accountRepository.findByConnectionId(verbindung.getId()).get(0);
        konto.setCurrency("CHF");
        accountRepository.saveAndFlush(konto);

        BankSyncService.SyncSummary ergebnis =
                bankSyncService.syncConnection(verbindung.getId(), null, true);

        assertEquals(0, ergebnis.imported());
        // Ohne Hinweis sähe das für den Nutzer aus wie ein Defekt - die Prognose hat aber
        // nirgends einen Umrechnungspfad.
        assertTrue(ergebnis.warnings().stream().anyMatch(w -> w.contains("CHF")),
                "es fehlt der Hinweis: " + ergebnis.warnings());
    }

    @Test
    void unbekannterStateWirdSauberAbgelehnt() {
        assertThrows(com.Finn.everything_app.exception.BadRequestException.class,
                () -> bankSyncService.completeAuthorization("irgendein-code", "kein-echter-state"));
    }

    @Test
    void abgelaufenerStateWirdAbgelehnt() {
        bankSyncService.startAuthorization(nutzer.getId(), "Demo-Bank (Testdaten)", "DE");
        BankConnection verbindung = connectionRepository.findByUserId(nutzer.getId()).get(0);

        // Der State ist nur zehn Minuten gültig - danach ist der Anmeldevorgang tot.
        verbindung.setCreatedAt(LocalDateTime.now().minusHours(2));
        connectionRepository.saveAndFlush(verbindung);

        assertThrows(com.Finn.everything_app.exception.BadRequestException.class,
                () -> bankSyncService.completeAuthorization("code", verbindung.getAuthState()));
    }

    @Test
    void trennenBehaeltDieBuchungenUndLoestNurDenKontobezug() {
        BankConnection verbindung = verbinden();
        bankSyncService.syncConnection(verbindung.getId(), null, true);
        int vorher = transactionRepository.findByUserId(nutzer.getId()).size();

        bankSyncService.disconnect(nutzer.getId(), verbindung.getId());

        List<FinanceTransaction> nachher = transactionRepository.findByUserId(nutzer.getId());
        assertEquals(vorher, nachher.size(), "die Historie gehört dem Nutzer, nicht der Bank");
        assertTrue(nachher.stream().allMatch(t -> t.getBankAccount() == null));
        assertTrue(accountRepository.findByUserId(nutzer.getId()).isEmpty());
        assertTrue(connectionRepository.findByUserId(nutzer.getId()).isEmpty());
    }

    @Test
    void neuverbindenNachTrennenImportiertNichtDoppelt() {
        BankConnection erste = verbinden();
        bankSyncService.syncConnection(erste.getId(), null, true);
        int vorher = transactionRepository.findByUserId(nutzer.getId()).size();

        bankSyncService.disconnect(nutzer.getId(), erste.getId());

        BankConnection zweite = verbinden();
        bankSyncService.syncConnection(zweite.getId(), null, true);

        // Der identification_hash ist derselbe, also auch der Schlüsselpräfix - die alten
        // Buchungen sind weiterhin als bekannt erkennbar.
        assertEquals(vorher, transactionRepository.findByUserId(nutzer.getId()).size(),
                "nach einer Neu-Autorisierung darf die Historie nicht ein zweites Mal ankommen");
    }

    @Test
    void manuelleBuchungenBleibenVomSyncUnberuehrt() {
        FinanceTransaction getippt = new FinanceTransaction();
        getippt.setUser(nutzer);
        getippt.setAmount(9.99);
        getippt.setType("AUSGABE");
        getippt.setCategory("Sonstiges");
        getippt.setDescription("Von Hand");
        getippt.setTransactionDate(LocalDate.now());
        getippt.setSource(TransactionSource.MANUAL);
        transactionRepository.saveAndFlush(getippt);

        BankConnection verbindung = verbinden();
        bankSyncService.syncConnection(verbindung.getId(), null, true);

        FinanceTransaction nachher = transactionRepository.findById(getippt.getId()).orElseThrow();
        assertEquals(TransactionSource.MANUAL, nachher.getSource());
        assertNull(nachher.getExternalId());
        assertNull(nachher.getBankAccount());
    }

    // ==================== Hilfen ====================

    private BankConnection verbinden() {
        bankSyncService.startAuthorization(nutzer.getId(), "Demo-Bank (Testdaten)", "DE");

        BankConnection pending = connectionRepository.findByUserId(nutzer.getId()).stream()
                .filter(c -> c.getAuthState() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine offene Anmeldung angelegt"));

        return bankSyncService.completeAuthorization("demo-code", pending.getAuthState());
    }

    private String namen(List<Contract> vertraege) {
        return vertraege.stream().map(c -> c.getName() + "/" + c.getFrequency()).toList().toString();
    }

    private User anlegen(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("egal");
        return userRepository.save(user);
    }
}
