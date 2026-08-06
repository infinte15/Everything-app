import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/contract.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/contract_card.dart';
import '../widgets/finance_format.dart';
import '../widgets/finance_section.dart';

/// Verträge, gruppiert nach Fälligkeit.
///
/// Die Gruppierung ist die eigentliche Leistung dieses Tabs: eine alphabetische
/// Liste beantwortet keine Frage, "was kommt diese Woche noch" schon.
class FinanceContractsTab extends StatelessWidget {
  const FinanceContractsTab({super.key});

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();
    final contracts = finance.contracts;

    if (contracts.isEmpty) {
      return FinanceEmpty(
        icon: Icons.autorenew,
        title: 'Noch keine Verträge erkannt',
        message: finance.hasBankConnection
            ? 'Ab drei Buchungen in gleichem Abstand und mit ähnlichem Betrag '
                'entsteht hier automatisch ein Vertrag.'
            : 'Mit einem verbundenen Konto werden Abos, Miete und Gehalt '
                'automatisch erkannt.',
      );
    }

    final active = contracts.where((c) => c.active).toList()
      ..sort(_byDueDate);
    final inactive = contracts.where((c) => !c.active).toList();

    final income = active.where((c) => c.isIncome).toList();
    final expenses = active.where((c) => !c.isIncome).toList();

    final thisWeek = expenses.where((c) => _daysUntil(c) <= 7).toList();
    final later = expenses.where((c) => _daysUntil(c) > 7).toList();

    return RefreshIndicator(
      onRefresh: () => finance.loadMonthlyData(finance.currentMonth),
      color: KineticTheme.primary,
      backgroundColor: KineticTheme.surfaceElevated,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
        children: [
          _Summary(
            monthlyCost: finance.monthlyContractCost,
            count: expenses.length,
          ),

          if (income.isNotEmpty) ...[
            const FinanceSection(title: 'Einnahmen'),
            for (final contract in income) ContractCard(contract: contract),
          ],

          if (thisWeek.isNotEmpty) ...[
            const FinanceSection(title: 'In den nächsten 7 Tagen'),
            for (final contract in thisWeek) ContractCard(contract: contract),
          ],

          if (later.isNotEmpty) ...[
            const FinanceSection(title: 'Später'),
            for (final contract in later) ContractCard(contract: contract),
          ],

          if (inactive.isNotEmpty) ...[
            const FinanceSection(title: 'Vermutlich beendet'),
            Padding(
              padding: const EdgeInsets.only(bottom: 10, left: 4, right: 4),
              child: Text(
                'Seit deutlich mehr als einem Zyklus keine Buchung mehr. '
                'Kommt wieder eine, wird der Vertrag von selbst wieder aktiv.',
                style: KineticTheme.caption.copyWith(
                  fontSize: 12,
                  color: KineticTheme.textTertiary,
                ),
              ),
            ),
            for (final contract in inactive) ContractCard(contract: contract),
          ],
        ],
      ),
    );
  }

  static int _daysUntil(Contract contract) {
    if (contract.nextDueDate == null) return 9999;
    final today = DateTime.now();
    return DateTime(contract.nextDueDate!.year, contract.nextDueDate!.month,
            contract.nextDueDate!.day)
        .difference(DateTime(today.year, today.month, today.day))
        .inDays;
  }

  static int _byDueDate(Contract a, Contract b) =>
      _daysUntil(a).compareTo(_daysUntil(b));
}

class _Summary extends StatelessWidget {
  final double monthlyCost;
  final int count;

  const _Summary({required this.monthlyCost, required this.count});

  @override
  Widget build(BuildContext context) {
    return FinanceCard(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('FESTE KOSTEN PRO MONAT', style: KineticTheme.label),
          const SizedBox(height: 8),
          Text(FinanceFormat.money(monthlyCost),
              style: KineticTheme.figure.copyWith(fontSize: 28)),
          const SizedBox(height: 4),
          Text(
            // Auf einen Monat normalisiert: eine halbjährliche Versicherung
            // steht sonst in einem Monat voll drin und in fünf gar nicht.
            '$count Verträge · Jahresbeiträge anteilig gerechnet',
            style: KineticTheme.caption,
          ),
          const SizedBox(height: 10),
          Text(
            '${FinanceFormat.money(monthlyCost * 12)} im Jahr',
            style: KineticTheme.caption.copyWith(color: FinanceTheme.income),
          ),
        ],
      ),
    );
  }
}
