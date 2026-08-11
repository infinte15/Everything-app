import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../models/recipe_import_preview.dart';
import '../../../providers/recipe_provider.dart';
import '../../../theme/kinetic_theme.dart';
import 'recipe_import_preview_page.dart';

/// Rezept importieren - eine Seite mit zwei Wegen.
///
/// Adresse und eingefügter Text sind zwei Wege in dieselbe Sache und gehören
/// nicht in zwei Menüpunkte.
class RecipeImportPage extends StatefulWidget {
  const RecipeImportPage({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  State<RecipeImportPage> createState() => _RecipeImportPageState();
}

class _RecipeImportPageState extends State<RecipeImportPage> {
  late int _tab = widget.initialTab;
  final _urlController = TextEditingController();
  final _textController = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _prefillFromClipboard();
  }

  @override
  void dispose() {
    _urlController.dispose();
    _textController.dispose();
    super.dispose();
  }

  /// Liegt ein chefkoch-Link in der Zwischenablage, steht er gleich im Feld -
  /// von dort kommt er in neun von zehn Fällen.
  Future<void> _prefillFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim();
    if (text == null || !mounted) return;
    if (text.contains('chefkoch.de') && text.startsWith('http')) {
      setState(() {
        _urlController.text = text;
        _tab = 0;
      });
    }
  }

  Future<void> _read() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    final provider = context.read<RecipeProvider>();
    try {
      final RecipeImportPreview preview = _tab == 0
          ? await provider.importFromUrl(_urlController.text.trim())
          : await provider.importFromText(_textController.text);

      if (!mounted) return;
      setState(() => _loading = false);

      final saved = await Navigator.of(context).push<bool>(
        MaterialPageRoute(
          builder: (_) => RecipeImportPreviewPage(preview: preview),
        ),
      );
      if (saved == true && mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      // Die Meldung des Servers unverändert - sie ist für Nutzer geschrieben.
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  bool get _canRead => _tab == 0
      ? _urlController.text.trim().isNotEmpty
      : _textController.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        appBar: AppBar(title: const Text('Rezept importieren')),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            SegmentedButton<int>(
              segments: const [
                ButtonSegment(value: 0, label: Text('chefkoch.de')),
                ButtonSegment(value: 1, label: Text('Text einfügen')),
              ],
              selected: {_tab},
              onSelectionChanged: (selection) => setState(() {
                _tab = selection.first;
                _error = null;
              }),
              showSelectedIcon: false,
              style: SegmentedButton.styleFrom(
                backgroundColor: KineticTheme.surfaceElevated,
                foregroundColor: KineticTheme.textSecondary,
                selectedBackgroundColor:
                    KineticTheme.primary.withValues(alpha: 0.2),
                selectedForegroundColor: KineticTheme.primary,
                side: const BorderSide(color: KineticTheme.divider),
              ),
            ),
            const SizedBox(height: 24),
            if (_tab == 0) ..._urlSection() else ..._textSection(),
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(
                _error!,
                style: KineticTheme.caption.copyWith(color: KineticTheme.danger),
              ),
            ],
            const SizedBox(height: 28),
            FilledButton(
              onPressed: !_canRead || _loading ? null : _read,
              child: Text(_loading ? 'Wird gelesen…' : 'Rezept lesen'),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _urlSection() => [
        TextField(
          controller: _urlController,
          keyboardType: TextInputType.url,
          autocorrect: false,
          onChanged: (_) => setState(() {}),
          style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
          decoration: const InputDecoration(
            hintText: 'https://www.chefkoch.de/rezepte/…',
          ),
        ),
        const SizedBox(height: 10),
        Text(
          'Es werden nur Adressen von chefkoch.de gelesen.',
          style: KineticTheme.label,
        ),
      ];

  List<Widget> _textSection() => [
        TextField(
          controller: _textController,
          maxLines: 12,
          minLines: 12,
          onChanged: (_) => setState(() {}),
          style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
          decoration: const InputDecoration(
            hintText: 'Bildunterschrift aus Instagram hier einfügen',
          ),
        ),
        const SizedBox(height: 10),
        // Keine Entschuldigung, sondern die Erklärung, warum man kopieren muss.
        Text(
          'Instagram wird nicht abgerufen — gelesen wird nur der Text, den du '
          'einfügst. Überschriften wie "Zutaten:" und "Zubereitung:" helfen, '
          'nötig sind sie nicht.',
          style: KineticTheme.label,
        ),
      ];
}
