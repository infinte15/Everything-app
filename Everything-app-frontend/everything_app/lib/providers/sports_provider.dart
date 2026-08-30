import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/gym/gym_models.dart';
import '../services/sports_service.dart';

/// Zustand des Gym-Bereichs.
///
/// Alle Kennzahlen kommen vom Server - hier wird nichts mehr aus unvollstaendigen
/// Session-Daten nachgerechnet. Ein laufendes Training wird zusaetzlich lokal
/// gesichert, damit ein Absturz oder App-Neustart es nicht verliert.
class SportsProvider with ChangeNotifier {
  SportsProvider({SportsService? service}) : _service = service ?? SportsService();

  final SportsService _service;

  static const String _activeWorkoutKey = 'gym_active_workout';
  static const String _autoWarmupKey = 'gym_auto_warmup';
  static const String _showRpeKey = 'gym_show_rpe';
  static const int _fallbackRestSeconds = 90;

  /// Anteil des Arbeitsgewichts im ersten Abfallsatz.
  static const double _dropFactor = 0.8;

  /// Obergrenze der Pause nach einem Aufwärmsatz.
  static const int _warmupRestSeconds = 60;

  // ── Zustand ──────────────────────────────────────────────────────────────────

  List<GymRoutine> _routines = [];
  List<GymSession> _sessions = [];
  List<GymMuscleOption> _muscleOptions = [];
  List<GymMuscleVolume> _muscleVolumes = [];
  List<GymMuscleRecovery> _recovery = [];
  List<GymEquipmentProfile> _equipmentProfiles = [];
  GymWeeklyStats _weeklyStats = const GymWeeklyStats();
  GymBodyWeightSeries _bodyWeight = const GymBodyWeightSeries();

  ActiveWorkout? _activeWorkout;

  bool _isLoading = false;
  bool _isSaving = false;
  String? _error;

  int _restSecondsRemaining = 0;
  int _restSecondsTotal = 0;
  Timer? _restTimer;
  String? _restExerciseName;
  int _defaultRestSeconds = _fallbackRestSeconds;

  /// Aufwärmsätze beim Trainingsstart automatisch einfügen. Aus, bis es jemand einschaltet:
  /// drei zusätzliche Zeilen je Übung ungefragt anzulegen ist mehr Bevormundung als Hilfe.
  bool _autoWarmup = false;

  /// RIR/RPE-Spalte im Satzraster. Aus, weil die Mehrheit ohne sie trainiert und die
  /// Zeile sonst eng wird.
  bool _showRpe = false;

  /// Nächste Übung im laufenden Supersatz - gesetzt, solange die Runde nicht voll ist.
  int? _supersetNextIndex;

  // ── Getter ───────────────────────────────────────────────────────────────────

  bool get isLoading => _isLoading;
  bool get isSaving => _isSaving;
  String? get error => _error;

  List<GymRoutine> get routines => List.unmodifiable(_routines);
  List<GymSession> get sessions => List.unmodifiable(_sessions);
  List<GymMuscleOption> get muscleOptions => List.unmodifiable(_muscleOptions);
  List<GymMuscleVolume> get muscleVolumes => List.unmodifiable(_muscleVolumes);
  List<GymMuscleRecovery> get recovery => List.unmodifiable(_recovery);
  List<GymEquipmentProfile> get equipmentProfiles => List.unmodifiable(_equipmentProfiles);

  /// Aktives Ausrüstungsprofil, oder null wenn nicht gefiltert wird.
  GymEquipmentProfile? get activeEquipmentProfile {
    for (final p in _equipmentProfiles) {
      if (p.isActive) return p;
    }
    return null;
  }
  GymWeeklyStats get weeklyStats => _weeklyStats;
  GymBodyWeightSeries get bodyWeight => _bodyWeight;

  ActiveWorkout? get activeWorkout => _activeWorkout;
  bool get hasActiveWorkout => _activeWorkout != null;

  int get restSecondsRemaining => _restSecondsRemaining;
  int get restSecondsTotal => _restSecondsTotal;
  bool get isResting => _restSecondsRemaining > 0;
  String? get restExerciseName => _restExerciseName;
  int get defaultRestSeconds => _defaultRestSeconds;
  bool get autoWarmup => _autoWarmup;
  bool get showRpe => _showRpe;

  /// Übung, die im Supersatz als Nächstes dran ist. Null heißt: Runde voll, Pause läuft.
  int? get supersetNextIndex => _supersetNextIndex;

  /// 0..1 - Fortschritt des laufenden Pausen-Timers, für den Ring.
  double get restProgress =>
      _restSecondsTotal > 0 ? _restSecondsRemaining / _restSecondsTotal : 0;

  int get totalWorkouts => _sessions.length;
  int get currentStreakWeeks => _weeklyStats.currentStreakWeeks;

  double get totalVolumeAllTime =>
      _sessions.fold(0.0, (sum, s) => sum + s.totalVolumeKg);

  /// Muskel-Slug -> Auslastung 0..1, direkt für die Körper-Grafik.
  Map<String, double> get muscleActivation => {
        for (final m in _muscleVolumes) m.muscle: m.share,
      };

  // ── Laden ────────────────────────────────────────────────────────────────────

  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    await loadTrainingPreferences();
    await _restoreActiveWorkout();
    // Das aktive Profil filtert die Bibliothek - es muss stehen, bevor jemand sie öffnet.
    unawaited(loadEquipmentProfiles());

    try {
      final results = await Future.wait([
        _service.getRoutines(),
        _service.getSessions(),
        _service.getWeeklyStats(),
        _service.getMuscleVolume(startDate: _weekStart(), endDate: DateTime.now()),
        _service.getMuscleGroups(),
        // Nur das letzte halbe Jahr: die Kurve auf dem Startbildschirm zeigt ohnehin nicht
        // mehr, und die Kennzahlen daneben kommen unabhängig vom Zeitraum vollständig mit.
        _service.getBodyWeight(from: DateTime.now().subtract(const Duration(days: 182))),
      ]);

      _routines = results[0] as List<GymRoutine>;
      _sessions = results[1] as List<GymSession>;
      _weeklyStats = results[2] as GymWeeklyStats;
      _muscleVolumes = results[3] as List<GymMuscleVolume>;
      _muscleOptions = results[4] as List<GymMuscleOption>;
      _bodyWeight = results[5] as GymBodyWeightSeries;
      _error = null;
    } catch (e) {
      _error = 'Daten konnten nicht geladen werden: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  /// Lädt nur die Auswertungen neu - nach dem Abschluss eines Trainings.
  Future<void> refreshStats() async {
    try {
      final results = await Future.wait([
        _service.getWeeklyStats(),
        _service.getMuscleVolume(startDate: _weekStart(), endDate: DateTime.now()),
        _service.getSessions(),
      ]);
      _weeklyStats = results[0] as GymWeeklyStats;
      _muscleVolumes = results[1] as List<GymMuscleVolume>;
      _sessions = results[2] as List<GymSession>;
      notifyListeners();
    } catch (e) {
      debugPrint('Statistiken konnten nicht aktualisiert werden: $e');
    }
  }

  // ── Körpergewicht ────────────────────────────────────────────────────────────

  /// Trägt das Gewicht für einen Tag ein. Ein zweiter Eintrag am selben Tag ersetzt den ersten.
  Future<bool> logBodyWeight(double weightKg, {DateTime? date, String? note}) async {
    _isSaving = true;
    notifyListeners();
    try {
      await _service.logBodyWeight(weightKg, date: date, note: note);
      await _reloadBodyWeight();
      _error = null;
      return true;
    } catch (e) {
      _error = 'Gewicht konnte nicht gespeichert werden: $e';
      return false;
    } finally {
      _isSaving = false;
      notifyListeners();
    }
  }

  /// Setzt das Zielgewicht, oder entfernt es mit `null`.
  Future<bool> setBodyWeightTarget(double? targetKg) async {
    try {
      await _service.setBodyWeightTarget(targetKg);
      await _reloadBodyWeight();
      _error = null;
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Zielgewicht konnte nicht gesetzt werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> deleteBodyWeightEntry(int id) async {
    try {
      await _service.deleteBodyWeight(id);
      await _reloadBodyWeight();
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Eintrag konnte nicht gelöscht werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<void> _reloadBodyWeight() async {
    _bodyWeight = await _service.getBodyWeight(
      from: DateTime.now().subtract(const Duration(days: 182)),
    );
  }

  /// Muskel-Belastung für einen anderen Zeitraum (Woche / Monat / Jahr).
  /// Erholungsstand je Muskelgruppe. Eigener Aufruf: die Rechnung greift auf acht Wochen
  /// Verlauf zu und gehört nicht in jeden Seitenaufbau.
  Future<void> loadRecovery() async {
    try {
      _recovery = await _service.getRecovery();
      _error = null;
    } catch (e) {
      _error = 'Erholung konnte nicht geladen werden: $e';
    }
    notifyListeners();
  }

  // ── Ausrüstungsprofile ───────────────────────────────────────────────────────

  /// Geräte-Werte des Katalogs für die Profil-Auswahl. Bewusst kein Zustand: die Liste
  /// wird nur beim Bearbeiten eines Profils gebraucht.
  Future<List<String>> equipmentValues() async {
    try {
      return (await _service.getExerciseFilters()).equipment;
    } catch (e) {
      debugPrint('Geräteliste konnte nicht geladen werden: $e');
      return const [];
    }
  }

  Future<void> loadEquipmentProfiles() async {
    try {
      _equipmentProfiles = await _service.getEquipmentProfiles();
      _error = null;
    } catch (e) {
      _error = 'Ausrüstungsprofile konnten nicht geladen werden: $e';
    }
    notifyListeners();
  }

  Future<bool> saveEquipmentProfile({
    int? id,
    required String name,
    required List<String> equipment,
  }) =>
      _equipmentAction(() =>
          _service.saveEquipmentProfile(id: id, name: name, equipment: equipment));

  Future<bool> deleteEquipmentProfile(int id) =>
      _equipmentAction(() => _service.deleteEquipmentProfile(id));

  /// [id] null schaltet die Filterung ab.
  Future<bool> activateEquipmentProfile(int? id) =>
      _equipmentAction(() => _service.activateEquipmentProfile(id));

  /// Jede Änderung an den Profilen lädt die Liste neu - sie ist kurz, und der Server
  /// entscheidet, welches Profil aktiv ist.
  Future<bool> _equipmentAction(Future<void> Function() action) async {
    try {
      await action();
      _equipmentProfiles = await _service.getEquipmentProfiles();
      _error = null;
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Ausrüstungsprofil konnte nicht gespeichert werden: $e';
      notifyListeners();
      return false;
    }
  }

  // ── Stehende Übungsnotizen ───────────────────────────────────────────────────

  Future<String?> loadExerciseNote(int exerciseId) async {
    try {
      return await _service.getExerciseNote(exerciseId);
    } catch (e) {
      debugPrint('Notiz konnte nicht geladen werden: $e');
      return null;
    }
  }

  /// Lädt eine Katalogzeile nach - für das Übungsblatt aus dem laufenden Training.
  ///
  /// Der Trainingsblock trägt nur, was fürs Protokollieren nötig ist; Anleitung, Verlauf und
  /// die übrigen Katalogfelder hängen an der Übung selbst.
  Future<GymExercise?> loadExercise(int exerciseId) async {
    try {
      return await _service.getExercise(exerciseId);
    } catch (e) {
      _error = 'Übung konnte nicht geladen werden: $e';
      notifyListeners();
      return null;
    }
  }

  Future<bool> saveExerciseNote(int exerciseId, String text) async {
    try {
      await _service.saveExerciseNote(exerciseId, text);
      return true;
    } catch (e) {
      _error = 'Notiz konnte nicht gespeichert werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<void> loadMuscleVolume({required DateTime from, DateTime? to}) async {
    try {
      _muscleVolumes = await _service.getMuscleVolume(
        startDate: from,
        endDate: to ?? DateTime.now(),
      );
      notifyListeners();
    } catch (e) {
      debugPrint('Muskelauswertung fehlgeschlagen: $e');
    }
  }

  // ── Routinen ─────────────────────────────────────────────────────────────────

  Future<bool> saveRoutine({
    int? id,
    required String name,
    String? dayLabel,
    int? preferredWeekday,
    int? estimatedDurationMinutes,
    required List<GymRoutineExercise> exercises,
  }) async {
    _isSaving = true;
    notifyListeners();
    try {
      final routine = id == null
          ? await _service.createRoutine(
              name: name,
              dayLabel: dayLabel,
              preferredWeekday: preferredWeekday,
              estimatedDurationMinutes: estimatedDurationMinutes,
              exercises: exercises,
            )
          : await _service.updateRoutine(
              id: id,
              name: name,
              dayLabel: dayLabel,
              preferredWeekday: preferredWeekday,
              estimatedDurationMinutes: estimatedDurationMinutes,
              exercises: exercises,
            );

      final index = _routines.indexWhere((r) => r.id == routine.id);
      if (index >= 0) {
        _routines[index] = routine;
      } else {
        _routines.add(routine);
      }
      _error = null;
      return true;
    } catch (e) {
      _error = 'Routine konnte nicht gespeichert werden: $e';
      return false;
    } finally {
      _isSaving = false;
      notifyListeners();
    }
  }

  /// Legt eine Routine auf einen Wochentag - oder nimmt sie mit `null` wieder herunter.
  ///
  /// Ein Tag trägt höchstens eine Routine: wer eine Routine auf einen belegten Tag legt,
  /// nimmt die bisherige von dort herunter. Zwei Routinen am selben Tag wären für den
  /// Scheduler ohnehin nicht erfüllbar - er lässt höchstens ein Training pro Tag zu und
  /// müsste eine davon verwerfen.
  Future<bool> setRoutineWeekday(int routineId, int? weekday) async {
    _isSaving = true;
    notifyListeners();
    try {
      if (weekday != null) {
        for (final other in _routines) {
          if (other.id != routineId && other.preferredWeekday == weekday) {
            await _persistWeekday(other, null);
          }
        }
      }
      final target = _routines.firstWhere((r) => r.id == routineId);
      await _persistWeekday(target, weekday);
      _error = null;
      return true;
    } catch (e) {
      _error = 'Wochentag konnte nicht gesetzt werden: $e';
      return false;
    } finally {
      _isSaving = false;
      notifyListeners();
    }
  }

  /// Schreibt den Wochentag einer Routine, ohne ihre Übungen zu verlieren.
  ///
  /// Der Upsert des Servers ersetzt die Übungsliste durch die mitgeschickte, deshalb wird die
  /// vollständige Routine geladen: die Zusammenfassungen in [_routines] tragen nur Kennzahlen,
  /// mit ihnen zu speichern würde jede Routine leerräumen.
  Future<void> _persistWeekday(GymRoutine summary, int? weekday) async {
    final full = summary.exercises.isNotEmpty
        ? summary
        : await _service.getRoutine(summary.id);
    final saved = await _service.updateRoutine(
      id: full.id,
      name: full.name,
      dayLabel: weekday == null ? null : _weekdayLabels[weekday - 1],
      preferredWeekday: weekday,
      estimatedDurationMinutes: full.estimatedDurationMinutes,
      exercises: full.exercises,
    );
    final index = _routines.indexWhere((r) => r.id == saved.id);
    if (index >= 0) _routines[index] = saved;
  }

  static const List<String> _weekdayLabels = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

  Future<bool> deleteRoutine(int id) async {
    try {
      await _service.deleteRoutine(id);
      _routines.removeWhere((r) => r.id == id);
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Routine konnte nicht gelöscht werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<GymRoutine?> loadRoutineDetail(int id) async {
    try {
      final routine = await _service.getRoutine(id);
      final index = _routines.indexWhere((r) => r.id == id);
      if (index >= 0) _routines[index] = routine;
      notifyListeners();
      return routine;
    } catch (e) {
      _error = 'Routine konnte nicht geladen werden: $e';
      notifyListeners();
      return null;
    }
  }

  // ── Übungs-Katalog ───────────────────────────────────────────────────────────

  Future<GymExercisePage> searchExercises({
    String? search,
    String? muscle,
    String? equipment,
    int page = 0,
    int size = 30,
  }) =>
      _service.searchExercises(
        search: search,
        muscle: muscle,
        equipment: equipment,
        page: page,
        size: size,
      );

  Future<List<GymHistoryEntry>> exerciseHistory(int exerciseId, {int limit = 20}) =>
      _service.getExerciseHistory(exerciseId, limit: limit);

  Future<GymPersonalRecord?> personalRecords(int exerciseId) async {
    try {
      return await _service.getPersonalRecords(exerciseId);
    } catch (e) {
      debugPrint('Bestleistungen konnten nicht geladen werden: $e');
      return null;
    }
  }

  // ── Training starten ─────────────────────────────────────────────────────────

  Future<bool> startRoutine(int routineId) => _start(routineId: routineId);

  Future<bool> startEmptyWorkout({String name = 'Training'}) => _start(name: name);

  /// Übernimmt eine vom Scheduler eingeplante Einheit als laufendes Training.
  Future<bool> startScheduledSession(int sessionId) => _start(sessionId: sessionId);

  Future<bool> _start({int? routineId, int? sessionId, String? name}) async {
    try {
      final data = await _service.startWorkout(
        routineId: routineId,
        sessionId: sessionId,
        name: name,
      );
      _activeWorkout = ActiveWorkout.fromResponse(data);
      if (_autoWarmup) {
        for (var i = 0; i < _activeWorkout!.exercises.length; i++) {
          applyWarmupRamp(i);
        }
      }
      await _persistActiveWorkout();
      _error = null;
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Training konnte nicht gestartet werden: $e';
      notifyListeners();
      return false;
    }
  }

  // ── Laufendes Training bearbeiten ────────────────────────────────────────────

  void addExerciseToWorkout(GymExercise exercise, {int sets = 3}) {
    final workout = _activeWorkout;
    if (workout == null) return;
    workout.exercises.add(GymWorkoutExercise.fromExercise(exercise, sets: sets));
    _afterWorkoutChange();
  }

  void removeExerciseFromWorkout(int exerciseIndex) {
    final workout = _activeWorkout;
    if (workout == null || exerciseIndex >= workout.exercises.length) return;
    workout.exercises.removeAt(exerciseIndex);
    _afterWorkoutChange();
  }

  void addSetToExercise(int exerciseIndex) {
    final block = _blockAt(exerciseIndex);
    if (block == null) return;
    final last = block.sets.isNotEmpty ? block.sets.last : null;
    block.sets.add(GymLoggedSet(
      setNumber: block.sets.length + 1,
      weight: last?.weight,
      reps: last?.reps,
    ));
    _afterWorkoutChange();
  }

  void removeSet(int exerciseIndex, int setIndex) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return;
    block.sets.removeAt(setIndex);
    for (var i = 0; i < block.sets.length; i++) {
      block.sets[i].setNumber = i + 1;
    }
    _afterWorkoutChange();
  }

  /// Hakt einen Satz ab bzw. wieder aus. Beim Abhaken startet die Pause automatisch.
  void toggleSetDone(int exerciseIndex, int setIndex, {bool startRest = true}) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return;

    final set = block.sets[setIndex];
    set.isCompleted = !set.isCompleted;

    if (set.isCompleted && startRest) {
      // Im Supersatz wird erst die Runde zu Ende trainiert, dann pausiert - genau das
      // unterscheidet einen Supersatz von zwei Übungen hintereinander.
      final next = _nextSupersetExercise(exerciseIndex);
      _supersetNextIndex = next;
      if (next == null) {
        startRestTimer(
          seconds: _restAfter(set, exerciseIndex),
          exerciseName: block.name,
        );
      }
    } else if (!set.isCompleted) {
      _supersetNextIndex = null;
    }
    _afterWorkoutChange();
  }

  void updateSetValues(int exerciseIndex, int setIndex, {double? weight, int? reps}) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return;
    final set = block.sets[setIndex];
    if (weight != null) set.weight = weight;
    if (reps != null) set.reps = reps;
    _afterWorkoutChange(notify: false);
  }

  void setSetType(int exerciseIndex, int setIndex, GymSetType type) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return;
    final set = block.sets[setIndex];
    set.setType = type;
    // Ein Satz, der keiner mehr ist: die Verknüpfung zum Arbeitssatz gilt nur für
    // Abfallsätze und Rest-Pause-Cluster.
    if (type != GymSetType.drop && type != GymSetType.restpause) {
      set.parentSetNumber = null;
    }
    _afterWorkoutChange();
  }

  void setSetRpe(int exerciseIndex, int setIndex, int? rpe) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return;
    block.sets[setIndex].rpe = rpe?.clamp(1, 10);
    _afterWorkoutChange();
  }

  /// Hängt einen Abfallsatz an einen Arbeitssatz - eigene, leichtere Last.
  ///
  /// Zählt zusätzlich ins Volumen: das ist echte Arbeit mit einem anderen Gewicht.
  void addDropSet(int exerciseIndex, int setIndex) =>
      _addChildSet(exerciseIndex, setIndex, GymSetType.drop, scale: _dropFactor);

  /// Hängt einen Rest-Pause-Cluster an einen Arbeitssatz - dieselbe Last, kurze Pause.
  ///
  /// Zählt *nicht* ins Volumen; die Wiederholungen gehören zum Arbeitssatz darüber.
  void addRestPauseSet(int exerciseIndex, int setIndex) =>
      _addChildSet(exerciseIndex, setIndex, GymSetType.restpause);

  void _addChildSet(int exerciseIndex, int setIndex, GymSetType type,
      {double scale = 1.0}) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return;

    final parent = block.sets[setIndex];
    // Ein Zusatzsatz hängt immer am Arbeitssatz, nie an einem anderen Zusatzsatz -
    // sonst entstünde eine Kette, die niemand mehr auswerten kann.
    if (parent.parentSetNumber != null) return;

    final weight = parent.weight;
    // Direkt hinter den Arbeitssatz und seine bereits vorhandenen Zusatzsätze.
    var insertAt = setIndex + 1;
    while (insertAt < block.sets.length &&
        block.sets[insertAt].parentSetNumber == parent.setNumber) {
      insertAt++;
    }

    block.sets.insert(
      insertAt,
      GymLoggedSet(
        setNumber: parent.setNumber,
        weight: weight == null ? null : _roundToPlate(weight * scale),
        reps: type == GymSetType.drop ? parent.reps : null,
        setType: type,
        parentSetNumber: parent.setNumber,
      ),
    );
    _renumber(block);
    _afterWorkoutChange();
  }

  /// Legt die Aufwärmrampe der Progression als Sätze vor den ersten Arbeitssatz.
  ///
  /// Idempotent: sind schon Aufwärmsätze da, passiert nichts. Sonst käme beim zweiten
  /// Antippen die halbe Rampe doppelt.
  bool applyWarmupRamp(int exerciseIndex) {
    final block = _blockAt(exerciseIndex);
    final ramp = block?.progression?.warmup ?? const [];
    if (block == null || ramp.isEmpty) return false;
    if (block.sets.any((s) => s.setType == GymSetType.warmup)) return false;

    block.sets.insertAll(
      0,
      ramp.map((w) => GymLoggedSet(
            setNumber: 0,
            weight: w.weight,
            reps: w.reps,
            setType: GymSetType.warmup,
          )),
    );
    _renumber(block);
    _afterWorkoutChange();
    return true;
  }

  /// Nummeriert die Sätze neu und zieht die Verweise der Zusatzsätze mit.
  ///
  /// Der Umweg über die Objekte statt über eine Nummern-Tabelle ist nötig: ein frisch
  /// eingefügter Zusatzsatz trägt zunächst dieselbe Nummer wie sein Arbeitssatz, eine
  /// Tabelle "alte Nummer → neue Nummer" wäre an dieser Stelle mehrdeutig.
  void _renumber(GymWorkoutExercise block) {
    final parentsByOldNumber = <int, GymLoggedSet>{};
    for (final set in block.sets) {
      if (set.parentSetNumber == null) {
        parentsByOldNumber.putIfAbsent(set.setNumber, () => set);
      }
    }

    final parentOf = <GymLoggedSet, GymLoggedSet>{};
    for (final set in block.sets) {
      final old = set.parentSetNumber;
      if (old == null) continue;
      final parent = parentsByOldNumber[old];
      if (parent != null && parent != set) parentOf[set] = parent;
    }

    for (var i = 0; i < block.sets.length; i++) {
      block.sets[i].setNumber = i + 1;
    }
    for (final set in block.sets) {
      if (set.parentSetNumber == null) continue;
      // Ein Zusatzsatz, dessen Arbeitssatz gelöscht wurde, wird wieder ein eigener Satz -
      // sonst fiele er aus jeder Auswertung heraus, ohne dass man es sähe.
      set.parentSetNumber = parentOf[set]?.setNumber;
    }
  }

  /// Auf die nächste ladbare Stufe (2,5 kg) - kein Gerät kennt 47,3 kg.
  double _roundToPlate(double value) => (value / 2.5).round() * 2.5;

  /// Übungen desselben Supersatzes, in Trainingsreihenfolge.
  List<int> supersetPartners(int exerciseIndex) {
    final workout = _activeWorkout;
    final block = _blockAt(exerciseIndex);
    final group = block?.supersetGroup;
    if (workout == null || group == null) return const [];
    final partners = <int>[];
    for (var i = 0; i < workout.exercises.length; i++) {
      if (workout.exercises[i].supersetGroup == group) partners.add(i);
    }
    return partners.length > 1 ? partners : const [];
  }

  /// Wer im Supersatz als Nächstes dran ist - die erste Übung der Gruppe, die in dieser
  /// Runde noch einen Satz offen hat. Null heißt: Runde voll, jetzt kommt die Pause.
  int? _nextSupersetExercise(int exerciseIndex) {
    final partners = supersetPartners(exerciseIndex);
    if (partners.isEmpty) return null;

    final done = _workSetsDone(exerciseIndex);
    // Reihum ab der aktuellen Übung, damit die Runde in der geplanten Reihenfolge läuft.
    final start = partners.indexOf(exerciseIndex);
    for (var step = 1; step <= partners.length; step++) {
      final candidate = partners[(start + step) % partners.length];
      if (candidate == exerciseIndex) continue;
      final block = _blockAt(candidate);
      if (block == null) continue;
      final open = block.sets.any((s) => !s.isCompleted && s.setType.countsTowardVolume);
      if (open && _workSetsDone(candidate) < done) return candidate;
    }
    return null;
  }

  int _workSetsDone(int exerciseIndex) {
    final block = _blockAt(exerciseIndex);
    if (block == null) return 0;
    return block.sets
        .where((s) => s.isCompleted && s.setType.countsTowardVolume)
        .length;
  }

  /// Pause nach einem Aufwärmsatz ist gedeckelt: drei Minuten nach der leeren Stange sind
  /// keine Pause, sondern eine Unterbrechung.
  int _restAfter(GymLoggedSet set, int exerciseIndex) {
    final rest = restSecondsFor(exerciseIndex);
    return set.setType == GymSetType.warmup
        ? (rest < _warmupRestSeconds ? rest : _warmupRestSeconds)
        : rest;
  }

  /// Pausenzeit dieser Übung: Routine-Vorgabe, sonst der persönliche Standard.
  int restSecondsFor(int exerciseIndex) {
    final block = _blockAt(exerciseIndex);
    return block?.restSeconds ?? _defaultRestSeconds;
  }

  /// Pausenzeit nur für diese Übung in diesem Training, unabhängig von der
  /// Routinen-Vorgabe oder dem persönlichen Standard.
  void setExerciseRestSeconds(int exerciseIndex, int seconds) {
    final block = _blockAt(exerciseIndex);
    if (block == null) return;
    block.restSeconds = seconds.clamp(0, 900);
    _afterWorkoutChange();
  }

  /// Übernimmt eine gespeicherte Übungsnotiz in das laufende Training.
  ///
  /// Die Notiz hängt an der Übung, nicht am Training - gespeichert wird sie über
  /// [saveExerciseNote]. Hier steht nur, was der Block davon anzeigt, damit die Zeile nach dem
  /// Bearbeiten sofort stimmt, statt bis zum nächsten Laden das Alte zu behaupten.
  void setExerciseNoteInWorkout(int exerciseIndex, String? note) {
    final block = _blockAt(exerciseIndex);
    if (block == null) return;
    final trimmed = note?.trim();
    block.exerciseNote = (trimmed == null || trimmed.isEmpty) ? null : trimmed;
    notifyListeners();
  }

  /// Erkennt eine neue Bestleistung, um den Satz im UI zu markieren.
  bool isPersonalRecord(int exerciseIndex, int setIndex) {
    final block = _blockAt(exerciseIndex);
    if (block == null || setIndex >= block.sets.length) return false;
    final set = block.sets[setIndex];
    final record = block.personalRecordWeight;
    if (!set.isCompleted || set.weight == null || record == null) return false;
    return set.weight! > record;
  }

  GymWorkoutExercise? _blockAt(int index) {
    final workout = _activeWorkout;
    if (workout == null || index < 0 || index >= workout.exercises.length) return null;
    return workout.exercises[index];
  }

  ActiveWorkoutStats get activeWorkoutStats {
    final workout = _activeWorkout;
    if (workout == null) return const ActiveWorkoutStats();
    var volume = 0.0;
    var completed = 0;
    var total = 0;
    // Gezählt werden Arbeitssätze - siehe GymWorkoutExercise.workSets.
    for (final block in workout.exercises) {
      for (final set in block.workSets) {
        total++;
        if (set.isCompleted) {
          completed++;
          volume += set.volume;
        }
      }
    }
    return ActiveWorkoutStats(
      volumeKg: volume,
      completedSets: completed,
      totalSets: total,
    );
  }

  // ── Training abschliessen ────────────────────────────────────────────────────

  Future<bool> finishWorkout({String? notes}) async {
    final workout = _activeWorkout;
    if (workout == null) return false;

    _isSaving = true;
    notifyListeners();
    try {
      final minutes = DateTime.now().difference(workout.startedAt).inMinutes;
      await _service.finishWorkout(
        sessionId: workout.sessionId,
        exercises: workout.exercises,
        notes: notes,
        durationMinutes: minutes < 1 ? 1 : minutes,
      );

      _activeWorkout = null;
      await _clearPersistedWorkout();
      skipRest();
      _error = null;
      await refreshStats();
      return true;
    } catch (e) {
      _error = 'Training konnte nicht gespeichert werden: $e';
      return false;
    } finally {
      _isSaving = false;
      notifyListeners();
    }
  }

  /// Verwirft das laufende Training. Die leere Einheit bleibt serverseitig bestehen
  /// und wird beim nächsten Abschluss überschrieben bzw. kann gelöscht werden.
  Future<void> cancelWorkout() async {
    final workout = _activeWorkout;
    _activeWorkout = null;
    await _clearPersistedWorkout();
    skipRest();
    notifyListeners();

    if (workout != null) {
      try {
        await _service.deleteSession(workout.sessionId);
      } catch (e) {
        debugPrint('Verworfene Einheit konnte nicht entfernt werden: $e');
      }
    }
  }

  // ── Pausen-Timer ─────────────────────────────────────────────────────────────

  void startRestTimer({required int seconds, String? exerciseName}) {
    _restTimer?.cancel();
    _restSecondsRemaining = seconds;
    _restSecondsTotal = seconds;
    _restExerciseName = exerciseName;
    _restTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (_restSecondsRemaining <= 1) {
        skipRest();
      } else {
        _restSecondsRemaining--;
        notifyListeners();
      }
    });
    notifyListeners();
  }

  void skipRest() {
    _restTimer?.cancel();
    _restTimer = null;
    _restSecondsRemaining = 0;
    _restSecondsTotal = 0;
    _restExerciseName = null;
    notifyListeners();
  }

  void adjustRest(int delta) {
    if (!isResting) return;
    _restSecondsRemaining = (_restSecondsRemaining + delta).clamp(1, 900);
    // Der Ring darf nicht überlaufen, wenn Zeit draufgelegt wird.
    if (_restSecondsRemaining > _restSecondsTotal) {
      _restSecondsTotal = _restSecondsRemaining;
    }
    notifyListeners();
  }

  void setDefaultRestSeconds(int seconds) {
    _defaultRestSeconds = seconds.clamp(15, 600);
    notifyListeners();
  }

  /// Liest die Trainings-Schalter. Fehlschlag ist kein Fehler: dann gilt der Standardwert.
  Future<void> loadTrainingPreferences() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _autoWarmup = prefs.getBool(_autoWarmupKey) ?? false;
      _showRpe = prefs.getBool(_showRpeKey) ?? false;
      notifyListeners();
    } catch (e) {
      debugPrint('Trainings-Einstellungen konnten nicht gelesen werden: $e');
    }
  }

  Future<void> setAutoWarmup(bool value) => _setFlag(_autoWarmupKey, value, (v) {
        _autoWarmup = v;
      });

  Future<void> setShowRpe(bool value) => _setFlag(_showRpeKey, value, (v) {
        _showRpe = v;
      });

  Future<void> _setFlag(String key, bool value, void Function(bool) apply) async {
    apply(value);
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(key, value);
    } catch (e) {
      debugPrint('Einstellung $key konnte nicht gespeichert werden: $e');
    }
  }

  // ── Lokale Sicherung des laufenden Trainings ─────────────────────────────────

  void _afterWorkoutChange({bool notify = true}) {
    unawaited(_persistActiveWorkout());
    if (notify) notifyListeners();
  }

  Future<void> _persistActiveWorkout() async {
    final workout = _activeWorkout;
    if (workout == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_activeWorkoutKey, json.encode(workout.toCache()));
    } catch (e) {
      debugPrint('Training konnte nicht zwischengespeichert werden: $e');
    }
  }

  Future<void> _clearPersistedWorkout() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_activeWorkoutKey);
    } catch (e) {
      debugPrint('Zwischenspeicher konnte nicht geleert werden: $e');
    }
  }

  Future<void> _restoreActiveWorkout() async {
    if (_activeWorkout != null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_activeWorkoutKey);
      if (raw == null) return;
      final decoded = json.decode(raw);
      if (decoded is Map<String, dynamic>) {
        _activeWorkout = ActiveWorkout.fromCache(decoded);
      }
    } catch (e) {
      debugPrint('Laufendes Training konnte nicht wiederhergestellt werden: $e');
      await _clearPersistedWorkout();
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }

  static DateTime _weekStart() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    return today.subtract(Duration(days: today.weekday - 1));
  }

  @override
  void dispose() {
    _restTimer?.cancel();
    super.dispose();
  }
}

/// Ein laufendes Training.
class ActiveWorkout {
  final int sessionId;
  final String name;
  final DateTime startedAt;
  final int? routineId;
  final List<GymWorkoutExercise> exercises;

  ActiveWorkout({
    required this.sessionId,
    required this.name,
    required this.startedAt,
    this.routineId,
    required this.exercises,
  });

  factory ActiveWorkout.fromResponse(Map<String, dynamic> m) => ActiveWorkout(
        sessionId: m['sessionId'] as int? ?? 0,
        name: m['name'] as String? ?? 'Training',
        startedAt: _date(m['startedAt']) ?? DateTime.now(),
        routineId: m['routineId'] as int?,
        exercises: (m['plannedExercises'] as List? ?? [])
            .whereType<Map>()
            .map((e) => GymWorkoutExercise.fromPlanned(Map<String, dynamic>.from(e)))
            .toList(),
      );

  factory ActiveWorkout.fromCache(Map<String, dynamic> m) => ActiveWorkout(
        sessionId: m['sessionId'] as int? ?? 0,
        name: m['name'] as String? ?? 'Training',
        startedAt: _date(m['startedAt']) ?? DateTime.now(),
        routineId: m['routineId'] as int?,
        exercises: (m['exercises'] as List? ?? [])
            .whereType<Map>()
            .map((e) => GymWorkoutExercise.fromCache(Map<String, dynamic>.from(e)))
            .toList(),
      );

  Map<String, dynamic> toCache() => {
        'sessionId': sessionId,
        'name': name,
        'startedAt': startedAt.toIso8601String(),
        'routineId': routineId,
        'exercises': exercises.map((e) => e.toCache()).toList(),
      };

  Duration get elapsed => DateTime.now().difference(startedAt);

  static DateTime? _date(dynamic value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString().replaceFirst(' ', 'T'));
  }
}

class ActiveWorkoutStats {
  final double volumeKg;
  final int completedSets;
  final int totalSets;

  const ActiveWorkoutStats({
    this.volumeKg = 0,
    this.completedSets = 0,
    this.totalSets = 0,
  });
}
