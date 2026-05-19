import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'dart:math';
import '../../../providers/study_provider.dart';
import '../../../providers/task_provider.dart';
import '../../../config/app_theme.dart';
import 'widgets/study_kinetic_card.dart';

class StudyDashboardPage extends StatelessWidget {
  const StudyDashboardPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final taskProvider = context.watch<TaskProvider>();

    // Determine current day index (0 = Monday, 6 = Sunday)
    final weekday = DateTime.now().weekday;
    final dayIndex = (weekday - 1).clamp(0, 4);

    final todayLessons = provider.lessonsForDay(dayIndex);

    final studiumTasks = taskProvider.tasks
        .where((t) => t.category == 'Studium' && t.status != 'COMPLETED')
        .toList();

    // Sort by deadline, null deadlines at the end
    studiumTasks.sort((a, b) {
      if (a.deadline == null && b.deadline == null) return 0;
      if (a.deadline == null) return 1;
      if (b.deadline == null) return -1;
      return a.deadline!.compareTo(b.deadline!);
    });

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: CustomScrollView(
        slivers: [
          // Section: Heute (Today's Lessons)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'HEUTE',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.5,
                          color: theme.colorScheme.onSurface,
                        ),
                      ),
                      Icon(
                        Icons.schedule,
                        color: theme.colorScheme.onSurfaceVariant,
                        size: 20,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (todayLessons.isEmpty)
                    StudyKineticCard(
                      backgroundColor: theme.colorScheme.surfaceContainerLow,
                      child: Center(
                        child: Text(
                          'Keine Vorlesungen heute',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    )
                  else
                    ...todayLessons.map((lesson) {
                      final startStr = '${lesson.startHour.toString().padLeft(2, '0')}:${lesson.startMinute.toString().padLeft(2, '0')}';
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: StudyKineticCard(
                          backgroundColor: theme.colorScheme.surfaceContainerLow,
                          padding: const EdgeInsets.all(20),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  Text(
                                    startStr,
                                    style: theme.textTheme.titleMedium?.copyWith(
                                      fontWeight: FontWeight.bold,
                                      color: theme.colorScheme.primary,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${lesson.durationMinutes} Min',
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(width: 20),
                              Container(
                                width: 1,
                                height: 44,
                                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.3),
                              ),
                              const SizedBox(width: 20),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      lesson.subject,
                                      style: theme.textTheme.bodyLarge?.copyWith(
                                        fontWeight: FontWeight.bold,
                                        color: theme.colorScheme.onSurface,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      '${lesson.professor ?? 'Unbekannt'} • ${lesson.room ?? 'Kein Raum'}',
                                      style: theme.textTheme.bodySmall?.copyWith(
                                        color: theme.colorScheme.onSurfaceVariant,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    }),
                ],
              ),
            ),
          ),

          // Section: Studium Aufgaben
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'AUFGABEN',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.5,
                          color: theme.colorScheme.onSurface,
                        ),
                      ),
                      Icon(
                        Icons.assignment_outlined,
                        color: theme.colorScheme.onSurfaceVariant,
                        size: 20,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (studiumTasks.isEmpty)
                    StudyKineticCard(
                      backgroundColor: theme.colorScheme.surfaceContainerLow,
                      child: Center(
                        child: Text(
                          'Keine ausstehenden Aufgaben',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    )
                  else
                    ...studiumTasks.map((task) {
                      final now = DateTime.now();
                      final today = DateTime(now.year, now.month, now.day);
                      final difference = task.deadline != null
                          ? DateTime(task.deadline!.year, task.deadline!.month, task.deadline!.day).difference(today).inDays
                          : null;

                      String deadlineLabel = '';
                      Color deadlineColor = theme.colorScheme.onSurfaceVariant;

                      if (difference == null) {
                        deadlineLabel = 'OHNE TERMIN';
                      } else if (difference == 0) {
                        deadlineLabel = 'HEUTE FÄLLIG';
                        deadlineColor = theme.colorScheme.error;
                      } else if (difference == 1) {
                        deadlineLabel = 'MORGEN FÄLLIG';
                        deadlineColor = const Color(0xFFE5B580); // Orange/Amber
                      } else if (difference > 1) {
                        deadlineLabel = 'IN $difference TAGEN';
                        deadlineColor = const Color(0xFFC2C1FF); // Study Primary
                      } else {
                        deadlineLabel = 'ÜBERFÄLLIG (${-difference} T.)';
                        deadlineColor = theme.colorScheme.error;
                      }

                      final leftBorderColor = AppTheme.getPriorityColor(task.priority);

                      final dayStr = task.deadline != null
                          ? '${task.deadline!.day.toString().padLeft(2, '0')}.'
                          : '--';
                      final monthStr = task.deadline != null
                          ? ['JAN', 'FEB', 'MÄR', 'APR', 'MAI', 'JUN', 'JUL', 'AUG', 'SEP', 'OKT', 'NOV', 'DEZ'][task.deadline!.month - 1]
                          : 'TASK';

                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Container(
                          decoration: BoxDecoration(
                            border: Border(
                              left: BorderSide(
                                color: leftBorderColor,
                                width: 3,
                              ),
                            ),
                          ),
                          child: StudyKineticCard(
                            backgroundColor: theme.colorScheme.surfaceContainerHighest,
                            padding: const EdgeInsets.all(20),
                            onTap: () {
                              showDialog(
                                context: context,
                                builder: (context) => AlertDialog(
                                  backgroundColor: const Color(0xFF131313),
                                  title: const Text('Aufgabe abschließen?'),
                                  content: Text('Möchtest du "${task.title}" als erledigt markieren?'),
                                  actions: [
                                    TextButton(
                                      onPressed: () => Navigator.pop(context),
                                      child: const Text('Abbrechen', style: TextStyle(color: Colors.grey)),
                                    ),
                                    TextButton(
                                      onPressed: () async {
                                        final messenger = ScaffoldMessenger.of(context);
                                        Navigator.pop(context);
                                        if (task.id != null) {
                                          await context.read<TaskProvider>().completeTask(task.id!);
                                          messenger.showSnackBar(
                                            SnackBar(
                                              content: Text('"${task.title}" abgeschlossen'),
                                              backgroundColor: const Color(0xFF1F2020),
                                            ),
                                          );
                                        }
                                      },
                                      child: const Text('Abschließen', style: TextStyle(color: Color(0xFFC2C1FF))),
                                    ),
                                  ],
                                ),
                              );
                            },
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        deadlineLabel,
                                        style: theme.textTheme.labelSmall?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: deadlineColor,
                                          letterSpacing: 1.0,
                                        ),
                                      ),
                                      const SizedBox(height: 6),
                                      Text(
                                        task.title,
                                        style: theme.textTheme.titleMedium?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: theme.colorScheme.onSurface,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                      if (task.description != null && task.description!.isNotEmpty) ...[
                                        const SizedBox(height: 4),
                                        Text(
                                          task.description!,
                                          style: theme.textTheme.bodySmall?.copyWith(
                                            color: theme.colorScheme.onSurfaceVariant,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ],
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 16),
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Text(
                                      dayStr,
                                      style: theme.textTheme.headlineMedium?.copyWith(
                                        fontWeight: FontWeight.w900,
                                        color: theme.colorScheme.onSurface,
                                        height: 1.0,
                                      ),
                                    ),
                                    Text(
                                      monthStr,
                                      style: theme.textTheme.labelSmall?.copyWith(
                                        fontWeight: FontWeight.bold,
                                        color: theme.colorScheme.onSurfaceVariant,
                                        letterSpacing: 1.0,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),
                      );
                    }),
                ],
              ),
            ),
          ),

          // Section: Upcoming Exams
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'PRÜFUNGEN',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.5,
                          color: theme.colorScheme.onSurface,
                        ),
                      ),
                      Icon(
                        Icons.event,
                        color: theme.colorScheme.onSurfaceVariant,
                        size: 20,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  // Render mockup exams
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border(
                          left: BorderSide(
                            color: theme.colorScheme.error,
                            width: 3,
                          ),
                        ),
                      ),
                      child: StudyKineticCard(
                        backgroundColor: theme.colorScheme.surfaceContainerHighest,
                        padding: const EdgeInsets.all(20),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'IN 12 TAGEN',
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.error,
                                    letterSpacing: 1.0,
                                  ),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  'Lineare Algebra',
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.onSurface,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Zwischenprüfung • 120 Min',
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                              ],
                            ),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Text(
                                  '05.',
                                  style: theme.textTheme.headlineMedium?.copyWith(
                                    fontWeight: FontWeight.w900,
                                    color: theme.colorScheme.onSurface,
                                    height: 1.0,
                                  ),
                                ),
                                Text(
                                  'NOV',
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.onSurfaceVariant,
                                    letterSpacing: 1.0,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: StudyKineticCard(
                      backgroundColor: theme.colorScheme.surfaceContainerLow,
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'IN 28 TAGEN',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.onSurfaceVariant,
                                  letterSpacing: 1.0,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                'Software Engineering',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.onSurface,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Projektpräsentation',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                '21.',
                                style: theme.textTheme.headlineMedium?.copyWith(
                                  fontWeight: FontWeight.w900,
                                  color: theme.colorScheme.onSurface,
                                  height: 1.0,
                                ),
                              ),
                              Text(
                                'NOV',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.onSurfaceVariant,
                                  letterSpacing: 1.0,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
// Section: Learning Progress (Lernfortschritt)
SliverToBoxAdapter(
  child: Padding(
    padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'LERNFORTSCHRITT',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: 0.5,
                color: theme.colorScheme.onSurface,
              ),
            ),
            Icon(
              Icons.donut_large,
              color: theme.colorScheme.onSurfaceVariant,
              size: 20,
            ),
          ],
        ),
        const SizedBox(height: 12),
        StudyKineticCard(
          backgroundColor: theme.colorScheme.surfaceContainerHigh,
          padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            physics: const BouncingScrollPhysics(),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // ── Main circle: weekly total goal ──────────────────
                Builder(builder: (context) {
                  final goals = provider.studyPlan;
                  final totalGoal = goals.fold<double>(0, (s, g) => s + g.weeklyGoalHours);
                  final totalLogged = goals.fold<double>(0, (s, g) => s + g.loggedHours);
                  final progress = totalGoal > 0
                      ? (totalLogged / totalGoal).clamp(0.0, 1.0)
                      : 0.0;
                  final pct = (progress * 100).round();

                  return _LernfortschrittCircle(
                    progress: progress,
                    label: 'WOCHENZIEL',
                    centerText: '$pct%',
                    color: theme.colorScheme.primary,
                    backgroundColor: theme.colorScheme.surfaceContainerLowest,
                    size: 120,
                    strokeWidth: 6,
                    theme: theme,
                    isMain: true,
                  );
                }),

                // ── Per-subject circles from Lernplan ───────────────
                ...provider.studyPlan.map((goal) {
                  final subjectProgress = goal.weeklyGoalHours > 0
                      ? (goal.loggedHours / goal.weeklyGoalHours).clamp(0.0, 1.0)
                      : 0.0;
                  final pct = (subjectProgress * 100).round();

                  final subjectColor = Color(goal.colorValue);

                  return Padding(
                    padding: const EdgeInsets.only(left: 16),
                    child: _LernfortschrittCircle(
                      progress: subjectProgress,
                      label: goal.subject,
                      centerText: '$pct%',
                      color: subjectColor,
                      backgroundColor: theme.colorScheme.surfaceContainerLowest,
                      size: 80,
                      strokeWidth: 5,
                      theme: theme,
                      isMain: false,
                    ),
                  );
                }),

                if (provider.studyPlan.isEmpty)
                  Padding(
                    padding: const EdgeInsets.only(left: 24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.add_circle_outline,
                            color: theme.colorScheme.onSurfaceVariant, size: 32),
                        const SizedBox(height: 8),
                        Text(
                          'Lernplan hinzufügen',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    ),
  ),
),

          // Section: Meine Fächer (My Subjects Grid)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 8),
              child: Text(
                'MEINE FÄCHER',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w900,
                  letterSpacing: 0.5,
                  color: theme.colorScheme.onSurface,
                ),
              ),
            ),
          ),

         SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            sliver: SliverLayoutBuilder(
              builder: (context, constraints) {
                final width = constraints.crossAxisExtent;
                final columns = width > 900 ? 8 : width > 600 ? 5 : 3;
                return SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: columns,
                    crossAxisSpacing: 10,
                    mainAxisSpacing: 10,
                    childAspectRatio: 1.1,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      // Subject cards
                      if (index < provider.subjects.length) {
                        final subject = provider.subjects[index];
                        IconData subjectIcon = Icons.menu_book;
                        if (subject.name.contains('Math') || subject.name.contains('Alge')) {
                          subjectIcon = Icons.functions;
                        } else if (subject.name.contains('Prog') || subject.name.contains('Info')) {
                          subjectIcon = Icons.terminal;
                        } else if (subject.name.contains('Phys')) {
                          subjectIcon = Icons.architecture;
                        }

                        // Count of notes for this subject
                        final noteCount = provider.notes.where((n) => n.courseName == subject.name).length;

                        return StudyKineticCard(
                          backgroundColor: theme.colorScheme.surfaceContainerLow,
                          padding: const EdgeInsets.all(12),
                          onTap: () {
                            provider.selectSubject(subject.id);
                            provider.setActiveTab(2); // Fächer
                          },
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Icon(
                                subjectIcon,
                                color: theme.colorScheme.primary,
                                size: 22,
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    subject.name,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      fontWeight: FontWeight.bold,
                                      color: theme.colorScheme.onSurface,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    noteCount == 0 ? 'Neuigkeiten' : '$noteCount Dokumente',
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    );
                  }
                },
                childCount: provider.subjects.length,
              ),
            );
          },
            ),
        ), // subjects grid
        const SliverToBoxAdapter(
          child: SizedBox(height: 100), // padding for bottom nav
        ),
        ],
      ),
    );
  }
}

class _LernfortschrittCircle extends StatelessWidget {
  final double progress;
  final String label;
  final String centerText;
  final Color color;
  final Color backgroundColor;
  final double size;
  final double strokeWidth;
  final ThemeData theme;
  final bool isMain;

  const _LernfortschrittCircle({
    required this.progress,
    required this.label,
    required this.centerText,
    required this.color,
    required this.backgroundColor,
    required this.size,
    required this.strokeWidth,
    required this.theme,
    required this.isMain,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: size,
          height: size,
          child: CustomPaint(
            painter: _DashboardProgressPainter(
              progress: progress,
              color: color,
              backgroundColor: backgroundColor,
              strokeWidth: strokeWidth,
            ),
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    centerText,
                    style: isMain
                        ? theme.textTheme.titleLarge?.copyWith(
                            fontWeight: FontWeight.w900,
                            color: theme.colorScheme.onSurface,
                          )
                        : theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w900,
                            color: theme.colorScheme.onSurface,
                          ),
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          width: size,
          child: Text(
            label,
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurfaceVariant,
              letterSpacing: isMain ? 1.5 : 0.5,
            ),
          ),
        ),
      ],
    );
  }
}

class _DashboardProgressPainter extends CustomPainter {
  final double progress;
  final Color color;
  final Color backgroundColor;
  final double strokeWidth; // ← add this

  _DashboardProgressPainter({
    required this.progress,
    required this.color,
    required this.backgroundColor,
    this.strokeWidth = 5.0, // ← default keeps existing behavior
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = min(size.width, size.height) / 2 - 8;

    final trackPaint = Paint()
      ..color = backgroundColor
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke;
    canvas.drawCircle(center, radius, trackPaint);

    final progressPaint = Paint()
      ..color = color
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.square
      ..style = PaintingStyle.stroke;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -pi / 2,
      2 * pi * progress,
      false,
      progressPaint,
    );
  }

  @override
  bool shouldRepaint(covariant _DashboardProgressPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.color != color ||
        oldDelegate.backgroundColor != backgroundColor ||
        oldDelegate.strokeWidth != strokeWidth;
  }
}
