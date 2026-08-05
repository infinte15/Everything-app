import 'package:flutter/material.dart';

class StudyKineticCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final Color? backgroundColor;

  /// Farbiger Rahmen, z.B. die Modulfarbe. Ohne Wert bleibt die Karte randlos — randlos und
  /// eckig ist der Normalfall von Kinetic Mono, der Rahmen die bewusste Ausnahme.
  final Color? borderColor;

  /// Strichstärke des Rahmens.
  ///
  /// Ein dickerer Rahmen vergrößert die Karte **nicht**: `Material(shape:)` malt über einen
  /// `CustomPaint` mit `foregroundPainter`, der keine Layoutwirkung hat, und `Border.paint`
  /// strichelt nach innen. Der Rahmen frisst also Innenabstand, keine Fläche — hier ist kein
  /// Ausgleich am Padding nötig.
  final double borderWidth;

  const StudyKineticCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(20),
    this.onTap,
    this.backgroundColor,
    this.borderColor,
    this.borderWidth = 1,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = backgroundColor ?? theme.colorScheme.surfaceContainerHighest;

    final content = InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.zero,
      child: Padding(
        padding: padding,
        child: child,
      ),
    );

    // Material verbietet borderRadius und shape gleichzeitig, deshalb zwei Zweige statt
    // eines Parameters. Border ist ein ShapeBorder und bleibt eckig — die Karte sieht also
    // aus wie zuvor, nur mit Kante.
    if (borderColor == null) {
      return Material(color: color, borderRadius: BorderRadius.zero, child: content);
    }
    return Material(
      color: color,
      shape: Border.all(color: borderColor!, width: borderWidth),
      child: content,
    );
  }
}
