import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../models/meal_type.dart';
import '../../../models/recipe.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import 'recipe_picker_sheet.dart';
import 'servings_stepper.dart';

/// Eine Mahlzeit einplanen: Rezept, Tag, Platz, Portionen.
class PlanMealSheet extends StatefulWidget {
  const PlanMealSheet({
    super.key,
    required this.date,
    this.mealType,
    this.recipe,
    this.servings,
  });

  final DateTime date;
  final MealType? mealType;
  final Recipe? recipe;

  /// Portionen, mit denen das Sheet aufgeht. Ohne Angabe die Grundmenge des
  /// Rezepts. Die Rezept-Detailseite gibt hier ihren Stepper-Wert mit - vorher
  /// ging "auf 6 Portionen einstellen und einplanen" still verloren.
  final int? servings;

  static Future<bool?> show(
    BuildContext context, {
    required DateTime date,
    MealType? mealType,
    Recipe? recipe,
    int? servings,
  }) {
    return showModalBottomSheet<bool>(
      context: context,
      backgroundColor: KineticTheme.surface,
      isScrollControlled: true,
      builder: (_) => PlanMealSheet(
        date: date,
        mealType: mealType,
        recipe: recipe,
        servings: servings,
      ),
    );
  }

  @override
  State<PlanMealSheet> createState() => _PlanMealSheetState();
}

class _PlanMealSheetState extends State<PlanMealSheet> {
  late DateTime _date = widget.date;
  late MealType _mealType = widget.mealType ?? MealType.abendessen;
  late Recipe? _recipe = widget.recipe;
  late int _servings = widget.servings ?? widget.recipe?.servings ?? 2;
  bool _scheduleCooking = false;
  bool _saving = false;

  Future<void> _pickRecipe() async {
    final chosen = await RecipePickerSheet.show(context, suitableFor: _mealType);
    if (chosen != null && mounted) {
      setState(() {
        _recipe = chosen;
        _servings = chosen.servings;
      });
    }
  }

  Future<void> _pickDate() async {
    final chosen = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      builder: (context, child) =>
          Theme(data: KineticTheme.darkTheme, child: child!),
    );
    if (chosen != null && mounted) setState(() => _date = chosen);
  }

  Future<void> _save() async {
    if (_recipe?.id == null) return;
    setState(() => _saving = true);

    final ok = await context.read<RecipeProvider>().planMeal(
          recipeId: _recipe!.id!,
          date: _date,
          mealType: _mealType,
          servings: _servings,
          scheduleCooking: _scheduleCooking,
        );

    if (!mounted) return;
    if (ok) {
      Navigator.pop(context, true);
    } else {
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: SafeArea(
        // Scrollbar, damit das Sheet bei aufgeklappter Tastatur nicht platzt.
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Mahlzeit planen', style: KineticTheme.title),
              const SizedBox(height: 20),

              _row(
                'Rezept',
                _recipe?.name ?? 'Auswählen',
                onTap: _pickRecipe,
                isPlaceholder: _recipe == null,
              ),
              const Divider(height: 24, color: KineticTheme.divider),

              _row('Tag', DateFormat('EEEE, d. MMMM', 'de_DE').format(_date),
                  onTap: _pickDate),
              const Divider(height: 24, color: KineticTheme.divider),

              Text('MAHLZEIT', style: KineticTheme.label),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                children: [
                  for (final type in MealType.values)
                    ChoiceChip(
                      label: Text(type.label),
                      selected: _mealType == type,
                      onSelected: (_) => setState(() => _mealType = type),
                    ),
                ],
              ),
              const Divider(height: 24, color: KineticTheme.divider),

              Row(
                children: [
                  Expanded(child: Text('Portionen', style: KineticTheme.subtitle)),
                  ServingsStepper(
                    value: _servings,
                    onChanged: (value) => setState(() => _servings = value),
                  ),
                ],
              ),
              const Divider(height: 24, color: KineticTheme.divider),

              SwitchListTile(
                value: _scheduleCooking,
                onChanged: (value) => setState(() => _scheduleCooking = value),
                contentPadding: EdgeInsets.zero,
                activeThumbColor: KineticTheme.primary,
                title: Text('Kochzeit einplanen', style: KineticTheme.subtitle),
                subtitle: Text(
                  'Legt eine Aufgabe an, die der Planer in den Kalender legt.',
                  style: KineticTheme.label,
                ),
              ),
              const SizedBox(height: 16),

              FilledButton(
                onPressed: _recipe == null || _saving ? null : _save,
                child: Text(_saving ? 'Wird geplant…' : 'Einplanen'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _row(String label, String value,
      {required VoidCallback onTap, bool isPlaceholder = false}) {
    return InkWell(
      onTap: onTap,
      child: Row(
        children: [
          Expanded(child: Text(label, style: KineticTheme.subtitle)),
          Flexible(
            child: Text(
              value,
              style: KineticTheme.caption.copyWith(
                color: isPlaceholder
                    ? KineticTheme.primary
                    : KineticTheme.textPrimary,
              ),
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.end,
            ),
          ),
          const Icon(Icons.chevron_right,
              size: 18, color: KineticTheme.textTertiary),
        ],
      ),
    );
  }
}
