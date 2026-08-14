import 'package:flutter/widgets.dart';

/// Der Lernzettel-Auszeichner.
///
/// Bewusst **kein** Blockmodell: der Inhalt bleibt ein einziger String in
/// `StudyNote.content`, und [parseBlocks] ist eine reine Funktion darauf. Das bearbeitbare
/// Modell, das der Editor daraus baut, steht in `note_blocks.dart` — diese Datei bleibt die
/// **Lese**richtung und damit die einzige Stelle, an der die Syntaxregeln stehen.
///
/// Zwei Konstrukte umfassen mehrere Zeilen (` ``` ` und `???`); alles andere entscheidet sich
/// am Zeilenanfang.
sealed class NoteBlock {
  /// Index der ersten Zeile dieses Blocks in `content.split('\n')`.
  final int startLine;

  /// Schachteltiefe, gelesen aus den führenden Leerzeichen — je zwei ergeben eine Stufe.
  ///
  /// Bestandsnotizen kennen keine Einrückung und landen darum durchweg auf 0. Vor der
  /// Einführung wurde ein eingerücktes `- foo` stillschweigend zum Absatz, weil die Präfixe
  /// gegen den links **nicht** getrimmten String geprüft wurden.
  final int indent;

  const NoteBlock(this.startLine, this.indent);
}

class HeadingBlock extends NoteBlock {
  final String text;
  final int level; // 1..3
  const HeadingBlock(super.startLine, super.indent, this.text, this.level);
}

class TodoBlock extends NoteBlock {
  final String text;
  final bool done;
  const TodoBlock(super.startLine, super.indent, this.text, this.done);
}

class BulletBlock extends NoteBlock {
  final String text;
  const BulletBlock(super.startLine, super.indent, this.text);
}

class NumberedBlock extends NoteBlock {
  final String text;

  /// Die getippte Zahl. Der Auszeichner nummeriert nicht um — das tut erst der Editor beim
  /// Serialisieren, und auch dort nur ab der getippten Startzahl.
  final String marker;
  const NumberedBlock(super.startLine, super.indent, this.text, this.marker);
}

class CalloutBlock extends NoteBlock {
  final String text;
  const CalloutBlock(super.startLine, super.indent, this.text);
}

class CodeBlock extends NoteBlock {
  final List<String> lines;
  const CodeBlock(super.startLine, super.indent, this.lines);
}

/// Frage oben, Antwort verborgen — das eigentliche Lernzettel-Werkzeug.
class ToggleBlock extends NoteBlock {
  final String question;
  final List<NoteBlock> body;
  const ToggleBlock(super.startLine, super.indent, this.question, this.body);
}

class DividerBlock extends NoteBlock {
  const DividerBlock(super.startLine, super.indent);
}

class SpacerBlock extends NoteBlock {
  const SpacerBlock(super.startLine, super.indent);
}

class ParagraphBlock extends NoteBlock {
  final String text;
  const ParagraphBlock(super.startLine, super.indent, this.text);
}

const String kCodeFence = '```';
const String kToggleFence = '???';

/// Wie viele Leerzeichen eine Schachtelstufe ausmachen.
const int kIndentWidth = 2;

/// Der Text nach der Zahl darf fehlen — siehe [_after].
final RegExp _numbered = RegExp(r'^(\d+)\.(?:\s+(.*))?$');

/// Der Text hinter einem Zeilenpräfix, oder null, wenn [body] gar nicht damit anfängt.
///
/// Nimmt das Präfix auch **ohne** das folgende Leerzeichen an: ein gerade angelegter, noch
/// leerer Listeneintrag wird als `-` gespeichert (eine Zeile darf nicht auf Leerzeichen enden),
/// und ohne diese Nachsicht stünde er nach dem Neuladen als wörtlicher Bindestrich da.
String? _after(String body, String prefix) {
  if (body.startsWith(prefix)) return body.substring(prefix.length);
  if (body == prefix.trimRight()) return '';
  return null;
}

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
    final trimmed = lines[i].trimRight();
    final body = trimmed.trimLeft();
    final indent = (trimmed.length - body.length) ~/ kIndentWidth;
    final at = offset + i;

    // ── Mehrzeilige Zäune ──────────────────────────────────────────────────
    if (body.startsWith(kCodeFence)) {
      // Der Rumpf wird um die Einrückung des Zauns erleichtert, damit Code auf jeder
      // Schachtelstufe wörtlich derselbe bleibt.
      final strip = indent * kIndentWidth;
      final content = <String>[];
      var j = i + 1;
      while (j < lines.length && !lines[j].trimLeft().startsWith(kCodeFence)) {
        content.add(_unindent(lines[j], strip));
        j++;
      }
      blocks.add(CodeBlock(at, indent, content));
      i = j; // die schließende Zeile (oder das Dokumentende) überspringen
      continue;
    }

    if (body.startsWith(kToggleFence)) {
      final question = body.substring(kToggleFence.length).trim();
      final inner = <String>[];
      // Mitzählen, statt am ersten `???` abzubrechen: sonst schlösse der Zaun eines
      // geschachtelten Aufklappblocks versehentlich seinen Elternblock mit.
      var level = 1;
      var j = i + 1;
      while (j < lines.length) {
        final t = lines[j].trim();
        if (t == kToggleFence) {
          level--;
          if (level == 0) break;
        } else if (t.startsWith(kToggleFence)) {
          level++;
        }
        inner.add(lines[j]);
        j++;
      }
      // Die Einrückung im Rumpf zählt relativ zum Zaun — der Zaun selbst ist die Schachtelung.
      blocks.add(ToggleBlock(at, indent, question, _parse(inner, at + 1)));
      i = j;
      continue;
    }

    // ── Zeilenpräfixe ──────────────────────────────────────────────────────
    // Ein Trenner sieht aus wie eine leere Aufzählung und muss deshalb vorher geprüft werden.
    if (body == '---' || body == '───') {
      blocks.add(DividerBlock(at, indent));
      continue;
    }
    if (body.isEmpty) {
      blocks.add(SpacerBlock(at, indent));
      continue;
    }

    String? rest;
    if ((rest = _after(body, '### ')) != null) {
      blocks.add(HeadingBlock(at, indent, rest!, 3));
    } else if ((rest = _after(body, '## ')) != null) {
      blocks.add(HeadingBlock(at, indent, rest!, 2));
    } else if ((rest = _after(body, '# ')) != null) {
      blocks.add(HeadingBlock(at, indent, rest!, 1));
    } else if ((rest = _after(body, '- [x] ')) != null ||
        (rest = _after(body, '- [X] ')) != null) {
      blocks.add(TodoBlock(at, indent, rest!, true));
    } else if ((rest = _after(body, '- [ ] ')) != null) {
      blocks.add(TodoBlock(at, indent, rest!, false));
    } else if ((rest = _after(body, '- [] ')) != null) {
      // Altbestand: die Kurzform ohne Leerzeichen in der Klammer.
      blocks.add(TodoBlock(at, indent, rest!, false));
    } else if ((rest = _after(body, '- ')) != null ||
        (rest = _after(body, '* ')) != null) {
      blocks.add(BulletBlock(at, indent, rest!));
    } else if (_numbered.hasMatch(body)) {
      final m = _numbered.firstMatch(body)!;
      blocks.add(NumberedBlock(at, indent, m.group(2) ?? '', m.group(1)!));
    } else if ((rest = _after(body, '> ')) != null) {
      blocks.add(CalloutBlock(at, indent, rest!));
    } else {
      blocks.add(ParagraphBlock(at, indent, body));
    }
  }

  return blocks;
}

/// Nimmt [count] führende Leerzeichen weg — aber nur so viele, wie wirklich dastehen.
String _unindent(String line, int count) {
  var n = 0;
  while (n < count && n < line.length && line.codeUnitAt(n) == 0x20) {
    n++;
  }
  return line.substring(n);
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

/// Wie [inlineSpans], aber die Auszeichnungszeichen bleiben stehen — nur gedimmt.
///
/// Für das bearbeitbare Feld. Dort darf **kein** Zeichen verschwinden: der Text im
/// `TextEditingController` und der gezeichnete Text müssen Zeichen für Zeichen dieselbe Länge
/// haben, sonst zeigen Einfügemarke und Auswahl an die falsche Stelle.
///
/// Notion blendet die Sternchen aus. Das ginge hier nur über `fontSize: 0`, und dann bliebe
/// jedes versteckte Zeichen trotzdem eine eigene Textposition: die Pfeiltaste bräuchte vier
/// Anschläge ohne sichtbare Bewegung über `**fett**`, und zwischen zwei Nullzeichen schrumpfte
/// die Einfügemarke auf null Höhe. Das sauber zu lösen hieße, ein eigenes Cursor-Modell zu
/// bauen — hier ist die eine Stelle, an der dieser Editor bewusst nicht Notion ist.
List<InlineSpan> editorSpans(
  String text,
  TextStyle base, {
  required Color codeBackground,
  required Color highlightBackground,
  required Color markerColor,
}) {
  final spans = <InlineSpan>[];
  final markerStyle = base.copyWith(color: markerColor);
  var cursor = 0;

  for (final m in _inline.allMatches(text)) {
    if (m.start > cursor) {
      spans.add(TextSpan(text: text.substring(cursor, m.start), style: base));
    }

    // Wie viele Zeichen die Auszeichnung auf jeder Seite belegt.
    final int marker;
    final TextStyle style;
    final String content;
    if (m.group(1) != null) {
      (content, style, marker) = (m.group(1)!, base.copyWith(fontWeight: FontWeight.bold), 2);
    } else if (m.group(2) != null) {
      (content, style, marker) =
          (m.group(2)!, base.copyWith(fontStyle: FontStyle.italic), 1);
    } else if (m.group(3) != null) {
      (content, style, marker) = (
        m.group(3)!,
        base.copyWith(fontFamily: 'monospace', backgroundColor: codeBackground),
        1,
      );
    } else {
      (content, style, marker) =
          (m.group(4)!, base.copyWith(backgroundColor: highlightBackground), 2);
    }

    spans.add(TextSpan(text: text.substring(m.start, m.start + marker), style: markerStyle));
    spans.add(TextSpan(text: content, style: style));
    spans.add(TextSpan(text: text.substring(m.end - marker, m.end), style: markerStyle));
    cursor = m.end;
  }

  if (cursor < text.length) {
    spans.add(TextSpan(text: text.substring(cursor), style: base));
  }
  return spans;
}
