enum GymSetType { normal, warmup, dropSet, failure, amrap }

extension GymSetTypeLabel on GymSetType {
  String get short {
    switch (this) {
      case GymSetType.warmup:
        return 'W';
      case GymSetType.dropSet:
        return 'D';
      case GymSetType.failure:
        return 'F';
      case GymSetType.amrap:
        return 'A';
      case GymSetType.normal:
        return '';
    }
  }

  String get label {
    switch (this) {
      case GymSetType.warmup:
        return 'Warm-up';
      case GymSetType.dropSet:
        return 'Drop set';
      case GymSetType.failure:
        return 'Failure';
      case GymSetType.amrap:
        return 'AMRAP';
      case GymSetType.normal:
        return 'Normal';
    }
  }
}

class GymExercise {
  final int id;
  final String name;
  final String category;
  final String equipment;
  final String difficulty;
  final List<String> muscleGroups;

  const GymExercise({
    required this.id,
    required this.name,
    required this.category,
    required this.equipment,
    required this.difficulty,
    required this.muscleGroups,
  });

  factory GymExercise.fromMap(Map<String, dynamic> m) => GymExercise(
        id: m['id'] as int? ?? 0,
        name: m['name'] as String? ?? '',
        category: m['category'] as String? ?? '',
        equipment: m['equipment'] as String? ?? '',
        difficulty: m['difficulty'] as String? ?? 'beginner',
        muscleGroups: (m['muscleGroups'] as List?)?.cast<String>() ?? [],
      );

  Map<String, dynamic> toMap() => {
        'id': id,
        'name': name,
        'category': category,
        'equipment': equipment,
        'difficulty': difficulty,
        'muscleGroups': muscleGroups,
      };
}

class GymLoggedSet {
  int setNumber;
  double weight;
  int reps;
  bool done;
  GymSetType type;
  int? rpe;

  GymLoggedSet({
    required this.setNumber,
    required this.weight,
    required this.reps,
    this.done = false,
    this.type = GymSetType.normal,
    this.rpe,
  });

  Map<String, dynamic> toMap() => {
        'set': setNumber,
        'weight': weight,
        'reps': reps,
        'done': done,
        'type': type.name,
        if (rpe != null) 'rpe': rpe,
      };

  factory GymLoggedSet.fromMap(Map<String, dynamic> m) => GymLoggedSet(
        setNumber: m['set'] as int? ?? 1,
        weight: (m['weight'] as num?)?.toDouble() ?? 0,
        reps: m['reps'] as int? ?? 0,
        done: m['done'] as bool? ?? false,
        type: GymSetType.values.firstWhere(
          (t) => t.name == m['type'],
          orElse: () => GymSetType.normal,
        ),
        rpe: m['rpe'] as int?,
      );

  double get volume => done ? weight * reps : 0;
}

class GymWorkoutExercise {
  final String name;
  final int exerciseId;
  List<GymLoggedSet> sets;

  GymWorkoutExercise({
    required this.name,
    this.exerciseId = 0,
    required this.sets,
  });

  Map<String, dynamic> toMap() => {
        'name': name,
        'exerciseId': exerciseId,
        'loggedSets': sets.map((s) => s.toMap()).toList(),
      };

  factory GymWorkoutExercise.fromMap(Map<String, dynamic> m) {
    final logged = (m['loggedSets'] as List?)
            ?.map((e) => GymLoggedSet.fromMap(Map<String, dynamic>.from(e as Map)))
            .toList() ??
        [];
    return GymWorkoutExercise(
      name: m['name'] as String? ?? '',
      exerciseId: m['exerciseId'] as int? ?? 0,
      sets: logged,
    );
  }
}

class GymRoutine {
  final int id;
  final String name;
  final String? day;
  final List<Map<String, dynamic>> exercises;
  final int estimatedMinutes;

  const GymRoutine({
    required this.id,
    required this.name,
    this.day,
    required this.exercises,
    this.estimatedMinutes = 60,
  });

  factory GymRoutine.fromMap(Map<String, dynamic> m) => GymRoutine(
        id: m['id'] as int? ?? 0,
        name: m['name'] as String? ?? '',
        day: m['day'] as String?,
        exercises: (m['exercises'] as List?)?.cast<Map<String, dynamic>>() ?? [],
        estimatedMinutes: m['estimatedDuration'] as int? ?? 60,
      );
}

class GymSession {
  final int id;
  final String name;
  final DateTime date;
  final int durationMinutes;
  final int totalSets;
  final double totalVolumeKg;
  final String notes;
  final List<Map<String, dynamic>> exercises;

  const GymSession({
    required this.id,
    required this.name,
    required this.date,
    required this.durationMinutes,
    required this.totalSets,
    required this.totalVolumeKg,
    this.notes = '',
    required this.exercises,
  });

  factory GymSession.fromMap(Map<String, dynamic> m) => GymSession(
        id: m['id'] as int? ?? 0,
        name: m['name'] as String? ?? 'Workout',
        date: m['date'] as DateTime? ?? DateTime.now(),
        durationMinutes: m['durationMinutes'] as int? ?? 0,
        totalSets: m['totalSets'] as int? ?? 0,
        totalVolumeKg: (m['totalVolumeKg'] as num?)?.toDouble() ?? 0,
        notes: m['notes'] as String? ?? '',
        exercises: (m['exercises'] as List?)?.cast<Map<String, dynamic>>() ?? [],
      );
}

class GymProgramTemplate {
  final String name;
  final String description;
  final String level;
  final int daysPerWeek;
  final List<GymRoutine> routines;

  const GymProgramTemplate({
    required this.name,
    required this.description,
    required this.level,
    required this.daysPerWeek,
    required this.routines,
  });
}
