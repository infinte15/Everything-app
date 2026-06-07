import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'api_service.dart';
import '../models/recipe.dart';

class RecipeService {
  final ApiService _api = ApiService();

  // ── Recipes ──────────────────────────────────────────────────────────────────

  Future<List<Recipe>> getAllRecipes() async {
    try {
      final response = await _api.get('/api/recipes');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.map((json) => Recipe.fromJson(json)).toList();
      }
      throw Exception('Failed to load recipes: ${response.statusCode}');
    } catch (e) {
      debugPrint('Error fetching recipes: $e');
      return [];
    }
  }

  Future<Recipe?> getRecipeById(int id) async {
    try {
      final response = await _api.get('/api/recipes/$id');
      if (_api.isSuccess(response)) {
        return Recipe.fromJson(json.decode(response.body));
      }
      return null;
    } catch (e) {
      debugPrint('Error fetching recipe: $e');
      return null;
    }
  }

  Future<Recipe?> createRecipe(Recipe recipe) async {
    try {
      final response = await _api.post('/api/recipes', recipe.toJson());
      if (_api.isSuccess(response)) {
        return Recipe.fromJson(json.decode(response.body));
      }
      throw Exception('Failed to create recipe: ${response.body}');
    } catch (e) {
      debugPrint('Error creating recipe: $e');
      return null;
    }
  }

  Future<Recipe?> updateRecipe(Recipe recipe) async {
    try {
      final response = await _api.put(
        '/api/recipes/${recipe.id}',
        recipe.toJson(),
      );
      if (_api.isSuccess(response)) {
        return Recipe.fromJson(json.decode(response.body));
      }
      throw Exception('Failed to update recipe: ${response.body}');
    } catch (e) {
      debugPrint('Error updating recipe: $e');
      return null;
    }
  }

  Future<Recipe?> toggleFavorite(int id) async {
    try {
      final response = await _api.put('/api/recipes/$id/favorite', {});
      if (_api.isSuccess(response)) {
        return Recipe.fromJson(json.decode(response.body));
      }
      return null;
    } catch (e) {
      debugPrint('Error toggling favorite: $e');
      return null;
    }
  }

  Future<bool> deleteRecipe(int id) async {
    try {
      final response = await _api.delete('/api/recipes/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting recipe: $e');
      return false;
    }
  }

  // ── Meal Plan ────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getMealPlan(
    DateTime startDate,
    DateTime endDate,
  ) async {
    try {
      final start = startDate.toIso8601String().split('T')[0];
      final end = endDate.toIso8601String().split('T')[0];
      final response = await _api.get(
        '/api/recipes/meal-plan?startDate=$start&endDate=$end',
      );
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching meal plan: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createMealPlan(
    Map<String, dynamic> mealPlanData,
  ) async {
    try {
      final response = await _api.post('/api/recipes/meal-plan', mealPlanData);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error creating meal plan: $e');
      return null;
    }
  }

  Future<bool> deleteMealPlan(int id) async {
    try {
      final response = await _api.delete('/api/recipes/meal-plan/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting meal plan: $e');
      return false;
    }
  }

  // ── Shopping List ────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>?> getShoppingList(
    DateTime startDate,
    DateTime endDate,
  ) async {
    try {
      final start = startDate.toIso8601String().split('T')[0];
      final end = endDate.toIso8601String().split('T')[0];
      final response = await _api.get(
        '/api/recipes/shopping-list?startDate=$start&endDate=$end',
      );
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error fetching shopping list: $e');
      return null;
    }
  }
}
