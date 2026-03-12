import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/recipe_provider.dart';
import '../../config/app_theme.dart';
import '../../models/recipe.dart';

class RecipeDetailPage extends StatefulWidget {
  final Recipe recipe;
  const RecipeDetailPage({super.key, required this.recipe});

  @override
  State<RecipeDetailPage> createState() => _RecipeDetailPageState();
}

class _RecipeDetailPageState extends State<RecipeDetailPage> {
  late int _currentServings;

  @override
  void initState() {
    super.initState();
    _currentServings = widget.recipe.servings > 0 ? widget.recipe.servings : 1;
  }

  void _updateServings(int delta) {
    setState(() {
      _currentServings += delta;
      if (_currentServings < 1) _currentServings = 1;
      if (_currentServings > 20) _currentServings = 20;
    });
  }

  String _formatScaledIngredient(String ingredientLine) {
    // A simple regex to find numbers at the start of a line, e.g., "400g Spaghetti" -> "400", "1 Dose Tomaten" -> "1"
    // Also handles decimals like "1.5" or simple fractions "1/2". For this demo we'll use a basic number matcher.
    
    final regex = RegExp(r'^([\d.,]+)\s*(.*)$');
    final match = regex.firstMatch(ingredientLine.trim());

    if (match != null) {
      final numStr = match.group(1)!.replaceAll(',', '.');
      final rest = match.group(2)!;
      
      final originalVal = double.tryParse(numStr);
      if (originalVal != null) {
        final scaledVal = (originalVal / widget.recipe.servings) * _currentServings;
        
        // Format nicely: drop .0 if it's an integer
        String formattedVal = scaledVal.toStringAsFixed(1);
        if (formattedVal.endsWith('.0')) {
          formattedVal = formattedVal.substring(0, formattedVal.length - 2);
        }
        
        return '$formattedVal $rest';
      }
    }
    
    // If no number found at start, return as is
    return ingredientLine;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<RecipeProvider>();
    final isFavorite = provider.favoriteRecipes.any((r) => r.id == widget.recipe.id);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: CustomScrollView(
        slivers: [
          // Hero Image Header
          SliverAppBar(
            expandedHeight: 300,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              background: widget.recipe.imageUrl != null
                  ? Image.network(widget.recipe.imageUrl!, fit: BoxFit.cover)
                  : Container(
                      color: AppTheme.recipesColor.withOpacity(0.2),
                      child: const Center(
                          child: Icon(Icons.restaurant,
                              size: 80, color: AppTheme.recipesColor)),
                    ),
            ),
            backgroundColor: AppTheme.recipesColor,
            elevation: 0,
            actions: [
              IconButton(
                icon: Icon(
                  isFavorite ? Icons.favorite : Icons.favorite_border,
                  color: isFavorite ? Colors.red : Colors.white,
                ),
                onPressed: () => provider.toggleFavorite(widget.recipe.id!),
              ),
              IconButton(
                icon: const Icon(Icons.share, color: Colors.white),
                onPressed: () {},
              ),
            ],
          ),

          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Badges
                  Wrap(
                    spacing: 8,
                    children: [
                      _Badge(
                          text: widget.recipe.category,
                          color: AppTheme.recipesColor),
                      if (widget.recipe.difficulty != null)
                        _Badge(
                            text: widget.recipe.difficulty!,
                            color: Colors.orange),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Title
                  Text(widget.recipe.name,
                      style: theme.textTheme.headlineSmall
                          ?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),

                  // Description
                  if (widget.recipe.description != null)
                    Text(
                      widget.recipe.description!,
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(color: Colors.grey[700], height: 1.4),
                    ),
                  const SizedBox(height: 20),

                  // Key Stats Bar
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    decoration: BoxDecoration(
                      border: Border.symmetric(
                          horizontal: BorderSide(
                              color: theme.colorScheme.outline.withOpacity(0.2))),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        _StatItem(
                            icon: Icons.timer_outlined,
                            value: '${widget.recipe.totalTimeMinutes} Min',
                            label: 'Gesamt'),
                        _StatItem(
                            icon: Icons.kitchen_outlined,
                            value: '${widget.recipe.prepTimeMinutes} Min',
                            label: 'Vorbereitung'),
                        _StatItem(
                            icon: Icons.local_fire_department_outlined,
                            value: '${widget.recipe.calories ?? '--'} kcal',
                            label: 'Pro Portion'),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Portion Calculator
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Zutaten',
                          style: TextStyle(
                              fontSize: 20, fontWeight: FontWeight.bold)),
                      Container(
                        decoration: BoxDecoration(
                          color: theme.colorScheme.surfaceVariant.withOpacity(0.5),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.remove),
                              onPressed: () => _updateServings(-1),
                              color: AppTheme.recipesColor,
                            ),
                            Text('$_currentServings Portionen',
                                style: const TextStyle(fontWeight: FontWeight.bold)),
                            IconButton(
                              icon: const Icon(Icons.add),
                              onPressed: () => _updateServings(1),
                              color: AppTheme.recipesColor,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Ingredients List
                  ...widget.recipe.ingredientsList.map((ing) {
                    final scaledText = _formatScaledIngredient(ing);
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Row(
                        children: [
                          const Icon(Icons.circle,
                              size: 8, color: AppTheme.recipesColor),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(scaledText,
                                style: const TextStyle(fontSize: 15)),
                          ),
                          IconButton(
                            icon: const Icon(Icons.add_shopping_cart,
                                size: 20, color: Colors.grey),
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                            onPressed: () {
                              provider.addToShoppingList(scaledText, 'Rezept Zutaten');
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                    content: Text('Zur Einkaufsliste hinzugefügt'),
                                    duration: Duration(seconds: 1)),
                              );
                            },
                          ),
                        ],
                      ),
                    );
                  }),
                  const SizedBox(height: 32),

                  // Instructions
                  const Text('Zubereitung',
                      style:
                          TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 16),
                  ...widget.recipe.instructionSteps.asMap().entries.map((entry) {
                    int stepIdx = entry.key + 1;
                    String stepText = entry.value;

                    // Remove leading numbers from string (e.g. "1. Do this")
                    final textRegex = RegExp(r'^\d+\.\s*(.*)');
                    final textMatch = textRegex.firstMatch(stepText.trim());
                    if (textMatch != null) {
                      stepText = textMatch.group(1)!;
                    }

                    return Padding(
                      padding: const EdgeInsets.only(bottom: 24),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 28,
                            height: 28,
                            decoration: BoxDecoration(
                              color: AppTheme.recipesColor,
                              shape: BoxShape.circle,
                            ),
                            alignment: Alignment.center,
                            child: Text(
                              '$stepIdx',
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold),
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Text(
                              stepText,
                              style: const TextStyle(height: 1.5, fontSize: 16),
                            ),
                          ),
                        ],
                      ),
                    );
                  }),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  final String text;
  final Color color;
  const _Badge({required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Text(
        text,
        style: TextStyle(
            color: color, fontSize: 12, fontWeight: FontWeight.bold),
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;

  const _StatItem(
      {required this.icon, required this.value, required this.label});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: Colors.grey[700]),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }
}
