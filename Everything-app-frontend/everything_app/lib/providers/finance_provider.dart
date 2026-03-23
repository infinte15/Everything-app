import 'package:flutter/material.dart';
import '../models/finance_transaction.dart';
import '../services/finance_service.dart';

class FinanceProvider with ChangeNotifier {
  final FinanceService _financeService = FinanceService();

  List<FinanceTransaction> _transactions = [];
  List<FinanceTransaction> _contracts = [];
  Map<String, dynamic>? _monthlyStats;
  
  bool _isLoading = false;
  String? _error;
  
  DateTime _currentMonth = DateTime.now();

  List<FinanceTransaction> get transactions => _transactions;
  List<FinanceTransaction> get contracts => _contracts;
  Map<String, dynamic>? get monthlyStats => _monthlyStats;
  
  bool get isLoading => _isLoading;
  String? get error => _error;
  DateTime get currentMonth => _currentMonth;

  double get totalIncome => _transactions
      .where((t) => t.type == 'EINNAHME')
      .fold(0.0, (sum, t) => sum + t.amount);

  double get totalExpenses => _transactions
      .where((t) => t.type == 'AUSGABE')
      .fold(0.0, (sum, t) => sum + t.amount);

  double get balance => totalIncome - totalExpenses;

  Map<String, double> get spendingByCategory {
    final map = <String, double>{};
    for (final t in _transactions.where((t) => t.type == 'AUSGABE')) {
      map[t.category] = (map[t.category] ?? 0) + t.amount;
    }
    return map;
  }

  List<FinanceTransaction> get recentTransactions =>
      _transactions.take(5).toList();


  Future<void> loadMonthlyData(DateTime month) async {
    _currentMonth = month;
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final firstDay = DateTime(month.year, month.month, 1);
      final lastDay = DateTime(month.year, month.month + 1, 0, 23, 59, 59);

      // Load data concurrently
      final results = await Future.wait([
        _financeService.getTransactions(),
        _financeService.getContracts(),
        _financeService.getMonthlyStatistics(month)
      ]);

      var allTransactions = results[0] as List<FinanceTransaction>;
      _contracts = results[1] as List<FinanceTransaction>;
      _monthlyStats = results[2] as Map<String, dynamic>;

      // Filter transactions for the selected month to mimic local filtering
      _transactions = allTransactions.where((t) {
        return t.transactionDate.isAfter(firstDay.subtract(const Duration(days: 1))) && 
               t.transactionDate.isBefore(lastDay.add(const Duration(days: 1))) &&
               t.isRecurring == false; // Exclude contracts from normal view
      }).toList();
      
      // Sort transactions descending
      _transactions.sort((a, b) => b.transactionDate.compareTo(a.transactionDate));

      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Finanzdaten: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> addTransaction(FinanceTransaction transaction) async {
    try {
      await _financeService.createTransaction(transaction);
      await loadMonthlyData(_currentMonth);
      return true;
    } catch (e) {
      _error = 'Fehler beim Hinzufügen: $e';
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
      _error = 'Fehler beim Aktualisieren: $e';
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
      _error = 'Fehler beim Löschen: $e';
      notifyListeners();
      return false;
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}