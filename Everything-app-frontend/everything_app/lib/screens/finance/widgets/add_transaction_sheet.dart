import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../models/finance_transaction.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';

/// Buchung von Hand eintragen.
///
/// Bleibt auch bei angeschlossener Bank sinnvoll: Bargeld taucht auf keinem
/// Kontoauszug auf.
class AddTransactionSheet extends StatefulWidget {
  const AddTransactionSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: KineticTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => const AddTransactionSheet(),
    );
  }

  @override
  State<AddTransactionSheet> createState() => _AddTransactionSheetState();
}

class _AddTransactionSheetState extends State<AddTransactionSheet> {
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

  bool _isExpense = true;
  String _category = 'Sonstiges';
  DateTime _date = DateTime.now();
  bool _saving = false;

  final _descriptionController = TextEditingController();
  final _amountController = TextEditingController();

  @override
  void dispose() {
    _descriptionController.dispose();
    _amountController.dispose();
    super.dispose();
  }

  bool get _isValid =>
      (double.tryParse(_amountController.text.replaceAll(',', '.')) ?? 0) > 0 &&
      _descriptionController.text.trim().isNotEmpty;

  Future<void> _save() async {
    final amount = double.tryParse(_amountController.text.replaceAll(',', '.')) ?? 0;
    if (amount <= 0 || _descriptionController.text.trim().isEmpty) return;

    setState(() => _saving = true);
    final ok = await context.read<FinanceProvider>().addTransaction(
          FinanceTransaction(
            amount: amount,
            type: _isExpense ? 'AUSGABE' : 'EINNAHME',
            category: _isExpense ? _category : 'Einnahmen',
            description: _descriptionController.text.trim(),
            transactionDate: _date,
          ),
        );

    if (!mounted) return;
    if (ok) {
      Navigator.pop(context);
    } else {
      setState(() => _saving = false);
    }
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      builder: (context, child) =>
          Theme(data: KineticTheme.darkTheme, child: child!),
    );
    if (picked != null) setState(() => _date = picked);
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
            Text('Buchung eintragen', style: KineticTheme.title),
            const SizedBox(height: 20),

            Row(
              children: [
                Expanded(
                  child: _TypeChip(
                    label: 'Ausgabe',
                    selected: _isExpense,
                    onTap: () => setState(() => _isExpense = true),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _TypeChip(
                    label: 'Einnahme',
                    selected: !_isExpense,
                    accent: FinanceTheme.income,
                    onTap: () => setState(() => _isExpense = false),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            TextField(
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              style: KineticTheme.figure.copyWith(fontSize: 26),
              textAlign: TextAlign.center,
              decoration: const InputDecoration(hintText: '0,00 €'),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 12),

            TextField(
              controller: _descriptionController,
              style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
              decoration: const InputDecoration(hintText: 'Wofür?'),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 16),

            if (_isExpense) ...[
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
              const SizedBox(height: 16),
            ],

            GestureDetector(
              onTap: _pickDate,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                decoration: BoxDecoration(
                  color: KineticTheme.surfaceElevated,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.calendar_today,
                        size: 16, color: KineticTheme.textSecondary),
                    const SizedBox(width: 12),
                    Text(
                      DateFormat('EEEE, d. MMMM yyyy', 'de_DE').format(_date),
                      style: KineticTheme.subtitle,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            FilledButton(
              onPressed: (!_isValid || _saving) ? null : _save,
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
      ),
    );
  }
}

class _TypeChip extends StatelessWidget {
  final String label;
  final bool selected;
  final Color? accent;
  final VoidCallback onTap;

  const _TypeChip({
    required this.label,
    required this.selected,
    required this.onTap,
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final color = accent ?? KineticTheme.textPrimary;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? KineticTheme.surfaceHighlight : KineticTheme.surfaceElevated,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected ? color.withValues(alpha: 0.6) : Colors.transparent,
          ),
        ),
        child: Text(
          label,
          style: KineticTheme.caption.copyWith(
            color: selected ? color : KineticTheme.textTertiary,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
          ),
        ),
      ),
    );
  }
}
