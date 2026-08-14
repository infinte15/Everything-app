import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/utils/note_markup.dart';

/// Reines Dart, keine Widgets — der Auszeichner ist eine Funktion und soll auch so geprueft
/// werden. Das bearbeitbare Blockmodell, das der Editor daraus baut, hat seine eigenen Tests
/// in note_blocks_test.dart.
void main() {
  const base = TextStyle(fontSize: 14);
  List<InlineSpan> spans(String text) => inlineSpans(
        text,
        base,
        codeBackground: const Color(0xFF252626),
        highlightBackground: const Color(0xFFFF9F0A),
      );

  group('parseBlocks', () {
    test('erkennt die Zeilenpraefixe', () {
      final blocks = parseBlocks([
        '# Titel',
        '## Untertitel',
        '### Klein',
        '- Punkt',
        '1. Erstens',
        '- [ ] offen',
        '- [x] erledigt',
        '> Merksatz',
        '---',
        'Fließtext',
      ].join('\n'));

      expect(blocks[0], isA<HeadingBlock>().having((b) => b.level, 'level', 1));
      expect(blocks[1], isA<HeadingBlock>().having((b) => b.level, 'level', 2));
      expect(blocks[2], isA<HeadingBlock>().having((b) => b.level, 'level', 3));
      expect(blocks[3], isA<BulletBlock>().having((b) => b.text, 'text', 'Punkt'));
      expect(blocks[4], isA<NumberedBlock>().having((b) => b.marker, 'marker', '1'));
      expect(blocks[5], isA<TodoBlock>().having((b) => b.done, 'done', false));
      expect(blocks[6], isA<TodoBlock>().having((b) => b.done, 'done', true));
      expect(blocks[7], isA<CalloutBlock>());
      expect(blocks[8], isA<DividerBlock>());
      expect(blocks[9], isA<ParagraphBlock>());
    });

    test('die Altform "- [] " gilt weiter als offene Checkbox', () {
      final blocks = parseBlocks('- [] alt');
      expect(blocks.single, isA<TodoBlock>().having((b) => b.done, 'done', false));
      expect((blocks.single as TodoBlock).text, 'alt');
    });

    test('nummerierte Listen behalten ihre getippte Zahl', () {
      final blocks = parseBlocks('10. Zehntens');
      expect((blocks.single as NumberedBlock).marker, '10',
          reason: 'der Rohtext ist die Wahrheit, es wird nicht neu durchnummeriert');
    });

    // Davon haengt die anklickbare Checkbox ab.
    test('startLine zeigt auf die echte Zeile im Gesamtdokument', () {
      final blocks = parseBlocks([
        '# Titel',       // 0
        '',              // 1
        '- [ ] erste',   // 2
        '- [ ] zweite',  // 3
      ].join('\n'));

      final todos = blocks.whereType<TodoBlock>().toList();
      expect(todos.map((t) => t.startLine), [2, 3]);
    });

    test('ein Aufklappblock verbirgt seinen Rumpf und parst ihn mit', () {
      final blocks = parseBlocks([
        '??? Was ist eine Cauchy-Folge?',
        '- Glieder liegen beliebig nah beieinander',
        '- [ ] nachschlagen',
        '???',
      ].join('\n'));

      final toggle = blocks.whereType<ToggleBlock>().single;
      expect(toggle.question, 'Was ist eine Cauchy-Folge?');
      expect(toggle.body.length, 2);
      expect(toggle.body[0], isA<BulletBlock>());
      // Auch im Rumpf muss die Zeilennummer stimmen, sonst schreibt der Haken daneben.
      expect((toggle.body[1] as TodoBlock).startLine, 2);
    });

    test('ein Codeblock wird nicht weiter zerlegt', () {
      final blocks = parseBlocks([
        '```',
        '# kein Titel',
        '- kein Punkt',
        '```',
      ].join('\n'));

      final code = blocks.whereType<CodeBlock>().single;
      expect(code.lines, ['# kein Titel', '- kein Punkt']);
    });

    test('ein offener Zaun frisst den Rest, statt Text zu verlieren', () {
      // Genau der Zustand waehrend des Tippens.
      final blocks = parseBlocks(['```', 'noch am Tippen'].join('\n'));
      expect((blocks.single as CodeBlock).lines, ['noch am Tippen']);

      final offen = parseBlocks(['??? Frage', 'Antwort'].join('\n'));
      expect((offen.single as ToggleBlock).body.length, 1);
    });
  });

  group('inlineSpans', () {
    String rendered(List<InlineSpan> s) =>
        s.map((e) => (e as TextSpan).text ?? '').join();

    test('fett, kursiv, Code und Markierung', () {
      final s = spans('**fett** *kursiv* `code` ==markiert==');
      expect(rendered(s), 'fett kursiv code markiert');

      final styles = s.whereType<TextSpan>().map((e) => e.style!).toList();
      expect(styles.any((st) => st.fontWeight == FontWeight.bold), isTrue);
      expect(styles.any((st) => st.fontStyle == FontStyle.italic), isTrue);
      expect(styles.any((st) => st.fontFamily == 'monospace'), isTrue);
      expect(styles.any((st) => st.backgroundColor == const Color(0xFFFF9F0A)), isTrue);
    });

    test('ein einzelnes Sternchen bleibt woertlich stehen', () {
      expect(rendered(spans('2 * 3 = 6')), '2 * 3 = 6');
    });

    // Der Grund, warum es kein _kursiv_ gibt.
    test('Unterstriche in Formeln bleiben unangetastet', () {
      expect(rendered(spans('f_n(x) und x_1')), 'f_n(x) und x_1');
    });

    test('Text ohne Auszeichnung kommt unveraendert durch', () {
      expect(rendered(spans('nichts besonderes')), 'nichts besonderes');
    });
  });

  group('Einrueckung', () {
    test('je zwei Leerzeichen sind eine Stufe', () {
      final blocks = parseBlocks([
        '- oben',
        '  - eine Stufe tiefer',
        '    - zwei Stufen tiefer',
      ].join('\n'));

      expect(blocks.map((b) => b.indent), [0, 1, 2]);
      // Frueher wurde das Praefix gegen den links nicht getrimmten String geprueft — aus
      // eingerueckten Aufzaehlungen wurden stillschweigend Absaetze.
      expect(blocks.every((b) => b is BulletBlock), isTrue);
    });

    test('Bestandsnotizen ohne Einrueckung landen auf 0', () {
      final blocks = parseBlocks('# Titel\n- Punkt\nText');
      expect(blocks.every((b) => b.indent == 0), isTrue);
    });

    test('ein eingerueckter Codeblock behaelt seinen Rumpf woertlich', () {
      final blocks = parseBlocks([
        '  ```',
        '  if (x) {',
        '    return 1;',
        '  }',
        '  ```',
      ].join('\n'));

      final code = blocks.whereType<CodeBlock>().single;
      expect(code.indent, 1);
      // Die Einrueckung des Zauns faellt weg, die des Codes selbst bleibt.
      expect(code.lines, ['if (x) {', '  return 1;', '}']);
    });
  });
}
