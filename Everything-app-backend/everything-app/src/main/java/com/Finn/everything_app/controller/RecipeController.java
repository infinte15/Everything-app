package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecipeController {

    private final RecipeService recipeService;
    private final MealPlanService mealPlanService;

    private final RecipeMapper recipeMapper;
    private final MealPlanMapper mealPlanMapper;

    // ==================== RECIPES ====================


    @GetMapping
    public ResponseEntity<List<RecipeDTO>> getAllRecipes(@CurrentUser Long userId) {
        List<Recipe> recipes = recipeService.getUserRecipes(userId);
        return ResponseEntity.ok(
                recipes.stream().map(recipeMapper::toDTO).collect(Collectors.toList())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeDTO> getRecipeById(@PathVariable Long id) {
        Recipe recipe = recipeService.getRecipeById(id);
        return ResponseEntity.ok(recipeMapper.toDTO(recipe));
    }


    @GetMapping("/category/{category}")
    public ResponseEntity<List<RecipeDTO>> getRecipesByCategory(
            @CurrentUser Long userId,
            @PathVariable String category) {

        List<Recipe> recipes = recipeService.getRecipesByCategory(userId, category);
        return ResponseEntity.ok(
                recipes.stream().map(recipeMapper::toDTO).collect(Collectors.toList())
        );
    }



    @GetMapping("/favorites")
    public ResponseEntity<List<RecipeDTO>> getFavoriteRecipes(@CurrentUser Long userId) {
        List<Recipe> recipes = recipeService.getFavoriteRecipes(userId);
        return ResponseEntity.ok(
                recipes.stream().map(recipeMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/search")
    public ResponseEntity<List<RecipeDTO>> searchRecipes(
            @CurrentUser Long userId,
            @RequestParam String query) {

        List<Recipe> recipes = recipeService.searchRecipes(userId, query);
        return ResponseEntity.ok(
                recipes.stream().map(recipeMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/quick")
    public ResponseEntity<List<RecipeDTO>> getQuickRecipes(@CurrentUser Long userId) {
        List<Recipe> recipes = recipeService.getQuickRecipes(userId, 30);
        return ResponseEntity.ok(
                recipes.stream().map(recipeMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping
    public ResponseEntity<RecipeDTO> createRecipe(
            @CurrentUser Long userId,
            @Valid @RequestBody RecipeDTO recipeDTO) {

        Recipe recipe = recipeMapper.toEntity(recipeDTO);
        Recipe created = recipeService.createRecipe(userId, recipe);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                recipeMapper.toDTO(created)
        );
    }


    @PutMapping("/{id}")
    public ResponseEntity<RecipeDTO> updateRecipe(
            @PathVariable Long id,
            @Valid @RequestBody RecipeDTO recipeDTO) {

        Recipe recipe = recipeMapper.toEntity(recipeDTO);
        Recipe updated = recipeService.updateRecipe(id, recipe);

        return ResponseEntity.ok(recipeMapper.toDTO(updated));
    }


    @PutMapping("/{id}/favorite")
    public ResponseEntity<RecipeDTO> toggleFavorite(@PathVariable Long id) {
        Recipe recipe = recipeService.toggleFavorite(id);
        return ResponseEntity.ok(recipeMapper.toDTO(recipe));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== MEAL PLANNING ====================


    @GetMapping("/meal-plan")
    public ResponseEntity<List<MealPlanDTO>> getMealPlan(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        List<MealPlan> mealPlans = mealPlanService.getMealPlanForPeriod(userId, start, end);
        return ResponseEntity.ok(
                mealPlans.stream().map(mealPlanMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/meal-plan/date/{date}")
    public ResponseEntity<List<MealPlanDTO>> getMealPlanForDate(
            @CurrentUser Long userId,
            @PathVariable String date) {

        LocalDate targetDate = LocalDate.parse(date);
        List<MealPlan> mealPlans = mealPlanService.getMealPlanForDate(userId, targetDate);

        return ResponseEntity.ok(
                mealPlans.stream().map(mealPlanMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/meal-plan")
    public ResponseEntity<MealPlanDTO> createMealPlan(
            @CurrentUser Long userId,
            @Valid @RequestBody MealPlanDTO mealPlanDTO) {

        MealPlan mealPlan = mealPlanMapper.toEntity(mealPlanDTO);
        MealPlan created = mealPlanService.createMealPlan(userId, mealPlan, mealPlanDTO.getRecipeId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                mealPlanMapper.toDTO(created)
        );
    }


    @PostMapping("/meal-plan/generate")
    public ResponseEntity<List<MealPlanDTO>> generateWeeklyMealPlan(
            @CurrentUser Long userId,
            @RequestParam String startDate) {

        LocalDate start = LocalDate.parse(startDate);
        List<MealPlan> mealPlans = mealPlanService.generateWeeklyPlan(userId, start);

        return ResponseEntity.ok(
                mealPlans.stream().map(mealPlanMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PutMapping("/meal-plan/{id}")
    public ResponseEntity<MealPlanDTO> updateMealPlan(
            @PathVariable Long id,
            @Valid @RequestBody MealPlanDTO mealPlanDTO) {

        MealPlan mealPlan = mealPlanMapper.toEntity(mealPlanDTO);
        MealPlan updated = mealPlanService.updateMealPlan(id, mealPlan);

        return ResponseEntity.ok(mealPlanMapper.toDTO(updated));
    }


    @PutMapping("/meal-plan/{id}/complete")
    public ResponseEntity<MealPlanDTO> completeMealPlan(@PathVariable Long id) {
        MealPlan completed = mealPlanService.completeMealPlan(id);
        return ResponseEntity.ok(mealPlanMapper.toDTO(completed));
    }


    @DeleteMapping("/meal-plan/{id}")
    public ResponseEntity<Void> deleteMealPlan(@PathVariable Long id) {
        mealPlanService.deleteMealPlan(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== SHOPPING LIST ====================


    @GetMapping("/shopping-list")
    public ResponseEntity<ShoppingListDTO> generateShoppingList(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        ShoppingListDTO shoppingList = mealPlanService.generateShoppingList(userId, start, end);
        return ResponseEntity.ok(shoppingList);
    }

    // ==================== NUTRITION STATS ====================

    @GetMapping("/nutrition/daily")
    public ResponseEntity<NutritionStatsDTO> getDailyNutrition(
            @CurrentUser Long userId,
            @RequestParam String date) {

        LocalDate targetDate = LocalDate.parse(date);
        NutritionStatsDTO stats = mealPlanService.calculateDailyNutrition(userId, targetDate);

        return ResponseEntity.ok(stats);
    }
}