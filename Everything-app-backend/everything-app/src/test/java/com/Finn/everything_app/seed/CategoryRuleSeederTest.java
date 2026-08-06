package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.CategoryRule;
import com.Finn.everything_app.model.RuleSource;
import com.Finn.everything_app.repository.CategoryRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Der Seeder ist in Tests per {@code app.category-rule-seed.enabled=false} abgeschaltet; hier wird
 * er absichtlich von Hand gegen die H2-Datenbank ausgefuehrt - genau wie beim
 * {@link ExerciseCatalogSeederTest}.
 */
@SpringBootTest
@Transactional
class CategoryRuleSeederTest {

    /** Das Vokabular, das die Flutter-Oberflaeche rendern kann (finance_screen.dart). */
    private static final Set<String> FRONTEND_KATEGORIEN = Set.of(
            "Lebensmittel", "Transport", "Wohnen", "Gesundheit", "Unterhaltung",
            "Kleidung", "Restaurant", "Einnahmen", "Sonstiges");

    @Autowired CategoryRuleRepository categoryRuleRepository;
    @Autowired ObjectMapper objectMapper;

    private CategoryRuleSeeder seeder;

    @BeforeEach
    void setUp() {
        categoryRuleRepository.deleteAll();
        seeder = new CategoryRuleSeeder(categoryRuleRepository, objectMapper);
    }

    @Test
    void seedetRegelnUndIstIdempotent() throws Exception {
        seeder.run(null);
        long nachErstemLauf = categoryRuleRepository.count();

        assertTrue(nachErstemLauf > 100,
                "der Regelsatz sollte über hundert Muster enthalten, waren: " + nachErstemLauf);

        seeder.run(null);

        assertEquals(nachErstemLauf, categoryRuleRepository.count(),
                "ein zweiter Start darf den Regelsatz nicht verdoppeln");
    }

    @Test
    void jedeGeseedeteRegelIstGlobalUndWohlgeformt() throws Exception {
        seeder.run(null);

        for (CategoryRule rule : categoryRuleRepository.findAll()) {
            assertNull(rule.getUser(), "ausgelieferte Regeln gehören keinem Nutzer");
            assertEquals(RuleSource.SEEDED, rule.getSource());
            assertEquals(Boolean.TRUE, rule.getActive(),
                    "active muss explizit gesetzt sein - findApplicableRules filtert darauf");
            assertNotNull(rule.getPriority());
            assertNotNull(rule.getMatchField());
            assertNotNull(rule.getMatchType());

            String pattern = rule.getPattern();
            assertNotNull(pattern);
            assertFalse(pattern.isBlank(), "leere Muster würden auf jede Buchung passen");
            assertEquals(pattern.trim().toLowerCase(Locale.ROOT), pattern,
                    "Muster werden normalisiert gespeichert: " + pattern);

            // Die wertvollste Zusicherung hier: ein Tippfehler in der JSON darf keine Kategorie
            // erzeugen, die die Flutter-App nicht darstellen kann.
            assertTrue(FRONTEND_KATEGORIEN.contains(rule.getCategory()),
                    "unbekannte Kategorie im Regelsatz: " + rule.getCategory());
        }
    }

    @Test
    void keineDoppeltenMusterImKatalog() throws Exception {
        seeder.run(null);

        List<CategoryRule> alle = categoryRuleRepository.findAll();
        Set<String> muster = alle.stream().map(CategoryRule::getPattern).collect(Collectors.toSet());

        assertEquals(alle.size(), muster.size(),
                "zwei Regeln zum selben Muster konkurrieren miteinander - Copy-Paste in der JSON?");
    }

    @Test
    void spaeterErgaenzteMusterWerdenNachgetragen() throws Exception {
        seeder.run(null);
        long vollstaendig = categoryRuleRepository.count();

        // Simuliert den Fall "der Regelsatz wächst in einer späteren Version": eine Regel fehlt
        // in der Datenbank, steht aber im Katalog.
        CategoryRule geloescht = categoryRuleRepository.findAll().get(0);
        categoryRuleRepository.delete(geloescht);
        categoryRuleRepository.flush();

        seeder.run(null);

        assertEquals(vollstaendig, categoryRuleRepository.count(),
                "die fehlende Regel muss beim nächsten Start nachgetragen werden");
    }

    @Test
    void einnahmenRegelnSchlagenAusgabenRegeln() throws Exception {
        seeder.run(null);

        CategoryRule gehalt = musterOderScheitern("gehalt");
        CategoryRule bahn = musterOderScheitern("deutsche bahn");

        // Sonst würde "Gehalt DB Netz AG" über eine Transport-Regel als Ausgabe kategorisiert.
        assertTrue(gehalt.getPriority() > bahn.getPriority(),
                "Einnahmen-Muster müssen vor Händler-Mustern greifen");
        assertEquals("Einnahmen", gehalt.getCategory());
    }

    private CategoryRule musterOderScheitern(String pattern) {
        return categoryRuleRepository.findAll().stream()
                .filter(r -> pattern.equals(r.getPattern()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Muster fehlt im Regelsatz: " + pattern));
    }
}
