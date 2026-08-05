import 'package:flutter/material.dart';
import '../models/study_note.dart';
import '../models/study_goal.dart';
import '../models/course_schedule.dart';
import '../models/study_subject.dart';
import '../models/study_semester.dart';
import '../models/study_grade.dart';
import '../models/flashcard_deck.dart';
import '../models/flashcard_review.dart';
import '../utils/anki_scheduler.dart';
import '../services/study_service.dart';

class StudyProvider with ChangeNotifier {
  // Services sind injizierbar, damit Tests eine Attrappe unterschieben koennen statt ins Netz
  // zu gehen - genau wie bei CalendarProvider. Ohne das war der Provider nicht testbar.
  StudyProvider({StudyService? studyService})
      : _studyService = studyService ?? StudyService();

  final StudyService _studyService;

  // ── Core data ───────────────────────────────────────────────────────────────
  List<StudyNote> _notes = [];
  List<StudyGoal> _goals = [];
  List<CourseSchedule> _schedules = [];
  List<StudySubject> _subjects = [];
  List<StudySemester> _semesters = [];
  List<FlashcardDeck> _flashcardDecks = [];
  List<Flashcard> _flashcards = [];
  List<FlashcardReview> _reviews = [];
  // Serverseitig gezaehlte Deck-Kennzahlen, je Deck einzeln nachgeladen. Die Liste rechnet
  // weiter lokal (siehe deckStats) - ein Request pro Deck waere genau das 1+N zurueck, das
  // getAllFlashcards gerade beseitigt hat.
  final Map<String, FlashcardDeckStats> _serverDeckStats = {};
  List<StudyGrade> _grades = [];

  // ── UI state ─────────────────────────────────────────────────────────────────
  String? _selectedNoteId;
  bool _isLoading = false;
  String? _error;
  int _activeTab = 0;
  String? _selectedSubjectId;

  // ── Getters ──────────────────────────────────────────────────────────────────
  bool get isLoading => _isLoading;
  String? get error => _error;
  List<StudyNote> get notes => _notes;
  List<StudyGoal> get studyPlan => _goals;
  List<CourseSchedule> get schedules => _schedules;
  List<StudySubject> get subjects => _subjects;
  List<StudySemester> get semesters => _semesters;

  /// Das als aktuell markierte Semester, sonst null.
  StudySemester? get currentSemester {
    for (final s in _semesters) {
      if (s.isCurrent) return s;
    }
    return null;
  }

  List<StudySubject> subjectsForSemester(String? semesterId) => semesterId == null
      ? _subjects
      : _subjects.where((s) => s.semesterId == semesterId).toList();
  List<FlashcardDeck> get flashcardDecks => _flashcardDecks;
  List<Flashcard> get flashcards => _flashcards;
  List<StudyGrade> get grades => _grades;
  List<FlashcardReview> get reviews => _reviews;

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

  // ── Seitenbaum ───────────────────────────────────────────────────────────────
  // Der Baum wird hier aus der flachen Liste gebaut; es gibt bewusst keinen Baum-Endpunkt.
  // Sortiert wird nach orderIndex, bei Gleichstand nach ID — genau wie der Server.

  /// Die Wurzelseiten in Anzeigereihenfolge.
  List<StudyNote> get noteTree => _sorted(_notes.where((n) => n.parentId == null));

  /// Die Unterseiten von [noteId] in Anzeigereihenfolge.
  List<StudyNote> childrenOf(int noteId) =>
      _sorted(_notes.where((n) => n.parentId == noteId));

  bool hasChildren(int noteId) => _notes.any((n) => n.parentId == noteId);

  /// Der Pfad von der Wurzel bis einschliesslich [noteId] — die Brotkrumen im Editor.
  /// Bricht bei 64 Schritten ab, damit eine kaputte Struktur die UI nicht aufhaengt.
  List<StudyNote> breadcrumbsFor(int noteId) {
    final byId = {for (final n in _notes) n.id: n};
    final path = <StudyNote>[];
    int? cursor = noteId;
    for (var hops = 0; cursor != null && hops < 64; hops++) {
      final note = byId[cursor];
      if (note == null) break;
      path.insert(0, note);
      cursor = note.parentId;
    }
    return path;
  }

  List<StudyNote> _sorted(Iterable<StudyNote> notes) {
    final list = notes.toList()
      ..sort((a, b) {
        final byIndex = a.orderIndex.compareTo(b.orderIndex);
        return byIndex != 0 ? byIndex : (a.id ?? 0).compareTo(b.id ?? 0);
      });
    return list;
  }

  // ── Sprint-Board (LERNPLAN-Tab) ──────────────────────────────────────────────
  // Der Status haengt als Tag "status:todo|in_progress|done" an der Notiz.

  /// Hoechstzahl der Seiten, die ohne eigenen Status in TO DO nachruecken.
  static const int kSprintBacklogLimit = 20;

  /// Der Kanban-Status einer Seite; null heisst "kein status:-Tag gesetzt".
  ///
  /// Exakter Vergleich auf der Tag-Liste statt contains() auf dem zusammengesetzten String:
  /// ein Tag wie "status:todo-later" landete sonst in TO DO, weil der gesuchte Status als
  /// Teilstring darin vorkommt.
  static String? statusOf(StudyNote note) {
    for (final tag in note.tagList) {
      if (tag.startsWith('status:')) return tag.substring('status:'.length);
    }
    return null;
  }

  /// Umfang des Boards: die Seiten der Module des aktuellen Semesters, sonst alle Seiten mit
  /// Modul. Ohne diese Eingrenzung stuende jede jemals angelegte Seite in TO DO — bei ein
  /// paar hundert Seiten ist das keine Spalte mehr, sondern eine Wand.
  Iterable<StudyNote> get _sprintScope {
    final semesterId = currentSemester?.id;
    if (semesterId == null) return _notes.where((n) => n.courseId != null);

    final courseIds = _subjects
        .where((s) => s.semesterId == semesterId)
        .map((s) => int.tryParse(s.id))
        .whereType<int>()
        .toSet();
    return _notes.where((n) => n.courseId != null && courseIds.contains(n.courseId));
  }

  /// Seiten ohne status:-Tag gelten als offen und ruecken automatisch nach — sonst taucht
  /// eine neu angelegte Seite in gar keiner Spalte auf. Explizit einsortierte Seiten stehen
  /// oben, der Rest folgt nach Aenderungsdatum und ist auf [kSprintBacklogLimit] gedeckelt.
  List<StudyNote> get todoNotes {
    final explicit = <StudyNote>[];
    final untagged = <StudyNote>[];
    for (final note in _sprintScope) {
      final status = statusOf(note);
      if (status == 'todo') {
        explicit.add(note);
      } else if (status == null) {
        untagged.add(note);
      }
    }
    untagged.sort((a, b) => (b.updatedAt ?? DateTime(0)).compareTo(a.updatedAt ?? DateTime(0)));
    return [...explicit, ...untagged.take(kSprintBacklogLimit)];
  }

  List<StudyNote> get inProgressNotes =>
      _sprintScope.where((n) => statusOf(n) == 'in_progress').toList();

  List<StudyNote> get doneNotes =>
      _sprintScope.where((n) => statusOf(n) == 'done').toList();

  /// Module mit anstehenden Karten, das dringendste zuerst.
  ///
  /// Gezaehlt wird [FlashcardDeckStats.studyCount], also faellige UND neue Karten — dasselbe
  /// Mass wie "ZU LERNEN" im FÄCHER-Tab und "N Karten faellig" in der Uebersicht. Nur
  /// stats.due liesse den Abschnitt leer, waehrend anderswo eine Zahl groesser null steht:
  /// eine noch nie gelernte Karte hat kein Wiederholungsdatum in der Vergangenheit.
  ///
  /// Rechnet ausschliesslich auf bereits geladenen Daten — dieselbe Quelle, aus der die
  /// Lernzone ihre Zahlen zieht. Ein eigener Endpunkt je Modul waere genau das 1+N zurueck,
  /// das getAllFlashcards beseitigt hat.
  List<({StudySubject subject, FlashcardDeckStats stats})> get coursesWithDueCards {
    final entries = _subjects
        .map((s) => (subject: s, stats: courseCardStats(s.id)))
        .where((e) => e.stats.studyCount > 0)
        .toList();
    entries.sort((a, b) => b.stats.studyCount.compareTo(a.stats.studyCount));
    return entries;
  }

  // ── Load ─────────────────────────────────────────────────────────────────────
  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      await Future.wait([
        _loadNotes(),
        _loadSemesters(),
        _loadSubjects(),
        _loadSchedules(),
        _loadFlashcards(),
        _loadGrades(),
        _loadGoals(),
      ]);
    } catch (e) {
      _error = 'Fehler beim Laden: $e';
    }
    _isLoading = false;
    notifyListeners();
  }

  Future<void> _loadNotes() async {
    final data = await _studyService.getAllNotes();
    _notes = data.map(StudyNote.fromJson).toList();
    _notes.sort((a, b) => (b.id ?? 0).compareTo(a.id ?? 0));
  }

  Future<void> _loadSubjects() async {
    final courses = await _studyService.getAllCourses();
    _subjects = courses.map(StudySubject.fromJson).toList();
  }

  Future<void> _loadSchedules() async {
    final data = await _studyService.getAllSchedules();
    _schedules = data.map(CourseSchedule.fromJson).toList();
  }

  Future<void> _loadSemesters() async {
    final data = await _studyService.getAllSemesters();
    _semesters = data.map(StudySemester.fromJson).toList();
  }

  /// Drei Requests, nicht 1 + N. Vorher wurde pro Deck einzeln nachgeladen, was beim Oeffnen
  /// des Study Space bei zehn Decks elf aufeinanderfolgende Roundtrips bedeutete.
  Future<void> _loadFlashcards() async {
    final results = await Future.wait([
      _studyService.getAllDecks(),
      _studyService.getAllFlashcards(),
      _studyService.getReviews(),
    ]);
    _flashcardDecks = results[0].map(FlashcardDeck.fromJson).toList();
    _flashcards = results[1].map(Flashcard.fromJson).toList();
    _reviews = results[2].map(FlashcardReview.fromJson).toList();
    _serverDeckStats.clear();
  }

  Future<void> _loadGrades() async {
    final gradesData = await _studyService.getAllGrades();
    _grades = gradesData.map(StudyGrade.fromJson).toList();
  }

  // ── Navigation ───────────────────────────────────────────────────────────────
  void selectNote(String? id) {
    _selectedNoteId = id;
    notifyListeners();
  }

  // ── Note CRUD (API) ──────────────────────────────────────────────────────────

  /// Gibt null zurueck, wenn der Server die Notiz nicht angenommen hat.
  ///
  /// Frueher legte dieser Pfad in dem Fall still eine lokale Notiz mit einer aus
  /// millisecondsSinceEpoch gebastelten 13-stelligen ID an. Die tauchte in der Liste auf,
  /// lief bei jedem spaeteren PUT/DELETE in ein 404 und verschwand beim naechsten Neuladen -
  /// eine Notiz, die aussah wie gespeichert und es nie war.
  /// Legt eine Notiz an — freie Notiz oder Modulseite, das entscheidet [courseId].
  ///
  /// Ohne Modul ist es eine freie Notiz aus dem Notizen-Space; [category] traegt dann
  /// „Personal" oder „Studium". Mit Modul ist es eine Seite des Seitenbaums im FAECHER-Tab.
  Future<StudyNote?> addNote({required String title, String content = '',
      int? parentId, int? courseId, String? category, String? icon}) async {
    final data = await _studyService.createNote({
      'title': title,
      'content': content,
      'parentId': parentId,
      'courseId': courseId,
      'category': category,
      // Startzustand fuer das Sprint-Board. Ohne den Tag ruecken neue Seiten nur ueber den
      // ungetaggten Nachrueckstapel nach und fallen ab kSprintBacklogLimit heraus.
      'tags': 'status:todo',
      'icon': icon,
    });

    if (data == null) {
      _error = 'Notiz konnte nicht gespeichert werden.';
      notifyListeners();
      return null;
    }

    final note = StudyNote.fromJson(data);
    _notes.insert(0, note);
    notifyListeners();
    return note;
  }

  /// Legt eine Unterseite unter [parentId] an. Der Server haengt sie ans Ende der Ebene und
  /// gibt ihr das Modul der Elternseite — deshalb wird hier keine courseId mitgegeben.
  Future<StudyNote?> createChildPage(int parentId, {String title = 'Neue Seite'}) =>
      addNote(title: title, parentId: parentId);

  /// Ordnet eine Seite samt Teilbaum einem Modul zu. Fuer Bestandsseiten, die vor der
  /// Modulpflicht entstanden sind, und zum Umhaengen zwischen Modulen.
  Future<bool> assignNoteToCourse(int noteId, int courseId) async {
    final updated = await _studyService.assignNoteCourse(noteId, courseId);
    if (updated == null) {
      _error = 'Seite konnte dem Modul nicht zugeordnet werden.';
      notifyListeners();
      return false;
    }

    // Der Teilbaum wandert mit; welche Seiten das genau sind, weiss der Server.
    await _loadNotes();
    notifyListeners();
    return true;
  }

  // ── Modul-Sicht: alles, was zu einem Fach gehoert ────────────────────────────

  /// Die obersten Seiten eines Moduls — alle Seiten des Moduls, deren Elternseite nicht
  /// ebenfalls dazu gehoert. Sonst taeuchte eine Unterseite doppelt auf.
  List<StudyNote> rootPagesOfCourse(String courseId) {
    final id = int.tryParse(courseId);
    if (id == null) return const [];

    final ofCourse = _notes.where((n) => n.courseId == id).toList();
    final ids = ofCourse.map((n) => n.id).toSet();
    return _sorted(ofCourse.where((n) => n.parentId == null || !ids.contains(n.parentId)));
  }

  /// Alle Seiten eines Moduls, auch die tiefer liegenden.
  int pageCountOfCourse(String courseId) {
    final id = int.tryParse(courseId);
    return id == null ? 0 : _notes.where((n) => n.courseId == id).length;
  }

  /// Die freien Notizen: alles ohne Modul. Sie leben im Notizen-Space, nicht im FAECHER-Tab.
  ///
  /// Bewusst kein „ohne Modul = Waise": ein fehlendes Modul ist ein gueltiger Dauerzustand,
  /// keine Luecke. Es gab hier eine Mahnliste, die jede Personal-Notiz zur Modulzuordnung
  /// aufforderte — genau die Verwechslung, die den Notizen-Space gekostet hat.
  List<StudyNote> get freeNotes =>
      _sorted(_notes.where((n) => n.courseId == null && n.parentId == null));

  List<FlashcardDeck> decksForCourse(String courseId) =>
      _flashcardDecks.where((d) => d.subjectId == courseId).toList();

  /// Die Kennzahlen aller Decks eines Moduls zusammengefasst.
  FlashcardDeckStats courseCardStats(String courseId) {
    var total = 0, due = 0, newCards = 0, learning = 0, mature = 0;
    for (final deck in decksForCourse(courseId)) {
      final s = deckStats(deck.id);
      total += s.total;
      due += s.due;
      newCards += s.newCards;
      learning += s.learning;
      mature += s.mature;
    }
    return FlashcardDeckStats(
      total: total, due: due, newCards: newCards, learning: learning, mature: mature,
    );
  }

  Future<bool> updateNote(StudyNote note) async {
    if (note.id == null) return false;
    final data = await _studyService.updateNote(note.id!, note.toJson());
    if (data == null) return false;

    final idx = _notes.indexWhere((n) => n.id == note.id);
    if (idx != -1) {
      _notes[idx] = StudyNote.fromJson(data);
      notifyListeners();
    }
    return true;
  }

  /// Loescht die Seite MIT ihrem Teilbaum — genau das tut der Server auch. Wuerde hier nur die
  /// eine Seite verschwinden, blieben ihre Unterseiten als Waisen in der Liste stehen.
  Future<bool> deleteNote(int id) async {
    final success = await _studyService.deleteNote(id);
    if (success) {
      final doomed = _subtreeIds(id);
      _notes.removeWhere((n) => doomed.contains(n.id));
      notifyListeners();
    }
    return success;
  }

  /// Haengt eine Seite unter [parentId] (null = Wurzelebene) an Position [position].
  ///
  /// Die Antwort enthaelt nur die verschobene Seite; die Geschwister haben serverseitig neue
  /// orderIndex-Werte bekommen, deshalb werden die Notizen danach neu geladen.
  Future<bool> moveNote(int id, int? parentId, int position) async {
    final moved = await _studyService.moveNote(id, parentId, position);
    if (moved == null) {
      _error = 'Die Seite konnte nicht verschoben werden.';
      notifyListeners();
      return false;
    }

    await _loadNotes();
    notifyListeners();
    return true;
  }

  /// Schreibt die Reihenfolge einer Ebene fest. Optimistisch: die Liste steht sofort richtig,
  /// bei einem Fehlschlag wird der vorherige Stand zurueckgeholt.
  Future<bool> reorderNotes(List<int> orderedIds) async {
    final previous = List<StudyNote>.from(_notes);

    for (var i = 0; i < orderedIds.length; i++) {
      final idx = _notes.indexWhere((n) => n.id == orderedIds[i]);
      if (idx != -1) _notes[idx] = _notes[idx].copyWith(orderIndex: i);
    }
    notifyListeners();

    final success = await _studyService.reorderNotes(orderedIds);
    if (!success) {
      _notes = previous;
      notifyListeners();
    }
    return success;
  }

  /// Die Seite selbst plus alles darunter. Bricht bei 64 Ebenen ab, damit eine kaputte
  /// Struktur nicht in eine Endlosschleife laeuft.
  Set<int> _subtreeIds(int rootId) {
    final childrenByParent = <int?, List<int>>{};
    for (final n in _notes) {
      if (n.id != null) {
        childrenByParent.putIfAbsent(n.parentId, () => []).add(n.id!);
      }
    }

    final collected = <int>{};
    final queue = <int>[rootId];
    var hops = 0;
    while (queue.isNotEmpty && hops++ < 64 * 64) {
      final current = queue.removeAt(0);
      if (!collected.add(current)) continue;
      queue.addAll(childrenByParent[current] ?? const []);
    }
    return collected;
  }

  Future<bool> toggleFavorite(int noteId) async {
    final idx = _notes.indexWhere((n) => n.id == noteId);
    if (idx == -1) return false;

    final current = _notes[idx];
    _notes[idx] = current.copyWith(isFavorite: !current.isFavorite);
    notifyListeners();

    final data = await _studyService.updateNote(noteId, _notes[idx].toJson());
    if (data == null) {
      _notes[idx] = current;   // optimistisch gesetzt, also auch wieder zuruecknehmen
      notifyListeners();
      return false;
    }
    return true;
  }

  /// Setzt den Kanban-Status als Tag an der Notiz (LERNPLAN-Tab).
  Future<bool> updateNoteStatus(int noteId, String status) async {
    final idx = _notes.indexWhere((n) => n.id == noteId);
    if (idx == -1) return false;

    final previous = _notes[idx];
    final tags = (previous.tags ?? '')
        .split(',')
        .where((t) => t.isNotEmpty && !t.startsWith('status:'))
        .toList()
      ..add('status:$status');

    _notes[idx] = previous.copyWith(tags: tags.join(','));
    notifyListeners();

    final data = await _studyService.updateNote(noteId, _notes[idx].toJson());
    if (data == null) {
      _notes[idx] = previous;
      notifyListeners();
      return false;
    }
    return true;
  }

  // ── Lernziele (API) ──────────────────────────────────────────────────────────
  //
  // Die Brücke in den Kalender liegt jetzt im Backend (StudyGoalService): dort spiegelt sich
  // jedes Ziel in einen Task, den der SmartScheduler platziert. Vorher tat das dieser
  // Provider — und weil die Ziele nur im Arbeitsspeicher lagen, blieb nach jedem Neustart
  // eine Aufgabe ohne zugehöriges Ziel zurück, die für immer weitergeplant wurde.
  //
  // Deshalb wird hier nichts optimistisch gesetzt: es gilt, was der Server zurückgibt.

  Future<void> _loadGoals() async {
    final data = await _studyService.getGoals();
    _goals = data.map(StudyGoal.fromJson).toList();
  }

  Future<bool> addStudyGoal({
    required int courseId,
    required double goalHours,
    String emoji = '📚',
  }) async {
    final data = await _studyService.createGoal({
      'courseId': courseId,
      'weeklyGoalHours': goalHours,
      'emoji': emoji,
    });
    if (data == null) return false;

    _goals = [..._goals, StudyGoal.fromJson(data)];
    notifyListeners();
    return true;
  }

  Future<bool> logStudyHours(int goalId, double hours) async {
    final data = await _studyService.logGoalHours(goalId, hours);
    if (data == null) return false;

    final updated = StudyGoal.fromJson(data);
    _goals = _goals.map((g) => g.id == goalId ? updated : g).toList();
    notifyListeners();
    return true;
  }

  Future<bool> updateStudyGoal(int goalId, {required double goalHours, String? emoji}) async {
    final data = await _studyService.updateGoal(goalId, {
      'weeklyGoalHours': goalHours,
      'emoji': ?emoji,
    });
    if (data == null) return false;

    final updated = StudyGoal.fromJson(data);
    _goals = _goals.map((g) => g.id == goalId ? updated : g).toList();
    notifyListeners();
    return true;
  }

  Future<bool> deleteStudyGoal(int id) async {
    if (!await _studyService.deleteGoal(id)) return false;

    _goals = _goals.where((g) => g.id != id).toList();
    notifyListeners();
    return true;
  }

  /// Module, für die noch kein Lernziel besteht — der Server lässt nur eines je Modul zu.
  List<StudySubject> get subjectsWithoutGoal {
    final taken = _goals.map((g) => g.courseId).toSet();
    return _subjects.where((s) => !taken.contains(int.tryParse(s.id))).toList();
  }

  // ── Stundenplan (API) ────────────────────────────────────────────────────────
  //
  // Jede Aenderung loest serverseitig eine Neuplanung des Kalenders aus (ScheduleChangedEvent):
  // der SmartScheduler behandelt Stundenplaene als Blockzeiten. Deshalb wird hier nichts
  // optimistisch gesetzt, sondern immer uebernommen, was zurueckkommt.

  List<CourseSchedule> lessonsForDay(int dayIndex) =>
      _schedules.where((s) => s.dayIndex == dayIndex).toList()
        ..sort((a, b) => (a.startHour * 60 + a.startMinute)
            .compareTo(b.startHour * 60 + b.startMinute));

  Future<bool> addSchedule({
    required int courseId,
    required int weekday,
    required int startHour,
    required int startMinute,
    required int endHour,
    required int endMinute,
    String? location,
  }) async {
    final created = await _studyService.createSchedule(courseId, {
      'dayOfWeek': _weekdayName(weekday),
      'startTime': _timeString(startHour, startMinute),
      'endTime': _timeString(endHour, endMinute),
      'location': location,
    });

    if (created == null) {
      _error = 'Veranstaltung konnte nicht gespeichert werden.';
      notifyListeners();
      return false;
    }

    _schedules.add(CourseSchedule.fromJson(created));
    notifyListeners();
    return true;
  }

  Future<bool> updateSchedule(CourseSchedule schedule) async {
    final idx = _schedules.indexWhere((s) => s.id == schedule.id);
    if (idx == -1) return false;

    final updated = await _studyService.updateSchedule(
      int.parse(schedule.courseId),
      int.parse(schedule.id),
      schedule.toJson(),
    );
    if (updated == null) return false;

    _schedules[idx] = CourseSchedule.fromJson(updated);
    notifyListeners();
    return true;
  }

  Future<bool> deleteSchedule(CourseSchedule schedule) async {
    final success = await _studyService.deleteSchedule(
      int.parse(schedule.courseId),
      int.parse(schedule.id),
    );
    if (success) {
      _schedules.removeWhere((s) => s.id == schedule.id);
      notifyListeners();
    }
    return success;
  }

  static const _weekdayNames = [
    'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
  ];

  static String _weekdayName(int weekday) => _weekdayNames[(weekday - 1).clamp(0, 6)];

  static String _timeString(int hour, int minute) =>
      '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}:00';

  // ── Flashcards (API + Anki-style SRS) ────────────────────────────────────────
  List<Flashcard> cardsForDeck(String deckId) =>
      _flashcards.where((f) => f.deckId == deckId).toList();

  /// Die Kennzahlen eines Decks — serverseitig gezaehlt, sobald [refreshDeckStats] fuer dieses
  /// Deck gelaufen ist, sonst aus den bereits geladenen Karten abgeleitet. Beide Wege benutzen
  /// dieselbe Einteilung, die lokale Rechnung ist also kein anderer Massstab, nur ein aelterer.
  FlashcardDeckStats deckStats(String deckId) =>
      _serverDeckStats[deckId] ?? _localDeckStats(deckId);

  /// Holt die Kennzahlen eines Decks frisch vom Server. Aufrufer ist die Deck-Detailseite —
  /// genau ein Request fuer genau das Deck, das gerade offen ist.
  Future<void> refreshDeckStats(String deckId) async {
    final data = await _studyService.getDeckStats(int.parse(deckId));
    if (data == null) return;

    _serverDeckStats[deckId] = FlashcardDeckStats.fromJson(data);
    notifyListeners();
  }

  /// Wie viele Karten seit [from] bewertet wurden. Ohne Angabe: seit Mitternacht.
  int reviewCountSince([DateTime? from]) {
    final now = DateTime.now();
    final start = from ?? DateTime(now.year, now.month, now.day);
    return _reviews.where((r) => r.reviewedAt.isAfter(start)).length;
  }

  FlashcardDeckStats _localDeckStats(String deckId) {
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

  /// Der Server ist die einzige Wahrheit fuer den Wiederholungszustand; hier wird nur
  /// uebernommen, was zurueckkommt.
  Future<bool> reviewFlashcardWithRating(String cardId, ReviewRating rating) async {
    final idx = _flashcards.indexWhere((f) => f.id == cardId);
    if (idx == -1) return false;

    final before = _flashcards[idx];
    final updated = await _studyService.reviewFlashcard(
      int.parse(cardId),
      rating.name.toUpperCase(),   // again/hard/good/easy -> AGAIN/HARD/GOOD/EASY
    );
    if (updated == null) {
      _error = 'Bewertung konnte nicht gespeichert werden.';
      notifyListeners();
      return false;
    }

    final after = Flashcard.fromJson(updated);
    _flashcards[idx] = after;

    // Der Server hat die Bewertung protokolliert; dieselbe Zeile lokal nachziehen, statt das
    // ganze Protokoll neu zu holen. Die Zahlen stammen aus der Antwort, sind also dieselben.
    _reviews.insert(0, FlashcardReview(
      id: 'local-${DateTime.now().microsecondsSinceEpoch}',
      flashcardId: cardId,
      deckId: after.deckId,
      rating: rating,
      reviewedAt: DateTime.now(),
      intervalDaysBefore: before.intervalDays,
      intervalDaysAfter: after.intervalDays,
    ));
    // Die Deck-Kennzahlen stimmen jetzt nicht mehr; die naechste Abfrage rechnet lokal, bis
    // die Detailseite sie wieder frisch holt.
    _serverDeckStats.remove(after.deckId);

    notifyListeners();
    return true;
  }

  Future<bool> addFlashcard(Flashcard flashcard) async {
    final created = await _studyService.createFlashcard(flashcard.toJson());
    if (created == null) return false;

    final card = Flashcard.fromJson(created);
    _flashcards.add(card);
    _serverDeckStats.remove(card.deckId);   // eine Karte mehr im Deck
    notifyListeners();
    return true;
  }

  Future<bool> addFlashcardToDeck({
    required String deckId,
    required String question,
    required String answer,
  }) {
    return addFlashcard(Flashcard(
      id: '',                       // vergibt das Backend
      deckId: deckId,
      question: question.trim(),
      answer: answer.trim(),
      nextReview: DateTime.now(),
    ));
  }

  Future<bool> updateFlashcard(Flashcard card) async {
    final idx = _flashcards.indexWhere((f) => f.id == card.id);
    if (idx == -1) return false;

    final updated = await _studyService.updateFlashcard(int.parse(card.id), card.toJson());
    if (updated == null) return false;

    _flashcards[idx] = Flashcard.fromJson(updated);
    notifyListeners();
    return true;
  }

  Future<bool> deleteFlashcard(String cardId) async {
    final success = await _studyService.deleteFlashcard(int.parse(cardId));
    if (success) {
      for (final card in _flashcards.where((f) => f.id == cardId)) {
        _serverDeckStats.remove(card.deckId);
      }
      _flashcards.removeWhere((f) => f.id == cardId);
      _reviews.removeWhere((r) => r.flashcardId == cardId);   // das Protokoll geht mit
      notifyListeners();
    }
    return success;
  }

  // ── Grades (API) ─────────────────────────────────────────────────────────────
  List<StudyGrade> gradesForSubject(String subjectId) =>
      _grades.where((g) => g.subjectId == subjectId).toList();

  Future<bool> addGrade(StudyGrade grade) async {
    final created = await _studyService.createGrade(grade.toJson());
    if (created == null) {
      _error = 'Note konnte nicht gespeichert werden.';
      notifyListeners();
      return false;
    }

    _grades.add(StudyGrade.fromJson(created));
    notifyListeners();
    return true;
  }

  Future<bool> updateGrade(StudyGrade grade) async {
    final idx = _grades.indexWhere((g) => g.id == grade.id);
    if (idx == -1) return false;

    final updated = await _studyService.updateGrade(int.parse(grade.id), grade.toJson());
    if (updated == null) return false;

    _grades[idx] = StudyGrade.fromJson(updated);
    notifyListeners();
    return true;
  }

  Future<bool> deleteGrade(String gradeId) async {
    final success = await _studyService.deleteGrade(int.parse(gradeId));
    if (success) {
      _grades.removeWhere((g) => g.id == gradeId);
      notifyListeners();
    }
    return success;
  }

  // ── Subjects (API) ───────────────────────────────────────────────────────────
  Future<bool> addSubject({
    required String name,
    required String professor,
    required int creditPoints,
    required String colorHex,
    String? semesterId,
  }) async {
    // Die Freitext-Bezeichnung setzt der Server aus dem verknuepften Semester - hier wird
    // nur die ID mitgegeben, damit beides nicht auseinanderlaufen kann.
    final created = await _studyService.createCourse(StudySubject(
      id: '',
      name: name,
      professor: professor,
      creditPoints: creditPoints,
      semesterId: semesterId,
      colorHex: colorHex,
    ).toJson());

    if (created == null) {
      _error = 'Modul konnte nicht gespeichert werden.';
      notifyListeners();
      return false;
    }

    _subjects.add(StudySubject.fromJson(created));
    if (semesterId != null) {
      await _loadSemesters();   // Modulzahl und ECTS des Semesters haben sich verschoben
    }
    notifyListeners();
    return true;
  }

  Future<bool> deleteSubject(String id) async {
    final success = await _studyService.deleteCourse(int.parse(id));
    if (success) {
      _subjects.removeWhere((s) => s.id == id);
      notifyListeners();
    }
    return success;
  }

  // ── Flashcard Decks (API) ────────────────────────────────────────────────────
  Future<bool> addFlashcardDeck(FlashcardDeck deck) async {
    final created = await _studyService.createDeck(deck.toJson());
    if (created == null) {
      _error = 'Deck konnte nicht gespeichert werden.';
      notifyListeners();
      return false;
    }

    _flashcardDecks.add(FlashcardDeck.fromJson(created));
    notifyListeners();
    return true;
  }

  Future<bool> deleteFlashcardDeck(String deckId) async {
    final success = await _studyService.deleteDeck(int.parse(deckId));
    if (success) {
      _flashcardDecks.removeWhere((d) => d.id == deckId);
      _flashcards.removeWhere((f) => f.deckId == deckId);
      _reviews.removeWhere((r) => r.deckId == deckId);
      _serverDeckStats.remove(deckId);
      notifyListeners();
    }
    return success;
  }

  FlashcardDeck? deckById(String id) {
    try {
      return _flashcardDecks.firstWhere((d) => d.id == id);
    } catch (_) {
      return null;
    }
  }

  // ── Semester (API) ───────────────────────────────────────────────────────────
  Future<bool> addSemester(StudySemester semester) async {
    final created = await _studyService.createSemester(semester.toJson());
    if (created == null) {
      _error = 'Semester konnte nicht gespeichert werden.';
      notifyListeners();
      return false;
    }

    _semesters.add(StudySemester.fromJson(created));
    _sortSemesters();
    notifyListeners();
    return true;
  }

  Future<bool> updateSemester(StudySemester semester) async {
    final idx = _semesters.indexWhere((s) => s.id == semester.id);
    if (idx == -1) return false;

    final updated =
        await _studyService.updateSemester(int.parse(semester.id), semester.toJson());
    if (updated == null) return false;

    _semesters[idx] = StudySemester.fromJson(updated);
    // Die Bezeichnung haengt auch an den Modulen (der Server pflegt sie mit), also neu laden.
    await _loadSubjects();
    notifyListeners();
    return true;
  }

  Future<bool> setCurrentSemester(String semesterId) async {
    final updated = await _studyService.setCurrentSemester(int.parse(semesterId));
    if (updated == null) return false;

    // Genau eines ist aktuell - lokal genauso durchziehen wie der Server es tut.
    _semesters = _semesters
        .map((s) => s.copyWith(isCurrent: s.id == semesterId))
        .toList();
    notifyListeners();
    return true;
  }

  Future<bool> deleteSemester(String semesterId) async {
    final success = await _studyService.deleteSemester(int.parse(semesterId));
    if (!success) return false;

    _semesters.removeWhere((s) => s.id == semesterId);
    // Die Module bleiben bestehen, verlieren aber ihre Zuordnung.
    _subjects = _subjects
        .map((s) => s.semesterId == semesterId
            ? StudySubject(
                id: s.id,
                name: s.name,
                professor: s.professor,
                colorHex: s.colorHex,
                creditPoints: s.creditPoints,
              )
            : s)
        .toList();
    notifyListeners();
    return true;
  }

  Future<bool> reorderSemesters(List<String> orderedIds) async {
    final previous = List<StudySemester>.from(_semesters);
    final byId = {for (final s in _semesters) s.id: s};
    _semesters = [
      for (var i = 0; i < orderedIds.length; i++)
        if (byId[orderedIds[i]] != null) byId[orderedIds[i]]!.copyWith(orderIndex: i),
    ];
    notifyListeners();

    final success = await _studyService
        .reorderSemesters(orderedIds.map(int.parse).toList());
    if (!success) {
      _semesters = previous;   // optimistisch gesetzt, also auch wieder zuruecknehmen
      notifyListeners();
    }
    return success;
  }

  /// Ordnet ein Modul einem Semester zu; null hebt die Zuordnung auf.
  Future<bool> assignSubjectToSemester(String subjectId, String? semesterId) async {
    final idx = _subjects.indexWhere((s) => s.id == subjectId);
    if (idx == -1) return false;

    final updated = await _studyService.assignSemester(
      int.parse(subjectId),
      semesterId != null ? int.parse(semesterId) : null,
    );
    if (updated == null) return false;

    _subjects[idx] = StudySubject.fromJson(updated);
    await _loadSemesters();   // Modulzahl und ECTS je Semester haben sich verschoben
    notifyListeners();
    return true;
  }

  void _sortSemesters() {
    _semesters.sort((a, b) {
      final byIndex = a.orderIndex.compareTo(b.orderIndex);
      return byIndex != 0 ? byIndex : a.id.compareTo(b.id);
    });
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────
  void clearError() {
    _error = null;
    notifyListeners();
  }
}