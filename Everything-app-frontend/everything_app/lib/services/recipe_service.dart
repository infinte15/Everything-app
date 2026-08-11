import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import '../models/meal_plan.dart';
import '../models/recipe.dart';
import '../models/recipe_cook_log.dart';
import '../models/recipe_import_preview.dart';
import '../models/shopping_item.dart';
import 'api_service.dart';

/// Ein Fehler, dessen Text angezeigt werden darf.
///
/// [toString] gibt nur die Meldung zurück und nicht "Exception: …": die Sätze
/// aus dem `ErrorResponse` des Backends ("Bisher werden nur Rezepte von
/// chefkoch.de erkannt.") sind für den Nutzer geschrieben und sollen ihn
/// unverändert erreichen.
class RecipeException implements Exception {
  RecipeException(this.message, [this.statusCode]);

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

/// Rezepte, Wochenplan und Einkaufsliste - ein Controller, ein Service.
///
/// **Jede Methode wirft bei Misserfolg.** Die frühere Fassung fing alles ab und
/// gab still `[]`, `null` oder `false` zurück; deshalb sah man dem Space nicht
/// an, dass er seit dem Backend-Umbau in jeden Aufruf einen Typfehler lief. Ein
/// Fehler, den niemand sehen kann, wird nicht behoben.
class RecipeService {
  RecipeService({ApiService? api}) : _api = api ?? ApiService();

  final ApiService _api;

  // ── Rezepte lesen ──────────────────────────────────────────────────────────

  Future<List<Recipe>> getAll() => _recipeList(ApiConfig.recipes, 'Rezepte');

  Future<Recipe> getById(int id) async {
    final response = await _api.get(ApiConfig.recipeById(id));
    return _recipe(response, 'Rezept konnte nicht geladen werden');
  }

  Future<List<Recipe>> getByCategory(String category) =>
      _recipeList(ApiConfig.recipesByCategory(category), 'Kategorie');

  Future<List<Recipe>> getFavorites() =>
      _recipeList(ApiConfig.favoriteRecipes, 'Favoriten');

  Future<List<Recipe>> search(String query) =>
      _recipeList(ApiConfig.recipeSearch(query), 'Suche');

  Future<List<Recipe>> getQuick({int maxMinutes = 30}) =>
      _recipeList(ApiConfig.quickRecipes(maxMinutes: maxMinutes), 'Schnelle Rezepte');

  Future<List<Recipe>> getBestRated({int minRating = 4}) =>
      _recipeList(ApiConfig.bestRatedRecipes(minRating: minRating), 'Beste Rezepte');

  Future<List<Recipe>> getNotCookedLately({int days = 30}) =>
      _recipeList(ApiConfig.notCookedLately(days: days), 'Lange nicht gekocht');

  Future<List<Recipe>> getNeverCooked() =>
      _recipeList(ApiConfig.neverCookedRecipes, 'Nie gekocht');

  Future<List<Recipe>> getRecentlyCooked() =>
      _recipeList(ApiConfig.recentlyCookedRecipes, 'Zuletzt gekocht');

  // ── Rezepte schreiben ──────────────────────────────────────────────────────

  Future<Recipe> create(Recipe recipe) async {
    final response = await _api.post(ApiConfig.recipes, recipe.toJson());
    return _recipe(response, 'Rezept konnte nicht angelegt werden', expect: 201);
  }

  Future<Recipe> update(Recipe recipe) async {
    final response =
        await _api.put(ApiConfig.recipeById(recipe.id!), recipe.toJson());
    return _recipe(response, 'Rezept konnte nicht geändert werden');
  }

  Future<void> delete(int id) async {
    final response = await _api.delete(ApiConfig.recipeById(id));
    if (response.statusCode != 204) {
      throw _error(response, 'Rezept konnte nicht gelöscht werden');
    }
  }

  Future<Recipe> toggleFavorite(int id) async {
    final response = await _api.put(ApiConfig.recipeFavorite(id), const {});
    return _recipe(response, 'Favorit konnte nicht geändert werden');
  }

  Future<Recipe> rate(int id, int stars) async {
    final response = await _api.put(ApiConfig.recipeRating(id), {'rating': stars});
    return _recipe(response, 'Bewertung konnte nicht gespeichert werden');
  }

  /// Hält fest, dass gekocht wurde. Der Server zählt hoch und gibt das Rezept
  /// mit den neuen Zahlen zurück - deshalb ein Rückgabewert.
  Future<Recipe> logCooked(
    int id, {
    int? servings,
    int? rating,
    String? note,
    DateTime? cookedAt,
  }) async {
    final response = await _api.post(ApiConfig.recipeCooked(id), {
      if (cookedAt != null) 'cookedAt': cookedAt.toIso8601String(),
      'servings': servings,
      'rating': rating,
      'note': note,
    });
    return _recipe(response, 'Eintrag konnte nicht gespeichert werden');
  }

  Future<List<RecipeCookLog>> getCookLog(int recipeId) async {
    final response = await _api.get(ApiConfig.recipeCookLog(recipeId));
    if (response.statusCode == 200) {
      return _decodeList(response)
          .map((json) => RecipeCookLog.fromJson(json))
          .toList();
    }
    throw _error(response, 'Verlauf konnte nicht geladen werden');
  }

  Future<void> deleteCookLog(int logId) async {
    final response = await _api.delete(ApiConfig.cookLogById(logId));
    if (response.statusCode != 204) {
      throw _error(response, 'Eintrag konnte nicht gelöscht werden');
    }
  }

  // ── Import ─────────────────────────────────────────────────────────────────

  Future<RecipeImportPreview> importFromUrl(String url) async {
    final response = await _api.post(ApiConfig.recipeImportPreview, {'url': url});
    if (response.statusCode == 200) {
      return RecipeImportPreview.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Die Seite ließ sich nicht lesen');
  }

  /// Liest eingefügten Text - typisch eine Instagram-Bildunterschrift.
  Future<RecipeImportPreview> importFromText(
    String text, {
    String? sourceName,
    String? sourceUrl,
  }) async {
    final response = await _api.post(ApiConfig.recipeImportText, {
      'text': text,
      'sourceName': sourceName,
      'sourceUrl': sourceUrl,
    });
    if (response.statusCode == 200) {
      return RecipeImportPreview.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Aus dem Text ließ sich kein Rezept lesen');
  }

  /// Zerlegt einen eingefügten Zutatenblock - derselbe Parser wie beim Import.
  Future<List<RecipeIngredient>> parseIngredients(String text) async {
    final response = await _api.post(ApiConfig.parseIngredients, {'text': text});
    if (response.statusCode == 200) {
      return _decodeList(response)
          .map((json) => RecipeIngredient.fromJson(json))
          .toList();
    }
    throw _error(response, 'Zutaten ließen sich nicht zerlegen');
  }

  // ── Wochenplan ─────────────────────────────────────────────────────────────

  Future<List<MealPlan>> getMealPlan(DateTime from, DateTime to) async {
    final response = await _api.get(ApiConfig.mealPlanRange(from, to));
    if (response.statusCode == 200) {
      return _decodeList(response).map((json) => MealPlan.fromJson(json)).toList();
    }
    throw _error(response, 'Wochenplan konnte nicht geladen werden');
  }

  Future<MealPlan> createMealPlan(MealPlan plan,
      {bool scheduleCooking = false}) async {
    final response = await _api.post(
      ApiConfig.mealPlan,
      plan.toJson(scheduleCooking: scheduleCooking),
    );
    if (response.statusCode == 201) {
      return MealPlan.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Mahlzeit konnte nicht geplant werden');
  }

  Future<MealPlan> updateMealPlan(MealPlan plan) async {
    final response =
        await _api.put(ApiConfig.mealPlanById(plan.id!), plan.toJson());
    if (response.statusCode == 200) {
      return MealPlan.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Mahlzeit konnte nicht geändert werden');
  }

  Future<MealPlan> completeMealPlan(int id) async {
    final response = await _api.put(ApiConfig.mealPlanComplete(id), const {});
    if (response.statusCode == 200) {
      return MealPlan.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Mahlzeit konnte nicht abgehakt werden');
  }

  Future<void> deleteMealPlan(int id) async {
    final response = await _api.delete(ApiConfig.mealPlanById(id));
    if (response.statusCode != 204) {
      throw _error(response, 'Mahlzeit konnte nicht entfernt werden');
    }
  }

  /// Füllt die freien Plätze der Woche. Belegte bleiben stehen.
  Future<List<MealPlan>> generateWeek(DateTime weekStart) async {
    final response = await _api.post(ApiConfig.mealPlanGenerate(weekStart), const {});
    if (response.statusCode == 200) {
      return _decodeList(response).map((json) => MealPlan.fromJson(json)).toList();
    }
    throw _error(response, 'Die Woche ließ sich nicht füllen');
  }

  // ── Einkaufsliste ──────────────────────────────────────────────────────────

  Future<List<ShoppingItem>> getShoppingList() async {
    final response = await _api.get(ApiConfig.shoppingList);
    if (response.statusCode == 200) {
      return _decodeList(response)
          .map((json) => ShoppingItem.fromJson(json))
          .toList();
    }
    throw _error(response, 'Einkaufsliste konnte nicht geladen werden');
  }

  Future<ShoppingItem> addShoppingItem(ShoppingItem item) async {
    final response = await _api.post(ApiConfig.shoppingList, item.toJson());
    if (response.statusCode == 201) {
      return ShoppingItem.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Eintrag konnte nicht hinzugefügt werden');
  }

  /// Teiländerung über PATCH - ein Häkchen soll nicht den ganzen Datensatz
  /// zurückschreiben und dabei eine gleichzeitige Änderung überfahren.
  Future<ShoppingItem> updateShoppingItem(
    int id, {
    bool? isChecked,
    String? name,
    double? amount,
    String? unit,
    String? category,
  }) async {
    final response = await _api.patch(ApiConfig.shoppingItemById(id), {
      'isChecked': ?isChecked,
      'name': ?name,
      'amount': ?amount,
      'unit': ?unit,
      'category': ?category,
    });
    if (response.statusCode == 200) {
      return ShoppingItem.fromJson(_decodeMap(response));
    }
    throw _error(response, 'Eintrag konnte nicht geändert werden');
  }

  Future<void> deleteShoppingItem(int id) async {
    final response = await _api.delete(ApiConfig.shoppingItemById(id));
    if (response.statusCode != 204) {
      throw _error(response, 'Eintrag konnte nicht gelöscht werden');
    }
  }

  Future<void> deleteCheckedShoppingItems() async {
    final response = await _api.delete(ApiConfig.shoppingListChecked);
    if (response.statusCode != 204) {
      throw _error(response, 'Erledigte konnten nicht gelöscht werden');
    }
  }

  /// Baut die Wochenplan-Zeilen neu auf. Eigene Einträge und Abgehaktes bleiben.
  Future<List<ShoppingItem>> rebuildFromMealPlan(DateTime from, DateTime to) async {
    final response =
        await _api.post(ApiConfig.shoppingListFromMealPlan(from, to), const {});
    if (response.statusCode == 200) {
      return _decodeList(response)
          .map((json) => ShoppingItem.fromJson(json))
          .toList();
    }
    throw _error(response, 'Die Liste ließ sich nicht aufbauen');
  }

  // ── Gemeinsames ────────────────────────────────────────────────────────────

  Future<List<Recipe>> _recipeList(String url, String was) async {
    final response = await _api.get(url);
    if (response.statusCode == 200) {
      return _decodeList(response).map((json) => Recipe.fromJson(json)).toList();
    }
    throw _error(response, '$was konnten nicht geladen werden');
  }

  Recipe _recipe(http.Response response, String fallback, {int expect = 200}) {
    if (response.statusCode == expect) {
      return Recipe.fromJson(_decodeMap(response));
    }
    throw _error(response, fallback);
  }

  List<Map<String, dynamic>> _decodeList(http.Response response) =>
      (json.decode(utf8.decode(response.bodyBytes)) as List)
          .cast<Map<String, dynamic>>();

  Map<String, dynamic> _decodeMap(http.Response response) =>
      json.decode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;

  /// Nimmt die Meldung des Servers, wenn er eine geschickt hat.
  ///
  /// Der `GlobalExceptionHandler` liefert bei 400 und 404 fertige deutsche
  /// Sätze. Sie hier durch einen eigenen zu ersetzen, wäre ein Rückschritt:
  /// "Die Seite ließ sich nicht lesen" sagt weniger als "Auf dieser Seite steckt
  /// kein Rezept. Ist das die Rezeptseite oder eine Übersicht?".
  RecipeException _error(http.Response response, String fallback) {
    try {
      final body = json.decode(utf8.decode(response.bodyBytes));
      final message = body is Map ? body['message'] : null;
      if (message is String && message.isNotEmpty) {
        return RecipeException(message, response.statusCode);
      }
    } catch (_) {
      // Ein unlesbarer Körper ist kein Grund, den Fehler zu verschlucken.
    }
    return RecipeException(fallback, response.statusCode);
  }
}
