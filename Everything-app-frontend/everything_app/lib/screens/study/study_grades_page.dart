import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/study_grade.dart';
import '../../../models/study_subject.dart';
import 'widgets/study_kinetic_card.dart';

class StudyGradesPage extends StatefulWidget {
  const StudyGradesPage({super.key});

  @override
  State<StudyGradesPage> createState() => _StudyGradesPageState();
}

class _StudyGradesPageState extends State<StudyGradesPage> {
  String? _expandedSubjectId;
  double _targetGpa = 2.0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final subjects = provider.subjects;
    final grades = provider.grades;

    // Helper functions for Notan-style calculations
    double calculateSubjectAverage(List<StudyGrade> subjectGrades) {
      if (subjectGrades.isEmpty) return 0.0;
      double weightedSum = 0;
      double totalWeight = 0;
      for (final g in subjectGrades) {
        weightedSum += g.grade * g.weight;
        totalWeight += g.weight;
      }
      return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    double overallGpa = 0.0;
    double totalCompletedCredits = 0.0;
    double totalCredits = 0.0;

    for (final subject in subjects) {
      totalCredits += subject.creditPoints;
      final subGrades = grades.where((g) => g.subjectId == subject.id).toList();
      if (subGrades.isNotEmpty) {
        final subAvg = calculateSubjectAverage(subGrades);
        overallGpa += subAvg * subject.creditPoints;
        totalCompletedCredits += subject.creditPoints;
      }
    }

    if (totalCompletedCredits > 0) {
      overallGpa = overallGpa / totalCompletedCredits;
    } else {
      overallGpa = 0.0;
    }

    String getWunschnoteMessage() {
      if (subjects.isEmpty) {
        return 'Füge Fächer hinzu, um die Berechnung zu starten.';
      }
      if (totalCompletedCredits == 0) {
        return 'Trage Noten ein, um den Wunschnote-Rechner zu aktivieren.';
      }
      final remainingCredits = totalCredits - totalCompletedCredits;
      if (remainingCredits <= 0) {
        return 'Alle Fächer sind abgeschlossen.';
      }

      final neededGrade = (_targetGpa * totalCredits - overallGpa * totalCompletedCredits) / remainingCredits;

      if (neededGrade < 1.0) {
        return 'Glückwunsch! Das Ziel ist bereits gesichert.';
      } else if (neededGrade > 5.0) {
        return 'Ziel nicht mehr erreichbar (benötigt: ${neededGrade.toStringAsFixed(1).replaceAll('.', ',')}).';
      } else {
        return 'Benötigter Schnitt im Rest (${remainingCredits.toInt()} CP): ${neededGrade.toStringAsFixed(1).replaceAll('.', ',')}';
      }
    }

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: CustomScrollView(
        slivers: [
          // Section: GPA Circular Display
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Center(
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    // Blur backdrop
                    Container(
                      width: 140,
                      height: 140,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: theme.colorScheme.primary.withValues(alpha: 0.15),
                            blurRadius: 50,
                            spreadRadius: 10,
                          ),
                        ],
                      ),
                    ),
                    // Circular container
                    Container(
                      width: 160,
                      height: 160,
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                        ),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            overallGpa == 0.0 ? '--' : overallGpa.toStringAsFixed(1).replaceAll('.', ','),
                            style: theme.textTheme.displayMedium?.copyWith(
                              fontWeight: FontWeight.w900,
                              color: theme.colorScheme.onSurface,
                              letterSpacing: -2.0,
                              fontSize: 48,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'GESAMTSCHNITT',
                            style: theme.textTheme.labelSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.onSurfaceVariant,
                              letterSpacing: 1.5,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${totalCompletedCredits.toInt()} / ${totalCredits.toInt()} CP',
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
            ),
          ),

          // Section: Wunschnote (Target Grade)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: StudyKineticCard(
                backgroundColor: theme.colorScheme.surfaceContainerLow,
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'ZIELSCHNITT (WUNSCHNOTE)',
                      style: theme.textTheme.labelSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.primary,
                        letterSpacing: 1.5,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            IconButton(
                              onPressed: _targetGpa <= 1.0
                                  ? null
                                  : () {
                                      setState(() {
                                        _targetGpa = double.parse((_targetGpa - 0.1).toStringAsFixed(1));
                                      });
                                    },
                              icon: const Icon(Icons.remove),
                              style: IconButton.styleFrom(
                                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              _targetGpa.toStringAsFixed(1).replaceAll('.', ','),
                              style: theme.textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                                color: theme.colorScheme.onSurface,
                              ),
                            ),
                            const SizedBox(width: 12),
                            IconButton(
                              onPressed: _targetGpa >= 4.0
                                  ? null
                                  : () {
                                      setState(() {
                                        _targetGpa = double.parse((_targetGpa + 0.1).toStringAsFixed(1));
                                      });
                                    },
                              icon: const Icon(Icons.add),
                              style: IconButton.styleFrom(
                                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                              ),
                            ),
                          ],
                        ),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.only(left: 16),
                            child: Text(
                              getWunschnoteMessage(),
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                                fontWeight: FontWeight.bold,
                              ),
                              textAlign: TextAlign.right,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),

          // Section: Grades List Header
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'MEINE FÄCHER',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w900,
                      color: theme.colorScheme.onSurface,
                      letterSpacing: 0.5,
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.add, color: theme.colorScheme.primary),
                    style: IconButton.styleFrom(
                      backgroundColor: theme.colorScheme.surfaceContainerHigh,
                      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                    ),
                    onPressed: () => _showAddGradeDialog(context),
                  ),
                ],
              ),
            ),
          ),

          // Subjects list items
          if (subjects.isEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: StudyKineticCard(
                  backgroundColor: theme.colorScheme.surfaceContainerLow,
                  child: const Center(
                    child: Padding(
                      padding: EdgeInsets.all(20.0),
                      child: Text('Keine Fächer eingetragen.'),
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
                    final subGrades = grades.where((g) => g.subjectId == subject.id).toList();
                    final subAvg = calculateSubjectAverage(subGrades);
                    final isExpanded = _expandedSubjectId == subject.id;
                    final isExcellent = subAvg > 0.0 && subAvg <= 1.5;

                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: StudyKineticCard(
                        backgroundColor: theme.colorScheme.surfaceContainerLow,
                        padding: const EdgeInsets.all(20),
                        onTap: () {
                          setState(() {
                            _expandedSubjectId = isExpanded ? null : subject.id;
                          });
                        },
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
                                      subject.name,
                                      style: theme.textTheme.titleMedium?.copyWith(
                                        fontWeight: FontWeight.bold,
                                        color: theme.colorScheme.onSurface,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      '${subject.creditPoints} CP • ${subGrades.length} Leistung(en)',
                                      style: theme.textTheme.bodySmall?.copyWith(
                                        color: theme.colorScheme.onSurfaceVariant,
                                      ),
                                    ),
                                  ],
                                ),
                                Container(
                                  width: 48,
                                  height: 48,
                                  decoration: BoxDecoration(
                                    color: subAvg == 0.0
                                        ? theme.colorScheme.surfaceContainerHighest
                                        : (isExcellent
                                            ? theme.colorScheme.primary
                                            : theme.colorScheme.primary.withValues(alpha: 0.15)),
                                    shape: BoxShape.circle,
                                  ),
                                  child: Center(
                                    child: Text(
                                      subAvg == 0.0
                                          ? '--'
                                          : subAvg.toStringAsFixed(1).replaceAll('.', ','),
                                      style: theme.textTheme.titleMedium?.copyWith(
                                        fontWeight: FontWeight.bold,
                                        color: subAvg == 0.0
                                            ? theme.colorScheme.onSurfaceVariant
                                            : (isExcellent
                                                ? theme.colorScheme.onPrimary
                                                : theme.colorScheme.primary),
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            if (isExpanded) ...[
                              const SizedBox(height: 16),
                              const Divider(height: 1),
                              const SizedBox(height: 16),
                              Text(
                                'EINZELNOTEN',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.primary,
                                  letterSpacing: 1.0,
                                ),
                              ),
                              const SizedBox(height: 8),
                              if (subGrades.isEmpty)
                                Padding(
                                  padding: const EdgeInsets.symmetric(vertical: 8),
                                  child: Text(
                                    'Noch keine Noten für dieses Fach eingetragen.',
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                      fontStyle: FontStyle.italic,
                                    ),
                                  ),
                                )
                              else
                                ...subGrades.map((g) {
                                  return Padding(
                                    padding: const EdgeInsets.symmetric(vertical: 6),
                                    child: Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Text(
                                                g.examName,
                                                style: theme.textTheme.bodyMedium?.copyWith(
                                                  color: theme.colorScheme.onSurface,
                                                  fontWeight: FontWeight.bold,
                                                ),
                                              ),
                                              Text(
                                                'Gewichtung: ${(g.weight * 100).toInt()}%',
                                                style: theme.textTheme.bodySmall?.copyWith(
                                                  color: theme.colorScheme.onSurfaceVariant,
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                        Row(
                                          children: [
                                            Text(
                                              g.grade.toStringAsFixed(1).replaceAll('.', ','),
                                              style: theme.textTheme.titleMedium?.copyWith(
                                                fontWeight: FontWeight.bold,
                                                color: theme.colorScheme.onSurface,
                                              ),
                                            ),
                                            const SizedBox(width: 8),
                                            IconButton(
                                              onPressed: () => _showEditGradeDialog(context, g),
                                              icon: const Icon(Icons.delete_outline, size: 18),
                                              color: theme.colorScheme.error,
                                            ),
                                          ],
                                        ),
                                      ],
                                    ),
                                  );
                                }),
                              const SizedBox(height: 12),
                              Align(
                                alignment: Alignment.centerRight,
                                child: TextButton.icon(
                                  onPressed: () => _showAddGradeDialog(context, prefilledSubjectId: subject.id),
                                  icon: const Icon(Icons.add, size: 16),
                                  label: const Text('Note hinzufügen'),
                                  style: TextButton.styleFrom(
                                    foregroundColor: theme.colorScheme.primary,
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                    backgroundColor: theme.colorScheme.surfaceContainerHighest,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    );
                  },
                  childCount: subjects.length,
                ),
              ),
            ),

          const SliverToBoxAdapter(
            child: SizedBox(height: 100),
          ),
        ],
      ),
    );
  }

  void _showAddGradeDialog(BuildContext context, {String? prefilledSubjectId}) {
    final examCtrl = TextEditingController(text: 'Klausur');
    final gradeCtrl = TextEditingController();
    final weightCtrl = TextEditingController(text: '1.0');
    final provider = context.read<StudyProvider>();
    String? selectedSubId = prefilledSubjectId ?? (provider.subjects.isNotEmpty ? provider.subjects.first.id : null);

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) {
          return AlertDialog(
            title: const Text('Note hinzufügen'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (prefilledSubjectId == null && provider.subjects.isNotEmpty)
                    DropdownButtonFormField<String>(
                      initialValue: selectedSubId,
                      decoration: const InputDecoration(labelText: 'Fach'),
                      items: provider.subjects.map((s) {
                        return DropdownMenuItem(value: s.id, child: Text(s.name));
                      }).toList(),
                      onChanged: (val) => setSt(() => selectedSubId = val),
                    )
                  else if (prefilledSubjectId != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'Fach: ${provider.subjects.firstWhere((s) => s.id == prefilledSubjectId, orElse: () => StudySubject(id: '', name: 'Unbekannt')).name}',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                        ),
                      ),
                    ),
                  TextField(controller: examCtrl, decoration: const InputDecoration(labelText: 'Leistungsnachweis')),
                  TextField(
                    controller: gradeCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Note (z.B. 1.7)'),
                  ),
                  TextField(
                    controller: weightCtrl,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Gewichtung'),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
              FilledButton(
                onPressed: () {
                  final gr = double.tryParse(gradeCtrl.text.replaceAll(',', '.')) ?? 2.0;
                  final wt = double.tryParse(weightCtrl.text.replaceAll(',', '.')) ?? 1.0;
                  if (selectedSubId != null) {
                    provider.addGrade(StudyGrade(
                      id: 'g${DateTime.now().millisecondsSinceEpoch}',
                      subjectId: selectedSubId!,
                      examName: examCtrl.text.trim(),
                      grade: gr,
                      weight: wt,
                      date: DateTime.now(),
                    ));
                    Navigator.pop(ctx);
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

  void _showEditGradeDialog(BuildContext context, StudyGrade grade) {
    final provider = context.read<StudyProvider>();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Eintrag verwalten'),
        content: Text('Möchtest du diese Leistung (${grade.examName}) aus dem Rechner entfernen?'),
        actions: [
          TextButton(
            onPressed: () {
              provider.deleteGrade(grade.id);
              Navigator.pop(ctx);
            },
            child: const Text('Löschen', style: TextStyle(color: Colors.red)),
          ),
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
        ],
      ),
    );
  }
}
