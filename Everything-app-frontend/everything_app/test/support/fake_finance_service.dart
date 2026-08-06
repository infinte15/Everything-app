import 'package:everything_app/models/budget_category.dart';
import 'package:everything_app/models/contract.dart';
import 'package:everything_app/models/finance_forecast.dart';
import 'package:everything_app/models/finance_transaction.dart';
import 'package:everything_app/services/finance_service.dart';

/// Ersatz für [FinanceService] in Provider- und Widget-Tests.
///
/// **Jede** Methode, die der Test berührt, muss hier überschrieben sein, und
/// keine davon ruft `super` auf. Eine fehlende Überschreibung geht stillschweigend
/// ins Netz und lässt `testWidgets` ohne Ausgabe hängen, bis der Test-Runner
/// abbricht - das kostet mehr Zeit als jede Fehlermeldung.
class FakeFinanceService extends FinanceService {
  FakeFinanceService({
    List<FinanceTransaction>? transactions,
    List<Contract>? contracts,
    FinanceForecast? forecast,
    List<BudgetCategory>? budgets,
    List<BudgetProgress>? budgetProgress,
  })  : transactions = List.of(transactions ?? const []),
        contracts = List.of(contracts ?? const []),
        forecast = forecast ?? emptyForecast(),
        budgets = List.of(budgets ?? const []),
        budgetProgress = List.of(budgetProgress ?? const []);

  final List<FinanceTransaction> transactions;
  final List<Contract> contracts;
  FinanceForecast forecast;
  final List<BudgetCategory> budgets;
  final List<BudgetProgress> budgetProgress;

  int recategorizeCallCount = 0;
  String? lastCategory;
  bool? lastApplyToPast;
  int affectedCount = 0;

  bool failTransactions = false;

  /// Prognose ohne verbundenes Konto: `available` bleibt null.
  static FinanceForecast emptyForecast() {
    final now = DateTime.now();
    return FinanceForecast(
      month: '${now.year}-${now.month.toString().padLeft(2, '0')}',
      monthStart: DateTime(now.year, now.month, 1),
      monthEnd: DateTime(now.year, now.month + 1, 0),
    );
  }

  @override
  Future<List<FinanceTransaction>> getTransactions() async {
    if (failTransactions) throw Exception('kaputt');
    return List.of(transactions);
  }

  @override
  Future<FinanceTransaction> createTransaction(FinanceTransaction transaction) async {
    final created = FinanceTransaction(
      id: transactions.length + 1,
      amount: transaction.amount,
      type: transaction.type,
      category: transaction.category,
      description: transaction.description,
      transactionDate: transaction.transactionDate,
    );
    transactions.add(created);
    return created;
  }

  @override
  Future<FinanceTransaction> updateTransaction(FinanceTransaction transaction) async {
    final index = transactions.indexWhere((t) => t.id == transaction.id);
    if (index != -1) transactions[index] = transaction;
    return transaction;
  }

  @override
  Future<void> deleteTransaction(int id) async {
    transactions.removeWhere((t) => t.id == id);
  }

  @override
  Future<RecategorizeResult> recategorize(
    int id, {
    required String category,
    String? subcategory,
    bool applyToPast = false,
  }) async {
    recategorizeCallCount++;
    lastCategory = category;
    lastApplyToPast = applyToPast;

    final index = transactions.indexWhere((t) => t.id == id);
    final updated = transactions[index].copyWith(category: category, categoryLocked: true);
    transactions[index] = updated;

    return RecategorizeResult(
      transaction: updated,
      affectedCount: affectedCount,
      appliedToPast: applyToPast,
    );
  }

  @override
  Future<List<Contract>> getContracts({bool activeOnly = false}) async {
    return activeOnly ? contracts.where((c) => c.active).toList() : List.of(contracts);
  }

  @override
  Future<List<FinanceTransaction>> getContractTransactions(int contractId) async {
    return transactions.where((t) => t.contractId == contractId).toList();
  }

  @override
  Future<Contract> updateContract(Contract contract) async {
    final index = contracts.indexWhere((c) => c.id == contract.id);
    if (index != -1) contracts[index] = contract;
    return contract;
  }

  @override
  Future<void> deleteContract(int id) async {
    contracts.removeWhere((c) => c.id == id);
  }

  @override
  Future<FinanceForecast> getForecast(DateTime month) async => forecast;

  @override
  Future<Map<String, dynamic>> getMonthlyStatistics(DateTime month) async => {};

  @override
  Future<List<BudgetCategory>> getBudgets() async => List.of(budgets);

  @override
  Future<List<BudgetProgress>> getBudgetProgress() async => List.of(budgetProgress);

  @override
  Future<BudgetCategory> createBudget(BudgetCategory budget) async {
    final created = BudgetCategory(
      id: budgets.length + 1,
      name: budget.name,
      limitAmount: budget.limitAmount,
      period: budget.period,
    );
    budgets.add(created);
    return created;
  }

  @override
  Future<BudgetCategory> updateBudget(BudgetCategory budget) async {
    final index = budgets.indexWhere((b) => b.id == budget.id);
    if (index != -1) budgets[index] = budget;
    return budget;
  }

  @override
  Future<void> deleteBudget(int id) async {
    budgets.removeWhere((b) => b.id == id);
  }
}
