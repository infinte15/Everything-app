import 'package:flutter/material.dart';

import '../../../models/finance_transaction.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';

/// Eine Buchung in der Liste.
///
/// Die Gegenpartei steht groß, der Verwendungszweck klein darunter - wer eine
/// Buchung wiedererkennen will, sucht nach "REWE", nicht nach der Belegnummer.
/// Bei getippten Buchungen gibt es keine Gegenpartei, dort trägt die
/// Beschreibung allein.
class TransactionTile extends StatelessWidget {
  final FinanceTransaction transaction;
  final VoidCallback? onTap;
  final VoidCallback? onCategoryTap;

  const TransactionTile({
    super.key,
    required this.transaction,
    this.onTap,
    this.onCategoryTap,
  });

  @override
  Widget build(BuildContext context) {
    final subtitle = transaction.subtitle;

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _CategoryMark(
              category: transaction.category,
              isContract: transaction.contractId != null,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    transaction.title,
                    style: KineticTheme.title.copyWith(fontSize: 15),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: KineticTheme.caption.copyWith(
                        fontSize: 12,
                        color: KineticTheme.textTertiary,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                  const SizedBox(height: 6),
                  _CategoryChip(
                    label: transaction.category,
                    locked: transaction.categoryLocked,
                    onTap: onCategoryTap,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Text(
              FinanceFormat.signed(transaction.amount, income: transaction.isIncome),
              style: KineticTheme.amount.copyWith(
                color: FinanceTheme.amountColor(income: transaction.isIncome),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Farbmarke der Kategorie - dieselbe Farbe wie im Donut, damit die Zuordnung
/// ohne Legende funktioniert.
class _CategoryMark extends StatelessWidget {
  final String category;
  final bool isContract;

  const _CategoryMark({required this.category, required this.isContract});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(
        color: FinanceTheme.categoryColor(category).withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(9),
      ),
      child: isContract
          // Ein Vertrag ist keine gewöhnliche Buchung: er wiederholt sich, und
          // genau das soll man in der Liste sehen.
          ? Icon(Icons.autorenew, size: 15, color: FinanceTheme.categoryColor(category))
          : Center(
              child: Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: FinanceTheme.categoryColor(category),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
    );
  }
}

class _CategoryChip extends StatelessWidget {
  final String label;
  final bool locked;
  final VoidCallback? onTap;

  const _CategoryChip({required this.label, required this.locked, this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: KineticTheme.surfaceElevated,
          borderRadius: BorderRadius.circular(6),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(label, style: KineticTheme.label.copyWith(fontSize: 10)),
            if (locked) ...[
              const SizedBox(width: 4),
              // Selbst gesetzt - die Automatik fasst sie nicht mehr an.
              const Icon(Icons.push_pin, size: 9, color: KineticTheme.textTertiary),
            ],
          ],
        ),
      ),
    );
  }
}
