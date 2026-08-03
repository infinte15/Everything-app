import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:everything_app/main.dart';
import 'package:everything_app/config/routes.dart';
import 'package:everything_app/screens/gym/widgets/exercise_progress_chart.dart';

const _shotDir =
    '/tmp/claude-1000/-home-finn-Dokumente-Projekte-Everything-app/04d0f0d3-166f-4287-9f63-6e1e9aafa8f6/scratchpad/gym_shots';

final _boundaryKey = GlobalKey();

// Bounded pump loop instead of pumpAndSettle: some gym screens use
// perpetual loading animations (shimmers/spinners) that never stop
// scheduling frames, which makes pumpAndSettle hang forever.
Future<void> _settle(WidgetTester tester, {int steps = 20}) async {
  for (var i = 0; i < steps; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _shoot(WidgetTester tester, String name) async {
  await tester.runAsync(() async {
    final boundary =
        _boundaryKey.currentContext!.findRenderObject() as RenderRepaintBoundary;
    final image = await boundary.toImage(pixelRatio: 1.0);
    final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!
        .buffer
        .asUint8List();
    Directory(_shotDir).createSync(recursive: true);
    File('$_shotDir/$name.png').writeAsBytesSync(bytes);
  });
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('gym makeover smoke walkthrough', (tester) async {
    await initializeDateFormatting('de_DE', null);

    await tester.pumpWidget(RepaintBoundary(key: _boundaryKey, child: const MyApp()));
    await _settle(tester, steps: 20);
    await _shoot(tester, '00_login');

    final devLogin = find.text('Quick Login (Dev)');
    expect(devLogin, findsOneWidget, reason: 'dev login button missing');
    await tester.tap(devLogin);
    await _settle(tester, steps: 20);
    await _shoot(tester, '01_home');

    router.go('/sports');
    await _settle(tester, steps: 20);
    await _shoot(tester, '02_gym_home');

    // Bottom-nav taps go by icon + hitTestable(): IndexedStack keeps offstage
    // tabs mounted, so text like "Training" can match a card *inside* a tab
    // as well as the nav label. hitTestable() filters to what a user could
    // actually tap; icons are unique to the nav bar (unlike the labels).
    await tester.tap(find.byIcon(Icons.fitness_center_rounded).hitTestable().last);
    await _settle(tester, steps: 10);
    await _shoot(tester, '03_gym_workout_tab');

    await tester.tap(find.byIcon(Icons.search_rounded).hitTestable().last);
    await _settle(tester, steps: 10);
    await _shoot(tester, '04_gym_explore_tab');

    await tester.tap(find.byIcon(Icons.person_rounded).hitTestable().last);
    await _settle(tester, steps: 10);
    await _shoot(tester, '05_gym_profile_tab');

    await tester.tap(find.byIcon(Icons.home_rounded).hitTestable().last);
    await _settle(tester, steps: 10);

    await tester.tap(find.byIcon(Icons.add).hitTestable().last);
    await _settle(tester, steps: 10);
    await _shoot(tester, '06_quick_start_sheet');

    await tester.tap(find.text('Leeres Training'));
    await _settle(tester, steps: 20);
    await _shoot(tester, '07_active_workout_empty');

    await tester.tap(find.text('Übung hinzufügen'));
    await _settle(tester, steps: 20);
    await _shoot(tester, '08_exercise_picker');

    // Exercise tiles are InkWell, not GestureDetector; seed order is
    // alphabetical so "3/4 Sit-Up" is the first result (see 08_exercise_picker.png).
    await tester.tap(find.widgetWithText(InkWell, '3/4 Sit-Up'));
    await _settle(tester, steps: 10);

    final confirmBtn = find.textContaining('übernehmen');
    if (tester.any(confirmBtn)) {
      await tester.tap(confirmBtn);
      await _settle(tester, steps: 20);
    }
    await _shoot(tester, '09_active_workout_with_exercise');

    // Satz-Art umstellen: die neuen Einseitig-links/rechts-Optionen muessen
    // neben den bestehenden (Aufwaermen, Drop, ...) auftauchen. Ueber den
    // GestureDetector suchen, nicht ueber den Text '1' - der matcht sonst
    // zuerst die "ÜBUNGEN: 1"-Kopfzeile statt der Satznummer.
    final setNumberCell = find.widgetWithText(GestureDetector, '1');
    if (tester.any(setNumberCell)) {
      await tester.tap(setNumberCell);
      await _settle(tester, steps: 15);
      await _shoot(tester, '09a_set_type_picker');

      final singleLeft = find.text('Einseitig (links)');
      if (tester.any(singleLeft)) {
        await tester.tap(singleLeft);
        await _settle(tester, steps: 10);
        await _shoot(tester, '09b_set_type_single_left');
      }
    }

    // Pause fuer diese Uebung anpassen.
    final restRow = find.byIcon(Icons.edit_rounded).hitTestable();
    if (tester.any(restRow)) {
      await tester.tap(restRow.first);
      await _settle(tester, steps: 15);
      await _shoot(tester, '09c_rest_seconds_picker');

      final preset120 = find.text('120s');
      if (tester.any(preset120)) {
        await tester.tap(preset120.last);
        await _settle(tester, steps: 5);
      }
      final apply = find.text('Übernehmen');
      if (tester.any(apply)) {
        await tester.tap(apply);
        await _settle(tester, steps: 10);
      }
      await _shoot(tester, '09d_rest_seconds_applied');
    }

    final addSet = find.text('Satz hinzufügen');
    if (tester.any(addSet)) {
      await tester.tap(addSet.first);
      await _settle(tester, steps: 10);
    }
    await _shoot(tester, '10_active_workout_set_added');

    // Laufendes Training verwerfen, sonst blockiert es den Rest des Laufs.
    await tester.tap(find.byIcon(Icons.delete_outline_rounded).hitTestable().last);
    await _settle(tester, steps: 10);
    final discard = find.text('Verwerfen');
    if (tester.any(discard)) {
      await tester.tap(discard.last);
      await _settle(tester, steps: 20);
    }

    // Übungsdetail einer Übung MIT Verlauf - sonst zeigt das Diagramm nur den
    // Leerzustand. "Barbell Bench Press" ist im Testlauf vorprotokolliert.
    await tester.tap(find.byIcon(Icons.search_rounded).hitTestable().last);
    await _settle(tester, steps: 15);
    await tester.enterText(find.byType(TextField).hitTestable().first, 'Bench Press');
    await _settle(tester, steps: 20);
    await _shoot(tester, '11_explore_search');

    final benchTile =
        find.widgetWithText(InkWell, 'Barbell Bench Press - Medium Grip');
    if (tester.any(benchTile)) {
      await tester.tap(benchTile.first);
      await _settle(tester, steps: 25);
      await _shoot(tester, '12_exercise_detail_figure');

      // Zum Diagramm scrollen. Ein blosses drag() reicht hier nicht: das Ziel
      // liegt ausserhalb des Viewports (drag greift dann ins Leere) und das
      // DraggableScrollableSheet verbraucht die erste Geste damit, sich
      // aufzuziehen. scrollUntilVisible auf dem Scrollable *im Sheet* macht
      // beides richtig.
      final sheetScrollable = find
          .descendant(
            of: find.byType(DraggableScrollableSheet),
            matching: find.byType(Scrollable),
          )
          .first;
      await tester.scrollUntilVisible(
        find.byType(ExerciseProgressChart),
        160,
        scrollable: sheetScrollable,
        maxScrolls: 20,
      );
      await _settle(tester, steps: 12);
      await _shoot(tester, '13_exercise_progress_1rm');

      final volumeTab = find.text('Volumen');
      if (tester.any(volumeTab)) {
        await tester.tap(volumeTab.last);
        await _settle(tester, steps: 15);
        await _shoot(tester, '14_exercise_progress_volume');
      }

      await tester.tapAt(const Offset(20, 20)); // Sheet schliessen
      await _settle(tester, steps: 15);
    }

    // Körper-Karte mit der geglätteten Figur.
    await tester.tap(find.byIcon(Icons.person_rounded).hitTestable().last);
    await _settle(tester, steps: 15);
    final bodyTab = find.text('Körper');
    if (tester.any(bodyTab)) {
      await tester.tap(bodyTab.last);
      await _settle(tester, steps: 25);
      await _shoot(tester, '15_body_map_week');

      // Der Standardbereich "Woche" ist im Testlauf leer (die protokollierten
      // Einheiten liegen Wochen zurueck) - erst der weite Bereich zeigt
      // tatsaechlich eingefaerbte Muskeln.
      final range = find.text('3 Monate');
      if (tester.any(range)) {
        await tester.tap(range.last);
        await _settle(tester, steps: 25);
        await _shoot(tester, '16_body_map_activated');
      }
    }
  });
}
