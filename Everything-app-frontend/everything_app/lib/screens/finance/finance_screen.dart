import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../providers/finance_provider.dart';
import '../../theme/kinetic_theme.dart';
import 'pages/bank_connect_page.dart';
import 'pages/finance_budget_tab.dart';
import 'pages/finance_contracts_tab.dart';
import 'pages/finance_overview_tab.dart';
import 'pages/finance_transactions_tab.dart';
import 'widgets/add_transaction_sheet.dart';

/// Der Finance Space.
///
/// Aufbau wie beim Gym Space: der ganze Bereich wird in [KineticTheme.darkTheme]
/// gewickelt und hat seine eigene Navigation. Die frühere Version brachte ihre
/// Farben selbst mit (`0xFF0F0F1A`, `0xFF1A1A2E`) und stand damit als einziger
/// Space außerhalb der Formensprache der App.
///
/// Grün ist aus dem Space verschwunden. Es bleibt die Space-Farbe im Raster und
/// im Kalender - drinnen aber macht eine grün-rote Oberfläche aus jeder Ausgabe
/// eine schlechte Nachricht, und die meisten Ausgaben sind schlicht normal.
class FinanceScreen extends StatefulWidget {
  const FinanceScreen({super.key});

  @override
  State<FinanceScreen> createState() => _FinanceScreenState();
}

class _FinanceScreenState extends State<FinanceScreen> {
  int _tabIndex = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final finance = context.read<FinanceProvider>();
      finance.loadMonthlyData(DateTime.now());
      finance.loadProviderStatus();
    });
  }

  void _openBankConnect() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const BankConnectPage()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();

    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        appBar: AppBar(
          title: const Text('Finanzen'),
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => context.go('/spaces'),
          ),
          actions: [
            IconButton(
              onPressed: _openBankConnect,
              icon: const Icon(Icons.account_balance, size: 20),
              tooltip: 'Konten',
            ),
          ],
        ),
        body: Stack(
          children: [
            IndexedStack(
              index: _tabIndex,
              children: [
                FinanceOverviewTab(
                  onConnectBank: _openBankConnect,
                  onShowAllTransactions: () => setState(() => _tabIndex = 1),
                ),
                const FinanceTransactionsTab(),
                const FinanceContractsTab(),
                const FinanceBudgetTab(),
              ],
            ),
            if (finance.isLoading)
              const Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: LinearProgressIndicator(
                  minHeight: 2,
                  backgroundColor: Colors.transparent,
                  color: KineticTheme.primary,
                ),
              ),
          ],
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () => AddTransactionSheet.show(context),
          backgroundColor: KineticTheme.primary,
          foregroundColor: KineticTheme.onPrimary,
          elevation: 0,
          child: const Icon(Icons.add),
        ),
        bottomNavigationBar: _FinanceBottomNav(
          selectedIndex: _tabIndex,
          onSelect: (index) => setState(() => _tabIndex = index),
        ),
      ),
    );
  }
}

/// Eigene Leiste statt [BottomNavigationBar]: dieselbe zurückhaltende Form wie im
/// Gym Space - Icon und Beschriftung, aktiv in Periwinkle, kein Hintergrund und
/// keine Animation.
class _FinanceBottomNav extends StatelessWidget {
  final int selectedIndex;
  final ValueChanged<int> onSelect;

  const _FinanceBottomNav({required this.selectedIndex, required this.onSelect});

  static const _items = [
    (Icons.dashboard_outlined, Icons.dashboard, 'Übersicht'),
    (Icons.receipt_long_outlined, Icons.receipt_long, 'Buchungen'),
    (Icons.autorenew_outlined, Icons.autorenew, 'Verträge'),
    (Icons.pie_chart_outline, Icons.pie_chart, 'Budget'),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: KineticTheme.background,
        border: Border(top: BorderSide(color: KineticTheme.divider, width: 0.5)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 58,
          child: Row(
            children: [
              for (var i = 0; i < _items.length; i++)
                Expanded(
                  child: InkWell(
                    onTap: () => onSelect(i),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          selectedIndex == i ? _items[i].$2 : _items[i].$1,
                          size: 21,
                          color: selectedIndex == i
                              ? KineticTheme.primary
                              : KineticTheme.textTertiary,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _items[i].$3,
                          style: KineticTheme.label.copyWith(
                            fontSize: 9,
                            color: selectedIndex == i
                                ? KineticTheme.primary
                                : KineticTheme.textTertiary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
