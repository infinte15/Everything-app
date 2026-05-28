import 'dart:async';

import 'package:flutter/material.dart';

import '../models/gym/gym_models.dart';

class SportsProvider with ChangeNotifier {
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
      final d = w['date'] as DateTime? ?? DateTime.now();
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
    await Future.delayed(const Duration(milliseconds: 200));
    _workoutPlans = [
      {
        'id': 1,
        'name': 'Push Day',
        'day': 'Mon',
        'exercises': [
          {'name': 'Bench Press', 'sets': 4, 'reps': 8, 'weight': 80, 'exerciseId': 1},
          {'name': 'Incline Dumbbell Press', 'sets': 3, 'reps': 10, 'weight': 28, 'exerciseId': 6},
          {'name': 'Cable Fly', 'sets': 3, 'reps': 12, 'weight': 15, 'exerciseId': 7},
          {'name': 'Lateral Raise', 'sets': 3, 'reps': 15, 'weight': 10, 'exerciseId': 8},
        ],
        'estimatedDuration': 60,
      },
      {
        'id': 2,
        'name': 'Pull Day',
        'day': 'Wed',
        'exercises': [
          {'name': 'Deadlift', 'sets': 4, 'reps': 5, 'weight': 120, 'exerciseId': 3},
          {'name': 'Barbell Row', 'sets': 4, 'reps': 8, 'weight': 70, 'exerciseId': 9},
          {'name': 'Lat Pulldown', 'sets': 3, 'reps': 10, 'weight': 55, 'exerciseId': 10},
          {'name': 'Barbell Curl', 'sets': 3, 'reps': 12, 'weight': 25, 'exerciseId': 11},
        ],
        'estimatedDuration': 65,
      },
      {
        'id': 3,
        'name': 'Leg Day',
        'day': 'Fri',
        'exercises': [
          {'name': 'Squat', 'sets': 4, 'reps': 6, 'weight': 100, 'exerciseId': 2},
          {'name': 'Leg Press', 'sets': 3, 'reps': 10, 'weight': 150, 'exerciseId': 12},
          {'name': 'Leg Curl', 'sets': 3, 'reps': 12, 'weight': 40, 'exerciseId': 13},
          {'name': 'Calf Raise', 'sets': 4, 'reps': 15, 'weight': 60, 'exerciseId': 14},
        ],
        'estimatedDuration': 70,
      },
    ];
  }

  Future<void> _loadWorkoutSessions() async {
    await Future.delayed(const Duration(milliseconds: 200));
    final now = DateTime.now();
    _workoutSessions = [
      {
        'id': 1,
        'name': 'Push Day',
        'date': now.subtract(const Duration(days: 1)),
        'durationMinutes': 72,
        'exercises': [
          {'name': 'Bench Press', 'sets': 4, 'reps': 8, 'weight': 80},
          {'name': 'Incline Dumbbell Press', 'sets': 3, 'reps': 10, 'weight': 28},
        ],
        'totalSets': 16,
        'totalVolumeKg': 4548.0,
        'notes': 'Felt strong on bench',
      },
      {
        'id': 2,
        'name': 'Pull Day',
        'date': now.subtract(const Duration(days: 3)),
        'durationMinutes': 68,
        'exercises': [
          {'name': 'Deadlift', 'sets': 4, 'reps': 5, 'weight': 120},
          {'name': 'Barbell Row', 'sets': 4, 'reps': 8, 'weight': 70},
        ],
        'totalSets': 14,
        'totalVolumeKg': 3820.0,
        'notes': '',
      },
      {
        'id': 3,
        'name': 'Leg Day',
        'date': now.subtract(const Duration(days: 5)),
        'durationMinutes': 75,
        'exercises': [
          {'name': 'Squat', 'sets': 4, 'reps': 6, 'weight': 100},
        ],
        'totalSets': 12,
        'totalVolumeKg': 5200.0,
        'notes': 'PR on squat',
      },
    ];
  }

  Future<void> _loadExercises() async {
    await Future.delayed(const Duration(milliseconds: 150));
    _exercises = [
      {'id': 1, 'name': 'Bench Press', 'category': 'Chest', 'equipment': 'Barbell', 'difficulty': 'intermediate', 'muscleGroups': ['Chest', 'Triceps', 'Shoulders']},
      {'id': 2, 'name': 'Squat', 'category': 'Legs', 'equipment': 'Barbell', 'difficulty': 'intermediate', 'muscleGroups': ['Quads', 'Glutes']},
      {'id': 3, 'name': 'Deadlift', 'category': 'Back', 'equipment': 'Barbell', 'difficulty': 'advanced', 'muscleGroups': ['Back', 'Glutes', 'Hamstrings']},
      {'id': 4, 'name': 'Overhead Press', 'category': 'Shoulders', 'equipment': 'Barbell', 'difficulty': 'intermediate', 'muscleGroups': ['Shoulders', 'Triceps']},
      {'id': 5, 'name': 'Dumbbell Bench Press', 'category': 'Chest', 'equipment': 'Dumbbell', 'difficulty': 'beginner', 'muscleGroups': ['Chest', 'Triceps']},
      {'id': 6, 'name': 'Incline Dumbbell Press', 'category': 'Chest', 'equipment': 'Dumbbell', 'difficulty': 'intermediate', 'muscleGroups': ['Chest']},
      {'id': 7, 'name': 'Cable Fly', 'category': 'Chest', 'equipment': 'Cable', 'difficulty': 'beginner', 'muscleGroups': ['Chest']},
      {'id': 8, 'name': 'Lateral Raise', 'category': 'Shoulders', 'equipment': 'Dumbbell', 'difficulty': 'beginner', 'muscleGroups': ['Shoulders']},
      {'id': 9, 'name': 'Barbell Row', 'category': 'Back', 'equipment': 'Barbell', 'difficulty': 'intermediate', 'muscleGroups': ['Back', 'Biceps']},
      {'id': 10, 'name': 'Lat Pulldown', 'category': 'Back', 'equipment': 'Cable', 'difficulty': 'beginner', 'muscleGroups': ['Back', 'Biceps']},
      {'id': 11, 'name': 'Barbell Curl', 'category': 'Arms', 'equipment': 'Barbell', 'difficulty': 'beginner', 'muscleGroups': ['Biceps']},
      {'id': 12, 'name': 'Leg Press', 'category': 'Legs', 'equipment': 'Machine', 'difficulty': 'beginner', 'muscleGroups': ['Quads', 'Glutes']},
      {'id': 13, 'name': 'Leg Curl', 'category': 'Legs', 'equipment': 'Machine', 'difficulty': 'beginner', 'muscleGroups': ['Hamstrings']},
      {'id': 14, 'name': 'Calf Raise', 'category': 'Legs', 'equipment': 'Machine', 'difficulty': 'beginner', 'muscleGroups': ['Calves']},
      {'id': 15, 'name': 'Triceps Pushdown', 'category': 'Arms', 'equipment': 'Cable', 'difficulty': 'beginner', 'muscleGroups': ['Triceps']},
      {'id': 16, 'name': 'Face Pull', 'category': 'Shoulders', 'equipment': 'Cable', 'difficulty': 'beginner', 'muscleGroups': ['Shoulders', 'Back']},
      {'id': 17, 'name': 'Romanian Deadlift', 'category': 'Legs', 'equipment': 'Barbell', 'difficulty': 'intermediate', 'muscleGroups': ['Hamstrings', 'Glutes']},
      {'id': 18, 'name': 'Pull-up', 'category': 'Back', 'equipment': 'Bodyweight', 'difficulty': 'intermediate', 'muscleGroups': ['Back', 'Biceps']},
    ];
  }

  void addRoutine({
    required String name,
    String? day,
    required List<Map<String, dynamic>> exercises,
    int estimatedMinutes = 60,
  }) {
    _workoutPlans.add({
      'id': _workoutPlans.length + 1,
      'name': name,
      'day': day,
      'exercises': exercises,
      'estimatedDuration': estimatedMinutes,
    });
    notifyListeners();
  }

  Future<bool> startWorkout(int workoutPlanId) async {
    try {
      final plan = _workoutPlans.firstWhere((p) => p['id'] == workoutPlanId);
      _initWorkout(
        name: plan['name'] as String? ?? 'Workout',
        planId: workoutPlanId,
        templateExercises: List<Map<String, dynamic>>.from(plan['exercises'] as List),
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
    return 0;
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

      final session = {
        'id': _workoutSessions.length + 1,
        'workoutPlanId': _currentWorkout!['workoutPlanId'],
        'name': _currentWorkout!['name'],
        'date': DateTime.now(),
        'durationMinutes': durationMinutes < 1 ? 1 : durationMinutes,
        'exercises': _currentWorkout!['exercises'],
        'totalSets': stats['completedSets'],
        'totalVolumeKg': stats['volume'],
        'notes': notes ?? '',
      };

      await Future.delayed(const Duration(milliseconds: 200));
      _workoutSessions.insert(0, session);
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
      final date = w['date'] as DateTime;
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
        (sum, w) => sum + (w['durationMinutes'] as int),
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
        final d = w['date'] as DateTime;
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
      ..sort((a, b) => (b['date'] as DateTime).compareTo(a['date'] as DateTime));

    int streak = 0;
    DateTime? lastDate;

    for (final session in sorted) {
      final date = session['date'] as DateTime;
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
