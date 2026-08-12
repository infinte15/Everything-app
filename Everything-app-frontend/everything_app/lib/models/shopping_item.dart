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
  ///
  /// **Muss nullbar sein und darf nicht mitgeschickt werden, wenn nichts
  /// dasteht.** Der Server ordnet nur dann selbst ein, wenn das Feld leer
  /// ankommt (`ShoppingListService.addManualItem`). Solange hier `'Sonstiges'`
  /// als Vorgabe stand und `toJson` sie mitschickte, hat der Klassifizierer mit
  /// seinen acht Regalen und ~200 Stichwörtern für keinen einzigen Eintrag je
  /// gelaufen - weder für eine Zutat von der Rezeptseite noch für einen von Hand
  /// angelegten Eintrag ohne gewähltes Regal. Alles landete unter "Sonstiges",
  /// während der Kommentar hier das Gegenteil behauptete.
  ///
  /// Für die Anzeige gibt es [aisle].
  final String? category;
  final bool isChecked;
  final DateTime? checkedAt;
  final ShoppingItemSource source;
  final DateTime? createdAt;

  const ShoppingItem({
    this.id,
    required this.name,
    this.amount,
    this.unit,
    this.category,
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
      category: json['category'],
      isChecked: json['isChecked'] ?? false,
      checkedAt:
          json['checkedAt'] is String ? DateTime.tryParse(json['checkedAt']) : null,
      source: ShoppingItemSource.fromWire(json['source']),
      createdAt:
          json['createdAt'] is String ? DateTime.tryParse(json['createdAt']) : null,
    );
  }

  /// Das Regal für die Anzeige - unsortiertes gehört unter "Sonstiges".
  String get aisle => category ?? 'Sonstiges';

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'name': name,
        'amount': amount,
        'unit': unit,
        // Weglassen, nicht null schicken: nur ein fehlender Schlüssel lässt den
        // Server selbst einsortieren.
        if (category != null) 'category': category,
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
  String toString() => 'ShoppingItem($name, $aisle, checked: $isChecked)';
}
