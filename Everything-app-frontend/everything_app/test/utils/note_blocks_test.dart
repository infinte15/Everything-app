import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/utils/note_blocks.dart';

/// Der Editor haelt seine Regeln bewusst hier, ausserhalb des Widgetbaums: Teilen,
/// Verschmelzen, Einruecken und vor allem das Serialisieren sind reine Funktionen und werden
/// auch so geprueft. Die wichtigste Zusicherung ist der Rundlauf — eine Bestandsnotiz darf
/// sich vom blossen Oeffnen und Speichern nicht veraendern.
void main() {
  /// Kurzschreibweise fuer den Rundlauf Markup → Bloecke → Markup.
  String roundTrip(String markup) => markupFromBlocks(blocksFromMarkup(markup));

  group('blocksFromMarkup', () {
    test('erkennt jede Blockart', () {
      final blocks = blocksFromMarkup([
        '# Titel',
        '- Punkt',
        '1. Erstens',
        '- [ ] offen',
        '- [x] erledigt',
        '> Merksatz',
        '---',
        'Fliesstext',
      ].join('\n'));

      expect(blocks.map((b) => b.kind), [
        BlockKind.heading,
        BlockKind.bullet,
        BlockKind.numbered,
        BlockKind.todo,
        BlockKind.todo,
        BlockKind.callout,
        BlockKind.divider,
        BlockKind.paragraph,
      ]);
      expect(blocks[0].level, 1);
      expect(blocks[3].checked, isFalse);
      expect(blocks[4].checked, isTrue);
    });

    test('vergibt lauter verschiedene IDs', () {
      final blocks = blocksFromMarkup('eins\nzwei\ndrei');
      expect(blocks.map((b) => b.id).toSet().length, 3);
    });

    test('ein leerer Inhalt ergibt einen leeren Absatz zum Hineinschreiben', () {
      final blocks = blocksFromMarkup('');
      expect(blocks.single.kind, BlockKind.paragraph);
      expect(blocks.single.text, isEmpty);
    });

    test('eine Leerzeile wird zum leeren Absatz, nicht verschluckt', () {
      final blocks = blocksFromMarkup('oben\n\nunten');
      expect(blocks.map((b) => b.text), ['oben', '', 'unten']);
    });

    test('ein Codeblock ist EIN Block mit Zeilenumbruechen im Text', () {
      final blocks = blocksFromMarkup('```\nzeile 1\nzeile 2\n```');
      expect(blocks.single.kind, BlockKind.code);
      expect(blocks.single.text, 'zeile 1\nzeile 2');
    });

    // Der Kern der Abflachung: aus der Rekursion des Auszeichners wird Tiefe.
    test('ein Aufklappblock wird zu Elternblock plus tieferem Lauf', () {
      final blocks = blocksFromMarkup([
        '??? Was ist Stetigkeit?',
        '- Grenzwert gleich Funktionswert',
        '- [ ] nachschlagen',
        '???',
        'danach',
      ].join('\n'));

      expect(blocks.map((b) => b.kind), [
        BlockKind.toggle,
        BlockKind.bullet,
        BlockKind.todo,
        BlockKind.paragraph,
      ]);
      expect(blocks.map((b) => b.depth), [0, 1, 1, 0]);
      expect(blocks.first.text, 'Was ist Stetigkeit?');
    });

    test('Einrueckung im Aufklapp-Rumpf zaehlt zusaetzlich, nicht doppelt', () {
      final blocks = blocksFromMarkup([
        '??? Frage',
        '  - direktes Kind',
        '    - Enkel',
        '???',
      ].join('\n'));

      expect(blocks.map((b) => b.depth), [0, 1, 2]);
    });

    test('die Altform "- [] " wird zu "- [ ] " vereinheitlicht', () {
      expect(blocksFromMarkup('- [] alt').single.checked, isFalse);
      expect(roundTrip('- [] alt'), '- [ ] alt');
    });
  });

  group('markupFromBlocks', () {
    test('Rundlauf laesst Bestandsnotizen unveraendert', () {
      const doc = '# Analysis I\n'
          '\n'
          'Eine Notiz mit **fett** und ==markiert==.\n'
          '\n'
          '## Kapitel 1\n'
          '- Punkt\n'
          '- noch ein Punkt\n'
          '- [ ] offen\n'
          '- [x] erledigt\n'
          '> Merksatz\n'
          '---\n'
          '```\n'
          'code() bleibt code()\n'
          '```\n'
          '??? Frage\n'
          'Antwort\n'
          '???';

      expect(roundTrip(doc), doc);
    });

    test('schliesst den Aufklapp-Zaun, sobald der Lauf endet', () {
      final blocks = [
        EditorBlock.fresh(kind: BlockKind.toggle, text: 'Frage'),
        EditorBlock.fresh(depth: 1, text: 'Antwort'),
        EditorBlock.fresh(text: 'danach'),
      ];

      // Das direkte Kind steht buendig: der Zaun drueckt die Schachtelung schon aus.
      expect(markupFromBlocks(blocks), '??? Frage\nAntwort\n???\ndanach');
    });

    test('schliesst offene Zaeune am Dokumentende', () {
      final blocks = [
        EditorBlock.fresh(kind: BlockKind.toggle, text: 'Frage'),
        EditorBlock.fresh(depth: 1, text: 'Antwort'),
      ];

      expect(markupFromBlocks(blocks), '??? Frage\nAntwort\n???');
    });

    test('nur die Einrueckung, die der Zaun nicht schon ausdrueckt', () {
      final blocks = [
        EditorBlock.fresh(kind: BlockKind.toggle, text: 'Frage'),
        EditorBlock.fresh(kind: BlockKind.bullet, depth: 1, text: 'Kind'),
        EditorBlock.fresh(kind: BlockKind.bullet, depth: 2, text: 'Enkel'),
      ];

      expect(markupFromBlocks(blocks), '??? Frage\n- Kind\n  - Enkel\n???');
    });

    // Sonst wuerde aus einem gerade angelegten, noch leeren Listeneintrag beim naechsten
    // Oeffnen ein woertlicher Bindestrich: die Zeile "- " verliert ihr Leerzeichen.
    test('ein leerer Block behaelt seine Art ueber das Speichern hinweg', () {
      for (final markup in ['#', '##', '-', '- [ ]', '- [x]', '1.', '>']) {
        expect(roundTrip(markup), markup, reason: 'Rundlauf von "$markup"');
      }

      expect(blocksFromMarkup('#').single.kind, BlockKind.heading);
      expect(blocksFromMarkup('-').single.kind, BlockKind.bullet);
      expect(blocksFromMarkup('- [x]').single.checked, isTrue);
      expect(blocksFromMarkup('1.').single.kind, BlockKind.numbered);
    });

    test('ein Trenner geht einer leeren Aufzaehlung vor', () {
      expect(blocksFromMarkup('---').single.kind, BlockKind.divider);
    });

    test('ein leerer Aufklappblock laeuft hin und zurueck', () {
      const doc = '???\nAntwort\n???';
      expect(roundTrip(doc), doc);
      expect(blocksFromMarkup(doc).map((b) => b.kind),
          [BlockKind.toggle, BlockKind.paragraph]);
    });

    test('geschachtelte Aufklappbloecke laufen hin und zurueck', () {
      const doc = '??? Aussen\n??? Innen\nAntwort\n???\ndanach\n???';
      expect(markupFromBlocks(blocksFromMarkup(doc)), doc);
      expect(blocksFromMarkup(doc).map((b) => b.depth), [0, 1, 2, 1]);
    });

    test('eine leere Zeile traegt keine Leerzeichen', () {
      final blocks = [
        EditorBlock.fresh(kind: BlockKind.toggle, text: 'F'),
        EditorBlock.fresh(depth: 1),
      ];
      // Sonst waechst der Text bei jedem Speichern um unsichtbare Zeichen.
      expect(markupFromBlocks(blocks).split('\n')[1], '');
    });
  });

  group('displayNumber', () {
    test('zaehlt ab der getippten Startzahl hoch', () {
      // Notion-Verhalten: eine mit 10. begonnene Liste faellt nicht auf 1 zurueck.
      final blocks = blocksFromMarkup('10. Zehntens\n11. Elftens\n12. Zwoelftens');
      expect([0, 1, 2].map((i) => displayNumber(blocks, i)), [10, 11, 12]);
    });

    test('nummeriert falsch getippte Zahlen um', () {
      final blocks = blocksFromMarkup('1. eins\n1. eins\n1. eins');
      expect([0, 1, 2].map((i) => displayNumber(blocks, i)), [1, 2, 3]);
    });

    test('ein fremder Block dazwischen beginnt einen neuen Lauf', () {
      final blocks = blocksFromMarkup('1. eins\n2. zwei\nText\n1. wieder eins');
      expect(displayNumber(blocks, 3), 1);
    });

    test('tiefer geschachtelte Bloecke unterbrechen den Lauf nicht', () {
      final blocks = blocksFromMarkup('1. eins\n  - Detail\n2. zwei');
      expect(displayNumber(blocks, 2), 2);
    });
  });

  group('markdownShortcut', () {
    test('erkennt die Kuerzel', () {
      expect(markdownShortcut('#')?.kind, BlockKind.heading);
      expect(markdownShortcut('#')?.level, 1);
      expect(markdownShortcut('###')?.level, 3);
      expect(markdownShortcut('-')?.kind, BlockKind.bullet);
      expect(markdownShortcut('*')?.kind, BlockKind.bullet);
      expect(markdownShortcut('[]')?.kind, BlockKind.todo);
      expect(markdownShortcut('>')?.kind, BlockKind.callout);
      expect(markdownShortcut('???')?.kind, BlockKind.toggle);
      expect(markdownShortcut('```')?.kind, BlockKind.code);
      expect(markdownShortcut('---')?.kind, BlockKind.divider);
    });

    test('eine Zahl mit Punkt wird zur nummerierten Liste ab dieser Zahl', () {
      expect(markdownShortcut('1.')?.kind, BlockKind.numbered);
      expect(markdownShortcut('7.')?.startNumber, 7);
    });

    test('gibt null zurueck, wo kein Kuerzel steht', () {
      // Sonst wuerde jedes Leerzeichen zur Verwandlung.
      expect(markdownShortcut(''), isNull);
      expect(markdownShortcut('Hallo'), isNull);
      expect(markdownShortcut('####'), isNull);
      expect(markdownShortcut('x_1'), isNull);
    });
  });

  group('continuationOf', () {
    test('Listen setzen sich fort, alles andere faellt auf den Absatz zurueck', () {
      EditorBlock next(BlockKind k) =>
          continuationOf(EditorBlock.fresh(kind: k, text: 'x'));

      expect(next(BlockKind.bullet).kind, BlockKind.bullet);
      expect(next(BlockKind.todo).kind, BlockKind.todo);
      expect(next(BlockKind.numbered).kind, BlockKind.numbered);
      expect(next(BlockKind.callout).kind, BlockKind.callout);
      // Sonst legte Enter unter einer Ueberschrift eine zweite Ueberschrift an.
      expect(next(BlockKind.heading).kind, BlockKind.paragraph);
    });

    test('eine fortgesetzte Checkbox startet unangehakt', () {
      final done = EditorBlock.fresh(kind: BlockKind.todo, text: 'x', checked: true);
      expect(continuationOf(done).checked, isFalse);
    });

    test('Enter im Kopf eines Aufklappblocks legt dessen erstes Kind an', () {
      final toggle = EditorBlock.fresh(kind: BlockKind.toggle, text: 'Frage');
      final child = continuationOf(toggle);
      expect(child.kind, BlockKind.paragraph);
      expect(child.depth, 1);
    });
  });

  group('toParagraph', () {
    test('streift Art, Haken und Ebene ab, behaelt Text und Tiefe', () {
      final b = EditorBlock.fresh(
          kind: BlockKind.todo, depth: 2, text: 'Rest', checked: true);
      final p = toParagraph(b);

      expect(p.kind, BlockKind.paragraph);
      expect(p.checked, isFalse);
      expect(p.text, 'Rest');
      expect(p.depth, 2);
      expect(p.id, b.id, reason: 'derselbe Block, nur andere Art');
    });
  });

  group('maxDepthAt', () {
    test('der erste Block bleibt auf 0', () {
      final blocks = blocksFromMarkup('- a\n- b');
      expect(maxDepthAt(blocks, 0), 0);
    });

    test('hoechstens eine Stufe tiefer als der Vorgaenger', () {
      final blocks = blocksFromMarkup('- a\n- b');
      expect(maxDepthAt(blocks, 1), 1);
    });

    test('nach einem tiefen Vorgaenger darf es entsprechend tiefer werden', () {
      final blocks = blocksFromMarkup('- a\n  - b\n- c');
      expect(maxDepthAt(blocks, 2), 2);
    });
  });

  group('runLengthAt', () {
    test('ein Block ohne Kinder ist ein Lauf von eins', () {
      final blocks = blocksFromMarkup('a\nb\nc');
      expect(runLengthAt(blocks, 1), 1);
    });

    test('ein Aufklappblock nimmt seine Kinder mit', () {
      final blocks = blocksFromMarkup('??? F\n- eins\n- zwei\n???\ndanach');
      expect(runLengthAt(blocks, 0), 3);
    });

    test('der Lauf endet, sobald es wieder flacher wird', () {
      final blocks = blocksFromMarkup('- a\n  - b\n    - c\n- d');
      expect(runLengthAt(blocks, 0), 3);
      expect(runLengthAt(blocks, 3), 1);
    });
  });

  group('moveRun', () {
    List<String> texts(List<EditorBlock> b) => b.map((e) => e.text).toList();

    test('schiebt einen Block nach unten', () {
      final blocks = blocksFromMarkup('a\nb\nc');
      expect(texts(moveRun(blocks, 0, 1, 3)), ['b', 'c', 'a']);
    });

    test('schiebt einen Block nach oben', () {
      final blocks = blocksFromMarkup('a\nb\nc');
      expect(texts(moveRun(blocks, 2, 1, 0)), ['c', 'a', 'b']);
    });

    test('nimmt die Kinder eines Aufklappblocks mit', () {
      final blocks = blocksFromMarkup('??? F\n- eins\n???\ndanach');
      final moved = moveRun(blocks, 0, runLengthAt(blocks, 0), 3);

      expect(texts(moved), ['danach', 'F', 'eins']);
      expect(moved.map((b) => b.depth), [0, 0, 1],
          reason: 'die innere Staffelung bleibt erhalten');
    });

    test('ein Ziel im bewegten Lauf bleibt wirkungslos', () {
      // Sonst spleisst man einen Block in sich selbst.
      final blocks = blocksFromMarkup('??? F\n- eins\n- zwei\n???');
      expect(texts(moveRun(blocks, 0, 3, 2)), texts(blocks));
    });

    test('ein Block behaelt beim Ziehen seine eigene Tiefe', () {
      final blocks = blocksFromMarkup('- a\n  - b\nc');
      // 'c' hinter das eingerueckte 'b': dort waere Tiefe 2 erlaubt, gewollt ist sie nicht.
      final moved = moveRun(blocks, 2, 1, 2);
      expect(moved.map((b) => b.depth), [0, 1, 0]);
    });

    test('zu tief fuer das Ziel rutscht der Lauf nach oben', () {
      final blocks = blocksFromMarkup('- a\n  - b\nc');
      // Das eingerueckte 'b' vor 'a' ziehen: dort ist nur Tiefe 0 erlaubt.
      final moved = moveRun(blocks, 1, 1, 0);
      expect(texts(moved), ['b', 'a', 'c']);
      expect(moved.first.depth, 0);
    });

    test('an den Anfang gezogen landet der Lauf auf Tiefe 0', () {
      final blocks = blocksFromMarkup('- a\n  - b');
      final moved = moveRun(blocks, 1, 1, 0);
      expect(texts(moved), ['b', 'a']);
      expect(moved.first.depth, 0);
    });
  });
}
