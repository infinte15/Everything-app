import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/recipe.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/category_filter_bar.dart';
import '../widgets/recipe_card.dart';
import '../widgets/recipe_error_banner.dart';
import '../widgets/recipe_section.dart';
import 'recipe_detail_page.dart';
import 'recipe_editor_page.dart';
import 'recipe_import_page.dart';

/// Das Kochbuch: **alle** Rezepte.
///
/// Die frühere Fassung zeigte nur Favoriten und behauptete damit, ein Rezept
/// ohne Herz gebe es nicht - wer eins anlegte und kein Herz setzte, fand es nie
/// wieder. Favoriten sind jetzt ein Filter neben Kategorie und Sortierung.
class RecipeCookbookTab extends StatelessWidget {
  const RecipeCookbookTab({super.key});

  void _openRecipe(BuildContext context, Recipe recipe) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => RecipeDetailPage(recipeId: recipe.id!),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<RecipeProvider>();
    final list = provider.filtered;

    final counts = <String, int>{};
    for (final recipe in provider.recipes) {
      counts[recipe.category] = (counts[recipe.category] ?? 0) + 1;
    }

    return RefreshIndicator(
      onRefresh: provider.load,
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
                provider.load();
              },
            ),
          _header(context, provider, list.length),
          CategoryFilterBar(
            selected: provider.categoryFilter,
            onSelect: provider.setCategoryFilter,
            counts: counts,
          ),
          const SizedBox(height: 8),
          if (list.isEmpty)
            _empty(context, provider)
          else
            for (final recipe in list) ...[
              RecipeRow(
                recipe: recipe,
                onTap: () => _openRecipe(context, recipe),
                onToggleFavorite: () => provider.toggleFavorite(recipe.id!),
              ),
              const Divider(
                  height: 1, thickness: 1, color: KineticTheme.divider, indent: 20),
            ],
        ],
      ),
    );
  }

  Widget _header(BuildContext context, RecipeProvider provider, int shown) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 12, 12),
      child: Row(
        children: [
          Expanded(
            child: Text(
              shown == provider.recipes.length
                  ? (shown == 1 ? '1 Rezept' : '$shown Rezepte')
                  : '$shown von ${provider.recipes.length}',
              style: KineticTheme.label,
            ),
          ),
          IconButton(
            onPressed: () => provider.setOnlyFavorites(!provider.onlyFavorites),
            icon: Icon(
              provider.onlyFavorites ? Icons.favorite : Icons.favorite_border,
              size: 20,
              color: provider.onlyFavorites
                  ? KineticTheme.primary
                  : KineticTheme.textTertiary,
            ),
            tooltip: 'Nur Favoriten',
          ),
          PopupMenuButton<RecipeSort>(
            initialValue: provider.sort,
            onSelected: provider.setSort,
            color: KineticTheme.surfaceElevated,
            tooltip: 'Sortierung',
            icon: const Icon(Icons.swap_vert,
                size: 20, color: KineticTheme.textTertiary),
            itemBuilder: (context) => [
              for (final sort in RecipeSort.values)
                PopupMenuItem(
                  value: sort,
                  child: Text(
                    sort.label,
                    style: KineticTheme.caption.copyWith(
                      color: sort == provider.sort
                          ? KineticTheme.primary
                          : KineticTheme.textSecondary,
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _empty(BuildContext context, RecipeProvider provider) {
    if (provider.recipes.isNotEmpty) {
      return RecipeEmpty(
        icon: Icons.filter_alt_off_outlined,
        title: 'Nichts in dieser Auswahl',
        message: provider.onlyFavorites
            ? 'Hier steht nur, was ein Herz hat.'
            : 'In dieser Kategorie liegt noch kein Rezept.',
        actionLabel: 'Filter zurücksetzen',
        onAction: () {
          provider.setOnlyFavorites(false);
          if (provider.categoryFilter != null) {
            provider.setCategoryFilter(provider.categoryFilter!);
          }
        },
      );
    }

    return RecipeEmpty(
      icon: Icons.menu_book_outlined,
      title: 'Noch keine Rezepte',
      message: 'Tipp eins ein oder hol dir eins von chefkoch.de.',
      actionLabel: 'Rezept anlegen',
      onAction: () => Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const RecipeEditorPage()),
      ),
      secondaryLabel: 'Von chefkoch importieren',
      onSecondary: () => Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const RecipeImportPage()),
      ),
    );
  }
}
