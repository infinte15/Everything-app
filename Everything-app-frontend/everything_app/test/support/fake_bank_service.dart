import 'package:everything_app/models/aspsp.dart';
import 'package:everything_app/models/bank_account.dart';
import 'package:everything_app/models/bank_connection.dart';
import 'package:everything_app/services/bank_service.dart';

/// Ersatz für [BankService]. Dieselbe Regel wie bei [FakeFinanceService]: jede
/// berührte Methode überschreiben, nie `super` aufrufen.
class FakeBankService extends BankService {
  FakeBankService({
    List<BankAccount>? accounts,
    List<BankConnection>? connections,
    List<Aspsp>? banks,
    this.demo = false,
  })  : accounts = List.of(accounts ?? const []),
        connections = List.of(connections ?? const []),
        banks = List.of(banks ?? const []);

  final List<BankAccount> accounts;
  final List<BankConnection> connections;
  final List<Aspsp> banks;
  bool demo;

  int syncCallCount = 0;
  BankSyncResult syncResult =
      BankSyncResult(accounts: 1, imported: 0, skipped: 0);
  bool syncFails = false;

  String authUrl = 'https://bank.example/auth';
  Aspsp? lastConnected;

  int? lastToggledAccountId;
  bool? lastToggledValue;

  @override
  Future<List<Aspsp>> getAspsps({String country = 'DE'}) async => List.of(banks);

  @override
  Future<String> connect(Aspsp aspsp) async {
    lastConnected = aspsp;
    return authUrl;
  }

  @override
  Future<List<BankConnection>> getConnections() async => List.of(connections);

  @override
  Future<List<BankAccount>> getAccounts() async => List.of(accounts);

  @override
  Future<BankAccount> updateAccount(
    int id, {
    bool? syncEnabled,
    String? displayName,
  }) async {
    lastToggledAccountId = id;
    lastToggledValue = syncEnabled;

    final index = accounts.indexWhere((a) => a.id == id);
    final existing = accounts[index];
    final updated = BankAccount(
      id: existing.id,
      displayName: displayName ?? existing.displayName,
      ibanSuffix: existing.ibanSuffix,
      currency: existing.currency,
      currentBalance: existing.currentBalance,
      balanceUpdatedAt: existing.balanceUpdatedAt,
      syncEnabled: syncEnabled ?? existing.syncEnabled,
      connectionId: existing.connectionId,
      aspspName: existing.aspspName,
    );
    accounts[index] = updated;
    return updated;
  }

  @override
  Future<BankSyncResult> sync() async {
    syncCallCount++;
    if (syncFails) throw Exception('Abruflimit erreicht');
    return syncResult;
  }

  @override
  Future<void> disconnect(int connectionId) async {
    connections.removeWhere((c) => c.id == connectionId);
    accounts.removeWhere((a) => a.connectionId == connectionId);
  }

  @override
  Future<bool> isDemo() async => demo;
}
