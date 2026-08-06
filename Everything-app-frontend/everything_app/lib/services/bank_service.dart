import 'dart:convert';

import '../config/api_config.dart';
import '../models/aspsp.dart';
import '../models/bank_account.dart';
import '../models/bank_connection.dart';
import 'api_service.dart';

/// Bankanbindung: Institut suchen, verbinden, abrufen, trennen.
class BankService {
  final ApiService _api = ApiService();

  /// Alle Institute eines Landes.
  ///
  /// Die Liste hat mehrere hundert Einträge - "Sparkasse" ist keine Bank,
  /// sondern ein Verbund aus regionalen Instituten. Die Suche im
  /// Verbinden-Bildschirm ist deshalb Pflicht, kein Komfort.
  Future<List<Aspsp>> getAspsps({String country = 'DE'}) async {
    final response = await _api.get(ApiConfig.bankAspsps(country));
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => Aspsp.fromJson(json)).toList();
    }
    throw Exception('Bankenliste konnte nicht geladen werden');
  }

  /// Startet die Zustimmung und liefert die URL für den externen Browser.
  Future<String> connect(Aspsp aspsp) async {
    final response = await _api.post(ApiConfig.bankConnect, {
      'aspspName': aspsp.name,
      'aspspCountry': aspsp.country,
    });
    if (response.statusCode == 200) {
      return json.decode(response.body)['authUrl'];
    }
    throw Exception(_api.getErrorMessage(response));
  }

  Future<List<BankConnection>> getConnections() async {
    final response = await _api.get(ApiConfig.bankConnections);
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => BankConnection.fromJson(json)).toList();
    }
    throw Exception('Bankverbindungen konnten nicht geladen werden');
  }

  Future<List<BankAccount>> getAccounts() async {
    final response = await _api.get(ApiConfig.bankAccounts);
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => BankAccount.fromJson(json)).toList();
    }
    throw Exception('Konten konnten nicht geladen werden');
  }

  Future<BankAccount> updateAccount(
    int id, {
    bool? syncEnabled,
    String? displayName,
  }) async {
    final response = await _api.patch(ApiConfig.bankAccountById(id), {
      'syncEnabled': ?syncEnabled,
      'displayName': ?displayName,
    });
    if (response.statusCode == 200) {
      return BankAccount.fromJson(json.decode(response.body));
    }
    throw Exception('Konto konnte nicht geändert werden');
  }

  /// Vom Nutzer ausgelöster Abruf.
  ///
  /// Zählt bei der Bank als beaufsichtigt und fällt deshalb nicht unter das
  /// Tageslimit - das Backend reicht dafür die Angaben dieses Aufrufs durch.
  Future<BankSyncResult> sync() async {
    final response = await _api.post(ApiConfig.bankSync, {});
    if (response.statusCode == 200) {
      return BankSyncResult.fromJson(json.decode(response.body));
    }
    throw Exception(_api.getErrorMessage(response));
  }

  Future<void> disconnect(int connectionId) async {
    final response = await _api.delete(ApiConfig.bankConnectionById(connectionId));
    if (response.statusCode != 204) {
      throw Exception('Verbindung konnte nicht getrennt werden');
    }
  }

  /// `true`, wenn das Backend im Demo-Betrieb läuft.
  ///
  /// Wird in der Oberfläche sichtbar gemacht: sonst hält man synthetische Zahlen
  /// für echte.
  Future<bool> isDemo() async {
    final response = await _api.get(ApiConfig.bankStatus);
    if (response.statusCode == 200) {
      return json.decode(response.body)['demo'] ?? false;
    }
    return false;
  }
}

/// Ergebnis eines Abrufs.
class BankSyncResult {
  final int accounts;
  final int imported;
  final int skipped;

  /// Was kein Abbruch ist, aber ohne Erklärung wie ein Defekt aussieht - ein
  /// übersprungenes Fremdwährungskonto etwa.
  final List<String> warnings;

  BankSyncResult({
    required this.accounts,
    required this.imported,
    required this.skipped,
    this.warnings = const [],
  });

  factory BankSyncResult.fromJson(Map<String, dynamic> json) {
    return BankSyncResult(
      accounts: (json['accounts'] as num?)?.toInt() ?? 0,
      imported: (json['imported'] as num?)?.toInt() ?? 0,
      skipped: (json['skipped'] as num?)?.toInt() ?? 0,
      warnings: ((json['warnings'] as List?) ?? []).map((e) => e.toString()).toList(),
    );
  }
}
