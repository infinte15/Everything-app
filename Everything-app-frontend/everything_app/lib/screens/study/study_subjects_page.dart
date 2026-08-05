import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/study_subject.dart';
import '../../../models/study_grade.dart';
import '../../../models/flashcard_deck.dart';
import '../../../utils/study_grade_calculator.dart';
import 'flashcards/flashcard_deck_page.dart';
import 'flashcards/flashcard_study_page.dart';
import 'flashcards/widgets/add_deck_sheet.dart';
import 'widgets/study_grade_sheet.dart';
import 'widgets/study_kinetic_card.dart';
import 'widgets/study_page_tree.dart';
import 'study_note_editor_page.dart';

class StudySubjectsPage extends StatefulWidget {
  const StudySubjectsPage({super.key});

  @override
  State<StudySubjectsPage> createState() => _StudySubjectsPageState();
}

class _StudySubjectsPageState extends State<StudySubjectsPage> {
  // Lokaler Navigations- & Filter-State
  String _searchQuery = '';
  final Map<String, bool> _expandedSubjects = {};
  final TextEditingController _searchCtrl = TextEditingController();

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
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
                border: Border(
                  right: BorderSide(
                    color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                  ),
                ),
              ),
              // Die Hintergrundfarbe traegt ein Material, kein DecoratedBox: ListTile malt
              // Auswahl und Tinteneffekt auf das naechste Material darueber. Lag die Farbe im
              // DecoratedBox, verdeckte sie beides — Flutter warf dafuer im Debug-Modus bei
              // jedem Oeffnen des Reiters eine Assertion.
              child: Material(
                color: theme.colorScheme.surfaceContainerLow,
                child: _buildLeftSidebar(context, subjects, selectedSubject),
              ),
            ),

            // Mittelteil: alles zum ausgewaehlten Modul.
            // Material statt Container: die Deck- und Notenzeilen sind ListTiles, und die malen
            // ihren Tinteneffekt auf das naechste Material — hinter einer blossen Hintergrund-
            // farbe waere er unsichtbar, was Flutter im Debug-Modus als Assertion meldet.
            Expanded(
              child: Material(
                color: const Color(0xFF0E0E0E),
                child: _buildCenterContent(context, provider, selectedSubject),
              ),
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
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => Scaffold(
                  backgroundColor: const Color(0xFF0E0E0E),
                  appBar: AppBar(
                    title: Text(subject.name.toUpperCase(), style: const TextStyle(letterSpacing: 1.0, fontSize: 16)),
                    backgroundColor: const Color(0xFF0E0E0E),
                    scrolledUnderElevation: 0,
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
                        setState(() => _expandedSubjects[subject.id] = true);
                      },
                    ),
                  ),
                  // Aufgeklappt: die obersten Seiten des Moduls als Sprungmarken. Hier standen
                  // vorher die festen Reiter "Skripte/Übungen/Projekte" — die waren Teil der
                  // erfundenen Pfade in note.category und filterten nichts Echtes.
                  if (isExpanded)
                    Padding(
                      padding: const EdgeInsets.only(left: 36, right: 12, bottom: 4),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: provider.rootPagesOfCourse(subject.id).map((page) {
                          return InkWell(
                            onTap: () {
                              provider.selectSubject(subject.id);
                              Navigator.of(context).push(MaterialPageRoute(
                                builder: (_) => StudyNoteEditorPage(noteId: page.id!),
                              ));
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 6),
                              child: Row(
                                children: [
                                  Text(page.icon?.isNotEmpty == true ? page.icon! : '📄',
                                      style: const TextStyle(fontSize: 12)),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      page.title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        color: theme.colorScheme.onSurfaceVariant,
                                        fontSize: 13,
                                      ),
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

  // ── Center Pane ────────────────────────────────────────────────────────────
  //
  // Hier stand einmal ein Dateibrowser über einer erfundenen Hierarchie: Ordner aus einer
  // RAM-Liste, Pfade als „${subjectId}_$tab" in `note.category`, dazu ausgedachte Dateigrößen
  // („1.2 MB") und ein PDF-Symbol, das an `index % 2 == 0` hing. Nichts davon existierte
  // serverseitig.
  //
  // Und darunter ein Abschnitt „OHNE MODUL", der jede Seite ohne Modul zur Zuordnung
  // aufforderte. Auch der ist entfallen: eine Notiz ohne Modul ist keine Waise, sondern eine
  // freie Notiz aus dem Notizen-Space. Dieser Tab zeigt ausschließlich Modulseiten.

  /// Alles zu einem Modul auf einer Seite: Kopfzeile mit den Kennzahlen, darunter Seitenbaum,
  /// Karteikarten und Noten. Der Seitenbaum hatte bis hierher einen eigenen Reiter — dort stand
  /// er aber ohne Bezug zum Modul, und die Karten und Noten desselben Fachs lagen zwei Reiter
  /// weiter. Zusammengehörendes gehört zusammen.
  Widget _buildCenterContent(BuildContext context, StudyProvider provider, StudySubject subject) {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        _buildSubjectHeader(context, provider, subject),
        const SizedBox(height: 24),
        _buildPagesSection(context, provider, subject),
        const SizedBox(height: 32),
        _buildCardsSection(context, provider, subject),
        const SizedBox(height: 32),
        _buildGradesSection(context, provider, subject),
        const SizedBox(height: 40),
      ],
    );
  }

  /// Modulname, Dozent und die vier Kennzahlen, die man beim Draufschauen wissen will.
  Widget _buildSubjectHeader(
      BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);
    final moduleGrades = provider.gradesForSubject(subject.id);
    final average = _averageOrNull(moduleGrades);
    final color = _parseColor(subject.colorHex) ?? theme.colorScheme.primary;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(width: 4, height: 34, color: color),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    subject.name.toUpperCase(),
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w900,
                      letterSpacing: 0.5,
                    ),
                  ),
                  if (subject.professor?.isNotEmpty == true)
                    Text(
                      subject.professor!,
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                ],
              ),
            ),
            IconButton(
              icon: const Icon(Icons.more_horiz),
              onPressed: () => _showSubjectContextMenu(context, provider, subject),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _metric(context, 'NOTE',
                average == null ? '—' : StudyGradeCalculator.formatGrade(average),
                highlight: true),
            _metric(context, 'ECTS', '${subject.creditPoints}'),
            _metric(context, 'SEMESTER', subject.semester ?? 'keins',
                onTap: () => _showSemesterPicker(context, provider, subject)),
            _metric(context, 'ZU LERNEN', '${provider.courseCardStats(subject.id).studyCount}'),
          ],
        ),
      ],
    );
  }

  /// Der Rechner liefert 0 zurueck, wenn nichts zaehlt — hier soll dann ein Strich stehen und
  /// keine 0,0, die wie eine Bestnote aussieht.
  double? _averageOrNull(List<StudyGrade> moduleGrades) {
    if (moduleGrades.where((g) => g.countsTowardGrade).isEmpty) return null;
    return StudyGradeCalculator.subjectAverage(moduleGrades);
  }

  Widget _metric(BuildContext context, String label, String value,
      {bool highlight = false, VoidCallback? onTap}) {
    final theme = Theme.of(context);
    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(label,
                      style: theme.textTheme.labelSmall?.copyWith(
                          fontSize: 9,
                          letterSpacing: 1.2,
                          color: theme.colorScheme.outline)),
                  if (onTap != null) ...[
                    const SizedBox(width: 4),
                    Icon(Icons.edit, size: 9, color: theme.colorScheme.outline),
                  ],
                ],
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: theme.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: highlight ? theme.colorScheme.primary : theme.colorScheme.onSurface,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _sectionHeader(BuildContext context, String title, String subtitle,
      {required List<Widget> actions}) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Text(title,
            style: theme.textTheme.titleSmall
                ?.copyWith(fontWeight: FontWeight.w900, letterSpacing: 1.2)),
        const SizedBox(width: 10),
        Expanded(
          child: Text(subtitle,
              style: theme.textTheme.labelSmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ),
        ...actions,
      ],
    );
  }

  // ── Abschnitt: Seiten ──────────────────────────────────────────────────────
  Widget _buildPagesSection(
      BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);
    final roots = provider.rootPagesOfCourse(subject.id);
    final visible = _searchQuery.isEmpty
        ? roots
        : roots
            .where((n) => n.title.toLowerCase().contains(_searchQuery.toLowerCase()))
            .toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionHeader(context, 'SEITEN', '${provider.pageCountOfCourse(subject.id)} insgesamt',
            actions: [
              SizedBox(
                width: 180,
                height: 34,
                child: TextField(
                  controller: _searchCtrl,
                  onChanged: (val) => setState(() => _searchQuery = val),
                  style: theme.textTheme.bodySmall,
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.search, size: 14),
                    hintText: 'Suchen...',
                    hintStyle: theme.textTheme.labelSmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    contentPadding: EdgeInsets.zero,
                    border: const OutlineInputBorder(borderRadius: BorderRadius.zero),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              TextButton.icon(
                onPressed: () => _showAddNoteDialog(context, provider, subject),
                icon: const Icon(Icons.add, size: 16),
                label: const Text('SEITE'),
              ),
            ]),
        const SizedBox(height: 8),
        const Divider(height: 1, color: Colors.white10),
        const SizedBox(height: 8),
        if (visible.isEmpty)
          _buildEmptyState(context)
        else
          StudyPageTree(roots: visible),
      ],
    );
  }

  // ── Abschnitt: Karteikarten ────────────────────────────────────────────────
  Widget _buildCardsSection(
      BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);
    final decks = provider.decksForCourse(subject.id);
    final stats = provider.courseCardStats(subject.id);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionHeader(context, 'KARTEIKARTEN',
            decks.isEmpty
                ? 'noch kein Deck'
                : '${decks.length} ${decks.length == 1 ? 'Deck' : 'Decks'} · '
                    '${stats.total} Karten · ${stats.studyCount} zu lernen',
            actions: [
              if (stats.studyCount > 0 && decks.isNotEmpty)
                TextButton.icon(
                  onPressed: () => _startLearning(context, provider, decks),
                  icon: const Icon(Icons.play_arrow, size: 16),
                  label: Text('LERNEN (${stats.studyCount})'),
                ),
              TextButton.icon(
                onPressed: () => AddDeckSheet.show(context, subjectId: subject.id),
                icon: const Icon(Icons.add, size: 16),
                label: const Text('DECK'),
              ),
            ]),
        const SizedBox(height: 8),
        const Divider(height: 1, color: Colors.white10),
        const SizedBox(height: 8),
        if (decks.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text('Noch keine Karteikarten in diesem Modul.',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          )
        else
          ...decks.map((deck) {
            final s = provider.deckStats(deck.id);
            return ListTile(
              contentPadding: EdgeInsets.zero,
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
              leading: const Icon(Icons.style_outlined, size: 20),
              title: Text(deck.title,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
              subtitle: Text(
                '${s.newCards} neu · ${s.due} fällig · ${s.total} gesamt · ${s.masteryPercent} % gereift',
                style: theme.textTheme.labelSmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              trailing: s.studyCount == 0
                  ? null
                  : IconButton(
                      icon: Icon(Icons.play_circle_fill,
                          color: theme.colorScheme.primary, size: 26),
                      onPressed: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => FlashcardStudyPage(
                              deckId: deck.id, deckTitle: deck.title),
                        ),
                      ),
                    ),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => FlashcardDeckPage(deckId: deck.id)),
              ),
            );
          }),
      ],
    );
  }

  /// Startet die Lerneinheit im ersten Deck, in dem etwas ansteht.
  void _startLearning(
      BuildContext context, StudyProvider provider, List<FlashcardDeck> decks) {
    final deck = decks.firstWhere(
      (d) => provider.deckStats(d.id).studyCount > 0,
      orElse: () => decks.first,
    );
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => FlashcardStudyPage(deckId: deck.id, deckTitle: deck.title),
      ),
    );
  }

  // ── Abschnitt: Noten ───────────────────────────────────────────────────────
  Widget _buildGradesSection(
      BuildContext context, StudyProvider provider, StudySubject subject) {
    final theme = Theme.of(context);
    final grades = provider.gradesForSubject(subject.id);
    final average = _averageOrNull(grades);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionHeader(
            context,
            'NOTEN',
            average == null
                ? '${grades.length} Leistungen'
                : 'Modul-Ø ${StudyGradeCalculator.formatGrade(average)} · ${grades.length} Leistungen',
            actions: [
              TextButton.icon(
                onPressed: () => StudyGradeSheet.show(context, subject: subject),
                icon: const Icon(Icons.add, size: 16),
                label: const Text('NOTE'),
              ),
            ]),
        const SizedBox(height: 8),
        const Divider(height: 1, color: Colors.white10),
        const SizedBox(height: 8),
        if (grades.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text('Noch keine Leistung eingetragen.',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          )
        else
          ...grades.map((grade) => ListTile(
                contentPadding: EdgeInsets.zero,
                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                leading: const Icon(Icons.school_outlined, size: 20),
                title: Row(
                  children: [
                    Flexible(
                      child: Text(grade.examName,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                    ),
                    if (!grade.countsTowardGrade) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        color: theme.colorScheme.surfaceContainerHighest,
                        child: Text('SCHEIN',
                            style: theme.textTheme.labelSmall?.copyWith(
                                fontSize: 8,
                                letterSpacing: 1.0,
                                color: theme.colorScheme.onSurfaceVariant)),
                      ),
                    ],
                  ],
                ),
                subtitle: Text('Gewichtung ${grade.weightPercent} %',
                    style: theme.textTheme.labelSmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                trailing: Text(
                  StudyGradeCalculator.formatGrade(grade.grade),
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: gradeColor(grade.grade, theme.colorScheme),
                  ),
                ),
                onTap: () => StudyGradeSheet.show(context, subject: subject, existing: grade),
              )),
      ],
    );
  }


  Widget _buildEmptyState(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.description_outlined, size: 48, color: theme.colorScheme.outlineVariant),
          const SizedBox(height: 16),
          Text(
            'KEINE SEITEN VORHANDEN',
            style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant, letterSpacing: 1.5),
          ),
          const SizedBox(height: 8),
          Text(
            'Lege oben eine Seite an. Unterseiten entstehen im Baum selbst.',
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
          ),
        ],
      ),
    );
  }





  Future<void> _showSemesterPicker(
    BuildContext context,
    StudyProvider provider,
    StudySubject subject,
  ) async {
    final theme = Theme.of(context);
    final selected = await showModalBottomSheet<String?>(
      context: context,
      backgroundColor: const Color(0xFF1A1A1A),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 12),
            Text(
              'SEMESTER WÄHLEN',
              style: theme.textTheme.titleSmall
                  ?.copyWith(fontWeight: FontWeight.w900, letterSpacing: 0.5),
            ),
            const SizedBox(height: 8),
            ListTile(
              title: const Text('Keinem zugeordnet'),
              trailing: subject.semesterId == null
                  ? Icon(Icons.check, color: theme.colorScheme.primary)
                  : null,
              // Ein leerer String heisst hier "abwaehlen"; null waere von "Dialog
              // abgebrochen" nicht zu unterscheiden.
              onTap: () => Navigator.pop(ctx, ''),
            ),
            ...provider.semesters.map(
              (sem) => ListTile(
                title: Text(sem.label),
                subtitle: Text('${sem.moduleCount} Module · ${sem.totalEcts} ECTS'),
                trailing: subject.semesterId == sem.id
                    ? Icon(Icons.check, color: theme.colorScheme.primary)
                    : null,
                onTap: () => Navigator.pop(ctx, sem.id),
              ),
            ),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );

    if (selected == null) return;
    await provider.assignSubjectToSemester(
      subject.id,
      selected.isEmpty ? null : selected,
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


  /// Legt eine Wurzelseite dieses Moduls an. Unterseiten entstehen im Baum selbst.
  void _showAddNoteDialog(BuildContext context, StudyProvider provider, StudySubject subject) {
    final titleCtrl = TextEditingController();
    final contentCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: const Text('NEUE SEITE'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: titleCtrl, decoration: const InputDecoration(labelText: 'Titel (z.B. Kapitel 1)', border: OutlineInputBorder(borderRadius: BorderRadius.zero))),
            const SizedBox(height: 12),
            TextField(controller: contentCtrl, maxLines: 4, decoration: const InputDecoration(labelText: 'Inhalt (Markdown)', border: OutlineInputBorder(borderRadius: BorderRadius.zero))),
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
                  courseId: int.tryParse(subject.id),
                );
                Navigator.pop(ctx);
              }
            },
            child: const Text('SPEICHERN'),
          ),
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
    String selectedColor = '#3B82F6';
    // Vorbelegt mit dem laufenden Semester - das ist fast immer das gemeinte.
    String? selectedSemesterId = provider.currentSemester?.id;

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
                    // Auswahl statt Freitext: getippte Semesternamen erzeugten bei jedem
                    // Tippfehler still eine weitere Gruppe.
                    Expanded(
                      child: DropdownButtonFormField<String?>(
                        initialValue: selectedSemesterId,
                        isExpanded: true,
                        decoration: const InputDecoration(
                          labelText: 'Semester',
                          border: OutlineInputBorder(borderRadius: BorderRadius.zero),
                        ),
                        items: [
                          const DropdownMenuItem<String?>(
                            value: null,
                            child: Text('Keines'),
                          ),
                          ...provider.semesters.map(
                            (sem) => DropdownMenuItem<String?>(
                              value: sem.id,
                              child: Text(sem.label, overflow: TextOverflow.ellipsis),
                            ),
                          ),
                        ],
                        onChanged: (v) => setState(() => selectedSemesterId = v),
                      ),
                    ),
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
                    semesterId: selectedSemesterId,
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