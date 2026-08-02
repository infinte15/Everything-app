import 'dart:async';
import 'package:flutter/material.dart';
import '../models/gym/gym_models.dart';
import '../services/sports_service.dart';

class SportsProvider with ChangeNotifier {
  final SportsService _service = SportsService();

  List<Map<String, dynamic>> _workoutPlans = [];
  List<Map<String, dynamic>> _workoutSessions = [];
  List<Map<String, dynamic>> _exercises = [];
  Map<String, dynamic>? _currentWorkout;

  bool _isLoading = false;
  String? _error;

  int _restSecondsRemaining = 0;
  Timer? _restTimer;
  int _defaultRestSeconds = 90;
  String? _restExerciseName;

  bool get isLoading => _isLoading;
  String? get error => _error;
  List<Map<String, dynamic>> get workoutPlans => _workoutPlans;
  List<Map<String, dynamic>> get workoutSessions => _workoutSessions;
  List<Map<String, dynamic>> get exercises => _exercises;
  Map<String, dynamic>? get currentWorkout => _currentWorkout;
  int get restSecondsRemaining => _restSecondsRemaining;
  bool get isResting => _restSecondsRemaining > 0;
  String? get restExerciseName => _restExerciseName;
  int get defaultRestSeconds => _defaultRestSeconds;

  int get totalWorkouts => _workoutSessions.length;
  int get currentStreak => _calculateStreak();

  double get totalVolumeAllTime => _workoutSessions.fold<double>(
        0,
        (s, w) => s + ((w['totalVolumeKg'] as num?)?.toDouble() ?? _sessionVolume(w)),
      );

  double get weeklyVolume {
    final now = DateTime.now();
    final weekStart = now.subtract(Duration(days: now.weekday - 1));
    return _workoutSessions.where((w) {
      final d = w['date'] != null ? DateTime.parse(w['date'].toString()) : DateTime.now();
      return !d.isBefore(weekStart);
    }).fold<double>(0, (s, w) => s + ((w['totalVolumeKg'] as num?)?.toDouble() ?? _sessionVolume(w)));
  }

  int get weeklyWorkoutCount {
    final stats = getWeeklyStats();
    return stats['workouts'] as int? ?? 0;
  }

  List<GymProgramTemplate> get programTemplates => _templates;

  static final List<GymProgramTemplate> _templates = [
    GymProgramTemplate(
      name: 'Push Pull Legs',
      description: 'Classic 6-day hypertrophy split',
      level: 'Intermediate',
      daysPerWeek: 6,
      routines: [
        GymRoutine(id: 101, name: 'Push', exercises: [
          {'name': 'Bench Press', 'sets': 4, 'reps': 8, 'weight': 60},
          {'name': 'Incline Dumbbell Press', 'sets': 3, 'reps': 10, 'weight': 24},
          {'name': 'Lateral Raise', 'sets': 3, 'reps': 15, 'weight': 10},
        ], estimatedMinutes: 55),
        GymRoutine(id: 102, name: 'Pull', exercises: [
          {'name': 'Barbell Row', 'sets': 4, 'reps': 8, 'weight': 70},
          {'name': 'Lat Pulldown', 'sets': 3, 'reps': 10, 'weight': 50},
          {'name': 'Barbell Curl', 'sets': 3, 'reps': 12, 'weight': 25},
        ], estimatedMinutes: 55),
        GymRoutine(id: 103, name: 'Legs', exercises: [
          {'name': 'Squat', 'sets': 4, 'reps': 6, 'weight': 100},
          {'name': 'Romanian Deadlift', 'sets': 3, 'reps': 10, 'weight': 80},
          {'name': 'Leg Extension', 'sets': 3, 'reps': 12, 'weight': 45},
        ], estimatedMinutes: 65),
      ],
    ),
    GymProgramTemplate(
      name: 'StrongLifts 5×5',
      description: 'Linear progression strength program',
      level: 'Beginner',
      daysPerWeek: 3,
      routines: [
        GymRoutine(id: 201, name: 'Workout A', exercises: [
          {'name': 'Squat', 'sets': 5, 'reps': 5, 'weight': 60},
          {'name': 'Bench Press', 'sets': 5, 'reps': 5, 'weight': 40},
          {'name': 'Barbell Row', 'sets': 5, 'reps': 5, 'weight': 40},
        ], estimatedMinutes: 45),
        GymRoutine(id: 202, name: 'Workout B', exercises: [
          {'name': 'Squat', 'sets': 5, 'reps': 5, 'weight': 60},
          {'name': 'Overhead Press', 'sets': 5, 'reps': 5, 'weight': 30},
          {'name': 'Deadlift', 'sets': 1, 'reps': 5, 'weight': 80},
        ], estimatedMinutes: 45),
      ],
    ),
  ];

  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await Future.wait([
        _loadWorkoutPlans(),
        _loadWorkoutSessions(),
        _loadExercises(),
      ]);
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<void> _loadWorkoutPlans() async {
    _workoutPlans = await _service.getAllPlans();
    // Falls keine Backend-Workouts existieren, kann man Optional die Templates importieren.
    // Aber wir belassen es bei leeren Listen, wenn das Backend leer ist.
  }

  Future<void> _loadWorkoutSessions() async {
    final sessions = await _service.getAllSessions();
    
    // Wir müssen die Sets für jede Session laden oder das Backend gibt sie mit. 
    // Hier formatieren wir die Datum-Strings zu echten DateTimes für das Frontend
    _workoutSessions = sessions.map((s) {
      if (s['startTime'] != null && s['date'] == null) {
         s['date'] = s['startTime']; // Fallback
      }
      return s;
    }).toList();
  }

  Future<void> _loadExercises() async {
    _exercises = await _service.getAllExercises();
  }

  Future<void> addRoutine({
    required String name,
    String? day,
    required List<Map<String, dynamic>> exercises,
    int estimatedMinutes = 60,
  }) async {
    final planData = {
      'name': name,
      'goal': 'Hypertrophy',
      'difficulty': 'Intermediate',
      'durationWeeks': 12,
      'workoutsPerWeek': 3,
      // Das Backend hat keine direkten "Exercises" im Plan-DTO verknüpft,
      // man müsste hier ggf. Sessions/Routinen zum Plan erstellen.
    };
    
    final created = await _service.createPlan(planData);
    if (created != null) {
       _workoutPlans.add(created);
       notifyListeners();
    }
  }

  Future<bool> startWorkout(int workoutPlanId) async {
    try {
      final plan = _workoutPlans.firstWhere((p) => p['id'] == workoutPlanId);
      // Fallback auf leere Übungsliste, wenn keine "exercises" am Plan hängen
      final templateExercises = plan.containsKey('exercises') 
          ? List<Map<String, dynamic>>.from(plan['exercises'] as List)
          : <Map<String, dynamic>>[];
          
      _initWorkout(
        name: plan['name'] as String? ?? 'Workout',
        planId: workoutPlanId,
        templateExercises: templateExercises,
      );
      return true;
    } catch (e) {
      _error = 'Workout konnte nicht gestartet werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> startEmptyWorkout({String name = 'Workout'}) async {
    _initWorkout(name: name, planId: null, templateExercises: []);
    return true;
  }

  void _initWorkout({
    required String name,
    required int? planId,
    required List<Map<String, dynamic>> templateExercises,
  }) {
    final exercises = templateExercises.map((ex) {
      final setsCount = ex['sets'] as int? ?? 3;
      final defaultReps = ex['reps'] as int? ?? 10;
      final defaultWeight = (ex['weight'] as num?)?.toDouble() ?? 0;
      final exName = ex['name'] as String? ?? '';
      return {
        'name': exName,
        'exerciseId': ex['exerciseId'] ?? _exerciseIdByName(exName),
        'loggedSets': List.generate(
          setsCount,
          (i) => {
            'set': i + 1,
            'weight': defaultWeight,
            'reps': defaultReps,
            'done': false,
            'type': GymSetType.normal.name,
          },
        ),
      };
    }).toList();

    _currentWorkout = {
      'workoutPlanId': planId,
      'name': name,
      'startTime': DateTime.now(),
      'exercises': exercises,
      'completedSets': 0,
    };
    notifyListeners();
  }

  int _exerciseIdByName(String name) {
    for (final e in _exercises) {
      if (e['name'] == name) return e['id'] as int? ?? 0;
    }
    return 0; // 0 ist ungültig, Backend erwartet valide ID
  }

  void addExerciseToWorkout(Map<String, dynamic> exercise, {int sets = 3}) {
    if (_currentWorkout == null) return;
    final list = _currentWorkout!['exercises'] as List<Map<String, dynamic>>;
    final weight = (exercise['weight'] as num?)?.toDouble() ?? 0;
    list.add({
      'name': exercise['name'],
      'exerciseId': exercise['id'],
      'loggedSets': List.generate(
        sets,
        (i) => {
          'set': i + 1,
          'weight': weight,
          'reps': 10,
          'done': false,
          'type': GymSetType.normal.name,
        },
      ),
    });
    notifyListeners();
  }

  void completeSet({String? exerciseName, int restSeconds = 90}) {
    if (_currentWorkout == null) return;
    _currentWorkout!['completedSets'] =
        (_currentWorkout!['completedSets'] as int? ?? 0) + 1;
    startRestTimer(seconds: restSeconds, exerciseName: exerciseName);
    notifyListeners();
  }

  void toggleSetDone(int exerciseIndex, int setIndex, {bool startRest = true}) {
    if (_currentWorkout == null) return;
    final exercises = _currentWorkout!['exercises'] as List;
    if (exerciseIndex >= exercises.length) return;
    final ex = exercises[exerciseIndex] as Map<String, dynamic>;
    final sets = ex['loggedSets'] as List;
    if (setIndex >= sets.length) return;
    final set = sets[setIndex] as Map<String, dynamic>;
    final wasDone = set['done'] as bool? ?? false;
    set['done'] = !wasDone;
    if (!wasDone) {
      _currentWorkout!['completedSets'] =
          (_currentWorkout!['completedSets'] as int? ?? 0) + 1;
      if (startRest) {
        startRestTimer(
          seconds: _defaultRestSeconds,
          exerciseName: ex['name'] as String?,
        );
      }
    } else {
      _currentWorkout!['completedSets'] =
          ((_currentWorkout!['completedSets'] as int? ?? 1) - 1).clamp(0, 9999);
    }
    notifyListeners();
  }

  void addSetToExercise(int exerciseIndex) {
    if (_currentWorkout == null) return;
    final exercises = _currentWorkout!['exercises'] as List;
    if (exerciseIndex >= exercises.length) return;
    final ex = exercises[exerciseIndex] as Map<String, dynamic>;
    final sets = ex['loggedSets'] as List<Map<String, dynamic>>;
    final last = sets.isNotEmpty ? sets.last : null;
    sets.add({
      'set': sets.length + 1,
      'weight': last?['weight'] ?? 0,
      'reps': last?['reps'] ?? 10,
      'done': false,
      'type': GymSetType.normal.name,
    });
    notifyListeners();
  }

  void setSetType(int exerciseIndex, int setIndex, GymSetType type) {
    if (_currentWorkout == null) return;
    final exercises = _currentWorkout!['exercises'] as List;
    final sets = (exercises[exerciseIndex] as Map)['loggedSets'] as List;
    (sets[setIndex] as Map)['type'] = type.name;
    notifyListeners();
  }

  void updateSetValues(int exerciseIndex, int setIndex, {double? weight, int? reps}) {
    if (_currentWorkout == null) return;
    final exercises = _currentWorkout!['exercises'] as List;
    if (exerciseIndex >= exercises.length) return;
    final sets = (exercises[exerciseIndex] as Map)['loggedSets'] as List;
    if (setIndex >= sets.length) return;
    final set = sets[setIndex] as Map<String, dynamic>;
    if (weight != null) set['weight'] = weight;
    if (reps != null) set['reps'] = reps;
    notifyListeners();
  }

  void startRestTimer({required int seconds, String? exerciseName}) {
    _restTimer?.cancel();
    _restSecondsRemaining = seconds;
    _restExerciseName = exerciseName;
    _restTimer = Timer.periodic(const Duration(seconds: 1), (t) {
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
    _restSecondsRemaining = 0;
    _restExerciseName = null;
    notifyListeners();
  }

  void adjustRest(int delta) {
    _restSecondsRemaining = (_restSecondsRemaining + delta).clamp(0, 600);
    notifyListeners();
  }

  void setDefaultRestSeconds(int seconds) {
    _defaultRestSeconds = seconds.clamp(15, 300);
    notifyListeners();
  }

  String? getPreviousPerformance(String exerciseName) {
    for (final session in _workoutSessions) {
      final exercises = session['exercises'] as List? ?? [];
      for (final ex in exercises) {
        if (ex['name'] == exerciseName) {
          return '${ex['weight']} kg × ${ex['reps']}';
        }
      }
    }
    return null;
  }

  double? getPersonalRecord(String exerciseName) {
    double? best;
    for (final session in _workoutSessions) {
      final exercises = session['exercises'] as List? ?? [];
      for (final ex in exercises) {
        if (ex['name'] == exerciseName) {
          final w = (ex['weight'] as num?)?.toDouble() ?? 0;
          if (best == null || w > best) best = w;
        }
      }
    }
    return best;
  }

  Map<String, dynamic> getActiveWorkoutStats() {
    if (_currentWorkout == null) {
      return {'volume': 0.0, 'completedSets': 0, 'totalSets': 0};
    }
    double volume = 0;
    int completed = 0;
    int total = 0;
    for (final ex in _currentWorkout!['exercises'] as List) {
      for (final s in (ex as Map)['loggedSets'] as List) {
        total++;
        if (s['done'] == true) {
          completed++;
          volume += ((s['weight'] as num?) ?? 0) * ((s['reps'] as num?) ?? 0);
        }
      }
    }
    return {
      'volume': volume,
      'completedSets': completed,
      'totalSets': total,
    };
  }

  Future<bool> finishWorkout({String? notes}) async {
    if (_currentWorkout == null) return false;

    try {
      final startTime = _currentWorkout!['startTime'] as DateTime;
      final durationMinutes = DateTime.now().difference(startTime).inMinutes;
      final stats = getActiveWorkoutStats();

      // Create Session via API
      final sessionData = {
        'workoutPlanId': _currentWorkout!['workoutPlanId'],
        'name': _currentWorkout!['name'],
        'startTime': startTime.toIso8601String(),
        'endTime': DateTime.now().toIso8601String(),
        'durationMinutes': durationMinutes < 1 ? 1 : durationMinutes,
        'caloriesBurned': durationMinutes * 8, // dummy calc
        'intensity': 7, // dummy
        'notes': notes ?? '',
        'isCompleted': true,
        'workoutType': 'WEIGHTLIFTING'
      };

      final createdSession = await _service.createSession(sessionData);
      
      if (createdSession != null) {
        final sessionId = createdSession['id'];
        
        // Also log sets
        for (final ex in _currentWorkout!['exercises'] as List) {
          final exerciseId = ex['exerciseId'] as int;
          for (final s in (ex as Map)['loggedSets'] as List) {
            if (s['done'] == true) {
              await _service.createSet({
                'exerciseId': exerciseId,
                'workoutSessionId': sessionId,
                'setNumber': s['set'],
                'reps': s['reps'],
                'weight': s['weight'],
                'isCompleted': true
              });
            }
          }
        }
        
        // Optimistic UI Update
        createdSession['date'] = createdSession['startTime']; 
        createdSession['exercises'] = _currentWorkout!['exercises'];
        createdSession['totalSets'] = stats['completedSets'];
        createdSession['totalVolumeKg'] = stats['volume'];
        
        _workoutSessions.insert(0, createdSession);
      }

      _currentWorkout = null;
      skipRest();
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Speichern: $e';
      notifyListeners();
      return false;
    }
  }

  void cancelWorkout() {
    _currentWorkout = null;
    skipRest();
    notifyListeners();
  }

  List<Map<String, dynamic>> getWorkoutsForWeek(DateTime weekStart) {
    final weekEnd = weekStart.add(const Duration(days: 7));
    return _workoutSessions.where((w) {
      final date = w['date'] != null ? DateTime.parse(w['date'].toString()) : DateTime.now();
      return !date.isBefore(weekStart) && date.isBefore(weekEnd);
    }).toList();
  }

  Map<String, dynamic> getWeeklyStats() {
    final now = DateTime.now();
    final weekStart = DateTime(now.year, now.month, now.day)
        .subtract(Duration(days: now.weekday - 1));
    final weekWorkouts = getWorkoutsForWeek(weekStart);

    return {
      'workouts': weekWorkouts.length,
      'goal': 5,
      'totalMinutes': weekWorkouts.fold<int>(
        0,
        (sum, w) => sum + (w['durationMinutes'] as int? ?? 0),
      ),
      'totalVolume': weekWorkouts.fold<double>(
        0,
        (sum, w) => sum + ((w['totalVolumeKg'] as num?)?.toDouble() ?? 0),
      ),
      'streak': currentStreak,
    };
  }

  List<double> getVolumeChartPoints({int weeks = 8}) {
    final now = DateTime.now();
    return List.generate(weeks, (i) {
      final start = now.subtract(Duration(days: (weeks - i) * 7));
      final end = start.add(const Duration(days: 7));
      return _workoutSessions.where((w) {
        final d = w['date'] != null ? DateTime.parse(w['date'].toString()) : DateTime.now();
        return !d.isBefore(start) && d.isBefore(end);
      }).fold<double>(
        0,
        (s, w) => s + ((w['totalVolumeKg'] as num?)?.toDouble() ?? 0),
      );
    });
  }

  int _calculateStreak() {
    if (_workoutSessions.isEmpty) return 0;
    final sorted = List<Map<String, dynamic>>.from(_workoutSessions)
      ..sort((a, b) {
        final dateA = a['date'] != null ? DateTime.parse(a['date'].toString()) : DateTime.now();
        final dateB = b['date'] != null ? DateTime.parse(b['date'].toString()) : DateTime.now();
        return dateB.compareTo(dateA);
      });

    int streak = 0;
    DateTime? lastDate;

    for (final session in sorted) {
      final date = session['date'] != null ? DateTime.parse(session['date'].toString()) : DateTime.now();
      if (lastDate == null) {
        final daysDiff = DateTime.now().difference(date).inDays;
        if (daysDiff > 7) return 0;
        streak = 1;
        lastDate = date;
      } else {
        final daysDiff = lastDate.difference(date).inDays;
        if (daysDiff <= 7) {
          streak++;
          lastDate = date;
        } else {
          break;
        }
      }
    }
    return streak;
  }

  double _sessionVolume(Map<String, dynamic> session) {
    double v = 0;
    for (final ex in (session['exercises'] as List? ?? [])) {
      v += ((ex['weight'] as num?) ?? 0) * ((ex['reps'] as num?) ?? 0) * ((ex['sets'] as num?) ?? 1);
    }
    return v;
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _restTimer?.cancel();
    super.dispose();
  }
}
