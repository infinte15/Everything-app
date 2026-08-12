import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/shopping_item.dart';
import '../../../providers/recipe_provider.dart';
import '../../../providers/shopping_list_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/recipe_error_banner.dart';
import '../widgets/recipe_format.dart';
import '../widgets/recipe_section.dart';

/// Die Einkaufsliste, nach Ladenregalen gruppiert.
///
/// **Ohne eigene AppBar** - die frühere Fassung brachte eine mit und erzeugte
/// damit zwei Balken übereinander im selben Space.
///
/// Häkchen, Hinzufügen und Löschen gehen jetzt an den Server. Vorher lebten sie
/// nur im Speicher des Providers: gesetzt, und beim nächsten Laden weg.
class RecipeShoppingTab extends StatelessWidget {
  const RecipeShoppingTab({super.key, this.onOpenPlan});

  /// Der Rückweg in den Wochenplan - aus dem Kopf und aus dem Leerzustand.
  final VoidCallback? onOpenPlan;

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ShoppingListProvider>();
    final groups = provider.byAisle;

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
          _header(context, provider),
          if (provider.items.isEmpty)
            RecipeEmpty(
              icon: Icons.shopping_basket_outlined,
              title: 'Die Liste ist leer',
              message: 'Setz etwas drauf, oder übernimm die Zutaten der Woche '
                  'aus dem Wochenplan.',
              // Der Satz nannte den Wochenplan schon, führte aber nicht hin.
              actionLabel: onOpenPlan == null ? null : 'Zum Wochenplan',
              onAction: onOpenPlan,
            )
          else
            for (final entry in groups.entries) ...[
              RecipeSection(title: entry.key),
              for (final item in entry.value)
                _ShoppingItemTile(item: item, provider: provider),
            ],
        ],
      ),
    );
  }

  Widget _header(BuildContext context, ShoppingListProvider provider) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 8, 4),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Einkaufsliste', style: KineticTheme.title),
                const SizedBox(height: 4),
                Text(
                  provider.items.isEmpty
                      ? 'Nichts drauf'
                      : '${provider.openCount} offen · '
                          '${provider.checkedCount} erledigt',
                  style: KineticTheme.label,
                ),
                _origin(provider),
              ],
            ),
          ),
          PopupMenuButton<String>(
            color: KineticTheme.surfaceElevated,
            icon: const Icon(Icons.more_horiz,
                size: 20, color: KineticTheme.textTertiary),
            onSelected: (value) => _onMenu(context, provider, value),
            itemBuilder: (context) => [
              if (provider.hasChecked)
                PopupMenuItem(
                  value: 'clear',
                  child: Text('Erledigte löschen', style: KineticTheme.caption),
                ),
              PopupMenuItem(
                value: 'rebuild',
                child: Text('Aus Wochenplan aufbauen…', style: KineticTheme.caption),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// Woher die Liste stammt - und der Weg zurück in den Plan.
  ///
  /// Drei Stufen, absteigend nach Gewissheit: die Woche, aus der aufgebaut
  /// wurde; sonst der unscharfe Satz, wenn irgendeine Zeile aus einem
  /// Wochenplan kommt; sonst gar nichts. Bewusst **nicht**
  /// `RecipeProvider.weekStart` - das ist die Woche, die der Plan-Reiter gerade
  /// anzeigt, und blätterte der Nutzer dort weiter, behauptete dieser Kopf
  /// sofort etwas Falsches über die Herkunft der Liste.
  Widget _origin(ShoppingListProvider provider) {
    final start = provider.builtFromStart;
    final end = provider.builtFromEnd;

    if (start == null || end == null) {
      if (!provider.hasMealPlanItems) return const SizedBox.shrink();
      return Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Text('Enthält Zeilen aus einem Wochenplan',
            style: KineticTheme.label),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: InkWell(
        onTap: onOpenPlan,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Aus KW ${isoWeek(start)} · ${formatWeekRange(start, end)}',
              style: KineticTheme.label.copyWith(
                color: onOpenPlan == null
                    ? KineticTheme.textTertiary
                    : KineticTheme.primary,
              ),
            ),
            if (onOpenPlan != null) ...[
              const SizedBox(width: 2),
              const Icon(Icons.chevron_right, size: 14, color: KineticTheme.primary),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _onMenu(
      BuildContext context, ShoppingListProvider provider, String value) async {
    if (value == 'clear') {
      await provider.clearChecked();
      return;
    }

    final recipes = context.read<RecipeProvider>();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Theme(
        data: KineticTheme.darkTheme,
        child: AlertDialog(
          backgroundColor: KineticTheme.surfaceElevated,
          title: Text('Aus Wochenplan aufbauen?', style: KineticTheme.title),
          // Konkret statt "der angezeigten Woche": der Nutzer steht auf dem
          // Einkaufsreiter und kann sie gerade nicht sehen.
          content: Text(
            'Zutaten der KW ${isoWeek(recipes.weekStart)} '
            '(${formatWeekRange(recipes.weekStart, recipes.weekEnd)}) '
            'übernehmen? Ersetzt die noch nicht abgehakten Zeilen aus dem '
            'Wochenplan - eigene Einträge und Abgehaktes bleiben stehen.',
            style: KineticTheme.caption,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Abbrechen'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Aufbauen'),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true) return;
    await provider.rebuildFromWeek(recipes.weekStart, recipes.weekEnd);
  }
}

/// Eine Zeile der Liste.
class _ShoppingItemTile extends StatelessWidget {
  const _ShoppingItemTile({required this.item, required this.provider});

  final ShoppingItem item;
  final ShoppingListProvider provider;

  @override
  Widget build(BuildContext context) {
    final amount = formatAmountWithUnit(item.amount, item.unit);

    return Dismissible(
      key: ValueKey('shopping-${item.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: KineticTheme.surfaceElevated,
        child: const Icon(Icons.delete_outline,
            size: 20, color: KineticTheme.danger),
      ),
      onDismissed: (_) => provider.remove(item.id!),
      child: InkWell(
        onTap: () => provider.toggle(item),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
          child: Row(
            children: [
              Icon(
                item.isChecked
                    ? Icons.check_box_outlined
                    : Icons.check_box_outline_blank,
                size: 20,
                color: item.isChecked
                    ? KineticTheme.primary
                    : KineticTheme.textTertiary,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      child: Text(
                        item.name,
                        style: KineticTheme.subtitle.copyWith(
                          color: item.isChecked
                              ? KineticTheme.textTertiary
                              : KineticTheme.textPrimary,
                          decoration:
                              item.isChecked ? TextDecoration.lineThrough : null,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    // Damit sichtbar ist, welche Zeilen ein Neuaufbau verwirft.
                    if (item.source == ShoppingItemSource.mealPlan) ...[
                      const SizedBox(width: 8),
                      Text('Wochenplan', style: KineticTheme.label),
                    ],
                  ],
                ),
              ),
              if (amount.isNotEmpty)
                Text(
                  amount,
                  style: KineticTheme.amount.copyWith(
                    color: item.isChecked
                        ? KineticTheme.textTertiary
                        : KineticTheme.textPrimary,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
