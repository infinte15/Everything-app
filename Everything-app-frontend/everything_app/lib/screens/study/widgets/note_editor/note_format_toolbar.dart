import 'package:flutter/material.dart';

/// Die Auszeichnungen, die sich auf eine Textauswahl legen lassen.
enum InlineFormat {
  bold('**'),
  italic('*'),
  code('`'),
  highlight('==');

  /// Was links und rechts um die Auswahl geschrieben wird.
  final String marker;
  const InlineFormat(this.marker);
}

/// Die kleine Leiste über einer Textauswahl.
///
/// Erscheint von selbst, sobald etwas markiert ist — nicht erst auf Rechtsklick, wie es
/// Flutters eigene Auswahlleiste täte.
class NoteFormatToolbar extends StatelessWidget {
  final ValueChanged<InlineFormat> onApply;

  const NoteFormatToolbar({super.key, required this.onApply});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      elevation: 8,
      borderRadius: BorderRadius.zero,
      child: Container(
        decoration: BoxDecoration(border: Border.all(color: theme.colorScheme.outlineVariant)),
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 2),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _Button(
              tooltip: 'Fett (Strg+B)',
              onTap: () => onApply(InlineFormat.bold),
              child: const Text('B', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            _Button(
              tooltip: 'Kursiv (Strg+I)',
              onTap: () => onApply(InlineFormat.italic),
              child: const Text('I',
                  style: TextStyle(fontStyle: FontStyle.italic, fontFamily: 'serif')),
            ),
            _Button(
              tooltip: 'Code (Strg+E)',
              onTap: () => onApply(InlineFormat.code),
              child: const Icon(Icons.code, size: 16),
            ),
            _Button(
              tooltip: 'Markiert (Strg+H)',
              onTap: () => onApply(InlineFormat.highlight),
              child: const Icon(Icons.border_color_outlined, size: 14),
            ),
          ],
        ),
      ),
    );
  }
}

class _Button extends StatelessWidget {
  final String tooltip;
  final VoidCallback onTap;
  final Widget child;

  const _Button({required this.tooltip, required this.onTap, required this.child});

  @override
  Widget build(BuildContext context) => Tooltip(
        message: tooltip,
        waitDuration: const Duration(milliseconds: 500),
        child: InkWell(
          onTap: onTap,
          child: SizedBox(
            width: 32,
            height: 30,
            child: Center(
              child: DefaultTextStyle.merge(
                style: TextStyle(
                  fontSize: 14,
                  color: Theme.of(context).colorScheme.onSurface,
                ),
                child: child,
              ),
            ),
          ),
        ),
      );
}
