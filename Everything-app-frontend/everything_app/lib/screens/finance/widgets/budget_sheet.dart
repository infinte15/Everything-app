import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/budget_category.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';

/// Budget anlegen oder ändern.
///
/// Der Name ist zugleich der Schlüssel: das Backend rechnet den Verbrauch gegen
/// die Buchungen derselben Kategorie. Deshalb eine Auswahl statt eines
/// Freitextfelds - ein getipptes "Lebensmitel" ergäbe ein Budget, das nie etwas
/// verbraucht.
class BudgetSheet extends StatefulWidget {
  final BudgetCategory? existing;

  const BudgetSheet({super.key, this.existing});

  static Future<void> show(BuildContext context, {BudgetCategory? existing}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: KineticTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => BudgetSheet(existing: existing),
    );
  }

  @override
  State<BudgetSheet> createState() => _BudgetSheetState();
}

class _BudgetSheetState extends State<BudgetSheet> {
  static const _categories = [
    'Lebensmittel',
    'Wohnen',
    'Mobilität',
    'Gesundheit',
    'Freizeit',
    'Unterhaltung',
    'Shopping',
    'Restaurant',
    'Sonstiges',
  ];

  late String _category = widget.existing?.name ?? 'Lebensmittel';
  late final _limitController = TextEditingController(
    text: widget.existing == null
        ? ''
        : widget.existing!.limitAmount.toStringAsFixed(0),
  );
  bool _saving = false;

  @override
  void dispose() {
    _limitController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final limit = double.tryParse(_limitController.text.replaceAll(',', '.')) ?? 0;
    if (limit <= 0) return;

    setState(() => _saving = true);
    final ok = await context.read<FinanceProvider>().saveBudget(
          BudgetCategory(
            id: widget.existing?.id,
            name: _category,
            limitAmount: limit,
            period: 'MONTHLY',
          ),
        );

    if (!mounted) return;
    if (ok) {
      Navigator.pop(context);
    } else {
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
          20, 12, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
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
            Text(
              widget.existing == null ? 'Budget anlegen' : 'Budget ändern',
              style: KineticTheme.title,
            ),
            const SizedBox(height: 20),

            if (widget.existing == null) ...[
              Text('KATEGORIE', style: KineticTheme.label),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final category in _categories)
                    GestureDetector(
                      onTap: () => setState(() => _category = category),
                      child: Container(
                        padding:
                            const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: _category == category
                              ? FinanceTheme.categoryColor(category)
                                  .withValues(alpha: 0.18)
                              : KineticTheme.surfaceElevated,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: _category == category
                                ? FinanceTheme.categoryColor(category)
                                : KineticTheme.divider,
                          ),
                        ),
                        child: Text(category, style: KineticTheme.caption),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 20),
            ],

            Text('MONATLICHE GRENZE', style: KineticTheme.label),
            const SizedBox(height: 10),
            TextField(
              controller: _limitController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              style: KineticTheme.figure.copyWith(fontSize: 26),
              textAlign: TextAlign.center,
              decoration: const InputDecoration(hintText: '0 €'),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 20),

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
            if (widget.existing?.id != null) ...[
              const SizedBox(height: 8),
              TextButton(
                onPressed: () async {
                  final ok = await context
                      .read<FinanceProvider>()
                      .deleteBudget(widget.existing!.id!);
                  if (ok && context.mounted) Navigator.pop(context);
                },
                child: Text(
                  'Budget löschen',
                  style: KineticTheme.caption.copyWith(color: FinanceTheme.shortfall),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
