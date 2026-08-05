import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/screens/notes/notes_screen.dart';

import '../../support/fake_study_service.dart';

/// Der Notizen-Space war eine Zeit lang geloescht: die Regel „jede Seite gehoert zu einem
/// Modul" galt eigentlich nur den Seiten des Study Space, wurde aber auf alle Notizen
/// angewandt. Diese Tests halten die Trennung fest.
Future<StudyProvider> _pumpNotes(
  WidgetTester tester, {
  required FakeStudyService fake,
  Size surface = const Size(1080, 2400),
}) async {
  tester.view.physicalSize = surface;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final provider = StudyProvider(studyService: fake);
  await provider.loadData();

  await tester.pumpWidget(
    ChangeNotifierProvider<StudyProvider>.value(
      value: provider,
      child: const MaterialApp(home: NotesScreen()),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

void main() {
  late FakeStudyService fake;

  setUp(() => fake = FakeStudyService());

  testWidgets('nur freie Notizen stehen im Notizen-Space, keine Modulseiten', (tester) async {
    fake.notes = [
      {'id': 1, 'title': 'Einkaufsliste', 'content': '', 'category': 'Personal'},
      {'id': 2, 'title': 'Analysis Skript', 'content': '', 'courseId': 5},
    ];
    final provider = await _pumpNotes(tester, fake: fake);

    expect(find.text('Einkaufsliste'), findsOneWidget);
    expect(find.text('Analysis Skript'), findsNothing,
        reason: 'Modulseiten leben im FAECHER-Tab des Study Space');

    provider.dispose();
  });

  testWidgets('der Studium-Filter blendet die Personal-Notiz aus', (tester) async {
    fake.notes = [
      {'id': 1, 'title': 'Einkaufsliste', 'content': '', 'category': 'Personal'},
      {'id': 2, 'title': 'Klausurtermine', 'content': '', 'category': 'Studium'},
    ];
    final provider = await _pumpNotes(tester, fake: fake);

    expect(find.text('Einkaufsliste'), findsOneWidget);
    expect(find.text('Klausurtermine'), findsOneWidget);

    // Die Kategorie-Chips liegen hinter dem Filtersymbol, nicht auf dem Schirm.
    await tester.tap(find.byIcon(Icons.filter_list));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ChoiceChip, 'Studium'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();

    expect(find.text('Klausurtermine'), findsOneWidget);
    expect(find.text('Einkaufsliste'), findsNothing);

    provider.dispose();
  });

  testWidgets('eine Notiz laesst sich anlegen, ohne dass es ein Modul gibt', (tester) async {
    // Der eigentliche Regressionsschutz: mit Modulpflicht war der Speichern-Knopf hier
    // wirkungslos, solange kein Fach angelegt war.
    final provider = await _pumpNotes(tester, fake: fake);

    await tester.tap(find.byType(FloatingActionButton));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).first, 'Einkaufsliste');
    await tester.tap(find.text('NOTIZ SPEICHERN'));
    await tester.pumpAndSettle();

    expect(fake.createNoteCallCount, 1);
    expect(fake.lastCreatedNote!['category'], 'Personal', reason: 'Vorgabe der Umschaltung');
    expect(fake.lastCreatedNote!['courseId'], isNull);

    provider.dispose();
  });
}
