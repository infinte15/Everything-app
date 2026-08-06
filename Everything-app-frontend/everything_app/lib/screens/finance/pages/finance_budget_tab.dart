import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/budget_category.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/budget_sheet.dart';
import '../widgets/finance_format.dart';
import '../widgets/finance_section.dart';

/// Budgets und ihr Verbrauch.
///
/// Rechnet seit dem Umbau gegen das Backend (`/api/finance/budgets` samt
/// `/stats/budget-progress`) statt gegen hartcodierte Grenzen - das Backend gab
/// es lange, angeschlossen war es nie.
class FinanceBudgetTab extends StatefulWidget {
  const FinanceBudgetTab({super.key});

  @override
  State<FinanceBudgetTab> createState() => _FinanceBudgetTabState();
}

class _FinanceBudgetTabState extends State<FinanceBudgetTab> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<FinanceProvider>().loadBudgets();
    });
  }

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final progress = finance.budgetProgress;

    if (progress.isEmpty) {
      return FinanceEmpty(
        icon: Icons.pie_chart_outline,
        title: 'Noch keine Budgets',
        message: 'Setze eine monatliche Grenze für eine Kategorie. Der Verbrauch '
            'wird gegen die importierten Ausgaben gerechnet.',
        actionLabel: 'Budget anlegen',
        onAction: () => BudgetSheet.show(context),
      );
    }

    final totalLimit = progress.fold(0.0, (sum, p) => sum + p.limitAmount);
    final totalSpent = progress.fold(0.0, (sum, p) => sum + p.spentAmount);

    return RefreshIndicator(
      onRefresh: () => finance.loadBudgets(),
      color: KineticTheme.primary,
      backgroundColor: KineticTheme.surfaceElevated,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
        children: [
          FinanceCard(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('BUDGET DIESEN MONAT', style: KineticTheme.label),
                const SizedBox(height: 8),
                Text(
                  FinanceFormat.money((totalLimit - totalSpent).clamp(0, double.infinity)),
                  style: KineticTheme.figure.copyWith(
                    fontSize: 28,
                    color: totalSpent > totalLimit
                        ? FinanceTheme.shortfall
                        : KineticTheme.textPrimary,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'von ${FinanceFormat.money(totalLimit)} übrig',
                  style: KineticTheme.caption,
                ),
              ],
            ),
          ),

          FinanceSection(
            title: 'Kategorien',
            action: '+ Budget',
            onAction: () => BudgetSheet.show(context),
          ),

          for (final entry in progress)
            _BudgetRow(
              progress: entry,
              onTap: () => BudgetSheet.show(
                context,
                existing: _matching(finance.budgets, entry),
              ),
            ),
        ],
      ),
    );
  }

  /// Der Fortschritt kommt fertig gerechnet, trägt aber nicht das Budget selbst -
  /// zum Ändern braucht das Sheet die Kategorie mit ihrer ID.
  BudgetCategory? _matching(List<BudgetCategory> budgets, BudgetProgress progress) {
    for (final budget in budgets) {
      if (budget.id == progress.categoryId || budget.name == progress.categoryName) {
        return budget;
      }
    }
    return null;
  }
}

class _BudgetRow extends StatelessWidget {
  final BudgetProgress progress;
  final VoidCallback onTap;

  const _BudgetRow({required this.progress, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final share = progress.limitAmount > 0
        ? (progress.spentAmount / progress.limitAmount).clamp(0.0, 1.0)
        : 0.0;
    final color = progress.isOverBudget
        ? FinanceTheme.shortfall
        : FinanceTheme.categoryColor(progress.categoryName);

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: FinanceCard(
        onTap: onTap,
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: FinanceTheme.categoryColor(progress.categoryName),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(progress.categoryName,
                      style: KineticTheme.title.copyWith(fontSize: 15)),
                ),
                Text(
                  FinanceFormat.money(progress.spentAmount),
                  style: KineticTheme.amount.copyWith(fontSize: 14, color: color),
                ),
                Text(
                  ' / ${FinanceFormat.moneyShort(progress.limitAmount)}',
                  style: KineticTheme.caption.copyWith(fontSize: 13),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: share,
                minHeight: 4,
                backgroundColor: KineticTheme.surfaceElevated,
                color: color,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              progress.isOverBudget
                  ? '${FinanceFormat.money(progress.spentAmount - progress.limitAmount)} über der Grenze'
                  : '${FinanceFormat.money(progress.remainingAmount)} übrig',
              style: KineticTheme.caption.copyWith(
                fontSize: 12,
                color: progress.isOverBudget
                    ? FinanceTheme.shortfall
                    : KineticTheme.textTertiary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
