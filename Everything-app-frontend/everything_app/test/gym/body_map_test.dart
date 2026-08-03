import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/models/gym/gym_models.dart';
import 'package:everything_app/screens/gym/widgets/body_activation_map.dart';
import 'package:everything_app/screens/gym/widgets/body_map_paths.dart';
import 'package:everything_app/screens/gym/widgets/exercise_muscle_figure.dart';
import 'package:everything_app/theme/lyfta_theme.dart';

/// Flächenschwerpunkt der (ungespiegelten) Punktliste einer Region, skaliert.
Offset _centroid(BodyRegion region, Size size) {
  var x = 0.0;
  var y = 0.0;
  for (final p in region.points) {
    x += p.dx;
    y += p.dy;
  }
  final n = region.points.length;
  return Offset(x / n * size.width, y / n * size.height);
}

BodyRegion _region(List<BodyRegion> regions, String muscle) =>
    regions.firstWhere((r) => r.muscle == muscle);

void main() {
  const size = Size(248, 400); // kBodyAspectRatio 0.62

  group('Treffertest überlebt die Kurvenglättung', () {
    // Das ist die eigentliche Regression: Path.contains() muss auch bei
    // kubischen Segmenten noch die richtige Muskelfläche liefern, sonst bricht
    // Tippen-zum-Filtern in der Körper-Karte lautlos weg.
    test('Vorderansicht trifft jede Region an ihrem Schwerpunkt', () {
      final painter = BodyMapPainter(back: false);
      for (final region in kBodyFrontRegions) {
        final hit = painter.muscleAt(_centroid(region, size), size);
        expect(hit, isNotNull, reason: '${region.muscle} wurde gar nicht getroffen');
      }
    });

    test('Rückansicht trifft jede Region an ihrem Schwerpunkt', () {
      final painter = BodyMapPainter(back: true);
      for (final region in kBodyBackRegions) {
        final hit = painter.muscleAt(_centroid(region, size), size);
        expect(hit, isNotNull, reason: '${region.muscle} wurde gar nicht getroffen');
      }
    });

    test('grosse, eindeutige Flächen liefern genau ihren Muskel', () {
      final front = BodyMapPainter(back: false);
      expect(
        front.muscleAt(_centroid(_region(kBodyFrontRegions, 'chest'), size), size),
        'chest',
      );
      expect(
        front.muscleAt(
            _centroid(_region(kBodyFrontRegions, 'quadriceps'), size), size),
        'quadriceps',
      );

      final back = BodyMapPainter(back: true);
      expect(
        back.muscleAt(_centroid(_region(kBodyBackRegions, 'glutes'), size), size),
        'glutes',
      );
      expect(
        back.muscleAt(
            _centroid(_region(kBodyBackRegions, 'hamstrings'), size), size),
        'hamstrings',
      );
    });

    test('ausserhalb der Figur wird nichts getroffen', () {
      final painter = BodyMapPainter(back: false);
      expect(painter.muscleAt(const Offset(2, 2), size), isNull);
    });
  });

  group('Zwei-Stufen-Einfärbung', () {
    test('beansprucht ist rot, unterstuetzend blau, Rest der Grundton', () {
      final painter = BodyMapPainter(
        back: false,
        highlight: const BodyHighlight(
          primary: {'chest'},
          secondary: {'triceps'},
        ),
      );

      expect(painter.colorFor('chest'), LyftaTheme.musclePrimary);
      expect(painter.colorFor('triceps'), LyftaTheme.muscleSecondary);
      expect(painter.colorFor('quadriceps'), LyftaTheme.bodyBase);
    });

    test('die Volumen-Rampe laeuft vom Grundton nach rot', () {
      final painter = BodyMapPainter(
        back: false,
        activation: const {'chest': 1.0, 'lats': 0.0},
      );
      expect(painter.colorFor('chest'), LyftaTheme.musclePrimary);
      expect(painter.colorFor('lats'), LyftaTheme.bodyBase);
    });

    test('highlight schlaegt die Volumen-Rampe', () {
      final painter = BodyMapPainter(
        back: false,
        activation: const {'quadriceps': 1.0},
        highlight: const BodyHighlight(primary: {'chest'}),
      );
      // quadriceps ist stark "trainiert", aber nicht Teil der Übung.
      expect(painter.colorFor('quadriceps'), painter.colorFor('lats'));
    });
  });

  group('GymHistoryEntry', () {
    test('liest die Kennzahlen fuer das Diagramm mit', () {
      final entry = GymHistoryEntry.fromMap(const {
        'sessionId': 7,
        'sessionName': 'Push A',
        'totalVolumeKg': 1234.5,
        'totalSets': 9,
        'bestSetWeight': 82.5,
        'bestSetReps': 6,
        'estimated1RM': 98.4,
      });

      expect(entry.bestSetWeight, 82.5);
      expect(entry.bestSetReps, 6);
      expect(entry.estimated1RM, 98.4);
    });

    test('fehlende Felder bleiben null statt zu werfen', () {
      final entry = GymHistoryEntry.fromMap(const {'sessionId': 1});
      expect(entry.bestSetReps, isNull);
      expect(entry.estimated1RM, isNull);
      expect(entry.totalVolumeKg, 0);
    });
  });

  group('Volumen-Formatierung', () {
    test('ab einer Tonne in t, darunter in kg', () {
      expect(gymFormatVolume(940), '940 kg');
      expect(gymFormatVolume(1250), '1.3 t');
      expect(gymFormatVolume(12500), '13 t');
    });
  });

  group('Seitenwahl', () {
    test('nur hinten vorkommende Muskeln waehlen die Rueckansicht', () {
      expect(preferBackView(const ['triceps'], const []), isTrue);
      expect(preferBackView(const ['glutes'], const []), isTrue);
      expect(preferBackView(const ['hamstrings'], const []), isTrue);
    });

    test('nur vorne vorkommende Muskeln waehlen die Vorderansicht', () {
      expect(preferBackView(const ['chest'], const []), isFalse);
      expect(preferBackView(const ['biceps'], const []), isFalse);
      expect(preferBackView(const ['quadriceps'], const []), isFalse);
    });

    test('primary wiegt schwerer als secondary', () {
      // Brust primaer, Trizeps sekundaer -> Vorderansicht.
      expect(preferBackView(const ['chest'], const ['triceps']), isFalse);
      // Trizeps primaer, Brust sekundaer -> Rueckansicht.
      expect(preferBackView(const ['triceps'], const ['chest']), isTrue);
    });

    test('Gleichstand und Unbekanntes fallen auf die Vorderansicht zurueck', () {
      expect(preferBackView(const [], const []), isFalse);
      expect(preferBackView(const ['shoulders'], const []), isFalse);
      expect(preferBackView(const ['nicht-existent'], const []), isFalse);
    });
  });
}
