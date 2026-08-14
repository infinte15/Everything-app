import 'package:flutter/material.dart';

import '../../../../utils/note_markup.dart';

/// Zeichnet `**fett**`, `*kursiv*`, `` `code` `` und `==markiert==` **während** des Tippens.
///
/// Die Auszeichnungszeichen bleiben stehen und werden nur gedimmt — [editorSpans] erklärt,
/// warum sie nicht verschwinden dürfen.
class NoteInlineController extends TextEditingController {
  /// Ob dieser Block überhaupt formatiert wird. In einem Codeblock ist `**` genau das, was
  /// dasteht; die Abfrage läuft über den Wirt, weil sich die Blockart ändern kann, ohne dass
  /// der Controller ausgetauscht wird.
  final bool Function() formatted;

  NoteInlineController({required this.formatted, super.text});

  @override
  TextSpan buildTextSpan({
    required BuildContext context,
    TextStyle? style,
    required bool withComposing,
  }) {
    // Solange eine Eingabe komponiert wird, bleibt es beim Standard: sonst verlieren tote
    // Tasten unter Linux (¨ + u → ü) und jede CJK-Eingabe die Unterstreichung ihrer
    // Kompositionszone.
    if (text.isEmpty ||
        !formatted() ||
        (withComposing && value.isComposingRangeValid)) {
      return super.buildTextSpan(
        context: context,
        style: style,
        withComposing: withComposing,
      );
    }

    final base = style ?? const TextStyle();
    final scheme = Theme.of(context).colorScheme;
    return TextSpan(
      style: base,
      children: editorSpans(
        text,
        base,
        codeBackground: scheme.surfaceContainerHighest,
        highlightBackground: const Color(0xFFFF9F0A).withValues(alpha: 0.35),
        markerColor: (base.color ?? scheme.onSurface).withValues(alpha: 0.35),
      ),
    );
  }
}
