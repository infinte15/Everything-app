import 'package:flutter/material.dart';
import '../models/recipe.dart';


class RecipeProvider with ChangeNotifier {
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
    // TODO: API Call -> GET /api/recipes
    await Future.delayed(const Duration(milliseconds: 300));
    
    _recipes = [
      Recipe(
        id: 1,
        name: 'Spaghetti Bolognese',
        description: 'Klassische italienische Pasta mit Fleischsauce',
        prepTimeMinutes: 15,
        cookTimeMinutes: 30,
        servings: 4,
        category: 'Pasta',
        ingredients: '''400g Spaghetti
500g Hackfleisch
1 Dose Tomaten
1 Zwiebel
2 Knoblauchzehen
Olivenöl
Salz, Pfeffer, Oregano''',
        instructions: '''1. Zwiebel und Knoblauch fein hacken
2. Olivenöl in Pfanne erhitzen
3. Zwiebel und Knoblauch anbraten
4. Hackfleisch hinzufügen und anbraten
5. Tomaten hinzufügen und würzen
6. 20 Minuten köcheln lassen
7. Spaghetti nach Packungsanweisung kochen
8. Pasta mit Sauce servieren''',
        calories: 650,
        protein: 35,
        carbs: 75,
        fat: 18,
        difficulty: 'MEDIUM',
        tags: 'Pasta, Italienisch, Klassiker',
        isFavorite: true,
      ),
      Recipe(
        id: 2,
        name: 'Hähnchen Curry',
        description: 'Cremiges indisches Curry mit Hähnchenbrust',
        prepTimeMinutes: 10,
        cookTimeMinutes: 25,
        servings: 4,
        category: 'Curry',
        ingredients: '''600g Hähnchenbrust
1 Dose Kokosmilch
2 EL Currypaste
1 Zwiebel
1 Paprika
200g Reis
Ingwer, Knoblauch
Salz, Koriander''',
        instructions: '''1. Hähnchen in Würfel schneiden
2. Zwiebel und Paprika schneiden
3. Hähnchen scharf anbraten
4. Zwiebel und Paprika hinzufügen
5. Currypaste einrühren
6. Kokosmilch hinzufügen
7. 15 Minuten köcheln lassen
8. Mit Reis servieren''',
        calories: 520,
        protein: 42,
        carbs: 48,
        fat: 16,
        difficulty: 'EASY',
        tags: 'Curry, Indisch, Schnell',
        isFavorite: false,
      ),
      Recipe(
        id: 3,
        name: 'Caesar Salad',
        description: 'Frischer Salat mit Hähnchen und Parmesan',
        prepTimeMinutes: 15,
        cookTimeMinutes: 5,
        servings: 2,
        category: 'Salat',
        ingredients: '''2 Hähnchenbrüste
1 Römersalat
50g Parmesan
Croutons
Caesar Dressing
Zitrone
Pfeffer''',
        instructions: '''1. Hähnchen braten und in Streifen schneiden
2. Salat waschen und zerkleinern
3. Parmesan hobeln
4. Alle Zutaten in Schüssel geben
5. Mit Dressing vermengen
6. Mit Croutons garnieren''',
        calories: 380,
        protein: 32,
        carbs: 18,
        fat: 22,
        difficulty: 'EASY',
        tags: 'Salat, Gesund, Schnell',
        isFavorite: true,
      ),
      Recipe(
        id: 4,
        name: 'Avocado Toast',
        description: 'Gesundes Frühstück mit Avocado',
        prepTimeMinutes: 5,
        cookTimeMinutes: 5,
        servings: 1,
        category: 'Frühstück',
        ingredients: '''2 Scheiben Vollkornbrot
1 Avocado
1 Ei
Salz, Pfeffer
Chiliflocken
Zitronensaft''',
        instructions: '''1. Brot toasten
2. Avocado zerdrücken und würzen
3. Ei braten
4. Avocado auf Toast verteilen
5. Ei darauf legen
6. Mit Chiliflocken garnieren''',
        calories: 290,
        protein: 12,
        carbs: 25,
        fat: 18,
        difficulty: 'EASY',
        tags: 'Frühstück, Gesund, Vegetarisch',
        isFavorite: false,
      ),
      Recipe(
        id: 5,
        name: 'Lachs mit Gemüse',
        description: 'Gesunder Lachs mit Ofengemüse',
        prepTimeMinutes: 10,
        cookTimeMinutes: 20,
        servings: 2,
        category: 'Fisch',
        ingredients: '''2 Lachsfilets
1 Zucchini
1 Paprika
200g Brokkoli
Olivenöl
Zitrone
Knoblauch, Kräuter''',
        instructions: '''1. Ofen auf 200°C vorheizen
2. Gemüse schneiden und würzen
3. Gemüse auf Backblech verteilen
4. Lachs würzen und darauf legen
5. 20 Minuten backen
6. Mit Zitrone servieren''',
        calories: 480,
        protein: 38,
        carbs: 12,
        fat: 32,
        difficulty: 'MEDIUM',
        tags: 'Fisch, Gesund, Low-Carb',
        isFavorite: true,
      ),
    ];
  }


  Future<void> _loadMealPlan() async {
    // TODO: API Call -> GET /api/recipes/meal-plan
    await Future.delayed(const Duration(milliseconds: 200));
    
    _mealPlan = {
      'Monday-Breakfast': [4],
      'Monday-Lunch': [3],
      'Monday-Dinner': [1],
      'Tuesday-Breakfast': [4],
      'Tuesday-Lunch': [3],
      'Tuesday-Dinner': [2],
      'Wednesday-Breakfast': [4],
      'Wednesday-Dinner': [5],
    };
  }


  Future<void> _loadShoppingList() async {
    // TODO: API Call -> GET /api/recipes/shopping-list
    await Future.delayed(const Duration(milliseconds: 200));
    
    _shoppingList = [
      {'name': 'Spaghetti (400g)', 'checked': false, 'category': 'Nudeln'},
      {'name': 'Hackfleisch (500g)', 'checked': false, 'category': 'Fleisch'},
      {'name': 'Tomaten (Dose)', 'checked': true, 'category': 'Konserven'},
      {'name': 'Zwiebeln (3 Stück)', 'checked': false, 'category': 'Gemüse'},
      {'name': 'Knoblauch', 'checked': true, 'category': 'Gemüse'},
      {'name': 'Avocado (2 Stück)', 'checked': false, 'category': 'Obst'},
      {'name': 'Lachs (300g)', 'checked': false, 'category': 'Fisch'},
    ];
  }

  
  Future<bool> addRecipe(Recipe recipe) async {
    try {
      // TODO: API Call -> POST /api/recipes
      await Future.delayed(const Duration(milliseconds: 300));
      
      final newRecipe = Recipe(
        id: _recipes.length + 1,
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
        tags: recipe.tags,
        isFavorite: recipe.isFavorite,
        createdAt: DateTime.now(),
      );
      
      _recipes.add(newRecipe);
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Erstellen des Rezepts: $e';
      notifyListeners();
      return false;
    }
  }

  Future<void> toggleFavorite(int recipeId) async {
    final index = _recipes.indexWhere((r) => r.id == recipeId);
    if (index != -1) {
      // Create new instance with toggled favorite
      final recipe = _recipes[index];
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
        isFavorite: !recipe.isFavorite, // Toggle
        createdAt: recipe.createdAt,
        updatedAt: recipe.updatedAt,
      );
      
      notifyListeners();
      
      // TODO: API Call -> PATCH /api/recipes/{id}/favorite
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
    
    // TODO: API Call -> POST /api/recipes/meal-plan
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
    
    // TODO: API Call -> DELETE /api/recipes/meal-plan
  }

  
  List<Recipe> getRecipesForMeal(String day, String meal) {
    final key = '$day-$meal';
    final ids = _mealPlan[key] ?? [];
    return _recipes.where((r) => ids.contains(r.id)).toList();
  }


  void toggleShoppingItem(int index) {
    _shoppingList[index]['checked'] = !_shoppingList[index]['checked'];
    notifyListeners();
    
    // TODO: API Call -> PATCH /api/recipes/shopping-list/{id}
  }

  
  Future<void> addToShoppingList(String name, String category) async {
    _shoppingList.add({
      'name': name,
      'checked': false,
      'category': category,
    });
    
    notifyListeners();
    
    // TODO: API Call -> POST /api/recipes/shopping-list
  }


  void clearCheckedItems() {
    _shoppingList.removeWhere((item) => item['checked'] == true);
    notifyListeners();
    
    // TODO: API Call -> DELETE /api/recipes/shopping-list/checked
  }


  void clearError() {
    _error = null;
    notifyListeners();
  }
}