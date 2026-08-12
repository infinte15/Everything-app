import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../providers/recipe_provider.dart';
import '../../providers/recipe_space_provider.dart';
import '../../providers/shopping_list_provider.dart';
import '../../theme/kinetic_theme.dart';
import 'pages/recipe_cookbook_tab.dart';
import 'pages/recipe_discover_tab.dart';
import 'pages/recipe_editor_page.dart';
import 'pages/recipe_import_page.dart';
import 'pages/recipe_plan_tab.dart';
import 'pages/recipe_shopping_tab.dart';
import 'widgets/add_shopping_item_sheet.dart';
import 'widgets/plan_meal_sheet.dart';

/// Der Rezepte-Space.
///
/// Aufbau wie beim Finance und beim Gym Space: der ganze Bereich wird in
/// [KineticTheme.darkTheme] gewickelt und bringt seine eigene Navigation mit.
/// Die frühere Fassung brachte ihre Farben selbst mit (Grün, Orange, weiße
/// Karten mit Schatten und 32er-Rundungen) und stand damit als einziger Space
/// außerhalb der Formensprache.
///
/// Die vier Reiter sind Chefkochs Aufbau: Entdecken, Kochbuch, Wochenplan,
/// Einkaufsliste.
class RecipesScreen extends StatefulWidget {
  const RecipesScreen({super.key});

  @override
  State<RecipesScreen> createState() => _RecipesScreenState();
}

class _RecipesScreenState extends State<RecipesScreen> {
  @override
  void initState() {
    super.initState();
    // Beim Öffnen laden - wie in jedem anderen Space. Vorher lud einzig ein
    // RefreshIndicator auf dem ersten Reiter, weshalb Kochbuch, Wochenplan und
    // Einkauf beim ersten Öffnen für immer leer blieben.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<RecipeProvider>().load();
      context.read<ShoppingListProvider>().load();
    });
  }

  void _goToTab(int index) => context.read<RecipeSpaceProvider>().openTab(index);

  /// Der FAB hängt am Reiter - und hier wird der tote "Neues Rezept"-Knopf
  /// lebendig, den es im Kochbuch schon gab.
  void _onAdd() {
    switch (context.read<RecipeSpaceProvider>().tab) {
      case 2:
        PlanMealSheet.show(context, date: DateTime.now());
      case 3:
        AddShoppingItemSheet.show(context);
      default:
        _showCreateMenu();
    }
  }

  void _showCreateMenu() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: KineticTheme.surface,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 8),
            ListTile(
              leading: const Icon(Icons.edit_outlined, color: KineticTheme.primary),
              title: Text('Neues Rezept', style: KineticTheme.title),
              subtitle: Text('Selbst eintippen', style: KineticTheme.caption),
              onTap: () {
                Navigator.pop(sheetContext);
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => const RecipeEditorPage(),
                ));
              },
            ),
            ListTile(
              leading: const Icon(Icons.link, color: KineticTheme.primary),
              title: Text('Von einer Adresse', style: KineticTheme.title),
              subtitle: Text('Link zu einer Rezeptseite einfügen',
                  style: KineticTheme.caption),
              onTap: () {
                Navigator.pop(sheetContext);
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => const RecipeImportPage(),
                ));
              },
            ),
            ListTile(
              leading: const Icon(Icons.content_paste, color: KineticTheme.primary),
              title: Text('Aus Text', style: KineticTheme.title),
              subtitle: Text('Bildunterschrift oder Rezepttext einfügen',
                  style: KineticTheme.caption),
              onTap: () {
                Navigator.pop(sheetContext);
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => const RecipeImportPage(initialTab: 1),
                ));
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final recipes = context.watch<RecipeProvider>();
    final shopping = context.watch<ShoppingListProvider>();
    final tabIndex = context.watch<RecipeSpaceProvider>().tab;

    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        appBar: AppBar(
          title: const Text('Rezepte'),
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            // go statt pop: bei einem Tiefenlink hat pop kein Ziel.
            onPressed: () => context.go('/spaces'),
          ),
        ),
        body: Stack(
          children: [
            IndexedStack(
              index: tabIndex,
              children: [
                RecipeDiscoverTab(onOpenCookbook: () => _goToTab(1)),
                const RecipeCookbookTab(),
                RecipePlanTab(onOpenShopping: () => _goToTab(3)),
                RecipeShoppingTab(onOpenPlan: () => _goToTab(2)),
              ],
            ),
            if (recipes.isLoading || shopping.isLoading || shopping.isRebuilding)
              const Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: LinearProgressIndicator(
                  minHeight: 2,
                  backgroundColor: Colors.transparent,
                  color: KineticTheme.primary,
                ),
              ),
          ],
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: _onAdd,
          backgroundColor: KineticTheme.primary,
          foregroundColor: KineticTheme.onPrimary,
          elevation: 0,
          child: const Icon(Icons.add),
        ),
        bottomNavigationBar: _RecipeBottomNav(
          selectedIndex: tabIndex,
          onSelect: _goToTab,
        ),
      ),
    );
  }
}

/// Eigene Leiste statt [BottomNavigationBar] - dieselbe zurückhaltende Form wie
/// im Finance und im Gym Space.
class _RecipeBottomNav extends StatelessWidget {
  const _RecipeBottomNav({required this.selectedIndex, required this.onSelect});

  final int selectedIndex;
  final ValueChanged<int> onSelect;

  static const _items = [
    (Icons.explore_outlined, Icons.explore, 'Entdecken'),
    (Icons.menu_book_outlined, Icons.menu_book, 'Kochbuch'),
    (Icons.calendar_view_week_outlined, Icons.calendar_view_week, 'Wochenplan'),
    (Icons.shopping_basket_outlined, Icons.shopping_basket, 'Einkauf'),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: KineticTheme.background,
        border: Border(top: BorderSide(color: KineticTheme.divider, width: 0.5)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 58,
          child: Row(
            children: [
              for (var i = 0; i < _items.length; i++)
                Expanded(
                  child: InkWell(
                    onTap: () => onSelect(i),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          selectedIndex == i ? _items[i].$2 : _items[i].$1,
                          size: 21,
                          color: selectedIndex == i
                              ? KineticTheme.primary
                              : KineticTheme.textTertiary,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _items[i].$3,
                          style: KineticTheme.label.copyWith(
                            fontSize: 9,
                            color: selectedIndex == i
                                ? KineticTheme.primary
                                : KineticTheme.textTertiary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
