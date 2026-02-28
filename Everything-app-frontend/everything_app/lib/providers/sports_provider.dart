import 'package:flutter/material.dart';

class SportsProvider with ChangeNotifier {
  List<Map<String, dynamic>> _workoutPlans = [];
  List<Map<String, dynamic>> _workoutSessions = [];
  List<Map<String, dynamic>> _exercises = [];
  Map<String, dynamic>? _currentWorkout;
  
  bool _isLoading = false;
  String? _error;


  bool get isLoading => _isLoading;
  String? get error => _error;
  List<Map<String, dynamic>> get workoutPlans => _workoutPlans;
  List<Map<String, dynamic>> get workoutSessions => _workoutSessions;
  List<Map<String, dynamic>> get exercises => _exercises;
  Map<String, dynamic>? get currentWorkout => _currentWorkout;


  int get totalWorkouts => _workoutSessions.length;
  int get currentStreak => _calculateStreak();
  double get totalHours => _workoutSessions.fold<double>(
    0, (sum, w) => sum + ((w['durationMinutes'] as int) / 60.0));

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
      _error = 'Fehler beim Laden der Sportdaten: $e';
    }
    
    _isLoading = false;
    notifyListeners();
  }


  Future<void> _loadWorkoutPlans() async {
    // TODO: API Call -> GET /api/sports/workout-plans
    await Future.delayed(const Duration(milliseconds: 300));
    
    _workoutPlans = [
      {
        'id': 1,
        'name': 'Oberkörper',
        'day': 'Monday',
        'exercises': [
          {'name': 'Bankdrücken', 'sets': 4, 'reps': 8, 'weight': 80},
          {'name': 'Schulterdrücken', 'sets': 3, 'reps': 10, 'weight': 30},
          {'name': 'Bizepscurls', 'sets': 3, 'reps': 12, 'weight': 15},
          {'name': 'Trizeps-Dips', 'sets': 3, 'reps': 10, 'weight': 0},
        ],
        'estimatedDuration': 60,
      },
      {
        'id': 2,
        'name': 'Unterkörper',
        'day': 'Wednesday',
        'exercises': [
          {'name': 'Kniebeugen', 'sets': 4, 'reps': 8, 'weight': 100},
          {'name': 'Kreuzheben', 'sets': 3, 'reps': 6, 'weight': 120},
          {'name': 'Beinpresse', 'sets': 3, 'reps': 10, 'weight': 150},
          {'name': 'Wadenheben', 'sets': 4, 'reps': 15, 'weight': 60},
        ],
        'estimatedDuration': 75,
      },
      {
        'id': 3,
        'name': 'Core & Cardio',
        'day': 'Friday',
        'exercises': [
          {'name': 'Plank', 'sets': 3, 'reps': 60, 'weight': 0}, // reps = seconds
          {'name': 'Crunch', 'sets': 4, 'reps': 20, 'weight': 0},
          {'name': 'Russian Twist', 'sets': 3, 'reps': 30, 'weight': 10},
          {'name': 'Laufen', 'sets': 1, 'reps': 30, 'weight': 0}, // reps = minutes
        ],
        'estimatedDuration': 45,
      },
    ];
  }

 
  Future<void> _loadWorkoutSessions() async {
    // TODO: API Call -> GET /api/sports/workout-sessions
    await Future.delayed(const Duration(milliseconds: 300));
    
    _workoutSessions = [
      {
        'id': 1,
        'workoutPlanId': 1,
        'name': 'Oberkörper',
        'date': DateTime.now().subtract(const Duration(days: 1)),
        'durationMinutes': 62,
        'exercises': [
          {'name': 'Bankdrücken', 'sets': 4, 'reps': 8, 'weight': 80},
          {'name': 'Schulterdrücken', 'sets': 3, 'reps': 10, 'weight': 30},
          {'name': 'Bizepscurls', 'sets': 3, 'reps': 12, 'weight': 15},
        ],
        'totalSets': 15,
        'notes': 'Gutes Training, letzte Sätze schwer',
      },
      {
        'id': 2,
        'workoutPlanId': 2,
        'name': 'Unterkörper',
        'date': DateTime.now().subtract(const Duration(days: 3)),
        'durationMinutes': 78,
        'exercises': [
          {'name': 'Kniebeugen', 'sets': 4, 'reps': 8, 'weight': 100},
          {'name': 'Kreuzheben', 'sets': 3, 'reps': 6, 'weight': 120},
        ],
        'totalSets': 18,
        'notes': 'Schwere Gewichte, gute Form',
      },
      {
        'id': 3,
        'workoutPlanId': 3,
        'name': 'Core & Cardio',
        'date': DateTime.now().subtract(const Duration(days: 5)),
        'durationMinutes': 45,
        'exercises': [
          {'name': 'Plank', 'sets': 3, 'reps': 60, 'weight': 0},
          {'name': 'Laufen', 'sets': 1, 'reps': 30, 'weight': 0},
        ],
        'totalSets': 10,
        'notes': 'Cardio war hart',
      },
    ];
  }

 
  Future<void> _loadExercises() async {
    // TODO: API Call -> GET /api/sports/exercises
    await Future.delayed(const Duration(milliseconds: 200));
    
    _exercises = [
      {
        'id': 1,
        'name': 'Bankdrücken',
        'category': 'Chest',
        'equipment': 'Barbell',
        'difficulty': 'intermediate',
        'muscleGroups': ['Chest', 'Triceps', 'Shoulders'],
      },
      {
        'id': 2,
        'name': 'Kniebeugen',
        'category': 'Legs',
        'equipment': 'Barbell',
        'difficulty': 'intermediate',
        'muscleGroups': ['Quads', 'Glutes', 'Hamstrings'],
      },
      {
        'id': 3,
        'name': 'Kreuzheben',
        'category': 'Back',
        'equipment': 'Barbell',
        'difficulty': 'advanced',
        'muscleGroups': ['Back', 'Glutes', 'Hamstrings'],
      },
      {
        'id': 4,
        'name': 'Schulterdrücken',
        'category': 'Shoulders',
        'equipment': 'Dumbbells',
        'difficulty': 'beginner',
        'muscleGroups': ['Shoulders', 'Triceps'],
      },
      {
        'id': 5,
        'name': 'Plank',
        'category': 'Core',
        'equipment': 'Bodyweight',
        'difficulty': 'beginner',
        'muscleGroups': ['Core', 'Abs'],
      },
    ];
  }


  Future<bool> startWorkout(int workoutPlanId) async {
    try {
      final plan = _workoutPlans.firstWhere((p) => p['id'] == workoutPlanId);
      
      _currentWorkout = {
        'workoutPlanId': workoutPlanId,
        'name': plan['name'],
        'startTime': DateTime.now(),
        'exercises': List.from(plan['exercises']),
        'currentExerciseIndex': 0,
        'completedSets': 0,
      };
      
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Starten des Workouts: $e';
      notifyListeners();
      return false;
    }
  }


  void completeSet() {
    if (_currentWorkout == null) return;
    
    _currentWorkout!['completedSets'] = 
        (_currentWorkout!['completedSets'] as int) + 1;
    notifyListeners();
  }

  void nextExercise() {
    if (_currentWorkout == null) return;
    
    final exercises = _currentWorkout!['exercises'] as List;
    final currentIndex = _currentWorkout!['currentExerciseIndex'] as int;
    
    if (currentIndex < exercises.length - 1) {
      _currentWorkout!['currentExerciseIndex'] = currentIndex + 1;
      notifyListeners();
    }
  }


  Future<bool> finishWorkout({String? notes}) async {
    if (_currentWorkout == null) return false;
    
    try {
      final startTime = _currentWorkout!['startTime'] as DateTime;
      final durationMinutes = DateTime.now().difference(startTime).inMinutes;
      
      final session = {
        'id': _workoutSessions.length + 1,
        'workoutPlanId': _currentWorkout!['workoutPlanId'],
        'name': _currentWorkout!['name'],
        'date': DateTime.now(),
        'durationMinutes': durationMinutes,
        'exercises': _currentWorkout!['exercises'],
        'totalSets': _currentWorkout!['completedSets'],
        'notes': notes ?? '',
      };
      
      // TODO: API Call -> POST /api/sports/workout-sessions
      await Future.delayed(const Duration(milliseconds: 300));
      
      _workoutSessions.insert(0, session);
      _currentWorkout = null;
      
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Beenden des Workouts: $e';
      notifyListeners();
      return false;
    }
  }

 
  void cancelWorkout() {
    _currentWorkout = null;
    notifyListeners();
  }

  /// Get workouts for specific week
  List<Map<String, dynamic>> getWorkoutsForWeek(DateTime weekStart) {
    final weekEnd = weekStart.add(const Duration(days: 7));
    return _workoutSessions.where((w) {
      final date = w['date'] as DateTime;
      return date.isAfter(weekStart) && date.isBefore(weekEnd);
    }).toList();
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
        
        final today = DateTime.now();
        final daysDiff = today.difference(date).inDays;
        if (daysDiff > 1) return 0; 
        streak = 1;
        lastDate = date;
      } else {
        
        final daysDiff = lastDate.difference(date).inDays;
        if (daysDiff == 1) {
          streak++;
          lastDate = date;
        } else {
          break;
        }
      }
    }
    
    return streak;
  }

  
  Map<String, dynamic> getWeeklyStats() {
    final now = DateTime.now();
    final weekStart = now.subtract(Duration(days: now.weekday - 1));
    final weekWorkouts = getWorkoutsForWeek(weekStart);
    
    return {
      'workouts': weekWorkouts.length,
      'totalMinutes': weekWorkouts.fold<int>(
        0, (sum, w) => sum + (w['durationMinutes'] as int)),
      'totalSets': weekWorkouts.fold<int>(
        0, (sum, w) => sum + (w['totalSets'] as int)),
      'streak': currentStreak,
    };
  }

  
  void clearError() {
    _error = null;
    notifyListeners();
  }
}