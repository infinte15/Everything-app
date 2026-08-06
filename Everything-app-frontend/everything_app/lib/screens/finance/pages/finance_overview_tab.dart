import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/finance_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/account_card.dart';
import '../widgets/available_card.dart';
import '../widgets/category_donut.dart';
import '../widgets/connection_banner.dart';
import '../widgets/finance_format.dart';
import '../widgets/finance_section.dart';
import '../widgets/forecast_chart.dart';
import '../widgets/month_navigator.dart';
import '../widgets/recategorize_sheet.dart';
import '../widgets/transaction_tile.dart';

/// Übersicht: die Kernzahl oben, dann Konten, Verlauf, Kategorien, letzte
/// Buchungen.
///
/// Die Reihenfolge ist die Antwortreihenfolge auf die Fragen, mit denen man den
/// Bildschirm öffnet: Wie viel habe ich noch? Wie viel ist auf dem Konto? Wie
/// entwickelt es sich? Wofür ging es weg?
class FinanceOverviewTab extends StatelessWidget {
  final VoidCallback onConnectBank;
  final VoidCallback onShowAllTransactions;

  const FinanceOverviewTab({
    super.key,
    required this.onConnectBank,
    required this.onShowAllTransactions,
  });

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final attention = finance.connectionNeedingAttention;

    return RefreshIndicator(
      onRefresh: () => finance.loadMonthlyData(finance.currentMonth),
      color: KineticTheme.primary,
      backgroundColor: KineticTheme.surfaceElevated,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
        children: [
          MonthNavigator(
            month: finance.currentMonth,
            onChanged: finance.loadMonthlyData,
          ),
          const SizedBox(height: 16),

          if (attention != null)
            ConnectionBanner(connection: attention, onReconnect: onConnectBank),

          AvailableCard(forecast: finance.forecast, onConnect: onConnectBank),

          if (finance.hasBankConnection) ...[
            const SizedBox(height: 12),
            AccountCard(
              accounts: finance.accounts,
              lastSyncAt: finance.lastSyncAt,
              isSyncing: finance.isSyncing,
              isDemo: finance.isDemo,
              onSync: () => _sync(context),
              onManage: onConnectBank,
            ),
          ],

          if (finance.forecast != null && finance.forecast!.series.length > 1) ...[
            const SizedBox(height: 12),
            ForecastChart(forecast: finance.forecast!),
          ],

          const FinanceSection(title: 'Monat'),
          Row(
            children: [
              Expanded(
                child: _MiniStat(
                  label: 'Eingegangen',
                  value: FinanceFormat.money(finance.totalIncome),
                  accent: KineticTheme.primary,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _MiniStat(
                  label: 'Ausgegeben',
                  value: FinanceFormat.money(finance.totalExpenses),
                ),
              ),
            ],
          ),

          if (finance.spendingByCategory.isNotEmpty) ...[
            const SizedBox(height: 12),
            CategoryDonut(spendingByCategory: finance.spendingByCategory),
          ],

          if (finance.recentTransactions.isNotEmpty) ...[
            FinanceSection(
              title: 'Letzte Buchungen',
              action: 'Alle',
              onAction: onShowAllTransactions,
            ),
            Container(
              decoration: BoxDecoration(
                color: KineticTheme.surface,
                borderRadius: BorderRadius.circular(KineticTheme.radius),
              ),
              child: Column(
                children: [
                  for (final transaction in finance.recentTransactions)
                    TransactionTile(
                      transaction: transaction,
                      onCategoryTap: transaction.id == null
                          ? null
                          : () => RecategorizeSheet.show(context, transaction),
                    ),
                ],
              ),
            ),
          ] else if (!finance.isLoading) ...[
            const SizedBox(height: 32),
            FinanceEmpty(
              icon: Icons.receipt_long,
              title: 'Noch keine Buchungen',
              message: finance.hasBankConnection
                  ? 'In diesem Monat ist noch nichts gebucht worden.'
                  : 'Verbinde ein Konto oder trage eine Buchung von Hand ein.',
              actionLabel: finance.hasBankConnection ? null : 'Konto verbinden',
              onAction: onConnectBank,
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _sync(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final result = await context.read<FinanceProvider>().syncBank();
    if (result == null) return;

    messenger.showSnackBar(SnackBar(
      content: Text(result.imported == 0
          ? 'Keine neuen Buchungen.'
          : '${result.imported} neue Buchungen übernommen.'),
    ));
  }
}

class _MiniStat extends StatelessWidget {
  final String label;
  final String value;
  final Color? accent;

  const _MiniStat({required this.label, required this.value, this.accent});

  @override
  Widget build(BuildContext context) {
    return FinanceCard(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(), style: KineticTheme.label),
          const SizedBox(height: 6),
          Text(
            value,
            style: KineticTheme.amount.copyWith(
              fontSize: 19,
              color: accent ?? KineticTheme.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}
