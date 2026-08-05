import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/screens/study/study_note_editor_page.dart';

import '../../support/fake_study_service.dart';

Future<StudyProvider> _pumpEditor(
  WidgetTester tester, {
  required FakeStudyService fake,
  required String content,
  Size surface = const Size(1080, 2400),
}) async {
  tester.view.physicalSize = surface;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  fake.notes = [
    {'id': 1, 'title': 'Lernzettel', 'content': content, 'category': 'Studium'},
  ];

  final provider = StudyProvider(studyService: fake);
  await provider.loadData();

  await tester.pumpWidget(
    ChangeNotifierProvider<StudyProvider>.value(
      value: provider,
      child: const MaterialApp(home: StudyNoteEditorPage(noteId: 1)),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

void main() {
  late FakeStudyService fake;

  setUp(() => fake = FakeStudyService());

  group('Anklickbare Checkbox', () {
    testWidgets('ein Klick schreibt die richtige Zeile um und speichert', (tester) async {
      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: '- [ ] erste\n- [ ] zweite',
      );

      // Die zweite Zeile — daran haengt, ob startLine stimmt.
      await tester.tap(find.text('zweite'));
      await tester.pumpAndSettle();

      expect(fake.lastUpdatedNote!['content'], '- [ ] erste\n- [x] zweite');

      provider.dispose();
    });

    testWidgets('scheitert das Speichern, springt der Haken zurueck', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '- [ ] offen');
      fake.failUpdateNote = true;

      await tester.tap(find.text('offen'));
      await tester.pumpAndSettle();

      // Sonst stuende ein Haken da, den der Server nicht kennt.
      expect(find.byIcon(Icons.check_box_outline_blank), findsOneWidget);
      expect(find.byIcon(Icons.check_box), findsNothing);
      expect(find.text('Haken konnte nicht gespeichert werden.'), findsOneWidget);

      provider.dispose();
    });
  });

  group('Lernzettel-Bloecke', () {
    testWidgets('die Antwort eines Aufklappblocks ist zunaechst verborgen', (tester) async {
      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: '??? Was ist eine Ableitung?\nDie Steigung der Tangente.\n???',
      );

      expect(find.text('Was ist eine Ableitung?'), findsOneWidget);
      expect(find.text('Die Steigung der Tangente.'), findsNothing,
          reason: 'sonst liesse sich der Zettel nicht abfragen');

      await tester.tap(find.text('Was ist eine Ableitung?'));
      await tester.pumpAndSettle();

      expect(find.text('Die Steigung der Tangente.'), findsOneWidget);

      provider.dispose();
    });

    testWidgets('ein Codeblock wird nicht inline formatiert', (tester) async {
      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: '```\n**kein Fettdruck**\n```',
      );

      expect(find.text('**kein Fettdruck**'), findsOneWidget);

      provider.dispose();
    });

    testWidgets('nummerierte Listen behalten ihre Zahl', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '3. Drittens');

      expect(find.text('3.'), findsOneWidget);
      expect(find.text('Drittens'), findsOneWidget);

      provider.dispose();
    });
  });

  group('Einfuegemenue', () {
    testWidgets('schreibt den Ausklapp-Block in den Rohtext', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '');

      await tester.tap(find.text('BEARBEITEN'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('EINFÜGEN'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ausklappbar (Frage/Antwort)'));
      await tester.pumpAndSettle();

      final field = tester.widget<EditableText>(find.byType(EditableText).last);
      expect(field.controller.text, contains('??? Frage'));
      expect(field.controller.text, contains('Antwort'));

      provider.dispose();
    });
  });
}
