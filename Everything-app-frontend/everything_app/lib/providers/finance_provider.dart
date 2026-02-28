import 'package:flutter/material.dart';

class FinanceProvider with ChangeNotifier {
  List<Map<String, dynamic>> _transactions = [];
  List<Map<String, dynamic>> _budgets = [];
  Map<String, dynamic> _statistics = {};
  
  bool _isLoading = false;
  String? _error;

  bool get isLoading => _isLoading;
  String? get error => _error;
  List<Map<String, dynamic>> get transactions => _transactions;
  List<Map<String, dynamic>> get budgets => _budgets;
  Map<String, dynamic> get statistics => _statistics;


  double get totalIncome => _transactions
      .where((t) => (t['amount'] as double) > 0)
      .fold<double>(0, (sum, t) => sum + (t['amount'] as double));
  
  double get totalExpenses => _transactions
      .where((t) => (t['amount'] as double) < 0)
      .fold<double>(0, (sum, t) => sum + (t['amount'] as double).abs());
  
  double get balance => totalIncome - totalExpenses;


  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    
    try {
      await Future.wait([
        _loadTransactions(),
        _loadBudgets(),
        _loadStatistics(),
      ]);
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Finanzdaten: $e';
    }
    
    _isLoading = false;
    notifyListeners();
  }


  Future<void> _loadTransactions() async {
    // TODO: API Call -> GET /api/finance/transactions
    await Future.delayed(const Duration(milliseconds: 300));
    
    _transactions = [
      {
        'id': 1,
        'name': 'Gehalt November',
        'amount': 2450.0,
        'date': DateTime.now().subtract(const Duration(days: 2)),
        'category': 'Einnahmen',
        'type': 'income',
        'emoji': '💰',
      },
      {
        'id': 2,
        'name': 'Miete',
        'amount': -800.0,
        'date': DateTime.now().subtract(const Duration(days: 1)),
        'category': 'Wohnen',
        'type': 'expense',
        'emoji': '🏠',
      },
      {
        'id': 3,
        'name': 'Supermarkt Rewe',
        'amount': -54.30,
        'date': DateTime.now(),
        'category': 'Lebensmittel',
        'type': 'expense',
        'emoji': '🛒',
      },
      {
        'id': 4,
        'name': 'Monatskarte ÖPNV',
        'amount': -29.50,
        'date': DateTime.now().subtract(const Duration(days: 3)),
        'category': 'Transport',
        'type': 'expense',
        'emoji': '🚌',
      },
      {
        'id': 5,
        'name': 'Starbucks',
        'amount': -6.80,
        'date': DateTime.now().subtract(const Duration(days: 3)),
        'category': 'Essen',
        'type': 'expense',
        'emoji': '☕',
      },
      {
        'id': 6,
        'name': 'Apotheke',
        'amount': -18.40,
        'date': DateTime.now().subtract(const Duration(days: 5)),
        'category': 'Gesundheit',
        'type': 'expense',
        'emoji': '💊',
      },
      {
        'id': 7,
        'name': 'Netflix',
        'amount': -12.99,
        'date': DateTime.now().subtract(const Duration(days: 7)),
        'category': 'Unterhaltung',
        'type': 'expense',
        'emoji': '🎬',
      },
      {
        'id': 8,
        'name': 'Zalando',
        'amount': -89.90,
        'date': DateTime.now().subtract(const Duration(days: 10)),
        'category': 'Kleidung',
        'type': 'expense',
        'emoji': '👕',
      },
    ];
  }


  Future<void> _loadBudgets() async {
    // TODO: API Call -> GET /api/finance/budgets
    await Future.delayed(const Duration(milliseconds: 200));
    
    _budgets = [
      {
        'id': 1,
        'category': 'Lebensmittel',
        'budget': 400.0,
        'spent': 320.0,
        'emoji': '🛒',
        'color': 0xFF10B981,
      },
      {
        'id': 2,
        'category': 'Miete & Nebenkosten',
        'budget': 800.0,
        'spent': 800.0,
        'emoji': '🏠',
        'color': 0xFF3B82F6,
      },
      {
        'id': 3,
        'category': 'Transport',
        'budget': 200.0,
        'spent': 145.0,
        'emoji': '🚌',
        'color': 0xFFF97316,
      },
      {
        'id': 4,
        'category': 'Unterhaltung',
        'budget': 100.0,
        'spent': 89.0,
        'emoji': '🎬',
        'color': 0xFF8B5CF6,
      },
      {
        'id': 5,
        'category': 'Kleidung',
        'budget': 150.0,
        'spent': 120.0,
        'emoji': '👕',
        'color': 0xFFEC4899,
      },
      {
        'id': 6,
        'category': 'Gesundheit',
        'budget': 50.0,
        'spent': 35.0,
        'emoji': '💊',
        'color': 0xFFEF4444,
      },
    ];
  }

 
  Future<void> _loadStatistics() async {
    // TODO: API Call -> GET /api/finance/statistics
    await Future.delayed(const Duration(milliseconds: 200));
    
    _statistics = {
      'monthlyAverage': 1650.0,
      'savingsRate': 0.31, // 31%
      'topCategory': 'Wohnen',
      'transactionCount': _transactions.length,
    };
  }


  Future<bool> addTransaction(Map<String, dynamic> transaction) async {
    try {
      // TODO: API Call -> POST /api/finance/transactions
      await Future.delayed(const Duration(milliseconds: 300));
      
      final newTransaction = {
        ...transaction,
        'id': _transactions.length + 1,
        'date': DateTime.now(),
      };
      
      _transactions.insert(0, newTransaction);
      
    
      _updateBudgetSpending(
        transaction['category'],
        (transaction['amount'] as double).abs(),
      );
      
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Erstellen der Transaktion: $e';
      notifyListeners();
      return false;
    }
  }


  void _updateBudgetSpending(String category, double amount) {
    final budgetIndex = _budgets.indexWhere((b) => b['category'] == category);
    if (budgetIndex != -1) {
      _budgets[budgetIndex]['spent'] = 
          (_budgets[budgetIndex]['spent'] as double) + amount;
    }
  }


  Future<bool> deleteTransaction(int id) async {
    try {
      // TODO: API Call -> DELETE /api/finance/transactions/{id}
      await Future.delayed(const Duration(milliseconds: 300));
      
      _transactions.removeWhere((t) => t['id'] == id);
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Fehler beim Löschen der Transaktion: $e';
      notifyListeners();
      return false;
    }
  }


  List<Map<String, dynamic>> getTransactionsByCategory(String category) {
    return _transactions
        .where((t) => t['category'] == category)
        .toList();
  }

 
  List<Map<String, dynamic>> getTransactionsInRange(
    DateTime start,
    DateTime end,
  ) {
    return _transactions.where((t) {
      final date = t['date'] as DateTime;
      return date.isAfter(start) && date.isBefore(end);
    }).toList();
  }


  Map<String, double> getSpendingByCategory() {
    final Map<String, double> categorySpending = {};
    
    for (final transaction in _transactions) {
      final amount = transaction['amount'] as double;
      if (amount < 0) {
        final category = transaction['category'] as String;
        categorySpending[category] = 
            (categorySpending[category] ?? 0) + amount.abs();
      }
    }
    
    return categorySpending;
  }


  Future<bool> updateBudget(int id, double newBudget) async {
    try {
      // TODO: API Call -> PATCH /api/finance/budgets/{id}
      await Future.delayed(const Duration(milliseconds: 300));
      
      final index = _budgets.indexWhere((b) => b['id'] == id);
      if (index != -1) {
        _budgets[index]['budget'] = newBudget;
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Aktualisieren des Budgets: $e';
      notifyListeners();
      return false;
    }
  }

  Map<String, dynamic> getBudgetStatus() {
    final totalBudget = _budgets.fold<double>(
      0, (sum, b) => sum + (b['budget'] as double));
    final totalSpent = _budgets.fold<double>(
      0, (sum, b) => sum + (b['spent'] as double));
    
    return {
      'totalBudget': totalBudget,
      'totalSpent': totalSpent,
      'remaining': totalBudget - totalSpent,
      'percentage': totalBudget > 0 ? (totalSpent / totalBudget) : 0,
    };
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}