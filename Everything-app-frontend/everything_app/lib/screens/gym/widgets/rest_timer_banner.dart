import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

/// Pausen-Timer über der Übungsliste: Restzeit als Ring, ±15 s und Überspringen.
class RestTimerBanner extends StatelessWidget {
  const RestTimerBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    if (!sports.isResting) return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LyftaTheme.divider),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 46,
            height: 46,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CircularProgressIndicator(
                  value: sports.restProgress,
                  strokeWidth: 3,
                  backgroundColor: LyftaTheme.surfaceElevated,
                  color: LyftaTheme.primary,
                ),
                const Icon(Icons.timer_outlined, size: 18, color: LyftaTheme.primary),
              ],
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('PAUSE', style: LyftaTheme.label.copyWith(color: LyftaTheme.primary)),
                const SizedBox(height: 2),
                Text(
                  formatSeconds(sports.restSecondsRemaining),
                  style: LyftaTheme.headline.copyWith(fontSize: 26),
                ),
                if (sports.restExerciseName != null)
                  Text(
                    'nach ${sports.restExerciseName}',
                    style: LyftaTheme.caption,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
          _RestButton(label: '-15', onTap: () => sports.adjustRest(-15)),
          const SizedBox(width: 6),
          _RestButton(label: '+15', onTap: () => sports.adjustRest(15)),
          const SizedBox(width: 6),
          _RestButton(label: 'Weiter', onTap: sports.skipRest, filled: true),
        ],
      ),
    );
  }
}

class _RestButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final bool filled;

  const _RestButton({required this.label, required this.onTap, this.filled = false});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: filled ? LyftaTheme.primary : LyftaTheme.surfaceElevated,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: filled ? LyftaTheme.onPrimary : LyftaTheme.textPrimary,
          ),
        ),
      ),
    );
  }
}

/// mm:ss, ab einer Stunde h:mm:ss - ohne die Stunden würde eine lange Einheit
/// wieder bei null anfangen.
String formatSeconds(int totalSeconds) {
  final seconds = totalSeconds.abs();
  final hours = seconds ~/ 3600;
  final minutes = (seconds % 3600) ~/ 60;
  final secs = seconds % 60;
  final mm = minutes.toString().padLeft(2, '0');
  final ss = secs.toString().padLeft(2, '0');
  return hours > 0 ? '$hours:$mm:$ss' : '$mm:$ss';
}
