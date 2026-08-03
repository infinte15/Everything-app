import 'package:flutter/material.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';
import 'gym_stat_card.dart';

/// Die vier Kennzahlen der laufenden Woche.
class WeekStatsGrid extends StatelessWidget {
  final GymWeeklyStats stats;

  const WeekStatsGrid({super.key, required this.stats});

  @override
  Widget build(BuildContext context) {
    final goal = stats.workoutGoal;

    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: GymStatCard(
                label: 'Trainings',
                value: '${stats.workoutsCompleted}',
                sub: goal != null ? 'von $goal geplant' : 'diese Woche',
                progress: stats.goalProgress,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: GymStatCard(
                label: 'Volumen',
                value: _formatVolume(stats.totalVolumeKg),
                sub: '${stats.totalSets} Sätze',
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: GymStatCard(
                label: 'Dauer',
                value: _formatDuration(stats.totalMinutes),
                sub: 'Trainingszeit',
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: GymStatCard(
                label: 'Serie',
                value: '${stats.currentStreakWeeks}',
                sub: stats.currentStreakWeeks == 1 ? 'Woche' : 'Wochen',
                accent: LyftaTheme.primary,
              ),
            ),
          ],
        ),
      ],
    );
  }

  String _formatVolume(double kg) => gymFormatVolume(kg);

  String _formatDuration(int minutes) {
    if (minutes < 60) return '$minutes min';
    final hours = minutes ~/ 60;
    final rest = minutes % 60;
    return rest == 0 ? '$hours h' : '$hours h $rest';
  }
}
