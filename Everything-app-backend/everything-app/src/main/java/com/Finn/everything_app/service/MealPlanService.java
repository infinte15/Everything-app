package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    @Transactional
    public MealPlan createMealPlan(Long userId, MealPlan mealPlan, Long recipeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RuntimeException("Rezept nicht gefunden"));

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

    @Transactional
    public MealPlan updateMealPlan(Long id, MealPlan updatedMealPlan) {
        MealPlan mealPlan = mealPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Essensplan nicht gefunden"));

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
    public MealPlan completeMealPlan(Long id) {
        MealPlan mealPlan = mealPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Essensplan nicht gefunden"));

        mealPlan.setIsCompleted(true);
        mealPlan.setCompletedAt(LocalDateTime.now());

        return mealPlanRepository.save(mealPlan);
    }

    @Transactional
    public void deleteMealPlan(Long id) {
        MealPlan mealPlan = mealPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Essensplan nicht gefunden"));
        mealPlanRepository.delete(mealPlan);
    }

    // AUTOMATISCHE WOCHENPLANUNG

    @Transactional
    public List<MealPlan> generateWeeklyPlan(Long userId, LocalDate startDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        List<Recipe> recipes = recipeRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (recipes.isEmpty()) {
            throw new RuntimeException("Keine Rezepte verfügbar für automatische Planung");
        }

        List<MealPlan> weeklyPlan = new ArrayList<>();
        String[] mealTypes = {"FRÜHSTÜCK", "MITTAGESSEN", "ABENDESSEN"};

        // Plane 7 Tage
        for (int day = 0; day < 7; day++) {
            LocalDate date = startDate.plusDays(day);

            for (String mealType : mealTypes) {
                // Wähle zufälliges Rezept passend zur Mahlzeit
                List<Recipe> suitableRecipes = recipes.stream()
                        .filter(r -> r.getCategory().equals(mealType))
                        .collect(Collectors.toList());

                if (!suitableRecipes.isEmpty()) {
                    Recipe randomRecipe = suitableRecipes.get(new Random().nextInt(suitableRecipes.size()));

                    MealPlan mealPlan = new MealPlan();
                    mealPlan.setUser(user);
                    mealPlan.setRecipe(randomRecipe);
                    mealPlan.setDate(date);
                    mealPlan.setMealType(mealType);
                    mealPlan.setPlannedServings(randomRecipe.getServings());

                    weeklyPlan.add(mealPlanRepository.save(mealPlan));
                }
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
            String[] ingredients = recipe.getIngredients().split("\n");

            for (String ingredient : ingredients) {
                ingredient = ingredient.trim();
                if (!ingredient.isEmpty()) {
                    allIngredients.add(ingredient);

                    // Kategorisiere (vereinfacht)
                    String category = categorizeIngredient(ingredient);
                    ingredientsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(ingredient);
                }
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