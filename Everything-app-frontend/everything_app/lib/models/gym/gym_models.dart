/// Datenmodelle des Gym-Bereichs.
///
/// Die Feldnamen folgen bewusst den Backend-DTOs (`setNumber`, `isCompleted`,
/// `estimatedDurationMinutes`), damit zwischen JSON und Modell nicht umbenannt werden muss.
library;

// ─────────────────────────────────────────────────────────────────────────────
// Satz-Arten
// ─────────────────────────────────────────────────────────────────────────────

enum GymSetType { normal, warmup, drop, failure, amrap, singleLeft, singleRight }

extension GymSetTypeLabel on GymSetType {
  /// Kürzel in der Satz-Zeile. Normale Sätze zeigen ihre Nummer, kein Kürzel.
  String get short {
    switch (this) {
      case GymSetType.warmup:
        return 'A';
      case GymSetType.drop:
        return 'D';
      case GymSetType.failure:
        return 'F';
      case GymSetType.amrap:
        return 'M';
      case GymSetType.singleLeft:
        return 'L';
      case GymSetType.singleRight:
        return 'R';
      case GymSetType.normal:
        return '';
    }
  }

  String get label {
    switch (this) {
      case GymSetType.warmup:
        return 'Aufwärmsatz';
      case GymSetType.drop:
        return 'Dropsatz';
      case GymSetType.failure:
        return 'Bis zum Versagen';
      case GymSetType.amrap:
        return 'Maximale Wiederholungen';
      case GymSetType.singleLeft:
        return 'Einseitig (links)';
      case GymSetType.singleRight:
        return 'Einseitig (rechts)';
      case GymSetType.normal:
        return 'Normaler Satz';
    }
  }

  /// Name, den das Backend erwartet (`SetType`).
  String get apiValue => name.toUpperCase();
}

GymSetType gymSetTypeFrom(dynamic value) {
  if (value == null) return GymSetType.normal;
  final normalized = value.toString().toLowerCase();
  return GymSetType.values.firstWhere(
    (t) => t.name == normalized,
    orElse: () => GymSetType.normal,
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Übungen
// ─────────────────────────────────────────────────────────────────────────────

class GymExercise {
  final int id;
  final String name;
  final String? description;
  final String? instructions;
  final String muscleGroup;
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final String equipment;
  final String difficulty;
  final String category;
  final String? mechanic;
  final String? imageUrl;
  final String? imageUrlEnd;
  final int? defaultRestSeconds;
  final bool isSystem;

  const GymExercise({
    required this.id,
    required this.name,
    this.description,
    this.instructions,
    this.muscleGroup = '',
    this.primaryMuscles = const [],
    this.secondaryMuscles = const [],
    this.equipment = '',
    this.difficulty = '',
    this.category = '',
    this.mechanic,
    this.imageUrl,
    this.imageUrlEnd,
    this.defaultRestSeconds,
    this.isSystem = false,
  });

  factory GymExercise.fromMap(Map<String, dynamic> m) => GymExercise(
        id: m['id'] as int? ?? 0,
        name: m['name'] as String? ?? '',
        description: m['description'] as String?,
        instructions: m['instructions'] as String?,
        muscleGroup: m['muscleGroup'] as String? ?? '',
        primaryMuscles: _stringList(m['primaryMuscles']),
        secondaryMuscles: _stringList(m['secondaryMuscles']),
        equipment: m['equipment'] as String? ?? '',
        difficulty: m['difficulty'] as String? ?? '',
        category: m['category'] as String? ?? '',
        mechanic: m['mechanic'] as String?,
        imageUrl: m['imageUrl'] as String?,
        imageUrlEnd: m['imageUrlEnd'] as String?,
        defaultRestSeconds: m['defaultRestSeconds'] as int?,
        isSystem: m['isSystem'] as bool? ?? false,
      );

  /// Führender Muskel für Einfärbung und Platzhalter-Grafik.
  String get leadMuscle =>
      primaryMuscles.isNotEmpty ? primaryMuscles.first : muscleGroup;
}

class GymMuscleOption {
  final String slug;
  final String label;

  const GymMuscleOption({required this.slug, required this.label});

  factory GymMuscleOption.fromMap(Map<String, dynamic> m) => GymMuscleOption(
        slug: m['slug'] as String? ?? '',
        label: m['label'] as String? ?? '',
      );
}

// ─────────────────────────────────────────────────────────────────────────────
// Routinen
// ─────────────────────────────────────────────────────────────────────────────

class GymRoutineExercise {
  final int? id;
  final int exerciseId;
  final String exerciseName;
  final String? imageUrl;
  final String equipment;
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final int orderIndex;
  final int targetSets;
  final int? targetRepsMin;
  final int? targetRepsMax;
  final double? targetWeight;
  final int? restSeconds;
  final String? notes;

  const GymRoutineExercise({
    this.id,
    required this.exerciseId,
    required this.exerciseName,
    this.imageUrl,
    this.equipment = '',
    this.primaryMuscles = const [],
    this.secondaryMuscles = const [],
    this.orderIndex = 0,
    this.targetSets = 3,
    this.targetRepsMin,
    this.targetRepsMax,
    this.targetWeight,
    this.restSeconds,
    this.notes,
  });

  factory GymRoutineExercise.fromMap(Map<String, dynamic> m) => GymRoutineExercise(
        id: m['id'] as int?,
        exerciseId: m['exerciseId'] as int? ?? 0,
        exerciseName: m['exerciseName'] as String? ?? '',
        imageUrl: m['imageUrl'] as String?,
        equipment: m['equipment'] as String? ?? '',
        primaryMuscles: _stringList(m['primaryMuscles']),
        secondaryMuscles: _stringList(m['secondaryMuscles']),
        orderIndex: m['orderIndex'] as int? ?? 0,
        targetSets: m['targetSets'] as int? ?? 3,
        targetRepsMin: m['targetRepsMin'] as int?,
        targetRepsMax: m['targetRepsMax'] as int?,
        targetWeight: (m['targetWeight'] as num?)?.toDouble(),
        restSeconds: m['restSeconds'] as int?,
        notes: m['notes'] as String?,
      );

  Map<String, dynamic> toRequest() => {
        'exerciseId': exerciseId,
        'targetSets': targetSets,
        if (targetRepsMin != null) 'targetRepsMin': targetRepsMin,
        if (targetRepsMax != null) 'targetRepsMax': targetRepsMax,
        if (targetWeight != null) 'targetWeight': targetWeight,
        if (restSeconds != null) 'restSeconds': restSeconds,
        if (notes != null) 'notes': notes,
      };

  GymRoutineExercise copyWith({
    int? targetSets,
    int? targetRepsMin,
    int? targetRepsMax,
    double? targetWeight,
    int? restSeconds,
  }) =>
      GymRoutineExercise(
        id: id,
        exerciseId: exerciseId,
        exerciseName: exerciseName,
        imageUrl: imageUrl,
        equipment: equipment,
        primaryMuscles: primaryMuscles,
        secondaryMuscles: secondaryMuscles,
        orderIndex: orderIndex,
        targetSets: targetSets ?? this.targetSets,
        targetRepsMin: targetRepsMin ?? this.targetRepsMin,
        targetRepsMax: targetRepsMax ?? this.targetRepsMax,
        targetWeight: targetWeight ?? this.targetWeight,
        restSeconds: restSeconds ?? this.restSeconds,
        notes: notes,
      );

  /// "8-12", "10" oder null, je nachdem was hinterlegt ist.
  String? get repRange {
    if (targetRepsMin == null && targetRepsMax == null) return null;
    if (targetRepsMin != null && targetRepsMax != null) {
      return targetRepsMin == targetRepsMax
          ? '$targetRepsMin'
          : '$targetRepsMin-$targetRepsMax';
    }
    return '${targetRepsMin ?? targetRepsMax}';
  }
}

class GymRoutine {
  final int id;
  final String name;
  final String? description;
  final String? imageUrl;
  final String? dayLabel;
  final int? estimatedDurationMinutes;
  final int orderIndex;
  final int exerciseCount;
  final int totalSets;
  final List<String> primaryMuscles;
  final List<String> previewImageUrls;
  final int? workoutPlanId;
  final DateTime? lastPerformedAt;
  final int performCount;
  final List<GymRoutineExercise> exercises;

  const GymRoutine({
    required this.id,
    required this.name,
    this.description,
    this.imageUrl,
    this.dayLabel,
    this.estimatedDurationMinutes,
    this.orderIndex = 0,
    this.exerciseCount = 0,
    this.totalSets = 0,
    this.primaryMuscles = const [],
    this.previewImageUrls = const [],
    this.workoutPlanId,
    this.lastPerformedAt,
    this.performCount = 0,
    this.exercises = const [],
  });

  factory GymRoutine.fromMap(Map<String, dynamic> m) => GymRoutine(
        id: m['id'] as int? ?? 0,
        name: m['name'] as String? ?? '',
        description: m['description'] as String?,
        imageUrl: m['imageUrl'] as String?,
        dayLabel: m['dayLabel'] as String?,
        estimatedDurationMinutes: m['estimatedDurationMinutes'] as int?,
        orderIndex: m['orderIndex'] as int? ?? 0,
        exerciseCount: m['exerciseCount'] as int? ?? 0,
        totalSets: m['totalSets'] as int? ?? 0,
        primaryMuscles: _stringList(m['primaryMuscles']),
        previewImageUrls: _stringList(m['previewImageUrls']),
        workoutPlanId: m['workoutPlanId'] as int?,
        lastPerformedAt: _parseDate(m['lastPerformedAt']),
        performCount: m['performCount'] as int? ?? 0,
        exercises: _mapList(m['exercises'], GymRoutineExercise.fromMap),
      );

  /// Bild für die Karte: eigenes Titelbild, sonst das erste Übungsbild.
  String? get coverImageUrl =>
      imageUrl ?? (previewImageUrls.isNotEmpty ? previewImageUrls.first : null);
}

// ─────────────────────────────────────────────────────────────────────────────
// Protokollierte Sätze und Einheiten
// ─────────────────────────────────────────────────────────────────────────────

class GymLoggedSet {
  int setNumber;
  double? weight;
  int? reps;
  bool isCompleted;
  GymSetType setType;
  int? restSeconds;
  int? rpe;
  DateTime? performedAt;

  GymLoggedSet({
    required this.setNumber,
    this.weight,
    this.reps,
    this.isCompleted = false,
    this.setType = GymSetType.normal,
    this.restSeconds,
    this.rpe,
    this.performedAt,
  });

  factory GymLoggedSet.fromMap(Map<String, dynamic> m) => GymLoggedSet(
        setNumber: m['setNumber'] as int? ?? 1,
        weight: (m['weight'] as num?)?.toDouble(),
        reps: m['reps'] as int?,
        isCompleted: m['isCompleted'] as bool? ?? false,
        setType: gymSetTypeFrom(m['setType']),
        restSeconds: m['restSeconds'] as int?,
        rpe: m['rpe'] as int?,
        performedAt: _parseDate(m['performedAt']),
      );

  Map<String, dynamic> toRequest() => {
        'setNumber': setNumber,
        if (weight != null) 'weight': weight,
        if (reps != null) 'reps': reps,
        'isCompleted': isCompleted,
        'setType': setType.apiValue,
        if (restSeconds != null) 'restSeconds': restSeconds,
        if (rpe != null) 'rpe': rpe,
      };

  Map<String, dynamic> toCache() => {
        'setNumber': setNumber,
        'weight': weight,
        'reps': reps,
        'isCompleted': isCompleted,
        'setType': setType.name,
        'restSeconds': restSeconds,
        'rpe': rpe,
      };

  double get volume =>
      isCompleted ? (weight ?? 0) * (reps ?? 0) : 0;

  /// "80 kg × 8" für die "vorher"-Spalte.
  String get summary {
    if (weight == null && reps == null) return '-';
    final w = weight != null ? '${_trimNumber(weight!)} kg' : '';
    final r = reps != null ? '${reps!}' : '';
    if (w.isEmpty) return r;
    if (r.isEmpty) return w;
    return '$w × $r';
  }
}

/// Ein Übungsblock im laufenden Training.
class GymWorkoutExercise {
  final int exerciseId;
  final String name;
  final String? imageUrl;
  final String equipment;
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final int? routineExerciseId;

  /// Nicht final: im laufenden Training über [SportsProvider.setExerciseRestSeconds]
  /// anpassbar, statt nur einmal aus der Routine übernommen zu werden.
  int? restSeconds;
  final int? targetRepsMin;
  final int? targetRepsMax;
  final List<GymLoggedSet> previous;
  final double? personalRecordWeight;
  List<GymLoggedSet> sets;

  GymWorkoutExercise({
    required this.exerciseId,
    required this.name,
    this.imageUrl,
    this.equipment = '',
    this.primaryMuscles = const [],
    this.secondaryMuscles = const [],
    this.routineExerciseId,
    this.restSeconds,
    this.targetRepsMin,
    this.targetRepsMax,
    this.previous = const [],
    this.personalRecordWeight,
    required this.sets,
  });

  /// Baut den Block aus einer geplanten Übung inklusive leerer Zielsätze.
  factory GymWorkoutExercise.fromPlanned(Map<String, dynamic> m) {
    final previous = _mapList(m['previous'], GymLoggedSet.fromMap);
    final targetSets = m['targetSets'] as int? ?? 3;
    final targetWeight = (m['targetWeight'] as num?)?.toDouble();
    final targetReps = m['targetRepsMin'] as int?;

    return GymWorkoutExercise(
      exerciseId: m['exerciseId'] as int? ?? 0,
      name: m['name'] as String? ?? '',
      imageUrl: m['imageUrl'] as String?,
      equipment: m['equipment'] as String? ?? '',
      primaryMuscles: _stringList(m['primaryMuscles']),
      secondaryMuscles: _stringList(m['secondaryMuscles']),
      routineExerciseId: m['routineExerciseId'] as int?,
      restSeconds: m['restSeconds'] as int?,
      targetRepsMin: m['targetRepsMin'] as int?,
      targetRepsMax: m['targetRepsMax'] as int?,
      previous: previous,
      personalRecordWeight: (m['personalRecordWeight'] as num?)?.toDouble(),
      sets: List.generate(
        targetSets < 1 ? 1 : targetSets,
        (i) => GymLoggedSet(
          setNumber: i + 1,
          // Vorschlag aus der letzten Einheit, sonst aus der Routine.
          weight: i < previous.length ? previous[i].weight : targetWeight,
          reps: i < previous.length ? previous[i].reps : targetReps,
        ),
      ),
    );
  }

  factory GymWorkoutExercise.fromExercise(GymExercise exercise, {int sets = 3}) =>
      GymWorkoutExercise(
        exerciseId: exercise.id,
        name: exercise.name,
        imageUrl: exercise.imageUrl,
        equipment: exercise.equipment,
        primaryMuscles: exercise.primaryMuscles,
        secondaryMuscles: exercise.secondaryMuscles,
        restSeconds: exercise.defaultRestSeconds,
        sets: List.generate(sets, (i) => GymLoggedSet(setNumber: i + 1)),
      );

  factory GymWorkoutExercise.fromCache(Map<String, dynamic> m) => GymWorkoutExercise(
        exerciseId: m['exerciseId'] as int? ?? 0,
        name: m['name'] as String? ?? '',
        imageUrl: m['imageUrl'] as String?,
        equipment: m['equipment'] as String? ?? '',
        primaryMuscles: _stringList(m['primaryMuscles']),
        secondaryMuscles: _stringList(m['secondaryMuscles']),
        routineExerciseId: m['routineExerciseId'] as int?,
        restSeconds: m['restSeconds'] as int?,
        previous: _mapList(m['previous'], GymLoggedSet.fromMap),
        personalRecordWeight: (m['personalRecordWeight'] as num?)?.toDouble(),
        sets: _mapList(m['sets'], GymLoggedSet.fromMap),
      );

  Map<String, dynamic> toCache() => {
        'exerciseId': exerciseId,
        'name': name,
        'imageUrl': imageUrl,
        'equipment': equipment,
        'primaryMuscles': primaryMuscles,
        'secondaryMuscles': secondaryMuscles,
        'routineExerciseId': routineExerciseId,
        'restSeconds': restSeconds,
        'personalRecordWeight': personalRecordWeight,
        'previous': previous.map((s) => s.toCache()).toList(),
        'sets': sets.map((s) => s.toCache()).toList(),
      };

  Map<String, dynamic> toRequest(int orderIndex) => {
        'exerciseId': exerciseId,
        'orderIndex': orderIndex,
        if (restSeconds != null) 'restSeconds': restSeconds,
        if (routineExerciseId != null) 'routineExerciseId': routineExerciseId,
        // Nur abgehakte Sätze zählen als trainiert.
        'sets': sets.where((s) => s.isCompleted).map((s) => s.toRequest()).toList(),
      };

  int get completedSets => sets.where((s) => s.isCompleted).length;

  double get volume => sets.fold(0.0, (sum, s) => sum + s.volume);
}

class GymSession {
  final int id;
  final String name;
  final DateTime? startTime;
  final int durationMinutes;
  final int totalSets;
  final double totalVolumeKg;
  final String notes;
  final int? routineId;
  final String? routineName;
  final bool isCompleted;
  final List<GymWorkoutExercise> exercises;

  const GymSession({
    required this.id,
    required this.name,
    this.startTime,
    this.durationMinutes = 0,
    this.totalSets = 0,
    this.totalVolumeKg = 0,
    this.notes = '',
    this.routineId,
    this.routineName,
    this.isCompleted = false,
    this.exercises = const [],
  });

  factory GymSession.fromMap(Map<String, dynamic> m) => GymSession(
        id: m['id'] as int? ?? 0,
        name: m['name'] as String? ?? 'Training',
        // Das Backend liefert einen ISO-String, kein DateTime.
        startTime: _parseDate(m['startTime']),
        durationMinutes: m['durationMinutes'] as int? ?? 0,
        totalSets: m['totalSets'] as int? ?? 0,
        totalVolumeKg: (m['totalVolumeKg'] as num?)?.toDouble() ?? 0,
        notes: m['notes'] as String? ?? '',
        routineId: m['routineId'] as int?,
        routineName: m['routineName'] as String?,
        isCompleted: m['isCompleted'] as bool? ?? false,
        exercises: _mapList(m['exercises'], _sessionExerciseFromMap),
      );

  static GymWorkoutExercise _sessionExerciseFromMap(Map<String, dynamic> m) =>
      GymWorkoutExercise(
        exerciseId: m['exerciseId'] as int? ?? 0,
        name: m['name'] as String? ?? '',
        imageUrl: m['imageUrl'] as String?,
        equipment: m['equipment'] as String? ?? '',
        primaryMuscles: _stringList(m['primaryMuscles']),
        restSeconds: m['restSeconds'] as int?,
        sets: _mapList(m['sets'], GymLoggedSet.fromMap),
      );
}

// ─────────────────────────────────────────────────────────────────────────────
// Auswertungen
// ─────────────────────────────────────────────────────────────────────────────

class GymMuscleVolume {
  final String muscle;
  final String label;
  final double volumeKg;
  final double weightedSets;
  final int sessionCount;

  /// 0..1 relativ zum am stärksten belasteten Muskel - direkt die Einfärbung.
  final double share;

  const GymMuscleVolume({
    required this.muscle,
    required this.label,
    this.volumeKg = 0,
    this.weightedSets = 0,
    this.sessionCount = 0,
    this.share = 0,
  });

  factory GymMuscleVolume.fromMap(Map<String, dynamic> m) => GymMuscleVolume(
        muscle: m['muscle'] as String? ?? '',
        label: m['label'] as String? ?? '',
        volumeKg: (m['volumeKg'] as num?)?.toDouble() ?? 0,
        weightedSets: (m['weightedSets'] as num?)?.toDouble() ?? 0,
        sessionCount: m['sessionCount'] as int? ?? 0,
        share: (m['share'] as num?)?.toDouble() ?? 0,
      );
}

class GymVolumePoint {
  final DateTime? weekStart;
  final double volumeKg;
  final int workouts;
  final int minutes;

  const GymVolumePoint({
    this.weekStart,
    this.volumeKg = 0,
    this.workouts = 0,
    this.minutes = 0,
  });

  factory GymVolumePoint.fromMap(Map<String, dynamic> m) => GymVolumePoint(
        weekStart: _parseDate(m['weekStart']),
        volumeKg: (m['volumeKg'] as num?)?.toDouble() ?? 0,
        workouts: m['workouts'] as int? ?? 0,
        minutes: m['minutes'] as int? ?? 0,
      );
}

class GymWeeklyStats {
  final DateTime? weekStart;
  final int workoutsCompleted;
  final int? workoutGoal;
  final int totalMinutes;
  final double totalVolumeKg;
  final int totalSets;
  final int currentStreakWeeks;
  final int longestStreakWeeks;
  final List<GymVolumePoint> volumeSeries;

  const GymWeeklyStats({
    this.weekStart,
    this.workoutsCompleted = 0,
    this.workoutGoal,
    this.totalMinutes = 0,
    this.totalVolumeKg = 0,
    this.totalSets = 0,
    this.currentStreakWeeks = 0,
    this.longestStreakWeeks = 0,
    this.volumeSeries = const [],
  });

  factory GymWeeklyStats.fromMap(Map<String, dynamic> m) => GymWeeklyStats(
        weekStart: _parseDate(m['weekStart']),
        workoutsCompleted: m['workoutsCompleted'] as int? ?? 0,
        workoutGoal: m['workoutGoal'] as int?,
        totalMinutes: m['totalMinutes'] as int? ?? 0,
        totalVolumeKg: (m['totalVolumeKg'] as num?)?.toDouble() ?? 0,
        totalSets: m['totalSets'] as int? ?? 0,
        currentStreakWeeks: m['currentStreakWeeks'] as int? ?? 0,
        longestStreakWeeks: m['longestStreakWeeks'] as int? ?? 0,
        volumeSeries: _mapList(m['volumeSeries'], GymVolumePoint.fromMap),
      );

  /// Fortschritt zum Wochenziel, 0..1. Ohne aktives Programm gibt es kein Ziel.
  double? get goalProgress {
    final goal = workoutGoal;
    if (goal == null || goal <= 0) return null;
    return (workoutsCompleted / goal).clamp(0.0, 1.0);
  }
}

class GymPersonalRecord {
  final int exerciseId;
  final String exerciseName;
  final double? maxWeight;
  final int? maxWeightReps;
  final DateTime? maxWeightAt;
  final int? maxReps;
  final double? maxSetVolumeKg;
  final double? best1RM;
  final int totalSetsAllTime;

  const GymPersonalRecord({
    required this.exerciseId,
    this.exerciseName = '',
    this.maxWeight,
    this.maxWeightReps,
    this.maxWeightAt,
    this.maxReps,
    this.maxSetVolumeKg,
    this.best1RM,
    this.totalSetsAllTime = 0,
  });

  factory GymPersonalRecord.fromMap(Map<String, dynamic> m) => GymPersonalRecord(
        exerciseId: m['exerciseId'] as int? ?? 0,
        exerciseName: m['exerciseName'] as String? ?? '',
        maxWeight: (m['maxWeight'] as num?)?.toDouble(),
        maxWeightReps: m['maxWeightReps'] as int?,
        maxWeightAt: _parseDate(m['maxWeightAt']),
        maxReps: m['maxReps'] as int?,
        maxSetVolumeKg: (m['maxSetVolumeKg'] as num?)?.toDouble(),
        best1RM: (m['best1RM'] as num?)?.toDouble(),
        totalSetsAllTime: m['totalSetsAllTime'] as int? ?? 0,
      );

  bool get hasData => totalSetsAllTime > 0;
}

class GymHistoryEntry {
  final int sessionId;
  final String sessionName;
  final DateTime? performedAt;
  final List<GymLoggedSet> sets;
  final double totalVolumeKg;
  final int totalSets;
  final double? bestSetWeight;
  final int? bestSetReps;

  /// Geschätztes Einer-Maximum (Epley) aus dem schwersten Satz der Einheit.
  final double? estimated1RM;

  const GymHistoryEntry({
    required this.sessionId,
    this.sessionName = '',
    this.performedAt,
    this.sets = const [],
    this.totalVolumeKg = 0,
    this.totalSets = 0,
    this.bestSetWeight,
    this.bestSetReps,
    this.estimated1RM,
  });

  factory GymHistoryEntry.fromMap(Map<String, dynamic> m) => GymHistoryEntry(
        sessionId: m['sessionId'] as int? ?? 0,
        sessionName: m['sessionName'] as String? ?? '',
        performedAt: _parseDate(m['performedAt']),
        sets: _mapList(m['sets'], GymLoggedSet.fromMap),
        totalVolumeKg: (m['totalVolumeKg'] as num?)?.toDouble() ?? 0,
        totalSets: m['totalSets'] as int? ?? 0,
        bestSetWeight: (m['bestSetWeight'] as num?)?.toDouble(),
        bestSetReps: m['bestSetReps'] as int?,
        estimated1RM: (m['estimated1RM'] as num?)?.toDouble(),
      );
}

// ─────────────────────────────────────────────────────────────────────────────
// Hilfsfunktionen
// ─────────────────────────────────────────────────────────────────────────────

List<String> _stringList(dynamic value) {
  if (value is! List) return const [];
  return value.map((e) => e.toString()).toList();
}

List<T> _mapList<T>(dynamic value, T Function(Map<String, dynamic>) build) {
  if (value is! List) return const [];
  return value
      .whereType<Map>()
      .map((e) => build(Map<String, dynamic>.from(e)))
      .toList();
}

DateTime? _parseDate(dynamic value) {
  if (value == null) return null;
  if (value is DateTime) return value;
  // Das Backend formatiert Zeitstempel als "yyyy-MM-dd HH:mm:ss" (siehe
  // spring.jackson.date-format), reine Datumsfelder dagegen als "yyyy-MM-dd".
  return DateTime.tryParse(value.toString().replaceFirst(' ', 'T'));
}

String _trimNumber(double value) {
  if (value == value.roundToDouble()) return value.toInt().toString();
  return value.toStringAsFixed(1);
}

/// Formatiert Gewichte einheitlich (75 statt 75.0, aber 77.5 bleibt).
String gymFormatNumber(double value) => _trimNumber(value);

/// Volumen kompakt: ab einer Tonne in t, darunter in kg.
String gymFormatVolume(double kg) {
  if (kg >= 1000) {
    final tons = kg / 1000;
    return '${tons.toStringAsFixed(tons >= 10 ? 0 : 1)} t';
  }
  return '${kg.round()} kg';
}
