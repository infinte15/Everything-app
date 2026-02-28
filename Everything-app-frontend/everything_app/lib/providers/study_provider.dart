import 'package:flutter/material.dart';
import '../models/study_note.dart';

class StudyProvider with ChangeNotifier {
 
  List<StudyNote> _notes = [];
  List<Map<String, dynamic>> _courses = [];
  List<Map<String, dynamic>> _flashcards = [];
  List<Map<String, dynamic>> _grades = [];
  List<Map<String, dynamic>> _timetable = [];
  
  bool _isLoading = false;
  String? _error;

 
  bool get isLoading => _isLoading;
  String? get error => _error;
  List<StudyNote> get notes => _notes;
  List<Map<String, dynamic>> get courses => _courses;
  List<Map<String, dynamic>> get flashcards => _flashcards;
  List<Map<String, dynamic>> get grades => _grades;
  List<Map<String, dynamic>> get timetable => _timetable;


  List<StudyNote> get favoriteNotes => 
      _notes.where((n) => n.isFavorite).toList();
  
  List<StudyNote> notesByCategory(String category) =>
      _notes.where((n) => n.category == category).toList();
  
  List<StudyNote> notesByCourse(int courseId) =>
      _notes.where((n) => n.courseId == courseId).toList();


  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await Future.wait([
        _loadNotes(),
        _loadCourses(),
        _loadFlashcards(),
        _loadGrades(),
        _loadTimetable(),
      ]);
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Studiendaten: $e';
    }

    _isLoading = false;
    notifyListeners();
  }


  Future<void> _loadNotes() async {
    // TODO: API Call -> GET /api/study/notes
    // Für jetzt Mock-Daten
    await Future.delayed(const Duration(milliseconds: 300));
    _notes = [
      StudyNote(
        id: 1,
        title: 'Vorlesung Analysis - Kapitel 3',
        content: 'Integralrechnung, Hauptsatz der Differentialrechnung...',
        courseId: 1,
        courseName: 'Mathematik',
        category: 'Vorlesung',
        tags: 'Analysis, Integration, Mathematik',
        isFavorite: true,
        createdAt: DateTime.now().subtract(const Duration(days: 1)),
      ),
      StudyNote(
        id: 2,
        title: 'Java OOP Grundlagen',
        content: 'Klassen, Objekte, Vererbung, Polymorphismus...',
        courseId: 2,
        courseName: 'Programmierung',
        category: 'Tutorial',
        tags: 'Java, OOP, Programmierung',
        isFavorite: false,
        createdAt: DateTime.now().subtract(const Duration(days: 3)),
      ),
    ];
  }

 
  Future<void> _loadCourses() async {
    // TODO: API Call -> GET /api/study/courses
    await Future.delayed(const Duration(milliseconds: 200));
    _courses = [
      {
        'id': 1,
        'name': 'Mathematik II',
        'code': 'MATH201',
        'professor': 'Prof. Dr. Schmidt',
        'credits': 6,
        'semester': 'WiSe 2024/25',
      },
      {
        'id': 2,
        'name': 'Programmierung',
        'code': 'CS101',
        'professor': 'Prof. Dr. Müller',
        'credits': 8,
        'semester': 'WiSe 2024/25',
      },
      {
        'id': 3,
        'name': 'Physik',
        'code': 'PHYS101',
        'professor': 'Prof. Dr. Weber',
        'credits': 4,
        'semester': 'WiSe 2024/25',
      },
    ];
  }


  Future<void> _loadFlashcards() async {
    // TODO: API Call -> GET /api/study/flashcards
    await Future.delayed(const Duration(milliseconds: 200));
    _flashcards = [
      {
        'id': 1,
        'question': 'Was ist eine abstrakte Klasse?',
        'answer': 'Eine Klasse, die nicht instanziiert werden kann und als Vorlage für Unterklassen dient.',
        'courseId': 2,
        'difficulty': 'medium',
        'lastReviewed': DateTime.now().subtract(const Duration(days: 2)),
        'nextReview': DateTime.now().add(const Duration(days: 5)),
        'reviewCount': 3,
      },
      {
        'id': 2,
        'question': 'Was ist Polymorphismus?',
        'answer': 'Die Fähigkeit eines Objekts, sich als ein Objekt eines anderen Typs zu verhalten.',
        'courseId': 2,
        'difficulty': 'hard',
        'lastReviewed': DateTime.now().subtract(const Duration(days: 1)),
        'nextReview': DateTime.now().add(const Duration(days: 3)),
        'reviewCount': 5,
      },
      {
        'id': 3,
        'question': 'Was ist ein Interface?',
        'answer': 'Ein Vertrag, der definiert, welche Methoden eine Klasse implementieren muss.',
        'courseId': 2,
        'difficulty': 'medium',
        'lastReviewed': DateTime.now().subtract(const Duration(days: 4)),
        'nextReview': DateTime.now().add(const Duration(days: 7)),
        'reviewCount': 2,
      },
    ];
  }


  Future<void> _loadGrades() async {
    // TODO: API Call -> GET /api/study/grades
    await Future.delayed(const Duration(milliseconds: 200));
    _grades = [
      {
        'id': 1,
        'courseId': 1,
        'courseName': 'Mathematik',
        'grade': 2.0,
        'credits': 6,
        'type': 'Klausur',
        'date': DateTime.now().subtract(const Duration(days: 30)),
        'weight': 1.0,
      },
      {
        'id': 2,
        'courseId': 2,
        'courseName': 'Programmierung',
        'grade': 1.3,
        'credits': 8,
        'type': 'Projekt',
        'date': DateTime.now().subtract(const Duration(days: 15)),
        'weight': 1.0,
      },
      {
        'id': 3,
        'courseId': 3,
        'courseName': 'Physik',
        'grade': 2.7,
        'credits': 4,
        'type': 'Klausur',
        'date': DateTime.now().subtract(const Duration(days: 45)),
        'weight': 1.0,
      },
    ];
  }

  Future<void> _loadTimetable() async {
    // TODO: API Call -> GET /api/study/timetable
    await Future.delayed(const Duration(milliseconds: 200));
    _timetable = [
      {
        'id': 1,
        'day': 'Monday',
        'startTime': '08:00',
        'endTime': '09:30',
        'subject': 'Mathematik',
        'room': 'H1.01',
        'professor': 'Prof. Dr. Schmidt',
        'type': 'Vorlesung',
      },
      {
        'id': 2,
        'day': 'Monday',
        'startTime': '10:00',
        'endTime': '11:30',
        'subject': 'Programmierung',
        'room': 'PC-Pool',
        'professor': 'Prof. Dr. Müller',
        'type': 'Praktikum',
      },
      {
        'id': 3,
        'day': 'Tuesday',
        'startTime': '09:00',
        'endTime': '10:30',
        'subject': 'Physik',
        'room': 'H2.12',
        'professor': 'Prof. Dr. Weber',
        'type': 'Vorlesung',
      },
      {
        'id': 4,
        'day': 'Wednesday',
        'startTime': '14:00',
        'endTime': '15:30',
        'subject': 'Datenbanken',
        'room': 'H1.03',
        'professor': 'Prof. Dr. Klein',
        'type': 'Vorlesung',
      },
      {
        'id': 5,
        'day': 'Thursday',
        'startTime': '08:00',
        'endTime': '09:30',
        'subject': 'Algorithmen',
        'room': 'H2.01',
        'professor': 'Prof. Dr. Meyer',
        'type': 'Vorlesung',
      },
      {
        'id': 6,
        'day': 'Friday',
        'startTime': '11:00',
        'endTime': '12:30',
        'subject': 'Seminar',
        'room': 'S0.01',
        'professor': 'Prof. Dr. Schmidt',
        'type': 'Seminar',
      },
    ];
  }

  Future<bool> addNote(StudyNote note) async {
    try {
      // TODO: API Call -> POST /api/study/notes
      await Future.delayed(const Duration(milliseconds: 300));
      
      final newNote = note.copyWith(
        id: _notes.length + 1,
        createdAt: DateTime.now(),
      );
      
      _notes.insert(0, newNote);
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Erstellen der Notiz: $e';
      notifyListeners();
      return false;
    }
  }


  Future<bool> updateNote(StudyNote note) async {
    try {
      // TODO: API Call -> PUT /api/study/notes/{id}
      await Future.delayed(const Duration(milliseconds: 300));
      
      final index = _notes.indexWhere((n) => n.id == note.id);
      if (index != -1) {
        _notes[index] = note.copyWith(updatedAt: DateTime.now());
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Aktualisieren der Notiz: $e';
      notifyListeners();
      return false;
    }
  }


  Future<bool> deleteNote(int id) async {
    try {
      // TODO: API Call -> DELETE /api/study/notes/{id}
      await Future.delayed(const Duration(milliseconds: 300));
      
      _notes.removeWhere((n) => n.id == id);
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Löschen der Notiz: $e';
      notifyListeners();
      return false;
    }
  }

  Future<void> toggleFavorite(int noteId) async {
    final index = _notes.indexWhere((n) => n.id == noteId);
    if (index != -1) {
      _notes[index] = _notes[index].copyWith(
        isFavorite: !_notes[index].isFavorite,
      );
      notifyListeners();
      
      // TODO: API Call -> PATCH /api/study/notes/{id}/favorite
    }
  }


  Future<bool> addFlashcard(Map<String, dynamic> flashcard) async {
    try {
      // TODO: API Call -> POST /api/study/flashcards
      await Future.delayed(const Duration(milliseconds: 300));
      
      _flashcards.add({
        ...flashcard,
        'id': _flashcards.length + 1,
        'reviewCount': 0,
        'lastReviewed': null,
        'nextReview': DateTime.now().add(const Duration(days: 1)),
      });
      
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Erstellen der Karteikarte: $e';
      notifyListeners();
      return false;
    }
  }


  Future<void> reviewFlashcard(int id, bool correct) async {
    final index = _flashcards.indexWhere((f) => f['id'] == id);
    if (index != -1) {
      final card = _flashcards[index];
      final reviewCount = (card['reviewCount'] as int) + 1;
      
  
      int daysUntilNext;
      if (correct) {
        daysUntilNext = reviewCount * 2; 
      } else {
        daysUntilNext = 1; 
      }
      
      _flashcards[index] = {
        ...card,
        'reviewCount': correct ? reviewCount : 0,
        'lastReviewed': DateTime.now(),
        'nextReview': DateTime.now().add(Duration(days: daysUntilNext)),
      };
      
      notifyListeners();
      
      // TODO: API Call -> POST /api/study/flashcards/{id}/review
    }
  }


  List<Map<String, dynamic>> get dueFlashcards {
    final now = DateTime.now();
    return _flashcards.where((f) {
      final nextReview = f['nextReview'] as DateTime?;
      return nextReview == null || nextReview.isBefore(now);
    }).toList();
  }

  double get gpa {
    if (_grades.isEmpty) return 0.0;
    
    double totalWeighted = 0;
    int totalCredits = 0;
    
    for (final grade in _grades) {
      final g = grade['grade'] as double;
      final credits = grade['credits'] as int;
      final weight = grade['weight'] as double;
      
      totalWeighted += g * credits * weight;
      totalCredits += credits;
    }
    
    return totalCredits > 0 ? totalWeighted / totalCredits : 0.0;
  }


  int get totalCredits {
    return _grades.fold<int>(0, (sum, g) => sum + (g['credits'] as int));
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}