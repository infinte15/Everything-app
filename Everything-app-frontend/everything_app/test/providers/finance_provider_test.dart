import 'package:everything_app/models/bank_account.dart';
import 'package:everything_app/models/bank_connection.dart';
import 'package:everything_app/models/contract.dart';
import 'package:everything_app/models/finance_forecast.dart';
import 'package:everything_app/models/finance_transaction.dart';
import 'package:everything_app/providers/finance_provider.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_bank_service.dart';
import '../support/fake_finance_service.dart';

FinanceTransaction _tx({
  int id = 1,
  double amount = 20,
  String type = 'AUSGABE',
  String category = 'Lebensmittel',
  DateTime? date,
  bool isRecurring = false,
  String? counterparty,
}) {
  final now = DateTime.now();
  return FinanceTransaction(
    id: id,
    amount: amount,
    type: type,
    category: category,
    description: 'Buchung $id',
    // Der Provider lädt den laufenden Monat - Testdaten müssen darin liegen.
    transactionDate: date ?? DateTime(now.year, now.month, 15),
    isRecurring: isRecurring,
    counterparty: counterparty,
    source: 'BANK',
  );
}

Contract _contract({
  int id = 1,
  double amount = 13.99,
  String direction = 'EXPENSE',
  bool active = true,
  double monthly = 14.2,
}) {
  return Contract(
    id: id,
    name: 'Vertrag $id',
    category: 'Abos',
    direction: direction,
    amount: amount,
    frequency: 'MONTHLY',
    intervalDays: 30,
    nextDueDate: DateTime.now().add(const Duration(days: 3)),
    occurrenceCount: 14,
    active: active,
    monthlyAmount: monthly,
  );
}

BankAccount _account({int id = 1, double? balance = 1000}) => BankAccount(
      id: id,
      displayName: 'Girokonto',
      ibanSuffix: '2051',
      currentBalance: balance,
      balanceUpdatedAt: DateTime.now(),
      connectionId: 1,
    );

Future<FinanceProvider> _loaded(
  FakeFinanceService finance,
  FakeBankService bank,
) async {
  final provider = FinanceProvider(financeService: finance, bankService: bank);
  addTearDown(provider.dispose);
  await provider.loadMonthlyData(DateTime.now());
  return provider;
}

void main() {
  group('loadMonthlyData', () {
    test('rechnet Vertragsbuchungen in die Monatssummen ein', () async {
      // Regression: die Stelle filterte isRecurring == false heraus. Mit
      // importierten Daten fehlten damit Miete und Gehalt in totalIncome und
      // totalExpenses - die Kernzahl war schlicht falsch.
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, amount: 20, isRecurring: false),
        _tx(id: 2, amount: 845, isRecurring: true),
        _tx(id: 3, amount: 2480, type: 'EINNAHME', isRecurring: true),
      ]);

      final provider = await _loaded(finance, FakeBankService());

      expect(provider.totalExpenses, 865);
      expect(provider.totalIncome, 2480);
      expect(provider.transactions.length, 3);
    });

    test('nimmt nur Buchungen des geladenen Monats', () async {
      final now = DateTime.now();
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, date: DateTime(now.year, now.month, 10)),
        _tx(id: 2, date: DateTime(now.year, now.month - 2, 10)),
      ]);

      final provider = await _loaded(finance, FakeBankService());

      expect(provider.transactions.map((t) => t.id), [1]);
    });

    test('sortiert absteigend nach Datum', () async {
      final now = DateTime.now();
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, date: DateTime(now.year, now.month, 3)),
        _tx(id: 2, date: DateTime(now.year, now.month, 20)),
        _tx(id: 3, date: DateTime(now.year, now.month, 11)),
      ]);

      final provider = await _loaded(finance, FakeBankService());

      expect(provider.transactions.map((t) => t.id), [2, 3, 1]);
    });

    test('ein Fehler landet in error und nicht in einer Exception', () async {
      final finance = FakeFinanceService()..failTransactions = true;

      final provider = await _loaded(finance, FakeBankService());

      expect(provider.error, isNotNull);
      expect(provider.isLoading, isFalse);
    });
  });

  group('Kontostand', () {
    test('ist null ohne verbundenes Konto', () async {
      final provider = await _loaded(FakeFinanceService(), FakeBankService());

      // Bewusst nicht 0.0: das wäre eine Aussage über Geld, das niemand gezählt
      // hat.
      expect(provider.totalBalance, isNull);
      expect(provider.hasBankConnection, isFalse);
    });

    test('summiert über alle Konten', () async {
      final bank = FakeBankService(accounts: [
        _account(id: 1, balance: 1000),
        _account(id: 2, balance: 250.5),
      ]);

      final provider = await _loaded(FakeFinanceService(), bank);

      expect(provider.totalBalance, 1250.5);
      expect(provider.hasBankConnection, isTrue);
    });

    test('ist null, solange kein Konto einen Saldo gemeldet hat', () async {
      final bank = FakeBankService(accounts: [_account(balance: null)]);

      final provider = await _loaded(FakeFinanceService(), bank);

      expect(provider.totalBalance, isNull);
    });
  });

  group('connectionNeedingAttention', () {
    test('meldet eine abgelaufene Zustimmung', () async {
      final bank = FakeBankService(connections: [
        BankConnection(id: 1, aspspName: 'Testbank', status: 'EXPIRED'),
      ]);

      final provider = await _loaded(FakeFinanceService(), bank);

      expect(provider.connectionNeedingAttention?.id, 1);
    });

    test('meldet nichts bei einer gesunden Verbindung', () async {
      final bank = FakeBankService(connections: [
        BankConnection(
          id: 1,
          aspspName: 'Testbank',
          status: 'ACTIVE',
          daysUntilExpiry: 170,
        ),
      ]);

      final provider = await _loaded(FakeFinanceService(), bank);

      expect(provider.connectionNeedingAttention, isNull);
    });

    test('warnt vor, bevor die Zustimmung abläuft', () async {
      // Verlängern gibt es nicht - ohne Vorwarnung versiegt der Abruf stumm.
      final bank = FakeBankService(connections: [
        BankConnection(
          id: 1,
          aspspName: 'Testbank',
          status: 'ACTIVE',
          daysUntilExpiry: 5,
        ),
      ]);

      final provider = await _loaded(FakeFinanceService(), bank);

      expect(provider.connectionNeedingAttention?.id, 1);
    });

    test('ein Defekt hat Vorrang vor einer bloßen Vorwarnung', () async {
      final bank = FakeBankService(connections: [
        BankConnection(
          id: 1,
          aspspName: 'Bald abgelaufen',
          status: 'ACTIVE',
          daysUntilExpiry: 3,
        ),
        BankConnection(id: 2, aspspName: 'Kaputt', status: 'FAILED'),
      ]);

      final provider = await _loaded(FakeFinanceService(), bank);

      expect(provider.connectionNeedingAttention?.id, 2);
    });
  });

  group('Verträge', () {
    test('monthlyContractCost zählt nur aktive Ausgabenverträge', () async {
      final finance = FakeFinanceService(contracts: [
        _contract(id: 1, monthly: 14.2),
        _contract(id: 2, monthly: 30.0),
        _contract(id: 3, monthly: 2500, direction: 'INCOME'),
        _contract(id: 4, monthly: 99, active: false),
      ]);

      final provider = await _loaded(finance, FakeBankService());

      expect(provider.monthlyContractCost, closeTo(44.2, 0.001));
      expect(provider.activeContracts.length, 3);
    });
  });

  group('recategorize', () {
    test('reicht Kategorie und applyToPast durch und lädt neu', () async {
      final finance = FakeFinanceService(transactions: [_tx(id: 1)])
        ..affectedCount = 7;
      final provider = await _loaded(finance, FakeBankService());

      final result = await provider.recategorize(1,
          category: 'Restaurant', applyToPast: true);

      expect(finance.lastCategory, 'Restaurant');
      expect(finance.lastApplyToPast, isTrue);
      expect(result?.affectedCount, 7);
      expect(provider.transactions.first.category, 'Restaurant');
    });
  });

  group('syncBank', () {
    test('setzt isSyncing während des Abrufs und danach zurück', () async {
      final bank = FakeBankService(accounts: [_account()]);
      final provider = await _loaded(FakeFinanceService(), bank);

      final pending = provider.syncBank();
      expect(provider.isSyncing, isTrue);

      await pending;
      expect(provider.isSyncing, isFalse);
      expect(bank.syncCallCount, 1);
    });

    test('ein Abruffehler hinterlässt kein hängendes isSyncing', () async {
      // Sonst dreht sich das Symbol in der Kontokarte für immer weiter.
      final bank = FakeBankService(accounts: [_account()])..syncFails = true;
      final provider = await _loaded(FakeFinanceService(), bank);

      final result = await provider.syncBank();

      expect(result, isNull);
      expect(provider.isSyncing, isFalse);
      expect(provider.error, isNotNull);
    });
  });

  group('setAccountSyncEnabled', () {
    test('ändert das Konto in der Liste ohne vollständiges Neuladen', () async {
      final bank = FakeBankService(accounts: [_account(id: 1)]);
      final provider = await _loaded(FakeFinanceService(), bank);

      final ok = await provider.setAccountSyncEnabled(1, false);

      expect(ok, isTrue);
      expect(bank.lastToggledValue, isFalse);
      expect(provider.accounts.single.syncEnabled, isFalse);
    });
  });

  group('Prognose', () {
    test('reicht die Kernzahl durch', () async {
      final now = DateTime.now();
      final finance = FakeFinanceService()
        ..forecast = FinanceForecast(
          month: '${now.year}-${now.month}',
          monthStart: DateTime(now.year, now.month, 1),
          monthEnd: DateTime(now.year, now.month + 1, 0),
          currentBalance: 1000,
          available: 420.5,
          daysRemaining: 12,
        );

      final provider = await _loaded(finance, FakeBankService());

      expect(provider.forecast?.available, 420.5);
      expect(provider.forecast?.hasBalance, isTrue);
    });

    test('ohne Konto bleibt available null', () async {
      final provider = await _loaded(FakeFinanceService(), FakeBankService());

      expect(provider.forecast?.available, isNull);
      expect(provider.forecast?.hasBalance, isFalse);
    });
  });
}
