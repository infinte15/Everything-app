import 'package:flutter/material.dart';

import '../../../../utils/note_blocks.dart';

/// Eine Zeile des Notizeditors: Anfasserrinne, Blockzeichen und das bearbeitbare Feld.
///
/// Anders als früher gibt es keine getrennte Vorschau mehr — was hier steht, ist zugleich das
/// Ergebnis und die Eingabe. Der Block bestimmt nur Schrift und Vorzeichen; sämtliche Regeln
/// (Enter, Backspace, Tab, Kürzel) liegen beim Wirt, der [onKey] mitgibt.
class NoteBlockRow extends StatefulWidget {
  final EditorBlock block;
  final TextEditingController controller;
  final FocusNode focusNode;

  /// Sitzt am Feld, damit der Wirt darüber an dessen `RenderEditable` kommt — er braucht die
  /// Geometrie der Einfügemarke für den Sprung in den Nachbarblock und für die Overlays.
  final GlobalKey fieldKey;

  /// Ankerpunkt für Slash-Menü und Formatierleiste. Liegt am Feld, nicht an der ganzen Zeile,
  /// damit die Overlays an der Textkante hängen und nicht an der Anfasserrinne.
  final LayerLink link;

  /// Nur für [BlockKind.numbered]: die berechnete, nicht die getippte Zahl.
  final int? number;

  /// Ob dieser Block gerade die Einfügemarke hält — steuert allein den Platzhalter.
  final bool isActive;

  final KeyEventResult Function(KeyEvent) onKey;
  final VoidCallback onToggleChecked;
  final VoidCallback onToggleCollapsed;

  /// Beim Antippen verwirft der Wirt die gemerkte Spalte der Pfeiltasten.
  final VoidCallback onTapText;

  /// Baut die Anfasserrinne. Bekommt mit, ob sie gerade sichtbar sein soll — der Wirt kennt
  /// den Zeiger nicht, das Überfahren wird hier lokal gemerkt, damit nicht das ganze Dokument
  /// neu aufgebaut wird.
  final Widget Function(bool visible) gutterBuilder;

  const NoteBlockRow({
    super.key,
    required this.block,
    required this.controller,
    required this.focusNode,
    required this.fieldKey,
    required this.link,
    required this.isActive,
    required this.onKey,
    required this.onToggleChecked,
    required this.onToggleCollapsed,
    required this.onTapText,
    required this.gutterBuilder,
    this.number,
  });

  /// Breite der Rinne. Auch ohne sichtbare Anfasser reserviert, damit der Text beim
  /// Überfahren nicht springt.
  static const double gutterWidth = 44;

  /// Ein Einrückschritt in Pixeln.
  static const double indentStep = 24;

  /// Ein Feld ganz ohne Rahmen und Füllung — nackter Text auf dem Seitenhintergrund.
  ///
  /// Jeder Rahmenzustand muss einzeln abgeräumt werden: `AppTheme` setzt `enabledBorder` und
  /// `focusedBorder` ausdrücklich, und ein blosses `border: InputBorder.none` bliebe davon
  /// ungehört — der Block mit der Einfügemarke bekäme einen Kasten drumherum.
  static const InputDecoration bareField = InputDecoration(
    isCollapsed: true,
    filled: false,
    contentPadding: EdgeInsets.zero,
    border: InputBorder.none,
    enabledBorder: InputBorder.none,
    focusedBorder: InputBorder.none,
    disabledBorder: InputBorder.none,
    errorBorder: InputBorder.none,
    focusedErrorBorder: InputBorder.none,
  );

  @override
  State<NoteBlockRow> createState() => _NoteBlockRowState();
}

class _NoteBlockRowState extends State<NoteBlockRow> {
  bool _hover = false;

  EditorBlock get block => widget.block;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return MouseRegion(
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: Padding(
        padding: EdgeInsets.only(left: block.depth * NoteBlockRow.indentStep),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: NoteBlockRow.gutterWidth,
              // Auf Touch gibt es kein Überfahren; dort tritt der laufende Block an die Stelle.
              child: widget.gutterBuilder(_hover || widget.isActive),
            ),
            Expanded(child: _body(context, theme)),
          ],
        ),
      ),
    );
  }

  Widget _body(BuildContext context, ThemeData theme) {
    if (block.kind == BlockKind.divider) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 10),
        child: Divider(height: 1),
      );
    }
    if (block.kind == BlockKind.code) return _code(theme);
    if (block.kind == BlockKind.callout) return _callout(theme);

    final marker = _marker(theme);
    final field = _field(theme, style: _textStyle(theme));

    return Padding(
      padding: _outerPadding,
      child: marker == null
          ? field
          : Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [marker, Expanded(child: field)],
            ),
    );
  }

  // ── Vorzeichen ──────────────────────────────────────────────────────────────

  /// Checkbox, Punkt, Zahl oder Pfeil — was links vom Text steht.
  Widget? _marker(ThemeData theme) {
    final cs = theme.colorScheme;
    switch (block.kind) {
      case BlockKind.todo:
        return _MarkerBox(
          width: 28,
          // Nur das Kästchen, nicht die ganze Zeile: die Zeile gehört jetzt der Einfügemarke.
          child: InkWell(
            onTap: widget.onToggleChecked,
            borderRadius: BorderRadius.circular(4),
            child: Icon(
              block.checked ? Icons.check_box : Icons.check_box_outline_blank,
              size: 20,
              color: block.checked ? cs.primary : cs.onSurfaceVariant,
            ),
          ),
        );
      case BlockKind.bullet:
        return _MarkerBox(
          width: 24,
          child: Container(
            width: 5,
            height: 5,
            margin: const EdgeInsets.only(left: 5),
            decoration: BoxDecoration(color: cs.onSurfaceVariant, shape: BoxShape.circle),
          ),
        );
      case BlockKind.numbered:
        return _MarkerBox(
          // Etwas breiter als die anderen: zweistellige Nummern müssen auch noch passen.
          width: 34,
          child: Text(
            '${widget.number ?? 1}.',
            style: theme.textTheme.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
          ),
        );
      case BlockKind.toggle:
        return _MarkerBox(
          width: 26,
          child: InkWell(
            onTap: widget.onToggleCollapsed,
            borderRadius: BorderRadius.circular(4),
            child: Icon(
              block.collapsed ? Icons.chevron_right : Icons.expand_more,
              size: 18,
              color: cs.primary,
            ),
          ),
        );
      default:
        return null;
    }
  }

  // ── Schrift und Abstände ────────────────────────────────────────────────────

  TextStyle _textStyle(ThemeData theme) {
    final cs = theme.colorScheme;
    final t = theme.textTheme;
    return switch (block.kind) {
      BlockKind.heading => switch (block.level) {
          1 => t.headlineSmall!,
          2 => t.titleLarge!,
          _ => t.titleMedium!,
        }
            .copyWith(fontWeight: FontWeight.bold, color: cs.onSurface),
      BlockKind.toggle =>
        t.bodyMedium!.copyWith(fontWeight: FontWeight.bold, color: cs.onSurface),
      BlockKind.todo => t.bodyMedium!.copyWith(
          height: 1.5,
          color: block.checked ? cs.onSurfaceVariant : cs.onSurface,
          decoration: block.checked ? TextDecoration.lineThrough : null,
        ),
      _ => t.bodyMedium!.copyWith(height: 1.5, color: cs.onSurface),
    };
  }

  EdgeInsets get _outerPadding => switch (block.kind) {
        BlockKind.heading => EdgeInsets.only(top: block.level == 1 ? 18 : 14, bottom: 4),
        _ => const EdgeInsets.symmetric(vertical: 3),
      };

  String get _placeholder => switch (block.kind) {
        BlockKind.heading => 'Überschrift',
        BlockKind.toggle => 'Frage',
        BlockKind.callout => 'Merksatz',
        _ => "Schreibe etwas, oder tippe '/' für Befehle",
      };

  // ── Sonderfälle ─────────────────────────────────────────────────────────────

  Widget _callout(ThemeData theme) => Container(
        margin: const EdgeInsets.symmetric(vertical: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest,
          border: Border(left: BorderSide(color: theme.colorScheme.primary, width: 3)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('💡', style: TextStyle(fontSize: 16)),
            const SizedBox(width: 10),
            Expanded(
              child: _field(theme, style: theme.textTheme.bodyMedium!.copyWith(height: 1.5)),
            ),
          ],
        ),
      );

  Widget _code(ThemeData theme) => Container(
        width: double.infinity,
        margin: const EdgeInsets.symmetric(vertical: 8),
        padding: const EdgeInsets.all(12),
        color: theme.colorScheme.surfaceContainerHighest,
        child: _field(
          theme,
          style: theme.textTheme.bodySmall!.copyWith(
            fontFamily: 'monospace',
            color: theme.colorScheme.onSurface,
          ),
          placeholder: 'Code',
        ),
      );

  // ── Das Feld ────────────────────────────────────────────────────────────────

  Widget _field(ThemeData theme, {required TextStyle style, String? placeholder}) {
    // Der eigene Focus liegt zwischen Feld und Wurzel und wird deshalb vor
    // DefaultTextEditingShortcuts gefragt — nur so lässt sich Enter abfangen, bevor daraus ein
    // Zeilenumbruch wird. Er darf den Fokus dabei selbst nicht annehmen.
    return Focus(
      canRequestFocus: false,
      skipTraversal: true,
      onKeyEvent: (_, event) => widget.onKey(event),
      child: CompositedTransformTarget(
        link: widget.link,
        child: TextField(
          key: widget.fieldKey,
          controller: widget.controller,
          focusNode: widget.focusNode,
          maxLines: null,
          style: style,
          cursorColor: theme.colorScheme.primary,
          onTap: widget.onTapText,
          decoration: NoteBlockRow.bareField.copyWith(
            // Nur am Block mit der Einfügemarke, sonst stünde die Aufforderung überall.
            hintText: widget.isActive ? (placeholder ?? _placeholder) : null,
            hintStyle: style.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ),
      ),
    );
  }
}

/// Hält das Vorzeichen auf fester Breite und auf Höhe der ersten Textzeile.
class _MarkerBox extends StatelessWidget {
  final double width;
  final Widget child;
  const _MarkerBox({required this.width, required this.child});

  @override
  Widget build(BuildContext context) => SizedBox(
        width: width,
        child: Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Align(alignment: Alignment.topLeft, child: child),
        ),
      );
}
