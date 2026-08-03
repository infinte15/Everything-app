import 'package:flutter/material.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';
import 'exercise_muscle_figure.dart';

/// Routinen-Karte mit Titelbild, Umfang und beteiligten Muskelgruppen.
class RoutineCard extends StatelessWidget {
  final GymRoutine routine;
  final List<GymMuscleOption> muscleOptions;
  final VoidCallback onStart;
  final VoidCallback onTap;

  const RoutineCard({
    super.key,
    required this.routine,
    required this.muscleOptions,
    required this.onStart,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _Cover(routine: routine),
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            routine.name,
                            style: LyftaTheme.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (routine.dayLabel != null) _DayBadge(label: routine.dayLabel!),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(_subtitle(), style: LyftaTheme.subtitle),
                    if (routine.primaryMuscles.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      _MuscleChips(
                        muscles: routine.primaryMuscles,
                        options: muscleOptions,
                      ),
                    ],
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: onStart,
                        child: const Text('Training starten'),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _subtitle() {
    final parts = <String>[
      '${routine.exerciseCount} ${routine.exerciseCount == 1 ? 'Übung' : 'Übungen'}',
      '${routine.totalSets} Sätze',
    ];
    final minutes = routine.estimatedDurationMinutes;
    if (minutes != null && minutes > 0) {
      parts.add('ca. $minutes Min');
    }
    return parts.join(' · ');
  }
}

/// Titelbild: die Muskeln, die die Routine insgesamt trifft.
///
/// Früher eine Collage der ersten Übungsfotos - die gibt es nicht mehr, und aus
/// einer abgeleiteten Figur lässt sich nichts kacheln. Die Aggregat-Muskeln der
/// Routine sagen ohnehin mehr über sie aus als drei zufällige Übungsbilder.
class _Cover extends StatelessWidget {
  final GymRoutine routine;

  const _Cover({required this.routine});

  @override
  Widget build(BuildContext context) {
    return ExerciseMuscleFigureBanner(
      primaryMuscles: routine.primaryMuscles,
      height: 116,
    );
  }
}

class _DayBadge extends StatelessWidget {
  final String label;

  const _DayBadge({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: LyftaTheme.textSecondary,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _MuscleChips extends StatelessWidget {
  final List<String> muscles;
  final List<GymMuscleOption> options;

  const _MuscleChips({required this.muscles, required this.options});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: muscles.take(4).map((slug) {
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
          decoration: BoxDecoration(
            color: LyftaTheme.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Text(
            muscleLabel(slug, options),
            style: LyftaTheme.caption.copyWith(fontSize: 12),
          ),
        );
      }).toList(),
    );
  }
}

/// Deutsche Bezeichnung zu einem Muskel-Slug; fällt auf den Slug zurück.
String muscleLabel(String slug, List<GymMuscleOption> options) {
  for (final option in options) {
    if (option.slug == slug) return option.label;
  }
  return slug;
}
