import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/app_theme.dart';

class FinanceScreen extends StatefulWidget {
  const FinanceScreen({super.key});

  @override
  State<FinanceScreen> createState() => _FinanceScreenState();
}

class _FinanceScreenState extends State<FinanceScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Finanzen'),
        backgroundColor: AppTheme.financeColor,
        foregroundColor: Colors.black,
        bottom: TabBar(
          controller: _tabController,
          labelColor: Colors.black,
          unselectedLabelColor: Colors.black54,
          indicatorColor: Colors.black,
          tabs: const [
            Tab(icon: Icon(Icons.account_balance_wallet), text: 'Übersicht'),
            Tab(icon: Icon(Icons.receipt_long), text: 'Transaktionen'),
            Tab(icon: Icon(Icons.pie_chart), text: 'Budget'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: const [
          _OverviewTab(),
          _TransactionsTab(),
          _BudgetTab(),
        ],
      ),
    );
  }
}

// ─── Overview Tab ──────────────────────────────────────────────────────────────

class _OverviewTab extends StatelessWidget {
  const _OverviewTab();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final income = 2450.0;
    final expenses = 1680.50;
    final balance = income - expenses;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Balance Card
        Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [AppTheme.financeColor, AppTheme.financeColor.withOpacity(0.7)],
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            children: [
              const Text('Kontostand',
                  style: TextStyle(color: Colors.black54, fontSize: 14)),
              const SizedBox(height: 8),
              Text(
                NumberFormat.currency(locale: 'de_DE', symbol: '€')
                    .format(balance),
                style: const TextStyle(
                    fontSize: 42,
                    fontWeight: FontWeight.bold,
                    color: Colors.black),
              ),
              const SizedBox(height: 16),
              Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
                _BalanceItem(
                    label: 'Einnahmen',
                    value: income,
                    icon: Icons.arrow_upward,
                    color: Colors.green),
                Container(width: 1, height: 40, color: Colors.black26),
                _BalanceItem(
                    label: 'Ausgaben',
                    value: expenses,
                    icon: Icons.arrow_downward,
                    color: Colors.red),
              ]),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Spending by Category
        Text('Ausgaben nach Kategorie',
            style: theme.textTheme.titleMedium
                ?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        _CategoryBar(label: 'Lebensmittel', amount: 320, budget: 400, color: Colors.green),
        _CategoryBar(label: 'Miete', amount: 800, budget: 800, color: Colors.blue),
        _CategoryBar(label: 'Transport', amount: 145, budget: 200, color: Colors.orange),
        _CategoryBar(label: 'Unterhaltung', amount: 89, budget: 100, color: Colors.purple),
        _CategoryBar(label: 'Kleidung', amount: 120, budget: 150, color: Colors.pink),

        const SizedBox(height: 24),
        Text('Letzte Transaktionen',
            style: theme.textTheme.titleMedium
                ?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        ...[
          _Transaction(emoji: '🛒', name: 'Supermarkt', amount: -54.30, date: 'Heute'),
          _Transaction(emoji: '💰', name: 'Gehalt', amount: 2450.0, date: 'Mo.'),
          _Transaction(emoji: '🚌', name: 'ÖPNV', amount: -29.50, date: 'So.'),
        ].map((t) => _TransactionTile(t: t)),
      ],
    );
  }
}

class _BalanceItem extends StatelessWidget {
  final String label;
  final double value;
  final IconData icon;
  final Color color;

  const _BalanceItem(
      {required this.label,
      required this.value,
      required this.icon,
      required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      Row(children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 4),
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.black54)),
      ]),
      const SizedBox(height: 4),
      Text(
        NumberFormat.currency(locale: 'de_DE', symbol: '€').format(value),
        style: TextStyle(fontWeight: FontWeight.bold, color: color),
      ),
    ]);
  }
}

class _CategoryBar extends StatelessWidget {
  final String label;
  final double amount;
  final double budget;
  final Color color;

  const _CategoryBar(
      {required this.label,
      required this.amount,
      required this.budget,
      required this.color});

  @override
  Widget build(BuildContext context) {
    final progress = (amount / budget).clamp(0.0, 1.0);
    final isOverBudget = amount > budget;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text(label, style: const TextStyle(fontWeight: FontWeight.w500)),
          Text(
            '${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(amount)} / '
            '${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(budget)}',
            style: TextStyle(
                fontSize: 12,
                color: isOverBudget ? Colors.red : Colors.grey),
          ),
        ]),
        const SizedBox(height: 4),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: LinearProgressIndicator(
            value: progress,
            backgroundColor: color.withOpacity(0.15),
            valueColor: AlwaysStoppedAnimation<Color>(
                isOverBudget ? Colors.red : color),
            minHeight: 8,
          ),
        ),
      ]),
    );
  }
}

class _Transaction {
  final String emoji;
  final String name;
  final double amount;
  final String date;
  _Transaction(
      {required this.emoji,
      required this.name,
      required this.amount,
      required this.date});
}

class _TransactionTile extends StatelessWidget {
  final _Transaction t;
  const _TransactionTile({required this.t});

  @override
  Widget build(BuildContext context) {
    final isIncome = t.amount > 0;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: (isIncome ? Colors.green : Colors.red).withOpacity(0.1),
        child: Text(t.emoji),
      ),
      title: Text(t.name, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: Text(t.date),
      trailing: Text(
        '${isIncome ? '+' : ''}${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(t.amount)}',
        style: TextStyle(
          color: isIncome ? Colors.green : Colors.red,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

// ─── Transactions Tab ──────────────────────────────────────────────────────────

class _TransactionsTab extends StatefulWidget {
  const _TransactionsTab();

  @override
  State<_TransactionsTab> createState() => _TransactionsTabState();
}

class _TransactionsTabState extends State<_TransactionsTab> {
  final List<Map<String, dynamic>> _transactions = [
    {'emoji': '🛒', 'name': 'Supermarkt Rewe', 'amount': -54.30, 'date': DateTime.now(), 'cat': 'Lebensmittel'},
    {'emoji': '💰', 'name': 'Gehalt November', 'amount': 2450.0, 'date': DateTime.now().subtract(const Duration(days: 2)), 'cat': 'Einnahmen'},
    {'emoji': '🚌', 'name': 'Monatskarte ÖPNV', 'amount': -29.50, 'date': DateTime.now().subtract(const Duration(days: 3)), 'cat': 'Transport'},
    {'emoji': '☕', 'name': 'Starbucks', 'amount': -6.80, 'date': DateTime.now().subtract(const Duration(days: 3)), 'cat': 'Essen'},
    {'emoji': '💊', 'name': 'Apotheke', 'amount': -18.40, 'date': DateTime.now().subtract(const Duration(days: 5)), 'cat': 'Gesundheit'},
    {'emoji': '🎬', 'name': 'Netflix', 'amount': -12.99, 'date': DateTime.now().subtract(const Duration(days: 7)), 'cat': 'Unterhaltung'},
    {'emoji': '👕', 'name': 'Zalando', 'amount': -89.90, 'date': DateTime.now().subtract(const Duration(days: 10)), 'cat': 'Kleidung'},
  ];

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: _transactions.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (_, i) {
            final t = _transactions[i];
            final isIncome = t['amount'] > 0;
            return ListTile(
              leading: CircleAvatar(
                backgroundColor:
                    (isIncome ? Colors.green : Colors.red).withOpacity(0.1),
                child: Text(t['emoji']),
              ),
              title: Text(t['name'],
                  style: const TextStyle(fontWeight: FontWeight.w500)),
              subtitle: Row(children: [
                Text(t['cat'],
                    style: const TextStyle(fontSize: 11, color: Colors.grey)),
                const SizedBox(width: 8),
                Text(
                  DateFormat('dd.MM.').format(t['date']),
                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ]),
              trailing: Text(
                '${isIncome ? '+' : ''}${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(t['amount'])}',
                style: TextStyle(
                  color: isIncome ? Colors.green : Colors.red,
                  fontWeight: FontWeight.bold,
                ),
              ),
            );
          },
        ),
        Positioned(
          bottom: 16,
          right: 16,
          child: FloatingActionButton(
            backgroundColor: AppTheme.financeColor,
            foregroundColor: Colors.black,
            onPressed: () => _showAddTransaction(context),
            child: const Icon(Icons.add),
          ),
        ),
      ],
    );
  }

  void _showAddTransaction(BuildContext context) {
    final nameController = TextEditingController();
    final amountController = TextEditingController();
    bool isExpense = true;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => StatefulBuilder(
        builder: (ctx, setModalState) => Padding(
          padding: EdgeInsets.only(
              left: 24,
              right: 24,
              top: 24,
              bottom: MediaQuery.of(ctx).viewInsets.bottom + 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('Neue Transaktion',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              Row(children: [
                Expanded(
                  child: ChoiceChip(
                    label: const Text('Ausgabe'),
                    selected: isExpense,
                    selectedColor: Colors.red,
                    labelStyle: TextStyle(
                        color: isExpense ? Colors.white : null),
                    onSelected: (_) => setModalState(() => isExpense = true),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ChoiceChip(
                    label: const Text('Einnahme'),
                    selected: !isExpense,
                    selectedColor: Colors.green,
                    labelStyle: TextStyle(
                        color: !isExpense ? Colors.white : null),
                    onSelected: (_) => setModalState(() => isExpense = false),
                  ),
                ),
              ]),
              const SizedBox(height: 16),
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                    labelText: 'Beschreibung', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: amountController,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(
                    labelText: 'Betrag (€)', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () {
                  setState(() {
                    _transactions.insert(0, {
                      'emoji': isExpense ? '💸' : '💰',
                      'name': nameController.text,
                      'amount': isExpense
                          ? -(double.tryParse(amountController.text) ?? 0)
                          : (double.tryParse(amountController.text) ?? 0),
                      'date': DateTime.now(),
                      'cat': isExpense ? 'Sonstiges' : 'Einnahmen',
                    });
                  });
                  Navigator.pop(context);
                },
                child: const Text('Hinzufügen'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Budget Tab ────────────────────────────────────────────────────────────────

class _BudgetTab extends StatelessWidget {
  const _BudgetTab();

  @override
  Widget build(BuildContext context) {
    final budgets = [
      _BudgetItem('Lebensmittel', 320, 400, '🛒', Colors.green),
      _BudgetItem('Miete & Nebenkosten', 800, 800, '🏠', Colors.blue),
      _BudgetItem('Transport', 145, 200, '🚌', Colors.orange),
      _BudgetItem('Unterhaltung', 89, 100, '🎬', Colors.purple),
      _BudgetItem('Kleidung', 120, 150, '👕', Colors.pink),
      _BudgetItem('Gesundheit', 35, 50, '💊', Colors.red),
    ];

    final totalBudget = budgets.fold<double>(0, (s, b) => s + b.budget);
    final totalSpent = budgets.fold<double>(0, (s, b) => s + b.spent);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Summary
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: AppTheme.financeColor.withOpacity(0.2),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              Column(children: [
                const Text('Budget', style: TextStyle(color: Colors.grey)),
                Text(
                  NumberFormat.currency(locale: 'de_DE', symbol: '€')
                      .format(totalBudget),
                  style: const TextStyle(
                      fontSize: 20, fontWeight: FontWeight.bold),
                ),
              ]),
              Column(children: [
                const Text('Ausgegeben',
                    style: TextStyle(color: Colors.grey)),
                Text(
                  NumberFormat.currency(locale: 'de_DE', symbol: '€')
                      .format(totalSpent),
                  style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Colors.red),
                ),
              ]),
              Column(children: [
                const Text('Verfügbar', style: TextStyle(color: Colors.grey)),
                Text(
                  NumberFormat.currency(locale: 'de_DE', symbol: '€')
                      .format(totalBudget - totalSpent),
                  style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Colors.green),
                ),
              ]),
            ],
          ),
        ),
        const SizedBox(height: 24),
        ...budgets.map((b) => _BudgetCard(item: b)),
      ],
    );
  }
}

class _BudgetItem {
  final String name;
  final double spent;
  final double budget;
  final String emoji;
  final Color color;
  _BudgetItem(this.name, this.spent, this.budget, this.emoji, this.color);
}

class _BudgetCard extends StatelessWidget {
  final _BudgetItem item;
  const _BudgetCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final progress = (item.spent / item.budget).clamp(0.0, 1.0);
    final isOver = item.spent > item.budget;
    final remaining = item.budget - item.spent;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Text(item.emoji, style: const TextStyle(fontSize: 24)),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.name,
                        style: const TextStyle(fontWeight: FontWeight.bold)),
                    Text(
                      isOver
                          ? '${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(remaining * -1)} überzogen'
                          : '${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(remaining)} verfügbar',
                      style: TextStyle(
                          fontSize: 12,
                          color: isOver ? Colors.red : Colors.green),
                    ),
                  ]),
            ),
            Text(
              '${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(item.spent)} / '
              '${NumberFormat.currency(locale: 'de_DE', symbol: '€').format(item.budget)}',
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ]),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              backgroundColor: item.color.withOpacity(0.15),
              valueColor: AlwaysStoppedAnimation<Color>(
                  isOver ? Colors.red : item.color),
              minHeight: 10,
            ),
          ),
        ]),
      ),
    );
  }
}