import 'package:flutter/material.dart';

import '../models/aspsp.dart';
import '../models/bank_account.dart';
import '../models/bank_connection.dart';
import '../models/budget_category.dart';
import '../models/contract.dart';
import '../models/finance_forecast.dart';
import '../models/finance_transaction.dart';
import '../services/bank_service.dart';
import '../services/finance_service.dart';

/// Zustand des Finance Space.
///
/// Konstruktor-Injektion wie bei [StudyProvider] und [CalendarProvider]: ohne
/// sie ließe sich der Space nicht testen, weil jeder Aufruf ins Netz ginge.
class FinanceProvider with ChangeNotifier {
  FinanceProvider({FinanceService? financeService, BankService? bankService})
      : _financeService = financeService ?? FinanceService(),
        _bankService = bankService ?? BankService();

  final FinanceService _financeService;
  final BankService _bankService;

  List<FinanceTransaction> _transactions = [];
  List<Contract> _contracts = [];
  List<BankAccount> _accounts = [];
  List<BankConnection> _connections = [];
  List<BudgetProgress> _budgetProgress = [];
  List<BudgetCategory> _budgets = [];
  FinanceForecast? _forecast;
  Map<String, dynamic>? _monthlyStats;

  bool _isLoading = false;
  bool _isSyncing = false;
  bool _isDemo = false;
  String? _error;

  DateTime _currentMonth = DateTime.now();

  List<FinanceTransaction> get transactions => _transactions;
  List<Contract> get contracts => _contracts;
  List<BankAccount> get accounts => _accounts;
  List<BankConnection> get connections => _connections;
  List<BudgetProgress> get budgetProgress => _budgetProgress;
  List<BudgetCategory> get budgets => _budgets;
  FinanceForecast? get forecast => _forecast;
  Map<String, dynamic>? get monthlyStats => _monthlyStats;

  bool get isLoading => _isLoading;
  bool get isSyncing => _isSyncing;

  /// Im Demo-Betrieb sind alle Kontodaten erfunden - die Oberfläche sagt das.
  bool get isDemo => _isDemo;

  String? get error => _error;
  DateTime get currentMonth => _currentMonth;

  bool get hasBankConnection => _accounts.isNotEmpty;

  /// Summe über alle abgerufenen Konten; `null`, solange keines verbunden ist.
  double? get totalBalance {
    final withBalance = _accounts.where((a) => a.currentBalance != null);
    if (withBalance.isEmpty) return null;
    return withBalance.fold(0.0, (sum, a) => sum! + a.currentBalance!);
  }

  DateTime? get lastSyncAt {
    final times = _connections.map((c) => c.lastSyncAt).whereType<DateTime>();
    if (times.isEmpty) return null;
    return times.reduce((a, b) => a.isAfter(b) ? a : b);
  }

  /// Eine Verbindung, die neu eingerichtet werden muss. Ohne diesen sichtbaren
  /// Zustand versiegt der Abruf stumm.
  BankConnection? get connectionNeedingAttention {
    for (final connection in _connections) {
      if (connection.needsReconnect || connection.hasFailed) return connection;
    }
    for (final connection in _connections) {
      if (connection.expiresSoon) return connection;
    }
    return null;
  }

  // ── Abgeleitete Zahlen ─────────────────────────────────────────────────────

  double get totalIncome => _transactions
      .where((t) => t.isIncome)
      .fold(0.0, (sum, t) => sum + t.amount);

  double get totalExpenses => _transactions
      .where((t) => !t.isIncome)
      .fold(0.0, (sum, t) => sum + t.amount);

  double get balance => totalIncome - totalExpenses;

  Map<String, double> get spendingByCategory {
    final map = <String, double>{};
    for (final t in _transactions.where((t) => !t.isIncome)) {
      map[t.category] = (map[t.category] ?? 0) + t.amount;
    }
    return map;
  }

  List<FinanceTransaction> get recentTransactions => _transactions.take(6).toList();

  List<Contract> get activeContracts => _contracts.where((c) => c.active).toList();

  /// Monatliche Belastung aus allen aktiven Ausgabenverträgen.
  double get monthlyContractCost => activeContracts
      .where((c) => !c.isIncome)
      .fold(0.0, (sum, c) => sum + (c.monthlyAmount ?? 0));

  // ── Laden ──────────────────────────────────────────────────────────────────

  Future<void> loadMonthlyData(DateTime month) async {
    _currentMonth = month;
    _isLoading = true;
    _error = null;
    notifyListeners();

    final firstDay = DateTime(month.year, month.month, 1);
    final lastDay = DateTime(month.year, month.month + 1, 0, 23, 59, 59);

    try {
      final results = await Future.wait([
        _financeService.getTransactions(),
        _financeService.getContracts(),
        _financeService.getForecast(month),
        _bankService.getAccounts(),
        _bankService.getConnections(),
      ]);

      final allTransactions = results[0] as List<FinanceTransaction>;
      _contracts = results[1] as List<Contract>;
      _forecast = results[2] as FinanceForecast;
      _accounts = results[3] as List<BankAccount>;
      _connections = results[4] as List<BankConnection>;

      // Vertragsbuchungen bleiben drin. Früher filterte diese Stelle
      // isRecurring == false heraus - mit importierten Daten fehlten damit Miete
      // und Gehalt in totalIncome/totalExpenses, und die Kernzahl war schlicht
      // falsch. Ein Vertrag ist eine Buchung wie jede andere, er hat nur
      // zusätzlich einen Rhythmus.
      _transactions = allTransactions
          .where((t) =>
              !t.transactionDate.isBefore(firstDay) &&
              !t.transactionDate.isAfter(lastDay))
          .toList()
        ..sort((a, b) => b.transactionDate.compareTo(a.transactionDate));

      _error = null;
    } catch (e) {
      _error = 'Finanzdaten konnten nicht geladen werden: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  /// Budgets und ihr Verbrauch. Getrennt vom Monatsladen, weil nur der
  /// Budget-Tab sie braucht.
  Future<void> loadBudgets() async {
    try {
      final results = await Future.wait([
        _financeService.getBudgets(),
        _financeService.getBudgetProgress(),
      ]);
      _budgets = results[0] as List<BudgetCategory>;
      _budgetProgress = results[1] as List<BudgetProgress>;
      _error = null;
    } catch (e) {
      _error = 'Budgets konnten nicht geladen werden: $e';
    }
    notifyListeners();
  }

  Future<void> loadProviderStatus() async {
    try {
      _isDemo = await _bankService.isDemo();
    } catch (_) {
      // Kein Grund, deswegen den ganzen Space als fehlerhaft zu zeigen.
      _isDemo = false;
    }
    notifyListeners();
  }

  // ── Bank ───────────────────────────────────────────────────────────────────

  Future<List<Aspsp>> searchBanks({String country = 'DE'}) =>
      _bankService.getAspsps(country: country);

  /// Startet die Zustimmung und liefert die URL für den externen Browser.
  Future<String?> startBankConnection(Aspsp aspsp) async {
    try {
      return await _bankService.connect(aspsp);
    } catch (e) {
      _error = '$e';
      notifyListeners();
      return null;
    }
  }

  Future<BankSyncResult?> syncBank() async {
    _isSyncing = true;
    notifyListeners();

    BankSyncResult? result;
    try {
      result = await _bankService.sync();
      await loadMonthlyData(_currentMonth);
    } catch (e) {
      _error = '$e';
    }

    _isSyncing = false;
    notifyListeners();
    return result;
  }

  /// Nach der Rückkehr aus dem Browser: nur den Verbindungszustand nachladen.
  ///
  /// Der Erst-Import ist zu diesem Zeitpunkt bereits gelaufen - er passiert
  /// synchron im Callback, weil die volle Historie nur unmittelbar nach der
  /// Zustimmung verfügbar ist.
  Future<void> refreshAfterAuthorization() async {
    try {
      _connections = await _bankService.getConnections();
      _accounts = await _bankService.getAccounts();
    } catch (e) {
      _error = '$e';
    }
    notifyListeners();
    if (_accounts.isNotEmpty) {
      await loadMonthlyData(_currentMonth);
    }
  }

  Future<bool> setAccountSyncEnabled(int accountId, bool enabled) async {
    try {
      final updated =
          await _bankService.updateAccount(accountId, syncEnabled: enabled);
      _accounts = [
        for (final account in _accounts)
          if (account.id == updated.id) updated else account
      ];
      notifyListeners();
      return true;
    } catch (e) {
      _error = '$e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> disconnectBank(int connectionId) async {
    try {
      await _bankService.disconnect(connectionId);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Verbindung konnte nicht getrennt werden: $e';
      notifyListeners();
      return false;
    }
  }

  // ── Buchungen ──────────────────────────────────────────────────────────────

  Future<bool> addTransaction(FinanceTransaction transaction) async {
    try {
      await _financeService.createTransaction(transaction);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Buchung konnte nicht angelegt werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> updateTransaction(FinanceTransaction transaction) async {
    try {
      await _financeService.updateTransaction(transaction);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Buchung konnte nicht geändert werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> deleteTransaction(int id) async {
    try {
      await _financeService.deleteTransaction(id);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Buchung konnte nicht gelöscht werden: $e';
      notifyListeners();
      return false;
    }
  }

  /// Kategorie korrigieren. Das Backend leitet daraus eine Regel für künftige
  /// Buchungen derselben Gegenpartei ab.
  Future<RecategorizeResult?> recategorize(
    int transactionId, {
    required String category,
    String? subcategory,
    bool applyToPast = false,
  }) async {
    try {
      final result = await _financeService.recategorize(
        transactionId,
        category: category,
        subcategory: subcategory,
        applyToPast: applyToPast,
      );
      await loadMonthlyData(_currentMonth);
      return result;
    } catch (e) {
      _error = 'Kategorie konnte nicht geändert werden: $e';
      notifyListeners();
      return null;
    }
  }

  // ── Verträge ───────────────────────────────────────────────────────────────

  Future<bool> updateContract(Contract contract) async {
    try {
      await _financeService.updateContract(contract);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Vertrag konnte nicht geändert werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> deleteContract(int id) async {
    try {
      await _financeService.deleteContract(id);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Vertrag konnte nicht gelöscht werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<List<FinanceTransaction>> contractTransactions(int contractId) =>
      _financeService.getContractTransactions(contractId);

  // ── Budgets ────────────────────────────────────────────────────────────────

  Future<bool> saveBudget(BudgetCategory budget) async {
    try {
      if (budget.id == null) {
        await _financeService.createBudget(budget);
      } else {
        await _financeService.updateBudget(budget);
      }
      await loadBudgets();
      return true;
    } catch (e) {
      _error = 'Budget konnte nicht gespeichert werden: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> deleteBudget(int id) async {
    try {
      await _financeService.deleteBudget(id);
      await loadBudgets();
      return true;
    } catch (e) {
      _error = 'Budget konnte nicht gelöscht werden: $e';
      notifyListeners();
      return false;
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}
