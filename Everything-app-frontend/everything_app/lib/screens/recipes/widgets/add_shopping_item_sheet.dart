import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../config/recipe_aisles.dart';
import '../../../providers/shopping_list_provider.dart';
import '../../../theme/kinetic_theme.dart';

/// Eine Zeile auf die Einkaufsliste setzen.
///
/// Das Regal darf leer bleiben - dann sortiert der Server selbst ein, und das
/// tut er über eine Stichwortliste besser als jemand, der zwanzig Einträge von
/// Hand einordnen soll.
class AddShoppingItemSheet extends StatefulWidget {
  const AddShoppingItemSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      backgroundColor: KineticTheme.surface,
      isScrollControlled: true,
      builder: (_) => const AddShoppingItemSheet(),
    );
  }

  @override
  State<AddShoppingItemSheet> createState() => _AddShoppingItemSheetState();
}

class _AddShoppingItemSheetState extends State<AddShoppingItemSheet> {
  final _nameController = TextEditingController();
  final _amountController = TextEditingController();
  final _unitController = TextEditingController();
  String? _aisle;
  bool _saving = false;

  @override
  void dispose() {
    _nameController.dispose();
    _amountController.dispose();
    _unitController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) return;

    setState(() => _saving = true);
    final ok = await context.read<ShoppingListProvider>().add(
          name,
          amount: double.tryParse(_amountController.text.trim().replaceAll(',', '.')),
          unit: _unitController.text.trim().isEmpty
              ? null
              : _unitController.text.trim(),
          category: _aisle,
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
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SafeArea(
        // Scrollbar, damit das Sheet bei aufgeklappter Tastatur nicht platzt.
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Auf die Liste', style: KineticTheme.title),
              const SizedBox(height: 20),
              TextField(
                controller: _nameController,
                autofocus: true,
                textCapitalization: TextCapitalization.sentences,
                style:
                    KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
                decoration: const InputDecoration(hintText: 'Was?'),
                onSubmitted: (_) => _save(),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _amountController,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: [
                        FilteringTextInputFormatter.allow(RegExp(r'[\d.,]')),
                      ],
                      style: KineticTheme.subtitle
                          .copyWith(color: KineticTheme.textPrimary),
                      decoration: const InputDecoration(hintText: 'Menge'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: _unitController,
                      style: KineticTheme.subtitle
                          .copyWith(color: KineticTheme.textPrimary),
                      decoration: const InputDecoration(hintText: 'Einheit'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text('REGAL (OPTIONAL)', style: KineticTheme.label),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final aisle in recipeAisles)
                    ChoiceChip(
                      label: Text(aisle),
                      selected: _aisle == aisle,
                      onSelected: (selected) =>
                          setState(() => _aisle = selected ? aisle : null),
                    ),
                ],
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _saving ? null : _save,
                child: Text(_saving ? 'Wird gespeichert…' : 'Hinzufügen'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
