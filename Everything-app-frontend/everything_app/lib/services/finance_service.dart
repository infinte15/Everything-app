import 'dart:convert';

import '../config/api_config.dart';
import '../models/budget_category.dart';
import '../models/contract.dart';
import '../models/finance_forecast.dart';
import '../models/finance_transaction.dart';
import 'api_service.dart';

/// Buchungen, Verträge, Budgets und Prognose.
///
/// Die URLs stehen seit dem Umbau in [ApiConfig] statt inline hier - so wie in
/// den übrigen Services des Projekts.
class FinanceService {
  final ApiService _api = ApiService();

  // ── Buchungen ──────────────────────────────────────────────────────────────

  Future<List<FinanceTransaction>> getTransactions() async {
    final response = await _api.get(ApiConfig.transactions);
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => FinanceTransaction.fromJson(json)).toList();
    }
    throw Exception('Buchungen konnten nicht geladen werden');
  }

  Future<FinanceTransaction> createTransaction(FinanceTransaction transaction) async {
    final response =
        await _api.post(ApiConfig.transactions, transaction.toJson());
    if (response.statusCode == 201) {
      return FinanceTransaction.fromJson(json.decode(response.body));
    }
    throw Exception('Buchung konnte nicht angelegt werden: ${response.body}');
  }

  Future<FinanceTransaction> updateTransaction(FinanceTransaction transaction) async {
    final response = await _api.put(
      ApiConfig.transactionById(transaction.id!),
      transaction.toJson(),
    );
    if (response.statusCode == 200) {
      return FinanceTransaction.fromJson(json.decode(response.body));
    }
    throw Exception('Buchung konnte nicht geändert werden');
  }

  Future<void> deleteTransaction(int id) async {
    final response = await _api.delete(ApiConfig.transactionById(id));
    if (response.statusCode != 204) {
      throw Exception('Buchung konnte nicht gelöscht werden');
    }
  }

  /// Kategorie korrigieren - das Backend lernt daraus eine Regel.
  ///
  /// Liefert die Zahl der weiteren Buchungen derselben Gegenpartei zurück, damit
  /// die App "auch auf N frühere anwenden?" anbieten kann.
  Future<RecategorizeResult> recategorize(
    int id, {
    required String category,
    String? subcategory,
    bool applyToPast = false,
  }) async {
    final response = await _api.patch(ApiConfig.transactionCategory(id), {
      'category': category,
      'subcategory': ?subcategory,
      'applyToPast': applyToPast,
    });
    if (response.statusCode == 200) {
      final body = json.decode(response.body);
      return RecategorizeResult(
        transaction: FinanceTransaction.fromJson(body['transaction']),
        affectedCount: (body['affectedCount'] as num?)?.toInt() ?? 0,
        appliedToPast: body['appliedToPast'] ?? false,
      );
    }
    throw Exception('Kategorie konnte nicht geändert werden');
  }

  // ── Verträge ───────────────────────────────────────────────────────────────

  Future<List<Contract>> getContracts({bool activeOnly = false}) async {
    final response =
        await _api.get('${ApiConfig.contracts}?activeOnly=$activeOnly');
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => Contract.fromJson(json)).toList();
    }
    throw Exception('Verträge konnten nicht geladen werden');
  }

  Future<List<FinanceTransaction>> getContractTransactions(int contractId) async {
    final response = await _api.get(ApiConfig.contractTransactions(contractId));
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => FinanceTransaction.fromJson(json)).toList();
    }
    throw Exception('Buchungen zum Vertrag konnten nicht geladen werden');
  }

  Future<Contract> updateContract(Contract contract) async {
    final response =
        await _api.put(ApiConfig.contractById(contract.id), contract.toJson());
    if (response.statusCode == 200) {
      return Contract.fromJson(json.decode(response.body));
    }
    throw Exception('Vertrag konnte nicht geändert werden');
  }

  Future<void> deleteContract(int id) async {
    final response = await _api.delete(ApiConfig.contractById(id));
    if (response.statusCode != 204) {
      throw Exception('Vertrag konnte nicht gelöscht werden');
    }
  }

  // ── Prognose und Statistik ─────────────────────────────────────────────────

  Future<FinanceForecast> getForecast(DateTime month) async {
    final key = '${month.year}-${month.month.toString().padLeft(2, '0')}';
    final response = await _api.get(ApiConfig.financeForecast(key));
    if (response.statusCode == 200) {
      return FinanceForecast.fromJson(json.decode(response.body));
    }
    throw Exception('Prognose konnte nicht geladen werden');
  }

  Future<Map<String, dynamic>> getMonthlyStatistics(DateTime month) async {
    final key = '${month.year}-${month.month.toString().padLeft(2, '0')}';
    final response = await _api.get(ApiConfig.financeMonthlyStats(key));
    if (response.statusCode == 200) {
      return json.decode(response.body);
    }
    throw Exception('Monatsstatistik konnte nicht geladen werden');
  }

  // ── Budgets ────────────────────────────────────────────────────────────────

  Future<List<BudgetCategory>> getBudgets() async {
    final response = await _api.get(ApiConfig.budgets);
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => BudgetCategory.fromJson(json)).toList();
    }
    throw Exception('Budgets konnten nicht geladen werden');
  }

  Future<List<BudgetProgress>> getBudgetProgress() async {
    final response = await _api.get(ApiConfig.budgetProgress);
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => BudgetProgress.fromJson(json)).toList();
    }
    throw Exception('Budget-Fortschritt konnte nicht geladen werden');
  }

  Future<BudgetCategory> createBudget(BudgetCategory budget) async {
    final response = await _api.post(ApiConfig.budgets, budget.toJson());
    if (response.statusCode == 201) {
      return BudgetCategory.fromJson(json.decode(response.body));
    }
    throw Exception('Budget konnte nicht angelegt werden: ${response.body}');
  }

  Future<BudgetCategory> updateBudget(BudgetCategory budget) async {
    final response =
        await _api.put(ApiConfig.budgetById(budget.id!), budget.toJson());
    if (response.statusCode == 200) {
      return BudgetCategory.fromJson(json.decode(response.body));
    }
    throw Exception('Budget konnte nicht geändert werden');
  }

  Future<void> deleteBudget(int id) async {
    final response = await _api.delete(ApiConfig.budgetById(id));
    if (response.statusCode != 204) {
      throw Exception('Budget konnte nicht gelöscht werden');
    }
  }
}

/// Antwort auf eine Umkategorisierung.
class RecategorizeResult {
  final FinanceTransaction transaction;

  /// Weitere Buchungen derselben Gegenpartei.
  final int affectedCount;

  final bool appliedToPast;

  RecategorizeResult({
    required this.transaction,
    required this.affectedCount,
    required this.appliedToPast,
  });
}
