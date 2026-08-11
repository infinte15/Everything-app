import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../models/meal_plan.dart';
import '../../../models/meal_type.dart';
import '../../../providers/recipe_provider.dart';
import '../../../providers/shopping_list_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/plan_meal_sheet.dart';
import '../widgets/recipe_error_banner.dart';
import '../widgets/recipe_format.dart';
import '../widgets/recipe_image.dart';
import 'recipe_detail_page.dart';

/// Der Wochenplan - mit echten Daten.
///
/// Die frühere Fassung war nach englischen Wochentagsnamen aufgebaut
/// ("Monday", "Breakfast") und konnte deshalb nie einen Eintrag finden: der
/// Provider legte seine Einträge unter `yyyy-MM-dd-TYP` ab. Hier steht jetzt
/// eine echte Woche mit Kalenderwoche und Datumsbereich.
class RecipePlanTab extends StatelessWidget {
  const RecipePlanTab({super.key, required this.onOpenShopping});

  /// Ruft den Einkaufs-Reiter auf, nachdem die Zutaten übernommen wurden.
  final VoidCallback onOpenShopping;

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<RecipeProvider>();

    return RefreshIndicator(
      onRefresh: () => provider.loadWeek(provider.weekStart),
      color: KineticTheme.primary,
      backgroundColor: KineticTheme.surfaceElevated,
      child: ListView(
        padding: const EdgeInsets.only(bottom: 96),
        children: [
          if (provider.error != null)
            RecipeErrorBanner(
              message: provider.error!,
              onRetry: () {
                provider.clearError();
                provider.loadWeek(provider.weekStart);
              },
            ),
          _WeekHeader(provider: provider),
          for (var offset = 0; offset < 7; offset++)
            _DaySection(
              day: provider.weekStart.add(Duration(days: offset)),
              provider: provider,
            ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: OutlinedButton.icon(
              onPressed: () => _buildShoppingList(context, provider),
              icon: const Icon(Icons.shopping_basket_outlined, size: 18),
              label: const Text('Zutaten dieser Woche auf die Einkaufsliste'),
              style: OutlinedButton.styleFrom(
                foregroundColor: KineticTheme.primary,
                side: const BorderSide(color: KineticTheme.divider),
                minimumSize: const Size.fromHeight(48),
                shape: const RoundedRectangleBorder(),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _buildShoppingList(
      BuildContext context, RecipeProvider provider) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Theme(
        data: KineticTheme.darkTheme,
        child: AlertDialog(
          backgroundColor: KineticTheme.surfaceElevated,
          title: Text('Zutaten übernehmen?', style: KineticTheme.title),
          // Genau das, was ShoppingListService tatsächlich tut - nicht mehr und
          // nicht weniger.
          content: Text(
            'Ersetzt die noch nicht abgehakten Zeilen aus dem Wochenplan. '
            'Eigene Einträge und Abgehaktes bleiben stehen.',
            style: KineticTheme.caption,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Abbrechen'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Übernehmen'),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true || !context.mounted) return;

    final shopping = context.read<ShoppingListProvider>();
    final count =
        await shopping.rebuildFromWeek(provider.weekStart, provider.weekEnd);

    if (!context.mounted) return;
    if (count >= 0) {
      onOpenShopping();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(shopping.error ?? 'Hat nicht geklappt')),
      );
    }
  }
}

/// ‹ KW 33 · 11.–17. Aug › mit "Woche füllen".
class _WeekHeader extends StatelessWidget {
  const _WeekHeader({required this.provider});

  final RecipeProvider provider;

  /// Kalenderwoche nach ISO 8601 - Woche 1 ist die mit dem ersten Donnerstag.
  static int isoWeek(DateTime date) {
    final thursday =
        date.add(Duration(days: 4 - (date.weekday == 7 ? 7 : date.weekday)));
    final firstDay = DateTime(thursday.year, 1, 1);
    return ((thursday.difference(firstDay).inDays) / 7).floor() + 1;
  }

  bool get _isCurrentWeek =>
      provider.weekStart == RecipeProvider.mondayOf(DateTime.now());

  @override
  Widget build(BuildContext context) {
    final start = provider.weekStart;
    final end = provider.weekEnd;
    final range = '${DateFormat('d.', 'de_DE').format(start)}'
        '–${DateFormat('d. MMM', 'de_DE').format(end)}';

    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 16, 8, 4),
      child: Row(
        children: [
          IconButton(
            onPressed: provider.previousWeek,
            icon: const Icon(Icons.chevron_left, size: 22),
            tooltip: 'Vorige Woche',
          ),
          Expanded(
            child: Column(
              children: [
                Text('KW ${isoWeek(start)}', style: KineticTheme.title),
                const SizedBox(height: 2),
                Text(range, style: KineticTheme.label),
              ],
            ),
          ),
          if (!_isCurrentWeek)
            TextButton(
              onPressed: provider.thisWeek,
              child: Text('Heute',
                  style:
                      KineticTheme.caption.copyWith(color: KineticTheme.primary)),
            ),
          IconButton(
            onPressed: () => _fillWeek(context),
            icon: const Icon(Icons.auto_awesome_outlined, size: 19),
            tooltip: 'Woche füllen',
          ),
          IconButton(
            onPressed: provider.nextWeek,
            icon: const Icon(Icons.chevron_right, size: 22),
            tooltip: 'Nächste Woche',
          ),
        ],
      ),
    );
  }

  Future<void> _fillWeek(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Theme(
        data: KineticTheme.darkTheme,
        child: AlertDialog(
          backgroundColor: KineticTheme.surfaceElevated,
          title: Text('Woche füllen?', style: KineticTheme.title),
          // Der Satz stimmt erst, seit generateWeeklyPlan belegte Plätze
          // überspringt - vorher legte ein zweiter Druck alles doppelt an.
          content: Text(
            'Freie Plätze werden mit passenden Rezepten belegt. '
            'Was schon geplant ist, bleibt stehen.',
            style: KineticTheme.caption,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Abbrechen'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Füllen'),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true || !context.mounted) return;

    final added = await provider.generateWeek();
    if (!context.mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(switch (added) {
        < 0 => provider.error ?? 'Hat nicht geklappt',
        0 => 'Die Woche war schon voll.',
        1 => 'Eine Mahlzeit ergänzt.',
        _ => '$added Mahlzeiten ergänzt.',
      }),
    ));
  }
}

/// Ein Tag mit seinen drei Plätzen.
class _DaySection extends StatelessWidget {
  const _DaySection({required this.day, required this.provider});

  final DateTime day;
  final RecipeProvider provider;

  bool get _isToday {
    final now = DateTime.now();
    return day.year == now.year && day.month == now.month && day.day == now.day;
  }

  @override
  Widget build(BuildContext context) {
    // Snacks bekommen keinen festen Platz, werden aber angezeigt, wenn welche
    // geplant sind.
    final snacks = provider.mealsOn(day, MealType.snack);

    return Container(
      margin: const EdgeInsets.only(top: 16),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(
            // Der einzige Farbakzent des Reiters.
            color: _isToday ? KineticTheme.primary : Colors.transparent,
            width: 2,
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 0, 20, 8),
            child: Text(
              DateFormat('EEEE, d. MMM', 'de_DE').format(day).toUpperCase(),
              style: KineticTheme.label.copyWith(
                color: _isToday ? KineticTheme.primary : KineticTheme.textTertiary,
              ),
            ),
          ),
          for (final type in MealType.slots)
            _slot(context, type, provider.mealsOn(day, type)),
          for (final snack in snacks)
            _MealCard(meal: snack, provider: provider),
        ],
      ),
    );
  }

  Widget _slot(BuildContext context, MealType type, List<MealPlan> meals) {
    if (meals.isEmpty) {
      return _EmptySlot(day: day, mealType: type);
    }
    return Column(
      children: [
        for (final meal in meals) _MealCard(meal: meal, provider: provider),
      ],
    );
  }
}

/// Ein belegter Platz.
class _MealCard extends StatelessWidget {
  const _MealCard({required this.meal, required this.provider});

  final MealPlan meal;
  final RecipeProvider provider;

  @override
  Widget build(BuildContext context) {
    return Dismissible(
      key: ValueKey('meal-${meal.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: KineticTheme.surfaceElevated,
        child: const Icon(Icons.delete_outline,
            size: 20, color: KineticTheme.danger),
      ),
      onDismissed: (_) => provider.deleteMeal(meal.id!),
      child: InkWell(
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => RecipeDetailPage(recipeId: meal.recipeId),
        )),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 6, 12, 6),
          child: Row(
            children: [
              SizedBox(
                width: 62,
                child: Text(meal.mealType.label, style: KineticTheme.label),
              ),
              RecipeImage(
                url: meal.recipeImageUrl,
                name: meal.recipeName ?? '?',
                height: 44,
                width: 44,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      meal.recipeName ?? 'Rezept',
                      style: KineticTheme.subtitle.copyWith(
                        color: meal.isCompleted
                            ? KineticTheme.textTertiary
                            : KineticTheme.textPrimary,
                        decoration:
                            meal.isCompleted ? TextDecoration.lineThrough : null,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      [
                        formatServings(meal.plannedServings),
                        if (meal.notes != null && meal.notes!.isNotEmpty) meal.notes!,
                      ].join(' · '),
                      style: KineticTheme.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed:
                    meal.isCompleted ? null : () => provider.completeMeal(meal.id!),
                icon: Icon(
                  meal.isCompleted
                      ? Icons.check_circle
                      : Icons.check_circle_outline,
                  size: 20,
                  color: meal.isCompleted
                      ? KineticTheme.primary
                      : KineticTheme.textTertiary,
                ),
                tooltip: meal.isCompleted ? 'Gekocht' : 'Als gekocht abhaken',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Ein freier Platz: gepunkteter Rahmen und eine Einladung.
class _EmptySlot extends StatelessWidget {
  const _EmptySlot({required this.day, required this.mealType});

  final DateTime day;
  final MealType mealType;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => PlanMealSheet.show(context, date: day, mealType: mealType),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 6, 20, 6),
        child: Row(
          children: [
            SizedBox(
              width: 62,
              child: Text(mealType.label, style: KineticTheme.label),
            ),
            Expanded(
              child: Container(
                height: 44,
                alignment: Alignment.centerLeft,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                decoration: BoxDecoration(
                  border: Border.all(color: KineticTheme.divider),
                ),
                child: Text(
                  '+ Rezept wählen',
                  style: KineticTheme.caption
                      .copyWith(color: KineticTheme.textTertiary),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
