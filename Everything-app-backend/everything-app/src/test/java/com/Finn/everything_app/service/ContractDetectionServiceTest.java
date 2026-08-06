package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.ContractRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Erkennung ist bewusst konservativ: ein uebersehenes Abo ist ein Schoenheitsfehler, ein
 * erfundener Vertrag verfaelscht die Prognose. Die Tests halten beide Richtungen fest - was
 * erkannt werden <em>muss</em> und was nicht erkannt werden <em>darf</em>.
 */
@SpringBootTest
@Transactional
class ContractDetectionServiceTest {

    @Autowired ContractDetectionService detection;
    @Autowired ContractRepository contractRepository;
    @Autowired FinanceTransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;
    private LocalDate heute;

    @BeforeEach
    void setUp() {
        nutzer = anlegen("vertrag-nutzer");
        heute = LocalDate.now();
    }

    @Test
    void sauberMonatlichesAboWirdErkannt() {
        for (int i = 5; i >= 0; i--) {
            buchung("Netflix International B.V.", 13.99, heute.minusMonths(i).withDayOfMonth(8));
        }

        detection.detectForUser(nutzer.getId());

        Contract vertrag = einziger();
        assertEquals(ContractFrequency.MONTHLY, vertrag.getFrequency());
        assertEquals(13.99, vertrag.getAmount(), 0.001);
        assertEquals(TransactionType.EXPENSE, vertrag.getDirection());
        assertEquals(6, vertrag.getOccurrenceCount());
        assertTrue(vertrag.getDetectedAutomatically());
        assertEquals(vertrag.getLastBookingDate().plusDays(vertrag.getIntervalDays()),
                vertrag.getNextDueDate());
    }

    @Test
    void datumsdriftVonWenigenTagenStoertNicht() {
        // Echte Lastschriften treffen nie exakt denselben Tag - Wochenenden und Feiertage schieben.
        int[] tage = {8, 10, 7, 9, 8, 11};
        for (int i = 0; i < tage.length; i++) {
            buchung("Spotify AB", 10.99, heute.minusMonths(5L - i).withDayOfMonth(tage[i]));
        }

        detection.detectForUser(nutzer.getId());

        assertEquals(ContractFrequency.MONTHLY, einziger().getFrequency());
    }

    @Test
    void schwankenderBetragInnerhalbDerToleranzWirdZusammengefasst() {
        double[] betraege = {2480.0, 2510.0, 2495.0, 2530.0};
        for (int i = 0; i < betraege.length; i++) {
            einnahme("Muster GmbH", betraege[i], heute.minusMonths(3L - i).withDayOfMonth(28));
        }

        detection.detectForUser(nutzer.getId());

        Contract gehalt = einziger();
        assertEquals(TransactionType.INCOME, gehalt.getDirection(), "das Gehalt ist eine Einnahme");
        assertEquals(2502.5, gehalt.getAmount(), 0.01, "der Median, nicht die letzte Buchung");
    }

    @Test
    void halbjaehrlicheVersicherungWirdErkannt() {
        buchung("HUK-COBURG Versicherung", 312.40, heute.minusDays(365));
        buchung("HUK-COBURG Versicherung", 312.40, heute.minusDays(182));
        buchung("HUK-COBURG Versicherung", 312.40, heute.minusDays(1));

        detection.detectForUser(nutzer.getId());

        assertEquals(ContractFrequency.SEMIANNUAL, einziger().getFrequency(),
                "die Erkennung darf nicht auf Monatsrhythmen beschränkt sein");
    }

    @Test
    void zweiBuchungenErgebenKeinenVertrag() {
        buchung("Netflix International B.V.", 13.99, heute.minusMonths(1));
        buchung("Netflix International B.V.", 13.99, heute);

        detection.detectForUser(nutzer.getId());

        // Aus einem einzigen Abstand laesst sich kein Rhythmus ablesen.
        assertTrue(contractRepository.findByUserIdOrderByNameAsc(nutzer.getId()).isEmpty());
    }

    @Test
    void zufaelligeSupermarktbesucheErgebenKeinenVertrag() {
        int[] abstaende = {0, 3, 11, 12, 27, 29, 44};
        for (int abstand : abstaende) {
            buchung("REWE SAGT DANKE", 42.00, heute.minusDays(60L - abstand));
        }

        detection.detectForUser(nutzer.getId());

        assertTrue(contractRepository.findByUserIdOrderByNameAsc(nutzer.getId()).isEmpty(),
                "unregelmäßige Abstände dürfen keinen Vertrag ergeben");
    }

    @Test
    void einAusreisserImRhythmusKipptDieReihe() {
        // Fuenf saubere Monatsabstaende und ein Loch: der Median sagt weiterhin "monatlich",
        // aber die Reihe ist keine. Ohne die Ausreisserpruefung entstuende hier ein Vertrag.
        buchung("Zweifelhaft Dienst", 20.00, heute.minusDays(210));
        buchung("Zweifelhaft Dienst", 20.00, heute.minusDays(120));
        buchung("Zweifelhaft Dienst", 20.00, heute.minusDays(90));
        buchung("Zweifelhaft Dienst", 20.00, heute.minusDays(60));
        buchung("Zweifelhaft Dienst", 20.00, heute.minusDays(30));

        detection.detectForUser(nutzer.getId());

        assertTrue(contractRepository.findByUserIdOrderByNameAsc(nutzer.getId()).isEmpty());
    }

    @Test
    void verschiedeneBetragsniveauDerselbenGegenparteiErgebenZweiVertraege() {
        for (int i = 3; i >= 0; i--) {
            buchung("Amazon Prime", 8.99, heute.minusMonths(i).withDayOfMonth(5));
            buchung("Amazon Prime", 10.99, heute.minusMonths(i).withDayOfMonth(20));
        }

        detection.detectForUser(nutzer.getId());

        List<Contract> vertraege = contractRepository.findByUserIdOrderByNameAsc(nutzer.getId());
        assertEquals(2, vertraege.size(),
                "ein einziger Vertrag über 9,99 wäre eine Erfindung");
        assertTrue(vertraege.stream().anyMatch(c -> Math.abs(c.getAmount() - 8.99) < 0.01));
        assertTrue(vertraege.stream().anyMatch(c -> Math.abs(c.getAmount() - 10.99) < 0.01));
    }

    @Test
    void abgebrocheneReiheGiltAlsGekuendigt() {
        // Die Buchungshistorie eines gekündigten Abos bleibt bestehen, die Reihe ist also weiterhin
        // erkennbar. Entscheidend ist allein, dass die letzte Buchung deutlich überfällig ist.
        for (int i = 8; i >= 4; i--) {
            buchung("FitnessFirst Konstanz", 29.90, heute.minusMonths(i).withDayOfMonth(2));
        }

        detection.detectForUser(nutzer.getId());

        Contract vertrag = einziger();
        assertFalse(vertrag.getActive(), "seit über vier Monaten keine Buchung mehr");
        assertNotNull(vertrag.getCancelledAt());
        assertNull(vertrag.getNextDueDate(), "ein gekündigter Vertrag hat keine nächste Fälligkeit");
    }

    @Test
    void laufendeReiheBleibtUeberMehrereLaeufeAktiv() {
        for (int i = 5; i >= 0; i--) {
            buchung("Netflix International B.V.", 13.99, heute.minusMonths(i).withDayOfMonth(8));
        }

        detection.detectForUser(nutzer.getId());
        detection.detectForUser(nutzer.getId());

        Contract vertrag = einziger();
        assertTrue(vertrag.getActive(), "ein zweiter Lauf ohne neue Daten darf nichts kippen");
        assertNull(vertrag.getCancelledAt());
        assertEquals(1, contractRepository.findByUserIdOrderByNameAsc(nutzer.getId()).size(),
                "und er darf kein zweites Mal angelegt werden");
    }

    @Test
    void handgepflegterVertragWirdNichtUeberschrieben() {
        for (int i = 5; i >= 0; i--) {
            buchung("Netflix International B.V.", 13.99, heute.minusMonths(i).withDayOfMonth(8));
        }

        Contract handarbeit = new Contract();
        handarbeit.setUser(nutzer);
        handarbeit.setName("Netflix (mein Name)");
        handarbeit.setCounterpartyKey(CounterpartyNormalizer.normalize("Netflix International B.V."));
        handarbeit.setCategory("Unterhaltung");
        handarbeit.setDirection(TransactionType.EXPENSE);
        handarbeit.setAmount(13.99);
        handarbeit.setFrequency(ContractFrequency.MONTHLY);
        handarbeit.setIntervalDays(30);
        handarbeit.setDetectedAutomatically(false);
        handarbeit.setActive(true);
        contractRepository.saveAndFlush(handarbeit);

        detection.detectForUser(nutzer.getId());

        Contract nachher = contractRepository.findById(handarbeit.getId()).orElseThrow();
        assertEquals("Netflix (mein Name)", nachher.getName(),
                "eine Korrektur von Hand ist eine Entscheidung, keine Zwischenablage");
        assertEquals(1, contractRepository.findByUserIdOrderByNameAsc(nutzer.getId()).size(),
                "und es entsteht auch kein zweiter Vertrag daneben");
    }

    @Test
    void erkannteBuchungenWerdenDemVertragZugeordnet() {
        for (int i = 5; i >= 0; i--) {
            buchung("Netflix International B.V.", 13.99, heute.minusMonths(i).withDayOfMonth(8));
        }

        detection.detectForUser(nutzer.getId());

        Contract vertrag = einziger();
        List<FinanceTransaction> zugeordnet = transactionRepository.findByContractId(vertrag.getId());
        assertEquals(6, zugeordnet.size());
        assertTrue(zugeordnet.stream().allMatch(t -> Boolean.TRUE.equals(t.getIsRecurring())));
    }

    @Test
    void einnahmeUndAusgabeDerselbenGegenparteiWerdenNichtVermischt() {
        // Gehalt und Rücklastschrift sind nicht derselbe Vertrag.
        for (int i = 3; i >= 0; i--) {
            einnahme("Muster GmbH", 100.00, heute.minusMonths(i).withDayOfMonth(10));
            buchung("Muster GmbH", 100.00, heute.minusMonths(i).withDayOfMonth(11));
        }

        detection.detectForUser(nutzer.getId());

        // Beide Reihen liegen auf demselben Betrag und werden deshalb in einen Topf gruppiert -
        // die Richtungsprüfung verwirft ihn.
        assertTrue(contractRepository.findByUserIdOrderByNameAsc(nutzer.getId()).isEmpty());
    }

    // ==================== Hilfen ====================

    private Contract einziger() {
        List<Contract> vertraege = contractRepository.findByUserIdOrderByNameAsc(nutzer.getId());
        assertEquals(1, vertraege.size(), "genau ein Vertrag erwartet, war: " + vertraege.size());
        return vertraege.get(0);
    }

    private User anlegen(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("egal");
        return userRepository.save(user);
    }

    private void buchung(String gegenpartei, double betrag, LocalDate datum) {
        speichern(gegenpartei, betrag, datum, "AUSGABE");
    }

    private void einnahme(String gegenpartei, double betrag, LocalDate datum) {
        speichern(gegenpartei, betrag, datum, "EINNAHME");
    }

    private void speichern(String gegenpartei, double betrag, LocalDate datum, String typ) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(nutzer);
        transaction.setAmount(betrag);
        transaction.setType(typ);
        transaction.setCategory("Sonstiges");
        transaction.setDescription(gegenpartei);
        transaction.setCounterparty(gegenpartei);
        transaction.setTransactionDate(datum);
        transaction.setSource(TransactionSource.BANK);
        transaction.setExternalId(Optional.of(gegenpartei + betrag + datum + typ).get());
        transactionRepository.saveAndFlush(transaction);
    }
}
