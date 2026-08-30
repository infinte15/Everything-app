import 'package:flutter/material.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';

/// Die Vorgabe für heute samt Begründung, über dem Satzraster.
///
/// Eine Zahl ohne Begründung wird im Training ignoriert - deshalb steht der Satz aus
/// `ProgressionSuggestionDTO.why` gleichberechtigt daneben und nicht hinter einem Info-Symbol.
///
/// Farbe trägt nur die Steigerung: nach der Gestaltungslinie des Space bekommt Akzentfarbe
/// nur, was auch etwas bedeutet. Halten und Deload bleiben grau - ein Rückschritt muss nicht
/// zusätzlich angemalt werden.
class ProgressionBanner extends StatelessWidget {
  final GymProgressionSuggestion suggestion;

  /// Rampe einfügen. Null, wenn es nichts einzufügen gibt (oder schon Aufwärmsätze stehen).
  final VoidCallback? onApplyWarmup;

  const ProgressionBanner({
    super.key,
    required this.suggestion,
    this.onApplyWarmup,
  });

  IconData get _icon {
    switch (suggestion.kind) {
      case GymProgressionKind.up:
        return Icons.trending_up_rounded;
      case GymProgressionKind.deload:
        return Icons.trending_down_rounded;
      case GymProgressionKind.hold:
        return Icons.trending_flat_rounded;
      case GymProgressionKind.first:
        return Icons.flag_rounded;
      case GymProgressionKind.off:
        return Icons.remove_rounded;
    }
  }

  Color get _color => suggestion.kind == GymProgressionKind.up
      ? LyftaTheme.primary
      : LyftaTheme.textSecondary;

  @override
  Widget build(BuildContext context) {
    final ramp = suggestion.warmup;

    return Container(
      margin: const EdgeInsets.fromLTRB(12, 10, 12, 0),
      padding: const EdgeInsets.all(11),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(_icon, size: 16, color: _color),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      suggestion.headline,
                      style: LyftaTheme.title.copyWith(fontSize: 14, color: _color),
                    ),
                    if (suggestion.why.isNotEmpty) ...[
                      const SizedBox(height: 3),
                      Text(suggestion.why, style: LyftaTheme.caption.copyWith(fontSize: 12)),
                    ],
                  ],
                ),
              ),
            ],
          ),
          if (ramp.isNotEmpty && onApplyWarmup != null) ...[
            const SizedBox(height: 9),
            const Divider(height: 1, color: LyftaTheme.divider),
            const SizedBox(height: 7),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('AUFWÄRMEN', style: LyftaTheme.label),
                      const SizedBox(height: 2),
                      Text(
                        ramp
                            .map((w) => '${gymFormatNumber(w.weight)} × ${w.reps}')
                            .join('  ·  '),
                        style: LyftaTheme.caption.copyWith(fontSize: 12),
                      ),
                    ],
                  ),
                ),
                TextButton(
                  onPressed: onApplyWarmup,
                  child: const Text('Einfügen'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
