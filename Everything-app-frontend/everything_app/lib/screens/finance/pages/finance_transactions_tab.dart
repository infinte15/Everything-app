import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/finance_transaction.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/finance_format.dart';
import '../widgets/finance_section.dart';
import '../widgets/month_navigator.dart';
import '../widgets/recategorize_sheet.dart';
import '../widgets/transaction_tile.dart';

/// Alle Buchungen des Monats, nach Tagen gruppiert.
///
/// Der Kategorie-Chip an jeder Zeile öffnet das Umkategorisieren - der Weg dahin
/// muss kurz sein, weil die Automatik zwangsläufig danebenliegt und jede
/// Korrektur die nächsten Buchungen besser macht.
class FinanceTransactionsTab extends StatefulWidget {
  const FinanceTransactionsTab({super.key});

  @override
  State<FinanceTransactionsTab> createState() => _FinanceTransactionsTabState();
}

class _FinanceTransactionsTabState extends State<FinanceTransactionsTab> {
  String _query = '';

  /// null = alle, true = nur Einnahmen, false = nur Ausgaben.
  bool? _incomeFilter;

  List<FinanceTransaction> _filtered(List<FinanceTransaction> all) {
    final query = _query.trim().toLowerCase();
    return all.where((transaction) {
      if (_incomeFilter != null && transaction.isIncome != _incomeFilter) {
        return false;
      }
      if (query.isEmpty) return true;
      return transaction.title.toLowerCase().contains(query) ||
          transaction.description.toLowerCase().contains(query) ||
          transaction.category.toLowerCase().contains(query);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final transactions = _filtered(finance.transactions);

    // Nach Tag gruppieren. Die Liste kommt bereits absteigend sortiert, die
    // Reihenfolge der Gruppen ergibt sich also von selbst.
    final grouped = <DateTime, List<FinanceTransaction>>{};
    for (final transaction in transactions) {
      final day = DateTime(
        transaction.transactionDate.year,
        transaction.transactionDate.month,
        transaction.transactionDate.day,
      );
      grouped.putIfAbsent(day, () => []).add(transaction);
    }
    final days = grouped.keys.toList();

    return Column(
      children: [
        MonthNavigator(
          month: finance.currentMonth,
          onChanged: finance.loadMonthlyData,
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: Column(
            children: [
              TextField(
                onChanged: (value) => setState(() => _query = value),
                style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
                decoration: const InputDecoration(
                  hintText: 'Suchen',
                  prefixIcon:
                      Icon(Icons.search, size: 18, color: KineticTheme.textTertiary),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  _FilterChip(
                    label: 'Alle',
                    selected: _incomeFilter == null,
                    onTap: () => setState(() => _incomeFilter = null),
                  ),
                  const SizedBox(width: 8),
                  _FilterChip(
                    label: 'Ausgaben',
                    selected: _incomeFilter == false,
                    onTap: () => setState(() => _incomeFilter = false),
                  ),
                  const SizedBox(width: 8),
                  _FilterChip(
                    label: 'Einnahmen',
                    selected: _incomeFilter == true,
                    onTap: () => setState(() => _incomeFilter = true),
                  ),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: days.isEmpty
              ? FinanceEmpty(
                  icon: Icons.search_off,
                  title: 'Nichts gefunden',
                  message: _query.isEmpty
                      ? 'In diesem Monat gibt es keine Buchungen.'
                      : 'Keine Buchung passt auf "$_query".',
                )
              : RefreshIndicator(
                  onRefresh: () => finance.loadMonthlyData(finance.currentMonth),
                  color: KineticTheme.primary,
                  backgroundColor: KineticTheme.surfaceElevated,
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 96),
                    itemCount: days.length,
                    itemBuilder: (context, index) {
                      final day = days[index];
                      final entries = grouped[day]!;
                      return _DayGroup(day: day, transactions: entries);
                    },
                  ),
                ),
        ),
      ],
    );
  }
}

class _DayGroup extends StatelessWidget {
  final DateTime day;
  final List<FinanceTransaction> transactions;

  const _DayGroup({required this.day, required this.transactions});

  @override
  Widget build(BuildContext context) {
    // Tagessaldo: bei einem Tag mit Gehalt und Miete ist die Summe die
    // eigentliche Information, nicht die einzelnen Zeilen.
    final net = transactions.fold(
        0.0, (sum, t) => sum + (t.isIncome ? t.amount : -t.amount));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 20, 4, 8),
          child: Row(
            children: [
              Expanded(
                child: Text(FinanceFormat.dayLabel(day).toUpperCase(),
                    style: KineticTheme.label),
              ),
              Text(
                FinanceFormat.signed(net, income: net >= 0),
                style: KineticTheme.label.copyWith(fontSize: 10),
              ),
            ],
          ),
        ),
        Container(
          decoration: BoxDecoration(
            color: KineticTheme.surface,
            borderRadius: BorderRadius.circular(KineticTheme.radius),
          ),
          child: Column(
            children: [
              for (final transaction in transactions)
                TransactionTile(
                  transaction: transaction,
                  onCategoryTap: transaction.id == null
                      ? null
                      : () => RecategorizeSheet.show(context, transaction),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? KineticTheme.surfaceHighlight : KineticTheme.surfaceElevated,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: selected ? KineticTheme.primary.withValues(alpha: 0.5) : Colors.transparent,
          ),
        ),
        child: Text(
          label,
          style: KineticTheme.caption.copyWith(
            color: selected ? KineticTheme.textPrimary : KineticTheme.textTertiary,
          ),
        ),
      ),
    );
  }
}
