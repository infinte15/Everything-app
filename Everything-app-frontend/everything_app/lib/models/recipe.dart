
class Recipe {
  final int? id;
  final String name;
  final String? description;
  final int prepTimeMinutes;
  final int cookTimeMinutes;
  final int servings;
  final String category;
  final String ingredients; 
  final String instructions; 
  
  final int? calories;
  final double? protein;
  final double? carbs;
  final double? fat;
  
  final String? difficulty; 
  final String? imageUrl;
  final String? tags;
  final bool isFavorite;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  Recipe({
    this.id,
    required this.name,
    this.description,
    required this.prepTimeMinutes,
    required this.cookTimeMinutes,
    required this.servings,
    required this.category,
    required this.ingredients,
    required this.instructions,
    this.calories,
    this.protein,
    this.carbs,
    this.fat,
    this.difficulty,
    this.imageUrl,
    this.tags,
    this.isFavorite = false,
    this.createdAt,
    this.updatedAt,
  });

  // JSON zu Recipe
  factory Recipe.fromJson(Map<String, dynamic> json) {
    return Recipe(
      id: json['id'],
      name: json['name'],
      description: json['description'],
      prepTimeMinutes: json['prepTimeMinutes'],
      cookTimeMinutes: json['cookTimeMinutes'],
      servings: json['servings'],
      category: json['category'],
      ingredients: json['ingredients'],
      instructions: json['instructions'],
      calories: json['calories'],
      protein: json['protein']?.toDouble(),
      carbs: json['carbs']?.toDouble(),
      fat: json['fat']?.toDouble(),
      difficulty: json['difficulty'],
      imageUrl: json['imageUrl'],
      tags: json['tags'],
      isFavorite: json['isFavorite'] ?? false,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'])
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'])
          : null,
    );
  }

  // Recipe zu JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'description': description,
      'prepTimeMinutes': prepTimeMinutes,
      'cookTimeMinutes': cookTimeMinutes,
      'servings': servings,
      'category': category,
      'ingredients': ingredients,
      'instructions': instructions,
      'calories': calories,
      'protein': protein,
      'carbs': carbs,
      'fat': fat,
      'difficulty': difficulty,
      'imageUrl': imageUrl,
      'tags': tags,
      'isFavorite': isFavorite,
    };
  }


  int get totalTimeMinutes => prepTimeMinutes + cookTimeMinutes;

  // Parse Zutaten als Liste
  List<String> get ingredientsList {
    return ingredients.split('\n').where((i) => i.trim().isNotEmpty).toList();
  }

  // Parse Anleitung als Schritte
  List<String> get instructionSteps {
    return instructions.split('\n').where((i) => i.trim().isNotEmpty).toList();
  }

  @override
  String toString() => 'Recipe(id: $id, name: $name)';
}