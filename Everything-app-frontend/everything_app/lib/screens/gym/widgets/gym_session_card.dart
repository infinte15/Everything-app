import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../theme/lyfta_theme.dart';

class GymSessionCard extends StatelessWidget {
  final Map<String, dynamic> session;

  const GymSessionCard({super.key, required this.session});

  @override
  Widget build(BuildContext context) {
    final name = session['name'] as String? ?? 'Workout';
    final date = session['date'] as DateTime? ?? DateTime.now();
    final duration = session['durationMinutes'] as int? ?? 0;
    final volume = (session['totalVolumeKg'] as num?)?.toDouble() ?? 0;
    final sets = session['totalSets'] as int? ?? 0;
    final exercises = session['exercises'] as List? ?? [];
    final muscleHint = exercises
        .take(3)
        .map((e) => (e as Map)['name'])
        .join(', ');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: LyftaTheme.surfaceElevated,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.fitness_center, color: LyftaTheme.primary, size: 22),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(name, style: LyftaTheme.title),
                    if (muscleHint.isNotEmpty)
                      Text(
                        muscleHint,
                        style: LyftaTheme.subtitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                  ],
                ),
              ),
              Text(
                DateFormat('d MMM', 'de_DE').format(date),
                style: LyftaTheme.caption,
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(color: LyftaTheme.divider, height: 1),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _metric('Volume', '${volume.round()} kg'),
              _metric('Time', '$duration min'),
              _metric('Sets', '$sets'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _metric(String label, String value) {
    return Column(
      children: [
        Text(label.toUpperCase(), style: LyftaTheme.label),
        const SizedBox(height: 4),
        Text(value, style: LyftaTheme.title.copyWith(fontSize: 15)),
      ],
    );
  }
}
