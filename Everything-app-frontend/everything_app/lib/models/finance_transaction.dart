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
  });

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
    };
  }
}
