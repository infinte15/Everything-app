import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/study_subject.dart';
import '../../../models/study_note.dart';
import 'widgets/study_kinetic_card.dart';

class StudySubjectsPage extends StatefulWidget {
  const StudySubjectsPage({super.key});

  @override
  State<StudySubjectsPage> createState() => _StudySubjectsPageState();
}

class _StudySubjectsPageState extends State<StudySubjectsPage> {
  String _activeTab = 'Skripte'; // Skripte, Übungen, Projekte

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
            'Keine Fächer vorhanden',
            style: theme.textTheme.bodyLarge?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      );
    }

    // Default select first subject if not set
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

    final isWide = MediaQuery.of(context).size.width > 900;

    if (isWide) {
      return Scaffold(
        backgroundColor: const Color(0xFF0E0E0E),
        body: Row(
          children: [
            // Left Sidebar: Meine Fächer
            Container(
              width: 240,
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

            // Center Column: Main Content (Files/Notes grid)
            Expanded(
              child: Container(
                color: const Color(0xFF0E0E0E),
                child: _buildCenterContent(context, provider, selectedSubject),
              ),
            ),

            // Right Column: Detail Panel
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

    // Mobile/Tablet: Master-Detail view using simple navigation stack
    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: _buildMobileSubjectList(context, subjects, provider),
    );
  }

  // ── Mobile Subject List ────────────────────────────────────────────────────
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
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => Scaffold(
                  backgroundColor: const Color(0xFF0E0E0E),
                  appBar: AppBar(
                    title: Text(subject.name.toUpperCase()),
                    backgroundColor: const Color(0xFF0E0E0E),
                    scrolledUnderElevation: 0,
                    actions: [
                      IconButton(
                        icon: const Icon(Icons.delete_outline, color: Colors.red),
                        onPressed: () {
                          _showDeleteSubjectConfirm(context, provider, subject, onDeleted: () {
                            Navigator.pop(context); // close subject details page
                          });
                        },
                      ),
                    ],
                  ),
                  body: Row(
                    children: [
                      Expanded(
                        child: _buildCenterContent(context, provider, subject),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
          child: Row(
            children: [
              Container(
                width: 4,
                height: 50,
                color: color,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      subject.name,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subject.professor ?? 'Unbekannt',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                '${subject.creditPoints} CP',
                style: theme.textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: theme.colorScheme.primary,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  // ── Left Sidebar (Desktop) ─────────────────────────────────────────────────
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

              return Column(
                children: [
                  ListTile(
                    selected: isSelected,
                    selectedColor: theme.colorScheme.primary,
                    textColor: theme.colorScheme.onSurfaceVariant,
                    leading: Icon(
                      isSelected ? Icons.folder_open : Icons.folder,
                      size: 18,
                    ),
                    title: Text(
                      subject.name,
                      style: TextStyle(
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                        fontSize: 14,
                      ),
                    ),
                    trailing: isSelected ? const Icon(Icons.expand_more, size: 16) : null,
                    onTap: () {
                      provider.selectSubject(subject.id);
                    },
                  ),
                  if (isSelected)
                    Padding(
                      padding: const EdgeInsets.only(left: 36, right: 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: ['Skripte', 'Übungen', 'Projekte'].map((tab) {
                          final isTabActive = _activeTab == tab;
                          return InkWell(
                            onTap: () {
                              setState(() {
                                _activeTab = tab;
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
            label: const Text('Neues Fach'),
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
    );
  }

  // ── Center Content ─────────────────────────────────────────────────────────
  Widget _buildCenterContent(BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);

    // Filter notes for this subject
    final notes = provider.notes.where(
      (n) => n.courseName?.toLowerCase() == subject.name.toLowerCase() ||
             n.title.toLowerCase().contains(subject.name.split(' ').first.toLowerCase()),
    ).toList();

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header & Search
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _activeTab,
                style: theme.textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: theme.colorScheme.onSurface,
                ),
              ),
              SizedBox(
                width: 200,
                height: 40,
                child: TextField(
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.search, size: 16),
                    hintText: 'SUCHE IN DATEIEN...',
                    hintStyle: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      letterSpacing: 1.0,
                    ),
                    contentPadding: const EdgeInsets.symmetric(vertical: 8),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),

          // Grid list of notes
          Expanded(
            child: GridView.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 16,
                mainAxisSpacing: 16,
                childAspectRatio: 1.3,
              ),
              itemCount: notes.length + 1,
              itemBuilder: (context, index) {
                if (index < notes.length) {
                  final note = notes[index];
                  final isPdf = note.title.toLowerCase().contains('.pdf') || index % 2 == 0;

                  return StudyKineticCard(
                    backgroundColor: theme.colorScheme.surfaceContainerHighest,
                    padding: const EdgeInsets.all(16),
                    onTap: () {
                      _showNoteDialog(context, note);
                    },
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(8),
                              color: isPdf ? Colors.red.withValues(alpha: 0.1) : Colors.blue.withValues(alpha: 0.1),
                              child: Icon(
                                isPdf ? Icons.picture_as_pdf : Icons.description,
                                color: isPdf ? Colors.red : Colors.blue,
                                size: 20,
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.more_vert, size: 18),
                              onPressed: () {},
                            ),
                          ],
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              note.title,
                              style: theme.textTheme.bodyMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                                color: theme.colorScheme.onSurface,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Text(
                              _activeTab == 'Skripte' ? 'Vorlesung Grundlagen' : 'Mitschrift',
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                        Text(
                          '1.2 MB • ${_formatDate(note.createdAt)}',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.outline,
                            fontSize: 9,
                          ),
                        ),
                      ],
                    ),
                  );
                }

                // Dotted add button
                return Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () => _showAddNoteDialog(context, provider, subject),
                    borderRadius: BorderRadius.zero,
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border.all(
                          color: theme.colorScheme.outline,
                          style: BorderStyle.solid,
                        ),
                      ),
                      child: Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.add, size: 24),
                            const SizedBox(height: 8),
                            Text(
                              'NEU',
                              style: theme.textTheme.labelSmall?.copyWith(
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1.0,
                              ),
                            ),
                          ],
                        ),
                      ),
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

  // ── Right Sidebar (Desktop) ────────────────────────────────────────────────
  Widget _buildRightSidebar(BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);

    // Find deck related to this subject
    final deck = provider.flashcardDecks.firstWhere(
      (d) => d.subjectId == subject.id,
      orElse: () => provider.flashcardDecks.first,
    );

    // Find grades for this subject
    final grades = provider.grades.where((g) => g.subjectId == subject.id).toList();
    final avgGrade = grades.isEmpty ? 0.0 : grades.fold<double>(0, (s, g) => s + g.grade) / grades.length;

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Quick Info
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

          // Flashcard Status
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
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'Mastery',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
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
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
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
                style: TextStyle(
                  color: Colors.green,
                  fontWeight: FontWeight.bold,
                  fontSize: 10,
                  letterSpacing: 0.5,
                ),
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

  // ── Dialog Helpers ─────────────────────────────────────────────────────────
  void _showNoteDialog(BuildContext context, StudyNote note) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(note.title),
        content: SingleChildScrollView(
          child: Text(note.content ?? 'Kein Inhalt vorhanden.'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Schließen'),
          ),
        ],
      ),
    );
  }

  void _showAddNoteDialog(BuildContext context, StudyProvider provider, StudySubject subject) {
    final titleCtrl = TextEditingController();
    final contentCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Neue Notiz hinzufügen'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: titleCtrl, decoration: const InputDecoration(labelText: 'Titel')),
            const SizedBox(height: 8),
            TextField(
              controller: contentCtrl,
              decoration: const InputDecoration(labelText: 'Inhalt'),
              maxLines: 4,
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () {
              if (titleCtrl.text.trim().isNotEmpty) {
                provider.addNote(
                  title: titleCtrl.text.trim(),
                  content: contentCtrl.text.trim(),
                  courseName: subject.name,
                );
                Navigator.pop(ctx);
              }
            },
            child: const Text('Erstellen'),
          ),
        ],
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

  void _showDeleteSubjectConfirm(BuildContext context, StudyProvider provider, StudySubject subject, {VoidCallback? onDeleted}) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${subject.name} löschen?'),
        content: const Text('Möchtest du dieses Fach wirklich unwiderruflich löschen? Alle zugehörigen Daten bleiben erhalten.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Abbrechen'),
          ),
          FilledButton(
            onPressed: () {
              provider.deleteSubject(subject.id);
              provider.selectSubject(null);
              Navigator.pop(ctx); // Close dialog
              if (onDeleted != null) {
                onDeleted();
              }
            },
            style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
            child: const Text('Löschen'),
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
          title: const Text('Neues Fach hinzufügen'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'Fachname (z.B. Mathematik II)'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: profCtrl,
                  decoration: const InputDecoration(labelText: 'Dozent/Professor'),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: cpCtrl,
                        decoration: const InputDecoration(labelText: 'ECTS / Credit Points'),
                        keyboardType: TextInputType.number,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        controller: semCtrl,
                        decoration: const InputDecoration(labelText: 'Semester'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                const Text('Farbe wählen', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899'].map((hex) {
                    final isSel = selectedColor == hex;
                    return InkWell(
                      onTap: () {
                        setState(() {
                          selectedColor = hex;
                        });
                      },
                      child: Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(
                          color: Color(int.parse(hex.replaceFirst('#', '0xFF'))),
                          shape: BoxShape.circle,
                          border: isSel
                              ? Border.all(color: Colors.white, width: 2)
                              : null,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Abbrechen'),
            ),
            FilledButton(
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
              child: const Text('Hinzufügen'),
            ),
          ],
        ),
      ),
    );
  }
}
