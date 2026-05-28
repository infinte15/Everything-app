import 'package:flutter/material.dart';
import '../../../theme/lyfta_theme.dart';

class GymStatCard extends StatelessWidget {
  final String label;
  final String value;
  final String sub;
  final double? progress;
  final Color? accent;

  const GymStatCard({
    super.key,
    required this.label,
    required this.value,
    required this.sub,
    this.progress,
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(), style: LyftaTheme.label),
          const SizedBox(height: 6),
          Text(
            value,
            style: LyftaTheme.headline.copyWith(
              fontSize: 24,
              color: accent ?? LyftaTheme.textPrimary,
            ),
          ),
          Text(sub, style: LyftaTheme.caption),
          if (progress != null) ...[
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: progress!.clamp(0, 1),
                minHeight: 4,
                backgroundColor: LyftaTheme.surfaceElevated,
                color: LyftaTheme.primary,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
