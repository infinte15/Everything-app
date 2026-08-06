class FinanceTransaction {
  final int? id;
  final double amount;
  final String type; // 'EINNAHME' or 'AUSGABE'
  final String category;
  final String? subcategory;
  final String description;
  final DateTime transactionDate;
  final String? paymentMethod;
  final String? tags;
  final String? receiptUrl;
  final bool isRecurring;
  final String? recurringFrequency;
  final int? budgetCategoryId;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  // ── Bankimport ─────────────────────────────────────────────────────────────

  /// Name der Gegenpartei. Bei importierten Buchungen die große Zeile in der
  /// Liste, der Verwendungszweck die kleine. Bei getippten Buchungen `null`.
  final String? counterparty;

  /// 'MANUAL' oder 'BANK'. Bestandszeilen können `null` tragen und werden dann
  /// wie 'MANUAL' behandelt.
  final String? source;

  /// Der Nutzer hat die Kategorie selbst gesetzt - kein Vorschlag der Automatik.
  final bool categoryLocked;

  /// Gesetzt, wenn die Buchung zu einem erkannten Vertrag gehört.
  final int? contractId;

  final DateTime? valueDate;

  FinanceTransaction({
    this.id,
    required this.amount,
    required this.type,
    required this.category,
    this.subcategory,
    required this.description,
    required this.transactionDate,
    this.paymentMethod,
    this.tags,
    this.receiptUrl,
    this.isRecurring = false,
    this.recurringFrequency,
    this.budgetCategoryId,
    this.createdAt,
    this.updatedAt,
    this.counterparty,
    this.source,
    this.categoryLocked = false,
    this.contractId,
    this.valueDate,
  });

  bool get isIncome => type == 'EINNAHME';

  bool get isFromBank => source == 'BANK';

  /// Was in der Liste groß steht. Getippte Buchungen haben keine Gegenpartei,
  /// dort trägt die Beschreibung allein.
  String get title =>
      (counterparty != null && counterparty!.isNotEmpty) ? counterparty! : description;

  /// Die kleine Zeile darunter - leer, wenn sie nur den Titel wiederholen würde.
  String? get subtitle {
    if (counterparty == null || counterparty!.isEmpty) return null;
    if (description == counterparty) return null;
    return description;
  }

  FinanceTransaction copyWith({
    double? amount,
    String? type,
    String? category,
    String? subcategory,
    String? description,
    DateTime? transactionDate,
    String? paymentMethod,
    String? tags,
    bool? isRecurring,
    int? budgetCategoryId,
    String? counterparty,
    bool? categoryLocked,
  }) {
    return FinanceTransaction(
      id: id,
      amount: amount ?? this.amount,
      type: type ?? this.type,
      category: category ?? this.category,
      subcategory: subcategory ?? this.subcategory,
      description: description ?? this.description,
      transactionDate: transactionDate ?? this.transactionDate,
      paymentMethod: paymentMethod ?? this.paymentMethod,
      tags: tags ?? this.tags,
      receiptUrl: receiptUrl,
      isRecurring: isRecurring ?? this.isRecurring,
      recurringFrequency: recurringFrequency,
      budgetCategoryId: budgetCategoryId ?? this.budgetCategoryId,
      createdAt: createdAt,
      updatedAt: updatedAt,
      counterparty: counterparty ?? this.counterparty,
      source: source,
      categoryLocked: categoryLocked ?? this.categoryLocked,
      contractId: contractId,
      valueDate: valueDate,
    );
  }

  factory FinanceTransaction.fromJson(Map<String, dynamic> json) {
    return FinanceTransaction(
      id: json['id'],
      amount: (json['amount'] as num).toDouble(),
      type: json['type'],
      category: json['category'],
      subcategory: json['subcategory'],
      description: json['description'],
      transactionDate: DateTime.parse(json['transactionDate']),
      paymentMethod: json['paymentMethod'],
      tags: json['tags'],
      receiptUrl: json['receiptUrl'],
      isRecurring: json['isRecurring'] ?? false,
      recurringFrequency: json['recurringFrequency'],
      budgetCategoryId: json['budgetCategoryId'],
      createdAt: json['createdAt'] != null ? DateTime.parse(json['createdAt']) : null,
      updatedAt: json['updatedAt'] != null ? DateTime.parse(json['updatedAt']) : null,
      counterparty: json['counterparty'],
      source: json['source'],
      categoryLocked: json['categoryLocked'] ?? false,
      contractId: json['contractId'],
      valueDate:
          json['valueDate'] != null ? DateTime.parse(json['valueDate']) : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      if (id != null) 'id': id,
      'amount': amount,
      'type': type,
      'category': category,
      if (subcategory != null) 'subcategory': subcategory,
      'description': description,
      'transactionDate': transactionDate.toIso8601String().split('T')[0],
      if (paymentMethod != null) 'paymentMethod': paymentMethod,
      if (tags != null) 'tags': tags,
      if (receiptUrl != null) 'receiptUrl': receiptUrl,
      'isRecurring': isRecurring,
      if (recurringFrequency != null) 'recurringFrequency': recurringFrequency,
      if (budgetCategoryId != null) 'budgetCategoryId': budgetCategoryId,
      if (counterparty != null) 'counterparty': counterparty,
      // source, categoryLocked und contractId bleiben draußen: sie beschreiben
      // die Herkunft der Buchung und werden allein vom Backend gesetzt.
    };
  }
}
