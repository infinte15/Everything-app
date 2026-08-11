import 'meal_type.dart';

/// Eine geplante Mahlzeit an einem Tag.
class MealPlan {
  final int? id;
  final DateTime date;
  final MealType mealType;
  final int recipeId;
  final String? recipeName;
  final String? recipeImageUrl;
  final int plannedServings;
  final bool isCompleted;
  final DateTime? completedAt;
  final String? notes;

  /// Die Aufgabe, die der Planer daraus gemacht hat - nur lesbar. Gesetzt wird
  /// sie über [MealPlan.toJson] mit `scheduleCooking`.
  final int? cookingTaskId;
  final DateTime? createdAt;

  const MealPlan({
    this.id,
    required this.date,
    required this.mealType,
    required this.recipeId,
    this.recipeName,
    this.recipeImageUrl,
    this.plannedServings = 1,
    this.isCompleted = false,
    this.completedAt,
    this.notes,
    this.cookingTaskId,
    this.createdAt,
  });

  factory MealPlan.fromJson(Map<String, dynamic> json) {
    return MealPlan(
      id: json['id'],
      date: DateTime.parse(json['date']),
      mealType: MealType.tryParse(json['mealType']) ?? MealType.abendessen,
      recipeId: (json['recipeId'] as num?)?.toInt() ?? 0,
      recipeName: json['recipeName'],
      recipeImageUrl: json['recipeImageUrl'],
      plannedServings: (json['plannedServings'] as num?)?.toInt() ?? 1,
      isCompleted: json['isCompleted'] ?? false,
      completedAt: json['completedAt'] is String
          ? DateTime.tryParse(json['completedAt'])
          : null,
      notes: json['notes'],
      cookingTaskId: (json['cookingTaskId'] as num?)?.toInt(),
      createdAt:
          json['createdAt'] is String ? DateTime.tryParse(json['createdAt']) : null,
    );
  }

  /// [scheduleCooking] legt serverseitig eine Aufgabe an, die der Smart
  /// Scheduler in den Kalender legt.
  Map<String, dynamic> toJson({bool scheduleCooking = false}) => {
        if (id != null) 'id': id,
        'date': isoDate(date),
        'mealType': mealType.wire,
        'recipeId': recipeId,
        'plannedServings': plannedServings,
        'isCompleted': isCompleted,
        'notes': notes,
        'scheduleCooking': scheduleCooking,
      };

  /// Datum ohne Zeitanteil - der Server bindet ein `LocalDate`, ein voller
  /// Zeitstempel gibt 400.
  ///
  /// Von Hand zusammengesetzt und nicht über `toIso8601String()`: das liefert
  /// bei einem `DateTime` in UTC den Vortag, wenn es lokal schon der nächste ist.
  static String isoDate(DateTime day) =>
      '${day.year.toString().padLeft(4, '0')}-'
      '${day.month.toString().padLeft(2, '0')}-'
      '${day.day.toString().padLeft(2, '0')}';

  bool isOn(DateTime day) =>
      date.year == day.year && date.month == day.month && date.day == day.day;

  MealPlan copyWith({
    int? id,
    DateTime? date,
    MealType? mealType,
    int? recipeId,
    String? recipeName,
    String? recipeImageUrl,
    int? plannedServings,
    bool? isCompleted,
    DateTime? completedAt,
    String? notes,
    int? cookingTaskId,
  }) {
    return MealPlan(
      id: id ?? this.id,
      date: date ?? this.date,
      mealType: mealType ?? this.mealType,
      recipeId: recipeId ?? this.recipeId,
      recipeName: recipeName ?? this.recipeName,
      recipeImageUrl: recipeImageUrl ?? this.recipeImageUrl,
      plannedServings: plannedServings ?? this.plannedServings,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
      notes: notes ?? this.notes,
      cookingTaskId: cookingTaskId ?? this.cookingTaskId,
      createdAt: createdAt,
    );
  }

  @override
  String toString() => 'MealPlan(${isoDate(date)} ${mealType.wire} → $recipeName)';
}
