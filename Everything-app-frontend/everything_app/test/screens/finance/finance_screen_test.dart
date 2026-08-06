import 'package:everything_app/models/bank_account.dart';
import 'package:everything_app/models/bank_connection.dart';
import 'package:everything_app/models/budget_category.dart';
import 'package:everything_app/models/contract.dart';
import 'package:everything_app/models/finance_forecast.dart';
import 'package:everything_app/models/finance_transaction.dart';
import 'package:everything_app/providers/finance_provider.dart';
import 'package:everything_app/screens/finance/pages/finance_budget_tab.dart';
import 'package:everything_app/screens/finance/pages/finance_contracts_tab.dart';
import 'package:everything_app/screens/finance/pages/finance_overview_tab.dart';
import 'package:everything_app/screens/finance/pages/finance_transactions_tab.dart';
import 'package:everything_app/screens/finance/widgets/recategorize_sheet.dart';
import 'package:everything_app/theme/kinetic_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import '../../support/fake_bank_service.dart';
import '../../support/fake_finance_service.dart';

FinanceTransaction _tx({
  int id = 1,
  double amount = 24.9,
  String type = 'AUSGABE',
  String category = 'Lebensmittel',
  String? counterparty = 'REWE Markt',
  String description = 'REWE SAGT DANKE',
  int? day,
  int? contractId,
  bool categoryLocked = false,
}) {
  final now = DateTime.now();
  return FinanceTransaction(
    id: id,
    amount: amount,
    type: type,
    category: category,
    description: description,
    transactionDate: DateTime(now.year, now.month, day ?? 15),
    counterparty: counterparty,
    source: 'BANK',
    contractId: contractId,
    categoryLocked: categoryLocked,
  );
}

Contract _contract({
  int id = 1,
  String name = 'Netflix',
  double amount = 13.99,
  String direction = 'EXPENSE',
  bool active = true,
  int dueInDays = 3,
  int occurrences = 14,
  bool detected = true,
}) {
  return Contract(
    id: id,
    name: name,
    category: 'Abos',
    direction: direction,
    amount: amount,
    frequency: 'MONTHLY',
    intervalDays: 30,
    lastBookingDate: DateTime.now().subtract(const Duration(days: 27)),
    nextDueDate: DateTime.now().add(Duration(days: dueInDays)),
    occurrenceCount: occurrences,
    active: active,
    detectedAutomatically: detected,
    monthlyAmount: amount,
  );
}

FinanceForecast _forecast({double? available, double? balance, bool shortfall = false}) {
  final now = DateTime.now();
  return FinanceForecast(
    month: '${now.year}-${now.month.toString().padLeft(2, '0')}',
    monthStart: DateTime(now.year, now.month, 1),
    monthEnd: DateTime(now.year, now.month + 1, 0),
    currentBalance: balance,
    available: available,
    upcomingContractExpenses: 43.34,
    upcomingContractIncome: 2480,
    projectedVariableExpenses: 300,
    averageDailyVariableExpenses: 20,
    daysRemaining: 15,
    shortfall: shortfall,
  );
}

/// Baut einen Tab mit geladenem Provider. Der `pumpAndSettle` nach dem ersten
/// Frame ist Pflicht: die Tabs laden über einen `postFrameCallback` nach.
Future<FinanceProvider> _pumpTab(
  WidgetTester tester,
  Widget tab, {
  FakeFinanceService? finance,
  FakeBankService? bank,
}) async {
  final provider = FinanceProvider(
    financeService: finance ?? FakeFinanceService(),
    bankService: bank ?? FakeBankService(),
  );
  await provider.loadMonthlyData(DateTime.now());

  await tester.pumpWidget(
    ChangeNotifierProvider<FinanceProvider>.value(
      value: provider,
      child: MaterialApp(
        theme: KineticTheme.darkTheme,
        home: Scaffold(body: tab),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

void main() {
  setUpAll(() async {
    // FinanceFormat nutzt deutsche Datumsnamen - ohne diese Initialisierung
    // wirft DateFormat('EEEE', 'de_DE') in jedem Test.
    await initializeDateFormatting('de_DE');
  });

  group('Übersicht', () {
    testWidgets('ohne Konto steht dort keine erfundene Null', (tester) async {
      await _pumpTab(
        tester,
        FinanceOverviewTab(onConnectBank: () {}, onShowAllTransactions: () {}),
      );

      expect(find.text('Ohne verbundenes Konto lässt sich das nicht sagen.'),
          findsOneWidget);
      expect(find.text('Konto verbinden'), findsWidgets);
    });

    testWidgets('zeigt die Kernzahl samt ihrer Herleitung', (tester) async {
      final finance = FakeFinanceService()
        ..forecast = _forecast(available: 1234.56, balance: 900);
      final bank = FakeBankService(accounts: [
        BankAccount(id: 1, displayName: 'Girokonto', currentBalance: 900),
      ]);

      await _pumpTab(
        tester,
        FinanceOverviewTab(onConnectBank: () {}, onShowAllTransactions: () {}),
        finance: finance,
        bank: bank,
      );

      expect(find.text('1.234,56\u00A0€'), findsOneWidget);
      // Eine Kernzahl ohne Herleitung ist ein Orakel.
      expect(find.text('Kontostand heute'), findsOneWidget);
      expect(find.text('offene Verträge'), findsOneWidget);
      expect(find.textContaining('erwartete Einnahmen'), findsOneWidget);
    });

    testWidgets('eine Unterdeckung wird benannt, nicht nur eingefärbt',
        (tester) async {
      final finance = FakeFinanceService()
        ..forecast = _forecast(available: -80, balance: 100, shortfall: true);

      await _pumpTab(
        tester,
        FinanceOverviewTab(onConnectBank: () {}, onShowAllTransactions: () {}),
        finance: finance,
        bank: FakeBankService(accounts: [
          BankAccount(id: 1, displayName: 'Girokonto', currentBalance: 100),
        ]),
      );

      expect(find.textContaining('Es fehlen'), findsOneWidget);
    });

    testWidgets('kennzeichnet den Demo-Betrieb', (tester) async {
      // Ohne diese Kennzeichnung hält man synthetische Zahlen für echte.
      final bank = FakeBankService(
        accounts: [BankAccount(id: 1, displayName: 'Girokonto', currentBalance: 10)],
        demo: true,
      );
      final provider = await _pumpTab(
        tester,
        FinanceOverviewTab(onConnectBank: () {}, onShowAllTransactions: () {}),
        bank: bank,
      );

      await provider.loadProviderStatus();
      await tester.pumpAndSettle();

      expect(find.text('TESTDATEN'), findsOneWidget);
    });

    testWidgets('eine abgelaufene Zustimmung erscheint als Banner', (tester) async {
      final bank = FakeBankService(connections: [
        BankConnection(id: 1, aspspName: 'Sparkasse Bodensee', status: 'EXPIRED'),
      ]);

      await _pumpTab(
        tester,
        FinanceOverviewTab(onConnectBank: () {}, onShowAllTransactions: () {}),
        bank: bank,
      );

      expect(find.textContaining('ist abgelaufen'), findsOneWidget);
      expect(find.text('Erneuern'), findsOneWidget);
    });
  });

  group('Buchungen', () {
    testWidgets('Gegenpartei steht groß, Verwendungszweck klein', (tester) async {
      // Zwei Buchungen, damit der Tagessaldo in der Überschrift nicht zufällig
      // denselben Betrag trägt wie eine der Zeilen.
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, amount: 24.9),
        _tx(id: 2, amount: 5.10, counterparty: 'Bäckerei Dreher', description: 'Brötchen'),
      ]);

      await _pumpTab(tester, const FinanceTransactionsTab(), finance: finance);

      expect(find.text('REWE Markt'), findsOneWidget);
      expect(find.text('REWE SAGT DANKE'), findsOneWidget);
      expect(find.text('−24,90\u00A0€'), findsOneWidget);
      // Der Tagessaldo: bei einem Tag mit Gehalt und Miete ist die Summe die
      // eigentliche Information, nicht die einzelnen Zeilen.
      expect(find.text('−30,00\u00A0€'), findsOneWidget);
    });

    testWidgets('gruppiert nach Tagen', (tester) async {
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, day: 10),
        _tx(id: 2, day: 10),
        _tx(id: 3, day: 12),
      ]);

      await _pumpTab(tester, const FinanceTransactionsTab(), finance: finance);

      // Zwei Tagesgruppen, drei Zeilen.
      expect(find.text('REWE Markt'), findsNWidgets(3));
    });

    testWidgets('filtert auf Einnahmen', (tester) async {
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, amount: 20),
        _tx(id: 2, amount: 2480, type: 'EINNAHME', counterparty: 'Muster GmbH'),
      ]);

      await _pumpTab(tester, const FinanceTransactionsTab(), finance: finance);
      await tester.tap(find.text('Einnahmen'));
      await tester.pumpAndSettle();

      expect(find.text('Muster GmbH'), findsOneWidget);
      expect(find.text('REWE Markt'), findsNothing);
    });

    testWidgets('die Suche greift auf Gegenpartei und Kategorie', (tester) async {
      final finance = FakeFinanceService(transactions: [
        _tx(id: 1, counterparty: 'REWE Markt'),
        _tx(id: 2, counterparty: 'Shell', category: 'Mobilität', description: 'Tanken'),
      ]);

      await _pumpTab(tester, const FinanceTransactionsTab(), finance: finance);
      await tester.enterText(find.byType(TextField).first, 'shell');
      await tester.pumpAndSettle();

      expect(find.text('Shell'), findsOneWidget);
      expect(find.text('REWE Markt'), findsNothing);
    });

    testWidgets('der Kategorie-Chip öffnet das Umkategorisieren', (tester) async {
      final finance = FakeFinanceService(transactions: [_tx()]);

      await _pumpTab(tester, const FinanceTransactionsTab(), finance: finance);
      await tester.tap(find.text('Lebensmittel'));
      await tester.pumpAndSettle();

      expect(find.byType(RecategorizeSheet), findsOneWidget);
      // Der Lerneffekt muss dastehen - sonst löst man ihn aus, ohne es zu wissen.
      expect(find.textContaining('Künftige Buchungen von'), findsOneWidget);
    });

    testWidgets('Umkategorisieren reicht applyToPast durch', (tester) async {
      final finance = FakeFinanceService(transactions: [_tx()])..affectedCount = 3;

      await _pumpTab(tester, const FinanceTransactionsTab(), finance: finance);
      await tester.tap(find.text('Lebensmittel'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Restaurant'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Speichern'));
      await tester.pumpAndSettle();

      expect(finance.recategorizeCallCount, 1);
      expect(finance.lastCategory, 'Restaurant');
      expect(finance.lastApplyToPast, isTrue);
    });
  });

  group('Verträge', () {
    testWidgets('leer erklärt, woraus ein Vertrag entsteht', (tester) async {
      await _pumpTab(tester, const FinanceContractsTab());

      expect(find.text('Noch keine Verträge erkannt'), findsOneWidget);
    });

    testWidgets('zeigt die Herleitung des Vertrags', (tester) async {
      final finance = FakeFinanceService(contracts: [_contract()]);

      await _pumpTab(tester, const FinanceContractsTab(), finance: finance);

      expect(find.text('Netflix'), findsOneWidget);
      // Der Vertrag ist eine Schlussfolgerung - man muss sehen, worauf sie beruht.
      expect(find.text('aus 14 Buchungen'), findsOneWidget);
      expect(find.text('monatlich'), findsOneWidget);
      expect(find.text('in 3 Tagen · ${_shortDate(3)}'), findsOneWidget);
    });

    testWidgets('trennt Einnahmen, nahe Fälligkeiten und Späteres', (tester) async {
      final finance = FakeFinanceService(contracts: [
        _contract(id: 1, name: 'Netflix', dueInDays: 2),
        _contract(id: 2, name: 'Miete', dueInDays: 20, amount: 845),
        _contract(id: 3, name: 'Gehalt', direction: 'INCOME', amount: 2480),
      ]);

      await _pumpTab(tester, const FinanceContractsTab(), finance: finance);

      expect(find.text('EINNAHMEN'), findsOneWidget);
      expect(find.text('IN DEN NÄCHSTEN 7 TAGEN'), findsOneWidget);
      expect(find.text('SPÄTER'), findsOneWidget);
    });

    testWidgets('ein beendeter Vertrag heißt nicht "gekündigt"', (tester) async {
      // Das System sieht nur, dass nichts mehr gebucht wurde - eine Kündigung
      // kann es nicht kennen.
      final finance = FakeFinanceService(contracts: [
        _contract(id: 1, name: 'Altes Studio', active: false),
      ]);

      await _pumpTab(tester, const FinanceContractsTab(), finance: finance);

      expect(find.text('VERMUTLICH BEENDET'), findsOneWidget);
      expect(find.textContaining('vermutlich beendet'), findsOneWidget);
    });

    testWidgets('ein von Hand gepflegter Vertrag ist als solcher erkennbar',
        (tester) async {
      final finance = FakeFinanceService(contracts: [
        _contract(id: 1, detected: false),
      ]);

      await _pumpTab(tester, const FinanceContractsTab(), finance: finance);

      expect(find.text('von Hand'), findsOneWidget);
    });
  });

  group('Budget', () {
    testWidgets('leer bietet das Anlegen an', (tester) async {
      await _pumpTab(tester, const FinanceBudgetTab());

      expect(find.text('Noch keine Budgets'), findsOneWidget);
      expect(find.text('Budget anlegen'), findsOneWidget);
    });

    testWidgets('zeigt den Verbrauch aus dem Backend', (tester) async {
      final finance = FakeFinanceService(
        budgets: [BudgetCategory(id: 1, name: 'Lebensmittel', limitAmount: 400)],
        budgetProgress: [
          BudgetProgress(
            categoryId: 1,
            categoryName: 'Lebensmittel',
            limitAmount: 400,
            spentAmount: 250,
            remainingAmount: 150,
            percentageUsed: 62.5,
            isOverBudget: false,
          ),
        ],
      );

      await _pumpTab(tester, const FinanceBudgetTab(), finance: finance);

      expect(find.text('Lebensmittel'), findsOneWidget);
      expect(find.text('250,00\u00A0€'), findsOneWidget);
      expect(find.text('150,00\u00A0€ übrig'), findsOneWidget);
    });

    testWidgets('eine Überschreitung wird benannt', (tester) async {
      final finance = FakeFinanceService(
        budgets: [BudgetCategory(id: 1, name: 'Restaurant', limitAmount: 100)],
        budgetProgress: [
          BudgetProgress(
            categoryId: 1,
            categoryName: 'Restaurant',
            limitAmount: 100,
            spentAmount: 130,
            remainingAmount: -30,
            percentageUsed: 130,
            isOverBudget: true,
          ),
        ],
      );

      await _pumpTab(tester, const FinanceBudgetTab(), finance: finance);

      expect(find.text('30,00\u00A0€ über der Grenze'), findsOneWidget);
    });
  });
}

String _shortDate(int inDays) {
  final date = DateTime.now().add(Duration(days: inDays));
  const months = [
    'Jan.', 'Feb.', 'März', 'Apr.', 'Mai', 'Juni',
    'Juli', 'Aug.', 'Sep.', 'Okt.', 'Nov.', 'Dez.',
  ];
  return '${date.day}. ${months[date.month - 1]}';
}
