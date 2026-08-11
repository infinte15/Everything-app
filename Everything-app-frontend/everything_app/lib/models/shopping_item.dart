/// Woher eine Zeile der Einkaufsliste stammt.
///
/// Der Unterschied ist sichtbar und nicht nur Buchhaltung: "Aus Wochenplan
/// aufbauen" verwirft die nicht abgehakten [mealPlan]-Zeilen und lässt
/// [manual] stehen. Wer das nicht sieht, verliert seine eigenen Einträge.
enum ShoppingItemSource {
  manual('MANUAL', 'Eigener Eintrag'),
  mealPlan('MEAL_PLAN', 'Wochenplan');

  const ShoppingItemSource(this.wire, this.label);

  final String wire;
  final String label;

  static ShoppingItemSource fromWire(String? value) {
    for (final source in ShoppingItemSource.values) {
      if (source.wire == value) return source;
    }
    return ShoppingItemSource.manual;
  }
}

/// Eine Zeile der Einkaufsliste.
class ShoppingItem {
  final int? id;
  final String name;
  final double? amount;
  final String? unit;

  /// Das Ladenregal, vom Server über `IngredientAisleClassifier` bestimmt.
  final String category;
  final bool isChecked;
  final DateTime? checkedAt;
  final ShoppingItemSource source;
  final DateTime? createdAt;

  const ShoppingItem({
    this.id,
    required this.name,
    this.amount,
    this.unit,
    this.category = 'Sonstiges',
    this.isChecked = false,
    this.checkedAt,
    this.source = ShoppingItemSource.manual,
    this.createdAt,
  });

  factory ShoppingItem.fromJson(Map<String, dynamic> json) {
    return ShoppingItem(
      id: json['id'],
      name: json['name'] ?? '',
      amount: (json['amount'] as num?)?.toDouble(),
      unit: json['unit'],
      category: json['category'] ?? 'Sonstiges',
      isChecked: json['isChecked'] ?? false,
      checkedAt:
          json['checkedAt'] is String ? DateTime.tryParse(json['checkedAt']) : null,
      source: ShoppingItemSource.fromWire(json['source']),
      createdAt:
          json['createdAt'] is String ? DateTime.tryParse(json['createdAt']) : null,
    );
  }

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'name': name,
        'amount': amount,
        'unit': unit,
        'category': category,
        'isChecked': isChecked,
        'source': source.wire,
      };

  ShoppingItem copyWith({
    int? id,
    String? name,
    double? amount,
    String? unit,
    String? category,
    bool? isChecked,
    DateTime? checkedAt,
    ShoppingItemSource? source,
  }) {
    return ShoppingItem(
      id: id ?? this.id,
      name: name ?? this.name,
      amount: amount ?? this.amount,
      unit: unit ?? this.unit,
      category: category ?? this.category,
      isChecked: isChecked ?? this.isChecked,
      checkedAt: checkedAt ?? this.checkedAt,
      source: source ?? this.source,
      createdAt: createdAt,
    );
  }

  @override
  String toString() => 'ShoppingItem($name, $category, checked: $isChecked)';
}
