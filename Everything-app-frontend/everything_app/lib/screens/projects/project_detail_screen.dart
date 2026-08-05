import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../config/app_theme.dart';
import '../../models/calendar_event.dart';
import '../../models/project.dart';
import '../../models/task.dart';
import '../../providers/project_provider.dart';
import '../../providers/task_provider.dart';
import '../../widgets/kinetic_card.dart';

/// Detailansicht eines Projekts: Fortschritt aus den verknuepften Aufgaben, die Aufgaben
/// selbst und die vom Scheduler in die Kalenderluecken gelegte Projektzeit.
class ProjectDetailScreen extends StatefulWidget {
  final int projectId;
  const ProjectDetailScreen({super.key, required this.projectId});

  @override
  State<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends State<ProjectDetailScreen> {
  static const Color _accent = AppTheme.projectsColor;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<ProjectProvider>().loadProjectDetail(widget.projectId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<ProjectProvider>();
    final project = provider.projectById(widget.projectId);

    if (project == null) {
      return Scaffold(
        appBar: AppBar(leading: const BackButton(color: _accent)),
        body: Center(
          child: provider.isDetailLoading
              ? const CircularProgressIndicator(color: _accent)
              : Text('Projekt nicht gefunden', style: theme.textTheme.bodyMedium),
        ),
      );
    }

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        backgroundColor: theme.colorScheme.surface,
        elevation: 0,
        leading: const BackButton(color: _accent),
        title: Text(
          project.name.toUpperCase(),
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w900,
            letterSpacing: -0.5,
            color: _accent,
          ),
        ),
      ),
      body: RefreshIndicator(
        color: _accent,
        onRefresh: () => provider.loadProjectDetail(widget.projectId),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            _ProgressHeader(project: project, accent: _accent),
            const SizedBox(height: 24),
            _StatTiles(project: project, sessions: provider.detailSessions),
            const SizedBox(height: 28),
            _SectionHeader(
              title: 'AUFGABEN',
              icon: Icons.checklist_outlined,
              action: TextButton.icon(
                onPressed: () => _openTaskPicker(project),
                icon: const Icon(Icons.add, size: 16, color: _accent),
                label: const Text('Zuordnen', style: TextStyle(color: _accent, fontSize: 12)),
              ),
            ),
            const Divider(height: 1, color: Colors.white10),
            _TaskSection(
              tasks: provider.detailTasks,
              isLoading: provider.isDetailLoading,
              accent: _accent,
              onToggle: _completeTask,
              onDetach: (task) => provider.assignTask(task.id!, null),
            ),
            const SizedBox(height: 28),
            _SectionHeader(title: 'PROJEKTZEIT', icon: Icons.event_repeat_outlined),
            const Divider(height: 1, color: Colors.white10),
            const SizedBox(height: 12),
            _SessionPlanCard(project: project, accent: _accent, onChanged: _saveSessionPlan),
            const SizedBox(height: 12),
            _UpcomingSessions(
              sessions: provider.detailSessions,
              isLoading: provider.isDetailLoading,
              accent: _accent,
            ),
            const SizedBox(height: 28),
            _SectionHeader(title: 'ECKDATEN', icon: Icons.flag_outlined),
            const Divider(height: 1, color: Colors.white10),
            const SizedBox(height: 8),
            _InfoRow('Status', project.statusLabel),
            _InfoRow('Startdatum', _formatDate(project.startDate)),
            _InfoRow('Zieldatum', _formatDate(project.targetEndDate)),
            if (project.actualEndDate != null)
              _InfoRow('Abgeschlossen am', _formatDate(project.actualEndDate)),
          ],
        ),
      ),
    );
  }

  static String _formatDate(DateTime? date) =>
      date == null ? '—' : DateFormat('dd.MM.yyyy').format(date);

  Future<void> _completeTask(Task task) async {
    final ok = await context.read<TaskProvider>().completeTask(task.id!);
    if (!mounted) return;
    if (ok) {
      // Fortschritt und Zaehler rechnet der Server — also neu laden statt lokal zu raten.
      await context.read<ProjectProvider>().loadProjectDetail(widget.projectId);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Aufgabe konnte nicht abgehakt werden')),
      );
    }
  }

  Future<void> _saveSessionPlan(Project updated) async {
    final ok = await context.read<ProjectProvider>().updateProject(updated);
    if (!mounted) return;
    if (ok) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Projektzeit wird beim nächsten Planungslauf neu verteilt')),
      );
      await context.read<ProjectProvider>().loadProjectDetail(widget.projectId);
    }
  }

  /// Vorhandene, noch projektlose Aufgaben zuordnen.
  Future<void> _openTaskPicker(Project project) async {
    final taskProvider = context.read<TaskProvider>();
    if (taskProvider.tasks.isEmpty) await taskProvider.loadTasks();
    if (!mounted) return;

    final candidates = taskProvider.tasks
        .where((t) => t.projectId == null && t.status != 'COMPLETED')
        .toList();

    final picked = await showModalBottomSheet<Task>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (sheetContext) => SafeArea(
        child: candidates.isEmpty
            ? const Padding(
                padding: EdgeInsets.all(24),
                child: Text('Keine freien Aufgaben — alle sind bereits zugeordnet oder erledigt.'),
              )
            : ListView.builder(
                shrinkWrap: true,
                itemCount: candidates.length,
                itemBuilder: (_, i) => ListTile(
                  title: Text(candidates[i].title),
                  subtitle: candidates[i].deadline != null
                      ? Text('bis ${_formatDate(candidates[i].deadline)}')
                      : null,
                  onTap: () => Navigator.pop(sheetContext, candidates[i]),
                ),
              ),
      ),
    );

    if (picked == null || !mounted) return;
    await context.read<ProjectProvider>().assignTask(picked.id!, project.id);
  }
}

// ─── Fortschrittsring ─────────────────────────────────────────────────────────

class _ProgressHeader extends StatelessWidget {
  final Project project;
  final Color accent;
  const _ProgressHeader({required this.project, required this.accent});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final progress = (project.completionPercentage / 100).clamp(0.0, 1.0);

    return Column(
      children: [
        SizedBox(
          width: 160,
          height: 160,
          child: Stack(
            alignment: Alignment.center,
            children: [
              CustomPaint(
                size: const Size(160, 160),
                painter: _RingPainter(
                  progress: progress,
                  trackColor: theme.colorScheme.surfaceContainerHighest,
                  progressColor: accent,
                ),
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    '${project.completionPercentage}%',
                    style: theme.textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w900,
                      letterSpacing: -1,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    project.tasksTotal == 0
                        ? 'KEINE AUFGABEN'
                        : '${project.tasksCompleted} / ${project.tasksTotal} AUFGABEN',
                    style: theme.textTheme.labelSmall?.copyWith(
                      letterSpacing: 1.2,
                      fontWeight: FontWeight.w900,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        if (project.description != null && project.description!.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text(
            project.description!,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
      ],
    );
  }
}

class _RingPainter extends CustomPainter {
  final double progress;
  final Color trackColor;
  final Color progressColor;

  _RingPainter({required this.progress, required this.trackColor, required this.progressColor});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 6;
    final track = Paint()
      ..color = trackColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 8;
    // Eckige Enden: Kinetic Mono kennt keine runden Kappen.
    final arc = Paint()
      ..color = progressColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 8
      ..strokeCap = StrokeCap.square;

    canvas.drawCircle(center, radius, track);
    if (progress > 0) {
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        -math.pi / 2,
        2 * math.pi * progress,
        false,
        arc,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _RingPainter old) => old.progress != progress;
}

// ─── Kennzahlen ───────────────────────────────────────────────────────────────

class _StatTiles extends StatelessWidget {
  final Project project;
  final List<CalendarEvent> sessions;
  const _StatTiles({required this.project, required this.sessions});

  @override
  Widget build(BuildContext context) {
    final days = project.daysUntilTarget;
    final plannedMinutes = sessions
        .where((e) => e.startTime.isBefore(DateTime.now().add(const Duration(days: 7))))
        .fold<int>(0, (sum, e) => sum + e.durationInMinutes);

    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        _Metric(label: 'OFFEN', value: '${project.openTasks}'),
        _Metric(
          label: days == null ? 'ZIELDATUM' : (days < 0 ? 'ÜBERFÄLLIG' : 'TAGE BIS ZIEL'),
          value: days == null ? '—' : '${days.abs()}',
          highlight: days != null && days < 0,
        ),
        _Metric(label: 'WOCHENPENSUM', value: _formatMinutes(project.weeklyMinutes)),
        _Metric(label: 'GEPLANT 7 TAGE', value: _formatMinutes(plannedMinutes)),
      ],
    );
  }

  static String _formatMinutes(int minutes) {
    if (minutes == 0) return '—';
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '${m}min';
    return m == 0 ? '${h}h' : '${h}h ${m}min';
  }
}

class _Metric extends StatelessWidget {
  final String label;
  final String value;
  final bool highlight;
  const _Metric({required this.label, required this.value, this.highlight = false});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              label,
              style: theme.textTheme.labelSmall?.copyWith(
                fontSize: 9,
                letterSpacing: 1.2,
                fontWeight: FontWeight.w900,
                color: highlight ? theme.colorScheme.error : theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              value,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w900,
                color: highlight ? theme.colorScheme.error : null,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Aufgaben ─────────────────────────────────────────────────────────────────

class _TaskSection extends StatelessWidget {
  final List<Task> tasks;
  final bool isLoading;
  final Color accent;
  final ValueChanged<Task> onToggle;
  final ValueChanged<Task> onDetach;

  const _TaskSection({
    required this.tasks,
    required this.isLoading,
    required this.accent,
    required this.onToggle,
    required this.onDetach,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (tasks.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 24),
        child: Column(
          children: [
            Icon(Icons.checklist_outlined, size: 32, color: theme.colorScheme.outlineVariant),
            const SizedBox(height: 12),
            Text('NOCH KEINE AUFGABEN',
                style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.w900, letterSpacing: 1.2)),
            const SizedBox(height: 6),
            Text(
              isLoading ? 'Wird geladen …' : 'Der Fortschritt ergibt sich aus den zugeordneten Aufgaben.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      );
    }

    return Column(
      children: tasks.map((task) {
        final done = task.status == 'COMPLETED';
        return Container(
          margin: const EdgeInsets.only(top: 8),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLow,
            border: Border(
              left: BorderSide(color: AppTheme.getPriorityColor(task.priority), width: 4),
            ),
          ),
          child: ListTile(
            dense: true,
            shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            leading: IconButton(
              icon: Icon(
                done ? Icons.check_box_outlined : Icons.check_box_outline_blank,
                color: done ? accent : theme.colorScheme.onSurfaceVariant,
              ),
              onPressed: done ? null : () => onToggle(task),
            ),
            title: Text(
              task.title,
              style: theme.textTheme.bodyMedium?.copyWith(
                decoration: done ? TextDecoration.lineThrough : null,
                color: done ? theme.colorScheme.onSurfaceVariant : null,
              ),
            ),
            subtitle: task.deadline != null
                ? Text(
                    'bis ${DateFormat('dd.MM.yyyy').format(task.deadline!)}',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: task.isOverdue && !done
                          ? theme.colorScheme.error
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                  )
                : null,
            trailing: IconButton(
              tooltip: 'Aus dem Projekt lösen',
              icon: Icon(Icons.link_off, size: 18, color: theme.colorScheme.onSurfaceVariant),
              onPressed: () => onDetach(task),
            ),
          ),
        );
      }).toList(),
    );
  }
}

// ─── Projektzeit ──────────────────────────────────────────────────────────────

/// Wochenpensum einstellen. Gespeichert wird erst beim Loslassen des Reglers — jede
/// Zwischenstufe wuerde serverseitig einen CP-SAT-Lauf ausloesen.
class _SessionPlanCard extends StatefulWidget {
  final Project project;
  final Color accent;
  final ValueChanged<Project> onChanged;

  const _SessionPlanCard({required this.project, required this.accent, required this.onChanged});

  @override
  State<_SessionPlanCard> createState() => _SessionPlanCardState();
}

class _SessionPlanCardState extends State<_SessionPlanCard> {
  late int _sessions = widget.project.weeklySessionCount;
  late int _minutes = widget.project.sessionDurationMinutes;

  @override
  void didUpdateWidget(covariant _SessionPlanCard old) {
    super.didUpdateWidget(old);
    // Nach dem Neuladen die Serverwerte uebernehmen (z.B. wenn geclampt wurde).
    if (old.project.weeklySessionCount != widget.project.weeklySessionCount) {
      _sessions = widget.project.weeklySessionCount;
    }
    if (old.project.sessionDurationMinutes != widget.project.sessionDurationMinutes) {
      _minutes = widget.project.sessionDurationMinutes;
    }
  }

  void _commit() => widget.onChanged(
        widget.project.copyWith(weeklySessionCount: _sessions, sessionDurationMinutes: _minutes),
      );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = _sessions * _minutes;

    return KineticCard(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _sessions == 0
                ? 'Keine automatische Projektzeit'
                : '$_sessions× pro Woche · $_minutes Min = ${(total / 60).toStringAsFixed(total % 60 == 0 ? 0 : 1)} h',
            style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          Text(
            'Der Scheduler legt diese Blöcke in freie Kalenderlücken. 0 schaltet sie ab.',
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
          _SliderRow(
            label: 'SESSIONS / WOCHE',
            value: _sessions.toDouble(),
            min: 0,
            max: 14,
            divisions: 14,
            accent: widget.accent,
            display: '$_sessions×',
            onChanged: (v) => setState(() => _sessions = v.round()),
            onCommit: _commit,
          ),
          _SliderRow(
            label: 'DAUER JE SESSION',
            value: _minutes.toDouble(),
            min: 15,
            max: 240,
            divisions: 15,
            accent: widget.accent,
            display: '$_minutes min',
            onChanged: (v) => setState(() => _minutes = (v / 15).round() * 15),
            onCommit: _commit,
          ),
        ],
      ),
    );
  }
}

class _SliderRow extends StatelessWidget {
  final String label;
  final double value;
  final double min;
  final double max;
  final int divisions;
  final Color accent;
  final String display;
  final ValueChanged<double> onChanged;
  final VoidCallback onCommit;

  const _SliderRow({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.divisions,
    required this.accent,
    required this.display,
    required this.onChanged,
    required this.onCommit,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              label,
              style: theme.textTheme.labelSmall?.copyWith(
                fontSize: 9,
                letterSpacing: 1.2,
                fontWeight: FontWeight.w900,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const Spacer(),
            Text(display, style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w900)),
          ],
        ),
        Slider(
          value: value.clamp(min, max),
          min: min,
          max: max,
          divisions: divisions,
          activeColor: accent,
          inactiveColor: theme.colorScheme.surfaceContainerHighest,
          onChanged: onChanged,
          onChangeEnd: (_) => onCommit(),
        ),
      ],
    );
  }
}

class _UpcomingSessions extends StatelessWidget {
  final List<CalendarEvent> sessions;
  final bool isLoading;
  final Color accent;

  const _UpcomingSessions({required this.sessions, required this.isLoading, required this.accent});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (sessions.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Text(
          isLoading
              ? 'Blöcke werden geladen …'
              : 'Noch keine Blöcke — sie entstehen beim nächsten Planungslauf.',
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      );
    }

    // main.dart initialisiert genau dieses Locale.
    final dayFmt = DateFormat('EEE dd.MM.', 'de_DE');
    final timeFmt = DateFormat('HH:mm');

    return Column(
      children: sessions.map((s) {
        return Container(
          margin: const EdgeInsets.only(top: 6),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLow,
            border: Border(left: BorderSide(color: accent, width: 4)),
          ),
          child: Row(
            children: [
              Text(dayFmt.format(s.startTime),
                  style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(width: 12),
              Text(
                '${timeFmt.format(s.startTime)} – ${timeFmt.format(s.endTime)}',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              const Spacer(),
              if (s.isFixed)
                Icon(Icons.push_pin, size: 14, color: accent)
              else
                Icon(Icons.auto_awesome, size: 14, color: theme.colorScheme.outlineVariant),
            ],
          ),
        );
      }).toList(),
    );
  }
}

// ─── Kleinteile ───────────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  final IconData icon;
  final Widget? action;
  const _SectionHeader({required this.title, required this.icon, this.action});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Text(
          title,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w900,
            letterSpacing: 0.5,
          ),
        ),
        const Spacer(),
        ?action,
        const SizedBox(width: 4),
        Icon(icon, size: 18, color: theme.colorScheme.onSurfaceVariant),
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        children: [
          Text(label,
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const Spacer(),
          Text(value, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}
