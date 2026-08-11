import 'package:flutter/material.dart';

import '../../../config/recipe_categories.dart';
import '../../../theme/kinetic_theme.dart';

/// Die Kategorienleiste.
///
/// Bei Chefkoch führt eine Kategorie in eine Liste - genau das tut sie hier
/// auch. Die frühere Fassung zeigte sechs erfundene Kategorien ("Pasta",
/// "Schnell", "Fisch") ganz ohne `onTap`; sie waren Bilder von Knöpfen.
///
/// Farbe bekommt eine Kategorie nicht, nur ein Symbol - siehe
/// `config/recipe_categories.dart`.
class CategoryFilterBar extends StatelessWidget {
  const CategoryFilterBar({
    super.key,
    required this.selected,
    required this.onSelect,
    this.counts,
  });

  final String? selected;
  final ValueChanged<String> onSelect;

  /// Wie viele Rezepte je Kategorie. Kategorien ohne Rezept werden ausgeblendet -
  /// eine leere Kategorie anzutippen führt in eine leere Liste, und das ist ein
  /// Sackgassen-Knopf.
  final Map<String, int>? counts;

  @override
  Widget build(BuildContext context) {
    final visible = counts == null
        ? recipeCategories
        : recipeCategories.where((c) => (counts![c] ?? 0) > 0).toList();

    if (visible.isEmpty) return const SizedBox.shrink();

    return SizedBox(
      height: 40,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        itemCount: visible.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final category = visible[index];
          final isSelected = category == selected;

          return GestureDetector(
            onTap: () => onSelect(category),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: isSelected
                    ? KineticTheme.primary.withValues(alpha: 0.2)
                    : KineticTheme.surfaceElevated,
                border: Border.all(
                  color: isSelected ? KineticTheme.primary : KineticTheme.divider,
                ),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Row(
                children: [
                  Icon(
                    recipeCategoryIcon(category),
                    size: 15,
                    color: isSelected
                        ? KineticTheme.primary
                        : KineticTheme.textSecondary,
                  ),
                  const SizedBox(width: 7),
                  Text(
                    category,
                    style: KineticTheme.caption.copyWith(
                      color: isSelected
                          ? KineticTheme.primary
                          : KineticTheme.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
