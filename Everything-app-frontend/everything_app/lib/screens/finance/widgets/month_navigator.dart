import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';

/// Monatswechsel. Nach vorn nur bis zum laufenden Monat - für die Zukunft gibt
/// es keine Buchungen, nur die Prognose des aktuellen Monats.
class MonthNavigator extends StatelessWidget {
  final DateTime month;
  final ValueChanged<DateTime> onChanged;

  const MonthNavigator({super.key, required this.month, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final canGoForward = month.year < now.year ||
        (month.year == now.year && month.month < now.month);

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _Arrow(
          icon: Icons.chevron_left,
          onTap: () => onChanged(DateTime(month.year, month.month - 1)),
        ),
        SizedBox(
          width: 160,
          child: Text(
            FinanceFormat.month(month),
            style: KineticTheme.title,
            textAlign: TextAlign.center,
          ),
        ),
        _Arrow(
          icon: Icons.chevron_right,
          onTap: canGoForward
              ? () => onChanged(DateTime(month.year, month.month + 1))
              : null,
        ),
      ],
    );
  }
}

class _Arrow extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onTap;

  const _Arrow({required this.icon, this.onTap});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: onTap,
      icon: Icon(icon, size: 22),
      color: KineticTheme.textSecondary,
      disabledColor: KineticTheme.textTertiary.withValues(alpha: 0.35),
    );
  }
}
