import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';

/// Bewertung als Sterne.
///
/// Periwinkle statt Gold: der Space kennt genau einen Akzent, und der Stern
/// steht hier für dieselbe Art Aussage wie jede andere aktive Stelle. Ein
/// zweiter Farbton wäre Dekoration.
///
/// `null` als [rating] heißt "nicht bewertet" - dann sind alle Sterne leer und
/// grau. Das ist etwas anderes als eine Bewertung mit einem Stern.
class RatingStars extends StatelessWidget {
  const RatingStars({
    super.key,
    required this.rating,
    this.size = 14,
    this.onRate,
  });

  final int? rating;
  final double size;

  /// Ohne Rückruf ist die Anzeige passiv - so steht sie auf Karten.
  final ValueChanged<int>? onRate;

  @override
  Widget build(BuildContext context) {
    final value = rating ?? 0;

    return Semantics(
      label: rating == null ? 'Nicht bewertet' : '$rating von 5 Sternen',
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var star = 1; star <= 5; star++)
            GestureDetector(
              onTap: onRate == null ? null : () => onRate!(star),
              behavior: HitTestBehavior.opaque,
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: onRate == null ? 0.5 : 4),
                child: Icon(
                  star <= value ? Icons.star_rounded : Icons.star_outline_rounded,
                  size: size,
                  color: star <= value
                      ? KineticTheme.primary
                      : KineticTheme.textTertiary,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
