import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../config/finance_categories.dart';
import '../../../models/finance_transaction.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';

/// Kategorie einer Buchung korrigieren.
///
/// Die Korrektur ist mehr als eine Änderung an dieser einen Zeile: das Backend
/// leitet daraus eine Regel auf die Gegenpartei ab, künftige Buchungen derselben
/// Quelle landen also von selbst richtig. Das steht im Sheet, weil es sonst
/// unsichtbar bliebe - und weil man wissen sollte, was man gerade auslöst.
class RecategorizeSheet extends StatefulWidget {
  final FinanceTransaction transaction;

  const RecategorizeSheet({super.key, required this.transaction});

  static Future<void> show(BuildContext context, FinanceTransaction transaction) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: KineticTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => RecategorizeSheet(transaction: transaction),
    );
  }

  @override
  State<RecategorizeSheet> createState() => _RecategorizeSheetState();
}

class _RecategorizeSheetState extends State<RecategorizeSheet> {
  late String _selected = widget.transaction.category;
  bool _applyToPast = true;
  bool _saving = false;

  Future<void> _save() async {
    setState(() => _saving = true);

    final result = await context.read<FinanceProvider>().recategorize(
          widget.transaction.id!,
          category: _selected,
          applyToPast: _applyToPast,
        );

    if (!mounted) return;
    Navigator.pop(context);

    if (result != null && result.affectedCount > 0) {
      final applied = result.appliedToPast;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(applied
              ? '${result.affectedCount} weitere Buchungen wurden mitgeändert.'
              : '${result.affectedCount} frühere Buchungen blieben unverändert.'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final counterparty = widget.transaction.counterparty;

    return Padding(
      padding: EdgeInsets.fromLTRB(
          20, 12, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: KineticTheme.divider,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Text('Kategorie ändern', style: KineticTheme.title),
          const SizedBox(height: 4),
          Text(widget.transaction.title, style: KineticTheme.caption),
          const SizedBox(height: 20),

          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final category in financeCategories)
                _CategoryOption(
                  label: category,
                  selected: category == _selected,
                  onTap: () => setState(() => _selected = category),
                ),
            ],
          ),

          if (counterparty != null && counterparty.isNotEmpty) ...[
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: KineticTheme.surfaceElevated,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.auto_awesome, size: 15, color: KineticTheme.primary),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Künftige Buchungen von "$counterparty" werden automatisch '
                      'als "$_selected" einsortiert.',
                      style: KineticTheme.caption.copyWith(fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _applyToPast,
              onChanged: (value) => setState(() => _applyToPast = value),
              activeThumbColor: KineticTheme.primary,
              title: Text('Auch auf frühere Buchungen anwenden',
                  style: KineticTheme.subtitle.copyWith(fontSize: 14)),
            ),
          ],

          const SizedBox(height: 12),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Speichern'),
          ),
        ],
      ),
    );
  }
}

class _CategoryOption extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _CategoryOption({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final color = FinanceTheme.categoryColor(label);

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: selected ? color.withValues(alpha: 0.18) : KineticTheme.surfaceElevated,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: selected ? color : KineticTheme.divider),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 7,
              height: 7,
              decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(2)),
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: KineticTheme.caption.copyWith(
                color: selected ? KineticTheme.textPrimary : KineticTheme.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
