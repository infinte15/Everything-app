package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.FinanceForecastDTO;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Kernzahl des Finance Space.
 *
 * <p>Gerechnet wird gegen eine von Hand nachvollziehbare Reihe, nicht gegen die Demo-Historie:
 * eine Prognose, die "irgendeine plausible Zahl" liefert, ist nicht pruefbar.
 */
@SpringBootTest
@Transactional
class FinanceForecastServiceTest {

    @Autowired FinanceForecastService forecastService;
    @Autowired BankConnectionRepository connectionRepository;
    @Autowired BankAccountRepository accountRepository;
    @Autowired ContractRepository contractRepository;
    @Autowired FinanceTransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;
    private LocalDate heute;

    @BeforeEach
    void setUp() {
        nutzer = anlegen("prognose-nutzer");
        heute = LocalDate.now();
    }

    @Test
    void ohneKontoGibtEsKeineZahl() {
        vertrag("Miete", 845.00, TransactionType.EXPENSE, heute.plusDays(3), 30);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        // Eine aus getippten Buchungen zusammengezählte Null wäre keine Prognose, sondern eine
        // Behauptung über Geld, das niemand gezählt hat.
        assertNull(prognose.getAvailable(), "ohne Bankanbindung darf keine Zahl entstehen");
        assertNull(prognose.getCurrentBalance());
        assertTrue(prognose.getSeries().isEmpty());
        // Die Vertragsseite steht trotzdem zur Verfügung.
        assertEquals(845.00, prognose.getUpcomingContractExpenses(), 0.01);
    }

    @Test
    void kernzahlRechnetKontostandMinusVertraegeMinusAlltag() {
        konto(2000.00);

        // Nur Verträge, die im Restmonat fällig sind. Datum bewusst dicht an heute, damit der Test
        // unabhängig vom Kalendertag läuft.
        LocalDate monatsende = heute.withDayOfMonth(heute.lengthOfMonth());
        if (heute.isEqual(monatsende)) {
            // Am letzten Tag des Monats gibt es keine Resttage - dann prüft dieser Test nichts.
            return;
        }
        vertrag("Netflix", 13.99, TransactionType.EXPENSE, heute.plusDays(1), 30);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        int resttage = prognose.getDaysRemaining();
        double erwartet = 2000.00 - 13.99 - prognose.getAverageDailyVariableExpenses() * resttage;

        assertEquals(erwartet, prognose.getAvailable(), 0.02);
        assertEquals(13.99, prognose.getUpcomingContractExpenses(), 0.01);
        assertFalse(prognose.isShortfall());
    }

    @Test
    void einnahmenVertraegeErhoehenDieKernzahl() {
        konto(500.00);
        LocalDate monatsende = heute.withDayOfMonth(heute.lengthOfMonth());
        if (heute.isEqual(monatsende)) {
            return;
        }
        vertrag("Gehalt", 2480.00, TransactionType.INCOME, heute.plusDays(1), 30);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        assertEquals(2480.00, prognose.getUpcomingContractIncome(), 0.01);
        assertTrue(prognose.getAvailable() > 500.00,
                "ohne Richtungsunterscheidung würde das Gehalt abgezogen");
    }

    @Test
    void unterdeckungWirdAlsSolcheGemeldet() {
        konto(50.00);
        LocalDate monatsende = heute.withDayOfMonth(heute.lengthOfMonth());
        if (heute.isEqual(monatsende)) {
            return;
        }
        vertrag("Miete", 845.00, TransactionType.EXPENSE, heute.plusDays(1), 30);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        assertTrue(prognose.getAvailable() < 0);
        assertTrue(prognose.isShortfall(), "der einzige Anlass für Rot in der Oberfläche");
    }

    @Test
    void vertragsbuchungenZaehlenNichtInDenTagesdurchschnitt() {
        konto(1000.00);

        // Vormonat: eine Vertragsbuchung und eine Alltagsausgabe.
        LocalDate vormonat = heute.withDayOfMonth(1).minusDays(15);
        Contract miete = vertrag("Miete", 845.00, TransactionType.EXPENSE, heute.plusDays(90), 30);
        buchung(845.00, "AUSGABE", vormonat, miete);
        buchung(30.00, "AUSGABE", vormonat, null);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        // Ohne die Trennung stünde die Miete zweimal in der Rechnung: einmal im Durchschnitt und
        // einmal als Vertrag.
        long tage = vormonat.withDayOfMonth(1).lengthOfMonth()
                + vormonat.withDayOfMonth(1).minusMonths(1).lengthOfMonth()
                + vormonat.withDayOfMonth(1).minusMonths(2).lengthOfMonth();
        assertEquals(30.00 / tage, prognose.getAverageDailyVariableExpenses(), 0.02);
    }

    @Test
    void dieKurveEndetHeuteAufDemEchtenKontostand() {
        konto(1234.56);
        buchung(100.00, "AUSGABE", heute.withDayOfMonth(1), null);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        FinanceForecastDTO.DayPoint heutePunkt = prognose.getSeries().stream()
                .filter(p -> p.getDate().isEqual(heute))
                .findFirst()
                .orElseThrow(() -> new AssertionError("heute fehlt in der Reihe"));

        assertEquals(1234.56, heutePunkt.getBalance(), 0.01,
                "der einzige bekannte Saldo ist der von heute - alles andere wird daraus abgeleitet");
        assertFalse(heutePunkt.isProjected());

        assertTrue(prognose.getSeries().stream()
                        .filter(p -> p.getDate().isAfter(heute))
                        .allMatch(FinanceForecastDTO.DayPoint::isProjected),
                "alles nach heute ist Projektion und wird gestrichelt gezeichnet");
    }

    @Test
    void ueberfaelligerVertragFaelltNichtWeg() {
        konto(1000.00);
        LocalDate monatsende = heute.withDayOfMonth(heute.lengthOfMonth());
        if (heute.isEqual(monatsende)) {
            return;
        }
        // Fällig war gestern, die Bank hat noch nicht gebucht.
        vertrag("Strom", 89.90, TransactionType.EXPENSE, heute.minusDays(1), 30);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        assertEquals(89.90, prognose.getUpcomingContractExpenses(), 0.01,
                "eine überfällige Zahlung steht noch aus, sie ist nicht erledigt");
        assertEquals(1, prognose.getUpcoming().size());
    }

    @Test
    void gekuendigteVertraegeWerdenNichtEingerechnet() {
        konto(1000.00);
        Contract gekuendigt = vertrag("Altes Abo", 50.00, TransactionType.EXPENSE, heute.plusDays(1), 30);
        gekuendigt.setActive(false);
        gekuendigt.setCancelledAt(LocalDateTime.now());
        contractRepository.saveAndFlush(gekuendigt);

        FinanceForecastDTO prognose = forecastService.forecast(nutzer.getId(), heute);

        assertEquals(0.0, prognose.getUpcomingContractExpenses(), 0.001);
    }

    // ==================== Hilfen ====================

    private User anlegen(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("egal");
        return userRepository.save(user);
    }

    private void konto(double saldo) {
        BankConnection verbindung = new BankConnection();
        verbindung.setUser(nutzer);
        verbindung.setAspspName("Testbank");
        verbindung.setStatus(BankConnectionStatus.ACTIVE);
        connectionRepository.saveAndFlush(verbindung);

        BankAccount konto = new BankAccount();
        konto.setUser(nutzer);
        konto.setConnection(verbindung);
        konto.setIdentificationHash("prognose-hash");
        konto.setDisplayName("Girokonto");
        konto.setCurrentBalance(saldo);
        konto.setBalanceUpdatedAt(LocalDateTime.now());
        konto.setSyncEnabled(true);
        accountRepository.saveAndFlush(konto);
    }

    private Contract vertrag(String name, double betrag, TransactionType richtung,
                             LocalDate faellig, int intervall) {
        Contract contract = new Contract();
        contract.setUser(nutzer);
        contract.setName(name);
        contract.setCounterpartyKey(name.toLowerCase());
        contract.setCategory("Sonstiges");
        contract.setDirection(richtung);
        contract.setAmount(betrag);
        contract.setFrequency(ContractFrequency.MONTHLY);
        contract.setIntervalDays(intervall);
        contract.setNextDueDate(faellig);
        contract.setActive(true);
        contract.setDetectedAutomatically(true);
        return contractRepository.saveAndFlush(contract);
    }

    private void buchung(double betrag, String typ, LocalDate datum, Contract vertrag) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(nutzer);
        transaction.setAmount(betrag);
        transaction.setType(typ);
        transaction.setCategory("Sonstiges");
        transaction.setDescription("Test");
        transaction.setTransactionDate(datum);
        transaction.setSource(TransactionSource.BANK);
        transaction.setContract(vertrag);
        transaction.setIsRecurring(vertrag != null);
        transactionRepository.saveAndFlush(transaction);
    }
}
