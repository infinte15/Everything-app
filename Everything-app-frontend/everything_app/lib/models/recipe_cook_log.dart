/// Ein Eintrag im Kochprotokoll: wann das Rezept gekocht wurde, für wie viele
/// und was dabei aufgefallen ist.
class RecipeCookLog {
  final int? id;
  final int? recipeId;
  final String? recipeName;
  final DateTime cookedAt;
  final int? rating;
  final int? servings;
  final String? note;

  const RecipeCookLog({
    this.id,
    this.recipeId,
    this.recipeName,
    required this.cookedAt,
    this.rating,
    this.servings,
    this.note,
  });

  factory RecipeCookLog.fromJson(Map<String, dynamic> json) {
    return RecipeCookLog(
      id: json['id'],
      recipeId: (json['recipeId'] as num?)?.toInt(),
      recipeName: json['recipeName'],
      cookedAt: DateTime.tryParse(json['cookedAt'] ?? '') ?? DateTime.now(),
      rating: (json['rating'] as num?)?.toInt(),
      servings: (json['servings'] as num?)?.toInt(),
      note: json['note'],
    );
  }

  /// `cookedAt` bleibt weg, wenn es der aktuelle Zeitpunkt sein soll - der
  /// Server setzt ihn dann selbst.
  Map<String, dynamic> toJson({bool includeCookedAt = false}) => {
        if (includeCookedAt) 'cookedAt': cookedAt.toIso8601String(),
        'rating': rating,
        'servings': servings,
        'note': note,
      };

  @override
  String toString() => 'RecipeCookLog($cookedAt, $rating★)';
}
