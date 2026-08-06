/// Ein Budget für eine Kategorie.
///
/// Das Backend gibt es seit langem her; angeschlossen wurde es erst mit dem
/// Umbau des Finance Space - vorher standen im Budget-Tab hartcodierte Grenzen.
class BudgetCategory {
  final int? id;
  final String name;
  final double limitAmount;
  final String? period;
  final String? icon;
  final String? color;
  final bool isActive;

  BudgetCategory({
    this.id,
    required this.name,
    required this.limitAmount,
    this.period = 'MONTHLY',
    this.icon,
    this.color,
    this.isActive = true,
  });

  factory BudgetCategory.fromJson(Map<String, dynamic> json) {
    return BudgetCategory(
      id: json['id'],
      name: json['name'] ?? '',
      limitAmount: (json['limitAmount'] as num?)?.toDouble() ?? 0,
      period: json['period'],
      icon: json['icon'],
      color: json['color'],
      isActive: json['isActive'] ?? true,
    );
  }

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'name': name,
        'limitAmount': limitAmount,
        if (period != null) 'period': period,
        if (icon != null) 'icon': icon,
        if (color != null) 'color': color,
        'isActive': isActive,
      };
}

/// Verbrauch eines Budgets im laufenden Zeitraum - kommt fertig gerechnet vom
/// Backend (`/stats/budget-progress`).
class BudgetProgress {
  final int? categoryId;
  final String categoryName;
  final double limitAmount;
  final double spentAmount;
  final double remainingAmount;
  final double percentageUsed;
  final bool isOverBudget;

  BudgetProgress({
    this.categoryId,
    required this.categoryName,
    required this.limitAmount,
    required this.spentAmount,
    required this.remainingAmount,
    required this.percentageUsed,
    required this.isOverBudget,
  });

  factory BudgetProgress.fromJson(Map<String, dynamic> json) {
    final limit = (json['limitAmount'] as num?)?.toDouble() ?? 0;
    final spent = (json['spentAmount'] as num?)?.toDouble() ?? 0;
    return BudgetProgress(
      categoryId: json['categoryId'],
      categoryName: json['categoryName'] ?? '',
      limitAmount: limit,
      spentAmount: spent,
      remainingAmount:
          (json['remainingAmount'] as num?)?.toDouble() ?? (limit - spent),
      percentageUsed: (json['percentageUsed'] as num?)?.toDouble() ??
          (limit > 0 ? spent / limit * 100 : 0),
      isOverBudget: json['isOverBudget'] ?? (spent > limit),
    );
  }
}
