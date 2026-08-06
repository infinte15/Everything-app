/// Eine wiederkehrende Zahlung - Abo, Miete, Versicherung, aber auch das Gehalt.
///
/// [frequency] ist das Etikett für die Gruppierung, [intervalDays] der gemessene
/// Abstand; jede Datumsrechnung im Backend läuft über letzteren.
class Contract {
  final int id;
  final String name;
  final String category;
  final String? subcategory;

  /// 'INCOME' oder 'EXPENSE'.
  final String direction;

  /// Immer positiv - die Richtung steckt in [direction].
  final double amount;

  final String frequency;
  final int? intervalDays;
  final DateTime? lastBookingDate;
  final DateTime? nextDueDate;

  /// "Erkannt aus N Buchungen" - macht nachvollziehbar, woher der Vertrag kommt.
  final int occurrenceCount;

  final bool active;
  final DateTime? cancelledAt;

  /// `false` heißt: vom Nutzer angelegt oder korrigiert. Die Erkennung fasst ihn
  /// dann nicht mehr an.
  final bool detectedAutomatically;

  /// Auf einen Monat normalisiert, damit sich Verträge verschiedener Rhythmen
  /// summieren lassen.
  final double? monthlyAmount;

  Contract({
    required this.id,
    required this.name,
    required this.category,
    this.subcategory,
    required this.direction,
    required this.amount,
    required this.frequency,
    this.intervalDays,
    this.lastBookingDate,
    this.nextDueDate,
    this.occurrenceCount = 0,
    this.active = true,
    this.cancelledAt,
    this.detectedAutomatically = true,
    this.monthlyAmount,
  });

  factory Contract.fromJson(Map<String, dynamic> json) {
    return Contract(
      id: json['id'],
      name: json['name'] ?? '',
      category: json['category'] ?? 'Sonstiges',
      subcategory: json['subcategory'],
      direction: json['direction'] ?? 'EXPENSE',
      amount: (json['amount'] as num?)?.toDouble() ?? 0,
      frequency: json['frequency'] ?? 'MONTHLY',
      intervalDays: (json['intervalDays'] as num?)?.toInt(),
      lastBookingDate: json['lastBookingDate'] != null
          ? DateTime.parse(json['lastBookingDate'])
          : null,
      nextDueDate:
          json['nextDueDate'] != null ? DateTime.parse(json['nextDueDate']) : null,
      occurrenceCount: (json['occurrenceCount'] as num?)?.toInt() ?? 0,
      active: json['active'] ?? true,
      cancelledAt:
          json['cancelledAt'] != null ? DateTime.parse(json['cancelledAt']) : null,
      detectedAutomatically: json['detectedAutomatically'] ?? true,
      monthlyAmount: (json['monthlyAmount'] as num?)?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'category': category,
        if (subcategory != null) 'subcategory': subcategory,
        'direction': direction,
        'amount': amount,
        'frequency': frequency,
        if (intervalDays != null) 'intervalDays': intervalDays,
        if (nextDueDate != null)
          'nextDueDate': nextDueDate!.toIso8601String().split('T')[0],
        'active': active,
      };

  bool get isIncome => direction == 'INCOME';

  Contract copyWith({
    String? name,
    String? category,
    String? subcategory,
    String? direction,
    double? amount,
    String? frequency,
    int? intervalDays,
    DateTime? nextDueDate,
    bool? active,
  }) {
    return Contract(
      id: id,
      name: name ?? this.name,
      category: category ?? this.category,
      subcategory: subcategory ?? this.subcategory,
      direction: direction ?? this.direction,
      amount: amount ?? this.amount,
      frequency: frequency ?? this.frequency,
      intervalDays: intervalDays ?? this.intervalDays,
      lastBookingDate: lastBookingDate,
      nextDueDate: nextDueDate ?? this.nextDueDate,
      occurrenceCount: occurrenceCount,
      active: active ?? this.active,
      cancelledAt: cancelledAt,
      detectedAutomatically: detectedAutomatically,
      monthlyAmount: monthlyAmount,
    );
  }

  /// Deutsches Etikett des Rhythmus.
  String get frequencyLabel => switch (frequency) {
        'WEEKLY' => 'wöchentlich',
        'BIWEEKLY' => 'zweiwöchentlich',
        'MONTHLY' => 'monatlich',
        'BIMONTHLY' => 'zweimonatlich',
        'QUARTERLY' => 'vierteljährlich',
        'SEMIANNUAL' => 'halbjährlich',
        'YEARLY' => 'jährlich',
        _ => 'unregelmäßig',
      };
}
