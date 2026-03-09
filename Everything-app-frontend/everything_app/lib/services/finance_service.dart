import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/finance_transaction.dart';
import 'api_service.dart';

class FinanceService {
  final ApiService _api = ApiService();

  Future<List<FinanceTransaction>> getTransactions() async {
    final response = await _api.get('/api/finance/transactions');
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => FinanceTransaction.fromJson(json)).toList();
    }
    throw Exception('Failed to load transactions');
  }

  Future<List<FinanceTransaction>> getContracts() async {
    final response = await _api.get('/api/finance/contracts');
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => FinanceTransaction.fromJson(json)).toList();
    }
    throw Exception('Failed to load contracts');
  }

  Future<FinanceTransaction> createTransaction(FinanceTransaction transaction) async {
    final response = await _api.post(
      '/api/finance/transactions',
      transaction.toJson(),
    );
    if (response.statusCode == 201) {
      return FinanceTransaction.fromJson(json.decode(response.body));
    }
    throw Exception('Failed to create transaction: ${response.body}');
  }

  Future<FinanceTransaction> updateTransaction(FinanceTransaction transaction) async {
    final response = await _api.put(
      '/api/finance/transactions/${transaction.id}',
      transaction.toJson(),
    );
    if (response.statusCode == 200) {
      return FinanceTransaction.fromJson(json.decode(response.body));
    }
    throw Exception('Failed to update transaction');
  }

  Future<void> deleteTransaction(int id) async {
    final response = await _api.delete('/api/finance/transactions/$id');
    if (response.statusCode != 204) {
      throw Exception('Failed to delete transaction');
    }
  }

  Future<Map<String, dynamic>> getMonthlyStatistics(DateTime month) async {
    final year = month.year;
    final monthStr = month.month.toString().padLeft(2, '0');
    final response = await _api.get('/api/finance/stats/monthly?month=$year-$monthStr');
    
    if (response.statusCode == 200) {
      return json.decode(response.body);
    }
    throw Exception('Failed to load monthly statistics');
  }
}
