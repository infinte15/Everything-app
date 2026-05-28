import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../workout/active_workout_page.dart';

class GymQuickStartSheet extends StatelessWidget {
  const GymQuickStartSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => const GymQuickStartSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final plans = sports.workoutPlans;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: LyftaTheme.surfaceHighlight,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 20),
            Text('Start Workout', style: LyftaTheme.title),
            const SizedBox(height: 20),
            _StartTile(
              icon: Icons.bolt,
              title: 'Start Empty Workout',
              subtitle: 'Add exercises as you go',
              onTap: () async {
                Navigator.pop(context);
                await sports.startEmptyWorkout();
                if (context.mounted) {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()),
                  );
                }
              },
            ),
            const SizedBox(height: 12),
            Text('My Routines', style: LyftaTheme.caption),
            const SizedBox(height: 8),
            if (plans.isEmpty)
              Text('No routines yet — create one in Workout tab',
                  style: LyftaTheme.subtitle)
            else
              ...plans.map((p) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _StartTile(
                    icon: Icons.play_circle_fill,
                    iconColor: LyftaTheme.primary,
                    title: p['name'] as String? ?? '',
                    subtitle:
                        '${p['day']} · ${p['estimatedDuration']} min · ${(p['exercises'] as List).length} exercises',
                    onTap: () async {
                      Navigator.pop(context);
                      final ok = await sports.startWorkout(p['id'] as int);
                      if (ok && context.mounted) {
                        Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => const ActiveWorkoutPage(),
                          ),
                        );
                      }
                    },
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }
}

class _StartTile extends StatelessWidget {
  final IconData icon;
  final Color? iconColor;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _StartTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.iconColor,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: LyftaTheme.surfaceElevated,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(icon, color: iconColor ?? LyftaTheme.textPrimary, size: 28),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: LyftaTheme.title.copyWith(fontSize: 16)),
                    const SizedBox(height: 2),
                    Text(subtitle, style: LyftaTheme.subtitle),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: LyftaTheme.textTertiary),
            ],
          ),
        ),
      ),
    );
  }
}
