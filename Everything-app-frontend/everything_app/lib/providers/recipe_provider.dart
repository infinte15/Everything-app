import 'dart:async';

import 'package:flutter/material.dart';

import '../models/meal_plan.dart';
import '../models/meal_type.dart';
import '../models/recipe.dart';
import '../models/recipe_cook_log.dart';
import '../models/recipe_import_preview.dart';
import '../services/recipe_service.dart';

/// Wonach das Kochbuch sortiert.
enum RecipeSort {
  name('Name A–Z'),
  neueste('Zuletzt hinzugefügt'),
  zuletztGekocht('Zuletzt gekocht'),
  beste('Beste Bewertung'),
  schnellste('Schnellste');

  const RecipeSort(this.label);

  final String label;
}

/// Zustand des Rezept-Space: Rezepte, Entdecken-Reihen und Wochenplan.
///
/// **Warum der Wochenplan hierher gehört und die Einkaufsliste nicht.**
/// `completeMealPlan` schreibt serverseitig über das Kochprotokoll in *das
/// Rezept* (`cookCount`, `lastCookedAt`, ggf. die Bewertung). Wochenplan und
/// Rezeptliste können also gar nicht unabhängig voneinander veralten - trennt
/// man sie, zeigt das Kochbuch nach jedem Abhaken eine falsche Zahl, und
/// niemand sieht warum. Die Einkaufsliste dagegen teilt keinen Zustand mit den
/// Rezepten und liegt in [ShoppingListProvider].
class RecipeProvider with ChangeNotifier {
  RecipeProvider({RecipeService? service}) : _service = service ?? RecipeService();

  final RecipeService _service;

  List<Recipe> _recipes = [];
  List<Recipe> _quick = [];
  List<Recipe> _bestRated = [];
  List<Recipe> _notCookedLately = [];
  List<Recipe> _neverCooked = [];

  List<MealPlan> _mealPlan = [];
  DateTime _weekStart = mondayOf(DateTime.now());

  List<Recipe> _searchResults = [];
  String _searchQuery = '';
  String? _categoryFilter;
  bool _onlyFavorites = false;
  RecipeSort _sort = RecipeSort.name;

  final Map<int, List<RecipeCookLog>> _cookLogs = {};

  bool _isLoading = false;
  bool _isSearching = false;
  String? _error;

  List<Recipe> get recipes => _recipes;
  List<Recipe> get quick => _quick;
  List<Recipe> get bestRated => _bestRated;
  List<Recipe> get notCookedLately => _notCookedLately;
  List<Recipe> get neverCooked => _neverCooked;
  List<Recipe> get favorites => _recipes.where((r) => r.isFavorite).toList();

  List<MealPlan> get mealPlan => _mealPlan;
  DateTime get weekStart => _weekStart;
  DateTime get weekEnd => _weekStart.add(const Duration(days: 6));

  List<Recipe> get searchResults => _searchResults;
  String get searchQuery => _searchQuery;
  String? get categoryFilter => _categoryFilter;
  bool get onlyFavorites => _onlyFavorites;
  RecipeSort get sort => _sort;

  bool get isLoading => _isLoading;
  bool get isSearching => _isSearching;
  String? get error => _error;

  /// Der Montag der Woche, in der [day] liegt.
  static DateTime mondayOf(DateTime day) {
    final midnight = DateTime(day.year, day.month, day.day);
    return midnight.subtract(Duration(days: midnight.weekday - 1));
  }

  // ── Abgeleitete Listen ─────────────────────────────────────────────────────

  /// Was das Kochbuch zeigt: alle Rezepte, gefiltert und sortiert.
  ///
  /// *Alle* - der frühere Kochbuch-Tab zeigte nur Favoriten und behauptete
  /// damit, ein Rezept ohne Herz gebe es nicht.
  List<Recipe> get filtered {
    var list = _recipes.where((recipe) {
      if (_onlyFavorites && !recipe.isFavorite) return false;
      if (_categoryFilter != null && recipe.category != _categoryFilter) {
        return false;
      }
      return true;
    }).toList();

    int byDate(DateTime? a, DateTime? b) {
      if (a == null && b == null) return 0;
      if (a == null) return 1;
      if (b == null) return -1;
      return b.compareTo(a);
    }

    switch (_sort) {
      case RecipeSort.name:
        list.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      case RecipeSort.neueste:
        list.sort((a, b) => byDate(a.createdAt, b.createdAt));
      case RecipeSort.zuletztGekocht:
        list.sort((a, b) => byDate(a.lastCookedAt, b.lastCookedAt));
      case RecipeSort.beste:
        list.sort((a, b) => (b.rating ?? 0).compareTo(a.rating ?? 0));
      case RecipeSort.schnellste:
        list.sort((a, b) => a.totalTimeMinutes.compareTo(b.totalTimeMinutes));
    }
    return list;
  }

  /// Das Rezept des Tages.
  ///
  /// Deterministisch aus dem Datum, damit es sich innerhalb eines Tages nicht
  /// bei jedem Neuzeichnen ändert - und am nächsten Tag zuverlässig doch. Die
  /// frühere Fassung nahm "den ersten Favoriten": ein Rezept des Tages, das
  /// sich nie ändert.
  Recipe? recipeOfTheDayFor(DateTime day) {
    if (_recipes.isEmpty) return null;
    final sorted = [..._recipes]
      ..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    final daysSinceEpoch = DateTime(day.year, day.month, day.day)
        .difference(DateTime(1970, 1, 1))
        .inDays;
    return sorted[daysSinceEpoch % sorted.length];
  }

  Recipe? byId(int id) {
    for (final recipe in _recipes) {
      if (recipe.id == id) return recipe;
    }
    return null;
  }

  List<MealPlan> mealsOn(DateTime day, [MealType? type]) => _mealPlan
      .where((meal) =>
          meal.isOn(day) && (type == null || meal.mealType == type))
      .toList();

  List<RecipeCookLog> cookLogOf(int recipeId) => _cookLogs[recipeId] ?? const [];

  // ── Laden ──────────────────────────────────────────────────────────────────

  /// Rezepte, die vier Entdecken-Reihen und die aktuelle Woche.
  Future<void> load() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final results = await Future.wait([
        _service.getAll(),
        _service.getQuick(),
        _service.getBestRated(),
        _service.getNotCookedLately(),
        _service.getNeverCooked(),
        _service.getMealPlan(_weekStart, weekEnd),
      ]);

      _recipes = results[0] as List<Recipe>;
      _quick = results[1] as List<Recipe>;
      _bestRated = results[2] as List<Recipe>;
      _notCookedLately = results[3] as List<Recipe>;
      _neverCooked = results[4] as List<Recipe>;
      _mealPlan = results[5] as List<MealPlan>;
    } catch (e) {
      // Die alten Listen bleiben stehen. Sie zu leeren und ein Banner
      // daneben zu stellen liest sich wie "du hast keine Rezepte", und das ist
      // eine andere Aussage als "der Server war nicht erreichbar".
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> loadWeek(DateTime weekStart) async {
    _weekStart = mondayOf(weekStart);
    notifyListeners();
    try {
      _mealPlan = await _service.getMealPlan(_weekStart, weekEnd);
      _error = null;
    } catch (e) {
      _error = e.toString();
    }
    notifyListeners();
  }

  Future<void> nextWeek() => loadWeek(_weekStart.add(const Duration(days: 7)));

  Future<void> previousWeek() =>
      loadWeek(_weekStart.subtract(const Duration(days: 7)));

  Future<void> thisWeek() => loadWeek(DateTime.now());

  /// Nur die Rezeptlisten neu holen - nach dem Abhaken einer Mahlzeit oder nach
  /// einem Kocheintrag, wenn der Wochenplan schon aktuell ist.
  Future<void> reloadRecipes() async {
    try {
      final results = await Future.wait([
        _service.getAll(),
        _service.getQuick(),
        _service.getBestRated(),
        _service.getNotCookedLately(),
        _service.getNeverCooked(),
      ]);
      _recipes = results[0];
      _quick = results[1];
      _bestRated = results[2];
      _notCookedLately = results[3];
      _neverCooked = results[4];
      notifyListeners();
    } catch (e) {
      _error = e.toString();
      notifyListeners();
    }
  }

  // ── Suchen und Filtern ─────────────────────────────────────────────────────

  /// Sucht über Name, Tags und Zutaten. Die Entprellung liegt in der Oberfläche.
  Future<void> search(String query) async {
    _searchQuery = query;
    if (query.trim().isEmpty) {
      _searchResults = [];
      _isSearching = false;
      notifyListeners();
      return;
    }

    _isSearching = true;
    notifyListeners();
    try {
      _searchResults = await _service.search(query.trim());
      _error = null;
    } catch (e) {
      _error = e.toString();
    } finally {
      _isSearching = false;
      notifyListeners();
    }
  }

  void clearSearch() {
    _searchQuery = '';
    _searchResults = [];
    notifyListeners();
  }

  void setCategoryFilter(String? category) {
    _categoryFilter = _categoryFilter == category ? null : category;
    notifyListeners();
  }

  void setOnlyFavorites(bool value) {
    _onlyFavorites = value;
    notifyListeners();
  }

  void setSort(RecipeSort sort) {
    _sort = sort;
    notifyListeners();
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }

  // ── Rezepte ändern ─────────────────────────────────────────────────────────

  Future<Recipe?> create(Recipe recipe) async {
    try {
      final created = await _service.create(recipe);
      _recipes = [..._recipes, created];
      _error = null;
      notifyListeners();
      // Ein neues Rezept gehört sofort unter "Noch nie ausprobiert".
      unawaited(reloadRecipes());
      return created;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  Future<Recipe?> update(Recipe recipe) async {
    try {
      final updated = await _service.update(recipe);
      _replace(updated);
      _error = null;
      notifyListeners();
      return updated;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  Future<bool> delete(int id) async {
    final removed = byId(id);
    _recipes = _recipes.where((r) => r.id != id).toList();
    // Der Server löscht die Wochenplan-Einträge mit; hier auch, sonst zeigt der
    // Plan bis zum nächsten Laden ein Rezept, das es nicht mehr gibt.
    _mealPlan = _mealPlan.where((m) => m.recipeId != id).toList();
    notifyListeners();

    try {
      await _service.delete(id);
      unawaited(reloadRecipes());
      return true;
    } catch (e) {
      if (removed != null) _recipes = [..._recipes, removed];
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  /// Optimistisch: die Antwort ist vorhersagbar, und ein Herz, das erst nach
  /// einer Netzrunde umspringt, fühlt sich kaputt an.
  Future<void> toggleFavorite(int id) async {
    final before = byId(id);
    if (before == null) return;

    _replace(before.copyWith(isFavorite: !before.isFavorite));
    notifyListeners();

    try {
      _replace(await _service.toggleFavorite(id));
    } catch (e) {
      _replace(before);
      _error = e.toString();
    }
    notifyListeners();
  }

  Future<void> rate(int id, int stars) async {
    final before = byId(id);
    if (before == null) return;

    _replace(before.copyWith(rating: stars));
    notifyListeners();

    try {
      _replace(await _service.rate(id, stars));
      unawaited(reloadRecipes());
    } catch (e) {
      _replace(before);
      _error = e.toString();
    }
    notifyListeners();
  }

  /// **Nicht** optimistisch: `cookCount` und `lastCookedAt` rechnet der Server,
  /// und geraten wäre hier nur der halbe Wert.
  Future<Recipe?> logCooked(
    int id, {
    int? servings,
    int? rating,
    String? note,
  }) async {
    try {
      final updated = await _service.logCooked(
        id,
        servings: servings,
        rating: rating,
        note: note,
      );
      _replace(updated);
      _cookLogs.remove(id);
      _error = null;
      notifyListeners();
      unawaited(reloadRecipes());
      return updated;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  /// Ein einzelnes Rezept frisch vom Server - für die Detailseite.
  ///
  /// Geht über den injizierten Service und nicht über einen eigenen: sonst
  /// hinge jeder Widget-Test der Detailseite am Netz.
  Future<Recipe> fetchById(int id) async {
    final recipe = await _service.getById(id);
    _replace(recipe);
    notifyListeners();
    return recipe;
  }

  Future<List<RecipeCookLog>> loadCookLog(int recipeId) async {
    try {
      final log = await _service.getCookLog(recipeId);
      _cookLogs[recipeId] = log;
      notifyListeners();
      return log;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return const [];
    }
  }

  Future<bool> deleteCookLog(int logId, int recipeId) async {
    try {
      await _service.deleteCookLog(logId);
      await loadCookLog(recipeId);
      unawaited(reloadRecipes());
      return true;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  // ── Wochenplan ─────────────────────────────────────────────────────────────

  Future<bool> planMeal({
    required int recipeId,
    required DateTime date,
    required MealType mealType,
    int? servings,
    String? notes,
    bool scheduleCooking = false,
  }) async {
    try {
      final created = await _service.createMealPlan(
        MealPlan(
          date: date,
          mealType: mealType,
          recipeId: recipeId,
          plannedServings: servings ?? byId(recipeId)?.servings ?? 1,
          notes: notes,
        ),
        scheduleCooking: scheduleCooking,
      );
      _mealPlan = [..._mealPlan, created];
      _error = null;
      notifyListeners();
      return true;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  /// Füllt die freien Plätze der angezeigten Woche.
  Future<int> generateWeek() async {
    try {
      final created = await _service.generateWeek(_weekStart);
      _mealPlan = [..._mealPlan, ...created];
      _error = null;
      notifyListeners();
      return created.length;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return -1;
    }
  }

  /// Hakt eine Mahlzeit ab - und lädt danach die Rezepte nach.
  ///
  /// Genau deswegen liegt der Wochenplan in diesem Provider: der Server legt
  /// dabei einen Kocheintrag an und zieht `cookCount` und `lastCookedAt` am
  /// Rezept hoch. Ohne das Nachladen zeigt das Kochbuch daneben eine veraltete
  /// Zahl.
  Future<bool> completeMeal(int id) async {
    try {
      final updated = await _service.completeMealPlan(id);
      _mealPlan = _mealPlan.map((m) => m.id == id ? updated : m).toList();
      _error = null;
      notifyListeners();
      await reloadRecipes();
      return true;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  Future<bool> deleteMeal(int id) async {
    final before = _mealPlan;
    _mealPlan = _mealPlan.where((m) => m.id != id).toList();
    notifyListeners();

    try {
      await _service.deleteMealPlan(id);
      return true;
    } catch (e) {
      _mealPlan = before;
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  // ── Import ─────────────────────────────────────────────────────────────────

  /// Wirft weiter: die Import-Oberfläche zeigt die Meldung des Servers direkt am
  /// Eingabefeld und nicht als Banner über dem ganzen Space.
  Future<RecipeImportPreview> importFromUrl(String url) =>
      _service.importFromUrl(url);

  Future<RecipeImportPreview> importFromText(String text) =>
      _service.importFromText(text);

  Future<List<RecipeIngredient>> parseIngredients(String text) =>
      _service.parseIngredients(text);

  // ── Intern ─────────────────────────────────────────────────────────────────

  void _replace(Recipe recipe) {
    _recipes = _recipes.map((r) => r.id == recipe.id ? recipe : r).toList();
    _quick = _quick.map((r) => r.id == recipe.id ? recipe : r).toList();
    _bestRated = _bestRated.map((r) => r.id == recipe.id ? recipe : r).toList();
    _notCookedLately =
        _notCookedLately.map((r) => r.id == recipe.id ? recipe : r).toList();
    _neverCooked = _neverCooked.map((r) => r.id == recipe.id ? recipe : r).toList();
    _searchResults =
        _searchResults.map((r) => r.id == recipe.id ? recipe : r).toList();
  }
}
