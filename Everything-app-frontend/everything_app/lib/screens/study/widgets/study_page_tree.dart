import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/study_note.dart';
import '../../../providers/study_provider.dart';
import '../study_note_editor_page.dart';

/// Der Seitenbaum als wiederverwendbares Stück UI.
///
/// Zwei Aufrufer: der NOTIZEN-Tab (alle Wurzelseiten) und die Modulansicht (nur die Seiten
/// eines Moduls). Beide sollen sich gleich verhalten — deshalb wohnt der Baum hier und nicht
/// zweimal in den Screens.
///
/// Die aufgeklappten Knoten sind Zustand dieses Widgets: welche Ebene offen ist, geht niemanden
/// sonst etwas an und hat im Provider nichts verloren.
class StudyPageTree extends StatefulWidget {
  /// Die obersten Seiten dieser Ansicht.
  final List<StudyNote> roots;

  const StudyPageTree({super.key, required this.roots});

  @override
  State<StudyPageTree> createState() => _StudyPageTreeState();
}

class _StudyPageTreeState extends State<StudyPageTree> {
  final Set<int> _expanded = {};

  void _toggle(int id) => setState(() {
        if (!_expanded.remove(id)) _expanded.add(id);
      });

  @override
  Widget build(BuildContext context) {
    return _PageLevel(
      notes: widget.roots,
      depth: 0,
      expanded: _expanded,
      onToggle: _toggle,
    );
  }
}

// ── Eine Ebene des Baums ──────────────────────────────────────────────────────

/// Pro Ebene eine eigene [ReorderableListView]: gezogen wird immer nur innerhalb der
/// Geschwister. Ebenenübergreifend geht es über „Verschieben" im Kontextmenü — das prüft
/// serverseitig auf Zyklen, was ein Drag über die halbe Liste nicht könnte.
class _PageLevel extends StatelessWidget {
  final List<StudyNote> notes;
  final int depth;
  final Set<int> expanded;
  final void Function(int id) onToggle;

  const _PageLevel({
    required this.notes,
    required this.depth,
    required this.expanded,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    return ReorderableListView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      buildDefaultDragHandles: false,
      itemCount: notes.length,
      // onReorderItem statt onReorder: der neue Index ist hier bereits um die entnommene
      // Zeile bereinigt, das manuelle "if (newIndex > oldIndex) newIndex -= 1" entfällt.
      onReorderItem: (oldIndex, newIndex) {
        final ids = notes.map((n) => n.id!).toList();
        final moved = ids.removeAt(oldIndex);
        ids.insert(newIndex, moved);
        context.read<StudyProvider>().reorderNotes(ids);
      },
      itemBuilder: (context, index) {
        final note = notes[index];
        return _PageNode(
          key: ValueKey(note.id),
          note: note,
          index: index,
          depth: depth,
          expanded: expanded,
          onToggle: onToggle,
        );
      },
    );
  }
}

class _PageNode extends StatelessWidget {
  final StudyNote note;
  final int index;
  final int depth;
  final Set<int> expanded;
  final void Function(int id) onToggle;

  const _PageNode({
    super.key,
    required this.note,
    required this.index,
    required this.depth,
    required this.expanded,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final id = note.id!;
    final children = provider.childrenOf(id);
    final isExpanded = expanded.contains(id);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        InkWell(
          onTap: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => StudyNoteEditorPage(noteId: id),
          )),
          child: Padding(
            padding: EdgeInsets.only(left: depth * 18.0, top: 2, bottom: 2),
            child: Row(
              children: [
                SizedBox(
                  width: 24,
                  child: children.isEmpty
                      ? const Icon(Icons.circle, size: 5, color: Color(0xFF5A5A5A))
                      : IconButton(
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          iconSize: 20,
                          icon: Icon(
                            isExpanded ? Icons.keyboard_arrow_down : Icons.keyboard_arrow_right,
                            color: const Color(0xFFACABAA),
                          ),
                          onPressed: () => onToggle(id),
                        ),
                ),
                const SizedBox(width: 4),
                Text(note.icon?.isNotEmpty == true ? note.icon! : '📄',
                    style: const TextStyle(fontSize: 15)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    note.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if (note.isFavorite)
                  const Padding(
                    padding: EdgeInsets.only(right: 4),
                    child: Icon(Icons.star, size: 14, color: Color(0xFFFF9F0A)),
                  ),
                _NodeMenu(note: note, onExpandParent: () => onToggle(id)),
                // Eigener Griff statt buildDefaultDragHandles: sonst würde jeder Tipp auf die
                // Zeile als Drag-Beginn gewertet und man käme nicht mehr in die Seite hinein.
                ReorderableDragStartListener(
                  index: index,
                  child: const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                    child: Icon(Icons.drag_handle, size: 16, color: Color(0xFF5A5A5A)),
                  ),
                ),
              ],
            ),
          ),
        ),
        if (isExpanded && children.isNotEmpty)
          _PageLevel(
            notes: children,
            depth: depth + 1,
            expanded: expanded,
            onToggle: onToggle,
          ),
      ],
    );
  }
}

// ── Kontextmenü ───────────────────────────────────────────────────────────────

class _NodeMenu extends StatelessWidget {
  final StudyNote note;
  final VoidCallback onExpandParent;

  const _NodeMenu({required this.note, required this.onExpandParent});

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, size: 16, color: Color(0xFFACABAA)),
      color: const Color(0xFF252626),
      itemBuilder: (_) => const [
        PopupMenuItem(value: 'child', child: Text('Unterseite')),
        PopupMenuItem(value: 'move', child: Text('Verschieben')),
        PopupMenuItem(value: 'delete',
            child: Text('Löschen', style: TextStyle(color: Colors.redAccent))),
      ],
      onSelected: (value) async {
        final provider = context.read<StudyProvider>();
        switch (value) {
          case 'child':
            final created = await provider.createChildPage(note.id!);
            if (created != null) onExpandParent();
            break;
          case 'move':
            if (context.mounted) await _showMoveDialog(context, provider);
            break;
          case 'delete':
            if (context.mounted) await _confirmDelete(context, provider);
            break;
        }
      },
    );
  }

  /// Ebenenübergreifend verschieben. Die eigenen Nachfahren stehen nicht zur Auswahl — der
  /// Server lehnt das ohnehin mit 400 ab, aber eine Auswahl anzubieten, die scheitert, wäre
  /// eine Falle.
  Future<void> _showMoveDialog(BuildContext context, StudyProvider provider) async {
    final forbidden = provider.breadcrumbsFor(note.id!).map((n) => n.id).toSet();
    final candidates = provider.notes
        .where((n) => n.id != note.id && !_isDescendantOfSelf(provider, n))
        .toList()
      ..sort((a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()));

    final target = await showDialog<_MoveTarget>(
      context: context,
      builder: (ctx) => SimpleDialog(
        backgroundColor: const Color(0xFF252626),
        title: Text('„${note.title}" verschieben'),
        children: [
          SimpleDialogOption(
            onPressed: () => Navigator.pop(ctx, const _MoveTarget(null)),
            child: const Text('Auf die oberste Ebene'),
          ),
          const Divider(height: 1),
          ...candidates.map((n) => SimpleDialogOption(
                onPressed: () => Navigator.pop(ctx, _MoveTarget(n.id)),
                child: Text(
                  'Unter „${n.title}"',
                  style: TextStyle(
                    color: forbidden.contains(n.id) ? const Color(0xFF5A5A5A) : null,
                  ),
                ),
              )),
        ],
      ),
    );

    if (target == null) return;
    // position 0: die verschobene Seite steht vorn in ihrer neuen Ebene.
    await provider.moveNote(note.id!, target.parentId, 0);
  }

  bool _isDescendantOfSelf(StudyProvider provider, StudyNote candidate) =>
      provider.breadcrumbsFor(candidate.id!).any((n) => n.id == note.id);

  Future<void> _confirmDelete(BuildContext context, StudyProvider provider) async {
    final childCount = provider.childrenOf(note.id!).length;

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF252626),
        title: const Text('Seite löschen?'),
        content: Text(childCount == 0
            ? '„${note.title}" wird entfernt.'
            : '„${note.title}" und alle Unterseiten werden entfernt.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Abbrechen')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Löschen'),
          ),
        ],
      ),
    );

    if (ok == true) await provider.deleteNote(note.id!);
  }
}

/// Damit „oberste Ebene" (parentId == null) von „abgebrochen" (null) unterscheidbar bleibt.
class _MoveTarget {
  final int? parentId;
  const _MoveTarget(this.parentId);
}
