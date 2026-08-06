package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.CategoryRule;
import com.Finn.everything_app.model.RuleMatchField;
import com.Finn.everything_app.model.RuleMatchType;
import com.Finn.everything_app.model.RuleSource;
import com.Finn.everything_app.repository.CategoryRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Legt den mitgelieferten Kategorisierungs-Regelsatz beim Start aus {@code data/category-rules.json} an.
 *
 * <p>Die erzeugten Regeln sind global ({@code user == null}) und gelten fuer alle Nutzer; siehe
 * {@link CategoryRule} fuer die Konsequenzen dieser Entscheidung.
 *
 * <p>Idempotent ueber das normalisierte {@code pattern} der bereits vorhandenen globalen Regeln -
 * im Normalfall kostet der Seeder genau eine SELECT-Abfrage. Weil Muster normalisiert
 * <em>gespeichert</em> werden, braucht der Abgleich keine Umformung pro Zeile.
 *
 * <p><strong>Der Seeder fuegt nur hinzu, er aendert nie.</strong> Neue Muster erscheinen beim
 * naechsten Start; die Kategorie eines <em>bestehenden</em> Musters in der JSON zu aendern hat auf
 * einer gewachsenen Datenbank dagegen keine Wirkung. Dafuer braucht es eine Handmigration unter
 * {@code db/manual/} oder ein DELETE der globalen Zeile, die der Seeder dann neu anlegt. Ein
 * Upsert waere technisch gefahrlos (globale Regeln sind nicht nutzer-editierbar), aber "der Seeder
 * schreibt bei jedem Start still Zeilen um" ist der schlechtere Standard - und weicht von der
 * Semantik des {@link ExerciseCatalogSeeder} ab.
 *
 * <p>Abschaltbar mit {@code app.category-rule-seed.enabled=false} (so laeuft er in Tests nicht).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.category-rule-seed.enabled", havingValue = "true", matchIfMissing = true)
public class CategoryRuleSeeder implements ApplicationRunner {

    private static final String RESOURCE_PATH = "data/category-rules.json";

    /** Vorgaben fuer Felder, die eine Gruppe in der JSON weglaesst. */
    private static final int DEFAULT_PRIORITY = 100;
    private static final RuleMatchField DEFAULT_MATCH_FIELD = RuleMatchField.BOTH;
    private static final RuleMatchType DEFAULT_MATCH_TYPE = RuleMatchType.CONTAINS;

    private final CategoryRuleRepository categoryRuleRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Set<String> existing = categoryRuleRepository.findGlobalPatterns();

        List<CategoryRuleSeedGroup> groups = readCatalog();
        if (groups.isEmpty()) {
            log.warn("Regel-Katalog {} ist leer oder fehlt - kein Seeding", RESOURCE_PATH);
            return;
        }

        // Innerhalb eines Laufs darf dasselbe Muster nur einmal entstehen, sonst legt ein
        // Copy-Paste in der JSON zwei konkurrierende Regeln an.
        Set<String> seen = new LinkedHashSet<>(existing);
        List<CategoryRule> toInsert = new ArrayList<>();

        for (CategoryRuleSeedGroup group : groups) {
            if (group.patterns() == null || group.category() == null) {
                log.warn("Regel-Gruppe ohne Kategorie oder Muster - uebersprungen: {}", group);
                continue;
            }
            for (String rawPattern : group.patterns()) {
                String pattern = normalize(rawPattern);
                if (pattern.isEmpty() || !seen.add(pattern)) {
                    continue;
                }
                toInsert.add(toRule(group, pattern));
            }
        }

        if (toInsert.isEmpty()) {
            log.info("Regel-Katalog bereits vollständig ({} Regeln), kein Seeding nötig", existing.size());
            return;
        }

        // Kein Chunking: ~180 Zeilen, und hibernate.jdbc.batch_size sorgt ohnehin fuer Batching.
        categoryRuleRepository.saveAll(toInsert);
        log.info("Regel-Katalog geseedet: {} neue Regeln ({} waren bereits vorhanden)",
                toInsert.size(), existing.size());
    }

    private List<CategoryRuleSeedGroup> readCatalog() throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<CategoryRuleSeedGroup>>() {});
        }
    }

    private CategoryRule toRule(CategoryRuleSeedGroup group, String pattern) {
        CategoryRule rule = new CategoryRule();
        rule.setPattern(pattern);
        rule.setCategory(group.category());
        rule.setSubcategory(group.subcategory());
        rule.setPriority(group.priority() != null ? group.priority() : DEFAULT_PRIORITY);
        rule.setMatchField(group.matchField() != null ? group.matchField() : DEFAULT_MATCH_FIELD);
        rule.setMatchType(group.matchType() != null ? group.matchType() : DEFAULT_MATCH_TYPE);
        rule.setSource(RuleSource.SEEDED);
        // Explizit setzen statt sich auf den Feld-Initialisierer zu verlassen: findApplicableRules
        // filtert auf active = true, und NULL wuerde die Regel unsichtbar machen.
        rule.setActive(true);
        rule.setUser(null);
        return rule;
    }

    /** Muster werden normalisiert gespeichert, damit weder Abgleich noch Kategorisierung umformen muessen. */
    private String normalize(String pattern) {
        return pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
    }
}
