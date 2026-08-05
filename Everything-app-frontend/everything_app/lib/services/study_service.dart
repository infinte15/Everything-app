import 'dart:convert';
import 'package:flutter/foundation.dart';
import '../config/api_config.dart';
import 'api_service.dart';

/// Alle HTTP-Aufrufe des Study Space.
///
/// Zwei frühere Inkonsistenzen sind hier bereinigt: die URLs waren als Strings einkopiert
/// statt aus [ApiConfig] zu kommen, und der StudyProvider sprach für Notizen am Service
/// vorbei direkt mit dem ApiService. Beides läuft jetzt über einen Weg.
class StudyService {
  final ApiService _api = ApiService();

  // ── gemeinsame Hilfen ────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> _getList(String url, String what) async {
    try {
      final response = await _api.get(url);
      if (_api.isSuccess(response)) {
        final List<dynamic> data = json.decode(response.body);
        return data.cast<Map<String, dynamic>>();
      }
      debugPrint('Failed to fetch $what: ${response.statusCode} ${response.body}');
      return [];
    } catch (e) {
      debugPrint('Error fetching $what: $e');
      return [];
    }
  }

  Future<Map<String, dynamic>?> _send(
    Future<dynamic> Function() call,
    String what,
  ) async {
    try {
      final response = await call();
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to $what: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error during $what: $e');
      return null;
    }
  }

  Future<bool> _delete(String url, String what) async {
    try {
      final response = await _api.delete(url);
      if (!_api.isSuccess(response)) {
        debugPrint('Failed to delete $what: ${response.statusCode}');
      }
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting $what: $e');
      return false;
    }
  }

  // ── Notizen ──────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllNotes() =>
      _getList(ApiConfig.studyNotes, 'notes');

  Future<List<Map<String, dynamic>>> searchNotes(String query) =>
      _getList(ApiConfig.studyNoteSearch(query), 'note search');

  Future<Map<String, dynamic>?> createNote(Map<String, dynamic> note) =>
      _send(() => _api.post(ApiConfig.studyNotes, note), 'create note');

  Future<Map<String, dynamic>?> updateNote(int id, Map<String, dynamic> note) =>
      _send(() => _api.put(ApiConfig.studyNoteById(id), note), 'update note');

  Future<bool> deleteNote(int id) =>
      _delete(ApiConfig.studyNoteById(id), 'note $id');

  /// Haengt eine Seite unter [parentId] (null = Wurzelebene) an Position [position].
  Future<Map<String, dynamic>?> moveNote(int id, int? parentId, int position) =>
      _send(() => _api.put(ApiConfig.studyNoteMove(id),
              {'parentId': parentId, 'position': position}),
          'move note');

  /// Ordnet eine Seite samt Teilbaum einem Modul zu.
  Future<Map<String, dynamic>?> assignNoteCourse(int id, int courseId) =>
      _send(() => _api.put(ApiConfig.studyNoteCourse(id), {'courseId': courseId}),
          'assign note course');

  /// Schreibt die Reihenfolge einer Ebene fest; der Server setzt orderIndex auf die Position.
  Future<bool> reorderNotes(List<int> noteIds) async {
    try {
      final response =
          await _api.put(ApiConfig.studyNotesReorder, {'noteIds': noteIds});
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error reordering notes: $e');
      return false;
    }
  }

  // ── Kurse (im Frontend: Fächer/Module) ───────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllCourses() =>
      _getList(ApiConfig.courses, 'courses');

  Future<Map<String, dynamic>?> createCourse(Map<String, dynamic> course) =>
      _send(() => _api.post(ApiConfig.courses, course), 'create course');

  Future<Map<String, dynamic>?> updateCourse(int id, Map<String, dynamic> course) =>
      _send(() => _api.put(ApiConfig.courseById(id), course), 'update course');

  Future<bool> deleteCourse(int id) =>
      _delete(ApiConfig.courseById(id), 'course $id');

  /// Eigener Endpunkt, weil updateCourse partiell arbeitet: dort wäre „keinem Semester
  /// zugeordnet" nicht von „unverändert" zu unterscheiden. [semesterId] null hebt die
  /// Zuordnung auf.
  Future<Map<String, dynamic>?> assignSemester(int courseId, int? semesterId) =>
      _send(() => _api.put(ApiConfig.courseSemester(courseId), {'semesterId': semesterId}),
          'assign semester');

  // ── Stundenplan ──────────────────────────────────────────────────────────────

  /// Der ganze Stundenplan in einem Request — die Wochenansicht braucht alle Module.
  Future<List<Map<String, dynamic>>> getAllSchedules() =>
      _getList(ApiConfig.courseSchedules, 'schedules');

  Future<Map<String, dynamic>?> createSchedule(
          int courseId, Map<String, dynamic> schedule) =>
      _send(() => _api.post(ApiConfig.schedulesOfCourse(courseId), schedule),
          'create schedule');

  Future<Map<String, dynamic>?> updateSchedule(
          int courseId, int id, Map<String, dynamic> schedule) =>
      _send(() => _api.put(ApiConfig.scheduleById(courseId, id), schedule),
          'update schedule');

  Future<bool> deleteSchedule(int courseId, int id) =>
      _delete(ApiConfig.scheduleById(courseId, id), 'schedule $id');

  // ── Semester ─────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllSemesters() =>
      _getList(ApiConfig.semesters, 'semesters');

  Future<Map<String, dynamic>?> createSemester(Map<String, dynamic> semester) =>
      _send(() => _api.post(ApiConfig.semesters, semester), 'create semester');

  Future<Map<String, dynamic>?> updateSemester(int id, Map<String, dynamic> semester) =>
      _send(() => _api.put(ApiConfig.semesterById(id), semester), 'update semester');

  Future<Map<String, dynamic>?> setCurrentSemester(int id) =>
      _send(() => _api.put(ApiConfig.semesterCurrent(id), {}), 'set current semester');

  Future<bool> reorderSemesters(List<int> semesterIds) async {
    try {
      final response =
          await _api.put(ApiConfig.semesterReorder, {'semesterIds': semesterIds});
      return _api.isSuccess(response);
    } catch (e) {
      debugPrint('Error reordering semesters: $e');
      return false;
    }
  }

  Future<bool> deleteSemester(int id) =>
      _delete(ApiConfig.semesterById(id), 'semester $id');

  // ── Lernziele ──────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getGoals() =>
      _getList(ApiConfig.studyGoals, 'study goals');

  Future<Map<String, dynamic>?> createGoal(Map<String, dynamic> goal) =>
      _send(() => _api.post(ApiConfig.studyGoals, goal), 'create study goal');

  Future<Map<String, dynamic>?> updateGoal(int id, Map<String, dynamic> goal) =>
      _send(() => _api.put(ApiConfig.studyGoalById(id), goal), 'update study goal');

  /// Trägt Lernstunden nach. Eigener Endpunkt statt PUT, weil das Ziel selbst dabei
  /// unverändert bleibt — nur der Fortschritt wächst.
  Future<Map<String, dynamic>?> logGoalHours(int id, double hours) =>
      _send(() => _api.post(ApiConfig.studyGoalLog(id), {'hours': hours}), 'log study hours');

  Future<bool> deleteGoal(int id) =>
      _delete(ApiConfig.studyGoalById(id), 'study goal $id');

  // ── Noten ────────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllGrades() =>
      _getList(ApiConfig.grades, 'grades');

  Future<Map<String, dynamic>?> createGrade(Map<String, dynamic> grade) =>
      _send(() => _api.post(ApiConfig.grades, grade), 'create grade');

  Future<Map<String, dynamic>?> updateGrade(int id, Map<String, dynamic> grade) =>
      _send(() => _api.put(ApiConfig.gradeById(id), grade), 'update grade');

  Future<bool> deleteGrade(int id) =>
      _delete(ApiConfig.gradeById(id), 'grade $id');

  // ── Decks ────────────────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getAllDecks() =>
      _getList(ApiConfig.flashcardDecks, 'decks');

  Future<Map<String, dynamic>?> createDeck(Map<String, dynamic> deck) =>
      _send(() => _api.post(ApiConfig.flashcardDecks, deck), 'create deck');

  Future<bool> deleteDeck(int id) =>
      _delete(ApiConfig.flashcardDeckById(id), 'deck $id');

  /// Die Kennzahlen eines Decks, frisch gezählt. Die Zählerspalten am Deck selbst werden nur
  /// beim Bewerten fortgeschrieben und sind nach dem Anlegen einer Karte veraltet.
  Future<Map<String, dynamic>?> getDeckStats(int deckId) async {
    try {
      final response = await _api.get(ApiConfig.flashcardDeckStats(deckId));
      if (_api.isSuccess(response)) {
        return json.decode(response.body);
      }
      debugPrint('Failed to fetch deck stats: ${response.statusCode} ${response.body}');
      return null;
    } catch (e) {
      debugPrint('Error fetching deck stats: $e');
      return null;
    }
  }

  // ── Karteikarten ─────────────────────────────────────────────────────────────

  /// Alle Karten des Nutzers auf einmal. Vorher wurde pro Deck einzeln geholt, also
  /// 1 + N Requests beim Öffnen des Study Space.
  Future<List<Map<String, dynamic>>> getAllFlashcards() =>
      _getList(ApiConfig.flashcards, 'flashcards');

  Future<List<Map<String, dynamic>>> getCardsByDeck(int deckId) =>
      _getList(ApiConfig.flashcardsByDeck(deckId), 'cards for deck $deckId');

  Future<List<Map<String, dynamic>>> getDueCards() =>
      _getList(ApiConfig.dueFlashcards, 'due cards');

  Future<Map<String, dynamic>?> createFlashcard(Map<String, dynamic> card) =>
      _send(() => _api.post(ApiConfig.flashcards, card), 'create flashcard');

  Future<Map<String, dynamic>?> updateFlashcard(int id, Map<String, dynamic> card) =>
      _send(() => _api.put(ApiConfig.flashcardById(id), card), 'update flashcard');

  /// Die Bewertung geht in den Body, nicht mehr als Query-Parameter: der Server nimmt jetzt
  /// ein Enum entgegen und weist Unbekanntes mit 400 ab, statt es still als MEDIUM zu werten.
  /// [rating] ist einer von AGAIN, HARD, GOOD, EASY.
  Future<Map<String, dynamic>?> reviewFlashcard(int id, String rating) =>
      _send(() => _api.post(ApiConfig.reviewFlashcard(id), {'rating': rating}),
          'review flashcard');

  Future<bool> deleteFlashcard(int id) =>
      _delete(ApiConfig.flashcardById(id), 'flashcard $id');

  /// Das Review-Protokoll ab [since], neueste zuerst. Ohne Angabe die letzten 30 Tage.
  Future<List<Map<String, dynamic>>> getReviews({DateTime? since}) =>
      _getList(ApiConfig.flashcardReviews(since: since), 'reviews');
}
