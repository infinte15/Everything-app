/// Ein Konto hinter einer Bankverbindung.
///
/// Die IBAN kommt nur als Endziffern vom Backend - die vollständige braucht die
/// Oberfläche nirgends.
class BankAccount {
  final int id;
  final String displayName;
  final String? ibanSuffix;
  final String currency;
  final double? currentBalance;
  final DateTime? balanceUpdatedAt;
  final bool syncEnabled;
  final int? connectionId;
  final String? aspspName;

  BankAccount({
    required this.id,
    required this.displayName,
    this.ibanSuffix,
    this.currency = 'EUR',
    this.currentBalance,
    this.balanceUpdatedAt,
    this.syncEnabled = true,
    this.connectionId,
    this.aspspName,
  });

  factory BankAccount.fromJson(Map<String, dynamic> json) {
    return BankAccount(
      id: json['id'],
      displayName: json['displayName'] ?? 'Konto',
      ibanSuffix: json['ibanSuffix'],
      currency: json['currency'] ?? 'EUR',
      currentBalance: (json['currentBalance'] as num?)?.toDouble(),
      balanceUpdatedAt: json['balanceUpdatedAt'] != null
          ? DateTime.parse(json['balanceUpdatedAt'])
          : null,
      syncEnabled: json['syncEnabled'] ?? true,
      connectionId: json['connectionId'],
      aspspName: json['aspspName'],
    );
  }

  /// "Girokonto ••2051"
  String get label =>
      ibanSuffix == null ? displayName : '$displayName ••$ibanSuffix';
}
