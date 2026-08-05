import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/screens/study/widgets/study_page_tree.dart';

import '../../support/fake_study_service.dart';

/// Eine Notiz in Backend-Form — also mit den JSON-Schlüsseln des DTOs.
Map<String, dynamic> _note(int id, String title, {int? parentId, int orderIndex = 0}) => {
      'id': id,
      'title': title,
      'content': '',
      'parentId': parentId,
      'orderIndex': orderIndex,
      'isFavorite': false,
    };

Future<StudyProvider> _pumpTree(
  WidgetTester tester, {
  required FakeStudyService fake,
  // Die Standard-Testfläche von 800x600 ist schmaler als jedes Gerät, das dieser Screen
  // adressiert; realistische Maße statt einer synthetischen Größe.
  Size surface = const Size(1080, 2400),
}) async {
  tester.view.physicalSize = surface;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  // Vorab laden, damit der erste pump schon echte Daten sieht, statt gegen den
  // asynchronen Ladevorgang zu rennen.
  final provider = StudyProvider(studyService: fake);
  await provider.loadData();

  await tester.pumpWidget(
    ChangeNotifierProvider<StudyProvider>.value(
      value: provider,
      child: MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: StudyPageTree(roots: provider.noteTree),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();

  return provider;
}

void main() {
  late FakeStudyService fake;

  setUp(() => fake = FakeStudyService());

  testWidgets('Wurzelseiten stehen da, Unterseiten erst nach dem Aufklappen',
      (tester) async {
    fake.notes = [
      _note(1, 'Analysis'),
      _note(2, 'Kapitel 1', parentId: 1),
    ];

    final provider = await _pumpTree(tester, fake: fake);

    expect(find.text('Analysis'), findsOneWidget);
    expect(find.text('Kapitel 1'), findsNothing, reason: 'Ebenen starten zugeklappt');

    await tester.tap(find.byIcon(Icons.keyboard_arrow_right));
    await tester.pumpAndSettle();

    expect(find.text('Kapitel 1'), findsOneWidget);

    provider.dispose();
  });

  testWidgets('eine Seite ohne Unterseiten hat kein Aufklapp-Dreieck', (tester) async {
    fake.notes = [_note(1, 'Analysis')];

    final provider = await _pumpTree(tester, fake: fake);

    expect(find.text('Analysis'), findsOneWidget);
    expect(find.byIcon(Icons.keyboard_arrow_right), findsNothing);

    provider.dispose();
  });

  testWidgets('ohne Seiten rendert der Baum nichts und wirft nicht', (tester) async {
    final provider = await _pumpTree(tester, fake: fake);

    expect(find.byType(StudyPageTree), findsOneWidget);
    expect(tester.takeException(), isNull);

    provider.dispose();
  });

  // Die Reihenfolge kommt aus orderIndex, nicht aus der Ladereihenfolge.
  testWidgets('Geschwister stehen in der Reihenfolge ihres orderIndex', (tester) async {
    fake.notes = [
      _note(1, 'Zweite', orderIndex: 1),
      _note(2, 'Erste', orderIndex: 0),
    ];

    final provider = await _pumpTree(tester, fake: fake);

    final erste = tester.getTopLeft(find.text('Erste'));
    final zweite = tester.getTopLeft(find.text('Zweite'));
    expect(erste.dy, lessThan(zweite.dy));

    provider.dispose();
  });

  // Ein Tipp auf die Zeile oeffnet die Seite; der Ziehgriff sitzt bewusst daneben, damit
  // nicht jeder Fingertipp als Drag-Beginn gewertet wird.
  testWidgets('jede Seite hat einen eigenen Ziehgriff', (tester) async {
    fake.notes = [_note(1, 'Analysis'), _note(2, 'Lineare Algebra', orderIndex: 1)];

    final provider = await _pumpTree(tester, fake: fake);

    expect(find.byIcon(Icons.drag_handle), findsNWidgets(2));

    provider.dispose();
  });

  testWidgets('Loeschen fragt nach und nennt die betroffenen Unterseiten', (tester) async {
    fake.notes = [
      _note(1, 'Analysis'),
      _note(2, 'Kapitel 1', parentId: 1),
    ];

    final provider = await _pumpTree(tester, fake: fake);

    await tester.tap(find.byIcon(Icons.more_horiz).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Löschen'));
    await tester.pumpAndSettle();

    expect(find.textContaining('alle Unterseiten'), findsOneWidget);

    provider.dispose();
  });
}
