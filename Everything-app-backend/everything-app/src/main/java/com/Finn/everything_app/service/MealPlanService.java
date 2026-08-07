package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    @Transactional
    public MealPlan createMealPlan(Long userId, MealPlan mealPlan, Long recipeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rezept nicht gefunden"));

        mealPlan.setUser(user);
        mealPlan.setRecipe(recipe);

        if (mealPlan.getPlannedServings() == null) {
            mealPlan.setPlannedServings(recipe.getServings());
        }

        return mealPlanRepository.save(mealPlan);
    }

    public List<MealPlan> getMealPlanForPeriod(Long userId, LocalDate start, LocalDate end) {
        return mealPlanRepository.findByUserIdAndDateBetween(userId, start, end);
    }

    public List<MealPlan> getMealPlanForDate(Long userId, LocalDate date) {
        return mealPlanRepository.findByUserIdAndDate(userId, date);
    }

    public MealPlan getMealPlan(Long userId, Long id) {
        return mealPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Essensplan nicht gefunden"));
    }

    @Transactional
    public MealPlan updateMealPlan(Long userId, Long id, MealPlan updatedMealPlan) {
        MealPlan mealPlan = getMealPlan(userId, id);

        if (updatedMealPlan.getDate() != null) {
            mealPlan.setDate(updatedMealPlan.getDate());
        }
        if (updatedMealPlan.getMealType() != null) {
            mealPlan.setMealType(updatedMealPlan.getMealType());
        }
        if (updatedMealPlan.getPlannedServings() != null) {
            mealPlan.setPlannedServings(updatedMealPlan.getPlannedServings());
        }
        if (updatedMealPlan.getNotes() != null) {
            mealPlan.setNotes(updatedMealPlan.getNotes());
        }

        return mealPlanRepository.save(mealPlan);
    }

    @Transactional
    public MealPlan completeMealPlan(Long userId, Long id) {
        MealPlan mealPlan = getMealPlan(userId, id);

        mealPlan.setIsCompleted(true);
        mealPlan.setCompletedAt(LocalDateTime.now());

        return mealPlanRepository.save(mealPlan);
    }

    @Transactional
    public void deleteMealPlan(Long userId, Long id) {
        MealPlan mealPlan = getMealPlan(userId, id);
        mealPlanRepository.delete(mealPlan);
    }

    // AUTOMATISCHE WOCHENPLANUNG

    /**
     * Fuellt sieben Tage mit Fruehstueck, Mittag- und Abendessen.
     *
     * <p>Die Auswahl laeuft ueber {@link Recipe#getSuitableFor()}, nicht mehr ueber die
     * Kategorie. Vorher stand dort {@code recipe.getCategory().equals("MITTAGESSEN")} - die
     * Kategorie beschreibt aber, *was* ein Gericht ist, nicht *wann* man es isst. Mit einem
     * echten Kategorienkatalog ("Pasta & Reis", "Auflauf & Ofen") hat dieser Vergleich nie
     * wieder getroffen, und die Planung lieferte stumm eine leere Liste.
     *
     * <p>Innerhalb eines Durchlaufs kommt kein Rezept zweimal vor, solange es Alternativen
     * gibt; die Reihenfolge kommt aus der Abfrage, also lange nicht Gekochtes zuerst. Ein Slot
     * ohne passendes Rezept wird uebersprungen - frueher brach die ganze Generierung mit
     * {@code RuntimeException("Keine Rezepte verfügbar")} ab, auch wenn nur das Fruehstueck fehlte.
     */
    @Transactional
    public List<MealPlan> generateWeeklyPlan(Long userId, LocalDate startDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        MealType[] mealTypes = {MealType.FRUEHSTUECK, MealType.MITTAGESSEN, MealType.ABENDESSEN};

        Map<MealType, List<Recipe>> candidates = new EnumMap<>(MealType.class);
        for (MealType mealType : mealTypes) {
            candidates.put(mealType, recipeRepository.findSuitableFor(userId, mealType));
        }

        List<MealPlan> weeklyPlan = new ArrayList<>();
        Set<Long> alreadyPlanned = new HashSet<>();

        for (int day = 0; day < 7; day++) {
            LocalDate date = startDate.plusDays(day);

            for (MealType mealType : mealTypes) {
                List<Recipe> suitable = candidates.get(mealType);
                if (suitable.isEmpty()) {
                    continue;
                }

                Recipe chosen = suitable.stream()
                        .filter(r -> !alreadyPlanned.contains(r.getId()))
                        .findFirst()
                        // Weniger Rezepte als Slots: dann lieber ein zweites Mal dasselbe
                        // Gericht als eine Luecke im Plan.
                        .orElse(suitable.get(day % suitable.size()));
                alreadyPlanned.add(chosen.getId());

                MealPlan mealPlan = new MealPlan();
                mealPlan.setUser(user);
                mealPlan.setRecipe(chosen);
                mealPlan.setDate(date);
                mealPlan.setMealType(mealType);
                mealPlan.setPlannedServings(chosen.getServings());

                weeklyPlan.add(mealPlanRepository.save(mealPlan));
            }
        }

        return weeklyPlan;
    }

    // EINKAUFSLISTE GENERIEREN

    public ShoppingListDTO generateShoppingList(Long userId, LocalDate start, LocalDate end) {
        List<MealPlan> mealPlans = getMealPlanForPeriod(userId, start, end);

        // Sammle alle Zutaten
        Map<String, List<String>> ingredientsByCategory = new HashMap<>();
        Set<String> allIngredients = new HashSet<>();

        for (MealPlan mealPlan : mealPlans) {
            Recipe recipe = mealPlan.getRecipe();

            for (RecipeIngredient ingredient : recipe.getIngredientList()) {
                String name = ingredient.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                allIngredients.add(name);

                // Kategorisiere (vereinfacht)
                String category = categorizeIngredient(name);
                ingredientsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(name);
            }
        }

        ShoppingListDTO shoppingList = new ShoppingListDTO();
        shoppingList.setIngredientsByCategory(ingredientsByCategory);
        shoppingList.setTotalItems(allIngredients.size());
        shoppingList.setAllIngredients(new ArrayList<>(allIngredients));

        return shoppingList;
    }

    private String categorizeIngredient(String ingredient) {
        ingredient = ingredient.toLowerCase();

        if (ingredient.contains("fleisch") || ingredient.contains("hähnchen") || ingredient.contains("rind")) {
            return "Fleisch & Fisch";
        } else if (ingredient.contains("milch") || ingredient.contains("käse") || ingredient.contains("joghurt")) {
            return "Milchprodukte";
        } else if (ingredient.contains("tomate") || ingredient.contains("gurke") || ingredient.contains("salat")) {
            return "Gemüse";
        } else if (ingredient.contains("apfel") || ingredient.contains("banane") || ingredient.contains("orange")) {
            return "Obst";
        } else {
            return "Sonstiges";
        }
    }

    // NÄHRWERT-BERECHNUNG

    public NutritionStatsDTO calculateDailyNutrition(Long userId, LocalDate date) {
        List<MealPlan> dailyMeals = getMealPlanForDate(userId, date);

        int totalCalories = 0;
        double totalProtein = 0.0;
        double totalCarbs = 0.0;
        double totalFat = 0.0;
        int mealsCompleted = 0;

        for (MealPlan mealPlan : dailyMeals) {
            Recipe recipe = mealPlan.getRecipe();
            double servingRatio = (double) mealPlan.getPlannedServings() / recipe.getServings();

            if (recipe.getCalories() != null) {
                totalCalories += (int) (recipe.getCalories() * servingRatio);
            }
            if (recipe.getProtein() != null) {
                totalProtein += recipe.getProtein() * servingRatio;
            }
            if (recipe.getCarbs() != null) {
                totalCarbs += recipe.getCarbs() * servingRatio;
            }
            if (recipe.getFat() != null) {
                totalFat += recipe.getFat() * servingRatio;
            }

            if (mealPlan.getIsCompleted()) {
                mealsCompleted++;
            }
        }

        NutritionStatsDTO stats = new NutritionStatsDTO();
        stats.setTotalCalories(totalCalories);
        stats.setTotalProtein(totalProtein);
        stats.setTotalCarbs(totalCarbs);
        stats.setTotalFat(totalFat);
        stats.setMealsPlanned(dailyMeals.size());
        stats.setMealsCompleted(mealsCompleted);

        // Zielwerte (können später anpassbar gemacht werden)
        stats.setTargetCalories(2000);
        stats.setTargetProtein(150.0);
        stats.setTargetCarbs(250.0);
        stats.setTargetFat(65.0);

        return stats;
    }
}
