import 'meal_type.dart';

/// Eine Zutatenzeile.
///
/// [amount] ist `double` und kein Dezimalpaket. Dart hat keinen `BigDecimal` im
/// Kern, und `package:decimal` für das Halbieren von Küchenmengen einzubinden
/// wäre unverhältnismäßig: die Zahlen haben höchstens drei Nachkommastellen,
/// und `250.0 * 0.5` ist in IEEE-754 exakt. Die eigentliche Arbeit steckt nicht
/// im Rechnen, sondern im Anzeigen - siehe `formatAmount` in
/// `screens/recipes/widgets/recipe_format.dart`, das aus 0,5 ein "½" macht.
class RecipeIngredient {
  final int? id;
  final double? amount;
  final String? unit;
  final String name;
  final String? note;

  /// Die Zeile, wie sie beim Import dastand. Nur zum Nachsehen, wenn der Parser
  /// sie falsch zerlegt hat.
  final String? rawText;
  final String? groupLabel;

  const RecipeIngredient({
    this.id,
    this.amount,
    this.unit,
    required this.name,
    this.note,
    this.rawText,
    this.groupLabel,
  });

  factory RecipeIngredient.fromJson(Map<String, dynamic> json) {
    return RecipeIngredient(
      id: json['id'],
      amount: (json['amount'] as num?)?.toDouble(),
      unit: json['unit'],
      name: json['name'] ?? '',
      note: json['note'],
      rawText: json['rawText'],
      groupLabel: json['groupLabel'],
    );
  }

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'amount': amount,
        'unit': unit,
        'name': name,
        'note': note,
        'rawText': rawText,
        'groupLabel': groupLabel,
      };

  /// Menge mal [factor]. Eine Zutat ohne Menge ("Salz, Pfeffer") behält keine -
  /// das Dreifache von "etwas" ist immer noch "etwas".
  RecipeIngredient scaled(double factor) {
    if (amount == null) return this;
    return copyWith(amount: amount! * factor);
  }

  RecipeIngredient copyWith({
    int? id,
    double? amount,
    String? unit,
    String? name,
    String? note,
    String? rawText,
    String? groupLabel,
    bool clearAmount = false,
  }) {
    return RecipeIngredient(
      id: id ?? this.id,
      amount: clearAmount ? null : (amount ?? this.amount),
      unit: unit ?? this.unit,
      name: name ?? this.name,
      note: note ?? this.note,
      rawText: rawText ?? this.rawText,
      groupLabel: groupLabel ?? this.groupLabel,
    );
  }

  @override
  String toString() => 'RecipeIngredient($amount $unit $name)';
}

/// Ein Zubereitungsschritt. Die Nummer setzt die Oberfläche, sie steht nicht im
/// Text - sonst liest man später "1. 1. Mehl sieben".
class RecipeStep {
  final int? id;
  final String text;

  const RecipeStep({this.id, required this.text});

  factory RecipeStep.fromJson(Map<String, dynamic> json) =>
      RecipeStep(id: json['id'], text: json['text'] ?? '');

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'text': text,
      };
}

class Recipe {
  final int? id;
  final String name;
  final String? description;
  final int prepTimeMinutes;
  final int cookTimeMinutes;
  final int servings;
  final String category;
  final Set<MealType> suitableFor;
  final List<RecipeIngredient> ingredients;
  final List<RecipeStep> steps;

  final int? calories;
  final double? protein;
  final double? carbs;
  final double? fat;

  final String? difficulty;
  final String? imageUrl;
  final String? tags;
  final bool isFavorite;

  /// 1..5. `null` heißt "nicht bewertet" und nicht "schlecht" - der Unterschied
  /// entscheidet, ob ein Rezept unter "Deine besten" auftaucht oder gar nicht.
  final int? rating;

  final int cookCount;
  final DateTime? lastCookedAt;
  final String? notes;
  final String? sourceUrl;
  final String? sourceName;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const Recipe({
    this.id,
    required this.name,
    this.description,
    required this.prepTimeMinutes,
    required this.cookTimeMinutes,
    required this.servings,
    required this.category,
    this.suitableFor = const {},
    this.ingredients = const [],
    this.steps = const [],
    this.calories,
    this.protein,
    this.carbs,
    this.fat,
    this.difficulty,
    this.imageUrl,
    this.tags,
    this.isFavorite = false,
    this.rating,
    this.cookCount = 0,
    this.lastCookedAt,
    this.notes,
    this.sourceUrl,
    this.sourceName,
    this.createdAt,
    this.updatedAt,
  });

  /// Leere Vorlage für den Editor.
  factory Recipe.blank() => const Recipe(
        name: '',
        prepTimeMinutes: 0,
        cookTimeMinutes: 0,
        servings: 2,
        category: 'Sonstiges',
      );

  /// Liest die Antwort des Servers - nachsichtig.
  ///
  /// Pflichtfelder bekommen eine Vorgabe und unbekannte Mahlzeiten werden
  /// übersprungen: eine einzelne krumme Zeile darf nicht die ganze Liste
  /// umbringen. Ein echter Serverfehler dagegen muss sichtbar werden, und dafür
  /// ist der Service zuständig, nicht diese Methode.
  factory Recipe.fromJson(Map<String, dynamic> json) {
    return Recipe(
      id: json['id'],
      name: json['name'] ?? '',
      description: json['description'],
      prepTimeMinutes: (json['prepTimeMinutes'] as num?)?.toInt() ?? 0,
      cookTimeMinutes: (json['cookTimeMinutes'] as num?)?.toInt() ?? 0,
      servings: (json['servings'] as num?)?.toInt() ?? 1,
      category: json['category'] ?? 'Sonstiges',
      suitableFor: ((json['suitableFor'] as List?) ?? const [])
          .map((value) => MealType.tryParse(value as String?))
          .whereType<MealType>()
          .toSet(),
      ingredients: ((json['ingredients'] as List?) ?? const [])
          .map((item) => RecipeIngredient.fromJson(item as Map<String, dynamic>))
          .toList(),
      steps: ((json['steps'] as List?) ?? const [])
          .map((item) => RecipeStep.fromJson(item as Map<String, dynamic>))
          .toList(),
      calories: (json['calories'] as num?)?.toInt(),
      protein: (json['protein'] as num?)?.toDouble(),
      carbs: (json['carbs'] as num?)?.toDouble(),
      fat: (json['fat'] as num?)?.toDouble(),
      difficulty: json['difficulty'],
      imageUrl: json['imageUrl'],
      tags: json['tags'],
      isFavorite: json['isFavorite'] ?? false,
      rating: (json['rating'] as num?)?.toInt(),
      cookCount: (json['cookCount'] as num?)?.toInt() ?? 0,
      lastCookedAt: _date(json['lastCookedAt']),
      notes: json['notes'],
      sourceUrl: json['sourceUrl'],
      sourceName: json['sourceName'],
      createdAt: _date(json['createdAt']),
      updatedAt: _date(json['updatedAt']),
    );
  }

  static DateTime? _date(dynamic value) =>
      value is String ? DateTime.tryParse(value) : null;

  /// `ingredientsText` und `instructionsText` gehen bewusst **nicht** mit: der
  /// Server rendert sie selbst und ignoriert sie beim Schreiben. Ein Abbild,
  /// das Felder erfindet, driftet.
  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'name': name,
        'description': description,
        'prepTimeMinutes': prepTimeMinutes,
        'cookTimeMinutes': cookTimeMinutes,
        'servings': servings,
        'category': category,
        'suitableFor': suitableFor.map((type) => type.wire).toList(),
        'ingredients': ingredients.map((i) => i.toJson()).toList(),
        'steps': steps.map((s) => s.toJson()).toList(),
        'calories': calories,
        'protein': protein,
        'carbs': carbs,
        'fat': fat,
        'difficulty': difficulty,
        'imageUrl': imageUrl,
        'tags': tags,
        'isFavorite': isFavorite,
        'rating': rating,
        'notes': notes,
        'sourceUrl': sourceUrl,
        'sourceName': sourceName,
      };

  int get totalTimeMinutes => prepTimeMinutes + cookTimeMinutes;

  List<String> get tagList => (tags ?? '')
      .split(',')
      .map((tag) => tag.trim())
      .where((tag) => tag.isNotEmpty)
      .toList();

  bool get hasNutrition =>
      calories != null || protein != null || carbs != null || fat != null;

  /// Die Zutaten für eine andere Portionszahl.
  ///
  /// Das ist der Portionsrechner der Detailseite. Früher lief er über eine
  /// Regex auf dem Zutaten-*Text* und schrieb "0.5 EL Senf"; jetzt rechnet er
  /// auf der Zahl, die der Server ohnehin schon getrennt hält.
  List<RecipeIngredient> scaledTo(int portions) {
    if (servings <= 0 || portions <= 0 || portions == servings) {
      return ingredients;
    }
    final factor = portions / servings;
    return ingredients.map((i) => i.scaled(factor)).toList();
  }

  Recipe copyWith({
    int? id,
    String? name,
    String? description,
    int? prepTimeMinutes,
    int? cookTimeMinutes,
    int? servings,
    String? category,
    Set<MealType>? suitableFor,
    List<RecipeIngredient>? ingredients,
    List<RecipeStep>? steps,
    int? calories,
    double? protein,
    double? carbs,
    double? fat,
    String? difficulty,
    String? imageUrl,
    String? tags,
    bool? isFavorite,
    int? rating,
    int? cookCount,
    DateTime? lastCookedAt,
    String? notes,
    String? sourceUrl,
    String? sourceName,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return Recipe(
      id: id ?? this.id,
      name: name ?? this.name,
      description: description ?? this.description,
      prepTimeMinutes: prepTimeMinutes ?? this.prepTimeMinutes,
      cookTimeMinutes: cookTimeMinutes ?? this.cookTimeMinutes,
      servings: servings ?? this.servings,
      category: category ?? this.category,
      suitableFor: suitableFor ?? this.suitableFor,
      ingredients: ingredients ?? this.ingredients,
      steps: steps ?? this.steps,
      calories: calories ?? this.calories,
      protein: protein ?? this.protein,
      carbs: carbs ?? this.carbs,
      fat: fat ?? this.fat,
      difficulty: difficulty ?? this.difficulty,
      imageUrl: imageUrl ?? this.imageUrl,
      tags: tags ?? this.tags,
      isFavorite: isFavorite ?? this.isFavorite,
      rating: rating ?? this.rating,
      cookCount: cookCount ?? this.cookCount,
      lastCookedAt: lastCookedAt ?? this.lastCookedAt,
      notes: notes ?? this.notes,
      sourceUrl: sourceUrl ?? this.sourceUrl,
      sourceName: sourceName ?? this.sourceName,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  String toString() => 'Recipe(id: $id, name: $name)';
}
