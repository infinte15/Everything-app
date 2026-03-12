import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/study_provider.dart';
import '../../models/study_folder.dart';
import '../../models/study_note.dart';
import 'study_note_editor_page.dart';

class StudyFileSystemPage extends StatefulWidget {
  const StudyFileSystemPage({super.key});

  @override
  State<StudyFileSystemPage> createState() => _StudyFileSystemPageState();
}

class _StudyFileSystemPageState extends State<StudyFileSystemPage> {
  final Set<String> _expanded = {};

  @override
  void initState() {
    super.initState();
    final p = context.read<StudyProvider>();
    for (final f in p.rootFolders()) {
      _expanded.add(f.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final roots = provider.rootFolders();

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 8),
            child: Row(
              children: [
                const Text('📂', style: TextStyle(fontSize: 28)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text('Dateisystem',
                      style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold)),
                ),
                IconButton.filledTonal(
                  icon: const Icon(Icons.create_new_folder_outlined),
                  tooltip: 'Neuer Ordner',
                  onPressed: () => _showCreateFolderDialog(context, null),
                ),
                const SizedBox(width: 8),
                IconButton.filledTonal(
                  icon: const Icon(Icons.note_add_outlined),
                  tooltip: 'Neue Notiz',
                  onPressed: () => _showCreateNoteDialog(context, null),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: roots.isEmpty
                ? _EmptyFolderState(
                    onCreateFolder: () => _showCreateFolderDialog(context, null))
                : ListView(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    children: [
                      // Root notes (no folder)
                      ...provider.notesByFolder(null).map(
                            (n) => _NoteRow(
                              note: n,
                              depth: 0,
                              onTap: () => _openNote(context, n),
                              onDelete: () => provider.deleteNote(n.id!),
                            ),
                          ),
                      // Folders
                      for (final folder in roots)
                        _FolderTree(
                          folder: folder,
                          expanded: _expanded,
                          depth: 0,
                          onToggle: (id) => setState(() {
                            if (_expanded.contains(id)) {
                              _expanded.remove(id);
                            } else {
                              _expanded.add(id);
                            }
                          }),
                          onCreateFolder: (parentId) =>
                              _showCreateFolderDialog(context, parentId),
                          onCreateNote: (folderId) =>
                              _showCreateNoteDialog(context, folderId),
                          onOpenNote: (n) => _openNote(context, n),
                          onRenameFolder: (f) =>
                              _showRenameFolderDialog(context, f),
                          onDeleteFolder: (id) =>
                              context.read<StudyProvider>().deleteFolder(id),
                          onDeleteNote: (id) =>
                              context.read<StudyProvider>().deleteNote(id),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  void _openNote(BuildContext context, StudyNote note) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => StudyNoteEditorPage(noteId: note.id!),
    ));
  }

  Future<void> _showCreateFolderDialog(
      BuildContext context, String? parentId) async {
    final ctrl = TextEditingController();
    String selectedEmoji = '📁';
    final emojis = ['📁', '📂', '📚', '📝', '💡', '🔬', '💻', '📐', '⚛️', '🎯'];

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(builder: (ctx, setSt) {
        return AlertDialog(
          title: const Text('Neuer Ordner'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Wrap(
                spacing: 8,
                children: emojis.map((e) {
                  final sel = e == selectedEmoji;
                  return GestureDetector(
                    onTap: () => setSt(() => selectedEmoji = e),
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(8),
                        color: sel
                            ? Theme.of(ctx).colorScheme.primaryContainer
                            : Colors.transparent,
                      ),
                      child: Text(e, style: const TextStyle(fontSize: 22)),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: ctrl,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Name',
                  hintText: 'z.B. Mathematik',
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Abbrechen')),
            FilledButton(
              onPressed: () {
                if (ctrl.text.trim().isNotEmpty) {
                  context.read<StudyProvider>().addFolder(
                        name: ctrl.text.trim(),
                        parentId: parentId,
                        emoji: selectedEmoji,
                      );
                  Navigator.pop(ctx);
                }
              },
              child: const Text('Erstellen'),
            ),
          ],
        );
      }),
    );
  }

  Future<void> _showCreateNoteDialog(
      BuildContext context, String? folderId) async {
    final ctrl = TextEditingController();
    await showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Neue Seite'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Titel',
            hintText: 'z.B. Vorlesungsnotizen',
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () async {
              if (ctrl.text.trim().isNotEmpty) {
                final note = await context.read<StudyProvider>().addNote(
                      title: ctrl.text.trim(),
                      folderId: folderId,
                    );
                Navigator.pop(ctx);
                if (context.mounted) _openNote(context, note);
              }
            },
            child: const Text('Erstellen'),
          ),
        ],
      ),
    );
  }

  Future<void> _showRenameFolderDialog(
      BuildContext context, StudyFolder folder) async {
    final ctrl = TextEditingController(text: folder.name);
    await showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Umbenennen'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Name'),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () {
              if (ctrl.text.trim().isNotEmpty) {
                context
                    .read<StudyProvider>()
                    .renameFolder(folder.id, ctrl.text.trim());
                Navigator.pop(ctx);
              }
            },
            child: const Text('Speichern'),
          ),
        ],
      ),
    );
  }
}

// ── Folder tree widget ────────────────────────────────────────────────────────

class _FolderTree extends StatelessWidget {
  final StudyFolder folder;
  final Set<String> expanded;
  final int depth;
  final void Function(String id) onToggle;
  final void Function(String? parentId) onCreateFolder;
  final void Function(String? folderId) onCreateNote;
  final void Function(StudyNote note) onOpenNote;
  final void Function(StudyFolder folder) onRenameFolder;
  final void Function(String id) onDeleteFolder;
  final void Function(int id) onDeleteNote;

  const _FolderTree({
    required this.folder,
    required this.expanded,
    required this.depth,
    required this.onToggle,
    required this.onCreateFolder,
    required this.onCreateNote,
    required this.onOpenNote,
    required this.onRenameFolder,
    required this.onDeleteFolder,
    required this.onDeleteNote,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final isExpanded = expanded.contains(folder.id);
    final children = provider.childFolders(folder.id);
    final notes = provider.notesByFolder(folder.id);
    final hasChildren = children.isNotEmpty || notes.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Folder row
        InkWell(
          onTap: () => onToggle(folder.id),
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: EdgeInsets.only(
                left: 16.0 + depth * 20, right: 8, top: 6, bottom: 6),
            child: Row(
              children: [
                Icon(
                  hasChildren
                      ? (isExpanded
                          ? Icons.keyboard_arrow_down
                          : Icons.keyboard_arrow_right)
                      : Icons.circle,
                  size: hasChildren ? 20 : 6,
                  color: Colors.grey,
                ),
                const SizedBox(width: 6),
                Text(folder.emoji, style: const TextStyle(fontSize: 18)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(folder.name,
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(fontWeight: FontWeight.w600)),
                ),
                // Context menu
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_horiz, size: 18),
                  itemBuilder: (_) => [
                    const PopupMenuItem(
                        value: 'note',
                        child: Row(children: [
                          Icon(Icons.note_add_outlined, size: 18),
                          SizedBox(width: 8),
                          Text('Neue Seite'),
                        ])),
                    const PopupMenuItem(
                        value: 'folder',
                        child: Row(children: [
                          Icon(Icons.create_new_folder_outlined, size: 18),
                          SizedBox(width: 8),
                          Text('Unterordner'),
                        ])),
                    const PopupMenuItem(
                        value: 'rename',
                        child: Row(children: [
                          Icon(Icons.drive_file_rename_outline, size: 18),
                          SizedBox(width: 8),
                          Text('Umbenennen'),
                        ])),
                    const PopupMenuItem(
                        value: 'delete',
                        child: Row(children: [
                          Icon(Icons.delete_outline, size: 18, color: Colors.red),
                          SizedBox(width: 8),
                          Text('Löschen', style: TextStyle(color: Colors.red)),
                        ])),
                  ],
                  onSelected: (val) {
                    if (val == 'note') onCreateNote(folder.id);
                    if (val == 'folder') onCreateFolder(folder.id);
                    if (val == 'rename') onRenameFolder(folder);
                    if (val == 'delete') onDeleteFolder(folder.id);
                  },
                ),
              ],
            ),
          ),
        ),
        // Children
        if (isExpanded) ...[
          for (final child in children)
            _FolderTree(
              folder: child,
              expanded: expanded,
              depth: depth + 1,
              onToggle: onToggle,
              onCreateFolder: onCreateFolder,
              onCreateNote: onCreateNote,
              onOpenNote: onOpenNote,
              onRenameFolder: onRenameFolder,
              onDeleteFolder: onDeleteFolder,
              onDeleteNote: onDeleteNote,
            ),
          for (final note in notes)
            _NoteRow(
              note: note,
              depth: depth + 1,
              onTap: () => onOpenNote(note),
              onDelete: () => onDeleteNote(note.id!),
            ),
        ],
      ],
    );
  }
}

// ── Note row ──────────────────────────────────────────────────────────────────

class _NoteRow extends StatelessWidget {
  final StudyNote note;
  final int depth;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  const _NoteRow({
    required this.note,
    required this.depth,
    required this.onTap,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: EdgeInsets.only(
            left: 16.0 + depth * 20 + 32, right: 8, top: 5, bottom: 5),
        child: Row(
          children: [
            const Icon(Icons.article_outlined, size: 16, color: Colors.grey),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                note.title,
                style: theme.textTheme.bodyMedium,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_horiz, size: 16),
              itemBuilder: (_) => [
                const PopupMenuItem(
                    value: 'delete',
                    child: Row(children: [
                      Icon(Icons.delete_outline, size: 18, color: Colors.red),
                      SizedBox(width: 8),
                      Text('Löschen', style: TextStyle(color: Colors.red)),
                    ])),
              ],
              onSelected: (val) {
                if (val == 'delete') onDelete();
              },
            ),
          ],
        ),
      ),
    );
  }
}

// ── Empty state ───────────────────────────────────────────────────────────────

class _EmptyFolderState extends StatelessWidget {
  final VoidCallback onCreateFolder;
  const _EmptyFolderState({required this.onCreateFolder});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text('📂', style: TextStyle(fontSize: 64)),
          const SizedBox(height: 16),
          Text('Noch keine Ordner',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(color: Colors.grey)),
          const SizedBox(height: 8),
          Text('Erstelle deinen ersten Ordner',
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: Colors.grey)),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: onCreateFolder,
            icon: const Icon(Icons.create_new_folder_outlined),
            label: const Text('Ordner erstellen'),
          ),
        ],
      ),
    );
  }
}
