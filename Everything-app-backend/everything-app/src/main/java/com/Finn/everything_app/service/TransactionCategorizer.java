package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.CategoryRuleRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Ordnet Buchungen einer Kategorie zu - und lernt aus Korrekturen des Nutzers.
 *
 * <p>Die Reihenfolge der Regeln kommt vollstaendig aus
 * {@link CategoryRuleRepository#findApplicableRules(Long)}: eigene Regeln vor ausgelieferten,
 * darin absteigend nach Prioritaet. Hier wird nur noch der erste Treffer genommen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCategorizer {

    /** Auffangkategorie, wenn keine Regel passt. Muss im Vokabular der Oberflaeche liegen. */
    public static final String FALLBACK_CATEGORY = "Sonstiges";

    private final CategoryRuleRepository categoryRuleRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * Ein geladener Regelsatz mit vorkompilierten Mustern.
     *
     * <p>Der Sync kategorisiert hunderte Buchungen am Stueck; die Regeln fuer jede einzeln zu laden
     * und jedes Regex neu zu uebersetzen waere das Teuerste am ganzen Import.
     */
    public final class RuleSet {
        private final List<CategoryRule> rules;
        private final Map<Long, Pattern> compiled = new HashMap<>();
        private final List<CategoryRule> broken = new ArrayList<>();

        private RuleSet(List<CategoryRule> rules) {
            this.rules = rules;
            for (CategoryRule rule : rules) {
                if (rule.getMatchType() != RuleMatchType.REGEX) {
                    continue;
                }
                try {
                    compiled.put(rule.getId(), Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException e) {
                    // Einzeln abfangen: sonst reisst eine kaputte Nutzerregel die Kategorisierung
                    // des gesamten Imports mit. Die Regel wird stillgelegt, nicht geloescht.
                    log.warn("Regel {} hat ein ungültiges Muster '{}' und wird deaktiviert: {}",
                            rule.getId(), rule.getPattern(), e.getDescription());
                    rule.setActive(false);
                    broken.add(rule);
                }
            }
            if (!broken.isEmpty()) {
                categoryRuleRepository.saveAll(broken);
            }
        }

        private boolean isUsable(CategoryRule rule) {
            return rule.getMatchType() != RuleMatchType.REGEX || compiled.containsKey(rule.getId());
        }
    }

    /** Laedt den Regelsatz eines Nutzers einmal fuer einen ganzen Durchlauf. */
    @Transactional
    public RuleSet loadRules(Long userId) {
        return new RuleSet(categoryRuleRepository.findApplicableRules(userId));
    }

    /**
     * Setzt Kategorie und Unterkategorie an der Buchung.
     *
     * <p>Vom Nutzer festgelegte Kategorien ({@code categoryLocked}) bleiben unangetastet - das ist
     * der ganze Zweck des Feldes.
     *
     * @return {@code true}, wenn sich etwas geaendert hat
     */
    public boolean categorize(FinanceTransaction transaction, RuleSet ruleSet) {
        if (Boolean.TRUE.equals(transaction.getCategoryLocked())) {
            return false;
        }

        CategoryRule match = findMatch(transaction, ruleSet);
        String category = match != null ? match.getCategory() : FALLBACK_CATEGORY;
        String subcategory = match != null ? match.getSubcategory() : null;

        boolean changed = !category.equals(transaction.getCategory())
                || !java.util.Objects.equals(subcategory, transaction.getSubcategory());

        transaction.setCategory(category);
        transaction.setSubcategory(subcategory);
        return changed;
    }

    private CategoryRule findMatch(FinanceTransaction transaction, RuleSet ruleSet) {
        String counterparty = lower(transaction.getCounterparty());
        String description = lower(transaction.getDescription());

        for (CategoryRule rule : ruleSet.rules) {
            if (!ruleSet.isUsable(rule)) {
                continue;
            }
            if (matches(rule, counterparty, description, ruleSet)) {
                return rule;
            }
        }
        return null;
    }

    private boolean matches(CategoryRule rule, String counterparty, String description, RuleSet ruleSet) {
        RuleMatchField field = rule.getMatchField() == null ? RuleMatchField.BOTH : rule.getMatchField();

        boolean checkCounterparty = field != RuleMatchField.DESCRIPTION;
        boolean checkDescription = field != RuleMatchField.COUNTERPARTY;

        return (checkCounterparty && matchesValue(rule, counterparty, ruleSet))
                || (checkDescription && matchesValue(rule, description, ruleSet));
    }

    private boolean matchesValue(CategoryRule rule, String value, RuleSet ruleSet) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (rule.getMatchType() == RuleMatchType.REGEX) {
            Pattern pattern = ruleSet.compiled.get(rule.getId());
            return pattern != null && pattern.matcher(value).find();
        }
        // Muster liegen bereits normalisiert (klein, getrimmt) in der Datenbank.
        return value.contains(rule.getPattern());
    }

    // ==================== Lernen ====================

    /** Ergebnis einer Korrektur - {@code affected} zaehlt die weiteren Buchungen derselben Gegenpartei. */
    public record Recategorized(FinanceTransaction transaction, int affected) {
    }

    /**
     * Einstiegspunkt fuer den Endpunkt: prueft die Zugehoerigkeit und uebergibt an
     * {@link #learn(FinanceTransaction, String, String, boolean)}.
     */
    @Transactional
    public Recategorized recategorize(Long userId, Long transactionId, String category,
                                      String subcategory, boolean applyToPast) {
        FinanceTransaction transaction = transactionRepository.findById(transactionId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Buchung nicht gefunden: " + transactionId));

        int affected = learn(transaction, category, subcategory, applyToPast);
        return new Recategorized(transaction, affected);
    }

    /**
     * Uebernimmt eine Korrektur des Nutzers und leitet daraus eine Regel ab.
     *
     * <p>Die Regel haengt am normalisierten Namen der Gegenpartei, nicht am Verwendungszweck: der
     * enthaelt Belegnummern und wechselt bei jeder Buchung. Ohne Gegenpartei - etwa bei einer
     * manuell eingetippten Buchung - wird nichts gelernt; eine Regel auf einen Verwendungszweck
     * wie "Einkauf" wuerde wahllos zuschlagen.
     *
     * @param applyToPast ob dieselbe Gegenpartei rueckwirkend umkategorisiert wird
     * @return Anzahl weiterer Buchungen, die betroffen sind bzw. waeren
     */
    @Transactional
    public int learn(FinanceTransaction transaction, String category, String subcategory, boolean applyToPast) {
        Long userId = transaction.getUser().getId();

        transaction.setCategory(category);
        transaction.setSubcategory(subcategory);
        // Ab jetzt Handarbeit: die Auto-Kategorisierung fasst diese Buchung nicht mehr an.
        transaction.setCategoryLocked(true);
        transactionRepository.save(transaction);

        String key = CounterpartyNormalizer.normalize(transaction.getCounterparty());
        if (key.isEmpty()) {
            return 0;
        }

        upsertLearnedRule(userId, key, category, subcategory);

        List<FinanceTransaction> siblings = transactionRepository.findRecategorizable(userId).stream()
                .filter(other -> !other.getId().equals(transaction.getId()))
                .filter(other -> key.equals(CounterpartyNormalizer.normalize(other.getCounterparty())))
                .toList();

        if (applyToPast && !siblings.isEmpty()) {
            for (FinanceTransaction sibling : siblings) {
                sibling.setCategory(category);
                sibling.setSubcategory(subcategory);
            }
            transactionRepository.saveAll(siblings);
        }
        return siblings.size();
    }

    private void upsertLearnedRule(Long userId, String pattern, String category, String subcategory) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " nicht gefunden"));

        CategoryRule rule = categoryRuleRepository
                .findByUserIdAndPatternAndMatchField(userId, pattern, RuleMatchField.COUNTERPARTY)
                .orElseGet(CategoryRule::new);

        rule.setUser(user);
        rule.setPattern(pattern);
        // Bewusst nur die Gegenpartei: der Verwendungszweck wuerde die Regel zu breit machen.
        rule.setMatchField(RuleMatchField.COUNTERPARTY);
        rule.setMatchType(RuleMatchType.CONTAINS);
        rule.setCategory(category);
        rule.setSubcategory(subcategory);
        rule.setSource(RuleSource.LEARNED);
        rule.setActive(true);
        // Prioritaet ist zweitrangig - eigene Regeln stehen ohnehin vor allen ausgelieferten.
        rule.setPriority(150);
        categoryRuleRepository.save(rule);
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
