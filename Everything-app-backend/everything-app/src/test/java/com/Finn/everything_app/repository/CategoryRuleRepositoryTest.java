package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CategoryRule;
import com.Finn.everything_app.model.RuleSource;
import com.Finn.everything_app.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * findApplicableRules traegt die gesamte Override-Semantik des Regelwerks. Ein Tippfehler in der
 * ORDER-BY-Klausel bricht nichts sichtbar - er sortiert nur anders, und der Kategorisierer nimmt
 * ab da die falsche Regel. Deshalb steht die Reihenfolge hier als Zusicherung.
 */
@SpringBootTest
@Transactional
class CategoryRuleRepositoryTest {

    @Autowired CategoryRuleRepository categoryRuleRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;
    private User andererNutzer;

    @BeforeEach
    void setUp() {
        categoryRuleRepository.deleteAll();
        nutzer = anlegen("regel-nutzer");
        andererNutzer = anlegen("regel-fremder");
    }

    @Test
    void liefertGlobaleUndEigeneRegelnAberKeineFremden() {
        regel(null, "rewe", "Lebensmittel", 100, true);
        regel(nutzer, "mein bäcker", "Restaurant", 100, true);
        regel(andererNutzer, "fremdes muster", "Transport", 100, true);

        List<CategoryRule> regeln = categoryRuleRepository.findApplicableRules(nutzer.getId());

        assertEquals(2, regeln.size(), "genau die globale und die eigene Regel");
        assertTrue(regeln.stream().noneMatch(r -> "fremdes muster".equals(r.getPattern())),
                "die Regel eines anderen Nutzers darf nie mitkommen");
    }

    @Test
    void abgeschalteteRegelnKommenNichtMit() {
        regel(null, "rewe", "Lebensmittel", 100, true);
        regel(nutzer, "abgeschaltet", "Transport", 500, false);

        List<CategoryRule> regeln = categoryRuleRepository.findApplicableRules(nutzer.getId());

        assertEquals(1, regeln.size());
        assertEquals("rewe", regeln.get(0).getPattern());
    }

    @Test
    void eigeneRegelSchlaegtGlobaleRegelTrotzNiedrigererPrioritaet() {
        regel(null, "amazon", "Sonstiges", 900, true);
        regel(nutzer, "amazon", "Unterhaltung", 1, true);

        List<CategoryRule> regeln = categoryRuleRepository.findApplicableRules(nutzer.getId());

        // Globale Regeln sind nicht löschbar - eine eigene Regel ist der einzige Override, und der
        // darf sich nicht über eine hohe Priorität im Regelsatz aushebeln lassen.
        assertNotNull(regeln.get(0).getUser(), "die eigene Regel muss zuerst kommen");
        assertEquals("Unterhaltung", regeln.get(0).getCategory());
    }

    @Test
    void innerhalbEinerGruppeGewinntDieHoehereProritaet() {
        regel(null, "generisch", "Sonstiges", 50, true);
        regel(null, "konkret", "Lebensmittel", 100, true);
        regel(null, "einnahme", "Einnahmen", 200, true);

        List<CategoryRule> regeln = categoryRuleRepository.findApplicableRules(nutzer.getId());

        assertEquals(List.of("einnahme", "konkret", "generisch"),
                regeln.stream().map(CategoryRule::getPattern).toList());
    }

    @Test
    void findGlobalPatternsLiefertNurDieGlobalenMuster() {
        regel(null, "rewe", "Lebensmittel", 100, true);
        regel(nutzer, "mein bäcker", "Restaurant", 100, true);

        assertEquals(java.util.Set.of("rewe"), categoryRuleRepository.findGlobalPatterns());
    }

    private User anlegen(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("egal");
        return userRepository.save(user);
    }

    private void regel(User user, String pattern, String kategorie, int prioritaet, boolean aktiv) {
        CategoryRule rule = new CategoryRule();
        rule.setUser(user);
        rule.setPattern(pattern);
        rule.setCategory(kategorie);
        rule.setPriority(prioritaet);
        rule.setActive(aktiv);
        rule.setSource(user == null ? RuleSource.SEEDED : RuleSource.MANUAL);
        categoryRuleRepository.saveAndFlush(rule);
    }
}
