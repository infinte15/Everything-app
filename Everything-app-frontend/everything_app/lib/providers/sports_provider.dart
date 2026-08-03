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
  static const int _fallbackRestSeconds = 90;

  // ── Zustand ──────────────────────────────────────────────────────────────────

  List<GymRoutine> _routines = [];
  List<GymSession> _sessions = [];
  List<GymMuscleOption> _muscleOptions = [];
  List<GymMuscleVolume> _muscleVolumes = [];
  GymWeeklyStats _weeklyStats = const GymWeeklyStats();

  ActiveWorkout? _activeWorkout;

  bool _isLoading = false;
  bool _isSaving = false;
  String? _error;

  int _restSecondsRemaining = 0;
  int _restSecondsTotal = 0;
  Timer? _restTimer;
  String? _restExerciseName;
  int _defaultRestSeconds = _fallbackRestSeconds;

  // ── Getter ───────────────────────────────────────────────────────────────────

  bool get isLoading => _isLoading;
  bool get isSaving => _isSaving;
  String? get error => _error;

  List<GymRoutine> get routines => List.unmodifiable(_routines);
  List<GymSession> get sessions => List.unmodifiable(_sessions);
  List<GymMuscleOption> get muscleOptions => List.unmodifiable(_muscleOptions);
  List<GymMuscleVolume> get muscleVolumes => List.unmodifiable(_muscleVolumes);
  GymWeeklyStats get weeklyStats => _weeklyStats;

  ActiveWorkout? get activeWorkout => _activeWorkout;
  bool get hasActiveWorkout => _activeWorkout != null;

  int get restSecondsRemaining => _restSecondsRemaining;
  int get restSecondsTotal => _restSecondsTotal;
  bool get isResting => _restSecondsRemaining > 0;
  String? get restExerciseName => _restExerciseName;
  int get defaultRestSeconds => _defaultRestSeconds;

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

    await _restoreActiveWorkout();

    try {
      final results = await Future.wait([
        _service.getRoutines(),
        _service.getSessions(),
        _service.getWeeklyStats(),
        _service.getMuscleVolume(startDate: _weekStart(), endDate: DateTime.now()),
        _service.getMuscleGroups(),
      ]);

      _routines = results[0] as List<GymRoutine>;
      _sessions = results[1] as List<GymSession>;
      _weeklyStats = results[2] as GymWeeklyStats;
      _muscleVolumes = results[3] as List<GymMuscleVolume>;
      _muscleOptions = results[4] as List<GymMuscleOption>;
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

  /// Muskel-Belastung für einen anderen Zeitraum (Woche / Monat / Jahr).
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
              estimatedDurationMinutes: estimatedDurationMinutes,
              exercises: exercises,
            )
          : await _service.updateRoutine(
              id: id,
              name: name,
              dayLabel: dayLabel,
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
      startRestTimer(
        seconds: restSecondsFor(exerciseIndex),
        exerciseName: block.name,
      );
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
    block.sets[setIndex].setType = type;
    _afterWorkoutChange();
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
    for (final block in workout.exercises) {
      for (final set in block.sets) {
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
