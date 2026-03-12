import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../config/app_theme.dart';
import 'recipe_discover_page.dart';
import 'recipe_cookbook_page.dart';
import 'recipe_plan_page.dart';
import 'recipe_shopping_page.dart';

class RecipesScreen extends StatefulWidget {
  const RecipesScreen({super.key});

  @override
  State<RecipesScreen> createState() => _RecipesScreenState();
}

class _RecipesScreenState extends State<RecipesScreen> {
  int _selectedIndex = 0;

  static const _pages = [
    RecipeDiscoverPage(),
    RecipeCookbookPage(),
    RecipePlanPage(),
    RecipeShoppingPage(),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isWide = MediaQuery.of(context).size.width > 600;

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        leading: isWide
            ? null
            : IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => context.go('/spaces'),
              ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('🍳', style: TextStyle(fontSize: 20)),
            const SizedBox(width: 8),
            Text(
              ['Entdecken', 'Kochbuch', 'Wochenplan', 'Einkauf'][_selectedIndex],
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          ],
        ),
        backgroundColor: AppTheme.recipesColor,
        foregroundColor: Colors.white,
      ),
      body: Row(
        children: [
          if (isWide)
            NavigationRail(
              selectedIndex: _selectedIndex,
              onDestinationSelected: (i) => setState(() => _selectedIndex = i),
              labelType: NavigationRailLabelType.all,
              selectedIconTheme:
                  const IconThemeData(color: AppTheme.recipesColor),
              selectedLabelTextStyle: const TextStyle(
                color: AppTheme.recipesColor,
                fontWeight: FontWeight.bold,
              ),
              leading: Column(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back),
                    onPressed: () => context.go('/spaces'),
                  ),
                  const SizedBox(height: 16),
                ],
              ),
              destinations: const [
                NavigationRailDestination(
                  icon: Icon(Icons.explore_outlined),
                  selectedIcon: Icon(Icons.explore),
                  label: Text('Entdecken'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.menu_book_outlined),
                  selectedIcon: Icon(Icons.menu_book),
                  label: Text('Kochbuch'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.calendar_month_outlined),
                  selectedIcon: Icon(Icons.calendar_month),
                  label: Text('Planer'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.shopping_cart_outlined),
                  selectedIcon: Icon(Icons.shopping_cart),
                  label: Text('Einkauf'),
                ),
              ],
            ),
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 250),
              child: KeyedSubtree(
                key: ValueKey(_selectedIndex),
                child: _pages[_selectedIndex],
              ),
            ),
          ),
        ],
      ),
      bottomNavigationBar: isWide
          ? null
          : NavigationBar(
              selectedIndex: _selectedIndex,
              onDestinationSelected: (i) => setState(() => _selectedIndex = i),
              indicatorColor: AppTheme.recipesColor.withOpacity(0.2),
              destinations: const [
                NavigationDestination(
                  icon: Icon(Icons.explore_outlined),
                  selectedIcon:
                      Icon(Icons.explore, color: AppTheme.recipesColor),
                  label: 'Entdecken',
                ),
                NavigationDestination(
                  icon: Icon(Icons.menu_book_outlined),
                  selectedIcon:
                      Icon(Icons.menu_book, color: AppTheme.recipesColor),
                  label: 'Kochbuch',
                ),
                NavigationDestination(
                  icon: Icon(Icons.calendar_month_outlined),
                  selectedIcon:
                      Icon(Icons.calendar_month, color: AppTheme.recipesColor),
                  label: 'Planer',
                ),
                NavigationDestination(
                  icon: Icon(Icons.shopping_cart_outlined),
                  selectedIcon:
                      Icon(Icons.shopping_cart, color: AppTheme.recipesColor),
                  label: 'Einkauf',
                ),
              ],
            ),
    );
  }
}