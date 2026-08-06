package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.CategoryRuleRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gegen echte Repositories statt gegen Mocks: die Reihenfolge der Regeln kommt vollstaendig aus
 * einer JPQL-Abfrage, und genau daran haengt die Override-Semantik. Mit einem gemockten Repository
 * wuerde der Test nur pruefen, dass der Kategorisierer das erste Element einer Liste nimmt, die er
 * selbst diktiert hat.
 */
@SpringBootTest
@Transactional
class TransactionCategorizerTest {

    @Autowired TransactionCategorizer categorizer;
    @Autowired CategoryRuleRepository ruleRepository;
    @Autowired FinanceTransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;

    @BeforeEach
    void setUp() {
        ruleRepository.deleteAll();
        nutzer = anlegen("kategorisierer-nutzer");
    }

    @Test
    void ausgelieferteRegelGreift() {
        regel(null, "rewe", RuleMatchField.BOTH, RuleMatchType.CONTAINS, "Lebensmittel", 100);

        FinanceTransaction buchung = buchung("REWE Markt GmbH", "REWE SAGT DANKE");
        assertTrue(categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId())),
                "die Kategorie hat sich geändert");

        assertEquals("Lebensmittel", buchung.getCategory());
    }

    @Test
    void eigeneRegelSchlaegtAusgelieferteRegel() {
        regel(null, "amazon", RuleMatchField.BOTH, RuleMatchType.CONTAINS, "Sonstiges", 900);
        regel(nutzer, "amazon", RuleMatchField.COUNTERPARTY, RuleMatchType.CONTAINS, "Unterhaltung", 1);

        FinanceTransaction buchung = buchung("Amazon EU S.a.r.l.", "Bestellung");
        categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId()));

        // Eine ausgelieferte Regel ist nicht löschbar; die eigene Regel ist der einzige Override
        // und darf sich nicht über eine hohe Priorität der globalen Regel aushebeln lassen.
        assertEquals("Unterhaltung", buchung.getCategory());
    }

    @Test
    void ohneTrefferBleibtDieAuffangkategorie() {
        regel(null, "rewe", RuleMatchField.BOTH, RuleMatchType.CONTAINS, "Lebensmittel", 100);

        FinanceTransaction buchung = buchung("Völlig Unbekannt", "irgendwas");
        categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId()));

        assertEquals(TransactionCategorizer.FALLBACK_CATEGORY, buchung.getCategory());
    }

    @Test
    void festgelegteKategorieBleibtUnangetastet() {
        regel(null, "rewe", RuleMatchField.BOTH, RuleMatchType.CONTAINS, "Lebensmittel", 100);

        FinanceTransaction buchung = buchung("REWE Markt GmbH", "Einkauf");
        buchung.setCategory("Bewusst anders");
        buchung.setCategoryLocked(true);

        assertFalse(categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId())),
                "an einer festgelegten Buchung ändert sich nichts");
        assertEquals("Bewusst anders", buchung.getCategory());
    }

    @Test
    void kaputtesRegexLegtNurSeineEigeneRegelStill() {
        // Ohne die Einzelbehandlung reisst ein ungueltiges Muster die Kategorisierung des gesamten
        // Imports mit - und der Nutzer sieht hunderte Buchungen in "Sonstiges".
        regel(nutzer, "[unvollstaendig", RuleMatchField.BOTH, RuleMatchType.REGEX, "Kaputt", 900);
        regel(null, "rewe", RuleMatchField.BOTH, RuleMatchType.CONTAINS, "Lebensmittel", 100);

        FinanceTransaction buchung = buchung("REWE Markt GmbH", "Einkauf");
        categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId()));

        assertEquals("Lebensmittel", buchung.getCategory(), "die intakte Regel greift weiterhin");
        assertTrue(ruleRepository.findAll().stream()
                        .filter(r -> "[unvollstaendig".equals(r.getPattern()))
                        .noneMatch(CategoryRule::getActive),
                "die kaputte Regel wurde stillgelegt, nicht gelöscht");
    }

    @Test
    void regexRegelGreiftAufDenVerwendungszweck() {
        regel(nutzer, "miete\\s+wohnung", RuleMatchField.DESCRIPTION, RuleMatchType.REGEX, "Wohnen", 500);

        FinanceTransaction buchung = buchung("Hausverwaltung Kestner", "Miete Wohnung Blumenstr. 14");
        categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId()));

        assertEquals("Wohnen", buchung.getCategory());
    }

    // ==================== Lernen ====================

    @Test
    void lernenLegtEineRegelAufDieNormalisierteGegenparteiAn() {
        FinanceTransaction buchung = gespeichert("REWE SAGT DANKE. 12345//KONSTANZ", "Einkauf", "Sonstiges");

        categorizer.learn(buchung, "Lebensmittel", null, false);

        CategoryRule gelernt = ruleRepository.findAll().stream()
                .filter(r -> r.getSource() == RuleSource.LEARNED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine gelernte Regel angelegt"));

        // Das Muster muss die normalisierte Form sein, sonst passt es auf keine zweite Buchung
        // derselben Quelle - deren Rohtext trägt eine andere Filialnummer.
        assertEquals("rewe", gelernt.getPattern());
        assertEquals(RuleMatchField.COUNTERPARTY, gelernt.getMatchField(),
                "eine Regel auf den Verwendungszweck wäre zu breit");
        assertTrue(Boolean.TRUE.equals(buchung.getCategoryLocked()),
                "die korrigierte Buchung ist ab jetzt Handarbeit");
    }

    @Test
    void lernenZaehltDieGeschwisterUndAendertSieNurAufWunsch() {
        // Derselbe Markt, drei Rohtexte - genau der Fall, für den die Normalisierung da ist.
        FinanceTransaction korrigiert = gespeichert("REWE SAGT DANKE. 12345//KONSTANZ", "Einkauf A", "Sonstiges");
        gespeichert("REWE SAGT DANKE. 998877//SINGEN", "Einkauf B", "Sonstiges");
        gespeichert("REWE 4711", "Einkauf C", "Sonstiges");
        gespeichert("Shell Deutschland", "Tanken", "Sonstiges");

        int betroffen = categorizer.learn(korrigiert, "Lebensmittel", null, false);

        assertEquals(2, betroffen, "die beiden weiteren REWE-Buchungen, nicht die Tankstelle");
        assertTrue(transactionRepository.findAll().stream()
                        .filter(t -> "Einkauf B".equals(t.getDescription()))
                        .allMatch(t -> "Sonstiges".equals(t.getCategory())),
                "ohne applyToPast bleibt die Vergangenheit unberührt");
    }

    @Test
    void lernenMitApplyToPastKategorisiertDieVergangenheitUm() {
        FinanceTransaction korrigiert = gespeichert("REWE SAGT DANKE. 12345//KONSTANZ", "Einkauf A", "Sonstiges");
        gespeichert("REWE 998877", "Einkauf B", "Sonstiges");

        categorizer.learn(korrigiert, "Lebensmittel", "Supermarkt", true);

        assertTrue(transactionRepository.findAll().stream()
                        .filter(t -> "Einkauf B".equals(t.getDescription()))
                        .allMatch(t -> "Lebensmittel".equals(t.getCategory())),
                "mit applyToPast wird die frühere Buchung mitgezogen");
    }

    @Test
    void ohneGegenparteiWirdNichtsGelernt() {
        // Eine getippte Buchung hat keine Gegenpartei; eine Regel auf "Einkauf" würde wahllos
        // zuschlagen.
        FinanceTransaction buchung = gespeichert(null, "Einkauf", "Sonstiges");

        assertEquals(0, categorizer.learn(buchung, "Lebensmittel", null, true));
        assertTrue(ruleRepository.findAll().isEmpty(), "keine Regel aus einer Buchung ohne Gegenpartei");
    }

    // ==================== Hilfen ====================

    private User anlegen(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("egal");
        return userRepository.save(user);
    }

    private void regel(User user, String pattern, RuleMatchField field, RuleMatchType type,
                       String kategorie, int prioritaet) {
        CategoryRule rule = new CategoryRule();
        rule.setUser(user);
        rule.setPattern(pattern);
        rule.setMatchField(field);
        rule.setMatchType(type);
        rule.setCategory(kategorie);
        rule.setPriority(prioritaet);
        rule.setActive(true);
        rule.setSource(user == null ? RuleSource.SEEDED : RuleSource.MANUAL);
        ruleRepository.saveAndFlush(rule);
    }

    private FinanceTransaction buchung(String gegenpartei, String zweck) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(nutzer);
        transaction.setAmount(12.34);
        transaction.setType("AUSGABE");
        transaction.setCategory("Sonstiges");
        transaction.setDescription(zweck);
        transaction.setCounterparty(gegenpartei);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setSource(TransactionSource.BANK);
        transaction.setCategoryLocked(false);
        return transaction;
    }

    private FinanceTransaction gespeichert(String gegenpartei, String zweck, String kategorie) {
        FinanceTransaction transaction = buchung(gegenpartei, zweck);
        transaction.setCategory(kategorie);
        return transactionRepository.saveAndFlush(transaction);
    }

    @Test
    void loadRulesLiefertKeineFremdenRegeln() {
        User fremder = anlegen("kategorisierer-fremder");
        regel(fremder, "fremdes muster", RuleMatchField.BOTH, RuleMatchType.CONTAINS, "Transport", 900);

        FinanceTransaction buchung = buchung("Fremdes Muster GmbH", "egal");
        categorizer.categorize(buchung, categorizer.loadRules(nutzer.getId()));

        assertEquals(TransactionCategorizer.FALLBACK_CATEGORY, buchung.getCategory());
        assertEquals(List.of(), ruleRepository.findApplicableRules(nutzer.getId()));
    }
}
