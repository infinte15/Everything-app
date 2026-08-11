import 'package:flutter/material.dart';

import '../../../models/recipe.dart';
import '../../../theme/kinetic_theme.dart';
import 'rating_stars.dart';
import 'recipe_format.dart';
import 'recipe_image.dart';

/// Ein Rezept als breite Kachel - für die Karussells auf "Entdecken".
///
/// Bild oben, darunter Name und eine Zeile aus Kategorie, Zeit und Sternen. Wie
/// bei Chefkoch, nur ohne Rahmen, ohne Schatten und ohne Rundung: das Foto ist
/// das einzig Bunte, alles andere ist Fläche und Schrift.
class RecipeTile extends StatelessWidget {
  const RecipeTile({
    super.key,
    required this.recipe,
    required this.onTap,
    this.width = 168,
    this.onToggleFavorite,
  });

  final Recipe recipe;
  final VoidCallback onTap;
  final double width;
  final VoidCallback? onToggleFavorite;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: SizedBox(
        width: width,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                RecipeImage(
                  url: recipe.imageUrl,
                  name: recipe.name,
                  height: width * 0.68,
                  width: width,
                ),
                if (recipe.isFavorite)
                  const Positioned(
                    top: 6,
                    right: 6,
                    child: Icon(Icons.favorite,
                        size: 15, color: KineticTheme.primary),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              recipe.name,
              style: KineticTheme.title.copyWith(fontSize: 14),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Text(
                  formatDuration(recipe.totalTimeMinutes),
                  style: KineticTheme.label,
                ),
                if (recipe.rating != null) ...[
                  const SizedBox(width: 8),
                  RatingStars(rating: recipe.rating, size: 11),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Ein Rezept als Zeile - für das Kochbuch und die Suchtreffer.
///
/// 72er-Bild links, Text in der Mitte, Herz rechts. Die Trennlinie zieht die
/// Liste, nicht die Zeile.
class RecipeRow extends StatelessWidget {
  const RecipeRow({
    super.key,
    required this.recipe,
    required this.onTap,
    this.onToggleFavorite,
  });

  final Recipe recipe;
  final VoidCallback onTap;
  final VoidCallback? onToggleFavorite;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        child: Row(
          children: [
            RecipeImage(
              url: recipe.imageUrl,
              name: recipe.name,
              height: 72,
              width: 72,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    recipe.name,
                    style: KineticTheme.title.copyWith(fontSize: 15),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${recipe.category} · ${formatDuration(recipe.totalTimeMinutes)}',
                    style: KineticTheme.label,
                  ),
                  if (recipe.rating != null || recipe.cookCount > 0) ...[
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        if (recipe.rating != null)
                          RatingStars(rating: recipe.rating, size: 12),
                        if (recipe.rating != null && recipe.cookCount > 0)
                          const SizedBox(width: 8),
                        if (recipe.cookCount > 0)
                          Text(formatCookCount(recipe.cookCount),
                              style: KineticTheme.label),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            if (onToggleFavorite != null)
              IconButton(
                onPressed: onToggleFavorite,
                icon: Icon(
                  recipe.isFavorite ? Icons.favorite : Icons.favorite_border,
                  size: 20,
                  color: recipe.isFavorite
                      ? KineticTheme.primary
                      : KineticTheme.textTertiary,
                ),
                tooltip: recipe.isFavorite
                    ? 'Aus dem Kochbuch nehmen'
                    : 'Ins Kochbuch',
              ),
          ],
        ),
      ),
    );
  }
}
