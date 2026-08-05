import 'package:everything_app/models/study_grade.dart';
import 'package:everything_app/models/study_subject.dart';
import 'package:everything_app/utils/study_grade_calculator.dart';
import 'package:flutter_test/flutter_test.dart';

StudySubject subject(String id, int ects, {String? semesterId}) => StudySubject(
      id: id,
      name: 'Modul $id',
      creditPoints: ects,
      semesterId: semesterId,
      semester: semesterId == null ? null : 'Semester $semesterId',
    );

StudyGrade grade(
  String subjectId,
  double value, {
  int weight = 100,
  bool counts = true,
}) =>
    StudyGrade(
      id: 'g$subjectId$value$weight',
      subjectId: subjectId,
      examName: 'Leistung',
      grade: value,
      weightPercent: weight,
      countsTowardGrade: counts,
      date: DateTime(2026, 2, 14),
    );

void main() {
  group('subjectAverage', () {
    test('gewichtet die Teilleistungen', () {
      final avg = StudyGradeCalculator.subjectAverage([
        grade('1', 2.0, weight: 50),
        grade('1', 1.3, weight: 50),
      ]);

      expect(avg, closeTo(1.65, 0.001));
    });

    test('ungleiche Gewichte zaehlen anteilig', () {
      final avg = StudyGradeCalculator.subjectAverage([
        grade('1', 1.0, weight: 25),
        grade('1', 3.0, weight: 75),
      ]);

      expect(avg, closeTo(2.5, 0.001));
    });

    // Ein Schein wird abgelegt und bestanden, verschiebt den Schnitt aber nicht.
    test('ein Schein bewegt den Schnitt nicht', () {
      final withoutSchein = StudyGradeCalculator.subjectAverage([
        grade('1', 2.0, weight: 100),
      ]);
      final withSchein = StudyGradeCalculator.subjectAverage([
        grade('1', 2.0, weight: 100),
        grade('1', 5.0, weight: 100, counts: false),
      ]);

      expect(withSchein, withoutSchein,
          reason: 'die 5,0 des Scheins darf den Modulschnitt nicht anheben');
    });

    // Ohne diesen Ausschluss gaelte ein Modul mit Klausur (100%) + Schein als
    // 200% gewichtet und damit faelschlich als uebervollstaendig.
    test('ein Schein zaehlt nicht in die Gewichtungssumme', () {
      final grades = [
        grade('1', 2.0, weight: 50),
        grade('1', 4.0, weight: 100, counts: false),
      ];

      expect(StudyGradeCalculator.subjectWeightTotal(grades), 50);
      expect(StudyGradeCalculator.subjectIsComplete(grades), isFalse,
          reason: 'es fehlen noch 50% echte Gewichtung');
    });

    test('ein Modul nur mit Schein hat keinen Schnitt', () {
      expect(
        StudyGradeCalculator.subjectAverage([grade('1', 2.0, counts: false)]),
        0,
      );
    });
  });

  group('computeGpa', () {
    test('gewichtet die Modulschnitte nach ECTS', () {
      final subjects = [subject('1', 6), subject('2', 3)];
      final grades = [
        grade('1', 2.0, weight: 50),
        grade('1', 1.3, weight: 50),   // Modul 1: 1,65 bei 6 ECTS
        grade('2', 3.0),               // Modul 2: 3,0  bei 3 ECTS
      ];

      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: subjects,
        grades: grades,
      );

      // (1,65 * 6 + 3,0 * 3) / 9 = 2,1
      expect(snapshot.overallGpa, closeTo(2.1, 0.001));
      expect(snapshot.totalEcts, 9);
      expect(snapshot.completedEcts, 9);
      expect(snapshot.gradedSubjectCount, 2);
      expect(snapshot.hasGpa, isTrue);
    });

    test('unbewertete Module zaehlen in totalEcts, aber nicht in den Schnitt', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 6), subject('2', 6)],
        grades: [grade('1', 2.0)],
      );

      expect(snapshot.totalEcts, 12);
      expect(snapshot.completedEcts, 6);
      expect(snapshot.overallGpa, closeTo(2.0, 0.001));
      expect(snapshot.progress, closeTo(0.5, 0.001));
    });

    test('Module ohne ECTS werden uebergangen', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 0)],
        grades: [grade('1', 1.0)],
      );

      expect(snapshot.totalEcts, 0);
      expect(snapshot.hasGpa, isFalse);
    });

    // Der Filter laeuft jetzt ueber die echte Verknuepfung statt ueber den Freitext.
    test('semesterId filtert auf die Module dieses Semesters', () {
      final subjects = [
        subject('1', 6, semesterId: '10'),
        subject('2', 6, semesterId: '20'),
      ];
      final grades = [grade('1', 1.0), grade('2', 4.0)];

      final first = StudyGradeCalculator.computeGpa(
        subjects: subjects,
        grades: grades,
        semesterId: '10',
      );

      expect(first.overallGpa, closeTo(1.0, 0.001));
      expect(first.subjectCount, 1);
    });

    test('ohne Filter zaehlen alle Semester zusammen', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [
          subject('1', 6, semesterId: '10'),
          subject('2', 6, semesterId: '20'),
        ],
        grades: [grade('1', 1.0), grade('2', 4.0)],
      );

      expect(snapshot.overallGpa, closeTo(2.5, 0.001));
      expect(snapshot.subjectCount, 2);
    });

    test('ein Schein verschiebt auch den Gesamtschnitt nicht', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 6)],
        grades: [grade('1', 2.0), grade('1', 5.0, counts: false)],
      );

      expect(snapshot.overallGpa, closeTo(2.0, 0.001));
    });
  });

  group('Zielschnitt', () {
    test('rechnet die noetige Note auf die restlichen ECTS aus', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 30), subject('2', 30)],
        grades: [grade('1', 2.0)],
      );

      final needed = StudyGradeCalculator.neededAverageForTarget(
        snapshot: snapshot,
        targetGpa: 1.5,
      );

      // 1,5 * 60 = 90; bereits 2,0 * 30 = 60; also 30 auf 30 ECTS => 1,0
      expect(needed, closeTo(1.0, 0.001));
    });

    test('meldet ein nicht mehr erreichbares Ziel', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 50), subject('2', 10)],
        grades: [grade('1', 4.0)],
      );

      final needed = StudyGradeCalculator.neededAverageForTarget(
        snapshot: snapshot,
        targetGpa: 1.0,
      );
      expect(needed! < 1.0, isTrue,
          reason: 'unter 1,0 heisst: selbst eine glatte 1,0 reicht nicht mehr');

      final message = StudyGradeCalculator.wunschnoteMessage(
        snapshot: snapshot,
        targetGpa: 1.0,
        hasSubjects: true,
      );

      expect(message, contains('nicht mehr erreichbar'));
    });

    test('meldet ein gesichertes Ziel', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 30), subject('2', 30)],
        grades: [grade('1', 1.0)],
      );

      // (3,5 * 60 - 1,0 * 30) / 30 = 6,0 - schlechter als die schlechteste Note,
      // das Ziel kann also nicht mehr verfehlt werden.
      final message = StudyGradeCalculator.wunschnoteMessage(
        snapshot: snapshot,
        targetGpa: 3.5,
        hasSubjects: true,
      );

      expect(message, contains('sicher'));
    });

    test('nennt den noetigen Schnitt als Obergrenze', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 30), subject('2', 30)],
        grades: [grade('1', 1.0)],
      );

      // (2,5 * 60 - 1,0 * 30) / 30 = 4,0
      final message = StudyGradeCalculator.wunschnoteMessage(
        snapshot: snapshot,
        targetGpa: 2.5,
        hasSubjects: true,
      );

      expect(message, contains('Höchstens'));
      expect(message, contains('4,0'));
      expect(message, contains('30 ECTS'));
    });

    test('ohne offene ECTS gibt es nichts mehr zu rechnen', () {
      final snapshot = StudyGradeCalculator.computeGpa(
        subjects: [subject('1', 6)],
        grades: [grade('1', 2.0)],
      );

      expect(
        StudyGradeCalculator.neededAverageForTarget(
            snapshot: snapshot, targetGpa: 1.0),
        isNull,
      );
      expect(
        StudyGradeCalculator.wunschnoteMessage(
            snapshot: snapshot, targetGpa: 1.0, hasSubjects: true),
        contains('Alle Module'),
      );
    });
  });

  group('formatGrade', () {
    test('nutzt das deutsche Dezimalkomma', () {
      expect(StudyGradeCalculator.formatGrade(2.0), '2,0');
      expect(StudyGradeCalculator.formatGrade(1.3), '1,3');
      expect(StudyGradeCalculator.formatGrade(2.75), '2,8');
    });
  });
}
