import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/recipe_provider.dart';
import '../../config/app_theme.dart';

class RecipeShoppingPage extends StatelessWidget {
  const RecipeShoppingPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<RecipeProvider>();
    final items = provider.shoppingList;

    // Group items by category
    final groupedItems = <String, List<Map<String, dynamic>>>{};
    for (var i = 0; i < items.length; i++) {
      final item = items[i];
      final cat = item['category'] as String? ?? 'Sonstiges';
      if (!groupedItems.containsKey(cat)) {
        groupedItems[cat] = [];
      }
      groupedItems[cat]!.add({'index': i, 'item': item});
    }

    final hasCheckedItems = items.any((i) => i['checked'] == true);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        title: const Text('Einkaufsliste'),
        actions: [
          if (hasCheckedItems)
            TextButton.icon(
              onPressed: provider.clearCheckedItems,
              icon: const Icon(Icons.delete_sweep, color: Colors.grey),
              label: const Text('Erledigte löschen',
                  style: TextStyle(color: Colors.grey)),
            ),
        ],
      ),
      body: items.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.shopping_basket_outlined,
                      size: 64, color: Colors.grey),
                  const SizedBox(height: 16),
                  const Text('Liste ist leer',
                      style: TextStyle(color: Colors.grey, fontSize: 18)),
                  const SizedBox(height: 24),
                  FilledButton.icon(
                    onPressed: () => _showAddItemDialog(context),
                    icon: const Icon(Icons.add),
                    label: const Text('Artikel hinzufügen'),
                    style: FilledButton.styleFrom(
                        backgroundColor: AppTheme.recipesColor),
                  ),
                ],
              ),
            )
          : ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              children: [
                ...groupedItems.entries.map((group) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(top: 16, bottom: 8),
                        child: Text(
                          group.key,
                          style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.recipesColor),
                        ),
                      ),
                      Card(
                        margin: EdgeInsets.zero,
                        child: Column(
                          children: group.value.map((entry) {
                            final idx = entry['index'] as int;
                            final item = entry['item'] as Map<String, dynamic>;
                            final isChecked = item['checked'] == true;

                            return CheckboxListTile(
                              value: isChecked,
                              onChanged: (_) =>
                                  provider.toggleShoppingItem(idx),
                              title: Text(
                                item['name'],
                                style: TextStyle(
                                  decoration: isChecked
                                      ? TextDecoration.lineThrough
                                      : null,
                                  color: isChecked
                                      ? Colors.grey
                                      : theme.textTheme.bodyLarge?.color,
                                ),
                              ),
                              activeColor: AppTheme.recipesColor,
                              controlAffinity: ListTileControlAffinity.leading,
                            );
                          }).toList(),
                        ),
                      ),
                      const SizedBox(height: 8),
                    ],
                  );
                }),
                const SizedBox(height: 80), // Padding for FAB
              ],
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddItemDialog(context),
        backgroundColor: AppTheme.recipesColor,
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }

  Future<void> _showAddItemDialog(BuildContext context) async {
    final nameCtrl = TextEditingController();
    String category = 'Sonstiges';
    final categories = ['Gemüse', 'Obst', 'Fleisch', 'Fisch', 'Molkerei', 'Nudeln', 'Gewürze', 'Sonstiges'];

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(builder: (ctx, setSt) {
        return AlertDialog(
          title: const Text('Artikel hinzufügen'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                autofocus: true,
                decoration: const InputDecoration(labelText: 'Artikel'),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: category,
                decoration: const InputDecoration(labelText: 'Kategorie'),
                items: categories
                    .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                    .toList(),
                onChanged: (v) => setSt(() => category = v!),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Abbrechen')),
            FilledButton(
              onPressed: () {
                if (nameCtrl.text.trim().isNotEmpty) {
                  context
                      .read<RecipeProvider>()
                      .addToShoppingList(nameCtrl.text.trim(), category);
                  Navigator.pop(ctx);
                }
              },
              style:
                  FilledButton.styleFrom(backgroundColor: AppTheme.recipesColor),
              child: const Text('Hinzufügen'),
            ),
          ],
        );
      }),
    );
  }
}
