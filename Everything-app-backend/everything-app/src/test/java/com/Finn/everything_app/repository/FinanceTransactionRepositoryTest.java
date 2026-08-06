package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FinanceTransaction;
import com.Finn.everything_app.model.TransactionSource;
import com.Finn.everything_app.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sichert die Annahmen ab, auf denen der Bankimport aufsetzt: Dedup ueber externalId, und dass
 * manuelle Buchungen davon unberuehrt bleiben.
 */
@SpringBootTest
@Transactional
class FinanceTransactionRepositoryTest {

    @Autowired FinanceTransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;
    private User andererNutzer;

    @BeforeEach
    void setUp() {
        nutzer = anlegen("tx-nutzer");
        andererNutzer = anlegen("tx-fremder");
    }

    @Test
    void dedupErkenntBekannteUndUnbekannteBuchungen() {
        buchung(nutzer, "entry-1", LocalDate.of(2026, 7, 1), null);

        assertTrue(transactionRepository.existsByUserIdAndExternalId(nutzer.getId(), "entry-1"));
        assertFalse(transactionRepository.existsByUserIdAndExternalId(nutzer.getId(), "entry-2"));
    }

    @Test
    void dedupIstNutzerbezogen() {
        buchung(nutzer, "entry-1", LocalDate.of(2026, 7, 1), null);

        // Zwei Nutzer dürfen dieselbe entry_reference tragen - die Bank vergibt sie pro Konto.
        assertFalse(transactionRepository.existsByUserIdAndExternalId(andererNutzer.getId(), "entry-1"),
                "die externalId eines anderen Nutzers darf nicht als Duplikat zählen");
    }

    @Test
    void manuelleBuchungenDuerfenAlleExternalIdNullHaben() {
        buchung(nutzer, null, LocalDate.of(2026, 7, 1), null);
        buchung(nutzer, null, LocalDate.of(2026, 7, 2), null);
        buchung(nutzer, null, LocalDate.of(2026, 7, 3), null);

        // Stimmt die Annahme nicht, dass NULL-Werte in der UNIQUE-Bedingung als verschieden
        // gelten, bricht jede manuelle Buchung nach der ersten - und hier ist es am billigsten
        // zu merken.
        assertEquals(3, transactionRepository.findByUserId(nutzer.getId()).size());
    }

    @Test
    void findExternalIdsSinceLiefertNurBankBuchungenAbDemStichtag() {
        buchung(nutzer, "alt", LocalDate.of(2026, 1, 1), null);
        buchung(nutzer, "neu", LocalDate.of(2026, 7, 1), null);
        buchung(nutzer, null, LocalDate.of(2026, 7, 2), null);

        var ids = transactionRepository.findExternalIdsSince(nutzer.getId(), LocalDate.of(2026, 6, 1));

        assertEquals(java.util.Set.of("neu"), ids);
    }

    @Test
    void findRecategorizableUeberspringtGesperrteUndNimmtAltzeilenMit() {
        buchung(nutzer, null, LocalDate.of(2026, 7, 1), false);
        // Bestandszeile aus der Zeit vor der Spalte: category_locked ist NULL.
        buchung(nutzer, null, LocalDate.of(2026, 7, 2), null);
        buchung(nutzer, null, LocalDate.of(2026, 7, 3), true);

        List<FinanceTransaction> offen = transactionRepository.findRecategorizable(nutzer.getId());

        assertEquals(2, offen.size(), "NULL zählt wie false, nur true wird übersprungen");
        assertTrue(offen.stream().noneMatch(t -> Boolean.TRUE.equals(t.getCategoryLocked())));
    }

    private User anlegen(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("egal");
        return userRepository.save(user);
    }

    private void buchung(User user, String externalId, LocalDate datum, Boolean gesperrt) {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(user);
        tx.setAmount(12.34);
        tx.setType("AUSGABE");
        tx.setCategory("Sonstiges");
        tx.setDescription("Testbuchung");
        tx.setTransactionDate(datum);
        tx.setExternalId(externalId);
        tx.setSource(externalId == null ? TransactionSource.MANUAL : TransactionSource.BANK);
        tx.setCategoryLocked(gesperrt);
        transactionRepository.saveAndFlush(tx);
    }
}
