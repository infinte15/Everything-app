package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ShoppingItemDTO;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.service.recipe.IngredientAisleClassifier;
import com.Finn.everything_app.service.recipe.UnitVocabulary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Die Einkaufsliste - eine offene Liste je Nutzer, dauerhaft gespeichert.
 *
 * <p>Loest {@code MealPlanService.generateShoppingList} ab, die bei jedem Aufruf neu rechnete
 * und dabei drei Dinge falsch machte: die Zutaten landeten in einem {@code HashSet}, sodass
 * "200 g Mehl" und "300 g Mehl" zu einer Zeile ohne Summe wurden; die Mengen wurden nicht auf
 * die geplanten Portionen umgerechnet; und der Haken lag im Frontend in einer Map und war beim
 * naechsten Zeichnen weg.
 */
@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private final ShoppingItemRepository shoppingItemRepository;
    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final IngredientAisleClassifier aisleClassifier;

    public List<ShoppingItem> getList(Long userId) {
        return shoppingItemRepository.findByUserIdOrderByCategoryAscSortOrderAscIdAsc(userId);
    }

    @Transactional
    public ShoppingItem addManualItem(Long userId, ShoppingItemDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        ShoppingItem item = new ShoppingItem();
        item.setUser(user);
        item.setName(dto.getName().trim());
        item.setAmount(dto.getAmount());
        item.setUnit(blankToNull(dto.getUnit()));
        item.setCategory(dto.getCategory() != null && !dto.getCategory().isBlank()
                ? dto.getCategory()
                : aisleClassifier.classify(dto.getName()));
        item.setIsChecked(false);
        item.setSource(ShoppingItemSource.MANUAL);

        return shoppingItemRepository.save(item);
    }

    @Transactional
    public ShoppingItem updateItem(Long userId, Long id, ShoppingItemDTO dto) {
        ShoppingItem item = shoppingItemRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Eintrag nicht gefunden"));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            item.setName(dto.getName().trim());
        }
        if (dto.getAmount() != null) {
            item.setAmount(dto.getAmount());
        }
        if (dto.getUnit() != null) {
            item.setUnit(blankToNull(dto.getUnit()));
        }
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            item.setCategory(dto.getCategory());
        }
        if (dto.getIsChecked() != null) {
            item.setIsChecked(dto.getIsChecked());
            item.setCheckedAt(dto.getIsChecked() ? LocalDateTime.now() : null);
        }

        return shoppingItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long userId, Long id) {
        ShoppingItem item = shoppingItemRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Eintrag nicht gefunden"));
        shoppingItemRepository.delete(item);
    }

    @Transactional
    public int deleteChecked(Long userId) {
        List<ShoppingItem> checked = shoppingItemRepository.findByUserIdAndIsChecked(userId, true);
        shoppingItemRepository.deleteAll(checked);
        return checked.size();
    }

    /**
     * Baut die Wochenplan-Zeilen neu auf.
     *
     * <p>Geloescht wird ausschliesslich, was aus dem Wochenplan stammt <em>und</em> noch nicht
     * abgehakt ist. Eigene Eintraege und alles, was man im Laden schon eingesammelt hat,
     * ueberlebt - sonst waere ein Klick auf "aktualisieren" mitten im Supermarkt eine
     * Katastrophe.
     */
    @Transactional
    public List<ShoppingItem> rebuildFromMealPlan(Long userId, LocalDate start, LocalDate end) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        shoppingItemRepository.deleteAll(shoppingItemRepository
                .findByUserIdAndSourceAndIsChecked(userId, ShoppingItemSource.MEAL_PLAN, false));

        List<MealPlan> mealPlans = mealPlanRepository
                .findByUserIdAndDateBetween(userId, start, end);

        Map<MergeKey, MergedIngredient> merged = new LinkedHashMap<>();

        for (MealPlan mealPlan : mealPlans) {
            Recipe recipe = mealPlan.getRecipe();
            BigDecimal ratio = servingRatio(mealPlan, recipe);

            for (RecipeIngredient ingredient : recipe.getIngredientList()) {
                if (ingredient.getName() == null || ingredient.getName().isBlank()) {
                    continue;
                }
                accumulate(merged, ingredient, ratio);
            }
        }

        List<ShoppingItem> created = new ArrayList<>();
        int order = 0;
        for (MergedIngredient entry : merged.values()) {
            ShoppingItem item = new ShoppingItem();
            item.setUser(user);
            item.setName(entry.displayName);
            item.setUnit(entry.unit);
            item.setAmount(entry.amount == null ? null : entry.amount.stripTrailingZeros());
            item.setCategory(aisleClassifier.classify(entry.displayName));
            item.setIsChecked(false);
            item.setSource(ShoppingItemSource.MEAL_PLAN);
            item.setSortOrder(order++);
            created.add(shoppingItemRepository.save(item));
        }
        return created;
    }

    /**
     * Zutaten eines einzelnen Rezepts uebernehmen - ohne Umweg ueber den Wochenplan.
     *
     * <p>Wer im Kochbuch etwas sieht, das er heute kaufen will, soll es nicht erst als Mahlzeit
     * einplanen muessen. Gerechnet wird mit demselben {@link #accumulate} wie beim Wochenplan,
     * damit dieselbe Zutat nicht je nach Weg eine andere Menge ergibt.
     *
     * <p><b>Zusammengelegt wird nur mit offenen, selbst angelegten Zeilen.</b> Das ist keine
     * Kleinigkeit: {@link #rebuildFromMealPlan} loescht alle offenen {@code MEAL_PLAN}-Zeilen.
     * Wer 300 g Mehl in eine solche Zeile addierte, verloere sie beim naechsten "Aus Wochenplan
     * aufbauen" stillschweigend. Und abgehakte Zeilen liegen schon im Wagen - sie wachsen zu
     * lassen, waehrend sie durchgestrichen dastehen, hilft niemandem.
     *
     * <p>Aus demselben Grund tragen neue Zeilen {@code MANUAL}: sie stammen nicht aus dem Plan
     * und muessen einen Neuaufbau ueberleben.
     */
    @Transactional
    public List<ShoppingItem> addFromRecipe(Long userId, Recipe recipe, Integer servings) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        BigDecimal ratio = ratio(servings, recipe.getServings());

        Map<MergeKey, MergedIngredient> merged = new LinkedHashMap<>();
        for (RecipeIngredient ingredient : recipe.getIngredientList()) {
            if (ingredient.getName() == null || ingredient.getName().isBlank()) {
                continue;
            }
            accumulate(merged, ingredient, ratio);
        }

        Map<MergeKey, ShoppingItem> candidates = new HashMap<>();
        for (ShoppingItem item : shoppingItemRepository
                .findByUserIdAndSourceAndIsChecked(userId, ShoppingItemSource.MANUAL, false)) {
            candidates.putIfAbsent(
                    new MergeKey(item.getName().toLowerCase(Locale.GERMAN).trim(),
                            UnitVocabulary.baseUnit(item.getUnit())),
                    item);
        }

        List<ShoppingItem> touched = new ArrayList<>();
        for (Map.Entry<MergeKey, MergedIngredient> entry : merged.entrySet()) {
            MergedIngredient ingredient = entry.getValue();
            ShoppingItem existing = candidates.get(entry.getKey());

            if (existing != null) {
                if (ingredient.amount != null && existing.getAmount() != null) {
                    existing.setAmount(existing.getAmount().add(ingredient.amount)
                            .stripTrailingZeros());
                    touched.add(shoppingItemRepository.save(existing));
                }
                // Trifft eine mengenlose Zutat ("Salz") auf eine vorhandene Zeile, bleibt alles
                // wie es ist - eine Menge zu erfinden waere schlimmer als nichts zu tun.
                continue;
            }

            ShoppingItem item = new ShoppingItem();
            item.setUser(user);
            item.setName(ingredient.displayName);
            item.setUnit(ingredient.unit);
            item.setAmount(ingredient.amount == null ? null : ingredient.amount.stripTrailingZeros());
            item.setCategory(aisleClassifier.classify(ingredient.displayName));
            item.setIsChecked(false);
            item.setSource(ShoppingItemSource.MANUAL);
            touched.add(shoppingItemRepository.save(item));
        }
        return touched;
    }

    /**
     * Anteil der geplanten an den im Rezept angegebenen Portionen.
     *
     * <p>Die alte Fassung hat gar nicht skaliert: wer ein Vier-Personen-Rezept fuer acht
     * einplante, bekam die Zutaten fuer vier auf den Zettel.
     */
    private BigDecimal servingRatio(MealPlan mealPlan, Recipe recipe) {
        return ratio(mealPlan.getPlannedServings(), recipe.getServings());
    }

    /** Dieselbe Rechnung fuer beide Wege - sonst laufen sie mit der Zeit auseinander. */
    private BigDecimal ratio(Integer planned, Integer base) {
        if (planned == null || base == null || base == 0 || planned.equals(base)) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(planned).divide(BigDecimal.valueOf(base), 4, RoundingMode.HALF_UP);
    }

    private void accumulate(Map<MergeKey, MergedIngredient> merged,
                            RecipeIngredient ingredient,
                            BigDecimal ratio) {

        String baseUnit = UnitVocabulary.baseUnit(ingredient.getUnit());
        MergeKey key = new MergeKey(
                ingredient.getName().toLowerCase(Locale.GERMAN).trim(),
                baseUnit);

        MergedIngredient existing = merged.get(key);
        if (existing == null) {
            existing = new MergedIngredient(ingredient.getName().trim(), baseUnit, null);
            merged.put(key, existing);
        }

        if (ingredient.getAmount() == null) {
            // Eine Zutat ohne Menge ("Salz") bleibt ohne Menge, auch wenn sie in zwei
            // Rezepten vorkommt. Sie zaehlbar zu machen waere erfunden.
            return;
        }
        BigDecimal scaled = ingredient.getAmount()
                .multiply(UnitVocabulary.toBaseFactor(ingredient.getUnit()))
                .multiply(ratio);
        existing.amount = existing.amount == null ? scaled : existing.amount.add(scaled);
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Zusammenlegen nur bei gleicher Zutat <em>und</em> vereinbarer Einheit.
     *
     * <p>"2 EL Öl" und "100 ml Öl" bleiben deshalb zwei Zeilen. Ohne die Dichte des Oels ist
     * jede Summe geraten, und eine falsche Zahl auf einem Einkaufszettel ist schlimmer als
     * zwei Zeilen, die man selbst zusammenzaehlt.
     */
    private record MergeKey(String name, String baseUnit) {
    }

    private static final class MergedIngredient {
        private final String displayName;
        private final String unit;
        private BigDecimal amount;

        private MergedIngredient(String displayName, String unit, BigDecimal amount) {
            this.displayName = displayName;
            this.unit = unit;
            this.amount = amount;
        }
    }
}
