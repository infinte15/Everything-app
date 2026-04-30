import 'package:flutter/material.dart';
import '../models/study_note.dart';
import '../models/study_folder.dart';
import '../models/study_plan.dart';
import '../models/lesson_plan_entry.dart';
import '../models/study_subject.dart';
import '../models/study_grade.dart';
import '../models/flashcard_deck.dart';

class StudyProvider with ChangeNotifier {
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
        _loadFolders(),
        _loadNotes(),
        _loadStudyPlan(),
        _loadLessonPlan(),
        _loadSubjects(),
        _loadFlashcards(),
        _loadGrades(),
      ]);
    } catch (e) {
      _error = 'Fehler beim Laden: $e';
    }
    _isLoading = false;
    notifyListeners();
  }

  Future<void> _loadFolders() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _folders = [
      StudyFolder(
        id: 'f1',
        name: 'Mathematik',
        emoji: '📐',
        color: '#3B82F6',
        noteIds: ['1'],
      ),
      StudyFolder(
        id: 'f2',
        name: 'Programmierung',
        emoji: '💻',
        color: '#10B981',
        noteIds: ['2'],
      ),
      StudyFolder(
        id: 'f3',
        name: 'Physik',
        emoji: '⚛️',
        color: '#F59E0B',
        noteIds: [],
      ),
      StudyFolder(
        id: 'f2-1',
        name: 'Algorithmen',
        emoji: '🔢',
        color: '#8B5CF6',
        parentId: 'f2',
        noteIds: [],
      ),
    ];
  }

  Future<void> _loadNotes() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _notes = [
      StudyNote(
        id: 1,
        title: 'Analysis – Kapitel 3',
        content: '# Integralrechnung\n\nDer **Hauptsatz** der Differentialrechnung verbindet Differentiation und Integration.\n\n## Formel\n\n∫ f(x) dx = F(b) - F(a)\n\n- [] Übungsaufgaben lösen\n- [] Klausurvorbereitung',
        courseId: 1,
        courseName: 'Mathematik',
        category: 'f1',
        tags: 'Analysis,Integration,status:in_progress',
        isFavorite: true,
        createdAt: DateTime.now().subtract(const Duration(days: 1)),
      ),
      StudyNote(
        id: 2,
        title: 'Java OOP Grundlagen',
        content: '# Objektorientierte Programmierung\n\nKlassen, Objekte, Vererbung, Polymorphismus.\n\n## Prinzipien\n\n- Encapsulation\n- Inheritance\n- Polymorphism\n- Abstraction\n\n- [x] Klassen verstanden\n- [] Vererbung üben',
        courseId: 2,
        courseName: 'Programmierung',
        category: 'f2',
        tags: 'Java,OOP,status:todo',
        isFavorite: false,
        createdAt: DateTime.now().subtract(const Duration(days: 3)),
      ),
      StudyNote(
        id: 3,
        title: 'Klausurvorbereitung Mathe',
        content: '# Klausur Cheat Sheet\n\nWichtige Formeln und Tipps für die Klausur.\n\n- [x] Kapitel 1 wiederholt\n- [x] Kapitel 2 wiederholt\n- [] Kapitel 3 wiederholt',
        courseId: 1,
        courseName: 'Mathematik',
        category: 'f1',
        tags: 'Klausur,status:done',
        isFavorite: false,
        createdAt: DateTime.now().subtract(const Duration(days: 7)),
      ),
    ];
  }

  Future<void> _loadStudyPlan() async {
    await Future.delayed(const Duration(milliseconds: 100));
    final weekStart = _currentWeekStart();
    _studyPlan = [
      StudyPlanGoal(id: 'sp1', subject: 'Mathematik', emoji: '📐',
          colorValue: 0xFF3B82F6, weeklyGoalHours: 8, loggedHours: 3.5, weekStart: weekStart),
      StudyPlanGoal(id: 'sp2', subject: 'Programmierung', emoji: '💻',
          colorValue: 0xFF10B981, weeklyGoalHours: 6, loggedHours: 6.0, weekStart: weekStart),
      StudyPlanGoal(id: 'sp3', subject: 'Physik', emoji: '⚛️',
          colorValue: 0xFFF59E0B, weeklyGoalHours: 4, loggedHours: 1.0, weekStart: weekStart),
      StudyPlanGoal(id: 'sp4', subject: 'Datenbanken', emoji: '🗄️',
          colorValue: 0xFF8B5CF6, weeklyGoalHours: 3, loggedHours: 0.5, weekStart: weekStart),
    ];
  }

  Future<void> _loadLessonPlan() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _lessonPlan = [
      LessonPlanEntry(id: 'lp1', subject: 'Mathematik', room: 'H1.01',
          professor: 'Prof. Dr. Schmidt', dayIndex: 0, startHour: 8,
          durationMinutes: 90, colorValue: 0xFF3B82F6),
      LessonPlanEntry(id: 'lp2', subject: 'Programmierung', room: 'PC-Pool',
          professor: 'Prof. Dr. Müller', dayIndex: 0, startHour: 10,
          durationMinutes: 90, colorValue: 0xFF10B981, type: 'Praktikum'),
      LessonPlanEntry(id: 'lp3', subject: 'Physik', room: 'H2.12',
          professor: 'Prof. Dr. Weber', dayIndex: 1, startHour: 9,
          durationMinutes: 90, colorValue: 0xFFF59E0B),
      LessonPlanEntry(id: 'lp4', subject: 'Datenbanken', room: 'H1.03',
          professor: 'Prof. Dr. Klein', dayIndex: 2, startHour: 14,
          durationMinutes: 90, colorValue: 0xFF8B5CF6),
      LessonPlanEntry(id: 'lp5', subject: 'Algorithmen', room: 'H2.01',
          professor: 'Prof. Dr. Meyer', dayIndex: 3, startHour: 8,
          durationMinutes: 90, colorValue: 0xFFEF4444),
      LessonPlanEntry(id: 'lp6', subject: 'Seminar', room: 'S0.01',
          professor: 'Prof. Dr. Schmidt', dayIndex: 4, startHour: 11,
          durationMinutes: 90, colorValue: 0xFFEC4899, type: 'Seminar'),
    ];
  }

  Future<void> _loadSubjects() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _subjects = [
      StudySubject(id: '1', name: 'Mathematik II', professor: 'Prof. Dr. Schmidt', creditPoints: 6, semester: 'WiSe 2024/25', colorHex: '#3B82F6'),
      StudySubject(id: '2', name: 'Programmierung', professor: 'Prof. Dr. Müller', creditPoints: 8, semester: 'WiSe 2024/25', colorHex: '#10B981'),
      StudySubject(id: '3', name: 'Physik', professor: 'Prof. Dr. Weber', creditPoints: 4, semester: 'WiSe 2024/25', colorHex: '#F59E0B'),
    ];
  }

  Future<void> _loadFlashcards() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _flashcardDecks = [
      FlashcardDeck(id: 'd1', title: 'Programmierung Grundlagen', subjectId: '2', totalCards: 25, toReviewCount: 12, masteryPercentage: 40),
      FlashcardDeck(id: 'd2', title: 'Analysis', subjectId: '1', totalCards: 40, toReviewCount: 5, masteryPercentage: 80),
    ];
    _flashcards = [
      Flashcard(id: 'fc1', deckId: 'd1', question: 'Was ist eine abstrakte Klasse?', answer: 'Eine Klasse, die nicht instanziiert werden kann.', srsLevel: 2, nextReview: DateTime.now().add(const Duration(days: 5))),
      Flashcard(id: 'fc2', deckId: 'd1', question: 'Was ist Polymorphismus?', answer: 'Die Fähigkeit eines Objekts, sich als ein Objekt eines anderen Typs zu verhalten.', srsLevel: 3, nextReview: DateTime.now().add(const Duration(days: 3))),
    ];
  }

  Future<void> _loadGrades() async {
    await Future.delayed(const Duration(milliseconds: 100));
    _grades = [
      StudyGrade(id: 'g1', subjectId: '1', examName: 'Klausur', grade: 2.0, weight: 1.0, date: DateTime.now().subtract(const Duration(days: 30))),
      StudyGrade(id: 'g2', subjectId: '2', examName: 'Projekt', grade: 1.3, weight: 1.0, date: DateTime.now().subtract(const Duration(days: 20))),
      StudyGrade(id: 'g3', subjectId: '3', examName: 'Klausur', grade: 2.7, weight: 1.0, date: DateTime.now().subtract(const Duration(days: 10))),
    ];
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

  // ── Folder CRUD ──────────────────────────────────────────────────────────────
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

  // ── Note CRUD ────────────────────────────────────────────────────────────────
  Future<StudyNote> addNote({required String title, String content = '',
      String? folderId, String? courseName}) async {
    final note = StudyNote(
      id: DateTime.now().millisecondsSinceEpoch,
      title: title,
      content: content,
      courseName: courseName,
      category: folderId,
      tags: 'status:todo',
      createdAt: DateTime.now(),
    );
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

  Future<void> updateNote(StudyNote note) async {
    final idx = _notes.indexWhere((n) => n.id == note.id);
    if (idx != -1) {
      _notes[idx] = note.copyWith(updatedAt: DateTime.now());
      notifyListeners();
    }
  }

  Future<void> deleteNote(int id) async {
    _notes.removeWhere((n) => n.id == id);
    notifyListeners();
  }

  Future<void> toggleFavorite(int noteId) async {
    final idx = _notes.indexWhere((n) => n.id == noteId);
    if (idx != -1) {
      _notes[idx] = _notes[idx].copyWith(isFavorite: !_notes[idx].isFavorite);
      notifyListeners();
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
    }
  }

  // ── Study Plan CRUD ──────────────────────────────────────────────────────────
  Future<void> addStudyGoal({required String subject, required double goalHours,
      String emoji = '📚', int colorValue = 0xFF6366F1}) async {
    _studyPlan.add(StudyPlanGoal(
      id: 'sp${DateTime.now().millisecondsSinceEpoch}',
      subject: subject,
      emoji: emoji,
      colorValue: colorValue,
      weeklyGoalHours: goalHours,
      weekStart: _currentWeekStart(),
    ));
    notifyListeners();
  }

  Future<void> logStudyHours(String goalId, double hours) async {
    final idx = _studyPlan.indexWhere((g) => g.id == goalId);
    if (idx != -1) {
      _studyPlan[idx] = _studyPlan[idx].copyWith(
        loggedHours: _studyPlan[idx].loggedHours + hours,
      );
      notifyListeners();
    }
  }

  Future<void> deleteStudyGoal(String id) async {
    _studyPlan.removeWhere((g) => g.id == id);
    notifyListeners();
  }

  // ── Lesson Plan CRUD ─────────────────────────────────────────────────────────
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

  // ── Flashcards ───────────────────────────────────────────────────────────────
  List<Flashcard> get dueFlashcards {
    final now = DateTime.now();
    return _flashcards.where((f) {
      return f.nextReview.isBefore(now);
    }).toList();
  }

  Future<void> reviewFlashcard(String id, bool correct) async {
    final idx = _flashcards.indexWhere((f) => f.id == id);
    if (idx != -1) {
      final card = _flashcards[idx];
      final newSrsLevel = correct ? card.srsLevel + 1 : 0;
      final days = correct ? (newSrsLevel * 2).clamp(1, 30) : 1;
      _flashcards[idx] = Flashcard(
        id: card.id,
        deckId: card.deckId,
        question: card.question,
        answer: card.answer,
        srsLevel: newSrsLevel,
        nextReview: DateTime.now().add(Duration(days: days)),
      );
      notifyListeners();
    }
  }

  Future<bool> addFlashcard(Flashcard flashcard) async {
    try {
      _flashcards.add(flashcard);
      notifyListeners();
      return true;
    } catch (_) {
      return false;
    }
  }

  // ── Grades ───────────────────────────────────────────────────────────────────
  double get gpa {
    if (_grades.isEmpty) return 0.0;
    double weighted = 0;
    int credits = 0;
    for (final g in _grades) {
      final subject = _subjects.firstWhere((s) => s.id == g.subjectId, orElse: () => StudySubject(id: '', name: ''));
      weighted += g.grade * subject.creditPoints * g.weight;
      credits += (subject.creditPoints * g.weight).round();
    }
    return credits > 0 ? weighted / credits : 0.0;
  }

  int get totalCredits =>
      _grades.fold<int>(0, (s, g) {
        final subject = _subjects.firstWhere((sub) => sub.id == g.subjectId, orElse: () => StudySubject(id: '', name: ''));
        return s + subject.creditPoints;
      });

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