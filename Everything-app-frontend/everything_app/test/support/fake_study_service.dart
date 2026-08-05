import 'package:everything_app/services/study_service.dart';

/// In-memory-Ersatz für [StudyService]. Erweitert den echten Service und überschreibt alle
/// Methoden, die die Tests anfassen; keine ruft super auf, [ApiService] wird also nie
/// angefasst. Eine fehlende Überschreibung ginge stillschweigend ins Netz.
class FakeStudyService extends StudyService {
  // Antworten, in Backend-Form (also mit den JSON-Schlüsseln des DTOs, nicht denen des
  // Dart-Modells — genau daran ist die Anzeige vorher gescheitert).
  List<Map<String, dynamic>> notes = [];
  List<Map<String, dynamic>> courses = [];
  List<Map<String, dynamic>> semesters = [];
  List<Map<String, dynamic>> schedules = [];
  List<Map<String, dynamic>> decks = [];
  List<Map<String, dynamic>> cards = [];
  List<Map<String, dynamic>> grades = [];
  List<Map<String, dynamic>> reviews = [];
  List<Map<String, dynamic>> goals = [];
  Map<String, dynamic>? deckStats;

  // Zähler und Schalter für die Assertions.
  int getAllDecksCallCount = 0;
  int getAllFlashcardsCallCount = 0;
  int getCardsByDeckCallCount = 0;
  int getDeckStatsCallCount = 0;
  int getReviewsCallCount = 0;
  int createNoteCallCount = 0;
  int reviewCallCount = 0;
  String? lastReviewRating;
  Map<String, dynamic>? lastCreatedGrade;
  Map<String, dynamic>? lastCreatedCourse;
  Map<String, dynamic>? lastCreatedNote;
  Map<String, dynamic>? lastUpdatedNote;
  List<int>? lastReorder;
  ({int id, int? parentId, int position})? lastMove;
  Map<String, dynamic>? lastCreatedSchedule;
  ({int courseId, int id})? lastDeletedSchedule;
  Map<String, dynamic>? lastCreatedGoal;
  Map<String, dynamic>? lastUpdatedGoal;
  double? lastLoggedHours;

  bool failCreateNote = false;
  bool failUpdateNote = false;
  bool failCreateGrade = false;
  bool failReview = false;
  bool failReorder = false;
  bool failCreateSchedule = false;
  bool failCreateGoal = false;
  bool failLogHours = false;

  // ── Notizen ────────────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getAllNotes() async => notes;

  @override
  Future<List<Map<String, dynamic>>> searchNotes(String query) async => notes;

  @override
  Future<Map<String, dynamic>?> createNote(Map<String, dynamic> note) async {
    createNoteCallCount++;
    lastCreatedNote = note;
    if (failCreateNote) return null;
    final created = {'orderIndex': 0, ...note, 'id': 100 + createNoteCallCount};
    notes = [...notes, created];
    return created;
  }

  @override
  Future<Map<String, dynamic>?> updateNote(int id, Map<String, dynamic> note) async {
    if (failUpdateNote) return null;
    lastUpdatedNote = note;
    notes = notes.map((n) => n['id'] == id ? {...n, ...note} : n).toList();
    return {...note, 'id': id};
  }

  @override
  Future<bool> deleteNote(int id) async {
    notes = notes.where((n) => n['id'] != id).toList();
    return true;
  }

  @override
  Future<Map<String, dynamic>?> assignNoteCourse(int id, int courseId) async {
    notes = notes
        .map((n) => n['id'] == id ? {...n, 'courseId': courseId} : n)
        .toList();
    return notes.firstWhere((n) => n['id'] == id, orElse: () => {'id': id});
  }

  @override
  Future<Map<String, dynamic>?> moveNote(int id, int? parentId, int position) async {
    lastMove = (id: id, parentId: parentId, position: position);
    notes = notes
        .map((n) => n['id'] == id ? {...n, 'parentId': parentId, 'orderIndex': position} : n)
        .toList();
    return notes.firstWhere((n) => n['id'] == id, orElse: () => {'id': id});
  }

  @override
  Future<bool> reorderNotes(List<int> noteIds) async {
    lastReorder = noteIds;
    if (failReorder) return false;
    return true;
  }

  // ── Kurse ──────────────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getAllCourses() async => courses;

  @override
  Future<Map<String, dynamic>?> createCourse(Map<String, dynamic> course) async {
    lastCreatedCourse = course;
    return {...course, 'id': 1};
  }

  @override
  Future<Map<String, dynamic>?> updateCourse(int id, Map<String, dynamic> course) async =>
      {...course, 'id': id};

  @override
  Future<bool> deleteCourse(int id) async => true;

  // ── Stundenplan ────────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getAllSchedules() async => schedules;

  @override
  Future<Map<String, dynamic>?> createSchedule(
      int courseId, Map<String, dynamic> schedule) async {
    lastCreatedSchedule = {...schedule, 'courseId': courseId};
    if (failCreateSchedule) return null;
    return {...schedule, 'id': 33, 'courseId': courseId, 'courseName': 'Analysis I'};
  }

  @override
  Future<Map<String, dynamic>?> updateSchedule(
          int courseId, int id, Map<String, dynamic> schedule) async =>
      {...schedule, 'id': id, 'courseId': courseId, 'courseName': 'Analysis I'};

  @override
  Future<bool> deleteSchedule(int courseId, int id) async {
    lastDeletedSchedule = (courseId: courseId, id: id);
    return true;
  }

  // ── Semester ───────────────────────────────────────────────────────────────
  // Vollständig überschrieben, auch was kein Test direkt braucht: loadData() ruft
  // getAllSemesters mit, und eine fehlende Überschreibung ginge ins echte Netz. In einem
  // testWidgets-Lauf haengt der Test dann fuer immer, weil im Fake-Async nie eine echte
  // HTTP-Antwort ankommt.

  @override
  Future<List<Map<String, dynamic>>> getAllSemesters() async => semesters;

  @override
  Future<Map<String, dynamic>?> createSemester(Map<String, dynamic> semester) async =>
      {...semester, 'id': 11};

  @override
  Future<Map<String, dynamic>?> updateSemester(int id, Map<String, dynamic> semester) async =>
      {...semester, 'id': id};

  @override
  Future<Map<String, dynamic>?> setCurrentSemester(int id) async =>
      {'id': id, 'isCurrent': true};

  @override
  Future<bool> reorderSemesters(List<int> semesterIds) async => true;

  @override
  Future<bool> deleteSemester(int id) async => true;

  @override
  Future<Map<String, dynamic>?> assignSemester(int courseId, int? semesterId) async =>
      {'id': courseId, 'semesterId': semesterId};

  // ── Lernziele ──────────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getGoals() async => goals;

  @override
  Future<Map<String, dynamic>?> createGoal(Map<String, dynamic> goal) async {
    lastCreatedGoal = goal;
    if (failCreateGoal) return null;
    final created = {...goal, 'id': 61, 'loggedHours': 0.0, 'courseName': 'Analysis I'};
    goals = [...goals, created];
    return created;
  }

  @override
  Future<Map<String, dynamic>?> updateGoal(int id, Map<String, dynamic> goal) async {
    lastUpdatedGoal = goal;
    goals = goals.map((g) => g['id'] == id ? {...g, ...goal} : g).toList();
    return goals.firstWhere((g) => g['id'] == id, orElse: () => {...goal, 'id': id});
  }

  @override
  Future<Map<String, dynamic>?> logGoalHours(int id, double hours) async {
    lastLoggedHours = hours;
    if (failLogHours) return null;
    // Wie der Server: die erfassten Stunden kommen aufsummiert zurueck, nicht als Delta.
    goals = goals.map((g) {
      if (g['id'] != id) return g;
      final logged = ((g['loggedHours'] as num?)?.toDouble() ?? 0) + hours;
      return {...g, 'loggedHours': logged};
    }).toList();
    return goals.firstWhere((g) => g['id'] == id, orElse: () => {'id': id});
  }

  @override
  Future<bool> deleteGoal(int id) async {
    goals = goals.where((g) => g['id'] != id).toList();
    return true;
  }

  // ── Noten ──────────────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getAllGrades() async => grades;

  @override
  Future<Map<String, dynamic>?> createGrade(Map<String, dynamic> grade) async {
    lastCreatedGrade = grade;
    if (failCreateGrade) return null;
    return {...grade, 'id': 7};
  }

  @override
  Future<Map<String, dynamic>?> updateGrade(int id, Map<String, dynamic> grade) async =>
      {...grade, 'id': id};

  @override
  Future<bool> deleteGrade(int id) async => true;

  // ── Decks ──────────────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getAllDecks() async {
    getAllDecksCallCount++;
    return decks;
  }

  @override
  Future<Map<String, dynamic>?> createDeck(Map<String, dynamic> deck) async =>
      {...deck, 'id': 42};

  @override
  Future<bool> deleteDeck(int id) async => true;

  @override
  Future<Map<String, dynamic>?> getDeckStats(int deckId) async {
    getDeckStatsCallCount++;
    return deckStats;
  }

  // ── Karteikarten ───────────────────────────────────────────────────────────

  @override
  Future<List<Map<String, dynamic>>> getAllFlashcards() async {
    getAllFlashcardsCallCount++;
    return cards;
  }

  @override
  Future<List<Map<String, dynamic>>> getCardsByDeck(int deckId) async {
    getCardsByDeckCallCount++;
    return cards.where((c) => c['deckId'] == deckId).toList();
  }

  @override
  Future<List<Map<String, dynamic>>> getDueCards() async => cards;

  @override
  Future<Map<String, dynamic>?> createFlashcard(Map<String, dynamic> card) async =>
      {...card, 'id': 55, 'easeFactor': 2.5, 'repetitionCount': 0, 'intervalDays': 0};

  @override
  Future<Map<String, dynamic>?> updateFlashcard(int id, Map<String, dynamic> card) async =>
      {...card, 'id': id};

  @override
  Future<Map<String, dynamic>?> reviewFlashcard(int id, String rating) async {
    reviewCallCount++;
    lastReviewRating = rating;
    if (failReview) return null;
    return {
      'id': id,
      'deckId': 1,
      'question': 'Frage',
      'answer': 'Antwort',
      'repetitionCount': 1,
      'intervalDays': 1.0,
      'learningStep': 1,
      'easeFactor': 2.5,
      'nextReviewDate': DateTime.now().add(const Duration(days: 1)).toIso8601String(),
    };
  }

  @override
  Future<bool> deleteFlashcard(int id) async => true;

  @override
  Future<List<Map<String, dynamic>>> getReviews({DateTime? since}) async {
    getReviewsCallCount++;
    return reviews;
  }
}
