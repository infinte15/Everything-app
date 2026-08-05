import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/study_provider.dart';
import '../../models/study_note.dart';
import '../../utils/note_markup.dart';

class StudyNoteEditorPage extends StatefulWidget {
  final int noteId;
  const StudyNoteEditorPage({super.key, required this.noteId});

  @override
  State<StudyNoteEditorPage> createState() => _StudyNoteEditorPageState();
}

/// Vorschau oder Rohtext — nicht beides untereinander.
enum _EditorMode { preview, edit }

class _StudyNoteEditorPageState extends State<StudyNoteEditorPage> {
  late TextEditingController _titleCtrl;
  late TextEditingController _contentCtrl;
  StudyNote? _note;
  bool _dirty = false;
  _EditorMode _mode = _EditorMode.preview;

  @override
  void initState() {
    super.initState();
    final provider = context.read<StudyProvider>();
    // Frueher stand hier ein orElse mit einer leeren Notiz: eine unbekannte ID oeffnete
    // stillschweigend ein leeres Formular, dessen "Speichern" ins Leere lief. Jetzt bleibt
    // _note null und der Screen sagt, dass es die Seite nicht gibt.
    final matches = provider.notes.where((n) => n.id == widget.noteId);
    _note = matches.isEmpty ? null : matches.first;

    _titleCtrl = TextEditingController(text: _note?.title ?? '');
    _contentCtrl = TextEditingController(text: _note?.content ?? '');
    _titleCtrl.addListener(_onChanged);
    _contentCtrl.addListener(_onChanged);
  }

  /// Welche Aufklappblöcke offen stehen, geschlüsselt über ihre startLine.
  ///
  /// Bewusst **nur für diese Sitzung**: das zu speichern hieße, eine Markierung in den Inhalt
  /// zu schreiben — das machte aus dem Lesen eines Lernzettels einen Schreibvorgang. Und es
  /// wäre falsch herum: beim nächsten Öffnen sollen die Antworten wieder verborgen sein.
  final Set<int> _expandedToggles = {};

  void _onChanged() => setState(() => _dirty = true);

  /// Kippt die Checkbox in [lineIndex] und speichert sofort.
  ///
  /// Nur aus der Vorschau erreichbar: im Bearbeitenmodus stünde der Rohtext auf dem Schirm,
  /// und das Umschreiben des Controllers risse die Einfügemarke ans Ende.
  Future<void> _toggleTodo(int lineIndex) async {
    if (_note == null) return;

    final previous = _contentCtrl.text;
    final flipped = toggleTodoLine(previous, lineIndex);
    // null heisst: dort steht keine Checkbox (mehr). Lieber nichts tun, als die falsche
    // Zeile umzuschreiben.
    if (flipped == null) return;

    _contentCtrl.text = flipped;

    final ok = await context.read<StudyProvider>().updateNote(
          _note!.copyWith(title: _titleCtrl.text, content: flipped),
        );
    if (!mounted) return;

    if (!ok) {
      // updateNote laesst den Provider bei Misserfolg unveraendert — zurueckzunehmen ist
      // also genau dieser eine String.
      _contentCtrl.text = previous;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Haken konnte nicht gespeichert werden.')),
      );
      return;
    }
    setState(() => _dirty = false);
  }

  /// Fügt [snippet] an der Einfügemarke ein; [caretOffset] zählt ab dessen Anfang.
  void _insert(String snippet, {int caretOffset = 0}) {
    final sel = _contentCtrl.selection;
    final start = sel.start < 0 ? _contentCtrl.text.length : sel.start;
    final end = sel.end < 0 ? start : sel.end;

    _contentCtrl.value = TextEditingValue(
      text: _contentCtrl.text.replaceRange(start, end, snippet),
      selection: TextSelection.collapsed(offset: start + caretOffset),
    );
  }

  Future<void> _showInsertMenu() async {
    final entries = <({String label, String snippet, int caret})>[
      (label: 'Überschrift', snippet: '# ', caret: 2),
      (label: 'Unterüberschrift', snippet: '## ', caret: 3),
      (label: 'Aufzählung', snippet: '- ', caret: 2),
      (label: 'Nummerierte Liste', snippet: '1. ', caret: 3),
      (label: 'Todo', snippet: '- [ ] ', caret: 6),
      (label: 'Merksatz', snippet: '> ', caret: 2),
      (label: 'Ausklappbar (Frage/Antwort)',
          snippet: '\n$kToggleFence Frage\nAntwort\n$kToggleFence\n', caret: 5),
      (label: 'Codeblock', snippet: '\n$kCodeFence\n\n$kCodeFence\n', caret: 5),
      (label: 'Markiert', snippet: '====', caret: 2),
      (label: 'Trenner', snippet: '\n---\n', caret: 5),
    ];

    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const ListTile(
              title: Text('Einfügen', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            const Divider(height: 1),
            for (final e in entries)
              ListTile(
                dense: true,
                title: Text(e.label),
                onTap: () {
                  Navigator.pop(ctx);
                  _insert(e.snippet, caretOffset: e.caret);
                },
              ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _contentCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_note == null) return;
    final updated = _note!.copyWith(
      title: _titleCtrl.text,
      content: _contentCtrl.text,
    );
    await context.read<StudyProvider>().updateNote(updated);
    setState(() => _dirty = false);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Gespeichert'), duration: Duration(seconds: 1)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (_note == null) {
      return Scaffold(
        backgroundColor: theme.colorScheme.surface,
        appBar: AppBar(backgroundColor: Colors.transparent, elevation: 0),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.description_outlined, size: 56, color: Colors.grey),
              const SizedBox(height: 16),
              Text('Seite nicht gefunden', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                'Sie wurde vermutlich gelöscht.',
                style: theme.textTheme.bodyMedium?.copyWith(color: Colors.grey),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () async {
            if (_dirty) await _save();
            if (context.mounted) Navigator.pop(context);
          },
        ),
        actions: [
          if (_dirty)
            TextButton.icon(
              onPressed: _save,
              icon: const Icon(Icons.save_outlined),
              label: const Text('Speichern'),
            ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert),
            itemBuilder: (_) => [
              const PopupMenuItem(
                  value: 'favorite',
                  child: Row(children: [
                    Icon(Icons.star_outline),
                    SizedBox(width: 8),
                    Text('Favorit'),
                  ])),
            ],
            onSelected: (val) {
              if (val == 'favorite' && _note?.id != null) {
                context.read<StudyProvider>().toggleFavorite(_note!.id!);
              }
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Brotkrumen: der Pfad von der Wurzelseite hierher.
            _Breadcrumbs(noteId: widget.noteId),
            // Title
            TextField(
              controller: _titleCtrl,
              style: theme.textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
              decoration: const InputDecoration(
                hintText: 'Untitled',
                border: InputBorder.none,
                contentPadding: EdgeInsets.zero,
                filled: false,
              ),
            ),
            const SizedBox(height: 4),
            // Meta row
            if (_note?.courseName != null || _note?.category != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  children: [
                    if (_note?.courseName != null) ...[
                      const Icon(Icons.school_outlined, size: 14, color: Colors.grey),
                      const SizedBox(width: 4),
                      Text(_note!.courseName!,
                          style: const TextStyle(fontSize: 12, color: Colors.grey)),
                      const SizedBox(width: 12),
                    ],
                  ],
                ),
              ),
            const Divider(height: 24),
            // Vorschau ODER Rohtext. Vorher hing das TextField UNTER der gerenderten Vorschau,
            // sodass jeder Text zweimal auf dem Schirm stand.
            SegmentedButton<_EditorMode>(
              segments: const [
                ButtonSegment(
                  value: _EditorMode.preview,
                  label: Text('VORSCHAU'),
                  icon: Icon(Icons.visibility_outlined, size: 16),
                ),
                ButtonSegment(
                  value: _EditorMode.edit,
                  label: Text('BEARBEITEN'),
                  icon: Icon(Icons.edit_outlined, size: 16),
                ),
              ],
              selected: {_mode},
              showSelectedIcon: false,
              onSelectionChanged: (s) => setState(() => _mode = s.first),
            ),
            const SizedBox(height: 16),
            if (_mode == _EditorMode.preview)
              _BlockRenderer(
                blocks: parseBlocks(_contentCtrl.text),
                expanded: _expandedToggles,
                onToggleExpanded: (line) => setState(() {
                  _expandedToggles.contains(line)
                      ? _expandedToggles.remove(line)
                      : _expandedToggles.add(line);
                }),
                onToggleTodo: _toggleTodo,
              )
            else
              _RawEditor(controller: _contentCtrl, onInsert: _showInsertMenu),
          ],
        ),
      ),
    );
  }
}

// ── Brotkrumen ────────────────────────────────────────────────────────────────

class _Breadcrumbs extends StatelessWidget {
  final int noteId;
  const _Breadcrumbs({required this.noteId});

  @override
  Widget build(BuildContext context) {
    // Ohne die letzte Station: die steht direkt darunter als Titel.
    final path = context.watch<StudyProvider>().breadcrumbsFor(noteId);
    if (path.length < 2) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final ancestors = path.sublist(0, path.length - 1);

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          for (var i = 0; i < ancestors.length; i++) ...[
            InkWell(
              onTap: () => Navigator.of(context).pushReplacement(MaterialPageRoute(
                builder: (_) => StudyNoteEditorPage(noteId: ancestors[i].id!),
              )),
              child: Text(
                ancestors[i].title,
                style: theme.textTheme.labelMedium?.copyWith(color: Colors.grey),
              ),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4),
              child: Icon(Icons.chevron_right, size: 14, color: Colors.grey),
            ),
          ],
        ],
      ),
    );
  }
}

// ── Rohtext-Editor ────────────────────────────────────────────────────────────

class _RawEditor extends StatelessWidget {
  final TextEditingController controller;
  final Future<void> Function() onInsert;
  const _RawEditor({required this.controller, required this.onInsert});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                '# Überschrift · - Liste · 1. Nummer · - [ ] Todo · > Merksatz · '
                '??? Frage/Antwort ??? · ``` Code · **fett** · ==markiert==',
                style: theme.textTheme.labelSmall?.copyWith(color: Colors.grey),
              ),
            ),
            const SizedBox(width: 8),
            // Ein sichtbarer Knopf statt nur einer Tastenkombination: das Einfügemenü muss
            // auffindbar sein, sonst kennt es niemand.
            OutlinedButton.icon(
              onPressed: onInsert,
              icon: const Icon(Icons.add, size: 14),
              label: const Text('EINFÜGEN'),
              style: OutlinedButton.styleFrom(
                visualDensity: VisualDensity.compact,
                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                textStyle: theme.textTheme.labelSmall?.copyWith(fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: controller,
          maxLines: null,
          minLines: 12,
          autofocus: true,
          style: theme.textTheme.bodyMedium?.copyWith(
            fontFamily: 'monospace',
            fontSize: 13,
          ),
          decoration: InputDecoration(
            hintText: 'Schreibe hier...',
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: Colors.grey.shade300),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: Colors.grey.shade300),
            ),
            filled: true,
            fillColor: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
            contentPadding: const EdgeInsets.all(16),
          ),
        ),
      ],
    );
  }
}

// ── Blockdarstellung ──────────────────────────────────────────────────────────
//
// Der Parser liegt in lib/utils/note_markup.dart und ist eine reine Funktion auf dem
// Inhaltsstring — hier wird nur gezeichnet. Jeder Block kennt seine startLine; darüber
// weiß eine angetippte Checkbox, welche Zeile sie umschreiben muss.

class _BlockRenderer extends StatelessWidget {
  final List<NoteBlock> blocks;

  /// Nur im Vorschaumodus gesetzt. Im Bearbeitenmodus stünde der Rohtext auf dem Schirm, und
  /// ein Schreibzugriff auf den Controller risse die Einfügemarke ans Ende.
  final void Function(int startLine)? onToggleTodo;

  final Set<int> expanded;
  final void Function(int startLine) onToggleExpanded;

  const _BlockRenderer({
    required this.blocks,
    required this.expanded,
    required this.onToggleExpanded,
    this.onToggleTodo,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: blocks.map((b) => _build(context, b)).toList(),
    );
  }

  Widget _build(BuildContext context, NoteBlock block) {
    return switch (block) {
      HeadingBlock b => _BlockHeading(text: b.text, level: b.level),
      TodoBlock b => _BlockTodo(
          text: b.text,
          done: b.done,
          onTap: onToggleTodo == null ? null : () => onToggleTodo!(b.startLine),
        ),
      BulletBlock b => _BlockBullet(text: b.text),
      NumberedBlock b => _BlockNumbered(text: b.text, marker: b.marker),
      CalloutBlock b => _BlockCallout(text: b.text),
      CodeBlock b => _BlockCode(lines: b.lines),
      ToggleBlock b => _BlockToggle(
          question: b.question,
          body: b.body,
          isExpanded: expanded.contains(b.startLine),
          onTap: () => onToggleExpanded(b.startLine),
          renderBody: (ctx) => _BlockRenderer(
            blocks: b.body,
            expanded: expanded,
            onToggleExpanded: onToggleExpanded,
            onToggleTodo: onToggleTodo,
          ),
        ),
      DividerBlock _ => const Divider(height: 24),
      SpacerBlock _ => const SizedBox(height: 8),
      ParagraphBlock b => _BlockParagraph(text: b.text),
    };
  }
}

/// Formatierung innerhalb einer Zeile — einmal definiert, von jedem textführenden Block genutzt.
Widget _richLine(BuildContext context, String text, TextStyle style) {
  final theme = Theme.of(context);
  return Text.rich(TextSpan(
    children: inlineSpans(
      text,
      style,
      codeBackground: theme.colorScheme.surfaceContainerHighest,
      highlightBackground: const Color(0xFFFF9F0A).withValues(alpha: 0.35),
    ),
  ));
}

class _BlockHeading extends StatelessWidget {
  final String text;
  final int level;
  const _BlockHeading({required this.text, required this.level});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = switch (level) {
      1 => theme.textTheme.headlineSmall,
      2 => theme.textTheme.titleLarge,
      _ => theme.textTheme.titleMedium,
    };
    return Padding(
      padding: EdgeInsets.only(top: level == 1 ? 20 : 16, bottom: 8),
      child: _richLine(context, text,
          style!.copyWith(fontWeight: FontWeight.bold, color: theme.colorScheme.onSurface)),
    );
  }
}

class _BlockTodo extends StatelessWidget {
  final String text;
  final bool done;
  final VoidCallback? onTap;
  const _BlockTodo({required this.text, required this.done, this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Die ganze Zeile ist Trefferfläche, nicht nur das 20px-Symbol.
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              done ? Icons.check_box : Icons.check_box_outline_blank,
              size: 20,
              color: done ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _richLine(
                context,
                text,
                theme.textTheme.bodyMedium!.copyWith(
                  color: done
                      ? theme.colorScheme.onSurfaceVariant
                      : theme.colorScheme.onSurface,
                  decoration: done ? TextDecoration.lineThrough : null,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BlockBullet extends StatelessWidget {
  final String text;
  const _BlockBullet({required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 6, right: 10, left: 4),
            child: Container(
              width: 5,
              height: 5,
              decoration: BoxDecoration(
                color: theme.colorScheme.onSurfaceVariant,
                shape: BoxShape.circle,
              ),
            ),
          ),
          Expanded(child: _richLine(context, text, theme.textTheme.bodyMedium!)),
        ],
      ),
    );
  }
}

class _BlockNumbered extends StatelessWidget {
  final String text;
  final String marker;
  const _BlockNumbered({required this.text, required this.marker});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Feste Breite, damit die Textkanten untereinander stehen.
          SizedBox(
            width: 26,
            child: Text('$marker.',
                style: theme.textTheme.bodyMedium
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Expanded(child: _richLine(context, text, theme.textTheme.bodyMedium!)),
        ],
      ),
    );
  }
}

class _BlockCallout extends StatelessWidget {
  final String text;
  const _BlockCallout({required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        border: Border(left: BorderSide(color: theme.colorScheme.primary, width: 3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('💡', style: TextStyle(fontSize: 16)),
          const SizedBox(width: 10),
          Expanded(child: _richLine(context, text, theme.textTheme.bodyMedium!)),
        ],
      ),
    );
  }
}

class _BlockCode extends StatelessWidget {
  final List<String> lines;
  const _BlockCode({required this.lines});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.all(12),
      color: theme.colorScheme.surfaceContainerHighest,
      // Bewusst kein inlineSpans: in einem Codeblock ist ** genau das, was dasteht.
      child: SelectableText(
        lines.join('\n'),
        style: theme.textTheme.bodySmall?.copyWith(
          fontFamily: 'monospace',
          color: theme.colorScheme.onSurface,
        ),
      ),
    );
  }
}

/// Frage sichtbar, Antwort verborgen — damit sich ein Lernzettel abfragen lässt.
class _BlockToggle extends StatelessWidget {
  final String question;
  final List<NoteBlock> body;
  final bool isExpanded;
  final VoidCallback onTap;
  final WidgetBuilder renderBody;

  const _BlockToggle({
    required this.question,
    required this.body,
    required this.isExpanded,
    required this.onTap,
    required this.renderBody,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 6),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    isExpanded ? Icons.expand_more : Icons.chevron_right,
                    size: 18,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _richLine(
                      context,
                      question,
                      theme.textTheme.bodyMedium!.copyWith(fontWeight: FontWeight.bold),
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (isExpanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(38, 0, 12, 12),
              child: renderBody(context),
            ),
        ],
      ),
    );
  }
}

class _BlockParagraph extends StatelessWidget {
  final String text;
  const _BlockParagraph({required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: _richLine(context, text,
          theme.textTheme.bodyMedium!.copyWith(height: 1.5)),
    );
  }
}
