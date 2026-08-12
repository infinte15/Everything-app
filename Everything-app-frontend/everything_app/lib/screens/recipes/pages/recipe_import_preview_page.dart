import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/recipe.dart';
import '../../../models/recipe_import_preview.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/ingredient_table.dart';
import '../widgets/recipe_format.dart';
import '../widgets/recipe_image.dart';
import '../widgets/recipe_section.dart';
import 'recipe_editor_page.dart';

/// Was der Import gelesen hat - zum Ansehen, bevor es gespeichert wird.
///
/// Zwei Schritte statt einem: ein Import, der still ein halbfalsches Rezept
/// anlegt, ist schlimmer als gar keiner.
class RecipeImportPreviewPage extends StatefulWidget {
  const RecipeImportPreviewPage({super.key, required this.preview});

  final RecipeImportPreview preview;

  @override
  State<RecipeImportPreviewPage> createState() => _RecipeImportPreviewPageState();
}

class _RecipeImportPreviewPageState extends State<RecipeImportPreviewPage> {
  late final Recipe _recipe = widget.preview.recipe;
  bool _saving = false;

  bool get _canSave => widget.preview.isSaveable;

  Future<void> _edit() async {
    // Der Editor speichert selbst, wenn das Rezept schon eine Id hat - hier hat
    // es keine, also legt er es an und wir sind fertig.
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => RecipeEditorPage(initial: _recipe)),
    );
    if (saved == true && mounted) Navigator.of(context).pop(true);
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    final provider = context.read<RecipeProvider>();
    final created = await provider.create(_recipe);

    if (!mounted) return;
    if (created != null) {
      Navigator.of(context).pop(true);
    } else {
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(provider.error ?? 'Speichern hat nicht geklappt')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final warnings = widget.preview.warnings;

    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        appBar: AppBar(title: const Text('Gefundenes Rezept')),
        body: ListView(
          padding: const EdgeInsets.only(bottom: 40),
          children: [
            if (warnings.isNotEmpty) _warnings(warnings),
            if (_recipe.imageUrl != null && _recipe.imageUrl!.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: RecipeImage(
                      url: _recipe.imageUrl, name: _recipe.name, height: 200),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_recipe.name, style: KineticTheme.headline),
                  if (_recipe.description != null &&
                      _recipe.description!.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(_recipe.description!, style: KineticTheme.subtitle),
                  ],
                  const SizedBox(height: 12),
                  Text(
                    [
                      _recipe.category,
                      formatDuration(_recipe.totalTimeMinutes),
                      formatServings(_recipe.servings),
                      if (_recipe.sourceName != null) _recipe.sourceName!,
                    ].join(' · '),
                    style: KineticTheme.label,
                  ),
                ],
              ),
            ),
            const RecipeSection(title: 'Zutaten'),
            IngredientTable(ingredients: _recipe.ingredients),
            if (_recipe.steps.isNotEmpty) ...[
              const RecipeSection(title: 'Zubereitung'),
              for (var i = 0; i < _recipe.steps.length; i++)
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SizedBox(
                        width: 24,
                        child: Text('${i + 1}.', style: KineticTheme.label),
                      ),
                      Expanded(
                        child: Text(
                          _recipe.steps[i].text,
                          style: KineticTheme.subtitle.copyWith(
                            color: KineticTheme.textPrimary,
                            height: 1.5,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
            ],
            const SizedBox(height: 24),
            if (!_canSave)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(
                  'Ohne Zutaten und Zubereitung lässt sich das Rezept nicht '
                  'speichern — bitte zuerst bearbeiten.',
                  style: KineticTheme.caption,
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _saving ? null : _edit,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: KineticTheme.primary,
                        side: const BorderSide(color: KineticTheme.divider),
                        minimumSize: const Size.fromHeight(50),
                        shape: const RoundedRectangleBorder(),
                      ),
                      child: const Text('Bearbeiten'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton(
                      onPressed: !_canSave || _saving ? null : _save,
                      child: Text(_saving ? 'Wird gespeichert…' : 'Speichern'),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Warnungen stehen ganz oben - aber **nie rot**. Eine Warnung ist kein
  /// Fehler: das Rezept ist da, es fehlt nur etwas daran.
  Widget _warnings(List<String> warnings) {
    return Container(
      margin: const EdgeInsets.fromLTRB(20, 16, 20, 0),
      padding: const EdgeInsets.all(16),
      color: KineticTheme.surfaceElevated,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('BEIM LESEN AUFGEFALLEN', style: KineticTheme.label),
          const SizedBox(height: 10),
          for (final warning in warnings)
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('· ', style: KineticTheme.caption),
                  Expanded(child: Text(warning, style: KineticTheme.caption)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
