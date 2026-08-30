import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/create_routine_sheet.dart';
import '../widgets/exercise_media.dart';
import '../widgets/routine_card.dart' show muscleLabel;
import '../workout/active_workout_page.dart';

/// Übungsliste einer Routine mit Zielvorgaben.
class RoutineDetailPage extends StatefulWidget {
  final int routineId;

  const RoutineDetailPage({super.key, required this.routineId});

  @override
  State<RoutineDetailPage> createState() => _RoutineDetailPageState();
}

class _RoutineDetailPageState extends State<RoutineDetailPage> {
  GymRoutine? _routine;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final routine = await context.read<SportsProvider>().loadRoutineDetail(widget.routineId);
    if (!mounted) return;
    setState(() {
      _routine = routine;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final routine = _routine;

    return Theme(
      data: LyftaTheme.darkTheme,
      child: Scaffold(
        backgroundColor: LyftaTheme.background,
        appBar: AppBar(
          title: Text(routine?.name ?? 'Routine'),
          actions: [
            if (routine != null)
              IconButton(
                icon: const Icon(Icons.edit_outlined),
                tooltip: 'Bearbeiten',
                onPressed: () async {
                  await CreateRoutineSheet.show(context, existing: routine);
                  if (mounted) _load();
                },
              ),
            if (routine != null)
              IconButton(
                icon: const Icon(Icons.delete_outline_rounded),
                tooltip: 'Löschen',
                onPressed: () => _confirmDelete(routine),
              ),
          ],
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator(color: LyftaTheme.primary))
            : routine == null
                ? Center(
                    child: Text('Routine nicht gefunden', style: LyftaTheme.subtitle),
                  )
                : _content(routine),
        bottomNavigationBar: routine == null
            ? null
            : SafeArea(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                  child: FilledButton.icon(
                    onPressed: () => _start(routine.id),
                    icon: const Icon(Icons.play_arrow_rounded),
                    label: const Text('Training starten'),
                  ),
                ),
              ),
      ),
    );
  }

  Widget _content(GymRoutine routine) {
    final options = context.watch<SportsProvider>().muscleOptions;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        Row(
          children: [
            _summary('Übungen', '${routine.exercises.length}'),
            _summary('Sätze', '${routine.totalSets}'),
            _summary(
              'Dauer',
              routine.estimatedDurationMinutes != null
                  ? '${routine.estimatedDurationMinutes} min'
                  : '—',
            ),
          ],
        ),
        if (routine.performCount > 0) ...[
          const SizedBox(height: 12),
          Text(
            '${routine.performCount}× absolviert',
            style: LyftaTheme.caption,
          ),
        ],
        const SizedBox(height: 22),
        Text('Übungen', style: LyftaTheme.headline.copyWith(fontSize: 20)),
        const SizedBox(height: 12),
        if (routine.exercises.isEmpty)
          Text('Diese Routine hat noch keine Übungen.', style: LyftaTheme.subtitle)
        else
          ...routine.exercises.map((e) => _ExerciseRow(item: e, options: options)),
      ],
    );
  }

  Widget _summary(String label, String value) => Expanded(
        child: Container(
          margin: const EdgeInsets.only(right: 10),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: LyftaTheme.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label.toUpperCase(), style: LyftaTheme.label),
              const SizedBox(height: 4),
              Text(value, style: LyftaTheme.headline.copyWith(fontSize: 20)),
            ],
          ),
        ),
      );

  Future<void> _start(int routineId) async {
    final sports = context.read<SportsProvider>();
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);

    if (sports.hasActiveWorkout) {
      navigator.push(MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()));
      return;
    }

    final started = await sports.startRoutine(routineId);
    if (!started) {
      messenger.showSnackBar(
        SnackBar(content: Text(sports.error ?? 'Training konnte nicht gestartet werden')),
      );
      return;
    }
    navigator.pushReplacement(
      MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()),
    );
  }

  Future<void> _confirmDelete(GymRoutine routine) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: const Text('Routine löschen?'),
        content: Text(
          '"${routine.name}" wird entfernt. Bereits aufgezeichnete Trainings bleiben erhalten.',
          style: LyftaTheme.subtitle,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Abbrechen'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Löschen', style: TextStyle(color: LyftaTheme.danger)),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    final navigator = Navigator.of(context);
    final removed = await context.read<SportsProvider>().deleteRoutine(routine.id);
    if (removed && mounted) navigator.pop();
  }
}

class _ExerciseRow extends StatelessWidget {
  final GymRoutineExercise item;
  final List<GymMuscleOption> options;

  const _ExerciseRow({required this.item, required this.options});

  @override
  Widget build(BuildContext context) {
    final muscles = item.primaryMuscles
        .take(2)
        .map((m) => muscleLabel(m, options))
        .join(', ');

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          ExerciseThumb(
            imageUrl: item.imageUrl,
            primaryMuscles: item.primaryMuscles,
            secondaryMuscles: item.secondaryMuscles,
            size: 52,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.exerciseName,
                  style: LyftaTheme.title.copyWith(fontSize: 15),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 3),
                Text(
                  [
                    if (muscles.isNotEmpty) muscles,
                    if (item.equipment.isNotEmpty) item.equipment,
                  ].join(' · '),
                  style: LyftaTheme.caption,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                item.repRange != null
                    ? '${item.targetSets} × ${item.repRange}'
                    : '${item.targetSets} Sätze',
                style: LyftaTheme.title.copyWith(fontSize: 14),
              ),
              if (item.restSeconds != null)
                Text('${item.restSeconds}s Pause', style: LyftaTheme.label),
            ],
          ),
        ],
      ),
    );
  }
}
