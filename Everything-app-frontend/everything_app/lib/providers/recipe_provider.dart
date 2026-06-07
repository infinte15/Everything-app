import 'package:flutter/material.dart';
import '../models/recipe.dart';
import '../services/recipe_service.dart';
import '../services/task_service.dart';
import '../models/task.dart';

class RecipeProvider with ChangeNotifier {
  final RecipeService _service = RecipeService();
  final TaskService _taskService = TaskService();
  
  List<Recipe> _recipes = [];
  Map<String, List<int>> _mealPlan = {}; 
  List<Map<String, dynamic>> _shoppingList = [];
  
  bool _isLoading = false;
  String? _error;

  bool get isLoading => _isLoading;
  String? get error => _error;
  List<Recipe> get recipes => _recipes;
  Map<String, List<int>> get mealPlan => _mealPlan;
  List<Map<String, dynamic>> get shoppingList => _shoppingList;

  List<Recipe> get favoriteRecipes => 
      _recipes.where((r) => r.isFavorite).toList();
  
  List<Recipe> recipesByCategory(String category) =>
      _recipes.where((r) => r.category == category).toList();
  
  List<Recipe> get quickRecipes => 
      _recipes.where((r) => r.totalTimeMinutes <= 30).toList();

  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    
    try {
      await Future.wait([
        _loadRecipes(),
        _loadMealPlan(),
        _loadShoppingList(),
      ]);
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Rezeptdaten: $e';
    }
    
    _isLoading = false;
    notifyListeners();
  }

  Future<void> _loadRecipes() async {
    _recipes = await _service.getAllRecipes();
  }

  Future<void> _loadMealPlan() async {
    final start = DateTime.now().subtract(const Duration(days: 7));
    final end = DateTime.now().add(const Duration(days: 14));
    final plan = await _service.getMealPlan(start, end);
    
    _mealPlan.clear();
    for (final meal in plan) {
      final date = meal['date'] as String; // e.g., '2023-10-01'
      final type = meal['mealType'] as String; // e.g., 'BREAKFAST'
      final recipeId = meal['recipeId'] as int?;
      
      if (recipeId != null) {
        final key = '$date-$type';
        if (_mealPlan.containsKey(key)) {
          _mealPlan[key]!.add(recipeId);
        } else {
          _mealPlan[key] = [recipeId];
        }
      }
    }
  }

  Future<void> _loadShoppingList() async {
    final start = DateTime.now();
    final end = DateTime.now().add(const Duration(days: 7));
    final list = await _service.getShoppingList(start, end);
    
    _shoppingList.clear();
    if (list != null && list.containsKey('items')) {
      final items = list['items'] as List<dynamic>;
      for (final item in items) {
        _shoppingList.add({
          'name': item['ingredientName'],
          'checked': false,
          'category': item['category'] ?? 'Allgemein',
          'amount': item['totalAmount'],
          'unit': item['unit']
        });
      }
    }
  }

  Future<bool> addRecipe(Recipe recipe) async {
    try {
      final created = await _service.createRecipe(recipe);
      if (created != null) {
        _recipes.add(created);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Erstellen des Rezepts: $e';
      notifyListeners();
      return false;
    }
  }

  Future<void> toggleFavorite(int recipeId) async {
    final index = _recipes.indexWhere((r) => r.id == recipeId);
    if (index != -1) {
      final recipe = _recipes[index];
      // Optimistic update
      _recipes[index] = Recipe(
        id: recipe.id,
        name: recipe.name,
        description: recipe.description,
        prepTimeMinutes: recipe.prepTimeMinutes,
        cookTimeMinutes: recipe.cookTimeMinutes,
        servings: recipe.servings,
        category: recipe.category,
        ingredients: recipe.ingredients,
        instructions: recipe.instructions,
        calories: recipe.calories,
        protein: recipe.protein,
        carbs: recipe.carbs,
        fat: recipe.fat,
        difficulty: recipe.difficulty,
        imageUrl: recipe.imageUrl,
        tags: recipe.tags,
        isFavorite: !recipe.isFavorite,
        createdAt: recipe.createdAt,
        updatedAt: recipe.updatedAt,
      );
      notifyListeners();
      
      final updated = await _service.toggleFavorite(recipeId);
      if (updated == null) {
        // Revert on failure
        _recipes[index] = recipe;
        notifyListeners();
      }
    }
  }

  Future<void> addToMealPlan(String day, String meal, int recipeId) async {
    final key = '$day-$meal';
    if (_mealPlan.containsKey(key)) {
      _mealPlan[key]!.add(recipeId);
    } else {
      _mealPlan[key] = [recipeId];
    }
    notifyListeners();
    
    // Call backend
    await _service.createMealPlan({
      'date': day,
      'mealType': meal.toUpperCase(),
      'recipeId': recipeId,
    });
    
    // Also create a Task for Meal Prep so the SmartScheduler picks it up!
    try {
      final recipe = _recipes.firstWhere((r) => r.id == recipeId);
      final prepTime = recipe.prepTimeMinutes + recipe.cookTimeMinutes;
      if (prepTime > 0) {
        // We set the deadline to the day of the meal, around evening or noon.
        DateTime deadline = DateTime.parse(day);
        if (meal.toUpperCase() == 'LUNCH') {
          deadline = deadline.add(const Duration(hours: 13)); // 1 PM
        } else if (meal.toUpperCase() == 'DINNER') {
          deadline = deadline.add(const Duration(hours: 19)); // 7 PM
        } else {
          deadline = deadline.add(const Duration(hours: 9)); // Breakfast 9 AM
        }
        
        final prepTask = Task(
          title: 'Kochen: ${recipe.name}',
          description: 'Meal prep for $meal on $day',
          estimatedDurationMinutes: prepTime,
          deadline: deadline,
          category: 'RECIPES',
          spaceType: 'RECIPES',
          priority: 3,
        );
        await _taskService.createTask(prepTask);
      }
    } catch (e) {
      debugPrint('Error syncing Meal Plan to Task: $e');
    }
  }

  Future<void> removeFromMealPlan(String day, String meal, int recipeId) async {
    final key = '$day-$meal';
    if (_mealPlan.containsKey(key)) {
      _mealPlan[key]!.remove(recipeId);
      if (_mealPlan[key]!.isEmpty) {
        _mealPlan.remove(key);
      }
    }
    notifyListeners();
    
    // For deleting, we would need the meal plan ID. 
    // Since we map it by date/meal in frontend, we might need a full reload 
    // or an endpoint that deletes by date+mealType+recipeId.
    // For now, reload the meal plan from backend to sync.
    await _loadMealPlan();
    notifyListeners();
  }

  List<Recipe> getRecipesForMeal(String day, String meal) {
    final key = '$day-$meal';
    final ids = _mealPlan[key] ?? [];
    return _recipes.where((r) => ids.contains(r.id)).toList();
  }

  void toggleShoppingItem(int index) {
    _shoppingList[index]['checked'] = !_shoppingList[index]['checked'];
    notifyListeners();
  }

  Future<void> addToShoppingList(String name, String category) async {
    _shoppingList.add({
      'name': name,
      'checked': false,
      'category': category,
    });
    notifyListeners();
  }

  void clearCheckedItems() {
    _shoppingList.removeWhere((item) => item['checked'] == true);
    notifyListeners();
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}