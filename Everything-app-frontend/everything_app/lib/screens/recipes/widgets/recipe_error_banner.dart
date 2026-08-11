import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';

/// Zeigt die Meldung des Servers und bietet an, es noch einmal zu versuchen.
///
/// Der Rezept-Space hatte so etwas bisher nicht: `provider.error` wurde von
/// keinem einzigen Screen gelesen. Ein Server, der nicht antwortet, sah damit
/// genauso aus wie ein leeres Kochbuch.
///
/// Die Fläche ist [KineticTheme.surfaceElevated] und nicht rot. Rot ist im
/// Kinetic Mono für Zerstörerisches reserviert - ein fehlgeschlagener Abruf ist
/// ärgerlich, aber nichts ist kaputtgegangen.
class RecipeErrorBanner extends StatelessWidget {
  const RecipeErrorBanner({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(20, 12, 20, 4),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      color: KineticTheme.surfaceElevated,
      child: Row(
        children: [
          const Icon(Icons.cloud_off_outlined,
              size: 18, color: KineticTheme.textTertiary),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              message,
              style: KineticTheme.caption,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (onRetry != null) ...[
            const SizedBox(width: 8),
            GestureDetector(
              onTap: onRetry,
              child: Text(
                'Wiederholen',
                style: KineticTheme.caption.copyWith(color: KineticTheme.primary),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
