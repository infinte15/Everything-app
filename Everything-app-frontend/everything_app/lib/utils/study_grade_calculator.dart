import 'package:flutter/material.dart' show Color, ColorScheme;

import '../models/study_grade.dart';
import '../models/study_subject.dart';

/// Studium (university) grade calculations — ECTS-weighted module average.
class StudyGpaSnapshot {
  final double overallGpa;
  final double totalEcts;
  final double completedEcts;
  final int gradedSubjectCount;
  final int subjectCount;

  const StudyGpaSnapshot({
    required this.overallGpa,
    required this.totalEcts,
    required this.completedEcts,
    required this.gradedSubjectCount,
    required this.subjectCount,
  });

  double get progress =>
      totalEcts > 0 ? (completedEcts / totalEcts).clamp(0.0, 1.0) : 0.0;

  bool get hasGpa => gradedSubjectCount > 0 && overallGpa > 0;
}

class StudyGradeCalculator {
  /// Teilleistungen, die in den Modulschnitt eingehen.
  ///
  /// Ein Schein (`countsTowardGrade == false`) wird abgelegt und bestanden, verschiebt den
  /// Schnitt aber nicht — und darf deshalb auch nicht in die Gewichtungssumme einfließen,
  /// sonst gälte ein Modul fälschlich als vollständig bewertet.
  static List<StudyGrade> _counting(List<StudyGrade> grades) =>
      grades.where((g) => g.countsTowardGrade).toList();

  static double subjectAverage(List<StudyGrade> grades) {
    final counting = _counting(grades);
    if (counting.isEmpty) return 0;
    double weightedSum = 0;
    int totalWeight = 0;
    for (final g in counting) {
      weightedSum += g.grade * g.weightPercent;
      totalWeight += g.weightPercent;
    }
    return totalWeight > 0 ? weightedSum / totalWeight : 0;
  }

  static int subjectWeightTotal(List<StudyGrade> grades) =>
      _counting(grades).fold<int>(0, (sum, g) => sum + g.weightPercent);

  static bool subjectIsComplete(List<StudyGrade> grades) =>
      _counting(grades).isNotEmpty && subjectWeightTotal(grades) >= 100;

  /// ECTS-gewichteter Gesamtschnitt.
  ///
  /// [semesterId] filtert über die echte Verknüpfung und ist der Weg, den die Oberfläche
  /// nimmt. [semesterFilter] (Freitext) bleibt für Module, die noch keinem Semester
  /// zugeordnet sind, und für ältere Aufrufer erhalten.
  static StudyGpaSnapshot computeGpa({
    required List<StudySubject> subjects,
    required List<StudyGrade> grades,
    String? semesterFilter,
    String? semesterId,
  }) {
    List<StudySubject> filtered = subjects;
    if (semesterId != null) {
      filtered = subjects.where((s) => s.semesterId == semesterId).toList();
    } else if (semesterFilter != null && semesterFilter != 'Alle') {
      filtered = subjects.where((s) => s.semester == semesterFilter).toList();
    }

    double totalEcts = 0;
    double completedEcts = 0;
    double weightedGpaSum = 0;
    int gradedCount = 0;

    for (final subject in filtered) {
      final ects = subject.creditPoints;
      if (ects <= 0) continue;
      totalEcts += ects;

      final subGrades =
          grades.where((g) => g.subjectId == subject.id).toList();
      if (subGrades.isEmpty) continue;

      final avg = subjectAverage(subGrades);
      if (avg <= 0) continue;

      weightedGpaSum += avg * ects;
      completedEcts += ects;
      gradedCount++;
    }

    final overall =
        completedEcts > 0 ? weightedGpaSum / completedEcts : 0.0;

    return StudyGpaSnapshot(
      overallGpa: overall,
      totalEcts: totalEcts,
      completedEcts: completedEcts,
      gradedSubjectCount: gradedCount,
      subjectCount: filtered.length,
    );
  }

  static double? neededAverageForTarget({
    required StudyGpaSnapshot snapshot,
    required double targetGpa,
  }) {
    if (snapshot.totalEcts <= 0) return null;
    final remaining = snapshot.totalEcts - snapshot.completedEcts;
    if (remaining <= 0) return null;
    if (!snapshot.hasGpa) return targetGpa;

    return (targetGpa * snapshot.totalEcts -
            snapshot.overallGpa * snapshot.completedEcts) /
        remaining;
  }

  static String formatGrade(double value) =>
      value.toStringAsFixed(1).replaceAll('.', ',');

  static String wunschnoteMessage({
    required StudyGpaSnapshot snapshot,
    required double targetGpa,
    required bool hasSubjects,
  }) {
    if (!hasSubjects) {
      return 'Lege zuerst Fächer unter „Fächer“ an.';
    }
    if (snapshot.completedEcts == 0) {
      return 'Trage Leistungsnachweise ein, um den Zielschnitt-Rechner zu nutzen.';
    }
    final remaining = snapshot.totalEcts - snapshot.completedEcts;
    if (remaining <= 0) {
      return 'Alle Module mit ECTS sind bewertet.';
    }

    final needed = neededAverageForTarget(
      snapshot: snapshot,
      targetGpa: targetGpa,
    );
    if (needed == null) return '';

    // Die beiden Fälle waren vertauscht. In der deutschen Skala ist 1,0 die beste und 5,0
    // die schlechteste Note, der nötige Schnitt ist also eine OBERGRENZE:
    //   needed < 1,0  → selbst mit lauter 1,0 auf alle offenen ECTS reicht es nicht mehr.
    //   needed > 5,0  → selbst im schlechtesten Fall wird das Ziel gehalten.
    // Vorher meldete der Rechner „Ziel bereits erreicht — weiter so!" genau dann, wenn das
    // Ziel gerade unerreichbar geworden war.
    if (needed < 1.0) {
      return 'Ziel nicht mehr erreichbar — auch mit 1,0 auf alle offenen ECTS.';
    }
    if (needed > 5.0) {
      return 'Ziel sicher — auch im schlechtesten Fall.';
    }
    return 'Höchstens Ø ${formatGrade(needed)} auf die restlichen ${remaining.toInt()} ECTS';
  }
}

Color gradeColor(double grade, ColorScheme scheme) {
  if (grade <= 0) return scheme.onSurfaceVariant;
  if (grade <= 1.5) return const Color(0xFF4ADE80);
  if (grade <= 2.5) return scheme.primary;
  if (grade <= 3.5) return const Color(0xFFF59E0B);
  if (grade <= 4.0) return const Color(0xFFFB923C);
  return scheme.error;
}
