import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'api_service.dart';

class SportsService {
  final ApiService _api = ApiService();

  // ── Workout Plans ────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllPlans() async {
    try {
      final response = await _api.get('/api/sports/plans');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching workout plans: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createPlan(Map<String, dynamic> plan) async {
    try {
      final response = await _api.post('/api/sports/plans', plan);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error creating plan: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> updatePlan(
    int id,
    Map<String, dynamic> plan,
  ) async {
    try {
      final response = await _api.put('/api/sports/plans/$id', plan);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error updating plan: $e');
      return null;
    }
  }

  Future<bool> deletePlan(int id) async {
    try {
      final response = await _api.delete('/api/sports/plans/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting plan: $e');
      return false;
    }
  }

  // ── Workout Sessions ─────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllSessions() async {
    try {
      final response = await _api.get('/api/sports/sessions');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching sessions: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createSession(
    Map<String, dynamic> session,
  ) async {
    try {
      final response = await _api.post('/api/sports/sessions', session);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to create session: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error creating session: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> updateSession(
    int id,
    Map<String, dynamic> session,
  ) async {
    try {
      final response = await _api.put('/api/sports/sessions/$id', session);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error updating session: $e');
      return null;
    }
  }

  Future<bool> deleteSession(int id) async {
    try {
      final response = await _api.delete('/api/sports/sessions/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting session: $e');
      return false;
    }
  }

  // ── Exercises ────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllExercises() async {
    try {
      final response = await _api.get('/api/sports/exercises');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching exercises: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createExercise(
    Map<String, dynamic> exercise,
  ) async {
    try {
      final response = await _api.post('/api/sports/exercises', exercise);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error creating exercise: $e');
      return null;
    }
  }

  // ── Exercise Sets ────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getSetsBySession(int sessionId) async {
    try {
      final response = await _api.get('/api/sports/sets/session/$sessionId');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching sets: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createSet(Map<String, dynamic> setData) async {
    try {
      final response = await _api.post('/api/sports/sets', setData);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to create set: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error creating set: $e');
      return null;
    }
  }

  // ── Statistics ───────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>?> getProgress({
    String? startDate,
    String? endDate,
  }) async {
    try {
      String url = '/api/sports/stats/progress';
      final params = <String>[];
      if (startDate != null) params.add('startDate=$startDate');
      if (endDate != null) params.add('endDate=$endDate');
      if (params.isNotEmpty) url += '?${params.join('&')}';

      final response = await _api.get(url);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error fetching progress: $e');
      return null;
    }
  }
}
