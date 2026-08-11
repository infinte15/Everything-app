import 'package:everything_app/models/meal_plan.dart';
import 'package:everything_app/models/meal_type.dart';
import 'package:everything_app/models/recipe.dart';
import 'package:everything_app/models/recipe_cook_log.dart';
import 'package:everything_app/models/recipe_import_preview.dart';
import 'package:everything_app/models/shopping_item.dart';
import 'package:everything_app/services/recipe_service.dart';

/// Ersatz für [RecipeService] in Provider- und Widget-Tests.
///
/// **Jede** Methode, die der Test berührt, muss hier überschrieben sein, und
/// keine davon ruft `super` auf. Eine fehlende Überschreibung geht stillschweigend
/// ins Netz und lässt `testWidgets` ohne Ausgabe hängen, bis der Test-Runner
/// abbricht - das kostet mehr Zeit als jede Fehlermeldung.
class FakeRecipeService extends RecipeService {
  FakeRecipeService({
    List<Recipe>? recipes,
    List<MealPlan>? mealPlan,
    List<ShoppingItem>? shoppingList,
    List<RecipeCookLog>? cookLog,
  })  : recipes = List.of(recipes ?? const []),
        mealPlan = List.of(mealPlan ?? const []),
        shoppingList = List.of(shoppingList ?? const []),
        cookLog = List.of(cookLog ?? const []);

  final List<Recipe> recipes;
  final List<MealPlan> mealPlan;
  final List<ShoppingItem> shoppingList;
  final List<RecipeCookLog> cookLog;

  /// Wenn gesetzt, wirft jeder Aufruf - für die Fehlerpfade.
  bool failAll = false;

  int loadCount = 0;
  int rateCount = 0;
  int favoriteCount = 0;
  int patchCount = 0;
  String? lastSearch;
  int? lastCompletedMealId;
  int nextId = 900;

  void _guard() {
    if (failAll) throw RecipeException('Server nicht erreichbar');
  }

  // ── Lesen ──────────────────────────────────────────────────────────────────

  @override
  Future<List<Recipe>> getAll() async {
    _guard();
    loadCount++;
    return List.of(recipes);
  }

  @override
  Future<Recipe> getById(int id) async {
    _guard();
    return recipes.firstWhere((r) => r.id == id,
        orElse: () => throw RecipeException('Rezept nicht gefunden', 404));
  }

  @override
  Future<List<Recipe>> getByCategory(String category) async {
    _guard();
    return recipes.where((r) => r.category == category).toList();
  }

  @override
  Future<List<Recipe>> getFavorites() async {
    _guard();
    return recipes.where((r) => r.isFavorite).toList();
  }

  @override
  Future<List<Recipe>> search(String query) async {
    _guard();
    lastSearch = query;
    return recipes
        .where((r) => r.name.toLowerCase().contains(query.toLowerCase()))
        .toList();
  }

  @override
  Future<List<Recipe>> getQuick({int maxMinutes = 30}) async {
    _guard();
    return recipes.where((r) => r.totalTimeMinutes <= maxMinutes).toList();
  }

  @override
  Future<List<Recipe>> getBestRated({int minRating = 4}) async {
    _guard();
    return recipes.where((r) => (r.rating ?? 0) >= minRating).toList();
  }

  @override
  Future<List<Recipe>> getNotCookedLately({int days = 30}) async {
    _guard();
    return recipes.where((r) => r.cookCount > 0).toList();
  }

  @override
  Future<List<Recipe>> getNeverCooked() async {
    _guard();
    return recipes.where((r) => r.cookCount == 0).toList();
  }

  @override
  Future<List<Recipe>> getRecentlyCooked() async {
    _guard();
    return recipes.where((r) => r.lastCookedAt != null).toList();
  }

  // ── Schreiben ──────────────────────────────────────────────────────────────

  @override
  Future<Recipe> create(Recipe recipe) async {
    _guard();
    final created = recipe.copyWith(id: nextId++);
    recipes.add(created);
    return created;
  }

  @override
  Future<Recipe> update(Recipe recipe) async {
    _guard();
    final index = recipes.indexWhere((r) => r.id == recipe.id);
    if (index >= 0) recipes[index] = recipe;
    return recipe;
  }

  @override
  Future<void> delete(int id) async {
    _guard();
    recipes.removeWhere((r) => r.id == id);
  }

  @override
  Future<Recipe> toggleFavorite(int id) async {
    _guard();
    favoriteCount++;
    final index = recipes.indexWhere((r) => r.id == id);
    final updated = recipes[index].copyWith(isFavorite: !recipes[index].isFavorite);
    recipes[index] = updated;
    return updated;
  }

  @override
  Future<Recipe> rate(int id, int stars) async {
    _guard();
    rateCount++;
    final index = recipes.indexWhere((r) => r.id == id);
    final updated = recipes[index].copyWith(rating: stars);
    recipes[index] = updated;
    return updated;
  }

  @override
  Future<Recipe> logCooked(
    int id, {
    int? servings,
    int? rating,
    String? note,
    DateTime? cookedAt,
  }) async {
    _guard();
    final index = recipes.indexWhere((r) => r.id == id);
    final updated = recipes[index].copyWith(
      cookCount: recipes[index].cookCount + 1,
      lastCookedAt: cookedAt ?? DateTime.now(),
      rating: rating ?? recipes[index].rating,
    );
    recipes[index] = updated;
    return updated;
  }

  @override
  Future<List<RecipeCookLog>> getCookLog(int recipeId) async {
    _guard();
    return List.of(cookLog);
  }

  @override
  Future<void> deleteCookLog(int logId) async {
    _guard();
    cookLog.removeWhere((entry) => entry.id == logId);
  }

  // ── Import ─────────────────────────────────────────────────────────────────

  @override
  Future<RecipeImportPreview> importFromUrl(String url) async {
    _guard();
    return RecipeImportPreview(recipe: Recipe.blank());
  }

  @override
  Future<RecipeImportPreview> importFromText(
    String text, {
    String? sourceName,
    String? sourceUrl,
  }) async {
    _guard();
    return RecipeImportPreview(recipe: Recipe.blank());
  }

  @override
  Future<List<RecipeIngredient>> parseIngredients(String text) async {
    _guard();
    return text
        .split('\n')
        .where((line) => line.trim().isNotEmpty)
        .map((line) => RecipeIngredient(name: line.trim()))
        .toList();
  }

  // ── Wochenplan ─────────────────────────────────────────────────────────────

  @override
  Future<List<MealPlan>> getMealPlan(DateTime from, DateTime to) async {
    _guard();
    return mealPlan
        .where((meal) =>
            !meal.date.isBefore(from) &&
            !meal.date.isAfter(to.add(const Duration(days: 1))))
        .toList();
  }

  @override
  Future<MealPlan> createMealPlan(MealPlan plan,
      {bool scheduleCooking = false}) async {
    _guard();
    final created = plan.copyWith(id: nextId++);
    mealPlan.add(created);
    return created;
  }

  @override
  Future<MealPlan> updateMealPlan(MealPlan plan) async {
    _guard();
    final index = mealPlan.indexWhere((m) => m.id == plan.id);
    if (index >= 0) mealPlan[index] = plan;
    return plan;
  }

  @override
  Future<MealPlan> completeMealPlan(int id) async {
    _guard();
    lastCompletedMealId = id;
    final index = mealPlan.indexWhere((m) => m.id == id);
    final updated = mealPlan[index].copyWith(isCompleted: true);
    mealPlan[index] = updated;
    return updated;
  }

  @override
  Future<void> deleteMealPlan(int id) async {
    _guard();
    mealPlan.removeWhere((m) => m.id == id);
  }

  @override
  Future<List<MealPlan>> generateWeek(DateTime weekStart) async {
    _guard();
    final created = MealPlan(
      id: nextId++,
      date: weekStart,
      mealType: MealType.abendessen,
      recipeId: recipes.isEmpty ? 1 : recipes.first.id!,
    );
    mealPlan.add(created);
    return [created];
  }

  // ── Einkaufsliste ──────────────────────────────────────────────────────────

  @override
  Future<List<ShoppingItem>> getShoppingList() async {
    _guard();
    return List.of(shoppingList);
  }

  @override
  Future<ShoppingItem> addShoppingItem(ShoppingItem item) async {
    _guard();
    final created = item.copyWith(id: nextId++);
    shoppingList.add(created);
    return created;
  }

  @override
  Future<ShoppingItem> updateShoppingItem(
    int id, {
    bool? isChecked,
    String? name,
    double? amount,
    String? unit,
    String? category,
  }) async {
    _guard();
    patchCount++;
    final index = shoppingList.indexWhere((i) => i.id == id);
    final updated = shoppingList[index].copyWith(
      isChecked: isChecked,
      name: name,
      amount: amount,
      unit: unit,
      category: category,
    );
    shoppingList[index] = updated;
    return updated;
  }

  @override
  Future<void> deleteShoppingItem(int id) async {
    _guard();
    shoppingList.removeWhere((i) => i.id == id);
  }

  @override
  Future<void> deleteCheckedShoppingItems() async {
    _guard();
    shoppingList.removeWhere((i) => i.isChecked);
  }

  @override
  Future<List<ShoppingItem>> rebuildFromMealPlan(
      DateTime from, DateTime to) async {
    _guard();
    return List.of(shoppingList);
  }
}

/// Ein Rezept mit brauchbaren Vorgaben - spart in jedem Test zehn Zeilen.
Recipe testRecipe({
  int id = 1,
  String name = 'Pfannkuchen',
  String category = 'Frühstück',
  int prep = 10,
  int cook = 20,
  int servings = 4,
  bool isFavorite = false,
  int? rating,
  int cookCount = 0,
  DateTime? lastCookedAt,
  DateTime? createdAt,
  String? imageUrl,
  List<RecipeIngredient>? ingredients,
  List<RecipeStep>? steps,
  int? calories,
}) {
  return Recipe(
    id: id,
    name: name,
    category: category,
    prepTimeMinutes: prep,
    cookTimeMinutes: cook,
    servings: servings,
    isFavorite: isFavorite,
    rating: rating,
    cookCount: cookCount,
    lastCookedAt: lastCookedAt,
    createdAt: createdAt,
    imageUrl: imageUrl,
    calories: calories,
    ingredients: ingredients ??
        const [RecipeIngredient(amount: 250, unit: 'g', name: 'Mehl')],
    steps: steps ?? const [RecipeStep(text: 'Alles verrühren.')],
  );
}
