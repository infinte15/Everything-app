/// Eine erteilte PSD2-Zustimmung.
///
/// [status] und [lastSyncError] sind nicht optional zu behandeln: eine
/// abgelaufene Zustimmung lässt den Abruf sonst stumm versiegen, und der Nutzer
/// hält Monate alte Zahlen für aktuell.
class BankConnection {
  final int id;
  final String aspspName;
  final String aspspCountry;
  final String status;
  final DateTime? validUntil;
  final DateTime? lastSyncAt;
  final String? lastSyncError;
  final int? daysUntilExpiry;
  final DateTime? createdAt;

  BankConnection({
    required this.id,
    required this.aspspName,
    this.aspspCountry = 'DE',
    required this.status,
    this.validUntil,
    this.lastSyncAt,
    this.lastSyncError,
    this.daysUntilExpiry,
    this.createdAt,
  });

  factory BankConnection.fromJson(Map<String, dynamic> json) {
    return BankConnection(
      id: json['id'],
      aspspName: json['aspspName'] ?? '',
      aspspCountry: json['aspspCountry'] ?? 'DE',
      status: json['status'] ?? 'PENDING',
      validUntil:
          json['validUntil'] != null ? DateTime.parse(json['validUntil']) : null,
      lastSyncAt:
          json['lastSyncAt'] != null ? DateTime.parse(json['lastSyncAt']) : null,
      lastSyncError: json['lastSyncError'],
      daysUntilExpiry: (json['daysUntilExpiry'] as num?)?.toInt(),
      createdAt:
          json['createdAt'] != null ? DateTime.parse(json['createdAt']) : null,
    );
  }

  bool get isActive => status == 'ACTIVE';

  /// Zustimmung weg - hier hilft nur neu einrichten, verlängern geht nicht.
  bool get needsReconnect => status == 'EXPIRED' || status == 'REVOKED';

  bool get hasFailed => status == 'FAILED';

  /// Vorwarnung, solange man noch in Ruhe reagieren kann.
  bool get expiresSoon =>
      isActive && daysUntilExpiry != null && daysUntilExpiry! <= 14;
}
