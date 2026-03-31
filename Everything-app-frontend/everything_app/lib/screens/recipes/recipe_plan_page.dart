import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/recipe_provider.dart';
import '../../config/app_theme.dart';

class RecipePlanPage extends StatelessWidget {
  const RecipePlanPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<RecipeProvider>();

    final days = [
      'Montag',
      'Dienstag',
      'Mittwoch',
      'Donnerstag',
      'Freitag',
      'Samstag',
      'Sonntag'
    ];
    final daysEn = [
      'Monday',
      'Tuesday',
      'Wednesday',
      'Thursday',
      'Friday',
      'Saturday',
      'Sunday'
    ];

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        children: [
          Row(
            children: [
              const Text('📅', style: TextStyle(fontSize: 28)),
              const SizedBox(width: 12),
              const Expanded(
                child: Text('Wochenplan',
                    style:
                        TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
              ),
              IconButton(
                icon: const Icon(Icons.more_horiz),
                onPressed: () {},
              ),
            ],
          ),
          const SizedBox(height: 24),
          for (int i = 0; i < 7; i++)
            _DayPlanCard(
              dayName: days[i],
              dayEn: daysEn[i],
              provider: provider,
            ),
        ],
      ),
    );
  }
}

class _DayPlanCard extends StatelessWidget {
  final String dayName;
  final String dayEn;
  final RecipeProvider provider;

  const _DayPlanCard({
    required this.dayName,
    required this.dayEn,
    required this.provider,
  });

  @override
  Widget build(BuildContext context) {
    // Get recipes for meals
    final breakfast = provider.getRecipesForMeal(dayEn, 'Breakfast');
    final lunch = provider.getRecipesForMeal(dayEn, 'Lunch');
    final dinner = provider.getRecipesForMeal(dayEn, 'Dinner');

    final bool isEmpty =
        breakfast.isEmpty && lunch.isEmpty && dinner.isEmpty;

    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ExpansionTile(
        title: Text(dayName,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: isEmpty
            ? const Text('Keine Mahlzeiten geplant',
                style: TextStyle(color: Colors.grey, fontSize: 12))
            : Text(
                '${breakfast.length + lunch.length + dinner.length} Mahlzeiten',
                style: TextStyle(
                    color: AppTheme.recipesColor,
                    fontSize: 12,
                    fontWeight: FontWeight.w600)),
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _MealSection(
                    title: 'Frühstück',
                    recipes: breakfast,
                    onAdd: () {}),
                const Divider(),
                _MealSection(
                    title: 'Mittagessen', recipes: lunch, onAdd: () {}),
                const Divider(),
                _MealSection(
                    title: 'Abendessen', recipes: dinner, onAdd: () {}),
              ],
            ),
          )
        ],
      ),
    );
  }
}

class _MealSection extends StatelessWidget {
  final String title;
  final List recipes;
  final VoidCallback onAdd;

  const _MealSection({
    required this.title,
    required this.recipes,
    required this.onAdd,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(title,
                style: const TextStyle(
                    fontSize: 16, fontWeight: FontWeight.bold)),
            IconButton(
              icon: const Icon(Icons.add_circle_outline,
                  color: AppTheme.recipesColor),
              onPressed: onAdd,
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (recipes.isEmpty)
          const Text('Nichts geplant',
              style: TextStyle(color: Colors.grey, fontStyle: FontStyle.italic))
        else
          ...recipes.map((r) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    image: r.imageUrl != null
                        ? DecorationImage(
                            image: NetworkImage(r.imageUrl!),
                            fit: BoxFit.cover)
                        : null,
                    color: AppTheme.recipesColor.withValues(alpha: 0.2),
                  ),
                  child: r.imageUrl == null
                      ? const Icon(Icons.restaurant,
                          size: 16, color: AppTheme.recipesColor)
                      : null,
                ),
                title: Text(r.name, style: const TextStyle(fontSize: 14)),
                trailing: const Icon(Icons.chevron_right, size: 16),
              )),
        const SizedBox(height: 8),
      ],
    );
  }
}
