import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/meal_type.dart';
import '../../../models/recipe.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import 'recipe_format.dart';
import 'recipe_image.dart';

/// Ein Rezept aussuchen.
///
/// Mit [suitableFor] stehen die passenden Rezepte oben, aber die übrigen bleiben
/// erreichbar: die Eignung ist ein Vorschlag des Systems, keine Vorschrift. Wer
/// abends Pfannkuchen will, soll sie planen können.
class RecipePickerSheet extends StatefulWidget {
  const RecipePickerSheet({super.key, this.suitableFor});

  final MealType? suitableFor;

  static Future<Recipe?> show(BuildContext context, {MealType? suitableFor}) {
    return showModalBottomSheet<Recipe>(
      context: context,
      backgroundColor: KineticTheme.surface,
      isScrollControlled: true,
      builder: (_) => RecipePickerSheet(suitableFor: suitableFor),
    );
  }

  @override
  State<RecipePickerSheet> createState() => _RecipePickerSheetState();
}

class _RecipePickerSheetState extends State<RecipePickerSheet> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final all = context.watch<RecipeProvider>().recipes;

    var list = all
        .where((r) => r.name.toLowerCase().contains(_query.toLowerCase()))
        .toList();

    if (widget.suitableFor != null) {
      list.sort((a, b) {
        final aFits = a.suitableFor.contains(widget.suitableFor) ? 0 : 1;
        final bFits = b.suitableFor.contains(widget.suitableFor) ? 0 : 1;
        if (aFits != bFits) return aFits - bFits;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
    } else {
      list.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    }

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.7,
      maxChildSize: 0.92,
      builder: (context, scrollController) => Column(
        children: [
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    widget.suitableFor == null
                        ? 'Rezept wählen'
                        : 'Rezept für ${widget.suitableFor!.label}',
                    style: KineticTheme.title,
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close, size: 20),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
            child: TextField(
              autofocus: false,
              onChanged: (value) => setState(() => _query = value),
              style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
              decoration: const InputDecoration(
                hintText: 'Suchen',
                isDense: true,
                prefixIcon:
                    Icon(Icons.search, size: 18, color: KineticTheme.textTertiary),
              ),
            ),
          ),
          Expanded(
            child: list.isEmpty
                ? Center(child: Text('Kein Rezept', style: KineticTheme.caption))
                : ListView.builder(
                    controller: scrollController,
                    itemCount: list.length,
                    itemBuilder: (context, index) {
                      final recipe = list[index];
                      final fits = widget.suitableFor == null ||
                          recipe.suitableFor.contains(widget.suitableFor);

                      return ListTile(
                        leading: RecipeImage(
                          url: recipe.imageUrl,
                          name: recipe.name,
                          height: 44,
                          width: 44,
                        ),
                        title: Text(recipe.name,
                            style: KineticTheme.subtitle
                                .copyWith(color: KineticTheme.textPrimary)),
                        subtitle: Text(
                          '${recipe.category} · '
                          '${formatDuration(recipe.totalTimeMinutes)}'
                          '${fits ? '' : ' · passt sonst nicht hierher'}',
                          style: KineticTheme.label,
                        ),
                        onTap: () => Navigator.pop(context, recipe),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
