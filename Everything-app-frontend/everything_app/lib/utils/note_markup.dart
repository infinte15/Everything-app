import 'package:flutter/widgets.dart';

/// Der Lernzettel-Auszeichner.
///
/// Bewusst **kein** Blockmodell: der Inhalt bleibt ein einziger String in
/// `StudyNote.content`, und [parseBlocks] ist eine reine Funktion darauf. Der Kniff, der die
/// anklickbare Checkbox trotzdem möglich macht, ist [NoteBlock.startLine] — damit weiß der
/// Editor, welche Zeile er beim Antippen umschreiben muss.
///
/// Zwei Konstrukte umfassen mehrere Zeilen (` ``` ` und `???`); alles andere entscheidet sich
/// am Zeilenanfang.
sealed class NoteBlock {
  /// Index der ersten Zeile dieses Blocks in `content.split('\n')`.
  final int startLine;
  const NoteBlock(this.startLine);
}

class HeadingBlock extends NoteBlock {
  final String text;
  final int level; // 1..3
  const HeadingBlock(super.startLine, this.text, this.level);
}

class TodoBlock extends NoteBlock {
  final String text;
  final bool done;
  const TodoBlock(super.startLine, this.text, this.done);
}

class BulletBlock extends NoteBlock {
  final String text;
  const BulletBlock(super.startLine, this.text);
}

class NumberedBlock extends NoteBlock {
  final String text;

  /// Die getippte Zahl, nicht neu durchnummeriert — der Rohtext bleibt die Wahrheit.
  final String marker;
  const NumberedBlock(super.startLine, this.text, this.marker);
}

class CalloutBlock extends NoteBlock {
  final String text;
  const CalloutBlock(super.startLine, this.text);
}

class CodeBlock extends NoteBlock {
  final List<String> lines;
  const CodeBlock(super.startLine, this.lines);
}

/// Frage oben, Antwort verborgen — das eigentliche Lernzettel-Werkzeug.
class ToggleBlock extends NoteBlock {
  final String question;
  final List<NoteBlock> body;
  const ToggleBlock(super.startLine, this.question, this.body);
}

class DividerBlock extends NoteBlock {
  const DividerBlock(super.startLine);
}

class SpacerBlock extends NoteBlock {
  const SpacerBlock(super.startLine);
}

class ParagraphBlock extends NoteBlock {
  final String text;
  const ParagraphBlock(super.startLine, this.text);
}

const String kCodeFence = '```';
const String kToggleFence = '???';

final RegExp _numbered = RegExp(r'^(\d+)\.\s+(.*)$');

/// Zerlegt [content] in Blöcke.
///
/// Ein nicht geschlossener Zaun frisst den Rest des Dokuments als Inhalt — beim Tippen
/// verschwindet damit nie Text, man sieht den Block nur noch nicht fertig.
List<NoteBlock> parseBlocks(String content) {
  final lines = content.split('\n');
  return _parse(lines, 0);
}

/// [offset] verschiebt die startLine, damit ein rekursiv geparster Aufklapp-Rumpf weiterhin
/// auf die echten Zeilen des Gesamtdokuments zeigt.
List<NoteBlock> _parse(List<String> lines, int offset) {
  final blocks = <NoteBlock>[];

  for (var i = 0; i < lines.length; i++) {
    final line = lines[i];
    final trimmed = line.trimRight();
    final at = offset + i;

    // ── Mehrzeilige Zäune ──────────────────────────────────────────────────
    if (trimmed.trimLeft().startsWith(kCodeFence)) {
      final body = <String>[];
      var j = i + 1;
      while (j < lines.length && !lines[j].trimLeft().startsWith(kCodeFence)) {
        body.add(lines[j]);
        j++;
      }
      blocks.add(CodeBlock(at, body));
      i = j; // die schließende Zeile (oder das Dokumentende) überspringen
      continue;
    }

    if (trimmed.trimLeft().startsWith(kToggleFence)) {
      final question = trimmed.trimLeft().substring(kToggleFence.length).trim();
      final body = <String>[];
      var j = i + 1;
      while (j < lines.length && lines[j].trimLeft() != kToggleFence) {
        body.add(lines[j]);
        j++;
      }
      blocks.add(ToggleBlock(at, question, _parse(body, at + 1)));
      i = j;
      continue;
    }

    // ── Zeilenpräfixe ──────────────────────────────────────────────────────
    if (trimmed.startsWith('### ')) {
      blocks.add(HeadingBlock(at, trimmed.substring(4), 3));
    } else if (trimmed.startsWith('## ')) {
      blocks.add(HeadingBlock(at, trimmed.substring(3), 2));
    } else if (trimmed.startsWith('# ')) {
      blocks.add(HeadingBlock(at, trimmed.substring(2), 1));
    } else if (trimmed.startsWith('- [x] ') || trimmed.startsWith('- [X] ')) {
      blocks.add(TodoBlock(at, trimmed.substring(6), true));
    } else if (trimmed.startsWith('- [ ] ')) {
      blocks.add(TodoBlock(at, trimmed.substring(6), false));
    } else if (trimmed.startsWith('- [] ')) {
      // Altbestand: die Kurzform ohne Leerzeichen in der Klammer.
      blocks.add(TodoBlock(at, trimmed.substring(5), false));
    } else if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      blocks.add(BulletBlock(at, trimmed.substring(2)));
    } else if (_numbered.hasMatch(trimmed)) {
      final m = _numbered.firstMatch(trimmed)!;
      blocks.add(NumberedBlock(at, m.group(2)!, m.group(1)!));
    } else if (trimmed.startsWith('> ')) {
      blocks.add(CalloutBlock(at, trimmed.substring(2)));
    } else if (trimmed == '---' || trimmed == '───') {
      blocks.add(DividerBlock(at));
    } else if (trimmed.trim().isEmpty) {
      blocks.add(SpacerBlock(at));
    } else {
      blocks.add(ParagraphBlock(at, trimmed));
    }
  }

  return blocks;
}

/// `**fett**`, `*kursiv*`, `` `code` `` und `==markiert==`.
///
/// Bewusst **kein** `_kursiv_`: in einem Lernzettel steht ständig `f_n(x)` oder `x_1`, und
/// Unterstrich-Kursiv machte daraus stillschweigend Kursivsatz.
final RegExp _inline = RegExp(r'\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|==(.+?)==');

/// Zerlegt eine Zeile in [TextSpan]s.
///
/// Ein Durchlauf über eine kombinierte Regex, **nicht** verschachtelt: unpaarige Zeichen
/// bleiben wörtlich stehen. Verschachtelung bräuchte einen echten Parser und löste ein
/// Problem, das ein Lernzettel nicht hat.
List<InlineSpan> inlineSpans(
  String text,
  TextStyle base, {
  required Color codeBackground,
  required Color highlightBackground,
}) {
  final spans = <InlineSpan>[];
  var cursor = 0;

  for (final m in _inline.allMatches(text)) {
    if (m.start > cursor) {
      spans.add(TextSpan(text: text.substring(cursor, m.start), style: base));
    }

    if (m.group(1) != null) {
      spans.add(TextSpan(
          text: m.group(1), style: base.copyWith(fontWeight: FontWeight.bold)));
    } else if (m.group(2) != null) {
      spans.add(TextSpan(
          text: m.group(2), style: base.copyWith(fontStyle: FontStyle.italic)));
    } else if (m.group(3) != null) {
      spans.add(TextSpan(
          text: m.group(3),
          style: base.copyWith(
              fontFamily: 'monospace', backgroundColor: codeBackground)));
    } else if (m.group(4) != null) {
      spans.add(TextSpan(
          text: m.group(4), style: base.copyWith(backgroundColor: highlightBackground)));
    }

    cursor = m.end;
  }

  if (cursor < text.length) {
    spans.add(TextSpan(text: text.substring(cursor), style: base));
  }
  return spans.isEmpty ? [TextSpan(text: text, style: base)] : spans;
}

/// Kippt die Checkbox in [lineIndex] um; gibt null zurück, wenn dort keine steht.
///
/// Getrennt vom Widget, damit die Regel genau einmal existiert und ohne Oberfläche prüfbar ist.
String? toggleTodoLine(String content, int lineIndex) {
  final lines = content.split('\n');
  if (lineIndex < 0 || lineIndex >= lines.length) return null;

  final line = lines[lineIndex];
  final String flipped;
  if (line.startsWith('- [x] ') || line.startsWith('- [X] ')) {
    flipped = '- [ ] ${line.substring(6)}';
  } else if (line.startsWith('- [ ] ')) {
    flipped = '- [x] ${line.substring(6)}';
  } else if (line.startsWith('- [] ')) {
    flipped = '- [x] ${line.substring(5)}';
  } else {
    // Der Rohtext hat sich seit dem Rendern geändert — lieber nichts tun als die falsche
    // Zeile umschreiben.
    return null;
  }

  lines[lineIndex] = flipped;
  return lines.join('\n');
}
