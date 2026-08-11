import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../config/recipe_categories.dart';
import '../../../models/meal_type.dart';
import '../../../models/recipe.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/recipe_section.dart';

/// Rezept anlegen oder ändern.
///
/// Eine Seite für beides. Der "Neues Rezept"-Knopf im Kochbuch hatte bisher
/// `onPressed: () {}` - es gab keinen Weg, ein Rezept anzulegen.
///
/// Die Validierung spiegelt das DTO **vorher**: Name, mindestens eine Zutat mit
/// Namen, mindestens ein Schritt. Ein 400 vom Server darf nie die erste
/// Rückmeldung auf zehn Minuten Tipparbeit sein.
class RecipeEditorPage extends StatefulWidget {
  const RecipeEditorPage({super.key, this.initial});

  /// Zum Ändern - oder als Vorbelegung aus einer Import-Vorschau.
  final Recipe? initial;

  @override
  State<RecipeEditorPage> createState() => _RecipeEditorPageState();
}

class _RecipeEditorPageState extends State<RecipeEditorPage> {
  final _formKey = GlobalKey<FormState>();

  late final _name = TextEditingController(text: widget.initial?.name ?? '');
  late final _description =
      TextEditingController(text: widget.initial?.description ?? '');
  late final _prep = TextEditingController(
      text: (widget.initial?.prepTimeMinutes ?? 0).toString());
  late final _cook = TextEditingController(
      text: (widget.initial?.cookTimeMinutes ?? 0).toString());
  late final _servings =
      TextEditingController(text: (widget.initial?.servings ?? 2).toString());
  late final _imageUrl = TextEditingController(text: widget.initial?.imageUrl ?? '');
  late final _tags = TextEditingController(text: widget.initial?.tags ?? '');
  late final _notes = TextEditingController(text: widget.initial?.notes ?? '');
  late final _calories =
      TextEditingController(text: widget.initial?.calories?.toString() ?? '');
  late final _protein =
      TextEditingController(text: widget.initial?.protein?.toString() ?? '');
  late final _carbs =
      TextEditingController(text: widget.initial?.carbs?.toString() ?? '');
  late final _fat = TextEditingController(text: widget.initial?.fat?.toString() ?? '');

  late String _category = widget.initial?.category ?? 'Sonstiges';
  late String _difficulty =
      displayDifficulty(widget.initial?.difficulty) ?? 'Mittel';
  late final Set<MealType> _suitableFor = {...?widget.initial?.suitableFor};

  late final List<_IngredientRow> _ingredients = [
    ...?widget.initial?.ingredients.map(_IngredientRow.from),
    if (widget.initial == null || widget.initial!.ingredients.isEmpty)
      _IngredientRow.empty(),
  ];

  late final List<TextEditingController> _steps = [
    ...?widget.initial?.steps.map((s) => TextEditingController(text: s.text)),
    if (widget.initial == null || widget.initial!.steps.isEmpty)
      TextEditingController(),
  ];

  bool _saving = false;

  /// Controller der "Zeilen einfügen"-Sheets, siehe [_pasteIngredients].
  final List<TextEditingController> _pasteControllers = [];

  bool get _isEdit => widget.initial?.id != null;

  @override
  void dispose() {
    for (final controller in [
      _name, _description, _prep, _cook, _servings,
      _imageUrl, _tags, _notes, _calories, _protein, _carbs, _fat,
    ]) {
      controller.dispose();
    }
    for (final row in _ingredients) {
      row.dispose();
    }
    for (final step in _steps) {
      step.dispose();
    }
    for (final controller in _pasteControllers) {
      controller.dispose();
    }
    super.dispose();
  }

  // ── Zutaten einfügen ───────────────────────────────────────────────────────

  /// Einen ganzen Zutatenblock einfügen und vom Server zerlegen lassen.
  ///
  /// Nutzt `POST /api/recipes/ingredients/parse` - denselben Parser wie der
  /// Import. Der Endpunkt existiert seit dem Backend-Umbau und wurde bisher von
  /// niemandem aufgerufen; er macht aus dem Editor das Werkzeug, das man beim
  /// Abtippen aus einem Kochbuch braucht.
  Future<void> _pasteIngredients() async {
    // Wird erst in [dispose] freigegeben, nicht gleich nach dem Schließen: das
    // Sheet läuft noch seine Schließanimation, sein Textfeld hängt so lange am
    // Controller, und ein Controller, der unter einem eingebauten Feld
    // weggezogen wird, reißt den Fokusbaum mit.
    final controller = TextEditingController();
    _pasteControllers.add(controller);
    final text = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: KineticTheme.surface,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding:
            EdgeInsets.only(bottom: MediaQuery.of(sheetContext).viewInsets.bottom),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Zeilen einfügen', style: KineticTheme.title),
                const SizedBox(height: 8),
                Text(
                  'Eine Zutat je Zeile. Menge und Einheit werden erkannt.',
                  style: KineticTheme.caption,
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: controller,
                  maxLines: 8,
                  autofocus: true,
                  style: KineticTheme.subtitle
                      .copyWith(color: KineticTheme.textPrimary),
                  decoration: const InputDecoration(
                    hintText: '400 g Mehl\n1 Prise Salz\n2 Eier',
                  ),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () =>
                      Navigator.pop(sheetContext, controller.text),
                  child: const Text('Zerlegen'),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    if (text == null || text.trim().isEmpty || !mounted) return;

    try {
      final parsed = await context.read<RecipeProvider>().parseIngredients(text);
      if (!mounted) return;
      setState(() {
        // Eine leere Anfangszeile wird ersetzt, nicht behalten.
        _ingredients.removeWhere((row) => row.isEmpty);
        _ingredients.addAll(parsed.map(_IngredientRow.from));
        if (_ingredients.isEmpty) _ingredients.add(_IngredientRow.empty());
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  // ── Speichern ──────────────────────────────────────────────────────────────

  Future<void> _save() async {
    // Der Form-Validator allein reicht hier **nicht**: die Seite ist eine
    // ListView, und ein Feld, das aus dem Bild gescrollt ist, ist abgebaut und
    // beim Form gar nicht mehr angemeldet - `validate()` liefe über einen
    // leeren Namen hinweg, und die erste Rückmeldung wäre ein 400 vom Server.
    // Deshalb wird der Wert selbst geprüft; der Validator bleibt für die
    // Meldung direkt am Feld, wenn es sichtbar ist.
    _formKey.currentState?.validate();
    if (_name.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Ohne Namen lässt sich das Rezept nicht speichern.'),
      ));
      return;
    }

    final ingredients = _ingredients
        .where((row) => row.name.text.trim().isNotEmpty)
        .map((row) => row.toIngredient())
        .toList();
    final steps = _steps
        .map((c) => c.text.trim())
        .where((text) => text.isNotEmpty)
        .map((text) => RecipeStep(text: text))
        .toList();

    if (ingredients.isEmpty || steps.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Mindestens eine Zutat und ein Schritt sind nötig.'),
      ));
      return;
    }

    setState(() => _saving = true);

    final recipe = (widget.initial ?? Recipe.blank()).copyWith(
      name: _name.text.trim(),
      description: _description.text.trim(),
      prepTimeMinutes: int.tryParse(_prep.text.trim()) ?? 0,
      cookTimeMinutes: int.tryParse(_cook.text.trim()) ?? 0,
      servings: int.tryParse(_servings.text.trim()) ?? 1,
      category: _category,
      difficulty: _difficulty,
      suitableFor: _suitableFor,
      ingredients: ingredients,
      steps: steps,
      imageUrl: _imageUrl.text.trim(),
      tags: _tags.text.trim(),
      notes: _notes.text.trim(),
      calories: int.tryParse(_calories.text.trim()),
      protein: double.tryParse(_protein.text.trim().replaceAll(',', '.')),
      carbs: double.tryParse(_carbs.text.trim().replaceAll(',', '.')),
      fat: double.tryParse(_fat.text.trim().replaceAll(',', '.')),
    );

    final provider = context.read<RecipeProvider>();
    final saved = _isEdit ? await provider.update(recipe) : await provider.create(recipe);

    if (!mounted) return;
    if (saved != null) {
      Navigator.of(context).pop(true);
    } else {
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(provider.error ?? 'Speichern hat nicht geklappt')),
      );
    }
  }

  // ── Aufbau ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        appBar: AppBar(title: Text(_isEdit ? 'Rezept bearbeiten' : 'Neues Rezept')),
        body: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.only(bottom: 40),
            children: [
              const RecipeSection(title: 'Grundangaben'),
              _field(_name, 'Name', validator: (value) =>
                  (value == null || value.trim().isEmpty) ? 'Name fehlt' : null),
              _field(_description, 'Beschreibung (optional)', maxLines: 3),

              const RecipeSection(title: 'Kategorie'),
              _chips(
                recipeCategories,
                selected: {_category},
                onTap: (value) => setState(() => _category = value),
              ),

              const RecipeSection(title: 'Aufwand'),
              _chips(
                recipeDifficulties,
                selected: {_difficulty},
                onTap: (value) => setState(() => _difficulty = value),
              ),

              const RecipeSection(title: 'Zeiten und Portionen'),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(
                  children: [
                    Expanded(child: _number(_prep, 'Vorbereiten (Min)')),
                    const SizedBox(width: 12),
                    Expanded(child: _number(_cook, 'Kochen (Min)')),
                    const SizedBox(width: 12),
                    Expanded(child: _number(_servings, 'Portionen')),
                  ],
                ),
              ),

              RecipeSection(
                title: 'Passt zu',
                action: _suitableFor.isEmpty ? 'aus der Kategorie' : null,
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(
                  'Leer lassen ist in Ordnung - dann leitet der Server die '
                  'Mahlzeiten aus der Kategorie ab.',
                  style: KineticTheme.label,
                ),
              ),
              const SizedBox(height: 10),
              _chips(
                MealType.values.map((t) => t.label).toList(),
                selected: _suitableFor.map((t) => t.label).toSet(),
                onTap: (label) {
                  final type =
                      MealType.values.firstWhere((t) => t.label == label);
                  setState(() {
                    _suitableFor.contains(type)
                        ? _suitableFor.remove(type)
                        : _suitableFor.add(type);
                  });
                },
              ),

              RecipeSection(
                title: 'Zutaten',
                action: 'Zeilen einfügen…',
                onAction: _pasteIngredients,
              ),
              for (var i = 0; i < _ingredients.length; i++)
                _ingredientRow(i),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
                child: TextButton.icon(
                  onPressed: () =>
                      setState(() => _ingredients.add(_IngredientRow.empty())),
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('Zutat'),
                  style: TextButton.styleFrom(foregroundColor: KineticTheme.primary),
                ),
              ),

              const RecipeSection(title: 'Zubereitung'),
              for (var i = 0; i < _steps.length; i++) _stepRow(i),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
                child: TextButton.icon(
                  onPressed: () =>
                      setState(() => _steps.add(TextEditingController())),
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('Schritt'),
                  style: TextButton.styleFrom(foregroundColor: KineticTheme.primary),
                ),
              ),

              const RecipeSection(title: 'Nährwerte je Portion (optional)'),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(
                  children: [
                    Expanded(child: _number(_calories, 'kcal')),
                    const SizedBox(width: 8),
                    Expanded(child: _number(_protein, 'Eiweiß', decimal: true)),
                    const SizedBox(width: 8),
                    Expanded(child: _number(_carbs, 'KH', decimal: true)),
                    const SizedBox(width: 8),
                    Expanded(child: _number(_fat, 'Fett', decimal: true)),
                  ],
                ),
              ),

              const RecipeSection(title: 'Sonstiges'),
              // Kein Upload: es gibt keinen, und einen anzudeuten wäre gelogen.
              _field(_imageUrl, 'Bild-Adresse (optional)'),
              _field(_tags, 'Tags, mit Komma getrennt (optional)'),
              _field(_notes, 'Notiz (optional)', maxLines: 3),

              const SizedBox(height: 28),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: FilledButton(
                  onPressed: _saving ? null : _save,
                  child: Text(_saving ? 'Wird gespeichert…' : 'Speichern'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _field(
    TextEditingController controller,
    String hint, {
    int maxLines = 1,
    String? Function(String?)? validator,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
      child: TextFormField(
        controller: controller,
        maxLines: maxLines,
        validator: validator,
        textCapitalization: TextCapitalization.sentences,
        style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
        decoration: InputDecoration(hintText: hint),
      ),
    );
  }

  Widget _number(TextEditingController controller, String label,
      {bool decimal = false}) {
    return TextFormField(
      controller: controller,
      keyboardType: TextInputType.numberWithOptions(decimal: decimal),
      inputFormatters: [
        FilteringTextInputFormatter.allow(decimal ? RegExp(r'[\d.,]') : RegExp(r'\d')),
      ],
      style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: KineticTheme.label,
        isDense: true,
      ),
    );
  }

  Widget _chips(
    List<String> options, {
    required Set<String> selected,
    required ValueChanged<String> onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final option in options)
            ChoiceChip(
              label: Text(option),
              selected: selected.contains(option),
              onSelected: (_) => onTap(option),
            ),
        ],
      ),
    );
  }

  Widget _ingredientRow(int index) {
    final row = _ingredients[index];

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 8, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          SizedBox(width: 58, child: _small(row.amount, 'Menge', decimal: true)),
          const SizedBox(width: 8),
          SizedBox(width: 62, child: _small(row.unit, 'Einheit')),
          const SizedBox(width: 8),
          Expanded(flex: 3, child: _small(row.name, 'Zutat')),
          const SizedBox(width: 8),
          Expanded(flex: 2, child: _small(row.note, 'Zusatz')),
          IconButton(
            onPressed: _ingredients.length == 1
                ? null
                : () => setState(() => _ingredients.removeAt(index)..dispose()),
            icon: const Icon(Icons.close, size: 16),
            color: KineticTheme.textTertiary,
            tooltip: 'Zeile entfernen',
          ),
        ],
      ),
    );
  }

  Widget _small(TextEditingController controller, String hint,
      {bool decimal = false}) {
    return TextField(
      controller: controller,
      keyboardType: decimal
          ? const TextInputType.numberWithOptions(decimal: true)
          : TextInputType.text,
      inputFormatters: decimal
          ? [FilteringTextInputFormatter.allow(RegExp(r'[\d.,]'))]
          : null,
      style: KineticTheme.caption.copyWith(color: KineticTheme.textPrimary),
      decoration: InputDecoration(
        hintText: hint,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
      ),
    );
  }

  Widget _stepRow(int index) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 8, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 28,
            height: 28,
            margin: const EdgeInsets.only(top: 8),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              border: Border.all(color: KineticTheme.divider),
              shape: BoxShape.circle,
            ),
            child: Text('${index + 1}', style: KineticTheme.label),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: _steps[index],
              maxLines: null,
              textCapitalization: TextCapitalization.sentences,
              style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
              decoration: const InputDecoration(hintText: 'Was ist zu tun?'),
            ),
          ),
          IconButton(
            onPressed: _steps.length == 1
                ? null
                : () => setState(() => _steps.removeAt(index).dispose()),
            icon: const Icon(Icons.close, size: 16),
            color: KineticTheme.textTertiary,
            tooltip: 'Schritt entfernen',
          ),
        ],
      ),
    );
  }
}

/// Die vier Eingabefelder einer Zutatenzeile, zusammengehalten.
class _IngredientRow {
  _IngredientRow({
    required this.amount,
    required this.unit,
    required this.name,
    required this.note,
    this.id,
  });

  factory _IngredientRow.empty() => _IngredientRow(
        amount: TextEditingController(),
        unit: TextEditingController(),
        name: TextEditingController(),
        note: TextEditingController(),
      );

  factory _IngredientRow.from(RecipeIngredient ingredient) => _IngredientRow(
        id: ingredient.id,
        amount: TextEditingController(
          text: ingredient.amount == null
              ? ''
              // Ganze Zahlen ohne ".0" - "400" statt "400.0".
              : (ingredient.amount! % 1 == 0
                  ? ingredient.amount!.toInt().toString()
                  : ingredient.amount.toString()),
        ),
        unit: TextEditingController(text: ingredient.unit ?? ''),
        name: TextEditingController(text: ingredient.name),
        note: TextEditingController(text: ingredient.note ?? ''),
      );

  final int? id;
  final TextEditingController amount;
  final TextEditingController unit;
  final TextEditingController name;
  final TextEditingController note;

  bool get isEmpty =>
      amount.text.trim().isEmpty &&
      unit.text.trim().isEmpty &&
      name.text.trim().isEmpty &&
      note.text.trim().isEmpty;

  RecipeIngredient toIngredient() => RecipeIngredient(
        id: id,
        amount: double.tryParse(amount.text.trim().replaceAll(',', '.')),
        unit: unit.text.trim().isEmpty ? null : unit.text.trim(),
        name: name.text.trim(),
        note: note.text.trim().isEmpty ? null : note.text.trim(),
      );

  void dispose() {
    amount.dispose();
    unit.dispose();
    name.dispose();
    note.dispose();
  }
}
