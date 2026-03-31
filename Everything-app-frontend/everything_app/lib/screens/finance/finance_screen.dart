import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import 'package:fl_chart/fl_chart.dart';
import '../../providers/finance_provider.dart';
import '../../models/finance_transaction.dart';
import '../../config/app_theme.dart';

// ─── Currency Formatter ────────────────────────────────────────────────────────
final _eur = NumberFormat.currency(locale: 'de_DE', symbol: '€');
String _fmt(double v) => _eur.format(v);

// ─── Category Metadata ─────────────────────────────────────────────────────────
const _categories = [
  _Cat('Lebensmittel', Icons.shopping_cart, Color(0xFF4CAF50)),
  _Cat('Transport', Icons.directions_car, Color(0xFF2196F3)),
  _Cat('Wohnen', Icons.home, Color(0xFF9C27B0)),
  _Cat('Gesundheit', Icons.favorite, Color(0xFFF44336)),
  _Cat('Unterhaltung', Icons.movie, Color(0xFFFF9800)),
  _Cat('Kleidung', Icons.checkroom, Color(0xFFE91E63)),
  _Cat('Restaurant', Icons.restaurant, Color(0xFFFF5722)),
  _Cat('Einnahmen', Icons.payments, Color(0xFF00BCD4)),
  _Cat('Sonstiges', Icons.category, Color(0xFF607D8B)),
];

class _Cat {
  final String name;
  final IconData icon;
  final Color color;
  const _Cat(this.name, this.icon, this.color);
}

_Cat _catFor(String name) =>
    _categories.firstWhere((c) => c.name == name,
        orElse: () => _categories.last);

// ─── Main Screen ───────────────────────────────────────────────────────────────

class FinanceScreen extends StatefulWidget {
  const FinanceScreen({super.key});

  @override
  State<FinanceScreen> createState() => _FinanceScreenState();
}

class _FinanceScreenState extends State<FinanceScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tab;

  @override
  void initState() {
    super.initState();
    _tab = TabController(length: 4, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<FinanceProvider>().loadMonthlyData(DateTime.now());
    });
  }

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F0F1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F0F1A),
        foregroundColor: Colors.white,
        title: const Text('Finanzen',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        bottom: TabBar(
          controller: _tab,
          labelColor: AppTheme.financeColor,
          unselectedLabelColor: Colors.white54,
          indicatorColor: AppTheme.financeColor,
          indicatorSize: TabBarIndicatorSize.label,
          tabs: const [
            Tab(text: 'Übersicht'),
            Tab(text: 'Transaktionen'),
            Tab(text: 'Verträge'),
            Tab(text: 'Budget'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tab,
        children: const [
          _OverviewTab(),
          _TransactionsTab(),
          _ContractsTab(),
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
    final finance = context.watch<FinanceProvider>();

    return RefreshIndicator(
      onRefresh: () => finance.loadMonthlyData(finance.currentMonth),
      color: AppTheme.financeColor,
      backgroundColor: const Color(0xFF1A1A2E),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          // ── Month Navigator ──
          _MonthNavigator(month: finance.currentMonth),
          const SizedBox(height: 16),

          // ── Balance Card ──
          _BalanceCard(
            balance: finance.balance,
            income: finance.totalIncome,
            expenses: finance.totalExpenses,
            isLoading: finance.isLoading,
          ),
          const SizedBox(height: 24),

          // ── Spending by Category Chart ──
          if (!finance.isLoading && finance.spendingByCategory.isNotEmpty) ...[
            const _SectionTitle('Ausgaben nach Kategorie'),
            const SizedBox(height: 12),
            _SpendingChart(data: finance.spendingByCategory),
            const SizedBox(height: 24),
          ],

          // ── Recent Transactions ──
          _SectionTitle('Letzte Transaktionen',
              trailing: TextButton(
                onPressed: () {},
                child: const Text('Alle',
                    style: TextStyle(color: AppTheme.financeColor)),
              )),
          const SizedBox(height: 8),
          if (finance.isLoading)
            const Center(
                child: CircularProgressIndicator(color: AppTheme.financeColor))
          else if (finance.recentTransactions.isEmpty)
            _empty('Noch keine Transaktionen')
          else
            ...finance.recentTransactions.map((t) => _TransactionTile(t: t)),
        ],
      ),
    );
  }
}

// ─── Month Navigator ──────────────────────────────────────────────────────────

class _MonthNavigator extends StatelessWidget {
  final DateTime month;
  const _MonthNavigator({required this.month});

  @override
  Widget build(BuildContext context) {
    final label = DateFormat('MMMM yyyy', 'de_DE').format(month);
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        IconButton(
          icon: const Icon(Icons.chevron_left, color: Colors.white70),
          onPressed: () => context.read<FinanceProvider>().loadMonthlyData(
              DateTime(month.year, month.month - 1)),
        ),
        Text(label,
            style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.w600)),
        IconButton(
          icon: const Icon(Icons.chevron_right, color: Colors.white70),
          onPressed: () => context.read<FinanceProvider>().loadMonthlyData(
              DateTime(month.year, month.month + 1)),
        ),
      ],
    );
  }
}

// ─── Balance Card ──────────────────────────────────────────────────────────────

class _BalanceCard extends StatelessWidget {
  final double balance;
  final double income;
  final double expenses;
  final bool isLoading;
  const _BalanceCard(
      {required this.balance,
      required this.income,
      required this.expenses,
      required this.isLoading});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0xFF1A1A2E),
            AppTheme.financeColor.withValues(alpha: 0.25),
          ],
        ),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
            color: AppTheme.financeColor.withValues(alpha: 0.3), width: 1),
      ),
      child: Column(
        children: [
          const Text('Verfügbares Guthaben',
              style: TextStyle(color: Colors.white54, fontSize: 13)),
          const SizedBox(height: 8),
          isLoading
              ? const CircularProgressIndicator(color: Colors.white)
              : Text(
                  _fmt(balance),
                  style: TextStyle(
                    fontSize: 40,
                    fontWeight: FontWeight.bold,
                    color: balance >= 0 ? Colors.white : Colors.redAccent,
                  ),
                ),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: _FlowTile(
                    label: 'Einnahmen',
                    value: income,
                    icon: Icons.arrow_upward_rounded,
                    color: const Color(0xFF4CAF50)),
              ),
              Container(
                  width: 1, height: 48, color: Colors.white12),
              Expanded(
                child: _FlowTile(
                    label: 'Ausgaben',
                    value: expenses,
                    icon: Icons.arrow_downward_rounded,
                    color: Colors.redAccent),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _FlowTile extends StatelessWidget {
  final String label;
  final double value;
  final IconData icon;
  final Color color;
  const _FlowTile(
      {required this.label,
      required this.value,
      required this.icon,
      required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 4),
          Text(label,
              style:
                  const TextStyle(color: Colors.white54, fontSize: 12)),
        ]),
        const SizedBox(height: 4),
        Text(_fmt(value),
            style: TextStyle(
                color: color, fontWeight: FontWeight.bold, fontSize: 16)),
      ],
    );
  }
}

// ─── Spending Chart ────────────────────────────────────────────────────────────

class _SpendingChart extends StatefulWidget {
  final Map<String, double> data;
  const _SpendingChart({required this.data});

  @override
  State<_SpendingChart> createState() => _SpendingChartState();
}

class _SpendingChartState extends State<_SpendingChart> {
  int _touched = -1;

  @override
  Widget build(BuildContext context) {
    final entries = widget.data.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final total = entries.fold(0.0, (s, e) => s + e.value);

    final sections = entries.asMap().entries.map((e) {
      final cat = _catFor(e.value.key);
      final isTouched = e.key == _touched;
      return PieChartSectionData(
        color: cat.color,
        value: e.value.value,
        radius: isTouched ? 64 : 54,
        title: isTouched ? '${(e.value.value / total * 100).round()}%' : '',
        titleStyle:
            const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
      );
    }).toList();

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white10),
      ),
      padding: const EdgeInsets.all(20),
      child: Column(children: [
        SizedBox(
          height: 180,
          child: Row(children: [
            Expanded(
              child: PieChart(
                PieChartData(
                  sections: sections,
                  centerSpaceRadius: 40,
                  sectionsSpace: 2,
                  pieTouchData: PieTouchData(
                    touchCallback: (event, response) {
                      setState(() {
                        if (!event.isInterestedForInteractions ||
                            response?.touchedSection == null) {
                          _touched = -1;
                        } else {
                          _touched = response!
                              .touchedSection!.touchedSectionIndex;
                        }
                      });
                    },
                  ),
                ),
              ),
            ),
            const SizedBox(width: 16),
            Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: entries.take(5).map((e) {
                final cat = _catFor(e.key);
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: Row(children: [
                    Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                            color: cat.color,
                            shape: BoxShape.circle)),
                    const SizedBox(width: 6),
                    Text(e.key,
                        style: const TextStyle(
                            color: Colors.white70, fontSize: 12)),
                    const SizedBox(width: 8),
                    Text(_fmt(e.value),
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 12,
                            fontWeight: FontWeight.w600)),
                  ]),
                );
              }).toList(),
            ),
          ]),
        ),
      ]),
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
  String _search = '';

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final filtered = finance.transactions.where((t) {
      if (_search.isEmpty) return true;
      return t.description.toLowerCase().contains(_search.toLowerCase()) ||
          t.category.toLowerCase().contains(_search.toLowerCase());
    }).toList();

    // Group by date
    final grouped = <String, List<FinanceTransaction>>{};
    for (final t in filtered) {
      final key = DateFormat('yyyy-MM-dd').format(t.transactionDate);
      grouped.putIfAbsent(key, () => []).add(t);
    }
    final days = grouped.keys.toList()..sort((a, b) => b.compareTo(a));

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F1A),
      body: Column(children: [
        // Search bar
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: TextField(
            style: const TextStyle(color: Colors.white),
            decoration: InputDecoration(
              hintText: 'Suchen...',
              hintStyle: const TextStyle(color: Colors.white38),
              prefixIcon: const Icon(Icons.search, color: Colors.white38),
              filled: true,
              fillColor: const Color(0xFF1A1A2E),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
            ),
            onChanged: (v) => setState(() => _search = v),
          ),
        ),

        // List
        Expanded(
          child: finance.isLoading
              ? const Center(
                  child: CircularProgressIndicator(
                      color: AppTheme.financeColor))
              : filtered.isEmpty
                  ? _empty('Keine Transaktionen gefunden')
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
                      itemCount: days.length,
                      itemBuilder: (_, i) {
                        final day = days[i];
                        final dayTx = grouped[day]!;
                        final date = DateTime.parse(day);
                        final dayTotal = dayTx.fold<double>(
                            0,
                            (s, t) => s +
                                (t.type == 'EINNAHME'
                                    ? t.amount
                                    : -t.amount));
                        return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // Day header
                              Padding(
                                padding:
                                    const EdgeInsets.symmetric(vertical: 8),
                                child: Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        _dayLabel(date),
                                        style: const TextStyle(
                                            color: Colors.white54,
                                            fontSize: 12,
                                            fontWeight: FontWeight.w600,
                                            letterSpacing: 0.5),
                                      ),
                                      Text(
                                        (dayTotal >= 0 ? '+' : '') +
                                            _fmt(dayTotal),
                                        style: TextStyle(
                                          color: dayTotal >= 0
                                              ? const Color(0xFF4CAF50)
                                              : Colors.redAccent,
                                          fontSize: 12,
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                    ]),
                              ),
                              ...dayTx.map((t) => _TransactionTile(t: t)),
                              const SizedBox(height: 4),
                            ]);
                      },
                    ),
        ),
      ]),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddSheet(context),
        backgroundColor: AppTheme.financeColor,
        foregroundColor: Colors.white,
        child: const Icon(Icons.add),
      ),
    );
  }

  String _dayLabel(DateTime d) {
    final now = DateTime.now();
    if (d.year == now.year && d.month == now.month && d.day == now.day) {
      return 'Heute · ${DateFormat('dd. MMMM', 'de_DE').format(d)}';
    }
    if (d.year == now.year &&
        d.month == now.month &&
        d.day == now.day - 1) {
      return 'Gestern · ${DateFormat('dd. MMMM', 'de_DE').format(d)}';
    }
    return DateFormat('EEEE · dd. MMMM', 'de_DE').format(d);
  }

  void _showAddSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF1A1A2E),
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (_) => const _AddTransactionSheet(),
    );
  }
}

// ─── Transaction Tile ──────────────────────────────────────────────────────────

class _TransactionTile extends StatelessWidget {
  final FinanceTransaction t;
  const _TransactionTile({required this.t});

  @override
  Widget build(BuildContext context) {
    final cat = _catFor(t.category);
    final isIncome = t.type == 'EINNAHME';
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: cat.color.withValues(alpha: 0.15),
            shape: BoxShape.circle,
          ),
          child: Icon(cat.icon, color: cat.color, size: 22),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(t.description,
                style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                    fontSize: 14),
                maxLines: 1,
                overflow: TextOverflow.ellipsis),
            const SizedBox(height: 2),
            Row(children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: cat.color.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(t.category,
                    style: TextStyle(
                        color: cat.color,
                        fontSize: 10,
                        fontWeight: FontWeight.w600)),
              ),
              if (t.isRecurring) ...[
                const SizedBox(width: 6),
                const Icon(Icons.repeat, size: 11, color: Colors.white38),
              ],
            ]),
          ]),
        ),
        Text(
          '${isIncome ? '+' : '-'}${_fmt(t.amount)}',
          style: TextStyle(
            color: isIncome
                ? const Color(0xFF4CAF50)
                : Colors.redAccent,
            fontWeight: FontWeight.bold,
            fontSize: 15,
          ),
        ),
      ]),
    );
  }
}

// ─── Add Transaction Sheet ─────────────────────────────────────────────────────

class _AddTransactionSheet extends StatefulWidget {
  const _AddTransactionSheet();

  @override
  State<_AddTransactionSheet> createState() => _AddTransactionSheetState();
}

class _AddTransactionSheetState extends State<_AddTransactionSheet> {
  bool _isExpense = true;
  String _category = 'Sonstiges';
  final _descCtrl = TextEditingController();
  final _amtCtrl = TextEditingController();
  DateTime _date = DateTime.now();
  bool _recurring = false;

  @override
  void dispose() {
    _descCtrl.dispose();
    _amtCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final amt = double.tryParse(_amtCtrl.text.replaceAll(',', '.')) ?? 0;
    if (amt <= 0 || _descCtrl.text.trim().isEmpty) return;

    final tx = FinanceTransaction(
      amount: amt,
      type: _isExpense ? 'AUSGABE' : 'EINNAHME',
      category: _category,
      description: _descCtrl.text.trim(),
      transactionDate: _date,
      isRecurring: _recurring,
    );

    final ok = await context.read<FinanceProvider>().addTransaction(tx);
    if (ok && mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final insets = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 24, 24, insets + 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Handle
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.white24,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),
          const Text('Neue Transaktion',
              style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.bold)),
          const SizedBox(height: 20),

          // Type toggle
          Row(children: [
            Expanded(
              child: _TypeChip(
                label: 'Ausgabe',
                selected: _isExpense,
                activeColor: Colors.redAccent,
                onTap: () => setState(() => _isExpense = true),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _TypeChip(
                label: 'Einnahme',
                selected: !_isExpense,
                activeColor: const Color(0xFF4CAF50),
                onTap: () => setState(() => _isExpense = false),
              ),
            ),
          ]),
          const SizedBox(height: 16),

          // Amount
          TextField(
            controller: _amtCtrl,
            keyboardType:
                const TextInputType.numberWithOptions(decimal: true),
            style: const TextStyle(
                color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
            decoration: InputDecoration(
              prefixText: '€ ',
              prefixStyle: const TextStyle(
                  color: Colors.white54, fontSize: 24),
              hintText: '0,00',
              hintStyle: const TextStyle(color: Colors.white24, fontSize: 24),
              filled: true,
              fillColor: const Color(0xFF0F0F1A),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none),
            ),
          ),
          const SizedBox(height: 12),

          // Description
          TextField(
            controller: _descCtrl,
            style: const TextStyle(color: Colors.white),
            decoration: InputDecoration(
              hintText: 'Beschreibung',
              hintStyle: const TextStyle(color: Colors.white38),
              filled: true,
              fillColor: const Color(0xFF0F0F1A),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none),
            ),
          ),
          const SizedBox(height: 16),

          // Category picker
          const Text('Kategorie',
              style: TextStyle(color: Colors.white54, fontSize: 13)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _categories
                .where((c) =>
                    _isExpense ? c.name != 'Einnahmen' : c.name == 'Einnahmen')
                .map((c) => GestureDetector(
                      onTap: () => setState(() => _category = c.name),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 150),
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: _category == c.name
                              ? c.color.withValues(alpha: 0.25)
                              : const Color(0xFF0F0F1A),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                            color: _category == c.name
                                ? c.color
                                : Colors.white12,
                          ),
                        ),
                        child: Row(mainAxisSize: MainAxisSize.min, children: [
                          Icon(c.icon, size: 14, color: c.color),
                          const SizedBox(width: 6),
                          Text(c.name,
                              style: TextStyle(
                                  color: _category == c.name
                                      ? c.color
                                      : Colors.white54,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600)),
                        ]),
                      ),
                    ))
                .toList(),
          ),
          const SizedBox(height: 16),

          // Date + recurring row
          Row(children: [
            Expanded(
              child: GestureDetector(
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: _date,
                    firstDate: DateTime(2020),
                    lastDate: DateTime.now().add(const Duration(days: 365)),
                  );
                  if (picked != null) setState(() => _date = picked);
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0F0F1A),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Row(children: [
                    const Icon(Icons.calendar_today,
                        size: 16, color: Colors.white54),
                    const SizedBox(width: 8),
                    Text(
                      DateFormat('dd.MM.yyyy').format(_date),
                      style: const TextStyle(color: Colors.white70),
                    ),
                  ]),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Row(children: [
              const Text('Wiederkehrend',
                  style: TextStyle(color: Colors.white54, fontSize: 12)),
              Switch(
                value: _recurring,
                onChanged: (v) => setState(() => _recurring = v),
                activeThumbColor: AppTheme.financeColor,
              ),
            ]),
          ]),
          const SizedBox(height: 20),

          // Save button
          FilledButton(
            onPressed: _save,
            style: FilledButton.styleFrom(
              backgroundColor: AppTheme.financeColor,
              padding: const EdgeInsets.symmetric(vertical: 16),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14)),
            ),
            child: const Text('Speichern',
                style:
                    TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}

class _TypeChip extends StatelessWidget {
  final String label;
  final bool selected;
  final Color activeColor;
  final VoidCallback onTap;
  const _TypeChip(
      {required this.label,
      required this.selected,
      required this.activeColor,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        alignment: Alignment.center,
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: selected
              ? activeColor.withValues(alpha: 0.2)
              : const Color(0xFF0F0F1A),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
              color: selected ? activeColor : Colors.white12),
        ),
        child: Text(label,
            style: TextStyle(
                color: selected ? activeColor : Colors.white38,
                fontWeight: FontWeight.bold)),
      ),
    );
  }
}

// ─── Contracts Tab ─────────────────────────────────────────────────────────────

class _ContractsTab extends StatelessWidget {
  const _ContractsTab();

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final contracts = finance.contracts;
    final monthlyTotal =
        contracts.fold<double>(0, (s, t) => s + t.amount);

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F1A),
      body: finance.isLoading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.financeColor))
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                // Summary
                Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [
                        const Color(0xFF1A1A2E),
                        AppTheme.financeColor.withValues(alpha: 0.2),
                      ],
                    ),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                        color: AppTheme.financeColor.withValues(alpha: 0.2)),
                  ),
                  child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Monatliche Verträge',
                                  style: TextStyle(
                                      color: Colors.white54, fontSize: 13)),
                              SizedBox(height: 4),
                              Text('Fixausgaben gesamt',
                                  style: TextStyle(
                                      color: Colors.white, fontSize: 12)),
                            ]),
                        Text(_fmt(monthlyTotal),
                            style: const TextStyle(
                                color: Colors.redAccent,
                                fontSize: 22,
                                fontWeight: FontWeight.bold)),
                      ]),
                ),
                const SizedBox(height: 20),

                if (contracts.isEmpty)
                  _empty('Keine Verträge vorhanden')
                else
                  ...contracts.map((c) => _ContractCard(t: c)),
              ],
            ),
    );
  }
}

class _ContractCard extends StatelessWidget {
  final FinanceTransaction t;
  const _ContractCard({required this.t});

  @override
  Widget build(BuildContext context) {
    final cat = _catFor(t.category);
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(children: [
        Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: cat.color.withValues(alpha: 0.15),
            shape: BoxShape.circle,
          ),
          child: Icon(cat.icon, color: cat.color, size: 24),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(t.description,
                style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600)),
            Text(t.category,
                style: const TextStyle(
                    color: Colors.white54, fontSize: 12)),
          ]),
        ),
        Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Text('-${_fmt(t.amount)}',
              style: const TextStyle(
                  color: Colors.redAccent,
                  fontWeight: FontWeight.bold)),
          Text(
              t.recurringFrequency == 'YEARLY'
                  ? 'jährlich'
                  : 'monatlich',
              style: const TextStyle(
                  color: Colors.white38, fontSize: 11)),
        ]),
      ]),
    );
  }
}

// ─── Budget Tab ────────────────────────────────────────────────────────────────

class _BudgetTab extends StatelessWidget {
  const _BudgetTab();

  // Static budget limits — in a real app these come from the backend
  static const _limits = {
    'Lebensmittel': 400.0,
    'Transport': 200.0,
    'Wohnen': 900.0,
    'Gesundheit': 80.0,
    'Unterhaltung': 120.0,
    'Kleidung': 150.0,
    'Restaurant': 150.0,
  };

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final spending = finance.spendingByCategory;

    final totalBudget =
        _limits.values.fold(0.0, (s, v) => s + v);
    final totalSpent =
        spending.values.fold(0.0, (s, v) => s + v);
    final available = totalBudget - totalSpent;

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F1A),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: [
          // Summary header
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: const Color(0xFF1A1A2E),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: Colors.white10),
            ),
            child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _BudgetSummaryItem(
                      label: 'Budget', value: totalBudget, color: Colors.white),
                  Container(width: 1, height: 40, color: Colors.white12),
                  _BudgetSummaryItem(
                      label: 'Ausgegeben',
                      value: totalSpent,
                      color: Colors.redAccent),
                  Container(width: 1, height: 40, color: Colors.white12),
                  _BudgetSummaryItem(
                      label: 'Verfügbar',
                      value: available,
                      color: available >= 0
                          ? const Color(0xFF4CAF50)
                          : Colors.redAccent),
                ]),
          ),
          const SizedBox(height: 20),

          ..._limits.entries.map((e) {
            final spent = spending[e.key] ?? 0.0;
            final cat = _catFor(e.key);
            final progress = (spent / e.value).clamp(0.0, 1.0);
            final isOver = spent > e.value;
            return Container(
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF1A1A2E),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                    color: isOver
                        ? Colors.redAccent.withValues(alpha: 0.4)
                        : Colors.white10),
              ),
              child: Column(children: [
                Row(children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: cat.color.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(cat.icon, color: cat.color, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(e.key,
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w600)),
                          Text(
                            isOver
                                ? '${_fmt((spent - e.value))} überzogen'
                                : '${_fmt(e.value - spent)} verfügbar',
                            style: TextStyle(
                                color: isOver
                                    ? Colors.redAccent
                                    : const Color(0xFF4CAF50),
                                fontSize: 11),
                          ),
                        ]),
                  ),
                  Text('${_fmt(spent)} / ${_fmt(e.value)}',
                      style: const TextStyle(
                          color: Colors.white54, fontSize: 11)),
                ]),
                const SizedBox(height: 12),
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: progress,
                    minHeight: 8,
                    backgroundColor: Colors.white10,
                    valueColor: AlwaysStoppedAnimation<Color>(
                        isOver ? Colors.redAccent : cat.color),
                  ),
                ),
              ]),
            );
          }),
        ],
      ),
    );
  }
}

class _BudgetSummaryItem extends StatelessWidget {
  final String label;
  final double value;
  final Color color;
  const _BudgetSummaryItem(
      {required this.label, required this.value, required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      Text(label,
          style: const TextStyle(color: Colors.white54, fontSize: 11)),
      const SizedBox(height: 4),
      Text(_fmt(value),
          style: TextStyle(
              color: color, fontWeight: FontWeight.bold, fontSize: 15)),
    ]);
  }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  final String text;
  final Widget? trailing;
  const _SectionTitle(this.text, {this.trailing});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(text,
            style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 16)),
        if (trailing != null) trailing,
      ],
    );
  }
}

Widget _empty(String msg) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 40),
      child: Center(
        child: Text(msg,
            style: const TextStyle(color: Colors.white38, fontSize: 14)),
      ),
    );