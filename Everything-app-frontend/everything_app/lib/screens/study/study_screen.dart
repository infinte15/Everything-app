import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../providers/study_provider.dart';
import '../../config/app_theme.dart';
import 'study_file_system_page.dart';
import 'study_note_editor_page.dart';
import 'study_plan_page.dart';
import 'lesson_plan_page.dart';
import 'note_tracker_page.dart';

class StudyScreen extends StatefulWidget {
  const StudyScreen({super.key});

  @override
  State<StudyScreen> createState() => _StudyScreenState();
}

class _StudyScreenState extends State<StudyScreen> {
  int _selectedIndex = 0;

  static const _navItems = [
    _NavItem(Icons.folder_outlined, Icons.folder, 'Dateisystem'),
    _NavItem(Icons.edit_note_outlined, Icons.edit_note, 'Notizen'),
    _NavItem(Icons.track_changes_outlined, Icons.track_changes, 'Lernplan'),
    _NavItem(Icons.calendar_view_week_outlined, Icons.calendar_view_week, 'Stundenplan'),
    _NavItem(Icons.view_kanban_outlined, Icons.view_kanban, 'Tracker'),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<StudyProvider>().loadData();
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isWide = MediaQuery.of(context).size.width > 600;

    // Pages array (Notes shows all notes list if no note selected, else editor)
    final pages = [
      const StudyFileSystemPage(),
      _NotesListPage(onOpen: (id) {
        // handled internally
      }),
      const StudyPlanPage(),
      const LessonPlanPage(),
      const NoteTrackerPage(),
    ];

    if (isWide) {
      // Desktop-style: sidebar + main
      return Scaffold(
        backgroundColor: theme.colorScheme.surface,
        body: Row(
          children: [
            _StudySidebar(
              selectedIndex: _selectedIndex,
              navItems: _navItems,
              onSelect: (i) => setState(() => _selectedIndex = i),
              onBack: () => context.go('/spaces'),
            ),
            Expanded(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 250),
                child: KeyedSubtree(
                  key: ValueKey(_selectedIndex),
                  child: pages[_selectedIndex],
                ),
              ),
            ),
          ],
        ),
      );
    }

    // Mobile: bottom nav
    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/spaces'),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('🎓', style: TextStyle(fontSize: 20)),
            const SizedBox(width: 8),
            Text(_navItems[_selectedIndex].label,
                style: const TextStyle(fontWeight: FontWeight.bold)),
          ],
        ),
        backgroundColor: AppTheme.studyColor,
        foregroundColor: Colors.white,
      ),
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 220),
        child: KeyedSubtree(
          key: ValueKey(_selectedIndex),
          child: pages[_selectedIndex],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (i) => setState(() => _selectedIndex = i),
        destinations: _navItems.map((item) {
          return NavigationDestination(
            icon: Icon(item.icon),
            selectedIcon: Icon(item.selectedIcon),
            label: item.label,
          );
        }).toList(),
      ),
    );
  }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

class _StudySidebar extends StatelessWidget {
  final int selectedIndex;
  final List<_NavItem> navItems;
  final void Function(int) onSelect;
  final VoidCallback onBack;

  const _StudySidebar({
    required this.selectedIndex,
    required this.navItems,
    required this.onSelect,
    required this.onBack,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      width: 220,
      height: double.infinity,
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF7F7F5),
        border: Border(
          right: BorderSide(
            color: theme.colorScheme.outline.withValues(alpha: 0.12),
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Top bar with back
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 16, 12, 0),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back_ios_new, size: 16),
                    onPressed: onBack,
                    tooltip: 'Zurück',
                  ),
                  const SizedBox(width: 4),
                  const Text('🎓', style: TextStyle(fontSize: 20)),
                  const SizedBox(width: 8),
                  Text('Studium',
                      style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Divider(height: 1),
          ),
          const SizedBox(height: 8),
          // Nav items
          Expanded(
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              children: [
                for (int i = 0; i < navItems.length; i++)
                  _SidebarItem(
                    item: navItems[i],
                    selected: i == selectedIndex,
                    onTap: () => onSelect(i),
                  ),
              ],
            ),
          ),
          // Footer
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 20),
            child: Text(
              'Second Brain',
              style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
            ),
          ),
        ],
      ),
    );
  }
}

class _SidebarItem extends StatelessWidget {
  final _NavItem item;
  final bool selected;
  final VoidCallback onTap;

  const _SidebarItem({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: selected ? AppTheme.studyColor.withValues(alpha: 0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(
              selected ? item.selectedIcon : item.icon,
              size: 20,
              color: selected ? AppTheme.studyColor : theme.colorScheme.onSurface.withValues(alpha: 0.7),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                item.label,
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                  color: selected ? AppTheme.studyColor : null,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NavItem {
  final IconData icon;
  final IconData selectedIcon;
  final String label;
  const _NavItem(this.icon, this.selectedIcon, this.label);
}

// ── Notes list page ───────────────────────────────────────────────────────────

class _NotesListPage extends StatelessWidget {
  final void Function(int) onOpen;
  const _NotesListPage({required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final notes = provider.notes;

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
            child: Row(
              children: [
                const Text('📝', style: TextStyle(fontSize: 28)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text('Notizen',
                      style: theme.textTheme.headlineSmall
                          ?.copyWith(fontWeight: FontWeight.bold)),
                ),
                FilledButton.icon(
                  onPressed: () => _addNote(context),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Neu'),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: notes.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Text('📝', style: TextStyle(fontSize: 56)),
                        const SizedBox(height: 16),
                        Text('Noch keine Notizen',
                            style: theme.textTheme.titleMedium
                                ?.copyWith(color: Colors.grey)),
                        const SizedBox(height: 24),
                        FilledButton.icon(
                          onPressed: () => _addNote(context),
                          icon: const Icon(Icons.add),
                          label: const Text('Erste Notiz erstellen'),
                        ),
                      ],
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: notes.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (ctx, i) {
                      final note = notes[i];
                      return InkWell(
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(
                              builder: (_) =>
                                  StudyNoteEditorPage(noteId: note.id!)),
                        ),
                        borderRadius: BorderRadius.circular(12),
                        child: Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: theme.colorScheme.surfaceContainerHighest
                                .withValues(alpha: 0.3),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                                color: theme.colorScheme.outline
                                    .withValues(alpha: 0.12)),
                          ),
                          child: Row(
                            children: [
                              const Text('📄',
                                  style: TextStyle(fontSize: 22)),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(note.title,
                                        style: theme.textTheme.bodyMedium
                                            ?.copyWith(
                                                fontWeight: FontWeight.w600),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis),
                                    const SizedBox(height: 4),
                                    Row(
                                      children: [
                                        if (note.courseName != null) ...[
                                          Text(note.courseName!,
                                              style: const TextStyle(
                                                  fontSize: 11,
                                                  color: Colors.grey)),
                                          const Text(' · ',
                                              style: TextStyle(
                                                  color: Colors.grey)),
                                        ],
                                        if (note.createdAt != null)
                                          Text(
                                            _relDate(note.createdAt!),
                                            style: const TextStyle(
                                                fontSize: 11,
                                                color: Colors.grey),
                                          ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 8),
                              if (note.isFavorite)
                                const Icon(Icons.star,
                                    size: 16, color: Colors.amber),
                              const Icon(Icons.chevron_right,
                                  color: Colors.grey),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  String _relDate(DateTime d) {
    final diff = DateTime.now().difference(d);
    if (diff.inDays == 0) return 'Heute';
    if (diff.inDays == 1) return 'Gestern';
    return 'vor ${diff.inDays} Tagen';
  }

  Future<void> _addNote(BuildContext context) async {
    final ctrl = TextEditingController();
    await showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Neue Notiz'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
              labelText: 'Titel', hintText: 'z.B. Vorlesungsnotizen'),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () async {
              if (ctrl.text.trim().isNotEmpty) {
                final note = await context
                    .read<StudyProvider>()
                    .addNote(title: ctrl.text.trim());
                Navigator.pop(ctx);
                if (context.mounted) {
                  Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) =>
                        StudyNoteEditorPage(noteId: note.id!),
                  ));
                }
              }
            },
            child: const Text('Erstellen'),
          ),
        ],
      ),
    );
  }
}