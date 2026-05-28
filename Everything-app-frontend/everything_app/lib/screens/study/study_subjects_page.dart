import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/study_subject.dart';
import '../../../models/study_note.dart';
import '../../../models/study_folder.dart';
import 'widgets/study_kinetic_card.dart';

class StudySubjectsPage extends StatefulWidget {
  const StudySubjectsPage({super.key});

  @override
  State<StudySubjectsPage> createState() => _StudySubjectsPageState();
}

class _StudySubjectsPageState extends State<StudySubjectsPage> {
  // Lokaler Navigations- & Filter-State
  String _activeTab = 'Skripte'; // Skripte, Übungen, Projekte
  String? _currentFolderId; // null bedeutet Root-Ebene des aktiven Tabs
  bool _isGridView = true;
  String _sortBy = 'name'; // name, date
  String _searchQuery = '';
  final Map<String, bool> _expandedSubjects = {};
  final TextEditingController _searchCtrl = TextEditingController();

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  // Hilfsmethode zur Ermittlung der aktuellen Datei-Pfad-ID
  String _getCurrentPathId(String subjectId) {
    return _currentFolderId ?? '${subjectId}_$_activeTab';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final subjects = provider.subjects;

    if (subjects.isEmpty) {
      return Scaffold(
        backgroundColor: const Color(0xFF0E0E0E),
        body: Center(
          child: Text(
            'KEINE FÄCHER VORHANDEN',
            style: theme.textTheme.bodyLarge?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
              letterSpacing: 1.5,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      );
    }

    // Standardmäßig erstes Fach auswählen, falls keins gesetzt ist
    final selectedSubjectId = provider.selectedSubjectId ?? subjects.first.id;
    if (provider.selectedSubjectId == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        provider.selectSubject(subjects.first.id);
      });
    }

    final selectedSubject = subjects.firstWhere(
      (s) => s.id == selectedSubjectId,
      orElse: () => subjects.first,
    );

    // Initialen Expansions-State für das selektierte Fach setzen
    if (!_expandedSubjects.containsKey(selectedSubject.id)) {
      _expandedSubjects[selectedSubject.id] = true;
    }

    final isWide = MediaQuery.of(context).size.width > 900;

    if (isWide) {
      return Scaffold(
        backgroundColor: const Color(0xFF0E0E0E),
        body: Row(
          children: [
            // Linke Sidebar: Fachbaum (Subject Tree)
            Container(
              width: 260,
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerLow,
                border: Border(
                  right: BorderSide(
                    color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                  ),
                ),
              ),
              child: _buildLeftSidebar(context, subjects, selectedSubject),
            ),

            // Center Pane: Datei-Explorer / Browser
            Expanded(
              child: Container(
                color: const Color(0xFF0E0E0E),
                child: _buildCenterContent(context, provider, selectedSubject),
              ),
            ),

            // Rechte Sidebar: Quick Info & Flashcards
            Container(
              width: 300,
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerLowest,
                border: Border(
                  left: BorderSide(
                    color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                  ),
                ),
              ),
              child: _buildRightSidebar(context, provider, selectedSubject),
            ),
          ],
        ),
      );
    }

    // Mobile/Tablet Layout
    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: _buildMobileSubjectList(context, subjects, provider),
    );
  }

  // ── Mobile Ansichten ───────────────────────────────────────────────────────
  Widget _buildMobileSubjectList(BuildContext context, List<StudySubject> subjects, StudyProvider provider) {
    final theme = Theme.of(context);
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: subjects.length,
      separatorBuilder: (context, index) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final subject = subjects[index];
        final color = _parseColor(subject.colorHex) ?? theme.colorScheme.primary;

        return StudyKineticCard(
          backgroundColor: theme.colorScheme.surfaceContainerLow,
          onTap: () {
            provider.selectSubject(subject.id);
            setState(() {
              _currentFolderId = null;
            });
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => Scaffold(
                  backgroundColor: const Color(0xFF0E0E0E),
                  appBar: AppBar(
                    title: Text(subject.name.toUpperCase(), style: const TextStyle(letterSpacing: 1.0, fontSize: 16)),
                    backgroundColor: const Color(0xFF0E0E0E),
                    scrolledUnderElevation: 0,
                    actions: [
                      IconButton(
                        icon: const Icon(Icons.info_outline),
                        onPressed: () {
                          showModalBottomSheet(
                            context: context,
                            backgroundColor: theme.colorScheme.surfaceContainerLowest,
                            shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                            builder: (ctx) => SingleChildScrollView(
                              child: _buildRightSidebar(context, provider, subject),
                            ),
                          );
                        },
                      ),
                    ],
                  ),
                  body: _buildCenterContent(context, provider, subject),
                ),
              ),
            );
          },
          child: Row(
            children: [
              Container(width: 4, height: 50, color: color),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      subject.name,
                      style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subject.professor ?? 'Unbekannt',
                      style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
              Text(
                '${subject.creditPoints} CP',
                style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.bold, color: theme.colorScheme.primary),
              ),
            ],
          ),
        );
      },
    );
  }

  // ── Linke Sidebar: Fachbaum (Desktop) ──────────────────────────────────────
  Widget _buildLeftSidebar(BuildContext context, List<StudySubject> subjects, StudySubject selectedSubject) {
    final theme = Theme.of(context);
    final provider = context.read<StudyProvider>();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 24, 20, 16),
          child: Text(
            'MEINE FÄCHER',
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: FontWeight.w900,
              color: theme.colorScheme.onSurfaceVariant,
              letterSpacing: 1.5,
            ),
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: subjects.length,
            itemBuilder: (context, index) {
              final subject = subjects[index];
              final isSelected = subject.id == selectedSubject.id;
              final isExpanded = _expandedSubjects[subject.id] ?? false;
              final subjectColor = _parseColor(subject.colorHex) ?? theme.colorScheme.primary;

              return Column(
                children: [
                  GestureDetector(
                    onLongPress: () => _showSubjectContextMenu(context, provider, subject),
                    child: ListTile(
                      selected: isSelected,
                      selectedColor: theme.colorScheme.primary,
                      textColor: theme.colorScheme.onSurfaceVariant,
                      leading: Icon(
                        isExpanded ? Icons.folder_open : Icons.folder,
                        size: 18,
                        color: subjectColor,
                      ),
                      title: Text(
                        subject.name,
                        style: TextStyle(
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          fontSize: 14,
                        ),
                      ),
                      trailing: IconButton(
                        icon: Icon(isExpanded ? Icons.expand_less : Icons.expand_more, size: 16),
                        onPressed: () {
                          setState(() {
                            _expandedSubjects[subject.id] = !isExpanded;
                          });
                        },
                      ),
                      onTap: () {
                        provider.selectSubject(subject.id);
                        setState(() {
                          _currentFolderId = null;
                          _expandedSubjects[subject.id] = true;
                        });
                      },
                    ),
                  ),
                  if (isExpanded)
                    Padding(
                      padding: const EdgeInsets.only(left: 36, right: 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: ['Skripte', 'Übungen', 'Projekte'].map((tab) {
                          final isTabActive = isSelected && _activeTab == tab;
                          return InkWell(
                            onTap: () {
                              provider.selectSubject(subject.id);
                              setState(() {
                                _activeTab = tab;
                                _currentFolderId = null;
                              });
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              child: Row(
                                children: [
                                  Container(
                                    width: 4,
                                    height: 4,
                                    color: isTabActive ? theme.colorScheme.primary : Colors.transparent,
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    tab,
                                    style: TextStyle(
                                      color: isTabActive ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
                                      fontWeight: isTabActive ? FontWeight.bold : FontWeight.normal,
                                      fontSize: 13,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: TextButton.icon(
            onPressed: () => _showAddSubjectDialog(context, provider),
            icon: const Icon(Icons.add, size: 16),
            label: const Text('NEUES FACH'),
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.onSurfaceVariant,
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
              textStyle: const TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.0, fontSize: 12),
            ),
          ),
        ),
      ],
    );
  }

  // ── Center Pane: Explorer-Dateibrowser ─────────────────────────────────────
  Widget _buildCenterContent(BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);
    final currentPathId = _getCurrentPathId(subject.id);

    List<StudyFolder> currentFolders = provider.folders.where((f) => f.parentId == currentPathId).toList();
    List<StudyNote> currentNotes = provider.notes.where((n) => n.category == currentPathId).toList();

    if (_searchQuery.isNotEmpty) {
      currentFolders = currentFolders.where((f) => f.name.toLowerCase().contains(_searchQuery.toLowerCase())).toList();
      currentNotes = currentNotes.where((n) => n.title.toLowerCase().contains(_searchQuery.toLowerCase())).toList();
    }

    if (_sortBy == 'name') {
      currentFolders.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      currentNotes.sort((a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()));
    } else if (_sortBy == 'date') {
      currentNotes.sort((a, b) => (b.createdAt ?? DateTime.now()).compareTo(a.createdAt ?? DateTime.now()));
    }

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildBreadcrumbs(subject, provider.folders),
          const SizedBox(height: 16),
          const Divider(height: 1, color: Colors.white10),
          const SizedBox(height: 16),

          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 40,
                  child: TextField(
                    controller: _searchCtrl,
                    onChanged: (val) => setState(() => _searchQuery = val),
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.search, size: 16),
                      hintText: 'SUCHE IN DATEIEN...',
                      hintStyle: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                        letterSpacing: 1.0,
                      ),
                      contentPadding: const EdgeInsets.symmetric(vertical: 8),
                      border: const OutlineInputBorder(borderRadius: BorderRadius.zero),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              DropdownButton<String>(
                value: _sortBy,
                underline: const SizedBox(),
                icon: const Icon(Icons.sort, size: 18),
                style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurface),
                borderRadius: BorderRadius.zero,
                items: const [
                  DropdownMenuItem(value: 'name', child: Text('NAME')),
                  DropdownMenuItem(value: 'date', child: Text('DATUM')),
                ],
                onChanged: (val) {
                  if (val != null) setState(() => _sortBy = val);
                },
              ),
              const SizedBox(width: 8),
              IconButton(
                icon: Icon(_isGridView ? Icons.view_list : Icons.grid_view, size: 18),
                onPressed: () => setState(() => _isGridView = !_isGridView),
              ),
              const SizedBox(width: 8),
              ElevatedButton.icon(
                onPressed: () => _showAddFolderDialog(context, provider, currentPathId),
                icon: const Icon(Icons.create_new_folder, size: 16),
                label: const Text('ORDNER'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                  foregroundColor: theme.colorScheme.onSurface,
                  shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton.icon(
                onPressed: () => _showAddNoteDialog(context, provider, subject, currentPathId),
                icon: const Icon(Icons.note_add, size: 16),
                label: const Text('DATEI'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF5856D6),
                  foregroundColor: Colors.white,
                  shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          Expanded(
            child: (currentFolders.isEmpty && currentNotes.isEmpty)
                ? _buildEmptyState(context)
                : _isGridView
                    ? _buildGridView(context, provider, currentFolders, currentNotes)
                    : _buildListView(context, provider, currentFolders, currentNotes),
          ),
        ],
      ),
    );
  }

  // ── Datei-Browser Komponenten ──────────────────────────────────────────────
  Widget _buildBreadcrumbs(StudySubject subject, List<StudyFolder> allFolders) {
    final theme = Theme.of(context);
    List<Widget> crumbs = [];

    crumbs.add(
      InkWell(
        onTap: () => setState(() => _currentFolderId = null),
        child: Text(
          subject.name.toUpperCase(),
          style: TextStyle(fontWeight: FontWeight.bold, color: theme.colorScheme.primary, letterSpacing: 1.0),
        ),
      ),
    );

    crumbs.add(const Padding(
      padding: EdgeInsets.symmetric(horizontal: 8),
      child: Icon(Icons.chevron_right, size: 14, color: Colors.white30),
    ));
    crumbs.add(
      InkWell(
        onTap: () => setState(() => _currentFolderId = null),
        child: Text(
          _activeTab.toUpperCase(),
          style: TextStyle(
            fontWeight: _currentFolderId == null ? FontWeight.bold : FontWeight.normal,
            color: _currentFolderId == null ? theme.colorScheme.onSurface : theme.colorScheme.onSurfaceVariant,
            letterSpacing: 1.0,
          ),
        ),
      ),
    );

    if (_currentFolderId != null) {
      List<StudyFolder> pathChain = [];
      String? lookupId = _currentFolderId;

      while (lookupId != null && !lookupId.contains('_')) {
        final folder = allFolders.firstWhere((f) => f.id == lookupId, orElse: () => null as dynamic);
        if (folder != null) {
          pathChain.insert(0, folder);
          lookupId = folder.parentId;
        } else {
          break;
        }
      }

      for (var folder in pathChain) {
        crumbs.add(const Padding(
          padding: EdgeInsets.symmetric(horizontal: 8),
          child: Icon(Icons.chevron_right, size: 14, color: Colors.white30),
        ));
        final isLast = folder.id == _currentFolderId;
        crumbs.add(
          InkWell(
            onTap: isLast ? null : () => setState(() => _currentFolderId = folder.id),
            child: Text(
              folder.name.toUpperCase(),
              style: TextStyle(
                fontWeight: isLast ? FontWeight.bold : FontWeight.normal,
                color: isLast ? theme.colorScheme.onSurface : theme.colorScheme.onSurfaceVariant,
                letterSpacing: 1.0,
              ),
            ),
          ),
        );
      }
    }

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: crumbs),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.folder_open, size: 48, color: theme.colorScheme.outlineVariant),
          const SizedBox(height: 16),
          Text(
            'KEINE DATEIEN ODER ORDNER VORHANDEN',
            style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant, letterSpacing: 1.5),
          ),
          const SizedBox(height: 8),
          Text(
            'Erstelle oben ein neues Dokument oder einen Ordner, um die Ansicht zu füllen.',
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
          ),
        ],
      ),
    );
  }

  Widget _buildGridView(BuildContext context, StudyProvider provider, List<StudyFolder> folders, List<StudyNote> notes) {
    final theme = Theme.of(context);
    final totalCount = folders.length + notes.length;

    return GridView.builder(
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
        childAspectRatio: 1.4,
      ),
      itemCount: totalCount,
      itemBuilder: (context, index) {
        if (index < folders.length) {
          final folder = folders[index];
          return StudyKineticCard(
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
            padding: const EdgeInsets.all(16),
            onTap: () => setState(() => _currentFolderId = folder.id),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Icon(Icons.folder, color: theme.colorScheme.primary, size: 28),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 18),
                      onPressed: () => provider.deleteFolder(folder.id),
                    ),
                  ],
                ),
                Text(
                  folder.name,
                  style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  'Ordner',
                  style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant, fontSize: 9),
                ),
              ],
            ),
          );
        } else {
          final note = notes[index - folders.length];
          final isPdf = note.title.toLowerCase().contains('.pdf') || index % 2 == 0;

          return StudyKineticCard(
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
            padding: const EdgeInsets.all(16),
            onTap: () => _showNoteDialog(context, note),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      color: isPdf ? Colors.red.withValues(alpha: 0.1) : Colors.blue.withValues(alpha: 0.1),
                      child: Icon(
                        isPdf ? Icons.picture_as_pdf : Icons.description,
                        color: isPdf ? Colors.red : Colors.blue,
                        size: 18,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 18),
                      onPressed: () => provider.deleteNote(note.id ?? 0),
                    ),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      note.title,
                      style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _activeTab,
                      style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ],
                ),
                Text(
                  '1.2 MB • ${_formatDate(note.createdAt)}',
                  style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline, fontSize: 9),
                ),
              ],
            ),
          );
        }
      },
    );
  }

  Widget _buildListView(BuildContext context, StudyProvider provider, List<StudyFolder> folders, List<StudyNote> notes) {
    final theme = Theme.of(context);
    final totalCount = folders.length + notes.length;

    return ListView.builder(
      itemCount: totalCount,
      itemBuilder: (context, index) {
        if (index < folders.length) {
          final folder = folders[index];
          return ListTile(
            shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            leading: Icon(Icons.folder, color: theme.colorScheme.primary),
            title: Text(folder.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            subtitle: const Text('Ordner', style: TextStyle(fontSize: 11)),
            trailing: IconButton(
              icon: const Icon(Icons.delete_outline, size: 18),
              onPressed: () => provider.deleteFolder(folder.id),
            ),
            onTap: () => setState(() => _currentFolderId = folder.id),
          );
        } else {
          final note = notes[index - folders.length];
          final isPdf = note.title.toLowerCase().contains('.pdf') || index % 2 == 0;
          return ListTile(
            shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            leading: Icon(isPdf ? Icons.picture_as_pdf : Icons.description, color: isPdf ? Colors.red : Colors.blue),
            title: Text(note.title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            subtitle: Text('Datei • ${_formatDate(note.createdAt)} • 1.2 MB', style: const TextStyle(fontSize: 11)),
            trailing: IconButton(
              icon: const Icon(Icons.delete_outline, size: 18),
              onPressed: () => provider.deleteNote(note.id ?? 0),
            ),
            onTap: () => _showNoteDialog(context, note),
          );
        }
      },
    );
  }

  // ── Rechte Sidebar: Quick Info & Flashcards ────────────────────────────────
  Widget _buildRightSidebar(BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);

    final deck = provider.flashcardDecks.firstWhere(
      (d) => d.subjectId == subject.id,
      orElse: () => provider.flashcardDecks.isNotEmpty 
          ? provider.flashcardDecks.first 
          : throw Exception('Keine Decks verfügbar'),
    );

    final grades = provider.grades.where((g) => g.subjectId == subject.id).toList();
    final avgGrade = grades.isEmpty ? 0.0 : grades.fold<double>(0, (s, g) => s + g.grade) / grades.length;

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'QUICK-INFO (FACH)',
                style: theme.textTheme.labelSmall?.copyWith(
                  fontWeight: FontWeight.w900,
                  color: theme.colorScheme.onSurfaceVariant,
                  letterSpacing: 1.5,
                ),
              ),
              IconButton(
                icon: Icon(Icons.delete_outline, color: theme.colorScheme.error, size: 18),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
                onPressed: () {
                  _showDeleteSubjectConfirm(context, provider, subject);
                },
                tooltip: 'Fach löschen',
              ),
            ],
          ),
          const SizedBox(height: 16),
          _buildInfoRow('Modul', subject.name.split(' ').first),
          _buildInfoRow('Note', avgGrade == 0.0 ? '--' : avgGrade.toStringAsFixed(1), valueColor: theme.colorScheme.primary),
          _buildInfoRow('ECTS', '${subject.creditPoints} / 180'),
          _buildInfoRow('Status', 'Bestanden', isBadge: true),

          const SizedBox(height: 40),

          Container(
            color: theme.colorScheme.surfaceContainer,
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'FLASHCARD STATUS',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.w900,
                    color: theme.colorScheme.onSurfaceVariant,
                    letterSpacing: 1.5,
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '${deck.masteryPercentage}%',
                      style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    Text(
                      'Mastery',
                      style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                LinearProgressIndicator(
                  value: deck.masteryPercentage / 100,
                  backgroundColor: theme.colorScheme.outlineVariant,
                  valueColor: AlwaysStoppedAnimation(theme.colorScheme.primary),
                  minHeight: 2,
                ),
                const SizedBox(height: 16),
                Text(
                  '${deck.toReviewCount} Karten bereit für Review',
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
                const SizedBox(height: 24),
                ElevatedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.school, size: 16),
                  label: const Text('LERNEN STARTEN'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF5856D6),
                    foregroundColor: const Color(0xFFE2DFFF),
                    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {Color? valueColor, bool isBadge = false}) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: theme.colorScheme.onSurfaceVariant, fontSize: 13)),
          if (isBadge)
            Container(
              color: Colors.green.withValues(alpha: 0.1),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              child: const Text(
                'BESTANDEN',
                style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold, fontSize: 10, letterSpacing: 0.5),
              ),
            )
          else
            Text(
              value,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: valueColor ?? theme.colorScheme.onSurface,
                fontSize: valueColor != null ? 18 : 14,
              ),
            ),
        ],
      ),
    );
  }

  // ── Dialog & Kontext Helpers ───────────────────────────────────────────────
  void _showSubjectContextMenu(BuildContext context, StudyProvider provider, StudySubject subject) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: Text(subject.name.toUpperCase(), style: const TextStyle(letterSpacing: 1.0)),
        content: const Text('Wähle eine Aktion für dieses Studienfach.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('ABBRECHEN')),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              _showDeleteSubjectConfirm(context, provider, subject);
            },
            child: const Text('LÖSCHEN', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }

  void _showAddFolderDialog(BuildContext context, StudyProvider provider, String currentPathId) {
    final folderCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: const Text('NEUER ORDNER'),
        content: TextField(
          controller: folderCtrl,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Ordnername', border: OutlineInputBorder(borderRadius: BorderRadius.zero)),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('ABBRECHEN')),
          FilledButton(
            style: FilledButton.styleFrom(
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            ),
            onPressed: () {
              if (folderCtrl.text.trim().isNotEmpty) {
                provider.addFolder(name: folderCtrl.text.trim(), parentId: currentPathId);
                Navigator.pop(ctx);
                setState(() {});
              }
            },
            child: const Text('ERSTELLEN'),
          ),
        ],
      ),
    );
  }

  void _showAddNoteDialog(BuildContext context, StudyProvider provider, StudySubject subject, String currentPathId) {
    final titleCtrl = TextEditingController();
    final contentCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: const Text('NEUES DOKUMENT'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: titleCtrl, decoration: const InputDecoration(labelText: 'Titel (z.B. Kapitel 1.pdf)', border: OutlineInputBorder(borderRadius: BorderRadius.zero))),
            const SizedBox(height: 12),
            TextField(controller: contentCtrl, maxLines: 4, decoration: const InputDecoration(labelText: 'Inhalt / Notizen', border: OutlineInputBorder(borderRadius: BorderRadius.zero))),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('ABBRECHEN')),
          FilledButton(
            style: FilledButton.styleFrom(
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            ),
            onPressed: () {
              if (titleCtrl.text.trim().isNotEmpty) {
                provider.addNote(
                  title: titleCtrl.text.trim(),
                  content: contentCtrl.text.trim(),
                  folderId: currentPathId,
                  courseName: subject.name,
                );
                Navigator.pop(ctx);
                setState(() {});
              }
            },
            child: const Text('SPEICHERN'),
          ),
        ],
      ),
    );
  }

  void _showNoteDialog(BuildContext context, StudyNote note) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: Text(note.title),
        content: SingleChildScrollView(child: Text(note.content ?? 'Kein Inhalt vorhanden.')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('SCHLIESSEN')),
        ],
      ),
    );
  }

  void _showDeleteSubjectConfirm(BuildContext context, StudyProvider provider, StudySubject subject) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: Text('${subject.name} LÖSCHEN?'),
        content: const Text('Möchtest du dieses Fach wirklich unwiderruflich löschen?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('ABBRECHEN')),
          FilledButton(
            onPressed: () {
              provider.deleteSubject(subject.id);
              provider.selectSubject(null);
              Navigator.pop(ctx);
            },
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error, 
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            ),
            child: const Text('LÖSCHEN'),
          ),
        ],
      ),
    );
  }

  void _showAddSubjectDialog(BuildContext context, StudyProvider provider) {
    final nameCtrl = TextEditingController();
    final profCtrl = TextEditingController();
    final cpCtrl = TextEditingController();
    final semCtrl = TextEditingController();
    String selectedColor = '#3B82F6';

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
          title: const Text('NEUES FACH HINZUFÜGEN'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Fachname', border: OutlineInputBorder(borderRadius: BorderRadius.zero))),
                const SizedBox(height: 8),
                TextField(controller: profCtrl, decoration: const InputDecoration(labelText: 'Dozent', border: OutlineInputBorder(borderRadius: BorderRadius.zero))),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: TextField(controller: cpCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'ECTS', border: OutlineInputBorder(borderRadius: BorderRadius.zero)))),
                    const SizedBox(width: 12),
                    Expanded(child: TextField(controller: semCtrl, decoration: const InputDecoration(labelText: 'Semester', border: OutlineInputBorder(borderRadius: BorderRadius.zero)))),
                  ],
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899'].map((hex) {
                    final isSel = selectedColor == hex;
                    return InkWell(
                      onTap: () => setState(() => selectedColor = hex),
                      child: Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(
                          color: Color(int.parse(hex.replaceFirst('#', '0xFF'))),
                          shape: BoxShape.circle,
                          border: isSel ? Border.all(color: Colors.white, width: 2) : null,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('ABBRECHEN')),
            FilledButton(
              style: FilledButton.styleFrom(
                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
              ),
              onPressed: () {
                if (nameCtrl.text.trim().isNotEmpty) {
                  provider.addSubject(
                    name: nameCtrl.text.trim(),
                    professor: profCtrl.text.trim(),
                    creditPoints: int.tryParse(cpCtrl.text.trim()) ?? 0,
                    semester: semCtrl.text.trim(),
                    colorHex: selectedColor,
                  );
                  Navigator.pop(ctx);
                }
              },
              child: const Text('HINZUFÜGEN'),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return 'Heute';
    return '${dt.day}. ${_monthNames[dt.month - 1]}';
  }

  static const _monthNames = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];

  Color? _parseColor(String? hex) {
    if (hex == null) return null;
    final buffer = StringBuffer();
    if (hex.length == 6 || hex.length == 7) buffer.write('ff');
    buffer.write(hex.replaceFirst('#', ''));
    try {
      return Color(int.parse(buffer.toString(), radix: 16));
    } catch (_) {
      return null;
    }
  }
}