import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/study_grade.dart';
import '../../../models/study_semester.dart';
import '../../../models/study_subject.dart';
import '../../../providers/study_provider.dart';
import '../../../utils/study_grade_calculator.dart';
import 'widgets/study_grade_sheet.dart';
import 'widgets/study_kinetic_card.dart';
import 'widgets/study_semester_sheet.dart';

class StudyGradesPage extends StatefulWidget {
  const StudyGradesPage({super.key});

  @override
  State<StudyGradesPage> createState() => _StudyGradesPageState();
}

class _StudyGradesPageState extends State<StudyGradesPage> {
  String? _expandedSubjectId;

  /// null = alle Semester. Sonst die ID des gewaehlten Semesters — die Auswahl arbeitet auf
  /// den echten Semestern, nicht mehr auf den distinkten Freitexten der Module.
  String? _semesterFilterId;
  double _targetGpa = 2.0;

  List<StudySubject> _filteredSubjects(List<StudySubject> subjects) {
    if (_semesterFilterId == null) return subjects;
    return subjects.where((s) => s.semesterId == _semesterFilterId).toList();
  }

  String _filterLabel(List<StudySemester> semesters) {
    if (_semesterFilterId == null) return 'Alle Semester';
    for (final s in semesters) {
      if (s.id == _semesterFilterId) return s.label;
    }
    return 'Alle Semester';
  }

  Color? _subjectColor(StudySubject subject) {
    final hex = subject.colorHex;
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final allSubjects = provider.subjects;
    final grades = provider.grades;
    final subjects = _filteredSubjects(allSubjects);
    final semesters = provider.semesters;

    // Sobald das gewaehlte Semester weg ist (geloescht oder nach dem Neuladen nicht mehr da),
    // faellt die Auswahl auf "Alle" zurueck — sonst zeigte die Liste dauerhaft nichts an.
    if (_semesterFilterId != null &&
        !semesters.any((s) => s.id == _semesterFilterId)) {
      _semesterFilterId = null;
    }

    final snapshot = StudyGradeCalculator.computeGpa(
      subjects: allSubjects,
      grades: grades,
      semesterId: _semesterFilterId,
    );

    final wunschMsg = StudyGradeCalculator.wunschnoteMessage(
      snapshot: snapshot,
      targetGpa: _targetGpa,
      hasSubjects: allSubjects.isNotEmpty,
    );

    if (allSubjects.isEmpty) {
      return _EmptySubjectsState(
        onOpenSubjects: () => provider.setActiveTab(2),
      );
    }

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      floatingActionButton: subjects.isNotEmpty
          ? FloatingActionButton(
              backgroundColor: theme.colorScheme.primary,
              foregroundColor: theme.colorScheme.onPrimary,
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
              onPressed: () => StudyGradeSheet.show(context),
              child: const Icon(Icons.add),
            )
          : null,
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: _GpaHeader(
              snapshot: snapshot,
              theme: theme,
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: FilterChip(
                        label: const Text('Alle'),
                        selected: _semesterFilterId == null,
                        onSelected: (_) => setState(() => _semesterFilterId = null),
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.zero,
                        ),
                      ),
                    ),
                    ...semesters.map((sem) {
                      final selected = _semesterFilterId == sem.id;
                      return Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: FilterChip(
                          // Das laufende Semester bekommt einen Punkt, damit man es in einer
                          // langen Liste wiederfindet.
                          avatar: sem.isCurrent
                              ? Icon(Icons.circle,
                                  size: 8, color: theme.colorScheme.primary)
                              : null,
                          label: Text(sem.label),
                          selected: selected,
                          onSelected: (_) =>
                              setState(() => _semesterFilterId = sem.id),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.zero,
                          ),
                        ),
                      );
                    }),
                    ActionChip(
                      avatar: const Icon(Icons.tune, size: 16),
                      label: const Text('Semester'),
                      onPressed: () => StudySemesterSheet.show(context),
                      shape: const RoundedRectangleBorder(
                        borderRadius: BorderRadius.zero,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: _TargetGpaCard(
                targetGpa: _targetGpa,
                message: wunschMsg,
                onChanged: (v) => setState(() => _targetGpa = v),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'MODULE',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w900,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ),
                  Text(
                    '${snapshot.gradedSubjectCount}/${snapshot.subjectCount} bewertet',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (subjects.isEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: StudyKineticCard(
                  backgroundColor: theme.colorScheme.surfaceContainerLow,
                  child: Text(
                    'Keine Module in „${_filterLabel(semesters)}".',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final subject = subjects[index];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _SubjectGradeCard(
                        subject: subject,
                        grades: grades
                            .where((g) => g.subjectId == subject.id)
                            .toList()
                          ..sort((a, b) => b.date.compareTo(a.date)),
                        accent: _subjectColor(subject),
                        isExpanded: _expandedSubjectId == subject.id,
                        onToggle: () {
                          setState(() {
                            _expandedSubjectId =
                                _expandedSubjectId == subject.id
                                    ? null
                                    : subject.id;
                          });
                        },
                        onAddGrade: () =>
                            StudyGradeSheet.show(context, subject: subject),
                        onEditGrade: (g) =>
                            StudyGradeSheet.show(context, subject: subject, existing: g),
                      ),
                    );
                  },
                  childCount: subjects.length,
                ),
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 100)),
        ],
      ),
    );
  }
}

class _GpaHeader extends StatelessWidget {
  final StudyGpaSnapshot snapshot;
  final ThemeData theme;

  const _GpaHeader({required this.snapshot, required this.theme});

  @override
  Widget build(BuildContext context) {
    final gpa = snapshot.overallGpa;
    final display = snapshot.hasGpa
        ? StudyGradeCalculator.formatGrade(gpa)
        : '—';
    final gpaColor = snapshot.hasGpa
        ? gradeColor(gpa, theme.colorScheme)
        : theme.colorScheme.onSurfaceVariant;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 8),
      child: Column(
        children: [
          SizedBox(
            width: 180,
            height: 180,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CustomPaint(
                  size: const Size(180, 180),
                  painter: _RingPainter(
                    progress: snapshot.progress,
                    trackColor: theme.colorScheme.surfaceContainerHighest,
                    progressColor: theme.colorScheme.primary,
                  ),
                ),
                Container(
                  width: 140,
                  height: 140,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHigh,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: theme.colorScheme.outlineVariant.withValues(
                        alpha: 0.2,
                      ),
                    ),
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        display,
                        style: theme.textTheme.displayMedium?.copyWith(
                          fontWeight: FontWeight.w900,
                          fontSize: 44,
                          letterSpacing: -2,
                          color: gpaColor,
                          height: 1,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'GESAMTSCHNITT',
                        style: theme.textTheme.labelSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1.2,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '${snapshot.completedEcts.toInt()} / ${snapshot.totalEcts.toInt()} ECTS',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'ECTS-gewichteter Modulschnitt (Studium)',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  final double progress;
  final Color trackColor;
  final Color progressColor;

  _RingPainter({
    required this.progress,
    required this.trackColor,
    required this.progressColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 6;
    final track = Paint()
      ..color = trackColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 8;
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
  bool shouldRepaint(covariant _RingPainter old) =>
      old.progress != progress;
}

class _TargetGpaCard extends StatelessWidget {
  final double targetGpa;
  final String message;
  final ValueChanged<double> onChanged;

  const _TargetGpaCard({
    required this.targetGpa,
    required this.message,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return StudyKineticCard(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'ZIELSCHNITT',
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.primary,
              letterSpacing: 1.5,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              IconButton(
                onPressed: targetGpa <= 1.0
                    ? null
                    : () => onChanged(
                          double.parse((targetGpa - 0.1).toStringAsFixed(1)),
                        ),
                icon: const Icon(Icons.remove),
                style: IconButton.styleFrom(
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.zero,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                StudyGradeCalculator.formatGrade(targetGpa),
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                onPressed: targetGpa >= 5.0
                    ? null
                    : () => onChanged(
                          double.parse((targetGpa + 0.1).toStringAsFixed(1)),
                        ),
                icon: const Icon(Icons.add),
                style: IconButton.styleFrom(
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.zero,
                  ),
                ),
              ),
            ],
          ),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 4,
              overlayShape: SliderComponentShape.noOverlay,
            ),
            child: Slider(
              value: targetGpa,
              min: 1.0,
              max: 5.0,
              divisions: 40,
              label: StudyGradeCalculator.formatGrade(targetGpa),
              onChanged: (v) =>
                  onChanged(double.parse(v.toStringAsFixed(1))),
            ),
          ),
          Text(
            message,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _SubjectGradeCard extends StatelessWidget {
  final StudySubject subject;
  final List<StudyGrade> grades;
  final Color? accent;
  final bool isExpanded;
  final VoidCallback onToggle;
  final VoidCallback onAddGrade;
  final void Function(StudyGrade grade) onEditGrade;

  const _SubjectGradeCard({
    required this.subject,
    required this.grades,
    required this.accent,
    required this.isExpanded,
    required this.onToggle,
    required this.onAddGrade,
    required this.onEditGrade,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final avg = StudyGradeCalculator.subjectAverage(grades);
    final weightTotal = StudyGradeCalculator.subjectWeightTotal(grades);
    final complete = StudyGradeCalculator.subjectIsComplete(grades);
    final accentColor = accent ?? theme.colorScheme.primary;

    return StudyKineticCard(
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      padding: EdgeInsets.zero,
      onTap: onToggle,
      child: Column(
        children: [
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(width: 4, color: accentColor),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                subject.name,
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                [
                                  if (subject.creditPoints > 0)
                                    '${subject.creditPoints} ECTS',
                                  if (subject.semester != null &&
                                      subject.semester!.isNotEmpty)
                                    subject.semester!,
                                  '${grades.length} Leistung${grades.length == 1 ? '' : 'en'}',
                                  if (grades.isNotEmpty && !complete)
                                    'Gewicht $weightTotal%',
                                ].join(' · '),
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                        _GradeBadge(
                          value: avg,
                          theme: theme,
                          filled: grades.isNotEmpty,
                        ),
                        const SizedBox(width: 4),
                        Icon(
                          isExpanded
                              ? Icons.keyboard_arrow_up
                              : Icons.keyboard_arrow_down,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (isExpanded) ...[
            Divider(
              height: 1,
              color: theme.colorScheme.outlineVariant.withValues(alpha: 0.2),
            ),
            if (grades.isEmpty)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  'Noch keine Leistungen — tippe + um eine Note einzutragen.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              )
            else
              _GradesTable(
                grades: grades,
                onTapGrade: onEditGrade,
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton.icon(
                    onPressed: onAddGrade,
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Leistung'),
                    style: TextButton.styleFrom(
                      foregroundColor: theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _GradeBadge extends StatelessWidget {
  final double value;
  final ThemeData theme;
  final bool filled;

  const _GradeBadge({
    required this.value,
    required this.theme,
    required this.filled,
  });

  @override
  Widget build(BuildContext context) {
    final text = filled
        ? StudyGradeCalculator.formatGrade(value)
        : '—';
    final color = filled ? gradeColor(value, theme.colorScheme) : null;

    return Container(
      width: 52,
      height: 52,
      decoration: BoxDecoration(
        color: filled
            ? color!.withValues(alpha: 0.15)
            : theme.colorScheme.surfaceContainerHighest,
        shape: BoxShape.circle,
        border: filled
            ? Border.all(color: color!.withValues(alpha: 0.5))
            : null,
      ),
      alignment: Alignment.center,
      child: Text(
        text,
        style: theme.textTheme.titleMedium?.copyWith(
          fontWeight: FontWeight.bold,
          color: filled ? color : theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}

class _GradesTable extends StatelessWidget {
  final List<StudyGrade> grades;
  final void Function(StudyGrade) onTapGrade;

  const _GradesTable({required this.grades, required this.onTapGrade});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                flex: 5,
                child: Text(
                  'LEISTUNG',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: theme.colorScheme.primary,
                    letterSpacing: 1,
                  ),
                ),
              ),
              Expanded(
                flex: 2,
                child: Text(
                  'GEW.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: theme.colorScheme.primary,
                    letterSpacing: 1,
                  ),
                ),
              ),
              Expanded(
                flex: 2,
                child: Text(
                  'NOTE',
                  textAlign: TextAlign.end,
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: theme.colorScheme.primary,
                    letterSpacing: 1,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ...grades.map((g) {
            final typeLabel = g.examType ?? g.examName;
            return Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: () => onTapGrade(g),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  child: Row(
                    children: [
                      Expanded(
                        flex: 5,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              g.examName,
                              style: theme.textTheme.bodyMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              '$typeLabel · ${_formatDate(g.date)}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Expanded(
                        flex: 2,
                        child: Text(
                          '${g.weightPercent} %',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      Expanded(
                        flex: 2,
                        child: Text(
                          StudyGradeCalculator.formatGrade(g.grade),
                          textAlign: TextAlign.end,
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                            color: gradeColor(g.grade, theme.colorScheme),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ],
      ),
    );
  }

  String _formatDate(DateTime d) =>
      '${d.day.toString().padLeft(2, '0')}.${d.month.toString().padLeft(2, '0')}.${d.year}';
}

class _EmptySubjectsState extends StatelessWidget {
  final VoidCallback onOpenSubjects;

  const _EmptySubjectsState({required this.onOpenSubjects});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.menu_book_outlined,
              size: 64,
              color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
            ),
            const SizedBox(height: 24),
            Text(
              'NOCH KEINE MODULE',
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: 0.5,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              'Der Notenrechner nutzt die Fächer aus dem Tab „Fächer“. Lege dort zuerst deine Module mit ECTS an.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 28),
            FilledButton.icon(
              onPressed: onOpenSubjects,
              icon: const Icon(Icons.arrow_forward),
              label: const Text('ZU FÄCHERN'),
              style: FilledButton.styleFrom(
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.zero,
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 14,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
