import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/study_goal.dart';
import '../../../models/study_note.dart';
import '../../../widgets/pointer_aware_draggable.dart';
import 'widgets/study_kinetic_card.dart';

class StudyPlanPage extends StatelessWidget {
  const StudyPlanPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final goals = provider.studyPlan;

    // Summary calculations
    final totalGoal = goals.fold<double>(0, (s, g) => s + g.weeklyGoalHours);
    final totalLogged = goals.fold<double>(0, (s, g) => s + g.loggedHours);
    final progress = totalGoal > 0 ? (totalLogged / totalGoal).clamp(0.0, 1.0) : 0.0;

    // Einmal hier statt inline im Baum: die Seite ist stateless und wird bei jedem
    // notifyListeners neu gebaut, und die Berechnung laeuft ueber alle Decks und Karten.
    final dueByCourse = provider.coursesWithDueCards;

    final isWide = MediaQuery.of(context).size.width > 700;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Hero Section: Strategic Overview
            Text(
              'STRATEGIC OVERVIEW',
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w900,
                color: theme.colorScheme.onSurfaceVariant,
                letterSpacing: 1.5,
              ),
            ),
            const SizedBox(height: 12),
            StudyKineticCard(
              backgroundColor: theme.colorScheme.surfaceContainerHighest,
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Wöchentliches Ziel',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${totalLogged.toStringAsFixed(1)} / ${totalGoal.toStringAsFixed(0)} Stunden',
                            style: theme.textTheme.headlineMedium?.copyWith(
                              fontWeight: FontWeight.w900,
                              color: theme.colorScheme.onSurface,
                            ),
                          ),
                        ],
                      ),
                      Text(
                        '${(progress * 100).round()}%',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  LinearProgressIndicator(
                    value: progress,
                    backgroundColor: theme.colorScheme.surfaceContainerLowest,
                    valueColor: AlwaysStoppedAnimation(theme.colorScheme.primary),
                    minHeight: 4,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    progress >= 1.0
                        ? '🎉 Wochenziel erreicht!'
                        : 'Auf Kurs für dein wöchentliches Ziel.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: progress >= 1.0 ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 40),

            // Section: Exam Readiness (Goals List)
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'EXAM READINESS',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.w900,
                    color: theme.colorScheme.onSurfaceVariant,
                    letterSpacing: 1.5,
                  ),
                ),
                TextButton.icon(
                  onPressed: () => _showAddGoalDialog(context),
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('ZIEL HINZUFÜGEN'),
                  style: TextButton.styleFrom(
                    foregroundColor: theme.colorScheme.primary,
                    textStyle: theme.textTheme.labelSmall?.copyWith(fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (goals.isEmpty)
              StudyKineticCard(
                backgroundColor: theme.colorScheme.surfaceContainerLow,
                child: const Center(child: Text('Keine Lernziele definiert.')),
              )
            else
              if (!isWide)
              Column(
                children: List.generate(goals.length, (index) {
                  final goal = goals[index];
                  final color = goal.color;
                  final goalProgress = goal.weeklyGoalHours > 0
                      ? (goal.loggedHours / goal.weeklyGoalHours).clamp(0.0, 1.0)
                      : 0.0;

                  return Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: StudyKineticCard(
                      backgroundColor: theme.colorScheme.surfaceContainerLow,
                      borderColor: color.withValues(alpha: 0.55),
                      borderWidth: 2,
                      padding: const EdgeInsets.all(16),
                      onTap: () => _showLogHoursDialog(context, goal),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Row(
                                  children: [
                                    Text(goal.emoji, style: const TextStyle(fontSize: 20)),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Text(
                                        goal.courseName,
                                        style: theme.textTheme.titleMedium?.copyWith(
                                          fontWeight: FontWeight.bold,
                                        ),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 12),
                              Text(
                                '${(goalProgress * 100).round()}%',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: color,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          LinearProgressIndicator(
                            value: goalProgress,
                            backgroundColor: theme.colorScheme.surfaceContainerLowest,
                            valueColor: AlwaysStoppedAnimation(color),
                            minHeight: 2,
                          ),
                          const SizedBox(height: 8),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                '${goal.loggedHours.toStringAsFixed(1)} / ${goal.weeklyGoalHours.toStringAsFixed(0)} Std',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete_outline, size: 18),
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(),
                                onPressed: () {
                                  provider.deleteStudyGoal(goal.id!);
                                },
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              )
            else
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                // Feste Kachelhöhe statt childAspectRatio: die Höhe hing sonst an der
                // Fensterbreite, und bei 1200 px wurden aus ~90 px Inhalt 269 px hohe Kacheln.
                // (Die beiden isWide-Ternäre hier waren tot — dieser Zweig läuft nur bei isWide.)
                //
                // Mit der Systemschrift mitskaliert: eine starre Höhe läuft bei vergrößerter
                // Schrift über, weil der Inhalt wächst und die Kachel nicht.
                gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: 300,
                  mainAxisExtent: MediaQuery.textScalerOf(context).scale(128),
                  crossAxisSpacing: 16,
                  mainAxisSpacing: 16,
                ),
                itemCount: goals.length,
                itemBuilder: (context, index) {
                  final goal = goals[index];
                  final color = goal.color;
                  final goalProgress = goal.weeklyGoalHours > 0
                      ? (goal.loggedHours / goal.weeklyGoalHours).clamp(0.0, 1.0)
                      : 0.0;

                  return StudyKineticCard(
                    backgroundColor: theme.colorScheme.surfaceContainerLow,
                    borderColor: color.withValues(alpha: 0.55),
                    borderWidth: 2,
                    padding: const EdgeInsets.all(14),
                    onTap: () => _showLogHoursDialog(context, goal),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      // Kein spaceBetween mehr: das streckte den Inhalt nur in die viel zu
                      // hohe Kachel. Die Höhe ist jetzt am Inhalt bemessen.
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Row(
                                children: [
                                  Text(goal.emoji, style: const TextStyle(fontSize: 20)),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      goal.courseName,
                                      style: theme.textTheme.titleMedium?.copyWith(
                                        fontWeight: FontWeight.bold,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              '${(goalProgress * 100).round()}%',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: color,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        LinearProgressIndicator(
                          value: goalProgress,
                          backgroundColor: theme.colorScheme.surfaceContainerLowest,
                          valueColor: AlwaysStoppedAnimation(color),
                          minHeight: 2,
                        ),
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            // Expanded, damit die Zeile bei großer Systemschrift nicht seitlich
                            // ausbricht — der Löschknopf hat Vorrang.
                            Expanded(
                              child: Text(
                                '${goal.loggedHours.toStringAsFixed(1)} / ${goal.weeklyGoalHours.toStringAsFixed(0)} Std',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            // Ohne diese Verdichtung beansprucht der Knopf seine 48px
                            // Vorgabegröße — das war der eigentliche Grund, warum die
                            // Kachel so hoch sein musste.
                            IconButton(
                              icon: const Icon(Icons.delete_outline, size: 18),
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(),
                              visualDensity: VisualDensity.compact,
                              onPressed: () {
                                provider.deleteStudyGoal(goal.id!);
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                },
              ),

            // Section: Wiederholen (faellige Karteikarten je Modul)
            if (dueByCourse.isNotEmpty) ...[
              const SizedBox(height: 48),
              Text(
                'WIEDERHOLEN',
                style: theme.textTheme.labelSmall?.copyWith(
                  fontWeight: FontWeight.w900,
                  color: theme.colorScheme.onSurfaceVariant,
                  letterSpacing: 1.5,
                ),
              ),
              const SizedBox(height: 12),
              ...dueByCourse.map((entry) {
                final color = entry.subject.color;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: StudyKineticCard(
                    backgroundColor: theme.colorScheme.surfaceContainerLow,
                    borderColor: color.withValues(alpha: 0.5),
                    padding: const EdgeInsets.all(16),
                    onTap: () => provider.setActiveTab(4),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            entry.subject.name,
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Text(
                          entry.stats.studyCount == 1
                              ? '1 Karte zu lernen'
                              : '${entry.stats.studyCount} Karten zu lernen',
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontWeight: FontWeight.bold,
                            color: color,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Icon(Icons.chevron_right, size: 18, color: color),
                      ],
                    ),
                  ),
                );
              }),
            ],

            const SizedBox(height: 48),

            // Section: Active Sprint (Kanban Board)
            Text(
              'ACTIVE SPRINT',
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w900,
                color: theme.colorScheme.onSurfaceVariant,
                letterSpacing: 1.5,
              ),
            ),
            const SizedBox(height: 16),

            // Kanban columns layout
            isWide
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: _buildKanbanColumn(context, 'TO DO', provider.todoNotes, 'todo')),
                      const SizedBox(width: 16),
                      Expanded(child: _buildKanbanColumn(context, 'IN PROGRESS', provider.inProgressNotes, 'in_progress')),
                      const SizedBox(width: 16),
                      Expanded(child: _buildKanbanColumn(context, 'DONE', provider.doneNotes, 'done')),
                    ],
                  )
                : Column(
                    children: [
                      _buildKanbanColumn(context, 'TO DO', provider.todoNotes, 'todo'),
                      const SizedBox(height: 24),
                      _buildKanbanColumn(context, 'IN PROGRESS', provider.inProgressNotes, 'in_progress'),
                      const SizedBox(height: 24),
                      _buildKanbanColumn(context, 'DONE', provider.doneNotes, 'done'),
                    ],
                  ),

            const SizedBox(height: 100),
          ],
        ),
      ),
    );
  }

  Widget _buildKanbanColumn(BuildContext context, String title, List<StudyNote> notes, String status) {
    return _KanbanColumn(
      title: title,
      notes: notes,
      status: status,
      onTapNote: (note) => _showStatusSwitcher(context, note, status),
    );
  }

  // ── Status Switcher Dialog ────────────────────────────────────────────────
  void _showStatusSwitcher(BuildContext context, StudyNote note, String currentStatus) {
    final theme = Theme.of(context);
    final provider = context.read<StudyProvider>();

    showModalBottomSheet(
      context: context,
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                title: Text(note.title, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: const Text('Status ändern'),
              ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.list),
                title: const Text('TO DO'),
                selected: currentStatus == 'todo',
                onTap: () {
                  provider.updateNoteStatus(note.id!, 'todo');
                  Navigator.pop(ctx);
                },
              ),
              ListTile(
                leading: const Icon(Icons.play_arrow),
                title: const Text('IN PROGRESS'),
                selected: currentStatus == 'in_progress',
                onTap: () {
                  provider.updateNoteStatus(note.id!, 'in_progress');
                  Navigator.pop(ctx);
                },
              ),
              ListTile(
                leading: const Icon(Icons.check),
                title: const Text('DONE'),
                selected: currentStatus == 'done',
                onTap: () {
                  provider.updateNoteStatus(note.id!, 'done');
                  Navigator.pop(ctx);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  // ── Dialogs ────────────────────────────────────────────────────────────────
  void _showLogHoursDialog(BuildContext context, StudyGoal goal) {
    final ctrl = TextEditingController(text: '1.0');
    final provider = context.read<StudyProvider>();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${goal.emoji} ${goal.courseName}'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(labelText: 'Lernzeit eintragen', suffixText: 'Std'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () async {
              final hrs = double.tryParse(ctrl.text) ?? 0.0;
              final messenger = ScaffoldMessenger.of(context);
              Navigator.pop(ctx);
              if (hrs <= 0.0 || goal.id == null) return;
              // Der Server rechnet die Stunden auf und passt den Brücken-Task an; scheitert
              // das, bliebe die Anzeige sonst mit einem Fortschritt stehen, den es nicht gibt.
              if (!await provider.logStudyHours(goal.id!, hrs)) {
                messenger.showSnackBar(
                  const SnackBar(content: Text('Lernzeit konnte nicht gespeichert werden.')),
                );
              }
            },
            child: const Text('Erfassen'),
          ),
        ],
      ),
    );
  }

  void _showAddGoalDialog(BuildContext context) {
    final provider = context.read<StudyProvider>();
    // Ein Ziel je Modul — der Server weist ein zweites ab, also gar nicht erst anbieten.
    final available = provider.subjectsWithoutGoal;
    final hoursCtrl = TextEditingController(text: '5');
    String emoji = '📚';
    String? courseId = available.isNotEmpty ? available.first.id : null;

    final emojiOptions = ['📚', '📐', '💻', '⚛️', '🗄️', '🔬', '🎯', '📖'];

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) {
          return AlertDialog(
            title: const Text('Neues Lernziel'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Kein Freitextfeld und kein Farbwähler mehr: das Ziel hängt an einem Modul
                  // und erbt dessen Namen und Farbe. Sonst wären "Analysis" und "Analysis I"
                  // zwei Ziele, und dasselbe Modul hätte hier eine andere Farbe als im
                  // Stundenplan.
                  if (available.isEmpty)
                    Text(
                      provider.subjects.isEmpty
                          ? 'Lege zuerst ein Modul an.'
                          : 'Für jedes Modul gibt es bereits ein Lernziel.',
                      style: Theme.of(ctx).textTheme.bodyMedium,
                    )
                  else
                    DropdownButtonFormField<String>(
                      initialValue: courseId,
                      decoration: const InputDecoration(labelText: 'Modul'),
                      items: available
                          .map((s) => DropdownMenuItem(value: s.id, child: Text(s.name)))
                          .toList(),
                      onChanged: (v) => setSt(() => courseId = v),
                    ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: hoursCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Zielstunden', suffixText: 'Std'),
                  ),
                  const SizedBox(height: 16),
                  const Text('Symbol wählen:'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: emojiOptions.map((e) {
                      final sel = e == emoji;
                      return GestureDetector(
                        onTap: () => setSt(() => emoji = e),
                        child: Container(
                          padding: const EdgeInsets.all(8),
                          color: sel ? Theme.of(ctx).colorScheme.primaryContainer : Colors.transparent,
                          child: Text(e, style: const TextStyle(fontSize: 20)),
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
              FilledButton(
                onPressed: courseId == null
                    ? null
                    : () async {
                        final hrs = double.tryParse(hoursCtrl.text) ?? 5.0;
                        final messenger = ScaffoldMessenger.of(context);
                        Navigator.pop(ctx);
                        final ok = await provider.addStudyGoal(
                          courseId: int.parse(courseId!),
                          goalHours: hrs,
                          emoji: emoji,
                        );
                        if (!ok) {
                          messenger.showSnackBar(
                            const SnackBar(content: Text('Lernziel konnte nicht angelegt werden.')),
                          );
                        }
                      },
                child: const Text('Hinzufügen'),
              ),
            ],
          );
        },
      ),
    );
  }
}

/// Eine Kanban-Spalte mit Ablagefläche.
///
/// Anders als in `projects_screen.dart` hängt das [DragTarget] hier um die GANZE Spalte
/// (Kopf, Karten und Leerzustand) statt um eine ListView: die Spalten stehen als einfache
/// Columns in einem SingleChildScrollView und haben deshalb keine begrenzte Höhe, in die
/// sich ein Expanded/ListView setzen ließe.
///
/// Zwei bewusste Grenzen:
///  * Kein Auto-Scroll am Rand während des Ziehens — der äußere SingleChildScrollView weiß
///    nichts vom Drag. In der schmalen Ansicht stehen die drei Spalten untereinander, ein
///    Weg von TO DO nach DONE braucht dort ggf. zwei Gesten. Deshalb bleibt das
///    Tippen → Bottom-Sheet als gleichwertiger Weg bestehen.
///  * Keine Reihenfolge innerhalb einer Spalte. `StudyNote.orderIndex` ist die Ordnung des
///    Seitenbaums und darf dafür nicht mitbenutzt werden; ein Drop setzt nur den Status.
class _KanbanColumn extends StatefulWidget {
  const _KanbanColumn({
    required this.title,
    required this.notes,
    required this.status,
    required this.onTapNote,
  });

  final String title;
  final List<StudyNote> notes;

  /// 'todo' | 'in_progress' | 'done'
  final String status;

  final void Function(StudyNote note) onTapNote;

  @override
  State<_KanbanColumn> createState() => _KanbanColumnState();
}

class _KanbanColumnState extends State<_KanbanColumn> {
  bool _hovered = false;

  Future<void> _accept(StudyNote note) async {
    if (note.id == null) return;
    final provider = context.read<StudyProvider>();
    final messenger = ScaffoldMessenger.of(context);

    // updateNoteStatus setzt optimistisch und rollt bei Fehlschlag zurück — die Karte
    // springt also sofort, und nur wenn der Server ablehnt, wieder an ihren Platz.
    if (!await provider.updateNoteStatus(note.id!, widget.status)) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Status konnte nicht gespeichert werden.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final accent = theme.colorScheme.primary;

    return DragTarget<StudyNote>(
      // Der EFFEKTIVE Status: eine Seite ohne status:-Tag steht in TO DO, ein Drop dorthin
      // wäre also ein Schreibvorgang ohne Wirkung.
      onWillAcceptWithDetails: (d) {
        final from = StudyProvider.statusOf(d.data) ?? 'todo';
        if (from == widget.status) return false;
        setState(() => _hovered = true);
        return true;
      },
      onLeave: (_) => setState(() => _hovered = false),
      onAcceptWithDetails: (d) {
        setState(() => _hovered = false);
        _accept(d.data);
      },
      builder: (context, candidates, rejected) {
        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: _hovered ? accent.withValues(alpha: 0.07) : Colors.transparent,
            // Eckig: der 10px-Radius aus projects_screen.dart ist das Einzige, was hier
            // nicht übernommen wird — Kinetic Mono kennt keine runden Ecken.
            borderRadius: BorderRadius.zero,
            border: Border.all(
              color: _hovered ? accent.withValues(alpha: 0.5) : Colors.transparent,
              width: 1.5,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _header(theme),
              const SizedBox(height: 12),
              ConstrainedBox(
                // Auch eine Spalte mit einer einzigen kurzen Karte bleibt gut treffbar.
                constraints: const BoxConstraints(minHeight: 80),
                child: widget.notes.isEmpty ? _emptyState(theme) : _cards(theme),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _header(ThemeData theme) {
    return Container(
      padding: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
            width: 1,
          ),
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            widget.title,
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: FontWeight.bold,
              letterSpacing: 1.0,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            color: theme.colorScheme.surfaceContainerLow,
            child: Text(
              '${widget.notes.length}',
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.bold,
                color: theme.colorScheme.onSurface,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _emptyState(ThemeData theme) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        border: Border.all(
          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.1),
        ),
      ),
      child: Center(
        child: Text(
          'Karte hierher ziehen',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
          ),
        ),
      ),
    );
  }

  Widget _cards(ThemeData theme) {
    return Column(
      children: widget.notes.map((note) {
        final card = _card(theme, note);
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          // PointerAwareDraggable statt LongPressDraggable: mit der Maus wäre der Drag
          // sonst kaum auslösbar (1px Hit-Slop), und der Finger braucht den langen Druck,
          // damit das Scrollen im SingleChildScrollView erhalten bleibt.
          child: PointerAwareDraggable<StudyNote>(
            data: note,
            feedback: Material(
              color: Colors.transparent,
              child: Opacity(
                opacity: 0.85,
                child: SizedBox(width: 260, child: card),
              ),
            ),
            childWhenDragging: Opacity(opacity: 0.3, child: card),
            child: card,
          ),
        );
      }).toList(),
    );
  }

  Widget _card(ThemeData theme, StudyNote note) {
    final isDone = widget.status == 'done';

    return StudyKineticCard(
      backgroundColor: isDone
          ? theme.colorScheme.surfaceContainerLow
          : theme.colorScheme.surfaceContainerHighest,
      padding: const EdgeInsets.all(16),
      onTap: () => widget.onTapNote(note),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            note.title,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.bold,
              color: isDone ? theme.colorScheme.onSurfaceVariant : theme.colorScheme.onSurface,
              decoration: isDone ? TextDecoration.lineThrough : null,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(
                widget.status == 'todo'
                    ? Icons.menu_book
                    : widget.status == 'in_progress'
                        ? Icons.calculate
                        : Icons.check_circle,
                size: 12,
                color: widget.status == 'in_progress'
                    ? theme.colorScheme.primary
                    : theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  note.courseName ?? 'Notiz',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                    fontSize: 9,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
