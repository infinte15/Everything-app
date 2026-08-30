import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/models/gym/gym_models.dart';
import 'package:everything_app/screens/gym/widgets/body_activation_map.dart';
import 'package:everything_app/screens/gym/widgets/body_map_paths.dart';
import 'package:everything_app/screens/gym/widgets/muscle_filter_strip.dart';

/// Der Filter, wie ihn der Server liefert: alle Muskelgruppen inklusive Ausdauer.
const _options = [
  GymMuscleOption(slug: 'chest', label: 'Brust'),
  GymMuscleOption(slug: 'lats', label: 'Latissimus'),
  GymMuscleOption(slug: 'triceps', label: 'Trizeps'),
  GymMuscleOption(slug: 'cardio', label: 'Ausdauer'),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() => BodyMapGeometry.ensureLoaded());

  Widget strip({String? selected, ValueChanged<String?>? onChanged}) => MaterialApp(
        home: Scaffold(
          body: MuscleFilterStrip(
            options: _options,
            selected: selected,
            onChanged: onChanged ?? (_) {},
          ),
        ),
      );

  testWidgets('jede Muskelgruppe bekommt eine Kachel, dazu "Alle"', (tester) async {
    await tester.pumpWidget(strip());
    await tester.pumpAndSettle();

    expect(find.text('Alle'), findsOneWidget);
    for (final option in _options) {
      expect(find.text(option.label), findsOneWidget, reason: option.slug);
    }
  });

  testWidgets('zeichenbare Muskeln bekommen eine Figur, Ausdauer nicht', (tester) async {
    await tester.pumpWidget(strip());
    await tester.pumpAndSettle();

    // Brust, Latissimus und Trizeps haben eine Flaeche - Ausdauer nicht. Eine Figur, in der
    // nichts markiert waere, liesse sich von "kein Filter" nicht unterscheiden.
    expect(find.byType(BodyFigure), findsNWidgets(3));
  });

  testWidgets('ein Tipp meldet den Backend-Slug', (tester) async {
    String? picked;
    var calls = 0;
    await tester.pumpWidget(strip(onChanged: (slug) {
      picked = slug;
      calls++;
    }));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Latissimus'));
    expect(picked, 'lats');
    expect(calls, 1);
  });

  testWidgets('"Alle" meldet null', (tester) async {
    String? picked = 'chest';
    await tester.pumpWidget(strip(selected: 'chest', onChanged: (slug) => picked = slug));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alle'));
    expect(picked, isNull);
  });

  testWidgets('die Figur zeigt die Seite, auf der der Muskel zu sehen ist', (tester) async {
    await tester.pumpWidget(strip());
    await tester.pumpAndSettle();

    // Der Latissimus ist nur von hinten zu sehen, die Brust nur von vorne. Eine Kachel auf
    // der falschen Seite waere eine graue Figur ohne erkennbare Markierung.
    final figures = tester.widgetList<BodyFigure>(find.byType(BodyFigure)).toList();
    final backFlags = {
      for (final f in figures) f.highlight!.primary.first: f.back,
    };

    expect(backFlags['chest'], isFalse);
    expect(backFlags['upper-back'], isTrue, reason: 'lats wird auf upper-back abgebildet');
    expect(backFlags['triceps'], isTrue);
  });
}
