import 'package:flutter/material.dart';
import '../models/study_note.dart';
import '../models/study_folder.dart';
import '../models/study_plan.dart';
import '../models/lesson_plan_entry.dart';
import '../models/study_subject.dart';
import '../models/study_grade.dart';
import '../models/flashcard_deck.dart';
import '../models/task.dart';
import '../utils/anki_scheduler.dart';
import '../services/api_service.dart';
import '../services/study_service.dart';
import '../services/task_service.dart';

class StudyProvider with ChangeNotifier {
  final ApiService _apiService = ApiService();
  final StudyService _studyService = StudyService();
  final TaskService _taskService = TaskService();

  // ── Core data ───────────────────────────────────────────────────────────────
  List<StudyNote> _notes = [];
  List<StudyFolder> _folders = [];
  List<StudyPlanGoal> _studyPlan = [];
  List<LessonPlanEntry> _lessonPlan = [];
  List<StudySubject> _subjects = [];
  List<FlashcardDeck> _flashcardDecks = [];
  List<Flashcard> _flashcards = [];
  List<StudyGrade> _grades = [];

  // ── UI state ─────────────────────────────────────────────────────────────────
  String? _selectedFolderId;
  String? _selectedNoteId;
  bool _isLoading = false;
  String? _error;
  int _activeTab = 0;
  String? _selectedSubjectId;

  // ── Getters ──────────────────────────────────────────────────────────────────
  bool get isLoading => _isLoading;
  String? get error => _error;
  List<StudyNote> get notes => _notes;
  List<StudyFolder> get folders => _folders;
  List<StudyPlanGoal> get studyPlan => _studyPlan;
  List<LessonPlanEntry> get lessonPlan => _lessonPlan;
  List<StudySubject> get subjects => _subjects;
  List<FlashcardDeck> get flashcardDecks => _flashcardDecks;
  List<Flashcard> get flashcards => _flashcards;
  List<StudyGrade> get grades => _grades;

  String? get selectedFolderId => _selectedFolderId;
  String? get selectedNoteId => _selectedNoteId;
  int get activeTab => _activeTab;
  String? get selectedSubjectId => _selectedSubjectId;

  void setActiveTab(int index) {
    _activeTab = index;
    notifyListeners();
  }

  void selectSubject(String? id) {
    _selectedSubjectId = id;
    notifyListeners();
  }

  StudyNote? get selectedNote =>
      _selectedNoteId == null ? null : _notes.firstWhere(
        (n) => n.id.toString() == _selectedNoteId,
        orElse: () => _notes.isEmpty ? StudyNote(title: '', content: '') : _notes.first,
      );

  List<StudyNote> get favoriteNotes => _notes.where((n) => n.isFavorite).toList();

  List<StudyNote> notesByFolder(String? folderId) {
    if (folderId == null) return _notes.where((n) => n.category == null || n.category == '').toList();
    return _notes.where((n) => n.category == folderId).toList();
  }

  List<StudyFolder> rootFolders() => _folders.where((f) => f.parentId == null).toList();
  List<StudyFolder> childFolders(String parentId) =>
      _folders.where((f) => f.parentId == parentId).toList();

  // Kanban grouping
  List<StudyNote> get todoNotes =>
      _notes.where((n) => n.tags != null && n.tags!.contains('status:todo')).toList();
  List<StudyNote> get inProgressNotes =>
      _notes.where((n) => n.tags != null && n.tags!.contains('status:in_progress')).toList();
  List<StudyNote> get doneNotes =>
      _notes.where((n) => n.tags != null && n.tags!.contains('status:done')).toList();

  // ── Load ─────────────────────────────────────────────────────────────────────
  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      await Future.wait([
        _loadFolders(),      // Local only
        _loadNotes(),        // Backend
        _loadStudyPlan(),    // Local only
        _loadLessonPlan(),   // Local only
        _loadSubjects(),     // Backend
        _loadFlashcards(),   // Backend
        _loadGrades(),       // Backend
      ]);
    } catch (e) {
      _error = 'Fehler beim Laden: $e';
    }
    _isLoading = false;
    notifyListeners();
  }

  // Folders are strictly local in current design
  Future<void> _loadFolders() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _folders = []; // Clear mock data. User must create their own folders.
  }

  Future<void> _loadNotes() async {
    try {
      final response = await _apiService.get('/study/notes');
      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response) ?? [];
        _notes = data.map((n) => StudyNote.fromJson(n)).toList();
        _notes.sort((a, b) => (b.id ?? 0).compareTo(a.id ?? 0));
      }
    } catch (e) {
      debugPrint('Error loading notes: $e');
    }
  }

  // Study Plan is strictly local in current design
  Future<void> _loadStudyPlan() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _studyPlan = []; 
  }

  // Lesson Plan is strictly local in current design
  Future<void> _loadLessonPlan() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _lessonPlan = [];
  }

  Future<void> _loadSubjects() async {
    final courses = await _studyService.getAllCourses();
    _subjects = courses.map((c) => StudySubject(
      id: c['id'].toString(),
      name: c['name'] ?? '',
      professor: c['professor'] ?? '',
      creditPoints: c['creditPoints'] ?? 0,
      semester: c['semester'] ?? '',
      colorHex: c['colorHex'] ?? '#3B82F6',
    )).toList();
  }

  Future<void> _loadFlashcards() async {
    final decks = await _studyService.getAllDecks();
    _flashcardDecks = decks.map((d) => FlashcardDeck(
      id: d['id'].toString(),
      title: d['title'] ?? '',
      subjectId: d['courseId']?.toString() ?? '',
      description: d['description'] ?? '',
    )).toList();

    _flashcards = [];
    for (final deck in _flashcardDecks) {
      final cards = await _studyService.getCardsByDeck(int.parse(deck.id));
      _flashcards.addAll(cards.map((c) => Flashcard(
        id: c['id'].toString(),
        deckId: c['deckId'].toString(),
        question: c['front'] ?? '',
        answer: c['back'] ?? '',
        repetitions: c['repetitions'] ?? 0,
        intervalDays: c['intervalDays'] ?? 0,
        learningStep: c['learningStep'] ?? 0,
        ease: (c['easeFactor'] as num?)?.toDouble() ?? 2.5,
        nextReview: c['nextReview'] != null ? DateTime.parse(c['nextReview']) : DateTime.now(),
      )));
    }
  }

  Future<void> _loadGrades() async {
    final gradesData = await _studyService.getAllGrades();
    _grades = gradesData.map((g) => StudyGrade(
      id: g['id'].toString(),
      subjectId: g['courseId']?.toString() ?? '',
      examName: g['examName'] ?? '',
      examType: g['examType'] ?? 'Klausur',
      grade: (g['gradeValue'] as num?)?.toDouble() ?? 0.0,
      weightPercent: (g['weighting'] as num?)?.toInt() ?? 100,
      date: g['date'] != null ? DateTime.parse(g['date']) : DateTime.now(),
    )).toList();
  }

  // ── Navigation ───────────────────────────────────────────────────────────────
  void selectFolder(String? id) {
    _selectedFolderId = id;
    _selectedNoteId = null;
    notifyListeners();
  }

  void selectNote(String? id) {
    _selectedNoteId = id;
    notifyListeners();
  }

  // ── Folder CRUD (Local) ──────────────────────────────────────────────────────
  Future<StudyFolder> addFolder({required String name, String? parentId,
      String emoji = '📁', String? color}) async {
    final folder = StudyFolder(
      id: 'f${DateTime.now().millisecondsSinceEpoch}',
      name: name,
      emoji: emoji,
      color: color,
      parentId: parentId,
    );
    _folders.add(folder);

    if (parentId != null) {
      final idx = _folders.indexWhere((f) => f.id == parentId);
      if (idx != -1) {
        _folders[idx] = _folders[idx].copyWith(
          childIds: [..._folders[idx].childIds, folder.id],
        );
      }
    }
    notifyListeners();
    return folder;
  }

  Future<void> renameFolder(String id, String newName) async {
    final idx = _folders.indexWhere((f) => f.id == id);
    if (idx != -1) {
      _folders[idx] = _folders[idx].copyWith(name: newName);
      notifyListeners();
    }
  }

  Future<void> deleteFolder(String id) async {
    _folders.removeWhere((f) => f.id == id);
    // Remove child references
    for (int i = 0; i < _folders.length; i++) {
      _folders[i] = _folders[i].copyWith(
        childIds: _folders[i].childIds.where((c) => c != id).toList(),
      );
    }
    notifyListeners();
  }

  // ── Note CRUD (API) ──────────────────────────────────────────────────────────
  Future<StudyNote> addNote({required String title, String content = '',
      String? folderId, String? courseName}) async {
    
    final newNoteData = {
      'title': title,
      'content': content,
      'courseName': courseName,
      'category': folderId,
      'tags': 'status:todo',
    };

    try {
      final response = await _apiService.post('/study/notes', newNoteData);
      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        final note = StudyNote.fromJson(data);
        _notes.insert(0, note);

        if (folderId != null) {
          final idx = _folders.indexWhere((f) => f.id == folderId);
          if (idx != -1) {
            _folders[idx] = _folders[idx].copyWith(
              noteIds: [..._folders[idx].noteIds, note.id.toString()],
            );
          }
        }
        notifyListeners();
        return note;
      }
    } catch (e) {
      debugPrint('Error adding note: $e');
    }

    final fallbackNote = StudyNote(
      id: DateTime.now().millisecondsSinceEpoch,
      title: title,
      content: content,
      courseName: courseName,
      category: folderId,
      tags: 'status:todo',
      createdAt: DateTime.now(),
    );
    _notes.insert(0, fallbackNote);
    notifyListeners();
    return fallbackNote;
  }

  Future<void> updateNote(StudyNote note) async {
    try {
      final response = await _apiService.put(
        '/study/notes/${note.id}', 
        note.toJson()
      );
      if (_apiService.isSuccess(response)) {
        final idx = _notes.indexWhere((n) => n.id == note.id);
        if (idx != -1) {
          _notes[idx] = StudyNote.fromJson(_apiService.parseResponse(response));
          notifyListeners();
        }
      }
    } catch (e) {
       debugPrint('Error updating note: $e');
    }
  }

  Future<void> deleteNote(int id) async {
    try {
      final response = await _apiService.delete('/study/notes/$id');
      if (_apiService.isSuccess(response)) {
        _notes.removeWhere((n) => n.id == id);
        notifyListeners();
      }
    } catch (e) {
      debugPrint('Error deleting note: $e');
    }
  }

  Future<void> toggleFavorite(int noteId) async {
    final idx = _notes.indexWhere((n) => n.id == noteId);
    if (idx != -1) {
      final current = _notes[idx];
      _notes[idx] = current.copyWith(isFavorite: !current.isFavorite);
      notifyListeners();
      
      // Update backend
      try {
        await _apiService.put(
          '/study/notes/${current.id}', 
          _notes[idx].toJson()
        );
      } catch (e) {
        debugPrint('Error updating favorite: $e');
        // Revert
        _notes[idx] = current;
        notifyListeners();
      }
    }
  }

  Future<void> updateNoteStatus(int noteId, String status) async {
    final idx = _notes.indexWhere((n) => n.id == noteId);
    if (idx != -1) {
      final note = _notes[idx];
      final currentTags = (note.tags ?? '').split(',')
          .where((t) => !t.startsWith('status:'))
          .toList();
      currentTags.add('status:$status');
      
      _notes[idx] = note.copyWith(tags: currentTags.join(','));
      notifyListeners();
      
      try {
        await _apiService.put(
          '/study/notes/${note.id}', 
          _notes[idx].toJson()
        );
      } catch (e) {
        debugPrint('Error updating note status: $e');
      }
    }
  }

  // ── Study Plan CRUD (Local) ──────────────────────────────────────────────────
  Future<void> addStudyGoal({required String subject, required double goalHours,
      String emoji = '📚', int colorValue = 0xFF6366F1}) async {
    final goal = StudyPlanGoal(
      id: 'sp${DateTime.now().millisecondsSinceEpoch}',
      subject: subject,
      emoji: emoji,
      colorValue: colorValue,
      weeklyGoalHours: goalHours,
      weekStart: _currentWeekStart(),
    );
    _studyPlan.add(goal);
    notifyListeners();

    await _syncStudyGoalToTask(goal);
  }

  Future<void> logStudyHours(String goalId, double hours) async {
    final idx = _studyPlan.indexWhere((g) => g.id == goalId);
    if (idx != -1) {
      _studyPlan[idx] = _studyPlan[idx].copyWith(
        loggedHours: _studyPlan[idx].loggedHours + hours,
      );
      notifyListeners();

      await _syncStudyGoalToTask(_studyPlan[idx]);
    }
  }

  Future<void> deleteStudyGoal(String id) async {
    _studyPlan.removeWhere((g) => g.id == id);
    notifyListeners();
  }

  // Bridges a weekly study goal into the backend Task pipeline so the SmartScheduler
  // (which only knows about Tasks/Habits/Workouts, not local StudyPlanGoal state) can
  // place remaining study time on the calendar. Mirrors RecipeProvider.addToMealPlan.
  //
  // Dedup is query-based (tagged via Task.category), not local-state-based, because
  // _loadStudyPlan() resets _studyPlan to [] on every app restart.
  Future<void> _syncStudyGoalToTask(StudyPlanGoal goal) async {
    try {
      final weekTag = goal.weekStart.toIso8601String().split('T')[0];
      final bridgeTag = 'study-goal:${goal.subject}:$weekTag';
      final remainingHours = (goal.weeklyGoalHours - goal.loggedHours).clamp(0.0, goal.weeklyGoalHours);

      final allTasks = await _taskService.getAllTasks();
      Task? existingTask;
      for (final t in allTasks) {
        if (t.category == bridgeTag) {
          existingTask = t;
          break;
        }
      }

      if (remainingHours <= 0) {
        if (existingTask != null && existingTask.id != null && !existingTask.isCompleted) {
          await _taskService.completeTask(existingTask.id!);
        }
        return;
      }

      final deadline = goal.weekStart.add(const Duration(days: 7));
      final durationMinutes = (remainingHours * 60).round();

      if (existingTask != null) {
        await _taskService.updateTask(existingTask.copyWith(
          estimatedDurationMinutes: durationMinutes,
          deadline: deadline,
        ));
      } else {
        await _taskService.createTask(Task(
          title: 'Lernen: ${goal.subject}',
          description: 'Study goal for ${goal.subject} this week',
          estimatedDurationMinutes: durationMinutes,
          deadline: deadline,
          category: bridgeTag,
          spaceType: 'STUDY',
          priority: 3,
        ));
      }
    } catch (e) {
      debugPrint('Error syncing study goal to task: $e');
    }
  }

  // ── Lesson Plan CRUD (Local) ─────────────────────────────────────────────────
  Future<void> addLesson(LessonPlanEntry entry) async {
    _lessonPlan.add(entry);
    notifyListeners();
  }

  Future<void> updateLesson(LessonPlanEntry entry) async {
    final idx = _lessonPlan.indexWhere((l) => l.id == entry.id);
    if (idx != -1) {
      _lessonPlan[idx] = entry;
      notifyListeners();
    }
  }

  Future<void> deleteLesson(String id) async {
    _lessonPlan.removeWhere((l) => l.id == id);
    notifyListeners();
  }

  List<LessonPlanEntry> lessonsForDay(int dayIndex) =>
      _lessonPlan.where((l) => l.dayIndex == dayIndex).toList()
        ..sort((a, b) => (a.startHour * 60 + a.startMinute)
            .compareTo(b.startHour * 60 + b.startMinute));

  // ── Flashcards (API + Anki-style SRS) ────────────────────────────────────────
  List<Flashcard> cardsForDeck(String deckId) =>
      _flashcards.where((f) => f.deckId == deckId).toList();

  FlashcardDeckStats deckStats(String deckId) {
    final cards = cardsForDeck(deckId);
    int due = 0, newCount = 0, learning = 0, mature = 0;
    for (final c in cards) {
      if (AnkiScheduler.isNew(c)) {
        newCount++;
      } else if (AnkiScheduler.isLearning(c)) {
        learning++;
        if (AnkiScheduler.isDue(c)) due++;
      } else {
        mature++;
        if (AnkiScheduler.isDue(c)) due++;
      }
    }
    return FlashcardDeckStats(
      total: cards.length,
      due: due,
      newCards: newCount,
      learning: learning,
      mature: mature,
    );
  }

  List<Flashcard> get dueFlashcards =>
      _flashcards.where(AnkiScheduler.isDue).toList();

  List<Flashcard> studyQueueForDeck(String deckId) {
    final deck = _flashcardDecks.firstWhere((d) => d.id == deckId);
    final cards = cardsForDeck(deckId);
    final due = cards.where(AnkiScheduler.isDue).toList()
      ..sort((a, b) => a.nextReview.compareTo(b.nextReview));

    final newCards = cards.where(AnkiScheduler.isNew).take(deck.newCardsPerDay).toList();
    final seen = <String>{};
    final queue = <Flashcard>[];
    for (final c in [...due, ...newCards]) {
      if (seen.add(c.id)) queue.add(c);
    }
    return queue;
  }

  void reviewFlashcardWithRating(String cardId, ReviewRating rating) async {
    final idx = _flashcards.indexWhere((f) => f.id == cardId);
    if (idx == -1) return;
    
    // API Call (The API manages SRS state automatically based on 'quality')
    String qualityStr = 'GOOD';
    switch (rating) {
      case ReviewRating.again: qualityStr = 'AGAIN'; break;
      case ReviewRating.hard: qualityStr = 'HARD'; break;
      case ReviewRating.good: qualityStr = 'GOOD'; break;
      case ReviewRating.easy: qualityStr = 'EASY'; break;
    }
    
    final updatedData = await _studyService.reviewFlashcard(int.parse(cardId), qualityStr);
    
    if (updatedData != null) {
      _flashcards[idx] = Flashcard(
        id: updatedData['id'].toString(),
        deckId: updatedData['deckId'].toString(),
        question: updatedData['front'] ?? '',
        answer: updatedData['back'] ?? '',
        repetitions: updatedData['repetitions'] ?? 0,
        intervalDays: updatedData['intervalDays'] ?? 0,
        learningStep: updatedData['learningStep'] ?? 0,
        ease: (updatedData['easeFactor'] as num?)?.toDouble() ?? 2.5,
        nextReview: updatedData['nextReview'] != null 
            ? DateTime.parse(updatedData['nextReview']) 
            : DateTime.now(),
      );
      notifyListeners();
    }
  }

  @Deprecated('Use reviewFlashcardWithRating')
  Future<void> reviewFlashcard(String id, bool correct) async {
    reviewFlashcardWithRating(
      id,
      correct ? ReviewRating.good : ReviewRating.again,
    );
  }

  Future<bool> addFlashcard(Flashcard flashcard) async {
    final created = await _studyService.createFlashcard({
      'deckId': int.parse(flashcard.deckId),
      'front': flashcard.question,
      'back': flashcard.answer,
    });
    
    if (created != null) {
      _flashcards.add(Flashcard(
        id: created['id'].toString(),
        deckId: created['deckId'].toString(),
        question: created['front'] ?? '',
        answer: created['back'] ?? '',
        repetitions: created['repetitions'] ?? 0,
        intervalDays: created['intervalDays'] ?? 0,
        learningStep: created['learningStep'] ?? 0,
        ease: (created['easeFactor'] as num?)?.toDouble() ?? 2.5,
        nextReview: created['nextReview'] != null ? DateTime.parse(created['nextReview']) : DateTime.now(),
      ));
      notifyListeners();
      return true;
    }
    return false;
  }

  void addFlashcardToDeck({
    required String deckId,
    required String question,
    required String answer,
  }) {
    addFlashcard(Flashcard(
      id: '', // Will be assigned by backend
      deckId: deckId,
      question: question.trim(),
      answer: answer.trim(),
      nextReview: DateTime.now(),
    ));
  }

  void updateFlashcard(Flashcard card) async {
    final idx = _flashcards.indexWhere((f) => f.id == card.id);
    if (idx != -1) {
      final updated = await _studyService.updateFlashcard(int.parse(card.id), {
        'front': card.question,
        'back': card.answer,
      });
      if (updated != null) {
        _flashcards[idx] = card; // or use updated data
        notifyListeners();
      }
    }
  }

  void deleteFlashcard(String cardId) async {
    final success = await _studyService.deleteFlashcard(int.parse(cardId));
    if (success) {
      _flashcards.removeWhere((f) => f.id == cardId);
      notifyListeners();
    }
  }

  // ── Grades (API) ─────────────────────────────────────────────────────────────
  List<StudyGrade> gradesForSubject(String subjectId) =>
      _grades.where((g) => g.subjectId == subjectId).toList();

  Future<void> addGrade(StudyGrade grade) async {
    final created = await _studyService.createGrade({
      'courseId': int.parse(grade.subjectId),
      'examName': grade.examName,
      'examType': grade.examType,
      'gradeValue': grade.grade,
      'weighting': grade.weightPercent,
      'date': grade.date.toIso8601String().split('T')[0],
    });
    
    if (created != null) {
      _grades.add(StudyGrade(
        id: created['id'].toString(),
        subjectId: created['courseId'].toString(),
        examName: created['examName'],
        examType: created['examType'] ?? 'Klausur',
        grade: (created['gradeValue'] as num).toDouble(),
        weightPercent: (created['weighting'] as num).toInt(),
        date: DateTime.parse(created['date']),
      ));
      notifyListeners();
    }
  }

  Future<void> updateGrade(StudyGrade grade) async {
    final idx = _grades.indexWhere((g) => g.id == grade.id);
    if (idx != -1) {
      final updated = await _studyService.updateGrade(int.parse(grade.id), {
        'courseId': int.parse(grade.subjectId),
        'examName': grade.examName,
        'examType': grade.examType,
        'gradeValue': grade.grade,
        'weighting': grade.weightPercent,
        'date': grade.date.toIso8601String().split('T')[0],
      });
      if (updated != null) {
        _grades[idx] = grade;
        notifyListeners();
      }
    }
  }

  Future<void> deleteGrade(String gradeId) async {
    final success = await _studyService.deleteGrade(int.parse(gradeId));
    if (success) {
      _grades.removeWhere((g) => g.id == gradeId);
      notifyListeners();
    }
  }

  // ── Subjects (API) ───────────────────────────────────────────────────────────
  Future<void> addSubject({
    required String name,
    required String professor,
    required int creditPoints,
    required String semester,
    required String colorHex,
  }) async {
    final created = await _studyService.createCourse({
      'name': name,
      'professor': professor,
      'creditPoints': creditPoints,
      'semester': semester,
      'colorHex': colorHex,
    });
    
    if (created != null) {
      _subjects.add(StudySubject(
        id: created['id'].toString(),
        name: created['name'],
        professor: created['professor'] ?? '',
        creditPoints: created['creditPoints'] ?? 0,
        semester: created['semester'] ?? '',
        colorHex: created['colorHex'] ?? colorHex,
      ));
      notifyListeners();
    }
  }

  Future<void> deleteSubject(String id) async {
    final success = await _studyService.deleteCourse(int.parse(id));
    if (success) {
      _subjects.removeWhere((s) => s.id == id);
      notifyListeners();
    }
  }

  // ── Flashcard Decks (API) ────────────────────────────────────────────────────
  Future<void> addFlashcardDeck(FlashcardDeck deck) async {
    final created = await _studyService.createDeck({
      'courseId': int.tryParse(deck.subjectId) ?? 0, // Should be valid ID
      'title': deck.title,
      'description': deck.description,
    });
    
    if (created != null) {
      _flashcardDecks.add(FlashcardDeck(
        id: created['id'].toString(),
        title: created['title'],
        subjectId: created['courseId']?.toString() ?? '',
        description: created['description'] ?? '',
      ));
      notifyListeners();
    }
  }

  Future<void> deleteFlashcardDeck(String deckId) async {
    final success = await _studyService.deleteDeck(int.parse(deckId));
    if (success) {
      _flashcardDecks.removeWhere((d) => d.id == deckId);
      _flashcards.removeWhere((f) => f.deckId == deckId);
      notifyListeners();
    }
  }

  FlashcardDeck? deckById(String id) {
    try {
      return _flashcardDecks.firstWhere((d) => d.id == id);
    } catch (_) {
      return null;
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────
  DateTime _currentWeekStart() {
    final now = DateTime.now();
    return now.subtract(Duration(days: now.weekday - 1));
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}