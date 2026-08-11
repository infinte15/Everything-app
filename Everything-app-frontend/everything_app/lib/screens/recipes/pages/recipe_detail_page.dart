import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../config/recipe_categories.dart';
import '../../../models/recipe.dart';
import '../../../providers/recipe_provider.dart';
import '../../../providers/shopping_list_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/cooked_sheet.dart';
import '../widgets/ingredient_table.dart';
import '../widgets/plan_meal_sheet.dart';
import '../widgets/rating_stars.dart';
import '../widgets/recipe_format.dart';
import '../widgets/recipe_image.dart';
import '../widgets/recipe_section.dart';
import '../widgets/servings_stepper.dart';
import 'recipe_editor_page.dart';

/// Die Rezeptseite - Chefkochs Aufbau in Kinetic Mono.
///
/// Nimmt eine **Id** und kein fertiges [Recipe]: die Seite muss ein Rezept auch
/// öffnen können, wenn es nicht in der geladenen Liste steht - aus dem
/// Wochenplan etwa, oder später aus einem "Kochen: X"-Termin im Kalender.
class RecipeDetailPage extends StatefulWidget {
  const RecipeDetailPage({super.key, required this.recipeId});

  final int recipeId;

  @override
  State<RecipeDetailPage> createState() => _RecipeDetailPageState();
}

class _RecipeDetailPageState extends State<RecipeDetailPage> {
  Recipe? _recipe;
  int? _servings;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    final provider = context.read<RecipeProvider>();

    // Was schon geladen ist, sofort zeigen - die Seite darf nicht weiß
    // aufblitzen, nur weil das Rezept auch vom Server bestätigt wird.
    final known = provider.byId(widget.recipeId);
    if (known != null && mounted) {
      setState(() {
        _recipe = known;
        _servings ??= known.servings;
        _loading = false;
      });
    }

    try {
      final fresh = await provider.fetchById(widget.recipeId);
      if (!mounted) return;
      setState(() {
        _recipe = fresh;
        _servings ??= fresh.servings;
        _error = null;
        _loading = false;
      });
      provider.loadCookLog(widget.recipeId);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  // ── Aktionen ───────────────────────────────────────────────────────────────

  Future<void> _rate(int stars) async {
    final provider = context.read<RecipeProvider>();
    setState(() => _recipe = _recipe!.copyWith(rating: stars));
    await provider.rate(widget.recipeId, stars);
    final updated = provider.byId(widget.recipeId);
    if (updated != null && mounted) setState(() => _recipe = updated);
  }

  Future<void> _toggleFavorite() async {
    final provider = context.read<RecipeProvider>();
    setState(() => _recipe = _recipe!.copyWith(isFavorite: !_recipe!.isFavorite));
    await provider.toggleFavorite(widget.recipeId);
    final updated = provider.byId(widget.recipeId);
    if (updated != null && mounted) setState(() => _recipe = updated);
  }

  Future<void> _logCooked() async {
    final entry = await CookedSheet.show(
      context,
      servings: _servings ?? _recipe!.servings,
      rating: _recipe!.rating,
    );
    if (entry == null || !mounted) return;

    final updated = await context.read<RecipeProvider>().logCooked(
          widget.recipeId,
          servings: entry.servings,
          rating: entry.rating,
          note: entry.note,
        );

    if (!mounted) return;
    if (updated != null) {
      setState(() => _recipe = updated);
      context.read<RecipeProvider>().loadCookLog(widget.recipeId);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Eingetragen · ${formatCookCount(updated.cookCount)}')),
      );
    }
  }

  Future<void> _addToShoppingList() async {
    final scaled = _recipe!.scaledTo(_servings ?? _recipe!.servings);
    final count =
        await context.read<ShoppingListProvider>().addIngredients(scaled);

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(count < 0
          ? 'Hat nicht geklappt'
          : count == 1
              ? '1 Zutat hinzugefügt'
              : '$count Zutaten hinzugefügt'),
    ));
  }

  Future<void> _openSource() async {
    final url = Uri.tryParse(_recipe!.sourceUrl!);
    if (url == null) return;
    // Extern und nicht eingebettet: eine fremde Seite gehört in den Browser,
    // wo der Nutzer sieht, wo er ist.
    await launchUrl(url, mode: LaunchMode.externalApplication);
  }

  Future<void> _edit() async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => RecipeEditorPage(initial: _recipe)),
    );
    if (changed == true) _load();
  }

  Future<void> _delete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Theme(
        data: KineticTheme.darkTheme,
        child: AlertDialog(
          backgroundColor: KineticTheme.surfaceElevated,
          title: Text('Rezept löschen?', style: KineticTheme.title),
          content: Text(
            '"${_recipe!.name}" wird gelöscht, zusammen mit seinen '
            'Wochenplan-Einträgen und dem Kochverlauf.',
            style: KineticTheme.caption,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Abbrechen'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text('Löschen',
                  style: TextStyle(color: KineticTheme.danger)),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true || !mounted) return;
    final ok = await context.read<RecipeProvider>().delete(widget.recipeId);
    if (ok && mounted) Navigator.of(context).pop();
  }

  // ── Aufbau ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final recipe = _recipe;

    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        body: recipe == null
            ? _placeholderBody()
            : CustomScrollView(
                slivers: [
                  _appBar(recipe),
                  SliverToBoxAdapter(child: _head(recipe)),
                  SliverToBoxAdapter(child: _ingredients(recipe)),
                  SliverToBoxAdapter(child: _steps(recipe)),
                  SliverToBoxAdapter(child: _nutrition(recipe)),
                  SliverToBoxAdapter(child: _notes(recipe)),
                  SliverToBoxAdapter(child: _history(recipe)),
                  SliverToBoxAdapter(child: _source(recipe)),
                  const SliverToBoxAdapter(child: SizedBox(height: 100)),
                ],
              ),
        bottomNavigationBar: recipe == null
            ? null
            : SafeArea(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
                  child: FilledButton.icon(
                    onPressed: _logCooked,
                    icon: const Icon(Icons.restaurant, size: 18),
                    label: const Text('Gekocht'),
                  ),
                ),
              ),
      ),
    );
  }

  Widget _placeholderBody() {
    if (_loading) {
      return const Center(
        child: SizedBox(
          width: 22,
          height: 22,
          child:
              CircularProgressIndicator(strokeWidth: 2, color: KineticTheme.primary),
        ),
      );
    }
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined,
                size: 40, color: KineticTheme.textTertiary),
            const SizedBox(height: 16),
            Text(_error ?? 'Rezept nicht gefunden',
                style: KineticTheme.caption, textAlign: TextAlign.center),
            const SizedBox(height: 20),
            TextButton(onPressed: _load, child: const Text('Wiederholen')),
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Zurück'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _appBar(Recipe recipe) {
    return SliverAppBar(
      expandedHeight: 260,
      pinned: true,
      backgroundColor: KineticTheme.background,
      flexibleSpace: FlexibleSpaceBar(
        background: Stack(
          fit: StackFit.expand,
          children: [
            RecipeImage(url: recipe.imageUrl, name: recipe.name, height: 260),
            // Verlaufsschleier, damit Titel und Symbole auf jedem Foto lesbar
            // bleiben - auch auf einem hellen.
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0xCC0E0E0E), Colors.transparent, Color(0x990E0E0E)],
                  stops: [0, 0.45, 1],
                ),
              ),
            ),
          ],
        ),
      ),
      actions: [
        IconButton(
          onPressed: _toggleFavorite,
          icon: Icon(
            recipe.isFavorite ? Icons.favorite : Icons.favorite_border,
            color: recipe.isFavorite
                ? KineticTheme.primary
                : KineticTheme.textPrimary,
          ),
          tooltip: recipe.isFavorite ? 'Aus dem Kochbuch nehmen' : 'Ins Kochbuch',
        ),
        PopupMenuButton<String>(
          color: KineticTheme.surfaceElevated,
          onSelected: (value) => switch (value) {
            'edit' => _edit(),
            'delete' => _delete(),
            _ => null,
          },
          itemBuilder: (context) => [
            PopupMenuItem(
              value: 'edit',
              child: Text('Bearbeiten', style: KineticTheme.caption),
            ),
            PopupMenuItem(
              value: 'delete',
              child: Text('Löschen',
                  style: KineticTheme.caption.copyWith(color: KineticTheme.danger)),
            ),
          ],
        ),
      ],
    );
  }

  Widget _head(Recipe recipe) {
    final difficulty = displayDifficulty(recipe.difficulty);

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(recipe.name, style: KineticTheme.headline),
          if (recipe.description != null && recipe.description!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(recipe.description!, style: KineticTheme.subtitle),
          ],
          const SizedBox(height: 20),
          // Ohne Kalorien: die stehen weiter unten unter "Nährwerte", und
          // dieselbe Zahl zweimal auf einem Schirm ist Rauschen.
          _StatRow(cells: [
            ('Zeit', formatDuration(recipe.totalTimeMinutes)),
            ('Portionen', '${recipe.servings}'),
            if (difficulty != null) ('Aufwand', difficulty),
          ]),
          const SizedBox(height: 20),
          Row(
            children: [
              RatingStars(rating: recipe.rating, size: 24, onRate: _rate),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  [
                    if (recipe.cookCount > 0) formatCookCount(recipe.cookCount),
                    if (recipe.lastCookedAt != null)
                      'zuletzt am ${DateFormat('d. MMM', 'de_DE').format(recipe.lastCookedAt!)}',
                  ].join(' · '),
                  style: KineticTheme.label,
                ),
              ),
            ],
          ),
          if (recipe.tagList.isNotEmpty) ...[
            const SizedBox(height: 16),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final tag in recipe.tagList)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    color: KineticTheme.surfaceElevated,
                    child: Text(tag, style: KineticTheme.label),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _ingredients(Recipe recipe) {
    final portions = _servings ?? recipe.servings;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 28, 20, 12),
          child: Row(
            children: [
              Expanded(child: Text('ZUTATEN', style: KineticTheme.label)),
              ServingsStepper(
                value: portions,
                onChanged: (value) => setState(() => _servings = value),
              ),
            ],
          ),
        ),
        IngredientTable(ingredients: recipe.scaledTo(portions)),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _addToShoppingList,
                  style: _outlined,
                  child: const Text('Auf die Einkaufsliste'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton(
                  onPressed: () => PlanMealSheet.show(
                    context,
                    date: DateTime.now(),
                    recipe: recipe,
                  ),
                  style: _outlined,
                  child: const Text('Zum Wochenplan'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  static final _outlined = OutlinedButton.styleFrom(
    foregroundColor: KineticTheme.primary,
    side: const BorderSide(color: KineticTheme.divider),
    minimumSize: const Size.fromHeight(46),
    shape: const RoundedRectangleBorder(),
    textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
  );

  Widget _steps(Recipe recipe) {
    if (recipe.steps.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const RecipeSection(title: 'Zubereitung'),
        for (var i = 0; i < recipe.steps.length; i++)
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 28,
                  height: 28,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    border: Border.all(color: KineticTheme.primary),
                    shape: BoxShape.circle,
                  ),
                  child: Text('${i + 1}',
                      style: KineticTheme.label
                          .copyWith(color: KineticTheme.primary)),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Text(
                      recipe.steps[i].text,
                      style: KineticTheme.subtitle.copyWith(
                        color: KineticTheme.textPrimary,
                        height: 1.5,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _nutrition(Recipe recipe) {
    if (!recipe.hasNutrition) return const SizedBox.shrink();

    String grams(double? value) => value == null ? '–' : '${value.round()} g';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const RecipeSection(title: 'Nährwerte'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Text('je Portion', style: KineticTheme.label),
        ),
        const SizedBox(height: 12),
        // Skaliert bewusst **nicht** mit dem Portionsregler: die Werte sind je
        // Portion, und eine Portion bleibt eine Portion, egal wie viele man
        // kocht. Ein Test hält das fest.
        _StatRow(cells: [
          ('kcal', recipe.calories?.toString() ?? '–'),
          ('Eiweiß', grams(recipe.protein)),
          ('Kohlenhydrate', grams(recipe.carbs)),
          ('Fett', grams(recipe.fat)),
        ]),
      ],
    );
  }

  Widget _notes(Recipe recipe) {
    if (recipe.notes == null || recipe.notes!.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const RecipeSection(title: 'Notiz'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Text(recipe.notes!, style: KineticTheme.subtitle),
        ),
      ],
    );
  }

  Widget _history(Recipe recipe) {
    final log = context.watch<RecipeProvider>().cookLogOf(widget.recipeId);
    if (log.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const RecipeSection(title: 'Verlauf'),
        for (final entry in log)
          Dismissible(
            key: ValueKey('cooklog-${entry.id}'),
            direction: DismissDirection.endToStart,
            background: Container(
              alignment: Alignment.centerRight,
              padding: const EdgeInsets.only(right: 20),
              color: KineticTheme.surfaceElevated,
              child: const Icon(Icons.delete_outline,
                  size: 20, color: KineticTheme.danger),
            ),
            onDismissed: (_) async {
              await context
                  .read<RecipeProvider>()
                  .deleteCookLog(entry.id!, widget.recipeId);
              if (mounted) _load();
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
              child: Row(
                children: [
                  SizedBox(
                    width: 80,
                    child: Text(
                      DateFormat('d. MMM yy', 'de_DE').format(entry.cookedAt),
                      style: KineticTheme.caption,
                    ),
                  ),
                  if (entry.servings != null)
                    Text(formatServings(entry.servings!),
                        style: KineticTheme.label),
                  const Spacer(),
                  if (entry.rating != null)
                    RatingStars(rating: entry.rating, size: 12),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _source(Recipe recipe) {
    if (recipe.sourceName == null && recipe.sourceUrl == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 28, 20, 0),
      child: recipe.sourceUrl != null
          ? TextButton.icon(
              onPressed: _openSource,
              icon: const Icon(Icons.open_in_new, size: 15),
              label: Text('Auf ${recipe.sourceName ?? 'der Quellseite'} ansehen'),
              style: TextButton.styleFrom(foregroundColor: KineticTheme.primary),
            )
          : Text('Quelle: ${recipe.sourceName}', style: KineticTheme.label),
    );
  }
}

/// Eine Zeile aus Kennzahlen mit 1px-Trennern dazwischen.
class _StatRow extends StatelessWidget {
  const _StatRow({required this.cells});

  final List<(String, String)> cells;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < cells.length; i++) ...[
              if (i > 0)
                const VerticalDivider(
                    width: 1, thickness: 1, color: KineticTheme.divider),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(cells[i].$2, style: KineticTheme.amount),
                      const SizedBox(height: 2),
                      Text(cells[i].$1, style: KineticTheme.label),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
