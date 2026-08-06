import 'package:flutter/material.dart';

import '../../../models/contract.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';
import 'finance_section.dart';

/// Ein erkannter Vertrag.
///
/// "Erkannt aus 14 Buchungen" steht bewusst dabei: der Vertrag ist eine
/// Schlussfolgerung des Systems, keine Eingabe des Nutzers. Wer nicht sieht,
/// worauf sie beruht, kann sie weder glauben noch korrigieren.
class ContractCard extends StatelessWidget {
  final Contract contract;
  final VoidCallback? onTap;

  const ContractCard({super.key, required this.contract, this.onTap});

  @override
  Widget build(BuildContext context) {
    final inactive = !contract.active;

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: FinanceCard(
        onTap: onTap,
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: FinanceTheme.categoryColor(contract.category)
                    .withValues(alpha: inactive ? 0.08 : 0.16),
                borderRadius: BorderRadius.circular(9),
              ),
              child: Icon(
                contract.isIncome ? Icons.south_west : Icons.autorenew,
                size: 16,
                color: FinanceTheme.categoryColor(contract.category)
                    .withValues(alpha: inactive ? 0.5 : 1),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    contract.name,
                    style: KineticTheme.title.copyWith(
                      fontSize: 15,
                      color: inactive
                          ? KineticTheme.textTertiary
                          : KineticTheme.textPrimary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 3),
                  Text(_subtitle(), style: KineticTheme.caption.copyWith(fontSize: 12)),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      _Tag(label: contract.frequencyLabel),
                      const SizedBox(width: 6),
                      if (contract.detectedAutomatically)
                        _Tag(label: 'aus ${contract.occurrenceCount} Buchungen')
                      else
                        const _Tag(label: 'von Hand'),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  FinanceFormat.money(contract.amount),
                  style: KineticTheme.amount.copyWith(
                    color: inactive
                        ? KineticTheme.textTertiary
                        : (contract.isIncome
                            ? FinanceTheme.income
                            : KineticTheme.textPrimary),
                  ),
                ),
                if (contract.monthlyAmount != null &&
                    contract.frequency != 'MONTHLY') ...[
                  const SizedBox(height: 2),
                  Text(
                    '${FinanceFormat.money(contract.monthlyAmount!)}/Mon.',
                    style: KineticTheme.label.copyWith(fontSize: 9),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _subtitle() {
    if (!contract.active) {
      // Nicht "gekündigt": das System weiß nur, dass seit langem nichts mehr
      // gebucht wurde. Eine Kündigung kann es nicht sehen.
      return 'vermutlich beendet · zuletzt '
          '${contract.lastBookingDate == null ? "unbekannt" : FinanceFormat.shortDate(contract.lastBookingDate!)}';
    }
    if (contract.nextDueDate == null) {
      return contract.category;
    }
    final days = DateTime(contract.nextDueDate!.year, contract.nextDueDate!.month,
            contract.nextDueDate!.day)
        .difference(DateTime.now().copyWith(
            hour: 0, minute: 0, second: 0, millisecond: 0, microsecond: 0))
        .inDays;

    if (days < 0) return 'überfällig seit ${-days} Tagen';
    if (days == 0) return 'heute fällig';
    if (days == 1) return 'morgen fällig';
    return 'in $days Tagen · ${FinanceFormat.shortDate(contract.nextDueDate!)}';
  }
}

class _Tag extends StatelessWidget {
  final String label;

  const _Tag({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: KineticTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(5),
      ),
      child: Text(label, style: KineticTheme.label.copyWith(fontSize: 9)),
    );
  }
}
