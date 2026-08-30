import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/models/gym/gym_models.dart';
import 'package:everything_app/screens/gym/widgets/body_activation_map.dart';
import 'package:everything_app/screens/gym/widgets/body_map_paths.dart';
import 'package:everything_app/theme/lyfta_theme.dart';

void main() {
  // Die Geometrie liegt als Asset vor und muss vor den Pfad-Tests einmal geladen werden.
  TestWidgetsFlutterBinding.ensureInitialized();

  late BodyGeometry geometry;

  setUpAll(() async {
    await BodyMapGeometry.ensureLoaded();
    geometry = BodyMapGeometry.geometry.value!;
  });

  /// Die Figur so groß, wie sie in der Muskel-Karte tatsächlich gezeichnet wird.
  Size sizeFor(BodyGeometryView view) => Size(400 * view.aspectRatio, 400);

  /// Ein Punkt, der wirklich *in* der Muskelflaeche liegt.
  ///
  /// Nicht die Mitte des umschliessenden Rechtecks: die Brust besteht aus zwei Haelften,
  /// deren gemeinsame Mitte auf dem Brustbein liegt - also neben dem Muskel. Deshalb wird
  /// die erste Teilflaeche abgerastert, bis ein Punkt drinliegt.
  Offset insidePoint(BodyGeometryView view, String muscle, Size size) {
    final region = view.regions.firstWhere((r) => r.muscle == muscle);
    final scale = size.height / view.viewBox.height;
    final path = region.paths.first.transform(Float64List.fromList(<double>[
      scale, 0, 0, 0,
      0, scale, 0, 0,
      0, 0, 1, 0,
      -view.viewBox.left * scale, -view.viewBox.top * scale, 0, 1,
    ]));

    final b = path.getBounds();
    for (var fy = 1; fy < 16; fy++) {
      for (var fx = 1; fx < 16; fx++) {
        final p = Offset(b.left + b.width * fx / 16, b.top + b.height * fy / 16);
        if (path.contains(p)) return p;
      }
    }
    fail('Kein Punkt innerhalb von $muscle gefunden');
  }

  group('Geometrie', () {
    test('beide Ansichten kennen jede Flaeche, die sie zeichnen sollen', () {
      expect(geometry.front.muscles, kFrontMuscles);
      expect(geometry.back.muscles, kBackMuscles);
    });

    test('jede Flaeche hat eine echte Ausdehnung', () {
      for (final view in [geometry.front, geometry.back]) {
        for (final region in view.regions) {
          expect(region.paths, isNotEmpty, reason: region.muscle);
          expect(region.bounds.width, greaterThan(0), reason: region.muscle);
          expect(region.bounds.height, greaterThan(0), reason: region.muscle);
        }
      }
    });

    test('die Silhouette wird gezeichnet, aber nie eingefaerbt', () {
      expect(geometry.front.silhouette, isNotEmpty);
      expect(geometry.back.silhouette, isNotEmpty);
      for (final inert in kMuscleMapInert) {
        expect(geometry.front.muscles, isNot(contains(inert)));
      }
    });

    test('Muskeln liegen kopfwaerts vor fusswaerts', () {
      // Sanity-Check gegen ein verrutschtes Koordinatensystem: waere y gespiegelt,
      // stuenden die Waden ueber dem Trapezmuskel.
      expect(geometry.back.centerYOf('trapezius'),
          lessThan(geometry.back.centerYOf('gluteal')));
      expect(geometry.back.centerYOf('gluteal'),
          lessThan(geometry.back.centerYOf('calves')));
      expect(geometry.front.centerYOf('chest'),
          lessThan(geometry.front.centerYOf('quadriceps')));
    });
  });

  group('Treffertest', () {
    // Tippen-zum-Filtern in der Koerper-Karte haengt daran: Path.contains() muss
    // die richtige Flaeche liefern, sonst bricht es lautlos weg.
    test('grosse, eindeutige Flaechen liefern genau ihren Muskel', () {
      final front = geometry.front;
      final fs = sizeFor(front);
      final fp = BodyMapPainter(view: front);
      expect(fp.muscleAt(insidePoint(front, 'chest', fs), fs), 'chest');
      expect(fp.muscleAt(insidePoint(front, 'quadriceps', fs), fs), 'quadriceps');
      expect(fp.muscleAt(insidePoint(front, 'abs', fs), fs), 'abs');

      final back = geometry.back;
      final bs = sizeFor(back);
      final bp = BodyMapPainter(view: back);
      expect(bp.muscleAt(insidePoint(back, 'gluteal', bs), bs), 'gluteal');
      expect(bp.muscleAt(insidePoint(back, 'hamstring', bs), bs), 'hamstring');
      expect(bp.muscleAt(insidePoint(back, 'upper-back', bs), bs), 'upper-back');
    });

    test('ausserhalb der Figur wird nichts getroffen', () {
      final view = geometry.front;
      final size = sizeFor(view);
      expect(BodyMapPainter(view: view).muscleAt(const Offset(1, 1), size), isNull);
    });

    test('der Treffer kommt als Backend-Slug zurueck', () {
      // Die Karte faerbt nach Backend-Slugs ein; ein Tipp muss denselben Namen
      // liefern, sonst findet der Aufrufer seine Zahl nicht wieder.
      final view = geometry.back;
      final size = sizeFor(view);
      final painter = BodyMapPainter(view: view, activation: const {'lats': 0.8});
      expect(painter.backendMuscleAt(insidePoint(view, 'upper-back', size), size), 'lats');
    });
  });

  group('Uebersetzung der Muskel-Slugs', () {
    test('Backend-Slugs landen auf Flaechen der Grafik', () {
      expect(muscleMapSlug('abdominals'), 'abs');
      expect(muscleMapSlug('shoulders'), 'deltoids');
      expect(muscleMapSlug('lower back'), 'lower-back');
      expect(muscleMapSlug('LOWER_BACK'), 'lower-back');
      expect(muscleMapSlug('hip flexors'), 'hip-flexors');
    });

    test('Ausdauer und Unbekanntes haben keine Flaeche', () {
      expect(muscleMapSlug('cardio'), isNull);
      expect(muscleMapSlug('nicht-existent'), isNull);
    });

    test('fallen zwei Muskeln auf eine Flaeche, gewinnt der groessere Wert', () {
      // Latissimus und mittlerer Ruecken sind beide 'upper-back'. Die Summe waere
      // falsch: die Flaeche zeigt Beanspruchung, nicht die Zahl der Zulieferer.
      final folded = foldToMuscleMap(const {'lats': 0.8, 'middle back': 0.5});
      expect(folded['upper-back'], 0.8);
    });

    test('primaer schlaegt sekundaer beim selben Muskel', () {
      final highlight = BodyHighlight.fromBackend(const ['lats'], const ['middle back']);
      expect(highlight.primary, contains('upper-back'));
      expect(highlight.secondary, isNot(contains('upper-back')));
    });
  });

  group('Zwei-Stufen-Einfaerbung', () {
    test('beansprucht ist rot, unterstuetzend blau, Rest der Grundton', () {
      final painter = BodyMapPainter(
        view: geometry.front,
        highlight: BodyHighlight.fromBackend(const ['chest'], const ['triceps']),
      );

      expect(painter.colorFor('chest'), LyftaTheme.musclePrimary);
      expect(painter.colorFor('triceps'), LyftaTheme.muscleSecondary);
      expect(painter.colorFor('quadriceps'), LyftaTheme.bodyBase);
    });

    test('die Volumen-Rampe laeuft vom Grundton nach rot', () {
      final painter = BodyMapPainter(
        view: geometry.back,
        activation: const {'chest': 1.0, 'lats': 0.0},
      );
      expect(painter.colorFor('chest'), LyftaTheme.musclePrimary);
      expect(painter.colorFor('upper-back'), LyftaTheme.bodyBase);
    });

    test('highlight schlaegt die Volumen-Rampe', () {
      final painter = BodyMapPainter(
        view: geometry.front,
        activation: const {'quadriceps': 1.0},
        highlight: BodyHighlight.fromBackend(const ['chest'], const []),
      );
      // quadriceps ist stark "trainiert", aber nicht Teil der Uebung.
      expect(painter.colorFor('quadriceps'), LyftaTheme.bodyBase);
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
