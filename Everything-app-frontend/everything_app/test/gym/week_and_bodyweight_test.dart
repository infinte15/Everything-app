import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/models/gym/gym_models.dart';
import 'package:everything_app/screens/gym/widgets/week_strip_card.dart';

GymRoutine _routine({required int id, required String name, int? weekday}) =>
    GymRoutine(id: id, name: name, preferredWeekday: weekday);

/// Eine abgeschlossene Einheit. Der Streifen unterscheidet bewusst: offene Sessions sind
/// das, was der Scheduler eingeplant hat, und zählen als *geplant*, nicht als trainiert.
GymSession _session(DateTime at, {bool completed = true}) =>
    GymSession(id: 1, name: 'Push', startTime: at, isCompleted: completed);

void main() {
  Widget wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

  Widget strip({
    List<GymRoutine> routines = const [],
    List<GymSession> sessions = const [],
    String? active,
  }) =>
      wrap(WeekStripCard(
        routines: routines,
        sessions: sessions,
        activeWorkoutName: active,
        onStart: () {},
        onResume: () {},
      ));

  group('Geplante Uhrzeit', () {
    /// Die Frage, mit der man morgens in die App sieht, ist "wann heute?" - der Wochentag
    /// allein beantwortet sie nicht. Die Uhrzeit kommt vom Smart Scheduler.
    testWidgets('die Heute-Zeile nennt die Uhrzeit der geplanten Einheit', (tester) async {
      final now = DateTime.now();
      final at = DateTime(now.year, now.month, now.day, 18, 30);

      await tester.pumpWidget(strip(
        routines: [_routine(id: 1, name: 'Push A', weekday: now.weekday)],
        sessions: [_session(at, completed: false)],
      ));

      expect(find.text('HEUTE · 18:30'), findsOneWidget);
    });

    testWidgets('einstellige Minuten und Stunden bekommen ihre Null', (tester) async {
      final now = DateTime.now();
      final at = DateTime(now.year, now.month, now.day, 7, 5);

      await tester.pumpWidget(strip(
        routines: [_routine(id: 1, name: 'Push A', weekday: now.weekday)],
        sessions: [_session(at, completed: false)],
      ));

      expect(find.text('HEUTE · 07:05'), findsOneWidget);
    });

    testWidgets('ohne geplante Einheit bleibt es beim blossen HEUTE', (tester) async {
      final now = DateTime.now();
      await tester.pumpWidget(strip(
        routines: [_routine(id: 1, name: 'Push A', weekday: now.weekday)],
      ));

      expect(find.text('HEUTE'), findsOneWidget);
    });

    /// Nach dem Training ist die geplante Uhrzeit Vergangenheit und stuende der Meldung
    /// "erledigt" nur im Weg.
    testWidgets('nach dem Training verschwindet die Uhrzeit wieder', (tester) async {
      final now = DateTime.now();
      final at = DateTime(now.year, now.month, now.day, 18, 30);

      await tester.pumpWidget(strip(
        routines: [_routine(id: 1, name: 'Push A', weekday: now.weekday)],
        sessions: [
          _session(at, completed: false),
          GymSession(id: 2, name: 'Push', startTime: at, isCompleted: true),
        ],
      ));

      expect(find.text('Training erledigt'), findsOneWidget);
      expect(find.text('HEUTE'), findsOneWidget);
    });
  });

  group('Heute-Zeile', () {
    testWidgets('ohne Routine für heute steht dort Ruhetag', (tester) async {
      await tester.pumpWidget(strip());

      expect(find.text('Ruhetag'), findsOneWidget);
      expect(find.text('Starten'), findsNothing);
    });

    testWidgets('nennt die Routine des heutigen Wochentags', (tester) async {
      final today = DateTime.now().weekday;
      await tester.pumpWidget(strip(routines: [
        _routine(id: 1, name: 'Push A', weekday: today),
        _routine(id: 2, name: 'Pull A', weekday: today == 7 ? 1 : today + 1),
      ]));

      expect(find.text('Push A'), findsOneWidget);
      expect(find.text('Pull A'), findsNothing);
      expect(find.text('Starten'), findsOneWidget);
    });

    /// Ein erledigter Tag darf nicht weiter nach dem Training fragen - der Punkt im
    /// Streifen weiß es schon, die Zeile wirkte sonst wie eine offene Aufgabe.
    testWidgets('nach dem Training fragt die Zeile nicht mehr danach', (tester) async {
      final today = DateTime.now().weekday;
      await tester.pumpWidget(strip(
        routines: [_routine(id: 1, name: 'Push A', weekday: today)],
        sessions: [_session(DateTime.now())],
      ));

      expect(find.text('Training erledigt'), findsOneWidget);
      expect(find.text('Erledigt'), findsOneWidget);
      expect(find.text('Starten'), findsNothing);
    });

    testWidgets('ein laufendes Training schlägt alles andere', (tester) async {
      final today = DateTime.now().weekday;
      await tester.pumpWidget(strip(
        routines: [_routine(id: 1, name: 'Push A', weekday: today)],
        sessions: [_session(DateTime.now())],
        active: 'Pull A',
      ));

      expect(find.text('Pull A'), findsOneWidget);
      expect(find.text('Fortsetzen'), findsOneWidget);
    });

    testWidgets('ein Training an einem anderen Tag macht heute nicht erledigt', (tester) async {
      await tester.pumpWidget(strip(
        sessions: [_session(DateTime.now().subtract(const Duration(days: 3)))],
      ));

      expect(find.text('Ruhetag'), findsOneWidget);
      expect(find.text('Erledigt'), findsNothing);
    });

    /// Der Smart Scheduler legt Einheiten als offene Sessions in die Zukunft. Wären die
    /// "erledigt", stünde am Montag schon die ganze Woche als trainiert da.
    testWidgets('eine offene Session ist geplant, nicht erledigt', (tester) async {
      await tester.pumpWidget(strip(
        sessions: [_session(DateTime.now(), completed: false)],
      ));

      expect(find.text('Training erledigt'), findsNothing);
      expect(find.text('Push'), findsOneWidget);
      expect(find.text('Starten'), findsOneWidget);
    });
  });

  group('Gewichtsverlauf', () {
    GymBodyWeightSeries series(List<double> weights, {double? target}) {
      final entries = [
        for (var i = 0; i < weights.length; i++)
          {
            'date': DateTime.now()
                .subtract(Duration(days: weights.length - i))
                .toIso8601String()
                .substring(0, 10),
            'weightKg': weights[i],
          }
      ];
      return GymBodyWeightSeries.fromMap({
        'entries': entries,
        'latest': entries.isEmpty ? null : entries.last,
        'previous': entries.length > 1 ? entries[entries.length - 2] : null,
        'targetWeightKg': target,
      });
    }

    test('ohne Eintrag gibt es keine Veränderung und kein Ziel', () {
      final s = series([]);

      expect(s.isEmpty, isTrue);
      expect(s.delta, isNull);
      expect(s.toTarget, isNull);
    });

    test('beim ersten Wert gibt es nichts zu vergleichen', () {
      expect(series([80.0]).delta, isNull);
    });

    test('die Veränderung ist die Differenz zum vorherigen Wert', () {
      expect(series([80.0, 78.5]).delta, closeTo(-1.5, 0.001));
      expect(series([78.5, 80.0]).delta, closeTo(1.5, 0.001));
    });

    /// Ein unverändertes Gewicht ist keine Bewegung. Ohne die Schwelle stünde neben der
    /// Zahl ein "0,0" mit Pfeil, das nach Veränderung aussieht, wo keine war.
    test('ein unverändertes Gewicht zeigt keine Veränderung', () {
      expect(series([80.0, 80.0]).delta, isNull);
      expect(series([80.0, 80.02]).delta, isNull);
    });

    test('der Abstand zum Ziel hat ein Vorzeichen', () {
      expect(series([80.0], target: 75.0).toTarget, closeTo(-5.0, 0.001));
      expect(series([70.0], target: 75.0).toTarget, closeTo(5.0, 0.001));
    });

    test('ohne Ziel gibt es keinen Abstand', () {
      expect(series([80.0]).toTarget, isNull);
    });
  });
}
