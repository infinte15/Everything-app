import 'package:flutter/material.dart';

import '../../../models/recipe.dart';
import '../../../theme/kinetic_theme.dart';
import 'recipe_format.dart';

/// Die Zutatentabelle - Chefkochs zweispaltige Liste, ohne Rahmen und ohne
/// Zebrastreifen.
///
/// Die Mengenspalte ist fest breit und rechtsbündig, und sie steht in
/// [KineticTheme.amount] mit tabellarischen Ziffern: beim Umstellen der
/// Portionen ändern sich die Zahlen, aber nichts springt in der Breite.
class IngredientTable extends StatelessWidget {
  const IngredientTable({super.key, required this.ingredients});

  final List<RecipeIngredient> ingredients;

  @override
  Widget build(BuildContext context) {
    if (ingredients.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        child: Text('Keine Zutaten hinterlegt.', style: KineticTheme.caption),
      );
    }

    return Column(
      children: [
        for (var i = 0; i < ingredients.length; i++) ...[
          _row(ingredients[i]),
          if (i < ingredients.length - 1)
            const Divider(
                height: 1, thickness: 1, color: KineticTheme.divider, indent: 20),
        ],
      ],
    );
  }

  Widget _row(RecipeIngredient ingredient) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 11),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 84,
            child: Text(
              formatAmountWithUnit(ingredient.amount, ingredient.unit),
              textAlign: TextAlign.right,
              style: KineticTheme.amount,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  ingredient.name,
                  style: KineticTheme.subtitle
                      .copyWith(color: KineticTheme.textPrimary),
                ),
                if (ingredient.note != null && ingredient.note!.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(ingredient.note!, style: KineticTheme.label),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
