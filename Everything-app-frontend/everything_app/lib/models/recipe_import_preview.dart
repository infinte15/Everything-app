import 'recipe.dart';

/// Was ein Import gelesen hat - noch nicht gespeichert.
///
/// Der zweistufige Ablauf ist Absicht: ein Import, der still ein halbfalsches
/// Rezept anlegt, ist schlimmer als gar keiner. [warnings] sind fertige deutsche
/// Sätze aus dem Backend ("Kategorie nicht erkennbar - bitte auswählen.") und
/// gehören unverändert angezeigt. Sie sind **keine Fehler**: ein Rezept mit
/// Warnungen lässt sich speichern, es fehlt nur etwas.
class RecipeImportPreview {
  final Recipe recipe;
  final List<String> warnings;

  const RecipeImportPreview({required this.recipe, this.warnings = const []});

  factory RecipeImportPreview.fromJson(Map<String, dynamic> json) {
    return RecipeImportPreview(
      recipe: Recipe.fromJson(json['recipe'] ?? const {}),
      warnings: ((json['warnings'] as List?) ?? const [])
          .map((w) => w.toString())
          .toList(),
    );
  }

  /// Ohne Zutaten oder ohne Schritte lehnt der Server das Rezept mit 400 ab
  /// (`@NotEmpty` auf beiden Listen). Die Vorschau sperrt "Speichern" deshalb
  /// vorher, statt den Nutzer in einen unerklärten Fehler laufen zu lassen.
  bool get isSaveable => recipe.ingredients.isNotEmpty && recipe.steps.isNotEmpty;
}
