import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/screens/study/study_note_editor_page.dart';
import 'package:everything_app/screens/study/widgets/note_editor/note_format_toolbar.dart';

import '../../support/fake_study_service.dart';

/// Der Editor ist jetzt WYSIWYG: kein Umschalten zwischen Vorschau und Rohtext mehr, jeder
/// Block ist an Ort und Stelle bearbeitbar. Geprueft wird darum nicht, was auf dem Schirm
/// steht, sondern was hinten herauskommt — `fake.lastUpdatedNote['content']` ist das Markup,
/// das der Editor aus seinen Bloecken gebaut hat.
///
/// Die reinen Regeln (Serialisieren, Teilen, Tiefen) haben ihre eigenen Tests in
/// test/utils/note_blocks_test.dart; hier geht es um Tastatur, Fokus und Speichern.
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

/// Laesst die Entprellung ablaufen und wartet das Speichern ab.
Future<void> _settleSave(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 1));
  await tester.pumpAndSettle();
}

/// Baut die Seite ab und entsorgt danach erst den Provider.
///
/// In dieser Reihenfolge, weil der Editor beim Abbau eine noch offene Entprellung nachholt —
/// genau wie in der App, wo der Provider die Seite ueberlebt. Andersherum schriebe er in einen
/// bereits entsorgten ChangeNotifier.
Future<void> _closeEditor(WidgetTester tester, StudyProvider provider) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pumpAndSettle();
  provider.dispose();
}

/// Index 0 ist der Titel, danach folgen die Bloecke in ihrer Reihenfolge.
TextEditingController _controllerAt(WidgetTester tester, int index) =>
    tester.widget<EditableText>(find.byType(EditableText).at(index)).controller;

/// Setzt die Einfuegemarke im schon fokussierten Block.
void _caret(WidgetTester tester, int index, int offset) {
  _controllerAt(tester, index).selection = TextSelection.collapsed(offset: offset);
}

/// Fokussiert einen Block ueber seinen Text und setzt die Einfuegemarke.
Future<void> _focusBlock(
  WidgetTester tester,
  String text, {
  required int index,
  required int offset,
}) async {
  await tester.tap(find.text(text));
  await tester.pumpAndSettle();
  _caret(tester, index, offset);
  await tester.pump();
}

void main() {
  late FakeStudyService fake;

  setUp(() => fake = FakeStudyService());

  group('Markdown-Umwandlung beim Tippen', () {
    testWidgets('"# " macht aus dem Absatz eine Ueberschrift', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'Text');

      await tester.tap(find.text('Text'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), '#');
      // Erst das Leerzeichen loest die Verwandlung aus — und wird dabei geschluckt.
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '#');

      await _closeEditor(tester, provider);
    });

    testWidgets('der Text hinter der Einfuegemarke bleibt stehen', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      // So sieht es beim Tippen aus: der Bindestrich steht schon vor dem Wort.
      await tester.enterText(find.byType(TextField).at(1), '-Punkt');
      _caret(tester, 1, 1);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- Punkt');

      await _closeEditor(tester, provider);
    });

    testWidgets('"[] " wird zur Checkbox', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), '[]');
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- [ ]');
      expect(find.byIcon(Icons.check_box_outline_blank), findsOneWidget);

      await _closeEditor(tester, provider);
    });

    testWidgets('woanders bleibt das Leerzeichen ein Leerzeichen', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      // Kein Kuerzel: mitten im Wort darf nichts passieren.
      await tester.enterText(find.byType(TextField).at(1), 'Hallo');
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'Hallo');

      await _closeEditor(tester, provider);
    });
  });

  group('Enter', () {
    testWidgets('teilt den Block an der Einfuegemarke', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'einszwei');

      await _focusBlock(tester, 'einszwei', index: 1, offset: 4);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'eins\nzwei');

      await _closeEditor(tester, provider);
    });

    testWidgets('setzt eine Aufzaehlung fort', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '- Punkt');

      await _focusBlock(tester, 'Punkt', index: 1, offset: 5);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- Punkt\n-');

      await _closeEditor(tester, provider);
    });

    testWidgets('unter einer Ueberschrift beginnt ein Absatz', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '# Titel');

      await _focusBlock(tester, 'Titel', index: 1, offset: 5);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      // Sonst legte Enter unter jeder Ueberschrift eine zweite Ueberschrift an.
      expect(fake.lastUpdatedNote!['content'], '# Titel\n');

      await _closeEditor(tester, provider);
    });

    testWidgets('mitten in einer Ueberschrift entstehen zwei Ueberschriften', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '# AB');

      await _focusBlock(tester, 'AB', index: 1, offset: 1);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '# A\n# B');

      await _closeEditor(tester, provider);
    });

    testWidgets('verlaesst eine Liste auf dem leeren Eintrag', (tester) async {
      final provider =
          await _pumpEditor(tester, fake: fake, content: '- Punkt\n-');

      await tester.tap(find.byType(TextField).at(2));
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- Punkt\n');

      await _closeEditor(tester, provider);
    });

    testWidgets('im Codeblock bleibt Enter ein Zeilenumbruch', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '```\neins\n```');

      await _focusBlock(tester, 'eins', index: 1, offset: 4);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      // Der Codeblock ist der einzige mehrzeilige Block; Enter darf ihn nicht teilen. Den
      // Umbruch selbst setzt in der App der Embedder — im Test kommt er nicht an, geprueft
      // wird deshalb nur, dass kein zweiter Block entstanden ist.
      expect(find.byType(TextField), findsNWidgets(2),
          reason: 'Titel und der eine Codeblock');
      expect(fake.lastUpdatedNote, isNull, reason: 'nichts hat sich geaendert');

      await _closeEditor(tester, provider);
    });
  });

  group('Backspace am Zeilenanfang', () {
    testWidgets('streift zuerst die Blockart ab', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '- Punkt');

      await _focusBlock(tester, 'Punkt', index: 1, offset: 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'Punkt');

      await _closeEditor(tester, provider);
    });

    testWidgets('rueckt danach aus', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'oben\n  tief');

      await _focusBlock(tester, 'tief', index: 2, offset: 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'oben\ntief');

      await _closeEditor(tester, provider);
    });

    testWidgets('verschmilzt zuletzt mit dem Vorgaenger', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'eins\nzwei');

      await _focusBlock(tester, 'zwei', index: 2, offset: 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'einszwei');
      // Die Einfuegemarke muss an der Nahtstelle stehen, nicht am Zeilenende.
      expect(_controllerAt(tester, 1).selection.baseOffset, 4);

      await _closeEditor(tester, provider);
    });

    testWidgets('loescht einen Trenner, statt ihn zu verschmelzen', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'oben\n---\nunten');

      await _focusBlock(tester, 'unten', index: 2, offset: 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'oben\nunten');

      await _closeEditor(tester, provider);
    });
  });

  group('Tab', () {
    testWidgets('rueckt ein und wieder aus', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: '- a\n- b');

      await _focusBlock(tester, 'b', index: 2, offset: 1);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await _settleSave(tester);
      expect(fake.lastUpdatedNote!['content'], '- a\n  - b');

      await tester.sendKeyEvent(LogicalKeyboardKey.tab, platform: 'linux');
      await _settleSave(tester);
      expect(fake.lastUpdatedNote!['content'], '- a\n  - b',
          reason: 'hoechstens eine Stufe tiefer als der Vorgaenger');

      await _closeEditor(tester, provider);
    });

    testWidgets('nimmt die Kinder eines Aufklappblocks mit', (tester) async {
      final provider =
          await _pumpEditor(tester, fake: fake, content: '- oben\n??? F\nAntwort\n???');

      await _focusBlock(tester, 'F', index: 2, offset: 1);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- oben\n  ??? F\n  Antwort\n  ???');

      await _closeEditor(tester, provider);
    });
  });

  group('Bloecke im Betrieb', () {
    testWidgets('ein Klick auf die Checkbox kippt sie und speichert', (tester) async {
      final provider =
          await _pumpEditor(tester, fake: fake, content: '- [ ] erste\n- [ ] zweite');

      await tester.tap(find.byIcon(Icons.check_box_outline_blank).at(1));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- [ ] erste\n- [x] zweite');

      await _closeEditor(tester, provider);
    });

    testWidgets('die Antwort eines Aufklappblocks ist zunaechst verborgen', (tester) async {
      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: '??? Was ist eine Ableitung?\nDie Steigung der Tangente.\n???',
      );

      expect(find.text('Was ist eine Ableitung?'), findsOneWidget);
      expect(find.text('Die Steigung der Tangente.'), findsNothing,
          reason: 'sonst liesse sich der Zettel nicht abfragen');

      await tester.tap(find.byIcon(Icons.chevron_right));
      await tester.pumpAndSettle();

      expect(find.text('Die Steigung der Tangente.'), findsOneWidget);

      await _closeEditor(tester, provider);
    });

    testWidgets('nummerierte Listen zaehlen ab der getippten Zahl hoch', (tester) async {
      final provider =
          await _pumpEditor(tester, fake: fake, content: '3. Drittens\n3. Viertens');

      // Die getippte Zahl gilt nur fuer den ersten Eintrag, danach wird gezaehlt.
      expect(find.text('3.'), findsOneWidget);
      expect(find.text('4.'), findsOneWidget);

      await _closeEditor(tester, provider);

      // Und die berechnete Nummer ist es auch, die gespeichert wird.
      expect(fake.lastUpdatedNote!['content'], '3. Drittens\n4. Viertens');
    });

    testWidgets('mehrzeilig Eingefuegtes wird zu eigenen Bloecken', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), '# Titel\n- eins\n- zwei');
      await _settleSave(tester);

      // Als Markup gelesen, nicht als Rohtext — so wie man es von Notion kennt.
      expect(fake.lastUpdatedNote!['content'], '# Titel\n- eins\n- zwei');

      await _closeEditor(tester, provider);
    });
  });

  group('Slash-Menue', () {
    /// Tippt [text] in den ersten Block und laesst die Palette darauf reagieren.
    Future<void> type(WidgetTester tester, String text) async {
      await tester.enterText(find.byType(TextField).at(1), text);
      await tester.pumpAndSettle();
    }

    testWidgets('"/" oeffnet die Palette, Tippen filtert sie', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      expect(find.text('Aufzählung'), findsNothing);

      await type(tester, '/');
      expect(find.text('Text'), findsOneWidget);
      expect(find.text('Aufzählung'), findsOneWidget);

      await type(tester, '/code');
      expect(find.text('Codeblock'), findsOneWidget);
      expect(find.text('Aufzählung'), findsNothing);

      await _settleSave(tester);
      await _closeEditor(tester, provider);
    });

    testWidgets('findet auch ohne Umlaut und ueber Suchwoerter', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();

      await type(tester, '/uber');
      expect(find.text('Überschrift 1'), findsOneWidget);

      // "Kasten" steht nirgends in der Beschriftung, nur in den Suchwoertern.
      await type(tester, '/kasten');
      expect(find.text('Todo'), findsOneWidget);

      await _settleSave(tester);
      await _closeEditor(tester, provider);
    });

    testWidgets('Pfeiltasten und Enter waehlen einen Befehl', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await type(tester, '/');

      // Der erste Eintrag ist "Text", der zweite "Ueberschrift 1".
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      // Die Abfrage selbst faellt weg, uebrig bleibt der leere Block in neuer Art.
      expect(fake.lastUpdatedNote!['content'], '#');
      expect(find.text('Überschrift 1'), findsNothing, reason: 'Menue ist zu');

      await _closeEditor(tester, provider);
    });

    testWidgets('ein Klick waehlt ebenso', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await type(tester, '/todo');
      await tester.tap(find.text('Todo'));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- [ ]');

      await _closeEditor(tester, provider);
    });

    testWidgets('behaelt, was links und rechts der Abfrage stand', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), 'Anfang /liste Ende');
      // Einfuegemarke hinter "liste", nicht am Zeilenende.
      _caret(tester, 1, 13);
      await tester.pump();
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '- Anfang  Ende');

      await _closeEditor(tester, provider);
    });

    testWidgets('Escape schickt es weg und es kommt nicht zurueck', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await type(tester, '/co');
      expect(find.text('Codeblock'), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Codeblock'), findsNothing);

      // Weitertippen an derselben Stelle darf es nicht wieder aufspringen lassen.
      await type(tester, '/co');
      expect(find.text('Codeblock'), findsNothing);

      await _settleSave(tester);
      await _closeEditor(tester, provider);
    });

    testWidgets('ein Schraegstrich mitten im Wort oeffnet nichts', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      // Sonst spraenge das Menue in jedem Bruch und jeder Netzadresse auf.
      await type(tester, 'km/h');
      expect(find.text('Codeblock'), findsNothing);

      await type(tester, 'Anfang / Ende');
      expect(find.text('Codeblock'), findsNothing,
          reason: 'ein Leerzeichen beendet die Abfrage');

      await _settleSave(tester);
      await _closeEditor(tester, provider);
    });

    testWidgets('der Trenner setzt sich ein und laesst darunter weiterschreiben',
        (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'x');

      await tester.tap(find.text('x'));
      await tester.pumpAndSettle();
      await type(tester, '/trenner');
      await tester.tap(find.text('Trenner'));
      await _settleSave(tester);

      // Der Trenner traegt keinen Text, darunter steht ein leerer Absatz zum Weiterschreiben.
      expect(fake.lastUpdatedNote!['content'], '---\n');
      expect(find.byType(Divider), findsOneWidget);

      await _closeEditor(tester, provider);
    });
  });

  group('Blockrinne und Blockmenue', () {
    /// Der ⋮⋮-Griff des Blocks an [index] — die Rinne ist erst sichtbar, wenn der Block die
    /// Einfuegemarke haelt oder ueberfahren wird.
    Finder handleAt(int index) => find.byIcon(Icons.drag_indicator).at(index);

    testWidgets('das Blockmenue dupliziert einen Block', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'eins\nzwei');

      await tester.tap(find.text('eins'));
      await tester.pumpAndSettle();
      await tester.tap(handleAt(0));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Duplizieren'));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'eins\neins\nzwei');

      await _closeEditor(tester, provider);
    });

    testWidgets('das Blockmenue loescht einen Aufklappblock samt Kindern', (tester) async {
      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: 'oben\n??? Frage\nAntwort\n???',
      );

      await tester.tap(find.text('Frage'));
      await tester.pumpAndSettle();
      await tester.tap(handleAt(1));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Löschen'));
      await _settleSave(tester);

      // Der Rumpf darf nicht als elternloser Rest zurueckbleiben.
      expect(fake.lastUpdatedNote!['content'], 'oben');

      await _closeEditor(tester, provider);
    });

    testWidgets('das Blockmenue wandelt die Blockart um', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'Text');

      await tester.tap(find.text('Text'));
      await tester.pumpAndSettle();
      await tester.tap(handleAt(0));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Überschrift 2'));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '## Text');

      await _closeEditor(tester, provider);
    });

    testWidgets('"+" legt einen Block darunter an und oeffnet das Befehlsmenue',
        (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'oben');

      await tester.tap(find.text('oben'));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.add).first);
      await tester.pumpAndSettle();

      expect(find.text('Aufzählung'), findsOneWidget, reason: 'das Slash-Menue steht offen');

      await tester.tap(find.text('Aufzählung'));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'oben\n-');

      await _closeEditor(tester, provider);
    });

    testWidgets('Ziehen ordnet um und nimmt Kinder mit', (tester) async {
      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: '??? Frage\nAntwort\n???\nletzte',
      );

      await tester.tap(find.text('Frage'));
      await tester.pumpAndSettle();

      // Mit der Maus, weil der Griff dafuer ab acht Pixeln greift statt per Long-Press.
      final gesture = await tester.startGesture(
        tester.getCenter(handleAt(0)),
        kind: PointerDeviceKind.mouse,
      );
      await tester.pump(const Duration(milliseconds: 20));
      // Unter den letzten Block: die Ablagestelle ganz am Ende des Dokuments.
      await gesture.moveTo(tester.getBottomLeft(find.text('letzte')) + const Offset(20, 6));
      await tester.pump();
      await gesture.up();
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'letzte\n??? Frage\nAntwort\n???');

      await _closeEditor(tester, provider);
    });
  });

  group('Formatierung im Text', () {
    /// Markiert im schon fokussierten Block und laesst die Leiste erscheinen.
    Future<void> select(WidgetTester tester, int index, int from, int to) async {
      _controllerAt(tester, index).selection =
          TextSelection(baseOffset: from, extentOffset: to);
      await tester.pumpAndSettle();
    }

    testWidgets('die Leiste erscheint erst bei einer Auswahl', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'Hallo Welt');

      await tester.tap(find.text('Hallo Welt'));
      await tester.pumpAndSettle();
      expect(find.byType(NoteFormatToolbar), findsNothing);

      await select(tester, 1, 0, 5);
      expect(find.byType(NoteFormatToolbar), findsOneWidget);

      // Wieder aufgehoben heisst wieder weg.
      await select(tester, 1, 5, 5);
      expect(find.byType(NoteFormatToolbar), findsNothing);

      await _settleSave(tester);
      await _closeEditor(tester, provider);
    });

    testWidgets('ein Klick auf B legt die Auszeichnung um die Auswahl', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'Hallo Welt');

      await tester.tap(find.text('Hallo Welt'));
      await tester.pumpAndSettle();
      await select(tester, 1, 6, 10);
      await tester.tap(find.text('B'));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'Hallo **Welt**');

      await _closeEditor(tester, provider);
    });

    testWidgets('derselbe Knopf nimmt sie wieder ab', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'Hallo **Welt**');

      await tester.tap(find.text('Hallo **Welt**'));
      await tester.pumpAndSettle();
      await select(tester, 1, 6, 14);
      await tester.tap(find.text('B'));
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'Hallo Welt');

      await _closeEditor(tester, provider);
    });

    testWidgets('Strg+B tut dasselbe wie der Knopf', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'Hallo Welt');

      await tester.tap(find.text('Hallo Welt'));
      await tester.pumpAndSettle();
      await select(tester, 1, 0, 5);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyB);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], '**Hallo** Welt');

      await _closeEditor(tester, provider);
    });

    testWidgets('Strg+E setzt Code, Strg+H markiert', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'ab');

      await tester.tap(find.text('ab'));
      await tester.pumpAndSettle();
      await select(tester, 1, 0, 2);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyE);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyH);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await _settleSave(tester);

      // Die Auswahl bleibt auf dem Ergebnis stehen, darum legt sich das zweite aussen herum.
      expect(fake.lastUpdatedNote!['content'], '==`ab`==');

      await _closeEditor(tester, provider);
    });

    testWidgets('das Feld zeichnet die Auszeichnung, ohne Zeichen zu schlucken',
        (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'ein **fettes** Wort');

      final editable = tester.widget<EditableText>(find.byType(EditableText).at(1));
      final span = editable.controller.buildTextSpan(
        context: tester.element(find.byType(EditableText).at(1)),
        style: const TextStyle(fontSize: 14),
        withComposing: false,
      );

      // Kein Zeichen darf verschwinden, sonst zeigten Einfuegemarke und Auswahl daneben.
      expect(span.toPlainText(), 'ein **fettes** Wort');
      // Aber der Inhalt zwischen den Sternchen ist fett gesetzt.
      final bold = <String>[];
      span.visitChildren((s) {
        if (s is TextSpan && s.style?.fontWeight == FontWeight.bold) bold.add(s.text ?? '');
        return true;
      });
      expect(bold, ['fettes']);

      await _closeEditor(tester, provider);
    });

    testWidgets('im Codeblock bleibt ** genau das, was dasteht', (tester) async {
      final provider =
          await _pumpEditor(tester, fake: fake, content: '```\n**kein Fettdruck**\n```');

      final editable = tester.widget<EditableText>(find.byType(EditableText).at(1));
      final span = editable.controller.buildTextSpan(
        context: tester.element(find.byType(EditableText).at(1)),
        style: const TextStyle(fontSize: 14),
        withComposing: false,
      );

      var bold = 0;
      span.visitChildren((s) {
        if (s is TextSpan && s.style?.fontWeight == FontWeight.bold) bold++;
        return true;
      });
      expect(bold, 0);
      expect(span.toPlainText(), '**kein Fettdruck**');

      await _closeEditor(tester, provider);
    });
  });

  group('Darstellung', () {
    // Ein Ueberlauf im Layout laesst testWidgets von selbst scheitern — dieser Test ist also
    // vor allem eine Sichtpruefung auf einem Desktop-Fenster mit allen Blockarten auf einmal.
    testWidgets('ein reichhaltiges Dokument passt auf ein Desktop-Fenster', (tester) async {
      const doc = '# Analysis I\n'
          '\n'
          'Ein Absatz mit **fett**, *kursiv*, `code` und ==markiert==.\n'
          '## Kapitel 1\n'
          '- Aufzählung\n'
          '  - eingerückt\n'
          '1. Erstens\n'
          '2. Zweitens\n'
          '- [ ] offen\n'
          '- [x] erledigt\n'
          '> Ein Merksatz, der lang genug ist, um über die Zeile hinauszulaufen und '
          'umbrechen zu müssen.\n'
          '---\n'
          '```\n'
          'int main() { return 0; }\n'
          '```\n'
          '??? Was ist Stetigkeit?\n'
          '- Grenzwert gleich Funktionswert\n'
          '???';

      final provider = await _pumpEditor(
        tester,
        fake: fake,
        content: doc,
        surface: const Size(1400, 900),
      );

      expect(find.text('Analysis I'), findsOneWidget);
      expect(find.byType(Divider), findsOneWidget);
      // Der Rumpf des Aufklappblocks bleibt zu, bis jemand ihn oeffnet.
      expect(find.text('Grenzwert gleich Funktionswert'), findsNothing);

      // Ueberfahren blendet die Anfasser ein, ohne dass der Text springt.
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      addTearDown(mouse.removePointer);
      await mouse.moveTo(tester.getCenter(find.text('Erstens')));
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.drag_indicator), findsWidgets);

      await _settleSave(tester);
      await _closeEditor(tester, provider);

      // Und das blosse Ansehen hat nichts geschrieben.
      expect(fake.lastUpdatedNote, isNull);
    });
  });

  group('Speichern', () {
    testWidgets('laeuft von selbst, ohne Knopf', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'alt');

      expect(find.text('Speichern'), findsNothing);

      await tester.tap(find.text('alt'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), 'neu');
      await _settleSave(tester);

      expect(fake.lastUpdatedNote!['content'], 'neu');
      expect(find.text('Gespeichert'), findsOneWidget);

      await _closeEditor(tester, provider);
    });

    testWidgets('das blosse Oeffnen aendert eine Bestandsnotiz nicht', (tester) async {
      const doc = '# Titel\n\n- [ ] offen\n- [x] fertig\n> Merksatz\n??? Frage\nAntwort\n???';
      final provider = await _pumpEditor(tester, fake: fake, content: doc);

      await _settleSave(tester);
      await _closeEditor(tester, provider);

      // Auch der Abbau der Seite darf nichts schreiben — sonst waere jedes blosse Nachlesen
      // eines Lernzettels ein Schreibvorgang.
      expect(fake.lastUpdatedNote, isNull);
    });

    testWidgets('meldet einen gescheiterten Versuch', (tester) async {
      final provider = await _pumpEditor(tester, fake: fake, content: 'alt');
      fake.failUpdateNote = true;

      await tester.tap(find.text('alt'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), 'neu');
      await _settleSave(tester);

      expect(find.text('Nicht gespeichert'), findsOneWidget);

      await _closeEditor(tester, provider);
    });
  });
}
