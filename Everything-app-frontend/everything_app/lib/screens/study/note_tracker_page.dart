import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/study_provider.dart';
import '../../models/study_note.dart';
import 'study_note_editor_page.dart';

class NoteTrackerPage extends StatelessWidget {
  const NoteTrackerPage({super.key});

  static const _columns = [
    _Column('📋', 'To Do', 'todo', Color(0xFF6366F1)),
    _Column('⚡', 'In Arbeit', 'in_progress', Color(0xFFF59E0B)),
    _Column('✅', 'Fertig', 'done', Color(0xFF10B981)),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();

    final groups = {
      'todo': provider.todoNotes,
      'in_progress': provider.inProgressNotes,
      'done': provider.doneNotes,
    };

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
            child: Row(
              children: [
                const Text('📊', style: TextStyle(fontSize: 28)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text('Notiz-Tracker',
                      style: theme.textTheme.headlineSmall
                          ?.copyWith(fontWeight: FontWeight.bold)),
                ),
                Text(
                  '${provider.notes.length} Seiten',
                  style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.all(16),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: _columns.map((col) {
                  final notes = groups[col.status] ?? [];
                  return _KanbanColumn(
                    col: col,
                    notes: notes,
                    onTap: (note) => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) =>
                            StudyNoteEditorPage(noteId: note.id!),
                      ),
                    ),
                    onStatusChange: (note, newStatus) {
                      context
                          .read<StudyProvider>()
                          .updateNoteStatus(note.id!, newStatus);
                    },
                    allColumns: _columns,
                  );
                }).toList(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Column {
  final String emoji;
  final String label;
  final String status;
  final Color color;
  const _Column(this.emoji, this.label, this.status, this.color);
}

class _KanbanColumn extends StatelessWidget {
  final _Column col;
  final List<StudyNote> notes;
  final void Function(StudyNote) onTap;
  final void Function(StudyNote, String) onStatusChange;
  final List<_Column> allColumns;

  const _KanbanColumn({
    required this.col,
    required this.notes,
    required this.onTap,
    required this.onStatusChange,
    required this.allColumns,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: 280,
      margin: const EdgeInsets.only(right: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Column header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: col.color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: col.color.withValues(alpha: 0.25)),
            ),
            child: Row(
              children: [
                Text(col.emoji, style: const TextStyle(fontSize: 18)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(col.label,
                      style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold, color: col.color)),
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: col.color.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text('${notes.length}',
                      style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: col.color)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          // Cards
          ...notes.map(
            (note) => _NoteCard(
              note: note,
              accentColor: col.color,
              onTap: () => onTap(note),
              onStatusChange: (s) => onStatusChange(note, s),
              allColumns: allColumns,
              currentStatus: col.status,
            ),
          ),
          if (notes.isEmpty)
            Container(
              height: 80,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                    color: theme.colorScheme.outline.withValues(alpha: 0.15),
                    style: BorderStyle.solid),
              ),
              child: Text('Keine Einträge',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: Colors.grey)),
            ),
        ],
      ),
    );
  }
}

class _NoteCard extends StatelessWidget {
  final StudyNote note;
  final Color accentColor;
  final VoidCallback onTap;
  final void Function(String) onStatusChange;
  final List<_Column> allColumns;
  final String currentStatus;

  const _NoteCard({
    required this.note,
    required this.accentColor,
    required this.onTap,
    required this.onStatusChange,
    required this.allColumns,
    required this.currentStatus,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tags = note.tagList
        .where((t) => !t.startsWith('status:'))
        .toList();

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
              color: theme.colorScheme.outline.withValues(alpha: 0.15)),
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.05),
                blurRadius: 8,
                offset: const Offset(0, 2)),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.article_outlined, size: 16, color: Colors.grey),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(note.title,
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(fontWeight: FontWeight.w600),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis),
                ),
                // Move to other column menu
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_horiz, size: 16, color: Colors.grey),
                  itemBuilder: (_) => allColumns
                      .where((c) => c.status != currentStatus)
                      .map((c) => PopupMenuItem(
                            value: c.status,
                            child: Row(children: [
                              Text(c.emoji),
                              const SizedBox(width: 8),
                              Text('→ ${c.label}'),
                            ]),
                          ))
                      .toList(),
                  onSelected: onStatusChange,
                ),
              ],
            ),
            if (note.courseName != null) ...[
              const SizedBox(height: 6),
              Row(children: [
                const Icon(Icons.school_outlined, size: 12, color: Colors.grey),
                const SizedBox(width: 4),
                Text(note.courseName!,
                    style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ]),
            ],
            if (tags.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 4,
                runSpacing: 4,
                children: tags.map((t) {
                  return Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: accentColor.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(t,
                        style: TextStyle(
                            fontSize: 10,
                            color: accentColor,
                            fontWeight: FontWeight.w500)),
                  );
                }).toList(),
              ),
            ],
            if (note.createdAt != null) ...[
              const SizedBox(height: 8),
              Text(
                _relativeDate(note.createdAt!),
                style: const TextStyle(fontSize: 10, color: Colors.grey),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _relativeDate(DateTime d) {
    final diff = DateTime.now().difference(d);
    if (diff.inDays == 0) return 'Heute';
    if (diff.inDays == 1) return 'Gestern';
    return 'vor ${diff.inDays} Tagen';
  }
}
