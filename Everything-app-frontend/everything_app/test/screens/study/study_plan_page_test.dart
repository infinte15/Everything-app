import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:everything_app/models/study_note.dart';
import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/screens/study/study_plan_page.dart';
import 'package:everything_app/screens/study/widgets/study_kinetic_card.dart';
import 'package:everything_app/widgets/pointer_aware_draggable.dart';

import '../../support/fake_study_service.dart';

/// Eine Seite in Backend-Form. Ohne Modul stuende sie gar nicht auf dem Board.
Map<String, dynamic> _note(int id, String title, {String? status}) => {
      'id': id,
      'title': title,
      'content': '',
      'courseId': 5,
      'courseName': 'Analysis I',
      'tags': status == null ? null : 'status:$status',
      'isFavorite': false,
    };

Future<StudyProvider> _pumpBoard(
  WidgetTester tester, {
  required FakeStudyService fake,
  // Breit genug fuer das dreispaltige Layout (isWide > 700), damit die Spalten
  // nebeneinander stehen und ein Drag sie ohne Scrollen erreicht.
  Size surface = const Size(1400, 2400),
  // Systemweite Schriftvergroesserung. Die Lernziel-Kachel hat eine feste Hoehe, die damit
  // steht und faellt.
  double textScale = 1.0,
}) async {
  tester.view.physicalSize = surface;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  fake.courses = [
    {'id': 5, 'name': 'Analysis I', 'color': '#3B82F6'}
  ];

  final provider = StudyProvider(studyService: fake);
  await provider.loadData();

  await tester.pumpWidget(
    ChangeNotifierProvider<StudyProvider>.value(
      value: provider,
      child: MaterialApp(
        builder: (ctx, child) => MediaQuery(
          data: MediaQuery.of(ctx).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const StudyPlanPage(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

/// Zieht [from] auf [to] — mit der Maus, die ab 8px Slop sofort greift.
Future<void> _dragWithMouse(WidgetTester tester, Finder from, Finder to) async {
  final gesture = await tester.startGesture(
    tester.getCenter(from),
    kind: PointerDeviceKind.mouse,
  );
  await tester.pump(const Duration(milliseconds: 16));

  // In Schritten, damit der DragTarget die Bewegung mitbekommt.
  final target = tester.getCenter(to);
  final start = tester.getCenter(from);
  for (var i = 1; i <= 8; i++) {
    await gesture.moveTo(Offset.lerp(start, target, i / 8)!);
    await tester.pump(const Duration(milliseconds: 16));
  }
  await gesture.up();
  await tester.pumpAndSettle();
}

Map<String, dynamic> _goal(int id, String name) => {
      'id': id,
      'courseId': 5,
      'courseName': name,
      'courseColor': '#3B82F6',
      'emoji': '📐',
      'weeklyGoalHours': 6.0,
      'loggedHours': 2.0,
    };

void main() {
  late FakeStudyService fake;

  setUp(() => fake = FakeStudyService());

  group('Lernziel-Kacheln', () {
    testWidgets('die Kacheln bleiben flach und laufen nicht ueber', (tester) async {
      fake.goals = [for (var i = 0; i < 6; i++) _goal(60 + i, 'Modul $i')];
      final provider = await _pumpBoard(tester, fake: fake);

      // mainAxisExtent ist eine harte Hoehe — ein zu hoher Inhalt waere ein RenderFlex-Ueberlauf.
      expect(tester.takeException(), isNull);

      final card = find.ancestor(
        of: find.text('Modul 0'),
        matching: find.byType(StudyKineticCard),
      );
      expect(tester.getSize(card.first).height, lessThan(140),
          reason: 'vorher waren es bei 1200 px rund 269 px fuer ~90 px Inhalt');

      provider.dispose();
    });

    // Eine feste Hoehe ist nur so gut wie ihre Reserve: bei groesserer Systemschrift waechst
    // der Inhalt, die Kachel nicht.
    for (final scale in [1.3, 1.6]) {
      testWidgets('kein Ueberlauf bei Schriftgroesse $scale', (tester) async {
        fake.goals = [for (var i = 0; i < 3; i++) _goal(60 + i, 'Modul $i')];
        final provider = await _pumpBoard(tester, fake: fake, textScale: scale);

        expect(tester.takeException(), isNull);

        provider.dispose();
      });
    }

    testWidgets('ein dickerer Rahmen aendert die Kartengroesse nicht', (tester) async {
      // Material(shape:) malt ueber einen CustomPaint mit foregroundPainter — der hat keine
      // Layoutwirkung. Dieser Test haelt das fest, damit niemand spaeter das Padding
      // "ausgleicht".
      Future<Size> sizeFor(double width) async {
        await tester.pumpWidget(MaterialApp(
          home: Scaffold(
            body: Center(
              child: StudyKineticCard(
                borderColor: const Color(0xFF3B82F6),
                borderWidth: width,
                padding: const EdgeInsets.all(14),
                child: const Text('Inhalt'),
              ),
            ),
          ),
        ));
        await tester.pumpAndSettle();
        return tester.getSize(find.byType(StudyKineticCard));
      }

      expect(await sizeFor(1), await sizeFor(4));
    });
  });

  group('Sprint-Board Drag and Drop', () {
    testWidgets('eine Karte von TO DO nach DONE schreibt status:done', (tester) async {
      fake.notes = [_note(1, 'Kapitel 1')];
      final provider = await _pumpBoard(tester, fake: fake);

      expect(provider.todoNotes.map((n) => n.id), [1],
          reason: 'eine Seite ohne Status rueckt automatisch in TO DO nach');

      await _dragWithMouse(tester, find.text('Kapitel 1'), find.text('DONE'));

      expect(provider.doneNotes.map((n) => n.id), [1]);
      expect(provider.todoNotes, isEmpty);

      provider.dispose();
    });

    testWidgets('ein Drop auf die eigene Spalte schreibt nichts', (tester) async {
      fake.notes = [_note(1, 'Kapitel 1', status: 'done')];
      final provider = await _pumpBoard(tester, fake: fake);

      // Ohne den Vergleich auf den EFFEKTIVEN Status waere das ein Schreibvorgang
      // ohne jede Wirkung.
      await _dragWithMouse(tester, find.text('Kapitel 1'), find.text('DONE'));

      expect(provider.doneNotes.map((n) => n.id), [1]);

      provider.dispose();
    });

    testWidgets('ein Wischen ohne Halten zieht die Karte nicht', (tester) async {
      fake.notes = [_note(1, 'Kapitel 1')];
      final provider = await _pumpBoard(tester, fake: fake);

      // Finger statt Maus: ohne den langen Druck bleibt es eine Scrollgeste, sonst
      // liesse sich das Board nicht mehr scrollen.
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Kapitel 1')),
        kind: PointerDeviceKind.touch,
      );
      for (var i = 0; i < 6; i++) {
        await gesture.moveBy(const Offset(120, 0));
        await tester.pump(const Duration(milliseconds: 16));
      }
      await gesture.up();
      await tester.pumpAndSettle();

      expect(provider.todoNotes.map((n) => n.id), [1],
          reason: 'die Karte darf ohne Halten nicht die Spalte wechseln');

      provider.dispose();
    });

    testWidgets('Tippen oeffnet weiterhin das Status-Sheet', (tester) async {
      fake.notes = [_note(1, 'Kapitel 1')];
      final provider = await _pumpBoard(tester, fake: fake);

      await tester.tap(find.text('Kapitel 1'));
      await tester.pumpAndSettle();

      // Der Rueckfallweg bleibt, weil am Rand nicht automatisch gescrollt wird.
      expect(find.text('Status ändern'), findsOneWidget);
      expect(find.text('IN PROGRESS'), findsWidgets);

      provider.dispose();
    });

    testWidgets('Karten haengen in einem Draggable, die Spalten sind Drop-Ziele', (tester) async {
      fake.notes = [_note(1, 'Kapitel 1')];
      final provider = await _pumpBoard(tester, fake: fake);

      expect(
        find.ancestor(
          of: find.text('Kapitel 1'),
          matching: find.byType(PointerAwareDraggable<StudyNote>),
        ),
        findsOneWidget,
      );
      expect(find.byType(DragTarget<StudyNote>), findsNWidgets(3));

      provider.dispose();
    });
  });
}
