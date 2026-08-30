import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import 'exercise_media.dart';
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
  String? _note;
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
    final note = await sports.loadExerciseNote(widget.exercise.id);
    if (!mounted) return;
    setState(() {
      _records = records;
      _history = history;
      _note = note;
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
            ExerciseAnimation(
              animationUrl: exercise.animationUrl,
              imageUrl: exercise.imageUrl,
              primaryMuscles: exercise.primaryMuscles,
              secondaryMuscles: exercise.secondaryMuscles,
              height: 220,
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
            // Die eigene Notiz steht vor der Anleitung aus dem Katalog: was man sich selbst
            // aufgeschrieben hat, ist beim Nachschlagen fast immer das Gesuchte.
            _noteSection(),
            // Die gezeichnete Figur behält ihren Platz: die Animation zeigt die Bewegung,
            // sie zeigt, welche Muskeln dabei arbeiten. Zwei verschiedene Fragen.
            ..._executionSection(exercise),
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
            const GymVisualAttribution(),
          ],
        );
      },
    );
  }

  /// Stehende Notiz zur Übung - gilt bei jedem Training, unabhängig von der Routine.
  Widget _noteSection() {
    final note = _note;

    return Padding(
      padding: const EdgeInsets.only(bottom: 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('Meine Notiz', style: LyftaTheme.title),
              const Spacer(),
              TextButton(
                onPressed: _editNote,
                child: Text(note == null ? 'Hinzufügen' : 'Ändern'),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: LyftaTheme.surfaceElevated,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              note ??
                  'Was du dir hier notierst, steht bei jedem Training dieser Übung dabei - '
                      'Sitzhöhe, Griffbreite, worauf du achten willst.',
              style: note == null
                  ? LyftaTheme.caption
                  : LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _editNote() async {
    final controller = TextEditingController(text: _note ?? '');
    final sports = context.read<SportsProvider>();

    final text = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: Text('Notiz', style: LyftaTheme.title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 4,
          maxLength: 1000,
          style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary),
          decoration: const InputDecoration(hintText: 'Bank auf Stufe 3, Griff eng …'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Abbrechen'),
          ),
          // Leeren speichern heißt löschen - dafür braucht es keinen eigenen Knopf.
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, controller.text),
            child: const Text('Speichern'),
          ),
        ],
      ),
    );
    if (text == null) return;

    final ok = await sports.saveExerciseNote(widget.exercise.id, text);
    if (!mounted) return;
    if (ok) {
      setState(() => _note = text.trim().isEmpty ? null : text.trim());
    }
  }

  /// Ausführung: die Schritte aus dem Katalog, daneben die beanspruchte Muskulatur.
  ///
  /// Die Anleitungstexte sind englisch - so kommen sie aus dem Übungsdatensatz, und eine
  /// maschinelle Übersetzung von Fachbegriffen wie "scapular retraction" wäre schlechter
  /// als das Original.
  List<Widget> _executionSection(GymExercise exercise) {
    final steps = (exercise.instructions ?? '')
        .split(RegExp(r'\n\s*\n'))
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toList();
    if (steps.isEmpty) return const [];

    return [
      Text('Ausführung', style: LyftaTheme.title),
      const SizedBox(height: 10),
      Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (var i = 0; i < steps.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 20,
                          child: Text(
                            '${i + 1}.',
                            style: LyftaTheme.caption
                                .copyWith(color: LyftaTheme.textTertiary),
                          ),
                        ),
                        Expanded(child: Text(steps[i], style: LyftaTheme.subtitle)),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          ExerciseMuscleFigure(
            primaryMuscles: exercise.primaryMuscles,
            secondaryMuscles: exercise.secondaryMuscles,
            size: 96,
            side: BodySide.auto,
          ),
        ],
      ),
      const SizedBox(height: 22),
    ];
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
