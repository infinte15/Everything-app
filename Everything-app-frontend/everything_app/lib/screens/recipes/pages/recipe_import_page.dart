import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../models/recipe_import_preview.dart';
import '../../../providers/recipe_provider.dart';
import '../../../services/recipe_service.dart';
import '../../../theme/kinetic_theme.dart';
import 'recipe_import_preview_page.dart';

/// Rezept importieren - eine Seite mit zwei Wegen.
///
/// Adresse und eingefügter Text sind zwei Wege in dieselbe Sache und gehören
/// nicht in zwei Menüpunkte. Der zweite ist nicht der schlechtere: bei Instagram
/// und hinter Bezahlschranken ist er der einzige, der geht, und der Server
/// schickt einen von dort aus hierher zurück.
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

  /// Ob [_error] eine Anweisung ist und keine Panne.
  bool _errorIsHint = false;

  /// Die Adresse, zu der der eingefügte Text gehört.
  ///
  /// Gesetzt, wenn der Server eine Seite nicht lesen durfte und auf den
  /// Text-Weg verwiesen hat. Sie wird nicht abgerufen, sondern nur als Herkunft
  /// mitgespeichert - sonst steht das Rezept später ohne Quelle da.
  String? _pendingSourceUrl;

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

  /// Liegt ein Link in der Zwischenablage, steht er gleich im Feld - von dort
  /// kommt er in neun von zehn Fällen.
  Future<void> _prefillFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim();
    if (text == null || !mounted) return;
    // Keine Prüfung mehr auf eine bestimmte Seite - gelesen wird jede. Nur noch
    // die Frage, ob das überhaupt eine Adresse ist.
    if ((text.startsWith('http://') || text.startsWith('https://')) &&
        text.length < 2000 &&
        !text.contains(RegExp(r'\s'))) {
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
      _errorIsHint = false;
    });

    final provider = context.read<RecipeProvider>();
    try {
      final RecipeImportPreview preview = _tab == 0
          ? await provider.importFromUrl(_urlController.text.trim())
          : await provider.importFromText(
              _textController.text,
              sourceName: _hostOf(_pendingSourceUrl),
              sourceUrl: _pendingSourceUrl,
            );

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
      _handleError(e);
    }
  }

  /// Der Server sagt mit einer Kennung, wenn die Oberfläche etwas tun soll.
  ///
  /// Bei Instagram ist das der Normalfall und keine Panne: der Beitrag ist ohne
  /// Anmeldung nicht lesbar. Also in den Text-Tab wechseln, die Adresse merken -
  /// und die Meldung nicht rot anzeigen, denn sie ist eine Anweisung.
  void _handleError(Object e) {
    final needsCaption = e is RecipeException &&
        e.code == RecipeException.instagramPasteCaption;

    setState(() {
      // Die Meldung des Servers unverändert - sie ist für Nutzer geschrieben.
      _error = e.toString();
      _errorIsHint = needsCaption;
      _loading = false;
      if (needsCaption) {
        _pendingSourceUrl = _urlController.text.trim();
        _tab = 1;
      }
    });
  }

  /// "https://www.instagram.com/p/Abc/" → "instagram.com"
  String? _hostOf(String? url) {
    if (url == null) return null;
    final host = Uri.tryParse(url)?.host;
    if (host == null || host.isEmpty) return null;
    return host.startsWith('www.') ? host.substring(4) : host;
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
                ButtonSegment(value: 0, label: Text('Adresse')),
                ButtonSegment(value: 1, label: Text('Text einfügen')),
              ],
              selected: {_tab},
              onSelectionChanged: (selection) => setState(() {
                _tab = selection.first;
                _error = null;
                _errorIsHint = false;
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
                style: KineticTheme.caption.copyWith(
                  color: _errorIsHint
                      ? KineticTheme.textSecondary
                      : KineticTheme.danger,
                ),
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
            hintText: 'https://…',
          ),
        ),
        const SizedBox(height: 10),
        Text(
          'Adresse einer Rezeptseite einfügen - die meisten Kochseiten und Blogs '
          'lassen sich lesen. Instagram wird versucht; klappt es nicht, kannst du '
          'die Bildunterschrift einfügen.',
          style: KineticTheme.label,
        ),
      ];

  List<Widget> _textSection() => [
        if (_pendingSourceUrl != null) ...[
          _sourceChip(_pendingSourceUrl!),
          const SizedBox(height: 12),
        ],
        TextField(
          controller: _textController,
          maxLines: 12,
          minLines: 12,
          onChanged: (_) => setState(() {}),
          style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
          decoration: const InputDecoration(
            hintText: 'Bildunterschrift oder Rezepttext hier einfügen',
          ),
        ),
        const SizedBox(height: 10),
        Text(
          'Gelesen wird nur der Text, den du einfügst. Überschriften wie '
          '"Zutaten:" und "Zubereitung:" helfen, nötig sind sie nicht.',
          style: KineticTheme.label,
        ),
      ];

  /// Zeigt die gemerkte Herkunft - und lässt sie wieder loswerden.
  Widget _sourceChip(String url) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 6, 8),
      decoration: BoxDecoration(
        color: KineticTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: KineticTheme.divider),
      ),
      child: Row(
        children: [
          const Icon(Icons.link, size: 16, color: KineticTheme.textSecondary),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Herkunft: $url',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: KineticTheme.label,
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 16),
            color: KineticTheme.textSecondary,
            tooltip: 'Herkunft verwerfen',
            onPressed: () => setState(() => _pendingSourceUrl = null),
          ),
        ],
      ),
    );
  }
}
