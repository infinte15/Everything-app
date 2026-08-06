import 'contract.dart';

/// Was bis Monatsende noch bleibt.
///
/// [available] ist bewusst nullbar: ohne verbundenes Konto gibt es keinen
/// Kontostand, und eine erfundene Null wäre schlimmer als keine Zahl. Die
/// Oberfläche zeigt in dem Fall den "Konto verbinden"-Zustand.
class FinanceForecast {
  final String month;
  final DateTime monthStart;
  final DateTime monthEnd;

  final double? currentBalance;
  final double? available;

  final double upcomingContractExpenses;
  final double upcomingContractIncome;
  final double projectedVariableExpenses;
  final double averageDailyVariableExpenses;

  final int daysRemaining;

  /// Der einzige Anlass für Rot in diesem Space.
  final bool shortfall;

  final double monthIncome;
  final double monthExpenses;

  final List<ForecastPoint> series;

  /// Was in den Resttagen noch ansteht - als Liste, nicht nur als Summe.
  final List<Contract> upcoming;

  FinanceForecast({
    required this.month,
    required this.monthStart,
    required this.monthEnd,
    this.currentBalance,
    this.available,
    this.upcomingContractExpenses = 0,
    this.upcomingContractIncome = 0,
    this.projectedVariableExpenses = 0,
    this.averageDailyVariableExpenses = 0,
    this.daysRemaining = 0,
    this.shortfall = false,
    this.monthIncome = 0,
    this.monthExpenses = 0,
    this.series = const [],
    this.upcoming = const [],
  });

  factory FinanceForecast.fromJson(Map<String, dynamic> json) {
    return FinanceForecast(
      month: json['month'] ?? '',
      monthStart: DateTime.parse(json['monthStart']),
      monthEnd: DateTime.parse(json['monthEnd']),
      currentBalance: (json['currentBalance'] as num?)?.toDouble(),
      available: (json['available'] as num?)?.toDouble(),
      upcomingContractExpenses:
          (json['upcomingContractExpenses'] as num?)?.toDouble() ?? 0,
      upcomingContractIncome:
          (json['upcomingContractIncome'] as num?)?.toDouble() ?? 0,
      projectedVariableExpenses:
          (json['projectedVariableExpenses'] as num?)?.toDouble() ?? 0,
      averageDailyVariableExpenses:
          (json['averageDailyVariableExpenses'] as num?)?.toDouble() ?? 0,
      daysRemaining: (json['daysRemaining'] as num?)?.toInt() ?? 0,
      shortfall: json['shortfall'] ?? false,
      monthIncome: (json['monthIncome'] as num?)?.toDouble() ?? 0,
      monthExpenses: (json['monthExpenses'] as num?)?.toDouble() ?? 0,
      series: ((json['series'] as List?) ?? [])
          .map((e) => ForecastPoint.fromJson(e))
          .toList(),
      upcoming: ((json['upcoming'] as List?) ?? [])
          .map((e) => Contract.fromJson(e))
          .toList(),
    );
  }

  /// Ohne Bankanbindung gibt es keine Kernzahl - die Oberfläche zeigt dann statt
  /// einer Zahl die Aufforderung, ein Konto zu verbinden.
  bool get hasBalance => available != null;
}

/// Ein Punkt der Saldokurve.
///
/// [projected] trennt Ist von Prognose: der projizierte Teil wird gestrichelt
/// gezeichnet, eine durchgehende Linie gäbe eine Gewissheit vor, die es nicht
/// gibt.
class ForecastPoint {
  final DateTime date;
  final double balance;
  final bool projected;

  ForecastPoint({
    required this.date,
    required this.balance,
    required this.projected,
  });

  factory ForecastPoint.fromJson(Map<String, dynamic> json) {
    return ForecastPoint(
      date: DateTime.parse(json['date']),
      balance: (json['balance'] as num?)?.toDouble() ?? 0,
      projected: json['projected'] ?? false,
    );
  }
}
