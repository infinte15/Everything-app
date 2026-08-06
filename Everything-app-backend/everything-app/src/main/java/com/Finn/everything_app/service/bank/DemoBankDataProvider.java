package com.Finn.everything_app.service.bank;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Erfundene, aber glaubwuerdige Kontodaten - fuer Entwicklung und Tests ohne Bankzugang.
 *
 * <p>Der Grund fuer diese Klasse ist nicht Bequemlichkeit: fuer Sparkassen gibt es keine Sandbox,
 * und der Produktivzugang setzt eine Registrierung samt Verlinkung eigener Konten voraus. Ohne
 * einen Ersatz waere die gesamte Kette aus Import, Kategorisierung, Vertragserkennung und Prognose
 * bis dahin nicht ausfuehrbar - und damit auch nicht pruefbar.
 *
 * <p>Die erzeugte Historie ist auf die Vertragserkennung hin gebaut: Gehalt, Miete, drei Abos und
 * eine halbjaehrliche Versicherung sind sauber wiederkehrend (mit etwas Datumsdrift, wie im echten
 * Leben), der Rest ist gestreute Alltagsausgabe. Alles haengt an einem festen Startwert, damit
 * zwei Laeufe dieselben Daten liefern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "enablebanking.provider", havingValue = "demo", matchIfMissing = true)
public class DemoBankDataProvider implements BankDataProvider {

    /** Fester Startwert: zwei Laeufe muessen dieselbe Historie ergeben, sonst wackeln die Tests. */
    private static final long SEED = 20260806L;

    private static final int HISTORY_MONTHS = 14;
    private static final String DEMO_ASPSP = "Demo-Bank (Testdaten)";
    private static final String DEMO_HASH = "demo-0000-1111-2222-3333";

    private final EnableBankingProperties properties;

    @PostConstruct
    void warnAboutDemoMode() {
        log.warn("Bankanbindung laeuft im DEMO-Modus - alle Kontodaten sind erfunden. "
                + "Fuer echte Daten enablebanking.provider=live setzen.");
    }

    @Override
    public String providerName() {
        return "demo";
    }

    @Override
    public List<AspspInfo> listAspsps(String country) {
        return List.of(new AspspInfo(
                DEMO_ASPSP, country == null ? "DE" : country,
                null, "Testdaten", false, true, 180));
    }

    @Override
    public AuthStart startAuth(AuthRequest request) {
        // Kein Bank-Login: die "Auth-URL" zeigt direkt auf den eigenen Callback. Damit durchlaeuft
        // die App genau denselben Ablauf wie im Echtbetrieb, nur ohne SCA.
        String url = properties.getRedirectUrl()
                + "?code=demo-" + UUID.randomUUID()
                + "&state=" + request.state();
        return new AuthStart(url, "demo-authorization");
    }

    @Override
    public SessionResult redeemSession(String code) {
        return new SessionResult(
                "demo-session-" + UUID.randomUUID(),
                LocalDateTime.now().plusDays(properties.getConsentDays()),
                List.of(new SessionResult.ProviderAccount(
                        "demo-uid-" + UUID.randomUUID(),
                        DEMO_HASH,
                        "DE02120300000000202051",
                        "Girokonto",
                        "EUR")));
    }

    @Override
    public List<BankBalance> fetchBalances(String accountUid, PsuContext psu) {
        // Der Saldo muss zur erzeugten Historie passen, sonst rechnet die Prognose gegen Zufall.
        double balance = round(generate(LocalDate.now().minusMonths(HISTORY_MONTHS)).stream()
                .mapToDouble(tx -> tx.income() ? tx.amount() : -tx.amount())
                .sum() + 1500.0);

        return List.of(
                new BankBalance("XPCD", "Vorgemerkter Saldo", balance, "EUR"),
                new BankBalance("CLBD", "Gebuchter Saldo", round(balance + 42.17), "EUR"));
    }

    @Override
    public List<BankTx> fetchTransactions(String accountUid, LocalDate from, boolean deepBackfill, PsuContext psu) {
        LocalDate start = deepBackfill ? LocalDate.now().minusMonths(HISTORY_MONTHS) : from;
        return generate(start);
    }

    // ==================== Erzeugung ====================

    /**
     * Erzeugt die Historie und schneidet sie danach auf das angefragte Fenster zu.
     *
     * <p><strong>Immer ab demselben Anker</strong>, nie ab {@code from}: die Zufallsfolge haengt
     * daran, wie viele Monate durchlaufen werden. Wer bei {@code from} beginnt, bekommt fuer
     * denselben Tag je nach Fenstergroesse andere Buchungen - und der zweite Sync legt dann
     * Duplikate an, obwohl die Deduplizierung fehlerfrei arbeitet. Eine echte Bank liefert fuer
     * einen Tag immer dieselben Umsaetze, unabhaengig vom abgefragten Zeitraum; ein Ersatz, der
     * das nicht tut, taugt weder zum Entwickeln noch zum Testen.
     */
    private List<BankTx> generate(LocalDate from) {
        LocalDate today = LocalDate.now();
        LocalDate anchor = today.minusMonths(HISTORY_MONTHS).withDayOfMonth(1);
        LocalDate start = from.isBefore(anchor) ? anchor : from;

        Random random = new Random(SEED);
        List<BankTx> result = new ArrayList<>();

        LocalDate month = anchor;
        while (!month.isAfter(today)) {
            addRecurring(result, month, today, random);
            addEverydaySpending(result, month, today, random);
            month = month.plusMonths(1);
        }

        result.removeIf(tx -> tx.bookingDate().isBefore(start) || tx.bookingDate().isAfter(today));
        result.sort((a, b) -> a.bookingDate().compareTo(b.bookingDate()));
        return result;
    }

    /** Die wiederkehrenden Posten - genau das, was die Vertragserkennung finden soll. */
    private void addRecurring(List<BankTx> out, LocalDate month, LocalDate today, Random random) {
        // Gehalt: letzter Werktag, mit kleiner Schwankung wie bei variablen Zulagen.
        LocalDate payday = drift(month.withDayOfMonth(month.lengthOfMonth()).minusDays(1), random, 2);
        add(out, payday, today, true, round(2480.0 + random.nextInt(80)),
                "Muster GmbH", "Gehalt " + month.getMonth() + " " + month.getYear());

        add(out, month.withDayOfMonth(1), today, false, 845.00,
                "Hausverwaltung Kestner", "Miete Wohnung Blumenstr. 14");

        add(out, drift(month.withDayOfMonth(3), random, 1), today, false, 89.90,
                "Stadtwerke Konstanz", "Abschlag Strom und Wasser");

        add(out, drift(month.withDayOfMonth(8), random, 1), today, false, 13.99,
                "Netflix International B.V.", "Netflix Abo");
        add(out, drift(month.withDayOfMonth(12), random, 1), today, false, 10.99,
                "Spotify AB", "Spotify Premium");
        add(out, drift(month.withDayOfMonth(2), random, 1), today, false, 29.90,
                "FitnessFirst Konstanz", "Mitgliedsbeitrag Studio");
        add(out, drift(month.withDayOfMonth(15), random, 1), today, false, 18.36,
                "Vodafone GmbH", "Mobilfunk Rechnung");

        // Halbjaehrlich - prueft, ob die Erkennung mehr als nur Monatsrhythmen findet.
        if (month.getMonthValue() == 1 || month.getMonthValue() == 7) {
            add(out, month.withDayOfMonth(20), today, false, 312.40,
                    "HUK-COBURG Versicherung", "Kfz-Versicherung Halbjahresbeitrag");
        }
    }

    /** Alltagsausgaben: unregelmaessig genug, dass daraus kein Vertrag werden darf. */
    private void addEverydaySpending(List<BankTx> out, LocalDate month, LocalDate today, Random random) {
        String[][] merchants = {
                {"REWE Markt GmbH", "REWE SAGT DANKE"},
                {"EDEKA Sued", "EDEKA Einkauf"},
                {"ALDI SUED", "ALDI SUED Filiale 4711"},
                {"LIDL Vertriebs-GmbH", "LIDL Einkauf"},
                {"Shell Deutschland", "Shell Tankstelle"},
                {"Deutsche Bahn AG", "DB Fernverkehr Ticket"},
                {"dm-drogerie markt", "dm Filiale"},
                {"Amazon EU S.a.r.l.", "Amazon Bestellung"},
                {"Ristorante Da Vinci", "Restaurantbesuch"},
                {"Baeckerei Dreher", "Baeckerei"},
        };

        int count = 14 + random.nextInt(10);
        for (int i = 0; i < count; i++) {
            String[] merchant = merchants[random.nextInt(merchants.length)];
            LocalDate date = month.withDayOfMonth(1 + random.nextInt(month.lengthOfMonth()));
            double amount = round(4.50 + random.nextDouble() * 75.0);
            add(out, date, today, false, amount, merchant[0], merchant[1]);
        }
    }

    private void add(List<BankTx> out, LocalDate date, LocalDate today,
                     boolean income, double amount, String counterparty, String description) {
        if (date.isAfter(today)) {
            return;
        }
        out.add(new BankTx(
                "demo-" + date + "-" + Math.abs((counterparty + amount).hashCode()),
                true, income, amount, "EUR", date, date, counterparty, description));
    }

    /** Verschiebt ein Datum um bis zu {@code maxDays} Tage - echte Lastschriften treffen nie exakt. */
    private LocalDate drift(LocalDate date, Random random, int maxDays) {
        int offset = random.nextInt(maxDays * 2 + 1) - maxDays;
        LocalDate shifted = date.plusDays(offset);
        return shifted.getMonth() == date.getMonth() ? shifted : date;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
