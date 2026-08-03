import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import 'exercise_muscle_figure.dart';
import 'exercise_progress_chart.dart';
import 'routine_card.dart' show muscleLabel;

/// Detailansicht einer Übung: Bild, Anleitung, Bestleistungen und Verlauf.
class ExerciseDetailSheet extends StatefulWidget {
  final GymExercise exercise;

  const ExerciseDetailSheet({super.key, required this.exercise});

  static Future<void> show(BuildContext context, GymExercise exercise) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: LyftaTheme.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => ExerciseDetailSheet(exercise: exercise),
    );
  }

  @override
  State<ExerciseDetailSheet> createState() => _ExerciseDetailSheetState();
}

class _ExerciseDetailSheetState extends State<ExerciseDetailSheet> {
  GymPersonalRecord? _records;
  List<GymHistoryEntry> _history = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final sports = context.read<SportsProvider>();
    final records = await sports.personalRecords(widget.exercise.id);
    List<GymHistoryEntry> history = const [];
    try {
      // Mehr als die Liste braucht, aber das Diagramm darunter lebt davon.
      // Der Server deckelt selbst bei 50.
      history = await sports.exerciseHistory(widget.exercise.id, limit: 50);
    } catch (_) {
      // Verlauf ist optional - die Übung selbst bleibt trotzdem sichtbar.
    }
    if (!mounted) return;
    setState(() {
      _records = records;
      _history = history;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final exercise = widget.exercise;
    final options = context.watch<SportsProvider>().muscleOptions;

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.85,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      builder: (context, controller) {
        return ListView(
          controller: controller,
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
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
            const SizedBox(height: 18),
            ExerciseMuscleFigureBanner(
              primaryMuscles: exercise.primaryMuscles,
              secondaryMuscles: exercise.secondaryMuscles,
              height: 200,
            ),
            const SizedBox(height: 18),
            Text(exercise.name, style: LyftaTheme.headline.copyWith(fontSize: 24)),
            const SizedBox(height: 6),
            Text(
              [
                if (exercise.equipment.isNotEmpty) exercise.equipment,
                if (exercise.difficulty.isNotEmpty) _difficultyLabel(exercise.difficulty),
              ].join(' · '),
              style: LyftaTheme.subtitle,
            ),
            const SizedBox(height: 16),
            if (exercise.primaryMuscles.isNotEmpty ||
                exercise.secondaryMuscles.isNotEmpty)
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  ...exercise.primaryMuscles.map(
                    (m) => _muscleChip(muscleLabel(m, options), primary: true),
                  ),
                  ...exercise.secondaryMuscles.map(
                    (m) => _muscleChip(muscleLabel(m, options), primary: false),
                  ),
                ],
              ),
            const SizedBox(height: 22),
            if (_loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 20),
                  child: CircularProgressIndicator(color: LyftaTheme.primary),
                ),
              )
            else ...[
              if (_records?.hasData == true) ...[
                _RecordCard(records: _records!),
                const SizedBox(height: 22),
              ],
              // Bestleistungen und Fortschritt beantworten beide "wie stehe ich
              // da"; die Ausführung ist Nachschlagewerk und kommt danach.
              if (_history.isNotEmpty) ...[
                Text('Fortschritt', style: LyftaTheme.title),
                const SizedBox(height: 10),
                ExerciseProgressChart(history: _history),
                const SizedBox(height: 22),
              ],
              Text('Verlauf', style: LyftaTheme.title),
              const SizedBox(height: 10),
              if (_history.isEmpty)
                Text(
                  'Diese Übung wurde noch nicht protokolliert.',
                  style: LyftaTheme.subtitle,
                )
              else
                ..._history.take(8).map(_historyRow),
            ],
          ],
        );
      },
    );
  }

  Widget _historyRow(GymHistoryEntry entry) {
    final date = entry.performedAt;
    final sets = entry.sets.where((s) => s.isCompleted).toList();

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  date != null
                      ? DateFormat('d. MMMM yyyy', 'de_DE').format(date)
                      : entry.sessionName,
                  style: LyftaTheme.title.copyWith(fontSize: 14),
                ),
              ),
              Text('${entry.totalVolumeKg.round()} kg', style: LyftaTheme.caption),
            ],
          ),
          if (sets.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: sets
                  .map((s) => Container(
                        padding:
                            const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: LyftaTheme.surfaceElevated,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          s.summary,
                          style: LyftaTheme.caption.copyWith(fontSize: 12),
                        ),
                      ))
                  .toList(),
            ),
          ],
        ],
      ),
    );
  }

  /// Dieselbe Rot/Blau-Codierung wie die Figur darüber - sonst müsste man sich
  /// zweimal merken, was "beansprucht" heißt.
  Widget _muscleChip(String label, {required bool primary}) {
    final color =
        primary ? LyftaTheme.musclePrimary : LyftaTheme.muscleSecondary;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: LyftaTheme.caption.copyWith(color: color, fontSize: 12),
      ),
    );
  }

  String _difficultyLabel(String value) {
    switch (value.toLowerCase()) {
      case 'beginner':
        return 'Einsteiger';
      case 'intermediate':
        return 'Fortgeschritten';
      case 'expert':
        return 'Profi';
      default:
        return value;
    }
  }
}

class _RecordCard extends StatelessWidget {
  final GymPersonalRecord records;

  const _RecordCard({required this.records});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LyftaTheme.prAccent.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LyftaTheme.prAccent.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.emoji_events_rounded,
                  size: 18, color: LyftaTheme.prAccent),
              const SizedBox(width: 8),
              Text('BESTLEISTUNGEN',
                  style: LyftaTheme.label.copyWith(color: LyftaTheme.prAccent)),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              _stat(
                'Gewicht',
                records.maxWeight != null
                    ? '${gymFormatNumber(records.maxWeight!)} kg'
                    : '—',
              ),
              _stat('Wdh.', records.maxReps?.toString() ?? '—'),
              _stat(
                'Geschätztes 1RM',
                records.best1RM != null ? '${records.best1RM!.round()} kg' : '—',
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text('${records.totalSetsAllTime} Sätze insgesamt', style: LyftaTheme.caption),
        ],
      ),
    );
  }

  Widget _stat(String label, String value) => Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label.toUpperCase(), style: LyftaTheme.label),
            const SizedBox(height: 3),
            Text(value, style: LyftaTheme.title.copyWith(fontSize: 16)),
          ],
        ),
      );
}
