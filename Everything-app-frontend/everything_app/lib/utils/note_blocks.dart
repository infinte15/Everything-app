/// Das bearbeitbare Blockmodell des Notizeditors.
///
/// `note_markup.dart` ist die Leserichtung (String → verschachtelte [NoteBlock]s), diese Datei
/// die Bearbeitungsrichtung: eine **flache** Liste aus [EditorBlock]s mit stabilen IDs, an der
/// jeder Block seinen eigenen `TextEditingController` und `FocusNode` hängen kann.
///
/// Zwei Regeln tragen alles andere:
///
/// 1. **Kein Hin und Zurück.** [blocksFromMarkup] läuft genau einmal beim Laden. Danach wird
///    die Liste nur noch an Ort und Stelle verändert; [markupFromBlocks] ist reine
///    Schreibrichtung. Ein Umweg über den String vergäbe neue IDs und würfe damit jeden
///    Controller, FocusNode und `EditableTextState` im Widgetbaum weg.
///
/// 2. **Zäune sind keine Präfixe.** `- `, `# `, `1. ` stehen am Zeilenanfang, ihre Tiefe wird
///    zu führenden Leerzeichen. ` ``` ` und `??? … ???` umschließen dagegen einen Rumpf ohne
///    Präfix. Daraus folgt: ein Codeblock ist **ein** Block, dessen Text `\n` enthält, und ein
///    Aufklappblock ist ein Elternblock plus der darauf folgende zusammenhängende Lauf tieferer
///    Blöcke.
///
/// Alles hier ist rein und ohne Widgetbaum prüfbar.
library;

import 'note_markup.dart';

enum BlockKind {
  paragraph,
  heading,
  todo,
  bullet,
  numbered,
  callout,
  code,
  toggle,
  divider,
}

var _nextId = 0;

class EditorBlock {
  /// Stabil über die gesamte Sitzung — Schlüssel für Controller, FocusNode und Widget-Key.
  final String id;
  final BlockKind kind;

  /// Schachteltiefe. Kinder eines Aufklappblocks sind genau die darauf folgenden Blöcke mit
  /// größerer Tiefe.
  final int depth;

  /// Bei [BlockKind.code] mehrzeilig, sonst immer einzeilig. Bei [BlockKind.divider] leer.
  final String text;

  /// Nur für [BlockKind.todo].
  final bool checked;

  /// Nur für [BlockKind.heading], 1..3.
  final int level;

  /// Nur für [BlockKind.numbered]: die getippte Startzahl. Die *angezeigte* Zahl berechnet
  /// [displayNumber] daraus — eine mit `10.` begonnene Liste läuft bei 10 weiter, statt beim
  /// bloßen Öffnen auf 1 zurückzufallen.
  final int startNumber;

  /// Nur für [BlockKind.toggle]. Bewusst nicht Teil des Markups: beim nächsten Öffnen sollen
  /// die Antworten wieder verborgen sein, und es zu speichern machte aus dem Lesen eines
  /// Lernzettels einen Schreibvorgang.
  final bool collapsed;

  const EditorBlock({
    required this.id,
    required this.kind,
    this.depth = 0,
    this.text = '',
    this.checked = false,
    this.level = 1,
    this.startNumber = 1,
    this.collapsed = true,
  });

  /// Ein neuer Block mit frischer ID.
  factory EditorBlock.fresh({
    BlockKind kind = BlockKind.paragraph,
    int depth = 0,
    String text = '',
    bool checked = false,
    int level = 1,
    int startNumber = 1,
  }) =>
      EditorBlock(
        id: 'b${_nextId++}',
        kind: kind,
        depth: depth,
        text: text,
        checked: checked,
        level: level,
        startNumber: startNumber,
      );

  EditorBlock copyWith({
    BlockKind? kind,
    int? depth,
    String? text,
    bool? checked,
    int? level,
    int? startNumber,
    bool? collapsed,
  }) =>
      EditorBlock(
        id: id,
        kind: kind ?? this.kind,
        depth: depth ?? this.depth,
        text: text ?? this.text,
        checked: checked ?? this.checked,
        level: level ?? this.level,
        startNumber: startNumber ?? this.startNumber,
        collapsed: collapsed ?? this.collapsed,
      );

  /// Trägt dieser Block Text, den man bearbeiten kann? Nur der Trenner tut es nicht.
  bool get isTextual => kind != BlockKind.divider;

  /// Ein Listeneintrag, den Enter fortsetzt und ein leeres Enter wieder verlässt.
  bool get isListItem =>
      kind == BlockKind.todo ||
      kind == BlockKind.bullet ||
      kind == BlockKind.numbered;

  @override
  String toString() => '$kind(d$depth${text.isEmpty ? '' : ' "$text"'})';
}

// ── Lesen ─────────────────────────────────────────────────────────────────────

/// Baut die Blockliste aus dem gespeicherten Markup. **Nur beim Laden aufrufen.**
///
/// Setzt auf [parseBlocks] auf und flacht dessen Aufklapp-Rekursion zu [EditorBlock.depth] ab.
/// Ein leerer Inhalt ergibt einen einzelnen leeren Absatz — sonst gäbe es nichts, worin die
/// Einfügemarke stehen könnte.
///
/// Die Altform `- [] ` (ohne Leerzeichen in der Klammer) wird dabei zu `- [ ] ` vereinheitlicht.
/// Das erste Speichern schreibt solche Zeilen also um; das ist gewollt und keine Fehlfunktion.
List<EditorBlock> blocksFromMarkup(String content) {
  final out = <EditorBlock>[];

  void walk(List<NoteBlock> src, int base) {
    for (final b in src) {
      // Der Zaun trägt die Schachtelung, die Leerzeichen im Rumpf zählen relativ dazu — darum
      // addiert und nicht das Maximum genommen. Ein von Hand geschriebener Rumpf ohne
      // Einrückung landet damit genau eine Stufe unter seinem Aufklappblock.
      final d = base + b.indent;
      switch (b) {
        case HeadingBlock():
          out.add(EditorBlock.fresh(
              kind: BlockKind.heading, depth: d, text: b.text, level: b.level));
        case TodoBlock():
          out.add(EditorBlock.fresh(
              kind: BlockKind.todo, depth: d, text: b.text, checked: b.done));
        case BulletBlock():
          out.add(EditorBlock.fresh(kind: BlockKind.bullet, depth: d, text: b.text));
        case NumberedBlock():
          out.add(EditorBlock.fresh(
            kind: BlockKind.numbered,
            depth: d,
            text: b.text,
            startNumber: int.tryParse(b.marker) ?? 1,
          ));
        case CalloutBlock():
          out.add(EditorBlock.fresh(kind: BlockKind.callout, depth: d, text: b.text));
        case CodeBlock():
          out.add(EditorBlock.fresh(
              kind: BlockKind.code, depth: d, text: b.lines.join('\n')));
        case DividerBlock():
          out.add(EditorBlock.fresh(kind: BlockKind.divider, depth: d));
        case SpacerBlock():
          out.add(EditorBlock.fresh(depth: d));
        case ParagraphBlock():
          out.add(EditorBlock.fresh(depth: d, text: b.text));
        case ToggleBlock():
          out.add(EditorBlock.fresh(
              kind: BlockKind.toggle, depth: d, text: b.question));
          walk(b.body, d + 1);
      }
    }
  }

  walk(parseBlocks(content), 0);
  if (out.isEmpty) out.add(EditorBlock.fresh());
  normalizeDepths(out);
  return out;
}

/// Begrenzt jede Tiefe auf höchstens eine Stufe unter dem Vorgänger — an Ort und Stelle.
///
/// Von Hand geschriebenes Markup darf beliebig weit einrücken, das Modell darf es nicht: sonst
/// entstünden Kinder ohne Eltern, und jede Tiefenrechnung müsste den Fall danach nochmal
/// abfangen. Läuft auch nach jedem Umbau der Liste, weil das Entfernen eines Blocks seine
/// Kinder eine Stufe zu tief zurücklassen kann.
void normalizeDepths(List<EditorBlock> blocks) {
  for (var i = 0; i < blocks.length; i++) {
    final limit = maxDepthAt(blocks, i);
    if (blocks[i].depth > limit) blocks[i] = blocks[i].copyWith(depth: limit);
  }
}

// ── Schreiben ─────────────────────────────────────────────────────────────────

/// Serialisiert die Blockliste zurück ins Markup.
///
/// Aufklappblöcke bekommen ihren schließenden Zaun, sobald ihr Lauf endet — deshalb der Stapel
/// offener Tiefen statt einer Rekursion.
///
/// Geschrieben wird nur die Einrückung, die der Zaun **nicht** schon ausdrückt: ein direktes
/// Kind eines Aufklappblocks steht bündig in dessen Rumpf. Damit bleibt eine Bestandsnotiz
/// nach dem bloßen Öffnen und Speichern Zeichen für Zeichen dieselbe.
String markupFromBlocks(List<EditorBlock> blocks) {
  final out = <String>[];
  final openToggles = <int>[]; // Tiefen der noch offenen Aufklappblöcke

  void closeDownTo(int depth) {
    while (openToggles.isNotEmpty && depth <= openToggles.last) {
      final own = openToggles.removeLast();
      out.add('${_pad(own - openToggles.length)}$kToggleFence');
    }
  }

  for (var i = 0; i < blocks.length; i++) {
    final b = blocks[i];
    closeDownTo(b.depth);
    final pad = _pad(b.depth - openToggles.length);

    switch (b.kind) {
      case BlockKind.heading:
        out.add(_line(pad, '#' * b.level.clamp(1, 3), b.text));
      case BlockKind.todo:
        out.add(_line(pad, '- [${b.checked ? 'x' : ' '}]', b.text));
      case BlockKind.bullet:
        out.add(_line(pad, '-', b.text));
      case BlockKind.numbered:
        out.add(_line(pad, '${displayNumber(blocks, i)}.', b.text));
      case BlockKind.callout:
        out.add(_line(pad, '>', b.text));
      case BlockKind.divider:
        out.add('$pad---');
      case BlockKind.code:
        out.add('$pad$kCodeFence');
        out.addAll(b.text.split('\n').map((l) => l.isEmpty ? '' : '$pad$l'));
        out.add('$pad$kCodeFence');
      case BlockKind.toggle:
        out.add(_line(pad, kToggleFence, b.text));
        openToggles.add(b.depth);
      case BlockKind.paragraph:
        // Eine leere Zeile darf keine Leerzeichen tragen, sonst wächst der Text bei jedem
        // Speichern um unsichtbare Zeichen.
        out.add(b.text.isEmpty ? '' : '$pad${b.text}');
    }
  }

  closeDownTo(-1);
  return out.join('\n');
}

String _pad(int depth) => ' ' * (depth * kIndentWidth);

/// Präfix und Text — ohne das trennende Leerzeichen, wenn kein Text folgt.
///
/// Eine Zeile darf nicht auf Leerzeichen enden: der Auszeichner trimmt sie beim Lesen weg, und
/// aus dem gerade angelegten, noch leeren `- ` würde beim nächsten Öffnen ein wörtlicher
/// Bindestrich. Die Gegenseite dazu ist `_after` in note_markup.dart.
String _line(String pad, String prefix, String text) =>
    text.isEmpty ? '$pad$prefix' : '$pad$prefix $text';

/// Die Zahl, die vor einem nummerierten Eintrag steht — für Anzeige **und** Serialisierung.
///
/// Ein Lauf beginnt bei der getippten Startzahl seines ersten Eintrags und zählt von dort
/// hoch. Tiefer geschachtelte Blöcke dazwischen unterbrechen ihn nicht; alles andere auf
/// gleicher oder geringerer Tiefe schon.
int displayNumber(List<EditorBlock> blocks, int index) {
  final self = blocks[index];
  if (self.kind != BlockKind.numbered) return 1;

  var count = 0;
  var start = self.startNumber;
  for (var i = index; i >= 0; i--) {
    final other = blocks[i];
    if (other.depth > self.depth) continue;
    if (other.depth < self.depth || other.kind != BlockKind.numbered) break;
    count++;
    start = other.startNumber;
  }
  return start + count - 1;
}

// ── Übergänge ─────────────────────────────────────────────────────────────────

final RegExp _shortcutNumber = RegExp(r'^(\d+)\.$');

/// Deutet das, was links von der Einfügemarke steht, als Markdown-Kürzel.
///
/// Wird beim Leerzeichen ausgewertet: aus `# ` wird eine Überschrift, aus `- ` eine
/// Aufzählung. Gibt null zurück, wenn dort kein Kürzel steht — dann bleibt das Leerzeichen
/// einfach ein Leerzeichen.
({BlockKind kind, int level, int startNumber})? markdownShortcut(String prefix) {
  switch (prefix) {
    case '#':
      return (kind: BlockKind.heading, level: 1, startNumber: 1);
    case '##':
      return (kind: BlockKind.heading, level: 2, startNumber: 1);
    case '###':
      return (kind: BlockKind.heading, level: 3, startNumber: 1);
    case '-':
    case '*':
    case '+':
      return (kind: BlockKind.bullet, level: 1, startNumber: 1);
    case '[]':
    case '[ ]':
    case '[x]':
    case '[X]':
      return (kind: BlockKind.todo, level: 1, startNumber: 1);
    case '>':
      return (kind: BlockKind.callout, level: 1, startNumber: 1);
    case kToggleFence:
      return (kind: BlockKind.toggle, level: 1, startNumber: 1);
    case kCodeFence:
      return (kind: BlockKind.code, level: 1, startNumber: 1);
    case '---':
    case '***':
      return (kind: BlockKind.divider, level: 1, startNumber: 1);
  }

  final m = _shortcutNumber.firstMatch(prefix);
  if (m != null) {
    return (
      kind: BlockKind.numbered,
      level: 1,
      startNumber: int.tryParse(m.group(1)!) ?? 1,
    );
  }
  return null;
}

/// Welche Art der Block bekommt, den Enter unter [b] anlegt.
///
/// Listen setzen sich fort, alles andere fällt auf den Absatz zurück — sonst legte Enter unter
/// einer Überschrift eine zweite Überschrift an. Enter im Kopf eines Aufklappblocks legt
/// dessen erstes Kind an.
EditorBlock continuationOf(EditorBlock b, {String text = ''}) => switch (b.kind) {
      BlockKind.todo => EditorBlock.fresh(
          kind: BlockKind.todo, depth: b.depth, text: text),
      BlockKind.bullet => EditorBlock.fresh(
          kind: BlockKind.bullet, depth: b.depth, text: text),
      BlockKind.numbered => EditorBlock.fresh(
          kind: BlockKind.numbered, depth: b.depth, text: text),
      BlockKind.callout => EditorBlock.fresh(
          kind: BlockKind.callout, depth: b.depth, text: text),
      BlockKind.toggle => EditorBlock.fresh(depth: b.depth + 1, text: text),
      _ => EditorBlock.fresh(depth: b.depth, text: text),
    };

/// Ein zweiter Block wie [b], aber mit eigener ID.
///
/// Für das Teilen mitten im Text (beide Hälften behalten die Blockart) und das Duplizieren
/// aus dem Blockmenü.
EditorBlock cloneOf(EditorBlock b, {String? text}) => EditorBlock.fresh(
      kind: b.kind,
      depth: b.depth,
      text: text ?? b.text,
      checked: b.checked,
      level: b.level,
      startNumber: b.startNumber,
    );

/// Macht aus [b] einen schlichten Absatz — was Backspace am Zeilenanfang und Enter auf einem
/// leeren Listeneintrag tun.
EditorBlock toParagraph(EditorBlock b) => b.copyWith(
      kind: BlockKind.paragraph,
      checked: false,
      level: 1,
      startNumber: 1,
    );

/// Die größte Tiefe, die der Block an [index] annehmen darf.
///
/// Höchstens eine Stufe tiefer als sein Vorgänger — sonst entstünden Kinder ohne Eltern.
int maxDepthAt(List<EditorBlock> blocks, int index) {
  if (index <= 0) return 0;
  return blocks[index - 1].depth + 1;
}

/// Der zusammenhängende Lauf, den der Block an [index] mitnimmt: er selbst plus alles
/// unmittelbar darauf Folgende, das tiefer steht. Für Aufklappblöcke sind das ihre Kinder.
///
/// Gibt die Länge zurück, nie 0.
int runLengthAt(List<EditorBlock> blocks, int index) {
  final depth = blocks[index].depth;
  var end = index + 1;
  while (end < blocks.length && blocks[end].depth > depth) {
    end++;
  }
  return end - index;
}

/// Verschiebt [count] Blöcke ab [from] so, dass sie vor dem Block landen, der jetzt an [to]
/// steht. Gibt eine neue Liste zurück; ein Ziel innerhalb des bewegten Laufs bleibt wirkungslos.
List<EditorBlock> moveRun(List<EditorBlock> blocks, int from, int count, int to) {
  if (to > from && to < from + count) return List.of(blocks);

  final next = List.of(blocks);
  final run = next.sublist(from, from + count);
  next.removeRange(from, from + count);
  final at = (to > from ? to - count : to).clamp(0, next.length);

  // Der Lauf behält seine Tiefe und seine innere Staffelung; nur wenn am Ziel gar nicht so
  // tief geschachtelt werden darf, rutscht seine Wurzel nach oben.
  final root = run.first.depth;
  final allowed = at == 0 ? 0 : next[at - 1].depth + 1;
  final shift = (root > allowed ? allowed : root) - root;
  next.insertAll(
    at,
    shift == 0
        ? run
        : run.map((b) => b.copyWith(depth: (b.depth + shift).clamp(0, 1 << 20))),
  );
  return next;
}
