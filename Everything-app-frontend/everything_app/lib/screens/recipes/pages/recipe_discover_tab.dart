import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/recipe.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/category_filter_bar.dart';
import '../widgets/recipe_card.dart';
import '../widgets/recipe_error_banner.dart';
import '../widgets/recipe_quick_actions.dart';
import '../widgets/recipe_section.dart';
import 'recipe_detail_page.dart';

/// Chefkochs Startseite, entfärbt: Suche, Kategorien und darunter die Reihen.
///
/// Die vier Reihen kommen aus Endpunkten, die es längst gibt und die bisher
/// niemand aufgerufen hat. `/recently-cooked` ist bewusst keine fünfte Reihe,
/// sondern eine Sortierung im Kochbuch - fünf Karussells untereinander sind
/// eine Halde und keine Startseite.
class RecipeDiscoverTab extends StatefulWidget {
  const RecipeDiscoverTab({super.key, required this.onOpenCookbook});

  /// Ein Kategorie-Chip führt ins Kochbuch, so wie bei Chefkoch eine Kategorie
  /// in eine Liste führt.
  final VoidCallback onOpenCookbook;

  @override
  State<RecipeDiscoverTab> createState() => _RecipeDiscoverTabState();
}

class _RecipeDiscoverTabState extends State<RecipeDiscoverTab> {
  final _searchController = TextEditingController();
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  /// 300 ms Entprellung: ohne sie schickt "Kürbissuppe" elf Suchanfragen los.
  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (mounted) context.read<RecipeProvider>().search(value);
    });
  }

  void _openRecipe(Recipe recipe) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => RecipeDetailPage(recipeId: recipe.id!),
    ));
  }

  void _onCategory(String category) {
    context.read<RecipeProvider>().setCategoryFilter(category);
    widget.onOpenCookbook();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<RecipeProvider>();

    return RefreshIndicator(
      onRefresh: provider.load,
      color: KineticTheme.primary,
      backgroundColor: KineticTheme.surfaceElevated,
      child: ListView(
        padding: const EdgeInsets.only(bottom: 96),
        children: [
          const SizedBox(height: 8),
          _searchField(provider),
          if (provider.error != null)
            RecipeErrorBanner(
              message: provider.error!,
              onRetry: () {
                provider.clearError();
                provider.load();
              },
            ),
          if (provider.searchQuery.trim().isNotEmpty)
            ..._searchResults(provider)
          else
            ..._discover(provider),
        ],
      ),
    );
  }

  Widget _searchField(RecipeProvider provider) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: TextField(
        controller: _searchController,
        onChanged: _onSearchChanged,
        textInputAction: TextInputAction.search,
        style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
        decoration: InputDecoration(
          hintText: 'Rezepte, Zutaten suchen',
          prefixIcon:
              const Icon(Icons.search, size: 20, color: KineticTheme.textTertiary),
          suffixIcon: provider.searchQuery.isEmpty
              ? null
              : IconButton(
                  icon: const Icon(Icons.close,
                      size: 18, color: KineticTheme.textTertiary),
                  onPressed: () {
                    _searchController.clear();
                    provider.clearSearch();
                  },
                ),
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(vertical: 14),
        ),
      ),
    );
  }

  // ── Suchergebnis ───────────────────────────────────────────────────────────

  List<Widget> _searchResults(RecipeProvider provider) {
    if (provider.isSearching) {
      return const [
        Padding(
          padding: EdgeInsets.all(48),
          child: Center(
            child: SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(
                  strokeWidth: 2, color: KineticTheme.primary),
            ),
          ),
        ),
      ];
    }

    if (provider.searchResults.isEmpty) {
      return [
        RecipeEmpty(
          icon: Icons.search_off,
          title: 'Nichts gefunden',
          message: 'Kein Rezept enthält "${provider.searchQuery.trim()}" '
              'im Namen, in den Tags oder in den Zutaten.',
        ),
      ];
    }

    return [
      RecipeSection(
        title: provider.searchResults.length == 1
            ? '1 Treffer'
            : '${provider.searchResults.length} Treffer',
      ),
      for (final recipe in provider.searchResults)
        RecipeRow(
          recipe: recipe,
          onTap: () => _openRecipe(recipe),
          onToggleFavorite: () => provider.toggleFavorite(recipe.id!),
          // Suchtreffer sind genau die Stelle, an der man findet, was man
          // Donnerstag kochen will.
          onPlan: () => planRecipeFlow(context, recipe: recipe),
          onAddToShoppingList: () =>
              addToShoppingListFlow(context, recipeId: recipe.id!),
        ),
    ];
  }

  // ── Entdecken ──────────────────────────────────────────────────────────────

  List<Widget> _discover(RecipeProvider provider) {
    if (provider.recipes.isEmpty && !provider.isLoading) {
      return [
        RecipeEmpty(
          icon: Icons.restaurant_outlined,
          title: 'Noch keine Rezepte',
          message: 'Leg ein Rezept an oder importier eins - '
              'über den Knopf unten rechts.',
        ),
      ];
    }

    final counts = <String, int>{};
    for (final recipe in provider.recipes) {
      counts[recipe.category] = (counts[recipe.category] ?? 0) + 1;
    }

    return [
      const RecipeSection(title: 'Kategorien'),
      CategoryFilterBar(
        selected: provider.categoryFilter,
        onSelect: _onCategory,
        counts: counts,
      ),
      _carousel(provider, 'In 30 Minuten fertig', provider.quick),
      _carousel(provider, 'Deine besten', provider.bestRated),
      _carousel(provider, 'Lange nicht gekocht', provider.notCookedLately),
      _carousel(provider, 'Noch nie ausprobiert', provider.neverCooked),
    ];
  }

  /// Eine leere Reihe wird gar nicht gezeichnet - eine Überschrift über einem
  /// leeren Streifen sieht aus wie ein Ladefehler.
  Widget _carousel(RecipeProvider provider, String title, List<Recipe> recipes) {
    if (recipes.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        RecipeSection(title: title),
        SizedBox(
          height: 200,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 20),
            itemCount: recipes.length,
            separatorBuilder: (_, _) => const SizedBox(width: 12),
            itemBuilder: (context, index) => RecipeTile(
              recipe: recipes[index],
              onTap: () => _openRecipe(recipes[index]),
            ),
          ),
        ),
      ],
    );
  }
}
