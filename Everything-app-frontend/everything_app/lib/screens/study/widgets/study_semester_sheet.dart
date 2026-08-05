import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/study_semester.dart';
import '../../../providers/study_provider.dart';

/// Semesterverwaltung: anlegen, umbenennen, laufendes Semester setzen, löschen, sortieren.
///
/// Die Semester waren bisher nur die distinkten Freitexte der Module — nicht sortierbar,
/// nicht umbenennbar, und ein Tippfehler erzeugte still ein zweites „Semester".
class StudySemesterSheet extends StatelessWidget {
  const StudySemesterSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF1A1A1A),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
        child: const StudySemesterSheet(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final semesters = provider.semesters;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'SEMESTER',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: 0.5,
              ),
            ),
            const SizedBox(height: 16),
            if (semesters.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Text(
                  'Noch keine Semester. Lege eines an, um deine Module zu gruppieren.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              )
            else
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: MediaQuery.sizeOf(context).height * 0.45,
                ),
                // Die Reihenfolge ist frei wählbar, weil sich Bezeichnungen wie „WS 25/26"
                // nicht sinnvoll alphabetisch sortieren lassen.
                child: ReorderableListView.builder(
                  shrinkWrap: true,
                  buildDefaultDragHandles: false,
                  itemCount: semesters.length,
                  // onReorderItem statt onReorder: der neue Index ist hier bereits um den
                  // entnommenen Eintrag korrigiert, das manuelle newIndex-- entfällt.
                  onReorderItem: (oldIndex, newIndex) {
                    final ids = semesters.map((s) => s.id).toList();
                    ids.insert(newIndex, ids.removeAt(oldIndex));
                    provider.reorderSemesters(ids);
                  },
                  itemBuilder: (context, index) {
                    final semester = semesters[index];
                    return _SemesterRow(
                      key: ValueKey(semester.id),
                      index: index,
                      semester: semester,
                      onSetCurrent: () => provider.setCurrentSemester(semester.id),
                      onRename: () => _showEditDialog(context, semester: semester),
                      onDelete: () => _confirmDelete(context, semester),
                    );
                  },
                ),
              ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () => _showEditDialog(context),
              style: FilledButton.styleFrom(
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.zero,
                ),
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
              icon: const Icon(Icons.add),
              label: const Text('SEMESTER ANLEGEN'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showEditDialog(
    BuildContext context, {
    StudySemester? semester,
  }) async {
    final provider = context.read<StudyProvider>();
    final controller = TextEditingController(text: semester?.label ?? '');
    final isEdit = semester != null;

    final label = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1A),
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: Text(isEdit ? 'Semester umbenennen' : 'Neues Semester'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: 'z. B. WS 2025/26',
            border: OutlineInputBorder(borderRadius: BorderRadius.zero),
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Abbrechen'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, controller.text.trim()),
            child: Text(isEdit ? 'Speichern' : 'Anlegen'),
          ),
        ],
      ),
    );

    controller.dispose();
    if (label == null || label.isEmpty) return;

    if (isEdit) {
      await provider.updateSemester(semester.copyWith(label: label));
    } else {
      await provider.addSemester(StudySemester(id: '', label: label));
    }
  }

  Future<void> _confirmDelete(
    BuildContext context,
    StudySemester semester,
  ) async {
    final provider = context.read<StudyProvider>();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1A),
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: Text('„${semester.label}" löschen?'),
        // Wichtig zu sagen: an den Modulen hängen Noten, Notizen und Karteikarten.
        content: Text(
          semester.moduleCount == 0
              ? 'Das Semester wird gelöscht.'
              : 'Die ${semester.moduleCount} Module bleiben erhalten und sind danach '
                  'keinem Semester zugeordnet.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Abbrechen'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Löschen'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await provider.deleteSemester(semester.id);
    }
  }
}

class _SemesterRow extends StatelessWidget {
  final int index;
  final StudySemester semester;
  final VoidCallback onSetCurrent;
  final VoidCallback onRename;
  final VoidCallback onDelete;

  const _SemesterRow({
    super.key,
    required this.index,
    required this.semester,
    required this.onSetCurrent,
    required this.onRename,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      color: theme.colorScheme.surfaceContainerHighest,
      child: Row(
        children: [
          ReorderableDragStartListener(
            index: index,
            child: Icon(
              Icons.drag_indicator,
              size: 18,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        semester.label,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    if (semester.isCurrent) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        color: theme.colorScheme.primary,
                        child: Text(
                          'AKTUELL',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onPrimary,
                            fontWeight: FontWeight.w900,
                            fontSize: 9,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  '${semester.moduleCount} Module · ${semester.totalEcts} ECTS',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          PopupMenuButton<String>(
            icon: Icon(Icons.more_vert,
                size: 18, color: theme.colorScheme.onSurfaceVariant),
            color: const Color(0xFF1F2020),
            shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            onSelected: (value) {
              switch (value) {
                case 'current':
                  onSetCurrent();
                case 'rename':
                  onRename();
                case 'delete':
                  onDelete();
              }
            },
            itemBuilder: (_) => [
              if (!semester.isCurrent)
                const PopupMenuItem(
                  value: 'current',
                  child: Text('Als aktuelles Semester'),
                ),
              const PopupMenuItem(value: 'rename', child: Text('Umbenennen')),
              const PopupMenuItem(value: 'delete', child: Text('Löschen')),
            ],
          ),
        ],
      ),
    );
  }
}
