import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/study_provider.dart';
import '../../models/study_note.dart';

class StudyNoteEditorPage extends StatefulWidget {
  final int noteId;
  const StudyNoteEditorPage({super.key, required this.noteId});

  @override
  State<StudyNoteEditorPage> createState() => _StudyNoteEditorPageState();
}

class _StudyNoteEditorPageState extends State<StudyNoteEditorPage> {
  late TextEditingController _titleCtrl;
  late TextEditingController _contentCtrl;
  StudyNote? _note;
  bool _dirty = false;

  @override
  void initState() {
    super.initState();
    final provider = context.read<StudyProvider>();
    _note = provider.notes.firstWhere(
      (n) => n.id == widget.noteId,
      orElse: () => StudyNote(title: '', content: ''),
    );
    _titleCtrl = TextEditingController(text: _note?.title ?? '');
    _contentCtrl = TextEditingController(text: _note?.content ?? '');
    _titleCtrl.addListener(_onChanged);
    _contentCtrl.addListener(_onChanged);
  }

  void _onChanged() => setState(() => _dirty = true);

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
    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () async {
            if (_dirty) await _save();
            if (mounted) Navigator.pop(context);
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
            // Block editor hint
            Container(
              margin: const EdgeInsets.only(bottom: 16),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFF3B82F6).withOpacity(0.08),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(
                    color: const Color(0xFF3B82F6).withOpacity(0.2)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.tips_and_updates_outlined,
                      size: 16, color: Color(0xFF3B82F6)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Tipp: Nutze # für Überschriften, - für Listen, [] für Todos, > für Callouts',
                      style: TextStyle(
                          fontSize: 12,
                          color: const Color(0xFF3B82F6).withOpacity(0.8)),
                    ),
                  ),
                ],
              ),
            ),
            // Rich content renderer / editor
            _BlockRenderer(
              content: _contentCtrl.text,
              controller: _contentCtrl,
            ),
          ],
        ),
      ),
    );
  }
}

// ── Block Renderer: renders markdown-like blocks with editing ─────────────────

class _BlockRenderer extends StatelessWidget {
  final String content;
  final TextEditingController controller;

  const _BlockRenderer({required this.content, required this.controller});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lines = content.split('\n');
    final widgets = <Widget>[];

    for (int i = 0; i < lines.length; i++) {
      final line = lines[i];
      if (line.startsWith('# ')) {
        widgets.add(_BlockHeading(text: line.substring(2), level: 1));
      } else if (line.startsWith('## ')) {
        widgets.add(_BlockHeading(text: line.substring(3), level: 2));
      } else if (line.startsWith('### ')) {
        widgets.add(_BlockHeading(text: line.substring(4), level: 3));
      } else if (line.startsWith('- [x] ')) {
        widgets.add(_BlockTodo(text: line.substring(6), done: true));
      } else if (line.startsWith('- [] ') || line.startsWith('- [ ] ')) {
        final text = line.startsWith('- [ ] ')
            ? line.substring(6)
            : line.substring(5);
        widgets.add(_BlockTodo(text: text, done: false));
      } else if (line.startsWith('- ') || line.startsWith('* ')) {
        widgets.add(_BlockBullet(text: line.substring(2)));
      } else if (line.startsWith('> ')) {
        widgets.add(_BlockCallout(text: line.substring(2)));
      } else if (line.startsWith('**') && line.endsWith('**') && line.length > 4) {
        widgets.add(_BlockBold(text: line.substring(2, line.length - 2)));
      } else if (line == '---' || line == '───') {
        widgets.add(const Padding(
          padding: EdgeInsets.symmetric(vertical: 8),
          child: Divider(),
        ));
      } else if (line.isEmpty) {
        widgets.add(const SizedBox(height: 8));
      } else {
        widgets.add(Padding(
          padding: const EdgeInsets.only(bottom: 4),
          child: Text(line, style: theme.textTheme.bodyMedium),
        ));
      }
    }

    // Append the raw editor below for editing
    widgets.add(const SizedBox(height: 16));
    widgets.add(const Divider());
    widgets.add(const SizedBox(height: 8));
    widgets.add(Text('Bearbeiten:', style: theme.textTheme.labelSmall?.copyWith(color: Colors.grey)));
    widgets.add(const SizedBox(height: 4));
    widgets.add(TextField(
      controller: controller,
      maxLines: null,
      style: theme.textTheme.bodyMedium?.copyWith(
        fontFamily: 'monospace',
        fontSize: 13,
      ),
      decoration: InputDecoration(
        hintText: 'Schreibe hier... (# Überschrift, - Liste, - [] Todo, > Callout)',
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: Colors.grey.shade300),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: Colors.grey.shade300),
        ),
        filled: true,
        fillColor: theme.colorScheme.surfaceVariant.withOpacity(0.3),
        contentPadding: const EdgeInsets.all(16),
      ),
    ));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: widgets,
    );
  }
}

class _BlockHeading extends StatelessWidget {
  final String text;
  final int level;
  const _BlockHeading({required this.text, required this.level});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = level == 1
        ? theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)
        : level == 2
            ? theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)
            : theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600);
    return Padding(
      padding: EdgeInsets.only(top: level == 1 ? 16 : 12, bottom: 6),
      child: Text(text, style: style),
    );
  }
}

class _BlockTodo extends StatelessWidget {
  final String text;
  final bool done;
  const _BlockTodo({required this.text, required this.done});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(done ? Icons.check_box : Icons.check_box_outline_blank,
              size: 20,
              color: done ? const Color(0xFF10B981) : Colors.grey),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                decoration: done ? TextDecoration.lineThrough : null,
                color: done ? Colors.grey : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BlockBullet extends StatelessWidget {
  final String text;
  const _BlockBullet({required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 6),
            child: Icon(Icons.circle, size: 6, color: Colors.grey),
          ),
          const SizedBox(width: 10),
          Expanded(child: Text(text)),
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
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 6),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.amber.withOpacity(0.12),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.amber.withOpacity(0.4)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('💡', style: TextStyle(fontSize: 18)),
          const SizedBox(width: 10),
          Expanded(
              child: Text(text,
                  style: const TextStyle(fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}

class _BlockBold extends StatelessWidget {
  final String text;
  const _BlockBold({required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text(text,
          style: Theme.of(context)
              .textTheme
              .bodyMedium
              ?.copyWith(fontWeight: FontWeight.bold)),
    );
  }
}
