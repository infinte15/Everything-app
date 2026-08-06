import 'package:flutter/material.dart';

import '../../../models/finance_forecast.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';
import 'finance_section.dart';

/// Die Kernzahl des Space: was bis Monatsende noch bleibt.
///
/// Ganzbreit und mit der größten Schrift im Space - sie ist der Grund, warum man
/// den Bildschirm überhaupt öffnet. Ohne verbundenes Konto steht hier keine
/// erfundene Null, sondern die Aufforderung, ein Konto zu verbinden.
class AvailableCard extends StatelessWidget {
  final FinanceForecast? forecast;
  final VoidCallback onConnect;

  const AvailableCard({super.key, required this.forecast, required this.onConnect});

  @override
  Widget build(BuildContext context) {
    final data = forecast;

    if (data == null || !data.hasBalance) {
      return FinanceCard(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('VERFÜGBAR BIS MONATSENDE', style: KineticTheme.label),
            const SizedBox(height: 12),
            Text(
              'Ohne verbundenes Konto lässt sich das nicht sagen.',
              style: KineticTheme.subtitle,
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onConnect,
              icon: const Icon(Icons.account_balance, size: 18),
              label: const Text('Konto verbinden'),
            ),
          ],
        ),
      );
    }

    final available = data.available!;
    final color = data.shortfall ? FinanceTheme.shortfall : KineticTheme.textPrimary;

    return FinanceCard(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('VERFÜGBAR BIS MONATSENDE', style: KineticTheme.label),
          const SizedBox(height: 8),
          Text(
            FinanceFormat.money(available),
            style: KineticTheme.figure.copyWith(color: color),
          ),
          const SizedBox(height: 4),
          Text(
            data.daysRemaining == 0
                ? 'Der Monat ist durch.'
                : 'noch ${data.daysRemaining} Tage',
            style: KineticTheme.caption,
          ),
          if (data.shortfall) ...[
            const SizedBox(height: 12),
            _ShortfallHint(missing: available.abs()),
          ],
          const SizedBox(height: 18),
          const Divider(height: 1, color: KineticTheme.divider),
          const SizedBox(height: 14),

          // Die Bestandteile einzeln. Eine Kernzahl ohne Herleitung ist ein
          // Orakel - man kann ihr weder trauen noch widersprechen.
          _Line(
            label: 'Kontostand heute',
            value: FinanceFormat.money(data.currentBalance ?? 0),
          ),
          if (data.upcomingContractIncome > 0)
            _Line(
              label: 'erwartete Einnahmen',
              value: FinanceFormat.signed(data.upcomingContractIncome, income: true),
              valueColor: FinanceTheme.income,
            ),
          if (data.upcomingContractExpenses > 0)
            _Line(
              label: 'offene Verträge',
              value: FinanceFormat.signed(data.upcomingContractExpenses, income: false),
            ),
          _Line(
            label: 'Alltag (Ø ${FinanceFormat.money(data.averageDailyVariableExpenses)}/Tag)',
            value: FinanceFormat.signed(data.projectedVariableExpenses, income: false),
          ),
        ],
      ),
    );
  }
}

class _ShortfallHint extends StatelessWidget {
  final double missing;

  const _ShortfallHint({required this.missing});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: FinanceTheme.shortfall.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          const Icon(Icons.trending_down, size: 16, color: FinanceTheme.shortfall),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Es fehlen ${FinanceFormat.money(missing)} bis Monatsende.',
              style: KineticTheme.caption.copyWith(color: FinanceTheme.shortfall),
            ),
          ),
        ],
      ),
    );
  }
}

class _Line extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;

  const _Line({required this.label, required this.value, this.valueColor});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(label, style: KineticTheme.caption)),
          Text(
            value,
            style: KineticTheme.amount.copyWith(
              fontSize: 14,
              color: valueColor ?? KineticTheme.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}
