import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:everything_app/models/gym/gym_models.dart';
import 'package:everything_app/providers/sports_provider.dart';
import 'package:everything_app/services/sports_service.dart';

/// Nur `startWorkout` wird gebraucht: alles Weitere in diesen Tests passiert im Provider,
/// ohne dass noch einmal jemand ans Netz muss.
class _FakeSportsService extends SportsService {
  _FakeSportsService(this.response);

  final Map<String, dynamic> response;

  @override
  Future<Map<String, dynamic>> startWorkout(
          {int? routineId, int? sessionId, String? name}) async =>
      response;
}

Map<String, dynamic> _planned({
  required int exerciseId,
  required String name,
  int targetSets = 3,
  double? targetWeight,
  int? supersetGroup,
  Map<String, dynamic>? progression,
}) =>
    {
      'exerciseId': exerciseId,
      'name': name,
      'targetSets': targetSets,
      'targetRepsMin': 5,
      'targetRepsMax': 5,
      'targetWeight': targetWeight,
      'supersetGroup': supersetGroup,
      'progression': progression,
    };

Map<String, dynamic> _progression({
  double? weight,
  int? reps,
  int? sets,
  String kind = 'up',
  List<Map<String, dynamic>> warmup = const [],
}) =>
    {
      'policy': 'LINEAR',
      'kind': kind,
      'weight': weight,
      'reps': reps,
      'sets': sets,
      'why': 'weil',
      'warmup': warmup,
    };

Future<SportsProvider> start(List<Map<String, dynamic>> exercises) async {
  SharedPreferences.setMockInitialValues({});
  final provider = SportsProvider(
    service: _FakeSportsService({
      'sessionId': 1,
      'name': 'Training',
      'plannedExercises': exercises,
    }),
  );
  await provider.startEmptyWorkout();
  return provider;
}

List<GymLoggedSet> setsOf(SportsProvider p, [int index = 0]) =>
    p.activeWorkout!.exercises[index].sets;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Vorgabe aus der Progression', () {
    test('setzt Gewicht und Wiederholungen für jeden Satz', () async {
      final p = await start([
        _planned(
          exerciseId: 1,
          name: 'bench press',
          targetWeight: 60,
          progression: _progression(weight: 62.5, reps: 5, sets: 3),
        ),
      ]);

      final sets = setsOf(p);
      expect(sets.length, 3);
      expect(sets.every((s) => s.weight == 62.5), isTrue);
      expect(sets.every((s) => s.reps == 5), isTrue);
    });

    /// Ohne Automatik bleibt es beim alten Verhalten - sonst würde eine Routine ohne
    /// Regel plötzlich anders vorbelegt sein als vorher.
    test('ohne Automatik gilt weiter die Routinen-Vorgabe', () async {
      final p = await start([
        _planned(
          exerciseId: 1,
          name: 'bench press',
          targetWeight: 60,
          progression: _progression(kind: 'off', weight: 999),
        ),
      ]);

      expect(setsOf(p).first.weight, 60);
    });
  });

  group('Aufwärmrampe', () {
    Future<SportsProvider> withRamp() => start([
          _planned(
            exerciseId: 1,
            name: 'bench press',
            progression: _progression(weight: 100, reps: 5, sets: 3, warmup: [
              {'weight': 40.0, 'reps': 8, 'percent': 40},
              {'weight': 60.0, 'reps': 5, 'percent': 60},
              {'weight': 80.0, 'reps': 3, 'percent': 80},
            ]),
          ),
        ]);

    test('legt die Sätze vor die Arbeitssätze und nummeriert durch', () async {
      final p = await withRamp();
      expect(p.applyWarmupRamp(0), isTrue);

      final sets = setsOf(p);
      expect(sets.length, 6);
      expect(sets.take(3).every((s) => s.setType == GymSetType.warmup), isTrue);
      expect(sets.map((s) => s.setNumber), [1, 2, 3, 4, 5, 6]);
      expect(sets[0].weight, 40.0);
      expect(sets[3].weight, 100.0);
    });

    /// Zweimal antippen darf die halbe Rampe nicht doppelt anlegen.
    test('ist idempotent', () async {
      final p = await withRamp();
      expect(p.applyWarmupRamp(0), isTrue);
      expect(p.applyWarmupRamp(0), isFalse);
      expect(setsOf(p).length, 6);
    });

    /// Der laufende Bildschirm muss dieselbe Zahl zeigen wie die fertige Einheit danach.
    test('Aufwärmsätze zählen nicht in die Satzanzahl', () async {
      final p = await withRamp();
      p.applyWarmupRamp(0);

      expect(setsOf(p).length, 6, reason: '3 Aufwärm- + 3 Arbeitssätze stehen da');
      expect(p.activeWorkoutStats.totalSets, 3, reason: 'gezählt werden nur Arbeitssätze');
    });

    /// Drei Minuten nach der leeren Stange sind keine Pause, sondern eine Unterbrechung.
    test('nach einem Aufwärmsatz ist die Pause gedeckelt', () async {
      final p = await withRamp();
      p.applyWarmupRamp(0);
      p.setExerciseRestSeconds(0, 180);

      p.toggleSetDone(0, 0);
      expect(p.restSecondsRemaining, 60);

      p.toggleSetDone(0, 3);
      expect(p.restSecondsRemaining, 180);
    });

    test('Aufwärmsätze zählen nicht ins Volumen', () async {
      final p = await withRamp();
      p.applyWarmupRamp(0);
      for (var i = 0; i < setsOf(p).length; i++) {
        p.toggleSetDone(0, i, startRest: false);
      }

      // Nur die drei Arbeitssätze: 3 × 100 × 5.
      expect(p.activeWorkoutStats.volumeKg, 1500);
    });
  });

  group('Zusatzsätze', () {
    Future<SportsProvider> withWork() async {
      final p = await start([
        _planned(
          exerciseId: 1,
          name: 'bench press',
          progression: _progression(weight: 100, reps: 5, sets: 3),
        ),
      ]);
      return p;
    }

    test('ein Dropsatz hängt am Arbeitssatz und wiegt weniger', () async {
      final p = await withWork();
      p.addDropSet(0, 0);

      final sets = setsOf(p);
      expect(sets.length, 4);
      expect(sets[1].setType, GymSetType.drop);
      expect(sets[1].parentSetNumber, 1);
      expect(sets[1].weight, 80.0);
      // Die Nummerierung bleibt lückenlos, die Verweise ziehen mit.
      expect(sets.map((s) => s.setNumber), [1, 2, 3, 4]);
    });

    test('ein Rest-Pause-Cluster behält das Gewicht', () async {
      final p = await withWork();
      p.addRestPauseSet(0, 0);

      expect(setsOf(p)[1].setType, GymSetType.restpause);
      expect(setsOf(p)[1].weight, 100.0);
    });

    /// Der Kern der Unterscheidung: der Dropsatz ist eigene Arbeit, der Rest-Pause-Cluster
    /// steckt schon im Arbeitssatz darüber. Beides gleich zu zählen war der Fehler, den
    /// openGym zweimal gemacht hat.
    test('Dropsatz zählt ins Volumen, Rest-Pause nicht', () async {
      final p = await withWork();
      p.addDropSet(0, 0);
      p.addRestPauseSet(0, 0);

      for (final set in setsOf(p)) {
        set.reps ??= 3;
      }
      for (var i = 0; i < setsOf(p).length; i++) {
        p.toggleSetDone(0, i, startRest: false);
      }

      // 3 Arbeitssätze à 100 × 5 = 1500, plus Dropsatz 80 × 5 = 400.
      expect(p.activeWorkoutStats.volumeKg, 1900);
    });

    test('ein Zusatzsatz bekommt keinen eigenen Zusatzsatz', () async {
      final p = await withWork();
      p.addDropSet(0, 0);
      p.addDropSet(0, 1);

      expect(setsOf(p).length, 4);
    });

    /// Wird ein Satz auf eine normale Art zurückgestellt, darf er nicht weiter an einem
    /// Arbeitssatz hängen - sonst zählte er als Zusatz und gleichzeitig als Arbeit.
    test('eine andere Satzart löst die Verknüpfung', () async {
      final p = await withWork();
      p.addDropSet(0, 0);
      p.setSetType(0, 1, GymSetType.normal);

      expect(setsOf(p)[1].parentSetNumber, isNull);
    });
  });

  group('Supersatz', () {
    Future<SportsProvider> pair() => start([
          _planned(exerciseId: 1, name: 'bench press', supersetGroup: 1),
          _planned(exerciseId: 2, name: 'barbell row', supersetGroup: 1),
          _planned(exerciseId: 3, name: 'curl'),
        ]);

    test('nach dem Satz kommt die Partnerübung statt der Pause', () async {
      final p = await pair();
      p.toggleSetDone(0, 0);

      expect(p.supersetNextIndex, 1);
      expect(p.isResting, isFalse);
    });

    test('ist die Runde voll, läuft die Pause', () async {
      final p = await pair();
      p.toggleSetDone(0, 0);
      p.toggleSetDone(1, 0);

      expect(p.supersetNextIndex, isNull);
      expect(p.isResting, isTrue);
    });

    test('eine Übung ohne Gruppe pausiert sofort', () async {
      final p = await pair();
      p.toggleSetDone(2, 0);

      expect(p.supersetNextIndex, isNull);
      expect(p.isResting, isTrue);
    });
  });
}
