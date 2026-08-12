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
  });

  final Recipe recipe;
  final VoidCallback onTap;
  final double width;

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
///
/// [onPlan] und [onAddToShoppingList] hängen ein "⋮"-Menü rechts an. **Benannt
/// und nicht ein generisches `trailing`:** so stehen Beschriftung und Symbol
/// einmal hier, statt dass jeder Reiter sein eigenes Menü mit eigenen Worten
/// baut.
class RecipeRow extends StatelessWidget {
  const RecipeRow({
    super.key,
    required this.recipe,
    required this.onTap,
    this.onToggleFavorite,
    this.onPlan,
    this.onAddToShoppingList,
  });

  final Recipe recipe;
  final VoidCallback onTap;
  final VoidCallback? onToggleFavorite;

  /// "Einplanen…" - öffnet das Sheet für den Wochenplan.
  final VoidCallback? onPlan;

  /// "Auf die Einkaufsliste" - übernimmt die Zutaten.
  final VoidCallback? onAddToShoppingList;

  @override
  Widget build(BuildContext context) {
    final hasMenu = onPlan != null || onAddToShoppingList != null;

    return InkWell(
      onTap: onTap,
      child: Padding(
        // Rechts enger, wenn das Menü da ist: zwei Knöpfe à 48 px neben einem
        // 72er-Bild sind auf einem schmalen Gerät zu viel.
        padding: EdgeInsets.fromLTRB(20, 8, hasMenu ? 4 : 20, 8),
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
            if (hasMenu)
              PopupMenuButton<int>(
                icon: const Icon(Icons.more_vert,
                    size: 20, color: KineticTheme.textTertiary),
                color: KineticTheme.surfaceElevated,
                tooltip: 'Mehr',
                onSelected: (value) {
                  if (value == 0) onPlan?.call();
                  if (value == 1) onAddToShoppingList?.call();
                },
                itemBuilder: (_) => [
                  if (onPlan != null)
                    PopupMenuItem(
                      value: 0,
                      child: Row(
                        children: [
                          const Icon(Icons.calendar_view_week_outlined,
                              size: 18, color: KineticTheme.textSecondary),
                          const SizedBox(width: 12),
                          Flexible(
                            child: Text('Einplanen…',
                                style: KineticTheme.subtitle),
                          ),
                        ],
                      ),
                    ),
                  if (onAddToShoppingList != null)
                    PopupMenuItem(
                      value: 1,
                      child: Row(
                        children: [
                          const Icon(Icons.shopping_basket_outlined,
                              size: 18, color: KineticTheme.textSecondary),
                          const SizedBox(width: 12),
                          Flexible(
                            child: Text('Auf die Einkaufsliste',
                                style: KineticTheme.subtitle),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}
