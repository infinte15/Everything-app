import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'api_service.dart';

class StudyService {
  final ApiService _api = ApiService();

  // ── Courses (= Subjects on frontend) ─────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllCourses() async {
    try {
      final response = await _api.get('/api/study/courses');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching courses: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createCourse(
    Map<String, dynamic> course,
  ) async {
    try {
      final response = await _api.post('/api/study/courses', course);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to create course: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error creating course: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> updateCourse(
    int id,
    Map<String, dynamic> course,
  ) async {
    try {
      final response = await _api.put('/api/study/courses/$id', course);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error updating course: $e');
      return null;
    }
  }

  Future<bool> deleteCourse(int id) async {
    try {
      final response = await _api.delete('/api/study/courses/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting course: $e');
      return false;
    }
  }

  // ── Grades ───────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllGrades() async {
    try {
      final response = await _api.get('/api/study/grades');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching grades: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createGrade(
    Map<String, dynamic> grade,
  ) async {
    try {
      final response = await _api.post('/api/study/grades', grade);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to create grade: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error creating grade: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> updateGrade(
    int id,
    Map<String, dynamic> grade,
  ) async {
    try {
      final response = await _api.put('/api/study/grades/$id', grade);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error updating grade: $e');
      return null;
    }
  }

  Future<bool> deleteGrade(int id) async {
    try {
      final response = await _api.delete('/api/study/grades/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting grade: $e');
      return false;
    }
  }

  // ── Flashcard Decks ──────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllDecks() async {
    try {
      final response = await _api.get('/api/study/decks');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching decks: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createDeck(
    Map<String, dynamic> deck,
  ) async {
    try {
      final response = await _api.post('/api/study/decks', deck);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to create deck: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error creating deck: $e');
      return null;
    }
  }

  Future<bool> deleteDeck(int id) async {
    try {
      final response = await _api.delete('/api/study/decks/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting deck: $e');
      return false;
    }
  }

  // ── Flashcards ───────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getCardsByDeck(int deckId) async {
    try {
      final response = await _api.get('/api/study/flashcards/deck/$deckId');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching cards for deck $deckId: $e');
      return [];
    }
  }

  Future<List<Map<String, dynamic>>> getDueCards() async {
    try {
      final response = await _api.get('/api/study/flashcards/due');
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      return [];
    } catch (e) {
      debugPrint('Error fetching due cards: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> createFlashcard(
    Map<String, dynamic> card,
  ) async {
    try {
      final response = await _api.post('/api/study/flashcards', card);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to create flashcard: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error creating flashcard: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> updateFlashcard(
    int id,
    Map<String, dynamic> card,
  ) async {
    try {
      final response = await _api.put('/api/study/flashcards/$id', card);
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error updating flashcard: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> reviewFlashcard(
    int id,
    String quality,
  ) async {
    try {
      final response = await _api.post(
        '/api/study/flashcards/$id/review?quality=$quality',
        {},
      );
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      return null;
    } catch (e) {
      debugPrint('Error reviewing flashcard: $e');
      return null;
    }
  }

  Future<bool> deleteFlashcard(int id) async {
    try {
      final response = await _api.delete('/api/study/flashcards/$id');
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting flashcard: $e');
      return false;
    }
  }
}
