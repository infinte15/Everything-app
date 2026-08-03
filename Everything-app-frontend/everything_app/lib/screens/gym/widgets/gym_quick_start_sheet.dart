import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../workout/active_workout_page.dart';
import 'exercise_muscle_figure.dart';

/// Schnellstart: leeres Training oder eine der eigenen Routinen.
class GymQuickStartSheet extends StatelessWidget {
  const GymQuickStartSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
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

    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.75,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 10),
            Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: LyftaTheme.surfaceHighlight,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 6),
              child: Row(
                children: [
                  Text(
                    'Training starten',
                    style: LyftaTheme.headline.copyWith(fontSize: 22),
                  ),
                ],
              ),
            ),
            Flexible(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
                shrinkWrap: true,
                children: [
                  _StartTile(
                    icon: Icons.add_rounded,
                    title: 'Leeres Training',
                    subtitle: 'Übungen unterwegs hinzufügen',
                    onTap: () => _start(context, null),
                  ),
                  if (sports.routines.isNotEmpty) ...[
                    const SizedBox(height: 20),
                    Text('MEINE ROUTINEN', style: LyftaTheme.label),
                    const SizedBox(height: 10),
                    ...sports.routines.map(
                      (routine) => _RoutineTile(
                        routine: routine,
                        onTap: () => _start(context, routine.id),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _start(BuildContext context, int? routineId) async {
    final sports = context.read<SportsProvider>();
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);

    navigator.pop();

    final started = routineId == null
        ? await sports.startEmptyWorkout()
        : await sports.startRoutine(routineId);

    if (!started) {
      messenger.showSnackBar(
        SnackBar(content: Text(sports.error ?? 'Training konnte nicht gestartet werden')),
      );
      return;
    }
    navigator.push(MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()));
  }
}

class _StartTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _StartTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: LyftaTheme.surfaceHighlight,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(icon, color: LyftaTheme.primary),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: LyftaTheme.title.copyWith(fontSize: 15)),
                      const SizedBox(height: 2),
                      Text(subtitle, style: LyftaTheme.caption),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded, color: LyftaTheme.textTertiary),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RoutineTile extends StatelessWidget {
  final GymRoutine routine;
  final VoidCallback onTap;

  const _RoutineTile({required this.routine, required this.onTap});

  @override
  Widget build(BuildContext context) {
    // Alle Angaben kommen aus dem Routinen-DTO - früher stand hier ein Zugriff auf
    // Felder, die der Trainingsplan gar nicht liefert.
    final parts = <String>[
      '${routine.exerciseCount} ${routine.exerciseCount == 1 ? 'Übung' : 'Übungen'}',
      '${routine.totalSets} Sätze',
      if (routine.estimatedDurationMinutes != null)
        'ca. ${routine.estimatedDurationMinutes} Min',
    ];

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Row(
              children: [
                ExerciseMuscleFigure(
                  primaryMuscles: routine.primaryMuscles,
                  size: 48,
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        routine.name,
                        style: LyftaTheme.title.copyWith(fontSize: 15),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(parts.join(' · '), style: LyftaTheme.caption),
                    ],
                  ),
                ),
                const Icon(Icons.play_arrow_rounded, color: LyftaTheme.primary),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
