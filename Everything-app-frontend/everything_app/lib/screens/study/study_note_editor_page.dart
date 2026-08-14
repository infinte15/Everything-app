import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../models/study_note.dart';
import '../../providers/study_provider.dart';
import '../../utils/note_blocks.dart';
import '../../widgets/pointer_aware_draggable.dart';
import 'widgets/note_editor/note_block_menu.dart';
import 'widgets/note_editor/note_block_row.dart';
import 'widgets/note_editor/note_format_toolbar.dart';
import 'widgets/note_editor/note_inline_controller.dart';
import 'widgets/note_editor/note_slash_menu.dart';

/// Der Notizeditor — eine Ansicht, kein Umschalten mehr.
///
/// Früher stand hier ein `SegmentedButton` zwischen VORSCHAU und BEARBEITEN: man tippte
/// `# Überschrift` und sah `# Überschrift`, das Ergebnis erst nach einem Moduswechsel. Jetzt
/// ist jeder Block in seiner Enddarstellung bearbeitbar.
///
/// **Aufbau.** Der Inhalt der Notiz bleibt ein einziger Markup-String; [blocksFromMarkup] baut
/// daraus **einmal beim Laden** eine flache [EditorBlock]-Liste, und [markupFromBlocks] macht
/// beim Speichern wieder einen String daraus. Der Umweg zurück über den String wird nie
/// gegangen — er vergäbe neue IDs und würfe damit jeden Controller und FocusNode weg.
///
/// **Wo die Wahrheit steht.** Für den Text ist es der jeweilige `TextEditingController`,
/// solange er lebt; [_syncText] holt ihn vor jedem Umbau und vor jedem Speichern ins Modell.
/// Sonst bräuchte jeder Tastendruck ein `setState` über das ganze Dokument.
class StudyNoteEditorPage extends StatefulWidget {
  final int noteId;
  const StudyNoteEditorPage({super.key, required this.noteId});

  @override
  State<StudyNoteEditorPage> createState() => _StudyNoteEditorPageState();
}

enum _SaveStatus { clean, pending, saving, saved, failed }

class _StudyNoteEditorPageState extends State<StudyNoteEditorPage> {
  /// Beim Aufbau gegriffen, damit Speichern auch ohne `context` geht — beim Verlassen der
  /// Seite ist der Elementbaum schon weg, die Notiz muss aber noch wegkommen.
  late final StudyProvider _provider;

  StudyNote? _note;
  late TextEditingController _titleCtrl;

  final List<EditorBlock> _blocks = [];
  final Map<String, TextEditingController> _ctrl = {};
  final Map<String, FocusNode> _focus = {};
  final Map<String, GlobalKey> _keys = {};
  final Map<String, LayerLink> _links = {};

  /// Sperrt die Controller-Zuhörer, solange der Editor selbst schreibt.
  ///
  /// `controller.value = …` benachrichtigt **synchron**; ohne diese Sperre liefe der Zuhörer
  /// mitten im eigenen Umbau erneut.
  bool _programmatic = false;

  String? _activeId;

  /// Die Spalte, die die Pfeiltasten über Blockgrenzen hinweg halten sollen — global, in
  /// Pixeln. Ohne sie fiele die Einfügemarke bei jedem Sprung auf Spalte 0.
  double? _desiredX;

  // ── Slash-Menü ──────────────────────────────────────────────────────────────
  // Der Zustand liegt hier und nicht im Menü: das Menü bekommt keinen Fokus, damit der Block
  // die Einfügemarke behält und man zum Filtern weitertippen kann. Die Pfeiltasten laufen
  // deshalb durch _onBlockKey.
  final _slashPortal = OverlayPortalController();
  final _slashScroll = ScrollController();
  String? _slashBlockId;
  int _slashStart = -1;
  String _slashQuery = '';
  int _slashIndex = 0;
  List<SlashCommand> _slashCommands = noteSlashCommands;

  /// Mit Escape weggeschicktes `/` — sonst spränge das Menü beim nächsten Tastendruck wieder auf.
  String? _slashDismissed;

  bool get _slashOpen => _slashBlockId != null;

  // ── Formatierleiste ─────────────────────────────────────────────────────────
  // Der Anker liegt in einem ValueNotifier, damit das Verschieben der Auswahl nur die Leiste
  // neu setzt und nicht das ganze Dokument.
  final _formatPortal = OverlayPortalController();
  final _formatAnchor = ValueNotifier<Offset>(Offset.zero);
  String? _formatBlockId;

  // ── Ziehen ──────────────────────────────────────────────────────────────────
  // Bewusst keine ValueNotifier und kein setState: welche Lücke gerade angesteuert wird, weiß
  // ihr eigener DragTarget über `candidateData`. Ein Neuaufbau des Dokuments je Mausbewegung
  // wäre bei ein paar hundert Blöcken nicht mehr flüssig.
  final _scroll = ScrollController();
  Timer? _autoScroll;
  double _pointerY = 0;

  Timer? _saveTimer;
  _SaveStatus _status = _SaveStatus.clean;
  String _savedTitle = '';
  String _savedContent = '';

  static const _saveDelay = Duration(milliseconds: 800);

  @override
  void initState() {
    super.initState();
    _provider = context.read<StudyProvider>();

    // Eine unbekannte ID oeffnete frueher stillschweigend ein leeres Formular, dessen
    // "Speichern" ins Leere lief. Jetzt bleibt _note null und der Screen sagt es.
    final matches = _provider.notes.where((n) => n.id == widget.noteId);
    _note = matches.isEmpty ? null : matches.first;

    _savedTitle = _note?.title ?? '';
    _savedContent = _note?.content ?? '';
    _titleCtrl = TextEditingController(text: _savedTitle);
    _titleCtrl.addListener(() {
      if (!_programmatic) _scheduleSave();
    });

    for (final b in blocksFromMarkup(_savedContent)) {
      _ensure(b);
      _blocks.add(b);
    }
  }

  @override
  void dispose() {
    // Letzte Rettung: eine noch offene Entprellung darf den Text nicht mitnehmen.
    _flush();
    _autoScroll?.cancel();
    _scroll.dispose();
    _slashScroll.dispose();
    _formatAnchor.dispose();
    _titleCtrl.dispose();
    for (final c in _ctrl.values) {
      c.dispose();
    }
    for (final f in _focus.values) {
      f.dispose();
    }
    super.dispose();
  }

  // ── Buchführung über Controller und Fokus ───────────────────────────────────

  void _ensure(EditorBlock b) {
    _ctrl.putIfAbsent(b.id, () {
      final c = NoteInlineController(
        text: b.text,
        // Als Abfrage und nicht als fester Wert: die Blockart kann sich ändern, ohne dass der
        // Controller ausgetauscht wird.
        formatted: () {
          final at = _indexOf(b.id);
          return at < 0 || _blocks[at].kind != BlockKind.code;
        },
      );
      c.addListener(() => _onBlockText(b.id));
      return c;
    });
    _focus.putIfAbsent(b.id, () {
      final f = FocusNode(debugLabel: 'note-${b.id}');
      f.addListener(() => _onFocusChange(b.id, f));
      return f;
    });
    _keys.putIfAbsent(b.id, () => GlobalKey());
    _links.putIfAbsent(b.id, () => LayerLink());
  }

  /// Gibt Controller und FocusNode eines entfernten Blocks frei — aber erst nach dem Bild.
  ///
  /// Sofort freizugeben hieße, dass das noch eingehängte `Focus`-Widget beim Aushängen ein
  /// `detach()` auf einem bereits entsorgten Knoten auslöst.
  void _retire(String id) {
    if (_slashBlockId == id) _closeSlash();
    final c = _ctrl.remove(id);
    final f = _focus.remove(id);
    _keys.remove(id);
    _links.remove(id);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      c?.dispose();
      f?.dispose();
    });
  }

  int _indexOf(String id) => _blocks.indexWhere((b) => b.id == id);

  void _syncText() {
    for (var i = 0; i < _blocks.length; i++) {
      final c = _ctrl[_blocks[i].id];
      if (c != null && c.text != _blocks[i].text) {
        _blocks[i] = _blocks[i].copyWith(text: c.text);
      }
    }
  }

  /// Schreibt Text und Einfügemarke eines Blocks, ohne die Zuhörer loszutreten.
  void _write(String id, String text, int caret) => _writeValue(
        id,
        TextEditingValue(
          text: text,
          selection: TextSelection.collapsed(offset: caret.clamp(0, text.length)),
        ),
      );

  void _writeValue(String id, TextEditingValue value) {
    _programmatic = true;
    // Nie `controller.text` — dessen Setter wirft die Auswahl auf -1 zurueck, und die
    // Einfuegemarke landet danach am Zeilenende.
    _ctrl[id]!.value = value;
    _programmatic = false;
  }

  void _onFocusChange(String id, FocusNode node) {
    if (node.hasFocus) {
      if (_activeId != id) {
        // Menue und Leiste gehoeren zu genau einem Block; wandert die Einfuegemarke, sind sie
        // erledigt.
        if (_slashBlockId != id) _closeSlash();
        if (_formatBlockId != id) _hideFormatToolbar();
        setState(() => _activeId = id);
      }
      return;
    }
    if (_formatBlockId == id) _hideFormatToolbar();
    if (_activeId != id) return;
    // Der Fokus kann gerade in den Nachbarblock wandern; erst wenn das durch ist, steht fest,
    // dass wirklich niemand mehr schreibt.
    scheduleMicrotask(() {
      if (!mounted || _activeId != id) return;
      if (_focus.values.any((n) => n.hasFocus)) return;
      setState(() => _activeId = null);
      _saveNow();
    });
  }

  void _onBlockText(String id) {
    if (_programmatic) return;
    final i = _indexOf(id);
    if (i < 0) return;
    // Sicherheitsnetz und zugleich das Verhalten beim Einfuegen mehrzeiligen Texts: ein
    // Zeilenumbruch, der es bis in einen Nicht-Code-Block schafft, wird zu eigenen Bloecken.
    if (_blocks[i].kind != BlockKind.code && _ctrl[id]!.text.contains('\n')) {
      _explode(i);
      return;
    }
    _updateSlash(id);
    _updateFormatToolbar(id);
    // Der Controller meldet sich auch, wenn sich nur die Auswahl bewegt hat. Das Modell
    // traegt den Stand des letzten Abgleichs, also verraet ein Vergleich, ob wirklich jemand
    // getippt hat — sonst meldete blosses Herumklicken „Gespeichert".
    if (_ctrl[id]!.text != _blocks[i].text) _scheduleSave();
  }

  // ── Formatierleiste ─────────────────────────────────────────────────────────

  /// Zeigt die Leiste, sobald in einem Block etwas markiert ist, und schiebt sie mit.
  void _updateFormatToolbar(String id) {
    final ctrl = _ctrl[id];
    final selection = ctrl?.selection;
    final visible = ctrl != null &&
        selection != null &&
        selection.isValid &&
        !selection.isCollapsed &&
        (_focus[id]?.hasFocus ?? false);

    if (!visible) {
      if (_formatBlockId == id) _hideFormatToolbar();
      return;
    }

    final editable = _editableOf(id);
    if (editable == null) return;

    // Das Auswahlrechteck kommt schon in globalen Koordinaten; der Anker misst von der
    // Textkante des Felds aus, an der der LayerLink hängt.
    final rect = TextSelectionToolbarAnchors.getSelectionRect(
      editable,
      editable.preferredLineHeight,
      editable.preferredLineHeight,
      editable.getEndpointsForSelection(selection),
    );
    final origin = editable.localToGlobal(Offset.zero);
    _formatAnchor.value = Offset(rect.left - origin.dx, rect.top - origin.dy - 6);

    if (_formatBlockId != id) {
      // Ein anderer Block heisst ein anderer LayerLink — dafuer muss das Overlay neu gebaut
      // werden, die blosse Ankerverschiebung reicht nicht.
      setState(() => _formatBlockId = id);
    }
    if (!_formatPortal.isShowing) _formatPortal.show();
  }

  void _hideFormatToolbar() {
    if (_formatBlockId == null) return;
    _formatBlockId = null;
    if (_formatPortal.isShowing) _formatPortal.hide();
    if (mounted) setState(() {});
  }

  /// Legt eine Auszeichnung um die Auswahl — oder nimmt sie wieder ab.
  void _applyInline(String id, InlineFormat format) {
    final ctrl = _ctrl[id];
    final selection = ctrl?.selection;
    if (ctrl == null || selection == null || !selection.isValid || selection.isCollapsed) {
      return;
    }

    final marker = format.marker;
    final selected = ctrl.text.substring(selection.start, selection.end);
    // Steht die Auszeichnung schon da, ist der Knopf ein Schalter.
    final wrapped = selected.length >= marker.length * 2 &&
        selected.startsWith(marker) &&
        selected.endsWith(marker);
    final replacement = wrapped
        ? selected.substring(marker.length, selected.length - marker.length)
        : '$marker$selected$marker';

    final text = ctrl.text.replaceRange(selection.start, selection.end, replacement);
    _writeValue(
      id,
      TextEditingValue(
        text: text,
        // Der Inhalt bleibt markiert, damit man weiter formatieren kann.
        selection: TextSelection(
          baseOffset: selection.start,
          extentOffset: selection.start + replacement.length,
        ),
      ),
    );
    _updateFormatToolbar(id);
    _scheduleSave();
  }

  // ── Slash-Menü ──────────────────────────────────────────────────────────────

  /// Liest allein aus Text und Einfügemarke ab, ob das Menü offen sein muss.
  ///
  /// Zustandslos bis auf [_slashDismissed]: es gibt kein „wurde geöffnet", das aus dem Tritt
  /// geraten könnte. Ein `/` zählt nur am Blockanfang oder nach einem Leerzeichen — sonst
  /// spränge das Menü mitten in jedem Bruch und jeder Netzadresse auf.
  void _updateSlash(String id) {
    final i = _indexOf(id);
    if (i < 0 || _blocks[i].kind == BlockKind.code) {
      _closeSlash();
      return;
    }

    final ctrl = _ctrl[id]!;
    final sel = ctrl.selection;
    if (!sel.isValid || !sel.isCollapsed) {
      _closeSlash();
      return;
    }

    final before = ctrl.text.substring(0, sel.baseOffset);
    final start = before.lastIndexOf('/');
    if (start < 0 || (start > 0 && before[start - 1] != ' ')) {
      _closeSlash();
      return;
    }

    final query = before.substring(start + 1);
    // Ein Leerzeichen beendet die Abfrage — danach ist es wieder gewoehnlicher Text.
    if (query.contains(' ')) {
      _closeSlash();
      return;
    }
    if (_slashDismissed == '$id:$start') return;

    final matches = filterSlashCommands(query);
    setState(() {
      if (query != _slashQuery || _slashBlockId != id) _slashIndex = 0;
      _slashBlockId = id;
      _slashStart = start;
      _slashQuery = query;
      _slashCommands = matches;
      if (_slashIndex >= matches.length) _slashIndex = 0;
    });
    if (!_slashPortal.isShowing) _slashPortal.show();
    _revealSlashItem();
  }

  void _closeSlash() {
    if (_slashBlockId == null) return;
    _slashBlockId = null;
    _slashQuery = '';
    _slashIndex = 0;
    if (_slashPortal.isShowing) _slashPortal.hide();
    if (mounted) setState(() {});
  }

  void _dismissSlash() {
    _slashDismissed = '$_slashBlockId:$_slashStart';
    _closeSlash();
  }

  void _moveSlash(int delta) {
    if (_slashCommands.isEmpty) return;
    setState(() {
      _slashIndex = (_slashIndex + delta) % _slashCommands.length;
      if (_slashIndex < 0) _slashIndex += _slashCommands.length;
    });
    _revealSlashItem();
  }

  /// Hält den hervorgehobenen Eintrag im Sichtfenster. Feste Zeilenhöhe, darum reicht Rechnen.
  void _revealSlashItem() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_slashScroll.hasClients) return;
      const height = NoteSlashMenu.itemHeight;
      final top = _slashIndex * height;
      final view = _slashScroll.position.viewportDimension;
      if (top < _slashScroll.offset) {
        _slashScroll.jumpTo(top);
      } else if (top + height > _slashScroll.offset + view) {
        _slashScroll.jumpTo(top + height - view);
      }
    });
  }

  void _commitSlash(SlashCommand command) {
    final id = _slashBlockId;
    if (id == null) return;
    final i = _indexOf(id);
    if (i < 0) {
      _closeSlash();
      return;
    }

    final ctrl = _ctrl[id]!;
    final caret = ctrl.selection.baseOffset.clamp(0, ctrl.text.length);
    // Die Abfrage selbst faellt weg; was links und rechts davon stand, bleibt.
    final rest = ctrl.text.substring(0, _slashStart) + ctrl.text.substring(caret);
    final at = _slashStart;
    _closeSlash();
    _syncText();

    if (command.kind == BlockKind.divider) {
      _insertDivider(i, rest);
      return;
    }
    _convert(i, command.kind, level: command.level, text: rest, caret: at);
    _focus[id]!.requestFocus();
  }

  // ── Tastatur ────────────────────────────────────────────────────────────────

  /// Der eine Ort, an dem die Editorregeln hängen.
  ///
  /// Sitzt in einem `Focus` unmittelbar über dem Feld und wird damit vor
  /// `DefaultTextEditingShortcuts` gefragt — `FocusManager` läuft vom Blatt zur Wurzel. Ein
  /// `handled` verhindert, dass der Embedder überhaupt Text einsetzt; nur so lässt sich Enter
  /// abfangen.
  KeyEventResult _onBlockKey(String id, KeyEvent event) {
    // Auch das Loslassen laeuft hier durch; ohne diese Schranke teilte ein Enter zweimal.
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }

    final i = _indexOf(id);
    if (i < 0) return KeyEventResult.ignored;

    final key = event.logicalKey;

    // Solange das Slash-Menue offen steht, gehoeren die Navigationstasten ihm. Das geht nur,
    // weil FocusManager vom Blatt zur Wurzel laeuft: sonst machte
    // DefaultTextEditingShortcuts aus Hoch/Runter senkrechte Cursorbewegungen.
    if (_slashOpen && id == _slashBlockId) {
      if (key == LogicalKeyboardKey.arrowDown) {
        _moveSlash(1);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowUp) {
        _moveSlash(-1);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.escape) {
        _dismissSlash();
        return KeyEventResult.handled;
      }
      final confirms = key == LogicalKeyboardKey.enter ||
          key == LogicalKeyboardKey.numpadEnter ||
          key == LogicalKeyboardKey.tab;
      if (confirms && _slashCommands.isNotEmpty) {
        _commitSlash(_slashCommands[_slashIndex]);
        return KeyEventResult.handled;
      }
    }

    if (HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isMetaPressed) {
      final format = switch (key) {
        LogicalKeyboardKey.keyB => InlineFormat.bold,
        LogicalKeyboardKey.keyI => InlineFormat.italic,
        LogicalKeyboardKey.keyE => InlineFormat.code,
        LogicalKeyboardKey.keyH => InlineFormat.highlight,
        _ => null,
      };
      if (format != null) {
        _applyInline(id, format);
        return KeyEventResult.handled;
      }
    }

    final vertical =
        key == LogicalKeyboardKey.arrowUp || key == LogicalKeyboardKey.arrowDown;
    if (!vertical) _desiredX = null;

    final block = _blocks[i];
    final sel = _ctrl[id]!.selection;
    final shift = HardwareKeyboard.instance.isShiftPressed;

    if (key == LogicalKeyboardKey.enter || key == LogicalKeyboardKey.numpadEnter) {
      // Im Codeblock ist Enter ein Zeilenumbruch — der Block ist der einzige mehrzeilige.
      if (block.kind == BlockKind.code) return KeyEventResult.ignored;
      _enter(i, sel);
      return KeyEventResult.handled;
    }

    if (key == LogicalKeyboardKey.backspace) {
      if (!sel.isValid || !sel.isCollapsed || sel.baseOffset != 0) {
        return KeyEventResult.ignored;
      }
      return _backspaceAtStart(i) ? KeyEventResult.handled : KeyEventResult.ignored;
    }

    if (key == LogicalKeyboardKey.tab) {
      if (block.kind == BlockKind.code) {
        _insertAtCaret(id, '  ');
      } else if (shift) {
        _outdent(i);
      } else {
        _indent(i);
      }
      // Ohne handled springt der Fokus unter Linux in den naechsten Block.
      return KeyEventResult.handled;
    }

    if (vertical && sel.isValid && sel.isCollapsed) {
      return _verticalHop(i, key == LogicalKeyboardKey.arrowUp ? -1 : 1, sel);
    }

    if (key == LogicalKeyboardKey.space) {
      return _tryShortcut(i, sel);
    }

    return KeyEventResult.ignored;
  }

  /// Deutet das, was links der Einfügemarke steht, als Markdown-Kürzel.
  ///
  /// Bewusst hier und nicht im Controller-Zuhörer: dort müsste der Controller aus seiner
  /// eigenen Benachrichtigung heraus umgeschrieben werden.
  KeyEventResult _tryShortcut(int i, TextSelection sel) {
    final block = _blocks[i];
    if (block.kind == BlockKind.code) return KeyEventResult.ignored;
    if (!sel.isValid || !sel.isCollapsed || sel.baseOffset <= 0) {
      return KeyEventResult.ignored;
    }

    final text = _ctrl[block.id]!.text;
    final shortcut = markdownShortcut(text.substring(0, sel.baseOffset));
    if (shortcut == null) return KeyEventResult.ignored;

    final rest = text.substring(sel.baseOffset);
    _syncText();

    if (shortcut.kind == BlockKind.divider) {
      _insertDivider(i, rest);
      return KeyEventResult.handled;
    }

    _convert(
      i,
      shortcut.kind,
      level: shortcut.level,
      startNumber: shortcut.startNumber,
      text: rest,
      caret: 0,
    );
    return KeyEventResult.handled;
  }

  /// Stellt die Blockart um — der gemeinsame Weg für Markdown-Kürzel und Slash-Menü.
  void _convert(
    int i,
    BlockKind kind, {
    int level = 1,
    int startNumber = 1,
    String? text,
    int? caret,
  }) {
    final block = _blocks[i];
    final value = text ?? block.text;
    _write(block.id, value, caret ?? value.length);
    setState(() {
      _blocks[i] = block.copyWith(
        kind: kind,
        level: level,
        startNumber: startNumber,
        text: value,
        checked: false,
        // Ein frisch angelegter Aufklappblock steht offen — sonst verschwaende der Nutzer
        // seinen Rumpf, kaum dass er ihn angelegt hat.
        collapsed: kind == BlockKind.toggle ? false : block.collapsed,
      );
    });
    _scheduleSave();
  }

  void _insertAtCaret(String id, String snippet) {
    final ctrl = _ctrl[id]!;
    final sel = ctrl.selection;
    final start = sel.isValid ? sel.start : ctrl.text.length;
    final end = sel.isValid ? sel.end : start;
    _write(id, ctrl.text.replaceRange(start, end, snippet), start + snippet.length);
    _scheduleSave();
  }

  // ── Umbauten ────────────────────────────────────────────────────────────────

  void _enter(int i, TextSelection sel) {
    _syncText();
    final block = _blocks[i];

    // Ein leerer Listeneintrag verlaesst die Liste, statt eine weitere leere Zeile anzulegen:
    // erst eine Stufe heraus, auf der aeussersten wird er zum Absatz.
    if (block.isListItem && block.text.isEmpty) {
      if (block.depth > 0) {
        _outdent(i);
      } else {
        _write(block.id, '', 0);
        setState(() => _blocks[i] = toParagraph(block));
        _scheduleSave();
      }
      return;
    }

    final caret = sel.isValid ? sel.start : block.text.length;
    final end = sel.isValid ? sel.end : caret;
    final left = block.text.substring(0, caret.clamp(0, block.text.length));
    final right = block.text.substring(end.clamp(0, block.text.length));

    // Mitten im Text geteilt behalten beide Haelften ihre Blockart; am Ende beginnt das, was
    // Enter sonst anlegt — unter einer Ueberschrift also ein Absatz.
    final next = right.isEmpty ? continuationOf(block) : cloneOf(block, text: right);
    _ensure(next);

    _write(block.id, left, left.length);
    setState(() {
      _blocks[i] = block.copyWith(text: left);
      _blocks.insert(i + 1, next);
      normalizeDepths(_blocks);
    });

    // Zulaessig auf einem noch nicht eingehaengten Knoten: der Wunsch wird beim Einhaengen
    // nachgeholt. Ein postFrameCallback braucht es dafuer nicht.
    _focus[next.id]!.requestFocus();
    _scheduleSave();
  }

  /// Backspace ganz am Anfang: erst die Blockart abstreifen, dann ausrücken, dann verschmelzen.
  ///
  /// Gibt false zurück, wenn nichts zu tun war — dann darf das Feld den Tastendruck behalten.
  bool _backspaceAtStart(int i) {
    _syncText();
    final block = _blocks[i];

    if (block.kind != BlockKind.paragraph) {
      setState(() => _blocks[i] = toParagraph(block));
      _scheduleSave();
      return true;
    }
    if (block.depth > 0) {
      _outdent(i);
      return true;
    }
    if (i == 0) return false;

    final prev = _blocks[i - 1];

    // Ein Trenner traegt keinen Text — er wird geloescht, statt verschmolzen.
    if (prev.kind == BlockKind.divider) {
      setState(() => _blocks.removeAt(i - 1));
      _retire(prev.id);
      _scheduleSave();
      return true;
    }
    // In einen Codeblock hinein wuerde das Verschmelzen Fliesstext einschleusen; stattdessen
    // wandert nur die Einfuegemarke ans Ende.
    if (prev.kind == BlockKind.code) {
      _ctrl[prev.id]!.selection =
          TextSelection.collapsed(offset: _ctrl[prev.id]!.text.length);
      _focus[prev.id]!.requestFocus();
      return true;
    }

    final joined = prev.text + block.text;
    _write(prev.id, joined, prev.text.length);
    // Erst den Ueberlebenden fokussieren, dann den sterbenden Block aushaengen: sonst ist
    // dessen Knoten beim Aushaengen noch primaerer Fokus und die Eingabeverbindung
    // schliesst und oeffnet neu.
    _focus[prev.id]!.requestFocus();
    setState(() {
      _blocks[i - 1] = prev.copyWith(text: joined);
      _blocks.removeAt(i);
      normalizeDepths(_blocks);
    });
    _retire(block.id);
    _scheduleSave();
    return true;
  }

  void _indent(int i) {
    final limit = maxDepthAt(_blocks, i);
    if (_blocks[i].depth >= limit) return;
    _shiftRun(i, 1);
  }

  void _outdent(int i) {
    if (_blocks[i].depth == 0) return;
    _shiftRun(i, -1);
  }

  /// Verschiebt den Block samt allem, was unter ihm hängt, um eine Stufe.
  void _shiftRun(int i, int delta) {
    _syncText();
    final length = runLengthAt(_blocks, i);
    setState(() {
      for (var k = i; k < i + length; k++) {
        _blocks[k] = _blocks[k].copyWith(depth: _blocks[k].depth + delta);
      }
      normalizeDepths(_blocks);
    });
    _scheduleSave();
  }

  /// Mehrzeiliger Text in einem einzeiligen Block — beim Einfügen aus der Zwischenablage.
  ///
  /// Wird als Markup gelesen, nicht als Rohtext: eine eingefügte Liste wird zu Listenblöcken,
  /// so wie man es von Notion kennt.
  void _explode(int i) {
    _syncText();
    final block = _blocks[i];
    final lines = _ctrl[block.id]!.text.split('\n');

    final incoming = blocksFromMarkup(lines.sublist(1).join('\n'))
        .map((b) => b.copyWith(depth: b.depth + block.depth))
        .toList();
    for (final b in incoming) {
      _ensure(b);
    }

    _write(block.id, lines.first, lines.first.length);
    setState(() {
      _blocks[i] = block.copyWith(text: lines.first);
      _blocks.insertAll(i + 1, incoming);
      normalizeDepths(_blocks);
    });

    final last = incoming.last;
    _ctrl[last.id]!.selection = TextSelection.collapsed(offset: last.text.length);
    _focus[last.id]!.requestFocus();
    _scheduleSave();
  }

  void _insertDivider(int i, String rest) {
    final below = EditorBlock.fresh(depth: _blocks[i].depth, text: rest);
    _ensure(below);
    _write(_blocks[i].id, '', 0);
    setState(() {
      _blocks[i] = _blocks[i].copyWith(kind: BlockKind.divider, text: '');
      _blocks.insert(i + 1, below);
      normalizeDepths(_blocks);
    });
    _focus[below.id]!.requestFocus();
    _scheduleSave();
  }

  void _toggleChecked(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    setState(() => _blocks[i] = _blocks[i].copyWith(checked: !_blocks[i].checked));
    _scheduleSave();
  }

  void _toggleCollapsed(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    // Bewusst nicht Teil des Markups: beim naechsten Oeffnen sollen die Antworten wieder
    // verborgen sein, und es zu speichern machte aus dem Lesen einen Schreibvorgang.
    setState(() => _blocks[i] = _blocks[i].copyWith(collapsed: !_blocks[i].collapsed));
  }

  // ── Blockmenü und Ziehen ────────────────────────────────────────────────────

  Future<void> _openBlockMenu(String id, Offset at) async {
    if (_indexOf(id) < 0) return;
    _syncText();
    await showNoteBlockMenu(
      context,
      globalPosition: at,
      onAction: (action) {
        switch (action) {
          case NoteBlockAction.duplicate:
            _duplicateBlock(id);
          case NoteBlockAction.delete:
            _deleteBlock(id);
        }
      },
      onConvert: (command) {
        final i = _indexOf(id);
        if (i < 0) return;
        _convert(i, command.kind, level: command.level);
      },
    );
  }

  /// Legt einen leeren Block darunter an und öffnet gleich das Befehlsmenü.
  void _insertBelow(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    _syncText();

    final block = EditorBlock.fresh(depth: _blocks[i].depth);
    _ensure(block);
    // Hinter den ganzen Lauf, sonst landete der neue Block zwischen einem Aufklappblock und
    // seinen Kindern.
    setState(() => _blocks.insert(i + runLengthAt(_blocks, i), block));
    _focus[block.id]!.requestFocus();
    // Genau das, was ein getipptes "/" ausloest — nur ohne den Umweg ueber die Tastatur.
    _write(block.id, '/', 1);
    // Erst nach dem Bild: vorher ist das Feld noch nicht vermessen, und das Menue wuesste
    // nicht, wo die Einfuegemarke steht.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _indexOf(block.id) >= 0) _updateSlash(block.id);
    });
    _scheduleSave();
  }

  void _duplicateBlock(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    _syncText();

    final length = runLengthAt(_blocks, i);
    final copies = [for (var k = i; k < i + length; k++) cloneOf(_blocks[k])];
    for (final copy in copies) {
      _ensure(copy);
    }
    setState(() => _blocks.insertAll(i + length, copies));
    _scheduleSave();
  }

  void _deleteBlock(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    _syncText();

    final length = runLengthAt(_blocks, i);
    final removed = [for (var k = i; k < i + length; k++) _blocks[k].id];

    setState(() {
      _blocks.removeRange(i, i + length);
      // Ein Dokument ohne Bloecke haette keine Stelle, an der die Einfuegemarke stehen koennte.
      if (_blocks.isEmpty) {
        final fresh = EditorBlock.fresh();
        _ensure(fresh);
        _blocks.add(fresh);
      }
      normalizeDepths(_blocks);
    });

    for (final gone in removed) {
      _retire(gone);
    }
    final next = _blocks[(i - 1).clamp(0, _blocks.length - 1)];
    if (next.isTextual) _focus[next.id]!.requestFocus();
    _scheduleSave();
  }

  /// Setzt den gezogenen Block — samt allem, was unter ihm hängt — vor [slot].
  void _dropBlock(String id, int slot) {
    final from = _indexOf(id);
    if (from < 0) return;
    _syncText();

    final moved = moveRun(_blocks, from, runLengthAt(_blocks, from), slot);
    setState(() {
      _blocks
        ..clear()
        ..addAll(moved);
      normalizeDepths(_blocks);
    });
    _scheduleSave();
  }

  /// `Draggable` scrollt nicht von selbst — bei einer langen Notiz wäre Ziehen sonst unbrauchbar.
  void _startAutoScroll() {
    _autoScroll?.cancel();
    _autoScroll = Timer.periodic(const Duration(milliseconds: 16), (_) {
      if (!_scroll.hasClients || !mounted) return;
      final box = context.findRenderObject() as RenderBox?;
      if (box == null) return;

      const edge = 72.0;
      const step = 14.0;
      final top = box.localToGlobal(Offset.zero).dy;
      final bottom = top + box.size.height;
      final position = _scroll.position;

      final double target;
      if (_pointerY < top + edge) {
        target = position.pixels - step;
      } else if (_pointerY > bottom - edge) {
        target = position.pixels + step;
      } else {
        return;
      }
      _scroll.jumpTo(
        target.clamp(position.minScrollExtent, position.maxScrollExtent),
      );
    });
  }

  void _endDrag() {
    _autoScroll?.cancel();
    _autoScroll = null;
  }

  // ── Bewegung zwischen Blöcken ───────────────────────────────────────────────

  /// Das `RenderEditable` eines Blocks.
  ///
  /// `TextField` reicht seinen `EditableTextState` nicht heraus, also über den GlobalKey am
  /// Feld die Renderobjekte absteigen. Nur so kommt der Editor an die Geometrie der
  /// Einfügemarke.
  RenderEditable? _editableOf(String id) {
    final object = _keys[id]?.currentContext?.findRenderObject();
    if (object == null) return null;

    RenderEditable? found;
    void visit(RenderObject node) {
      if (found != null) return;
      if (node is RenderEditable) {
        found = node;
        return;
      }
      node.visitChildren(visit);
    }

    visit(object);
    // Ein gerade erst angelegter Block ist noch nicht vermessen. Nach seiner Geometrie zu
    // fragen wirft ("does not have any constraints before it has been laid out"), also gilt er
    // hier als noch nicht da.
    if (found != null && !found!.hasSize) return null;
    return found;
  }

  /// Pfeil hoch/runter an der ersten bzw. letzten **sichtbaren** Zeile springt in den
  /// Nachbarblock; überall sonst behält das Feld die Taste.
  KeyEventResult _verticalHop(int i, int delta, TextSelection sel) {
    final source = _editableOf(_blocks[i].id);
    if (source == null) return KeyEventResult.ignored;

    final caret = source.getLocalRectForCaret(
      TextPosition(offset: sel.extentOffset, affinity: sel.affinity),
    );
    final lineHeight = source.preferredLineHeight;
    // Geometrisch statt ueber getLineAtOffset: dessen Antwort haengt an der Umbruchgrenze von
    // der Affinitaet ab.
    final atEdge = delta < 0
        ? caret.top < lineHeight * 0.5
        : caret.bottom > source.size.height - lineHeight * 0.5;
    if (!atEdge) {
      _desiredX = null;
      return KeyEventResult.ignored;
    }

    final target = _neighbour(i, delta);
    if (target == null) return KeyEventResult.ignored;

    _desiredX ??= source.localToGlobal(caret.center).dx;
    final targetId = _blocks[target].id;
    final targetCtrl = _ctrl[targetId]!;
    final targetEditable = _editableOf(targetId);

    var offset = delta < 0 ? targetCtrl.text.length : 0;
    if (targetEditable != null) {
      final y = delta < 0
          ? targetEditable
                  .localToGlobal(Offset(0, targetEditable.size.height))
                  .dy -
              lineHeight * 0.5
          : targetEditable.localToGlobal(Offset.zero).dy + lineHeight * 0.5;
      // getPositionForPoint erwartet globale Koordinaten.
      offset = targetEditable.getPositionForPoint(Offset(_desiredX!, y)).offset;
    }

    _programmatic = true;
    targetCtrl.selection =
        TextSelection.collapsed(offset: offset.clamp(0, targetCtrl.text.length));
    _programmatic = false;
    _focus[targetId]!.requestFocus();
    return KeyEventResult.handled;
  }

  /// Der nächste beschreibbare, sichtbare Block in Richtung [delta].
  int? _neighbour(int from, int delta) {
    for (var k = from + delta; k >= 0 && k < _blocks.length; k += delta) {
      if (_blocks[k].isTextual && !_hidden(k)) return k;
    }
    return null;
  }

  /// Steckt der Block in einem zugeklappten Aufklappblock?
  bool _hidden(int index) {
    var need = _blocks[index].depth;
    for (var k = index - 1; k >= 0 && need > 0; k--) {
      final b = _blocks[k];
      if (b.depth >= need) continue;
      // b ist der direkte Vorfahr auf der naechstflacheren Stufe.
      if (b.kind == BlockKind.toggle && b.collapsed) return true;
      need = b.depth;
    }
    return false;
  }

  // ── Speichern ───────────────────────────────────────────────────────────────

  void _scheduleSave() {
    _saveTimer?.cancel();
    if (_status != _SaveStatus.pending && mounted) {
      setState(() => _status = _SaveStatus.pending);
    }
    _saveTimer = Timer(_saveDelay, _saveNow);
  }

  Future<void> _saveNow() async {
    _saveTimer?.cancel();
    _saveTimer = null;
    if (_note == null) return;

    _syncText();
    final content = markupFromBlocks(_blocks);
    final title = _titleCtrl.text;
    if (content == _savedContent && title == _savedTitle) {
      if (mounted && _status == _SaveStatus.pending) {
        setState(() => _status = _SaveStatus.saved);
      }
      return;
    }

    if (mounted) setState(() => _status = _SaveStatus.saving);
    final updated = _note!.copyWith(title: title, content: content);
    final ok = await _provider.updateNote(updated);
    if (ok) {
      _note = updated;
      _savedContent = content;
      _savedTitle = title;
    }
    if (!mounted) return;
    setState(() => _status = ok ? _SaveStatus.saved : _SaveStatus.failed);
  }

  /// Speichern ohne Warten und ohne `context` — für den Weg aus der Seite heraus.
  void _flush() {
    _saveTimer?.cancel();
    _saveTimer = null;
    if (_note == null) return;

    _syncText();
    final content = markupFromBlocks(_blocks);
    final title = _titleCtrl.text;
    if (content == _savedContent && title == _savedTitle) return;

    _savedContent = content;
    _savedTitle = title;
    _provider.updateNote(_note!.copyWith(title: title, content: content));
  }

  // ── Oberfläche ──────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (_note == null) {
      return Scaffold(
        backgroundColor: theme.colorScheme.surface,
        appBar: AppBar(backgroundColor: Colors.transparent, elevation: 0),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.description_outlined, size: 56, color: Colors.grey),
              const SizedBox(height: 16),
              Text('Seite nicht gefunden', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                'Sie wurde vermutlich gelöscht.',
                style: theme.textTheme.bodyMedium?.copyWith(color: Colors.grey),
              ),
            ],
          ),
        ),
      );
    }

    return PopScope(
      // Fing frueher nur den Knopf ab; die Systemgeste verlor die letzten Aenderungen.
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) _flush();
      },
      child: Scaffold(
        backgroundColor: theme.colorScheme.surface,
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () async {
              await _saveNow();
              if (context.mounted) Navigator.pop(context);
            },
          ),
          actions: [
            _SaveIndicator(status: _status),
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert),
              itemBuilder: (_) => [
                const PopupMenuItem(
                    value: 'favorite',
                    child: Row(children: [
                      Icon(Icons.star_outline),
                      SizedBox(width: 8),
                      Text('Favorit'),
                    ])),
              ],
              onSelected: (val) {
                if (val == 'favorite' && _note?.id != null) {
                  _provider.toggleFavorite(_note!.id!);
                }
              },
            ),
          ],
        ),
        body: OverlayPortal(
          controller: _formatPortal,
          overlayChildBuilder: (_) => _formatOverlay(),
          child: OverlayPortal(
            controller: _slashPortal,
            overlayChildBuilder: (_) => _slashOverlay(),
            child: SingleChildScrollView(
              controller: _scroll,
              padding: const EdgeInsets.fromLTRB(12, 8, 24, 32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(left: NoteBlockRow.gutterWidth),
                    child: _header(theme),
                  ),
                  _document(),
                  _tailSpace(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _header(ThemeData theme) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Breadcrumbs(noteId: widget.noteId),
          TextField(
            controller: _titleCtrl,
            style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold),
            maxLines: null,
            decoration: NoteBlockRow.bareField.copyWith(hintText: 'Untitled'),
          ),
          if (_note?.courseName != null)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(
                children: [
                  const Icon(Icons.school_outlined, size: 14, color: Colors.grey),
                  const SizedBox(width: 4),
                  Text(_note!.courseName!,
                      style: const TextStyle(fontSize: 12, color: Colors.grey)),
                ],
              ),
            ),
          const SizedBox(height: 12),
        ],
      );

  /// Alle sichtbaren Blöcke als [Column].
  ///
  /// Bewusst **kein** `ListView.builder`: der haengt Bloecke ausserhalb des Sichtbereichs aus
  /// und zerstoert damit ihren `EditableTextState` und ihr `RenderEditable` — die
  /// Pfeiltastensprünge und die Overlays griffen fuer jeden weggescrollten Block ins Leere.
  Widget _document() {
    final rows = <Widget>[];
    for (var i = 0; i < _blocks.length; i++) {
      if (_hidden(i)) continue;
      final b = _blocks[i];
      rows.add(_dropGap(i));
      rows.add(NoteBlockRow(
        // Ohne stabilen Schluessel verwendet Flutter beim Einfuegen die Elemente der
        // Nachbarn weiter, und die Felder tauschen still ihre Controller.
        key: ValueKey(b.id),
        block: b,
        controller: _ctrl[b.id]!,
        focusNode: _focus[b.id]!,
        fieldKey: _keys[b.id]!,
        link: _links[b.id]!,
        number: b.kind == BlockKind.numbered ? displayNumber(_blocks, i) : null,
        isActive: _activeId == b.id,
        onKey: (event) => _onBlockKey(b.id, event),
        onToggleChecked: () => _toggleChecked(b.id),
        onToggleCollapsed: () => _toggleCollapsed(b.id),
        onTapText: () => _desiredX = null,
        gutterBuilder: (visible) => _gutter(b, visible),
      ));
    }
    rows.add(_dropGap(_blocks.length));
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: rows);
  }

  /// Die Ablagestelle zwischen zwei Blöcken.
  ///
  /// Ein eigener [DragTarget] je Lücke statt einer Rechnung über die Zeigerhöhe: der Treffer
  /// steht damit fest, und `candidateData` sagt schon, ob gerade hier abgelegt würde — ganz
  /// ohne eigenen Zustand.
  Widget _dropGap(int slot) => DragTarget<String>(
        onAcceptWithDetails: (details) => _dropBlock(details.data, slot),
        builder: (ctx, candidates, _) => SizedBox(
          height: 8,
          width: double.infinity,
          child: candidates.isEmpty
              ? null
              : Center(
                  child: Container(
                    height: 2,
                    color: Theme.of(ctx).colorScheme.primary,
                  ),
                ),
        ),
      );

  /// Die Anfasser links vom Block: einfügen und ziehen.
  Widget _gutter(EditorBlock block, bool visible) {
    // Ohne TextFieldTapRegion gilt der Mausdruck auf einen Anfasser als Klick ausserhalb des
    // Felds — onTapOutside naehme Fokus und Auswahl weg, noch bevor der Griff greift.
    return TextFieldTapRegion(
      child: IgnorePointer(
        ignoring: !visible,
        child: AnimatedOpacity(
          opacity: visible ? 1 : 0,
          duration: const Duration(milliseconds: 90),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _GutterButton(
                icon: Icons.add,
                tooltip: 'Block einfügen',
                onTap: () => _insertBelow(block.id),
              ),
              _dragHandle(block),
            ],
          ),
        ),
      ),
    );
  }

  /// Der ⋮⋮-Griff: klicken öffnet das Blockmenü, ziehen ordnet um.
  ///
  /// [PointerAwareDraggable] statt `LongPressDraggable`, weil dessen Maus-Hit-Slop ein Pixel
  /// beträgt und ein Mausdrag damit praktisch nie zustande kommt. Der Griff gewinnt bei einem
  /// Klick ohne Bewegung die Gestenarena, weil der Drag erst ab acht Pixeln zuschlägt.
  Widget _dragHandle(EditorBlock block) {
    return Builder(
      builder: (ctx) {
        final handle = _GutterButton(
          icon: Icons.drag_indicator,
          tooltip: 'Ziehen zum Verschieben, klicken für das Menü',
          onTap: () {
            final box = ctx.findRenderObject() as RenderBox?;
            _openBlockMenu(
              block.id,
              box == null
                  ? Offset.zero
                  : box.localToGlobal(box.size.bottomLeft(Offset.zero)),
            );
          },
        );

        return PointerAwareDraggable<String>(
          data: block.id,
          axis: Axis.vertical,
          dragAnchorStrategy: pointerDragAnchorStrategy,
          rootOverlay: true,
          // Nie ein TextField als Vorschau: das zweite Widget teilte sich Controller und
          // FocusNode mit dem echten Block.
          feedback: _dragPreview(block),
          childWhenDragging: Opacity(opacity: 0.3, child: handle),
          onDragStarted: _startAutoScroll,
          onDragUpdate: (details) => _pointerY = details.globalPosition.dy,
          onDragEnd: (_) => _endDrag(),
          onDraggableCanceled: (_, _) => _endDrag(),
          child: handle,
        );
      },
    );
  }

  Widget _dragPreview(EditorBlock block) {
    final theme = Theme.of(context);
    final text = _ctrl[block.id]?.text ?? block.text;
    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      elevation: 6,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 420),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Text(
          text.isEmpty ? 'Leerer Block' : text,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.bodyMedium,
        ),
      ),
    );
  }

  /// Das Slash-Menü, aufgehängt am Feld des laufenden Blocks.
  ///
  /// `CompositedTransformFollower` folgt dem Scrollen und jeder Verschiebung darüber ohne
  /// eigenen Neuaufbau; nur der waagerechte Versatz zur Einfügemarke muss nachgereicht werden.
  Widget _slashOverlay() {
    final id = _slashBlockId;
    final link = id == null ? null : _links[id];
    if (id == null || link == null) return const SizedBox.shrink();

    return Positioned(
      left: 0,
      top: 0,
      child: CompositedTransformFollower(
        link: link,
        targetAnchor: Alignment.bottomLeft,
        followerAnchor: Alignment.topLeft,
        offset: Offset(_caretDx(id), 6),
        // Ohne TextFieldTapRegion gilt der Mausdruck auf einen Eintrag als Klick ausserhalb
        // des Felds: onTapOutside nimmt Fokus und Auswahl weg, und der Eintrag wirkte auf
        // nichts. ExcludeFocus haelt zusaetzlich die Knoepfe vom Fokus fern.
        child: TextFieldTapRegion(
          child: ExcludeFocus(
            child: NoteSlashMenu(
              commands: _slashCommands,
              highlighted: _slashIndex,
              onPick: _commitSlash,
              scrollController: _slashScroll,
            ),
          ),
        ),
      ),
    );
  }

  /// Die Formatierleiste über der Auswahl.
  ///
  /// Nur der Anker steckt im [ValueListenableBuilder] — die Leiste selbst wird als `child`
  /// durchgereicht und beim Verschieben der Auswahl nicht neu gebaut.
  Widget _formatOverlay() {
    final id = _formatBlockId;
    final link = id == null ? null : _links[id];
    if (id == null || link == null) return const SizedBox.shrink();

    return Positioned(
      left: 0,
      top: 0,
      child: ValueListenableBuilder<Offset>(
        valueListenable: _formatAnchor,
        builder: (_, anchor, child) => CompositedTransformFollower(
          link: link,
          targetAnchor: Alignment.topLeft,
          followerAnchor: Alignment.bottomLeft,
          offset: anchor,
          child: child,
        ),
        child: TextFieldTapRegion(
          child: ExcludeFocus(
            child: NoteFormatToolbar(onApply: (format) => _applyInline(id, format)),
          ),
        ),
      ),
    );
  }

  /// Waagerechter Abstand der Einfügemarke von der linken Textkante.
  double _caretDx(String id) {
    final editable = _editableOf(id);
    final selection = _ctrl[id]?.selection;
    if (editable == null || selection == null || !selection.isValid) return 0;
    return editable
        .getLocalRectForCaret(TextPosition(offset: selection.baseOffset))
        .left;
  }

  /// Die leere Fläche unter dem letzten Block — ein Klick dorthin schreibt weiter, statt ins
  /// Nichts zu gehen.
  Widget _tailSpace() => GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          final last = _blocks.last;
          if (last.kind == BlockKind.paragraph && last.text.isEmpty) {
            _focus[last.id]!.requestFocus();
            return;
          }
          final block = EditorBlock.fresh(depth: 0);
          _ensure(block);
          setState(() => _blocks.add(block));
          _focus[block.id]!.requestFocus();
          _scheduleSave();
        },
        child: const SizedBox(height: 140, width: double.infinity),
      );
}

/// Ein Anfasser in der Rinne — klein, grau, und erst beim Überfahren zu sehen.
class _GutterButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  const _GutterButton({required this.icon, required this.tooltip, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      waitDuration: const Duration(milliseconds: 600),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(4),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 1),
          child: Icon(icon, size: 16, color: Theme.of(context).colorScheme.onSurfaceVariant),
        ),
      ),
    );
  }
}

// ── Speicheranzeige ───────────────────────────────────────────────────────────

/// Statt eines Speichern-Knopfs: die Seite sichert sich von selbst, hier steht nur, wo sie steht.
class _SaveIndicator extends StatelessWidget {
  final _SaveStatus status;
  const _SaveIndicator({required this.status});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (String label, Color color) = switch (status) {
      _SaveStatus.clean => ('', Colors.transparent),
      _SaveStatus.pending => ('…', theme.colorScheme.onSurfaceVariant),
      _SaveStatus.saving => ('Speichert…', theme.colorScheme.onSurfaceVariant),
      _SaveStatus.saved => ('Gespeichert', theme.colorScheme.onSurfaceVariant),
      _SaveStatus.failed => ('Nicht gespeichert', theme.colorScheme.error),
    };
    if (label.isEmpty) return const SizedBox.shrink();

    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Text(label, style: theme.textTheme.labelSmall?.copyWith(color: color)),
      ),
    );
  }
}

// ── Brotkrumen ────────────────────────────────────────────────────────────────

class _Breadcrumbs extends StatelessWidget {
  final int noteId;
  const _Breadcrumbs({required this.noteId});

  @override
  Widget build(BuildContext context) {
    // Ohne die letzte Station: die steht direkt darunter als Titel.
    final path = context.watch<StudyProvider>().breadcrumbsFor(noteId);
    if (path.length < 2) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final ancestors = path.sublist(0, path.length - 1);

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          for (var i = 0; i < ancestors.length; i++) ...[
            InkWell(
              onTap: () => Navigator.of(context).pushReplacement(MaterialPageRoute(
                builder: (_) => StudyNoteEditorPage(noteId: ancestors[i].id!),
              )),
              child: Text(
                ancestors[i].title,
                style: theme.textTheme.labelMedium?.copyWith(color: Colors.grey),
              ),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4),
              child: Icon(Icons.chevron_right, size: 14, color: Colors.grey),
            ),
          ],
        ],
      ),
    );
  }
}
