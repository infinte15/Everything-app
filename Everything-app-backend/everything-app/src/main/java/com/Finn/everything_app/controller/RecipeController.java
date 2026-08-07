package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import com.Finn.everything_app.service.recipe.ChefkochImporter;
import com.Finn.everything_app.service.recipe.IngredientParser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rezepte, Wochenplan, Einkaufsliste.
 *
 * <p>Alle Einzelzugriffe reichen die {@code userId} bis in die Repository-Abfrage durch. Vorher
 * nahmen {@code getRecipeById}, {@code updateRecipe}, {@code toggleFavorite},
 * {@code deleteRecipe} und die drei Wochenplan-Endpunkte nur eine Id entgegen - jeder
 * angemeldete Nutzer kam damit an fremde Daten.
 *
 * <p>Datumsangaben werden von Spring gebunden statt mit {@code LocalDate.parse} von Hand
 * zerlegt: ein kaputtes Datum ist damit ein 400 mit Begruendung und kein 500.
 */
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecipeController {

    private final RecipeService recipeService;
    private final MealPlanService mealPlanService;
    private final ChefkochImporter chefkochImporter;
    private final IngredientParser ingredientParser;

    private final RecipeMapper recipeMapper;
    private final MealPlanMapper mealPlanMapper;

    // ==================== RECIPES ====================


    @GetMapping
    public ResponseEntity<List<RecipeDTO>> getAllRecipes(@CurrentUser Long userId) {
        List<Recipe> recipes = recipeService.getUserRecipes(userId);
        return ResponseEntity.ok(toDTOs(recipes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeDTO> getRecipeById(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        Recipe recipe = recipeService.getRecipeById(userId, id);
        return ResponseEntity.ok(recipeMapper.toDTO(recipe));
    }


    @GetMapping("/category/{category}")
    public ResponseEntity<List<RecipeDTO>> getRecipesByCategory(
            @CurrentUser Long userId,
            @PathVariable String category) {

        return ResponseEntity.ok(toDTOs(recipeService.getRecipesByCategory(userId, category)));
    }


    @GetMapping("/favorites")
    public ResponseEntity<List<RecipeDTO>> getFavoriteRecipes(@CurrentUser Long userId) {
        return ResponseEntity.ok(toDTOs(recipeService.getFavoriteRecipes(userId)));
    }


    @GetMapping("/search")
    public ResponseEntity<List<RecipeDTO>> searchRecipes(
            @CurrentUser Long userId,
            @RequestParam String query) {

        return ResponseEntity.ok(toDTOs(recipeService.searchRecipes(userId, query)));
    }


    @GetMapping("/quick")
    public ResponseEntity<List<RecipeDTO>> getQuickRecipes(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "30") Integer maxMinutes) {

        return ResponseEntity.ok(toDTOs(recipeService.getQuickRecipes(userId, maxMinutes)));
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
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RecipeDTO recipeDTO) {

        Recipe recipe = recipeMapper.toEntity(recipeDTO);
        Recipe updated = recipeService.updateRecipe(userId, id, recipe);

        return ResponseEntity.ok(recipeMapper.toDTO(updated));
    }


    @PutMapping("/{id}/favorite")
    public ResponseEntity<RecipeDTO> toggleFavorite(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        Recipe recipe = recipeService.toggleFavorite(userId, id);
        return ResponseEntity.ok(recipeMapper.toDTO(recipe));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        recipeService.deleteRecipe(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== IMPORT ====================

    /**
     * Liest ein Rezept von einer chefkoch.de-Adresse - ohne es zu speichern.
     *
     * <p>Zwei Schritte statt einem: die App zeigt das Ergebnis samt Warnungen zur Ansicht, und
     * erst die Bestaetigung geht ueber {@code POST /api/recipes}. Ein Import, der still ein
     * halbfalsches Rezept anlegt, ist schlimmer als gar keiner.
     */
    @PostMapping("/import/preview")
    public ResponseEntity<RecipeImportPreviewDTO> previewImport(
            @Valid @RequestBody RecipeImportRequest request) {

        return ResponseEntity.ok(chefkochImporter.importFrom(request.getUrl()));
    }

    /**
     * Zerlegt eingefuegte Zutatenzeilen - reine Vorschau, schreibt nichts.
     *
     * <p>Damit von Hand eingefuegte Zeilen durch denselben Parser laufen wie importierte.
     */
    @PostMapping("/ingredients/parse")
    public ResponseEntity<List<RecipeIngredientDTO>> parseIngredients(
            @Valid @RequestBody IngredientParseRequest request) {

        List<RecipeIngredientDTO> parsed = ingredientParser.parseAll(request.getText()).stream()
                .map(i -> new RecipeIngredientDTO(
                        null, i.amount(), i.unit(), i.name(), i.note(), i.rawText(), null))
                .collect(Collectors.toList());

        return ResponseEntity.ok(parsed);
    }

    // ==================== MEAL PLANNING ====================


    @GetMapping("/meal-plan")
    public ResponseEntity<List<MealPlanDTO>> getMealPlan(
            @CurrentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<MealPlan> mealPlans = mealPlanService.getMealPlanForPeriod(userId, startDate, endDate);
        return ResponseEntity.ok(toMealPlanDTOs(mealPlans));
    }


    @GetMapping("/meal-plan/date/{date}")
    public ResponseEntity<List<MealPlanDTO>> getMealPlanForDate(
            @CurrentUser Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(toMealPlanDTOs(mealPlanService.getMealPlanForDate(userId, date)));
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {

        return ResponseEntity.ok(toMealPlanDTOs(mealPlanService.generateWeeklyPlan(userId, startDate)));
    }


    @PutMapping("/meal-plan/{id}")
    public ResponseEntity<MealPlanDTO> updateMealPlan(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody MealPlanDTO mealPlanDTO) {

        MealPlan mealPlan = mealPlanMapper.toEntity(mealPlanDTO);
        MealPlan updated = mealPlanService.updateMealPlan(userId, id, mealPlan);

        return ResponseEntity.ok(mealPlanMapper.toDTO(updated));
    }


    @PutMapping("/meal-plan/{id}/complete")
    public ResponseEntity<MealPlanDTO> completeMealPlan(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        MealPlan completed = mealPlanService.completeMealPlan(userId, id);
        return ResponseEntity.ok(mealPlanMapper.toDTO(completed));
    }


    @DeleteMapping("/meal-plan/{id}")
    public ResponseEntity<Void> deleteMealPlan(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        mealPlanService.deleteMealPlan(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== SHOPPING LIST ====================


    @GetMapping("/shopping-list")
    public ResponseEntity<ShoppingListDTO> generateShoppingList(
            @CurrentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(mealPlanService.generateShoppingList(userId, startDate, endDate));
    }

    // ==================== NUTRITION STATS ====================

    @GetMapping("/nutrition/daily")
    public ResponseEntity<NutritionStatsDTO> getDailyNutrition(
            @CurrentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(mealPlanService.calculateDailyNutrition(userId, date));
    }

    // ── Hilfen ────────────────────────────────────────────────────────────────────────────

    private List<RecipeDTO> toDTOs(List<Recipe> recipes) {
        return recipes.stream().map(recipeMapper::toDTO).collect(Collectors.toList());
    }

    private List<MealPlanDTO> toMealPlanDTOs(List<MealPlan> mealPlans) {
        return mealPlans.stream().map(mealPlanMapper::toDTO).collect(Collectors.toList());
    }
}
