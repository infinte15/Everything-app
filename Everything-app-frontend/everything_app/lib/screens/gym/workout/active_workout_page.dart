import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/exercise_picker_sheet.dart';
import '../widgets/exercise_muscle_figure.dart';
import '../widgets/rest_seconds_picker.dart';
import '../widgets/rest_timer_banner.dart';

/// Das laufende Training: Sätze abhaken, Pausen laufen automatisch.
class ActiveWorkoutPage extends StatefulWidget {
  const ActiveWorkoutPage({super.key});

  @override
  State<ActiveWorkoutPage> createState() => _ActiveWorkoutPageState();
}

class _ActiveWorkoutPageState extends State<ActiveWorkoutPage> {
  Timer? _timer;
  String _elapsed = '00:00';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _tick());
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    if (!mounted) return;
    final workout = context.read<SportsProvider>().activeWorkout;
    if (workout == null) return;
    // formatSeconds bringt ab einer Stunde die Stunden mit - ohne das würde eine
    // lange Einheit wieder bei 00:xx anfangen.
    final text = formatSeconds(workout.elapsed.inSeconds);
    if (text != _elapsed) {
      setState(() => _elapsed = text);
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final workout = sports.activeWorkout;

    if (workout == null) {
      return Theme(
        data: LyftaTheme.darkTheme,
        child: Scaffold(
          backgroundColor: LyftaTheme.background,
          appBar: AppBar(),
          body: Center(
            child: Text('Kein laufendes Training', style: LyftaTheme.subtitle),
          ),
        ),
      );
    }

    final stats = sports.activeWorkoutStats;

    return Theme(
      data: LyftaTheme.darkTheme,
      child: Scaffold(
        backgroundColor: LyftaTheme.background,
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.keyboard_arrow_down_rounded),
            tooltip: 'Minimieren',
            onPressed: () => Navigator.of(context).pop(),
          ),
          title: Column(
            children: [
              Text(workout.name, style: LyftaTheme.title.copyWith(fontSize: 15)),
              Text(
                _elapsed,
                style: LyftaTheme.caption.copyWith(color: LyftaTheme.primary),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: sports.isSaving ? null : _finish,
              child: const Text('Fertig'),
            ),
          ],
        ),
        body: Column(
          children: [
            const RestTimerBanner(),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 6),
              child: Row(
                children: [
                  _headerStat('Volumen', '${stats.volumeKg.round()} kg'),
                  _headerStat('Sätze', '${stats.completedSets}/${stats.totalSets}'),
                  _headerStat('Übungen', '${workout.exercises.length}'),
                ],
              ),
            ),
            Expanded(
              child: workout.exercises.isEmpty
                  ? _emptyState()
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                      itemCount: workout.exercises.length,
                      itemBuilder: (context, index) => _ExerciseBlock(
                        key: ValueKey(
                          '${workout.exercises[index].exerciseId}-$index',
                        ),
                        exerciseIndex: index,
                        block: workout.exercises[index],
                      ),
                    ),
            ),
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _addExercise,
                        icon: const Icon(Icons.add, color: LyftaTheme.primary),
                        label: const Text(
                          'Übung hinzufügen',
                          style: TextStyle(color: LyftaTheme.primary),
                        ),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: LyftaTheme.primary),
                          minimumSize: const Size.fromHeight(46),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    IconButton(
                      onPressed: _cancel,
                      icon: const Icon(Icons.delete_outline_rounded),
                      color: LyftaTheme.danger,
                      tooltip: 'Training verwerfen',
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState() => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.fitness_center_rounded,
                  size: 34, color: LyftaTheme.textTertiary),
              const SizedBox(height: 14),
              Text('Noch keine Übungen', style: LyftaTheme.title),
              const SizedBox(height: 6),
              Text(
                'Füge unten die erste Übung hinzu.',
                style: LyftaTheme.subtitle,
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );

  Widget _headerStat(String label, String value) => Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label.toUpperCase(), style: LyftaTheme.label),
            const SizedBox(height: 3),
            Text(value, style: LyftaTheme.headline.copyWith(fontSize: 19)),
          ],
        ),
      );

  Future<void> _addExercise() async {
    final picked = await ExercisePickerSheet.show(context);
    if (picked == null || !mounted) return;
    final sports = context.read<SportsProvider>();
    for (final exercise in picked) {
      sports.addExerciseToWorkout(exercise);
    }
  }

  Future<void> _finish() async {
    final sports = context.read<SportsProvider>();
    final stats = sports.activeWorkoutStats;

    if (stats.completedSets == 0) {
      final proceed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          backgroundColor: LyftaTheme.surface,
          title: const Text('Keine Sätze abgehakt'),
          content: Text(
            'Ohne abgehakte Sätze wird ein leeres Training gespeichert. Trotzdem beenden?',
            style: LyftaTheme.subtitle,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Zurück'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Beenden'),
            ),
          ],
        ),
      );
      if (proceed != true) return;
    }

    if (!mounted) return;

    final notesController = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: const Text('Training beenden'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${stats.completedSets} Sätze · ${stats.volumeKg.round()} kg bewegt',
              style: LyftaTheme.subtitle,
            ),
            const SizedBox(height: 14),
            TextField(
              controller: notesController,
              maxLines: 3,
              decoration: const InputDecoration(hintText: 'Notiz (optional)'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Abbrechen'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Speichern'),
          ),
        ],
      ),
    );

    final notes = notesController.text.trim();
    notesController.dispose();

    if (confirmed != true || !mounted) return;

    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final saved = await sports.finishWorkout(notes: notes.isEmpty ? null : notes);

    if (!mounted) return;
    if (saved) {
      navigator.pop();
      messenger.showSnackBar(const SnackBar(content: Text('Training gespeichert')));
    } else {
      messenger.showSnackBar(
        SnackBar(content: Text(sports.error ?? 'Speichern fehlgeschlagen')),
      );
    }
  }

  Future<void> _cancel() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: const Text('Training verwerfen?'),
        content: Text(
          'Alle Eingaben dieses Trainings gehen verloren.',
          style: LyftaTheme.subtitle,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Zurück'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Verwerfen',
                style: TextStyle(color: LyftaTheme.danger)),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;
    final navigator = Navigator.of(context);
    await context.read<SportsProvider>().cancelWorkout();
    if (mounted) navigator.pop();
  }
}

class _ExerciseBlock extends StatefulWidget {
  final int exerciseIndex;
  final GymWorkoutExercise block;

  const _ExerciseBlock({
    super.key,
    required this.exerciseIndex,
    required this.block,
  });

  @override
  State<_ExerciseBlock> createState() => _ExerciseBlockState();
}

class _ExerciseBlockState extends State<_ExerciseBlock> {
  bool _expanded = true;

  @override
  Widget build(BuildContext context) {
    final sports = context.read<SportsProvider>();
    final block = widget.block;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                ExerciseMuscleFigure(
                  primaryMuscles: block.primaryMuscles,
                  secondaryMuscles: block.secondaryMuscles,
                  size: 44,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        block.name,
                        style: LyftaTheme.title.copyWith(fontSize: 15),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      GestureDetector(
                        onTap: () => _editRestSeconds(sports),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              '${block.completedSets}/${block.sets.length} Sätze · '
                              '${sports.restSecondsFor(widget.exerciseIndex)}s Pause',
                              style: LyftaTheme.caption,
                            ),
                            const SizedBox(width: 3),
                            const Icon(Icons.edit_rounded,
                                size: 12, color: LyftaTheme.textTertiary),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: Icon(
                    _expanded
                        ? Icons.keyboard_arrow_up_rounded
                        : Icons.keyboard_arrow_down_rounded,
                  ),
                  color: LyftaTheme.textTertiary,
                  onPressed: () => setState(() => _expanded = !_expanded),
                ),
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_vert_rounded,
                      color: LyftaTheme.textTertiary),
                  color: LyftaTheme.surfaceElevated,
                  onSelected: (value) {
                    if (value == 'add') {
                      sports.addSetToExercise(widget.exerciseIndex);
                    } else if (value == 'remove') {
                      sports.removeExerciseFromWorkout(widget.exerciseIndex);
                    }
                  },
                  itemBuilder: (_) => const [
                    PopupMenuItem(value: 'add', child: Text('Satz hinzufügen')),
                    PopupMenuItem(value: 'remove', child: Text('Übung entfernen')),
                  ],
                ),
              ],
            ),
          ),
          if (_expanded) ...[
            const Divider(height: 1, color: LyftaTheme.divider),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 4),
              child: Row(
                children: [
                  SizedBox(width: 34, child: Text('SATZ', style: LyftaTheme.label)),
                  Expanded(child: Text('VORHER', style: LyftaTheme.label)),
                  SizedBox(width: 62, child: Text('KG', style: LyftaTheme.label)),
                  const SizedBox(width: 6),
                  SizedBox(width: 62, child: Text('WDH.', style: LyftaTheme.label)),
                  const SizedBox(width: 40),
                ],
              ),
            ),
            ...List.generate(block.sets.length, (setIndex) {
              return _SetRow(
                key: ValueKey('set-${widget.exerciseIndex}-$setIndex'),
                exerciseIndex: widget.exerciseIndex,
                setIndex: setIndex,
                set: block.sets[setIndex],
                previous: setIndex < block.previous.length
                    ? block.previous[setIndex]
                    : null,
              );
            }),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
              child: TextButton.icon(
                onPressed: () => sports.addSetToExercise(widget.exerciseIndex),
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Satz hinzufügen'),
              ),
            ),
          ],
        ],
      ),
    );
  }

  /// Pause nur für diese Übung - der nächste abgehakte Satz startet den
  /// Countdown mit dem neuen Wert (siehe [SportsProvider.restSecondsFor]).
  Future<void> _editRestSeconds(SportsProvider sports) async {
    final current = sports.restSecondsFor(widget.exerciseIndex);
    final picked = await pickRestSeconds(context, initial: current);
    if (picked != null) {
      sports.setExerciseRestSeconds(widget.exerciseIndex, picked);
    }
  }
}

class _SetRow extends StatefulWidget {
  final int exerciseIndex;
  final int setIndex;
  final GymLoggedSet set;
  final GymLoggedSet? previous;

  const _SetRow({
    super.key,
    required this.exerciseIndex,
    required this.setIndex,
    required this.set,
    this.previous,
  });

  @override
  State<_SetRow> createState() => _SetRowState();
}

class _SetRowState extends State<_SetRow> {
  late final TextEditingController _weightController = TextEditingController(
    text: widget.set.weight != null ? gymFormatNumber(widget.set.weight!) : '',
  );
  late final TextEditingController _repsController = TextEditingController(
    text: widget.set.reps?.toString() ?? '',
  );

  @override
  void dispose() {
    _weightController.dispose();
    _repsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.read<SportsProvider>();
    final done = widget.set.isCompleted;
    final isRecord = sports.isPersonalRecord(widget.exerciseIndex, widget.setIndex);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 150),
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
      decoration: BoxDecoration(
        color: done ? LyftaTheme.surfaceElevated : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 34,
            child: GestureDetector(
              // Tippen auf die Satznummer ändert die Satz-Art (Aufwärmen, Dropsatz …).
              onTap: () => _pickSetType(sports),
              child: Center(
                child: Text(
                  widget.set.setType == GymSetType.normal
                      ? '${widget.set.setNumber}'
                      : widget.set.setType.short,
                  style: LyftaTheme.title.copyWith(
                    fontSize: 14,
                    color: widget.set.setType == GymSetType.normal
                        ? LyftaTheme.textPrimary
                        : LyftaTheme.textSecondary,
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: Row(
              children: [
                Flexible(
                  child: Text(
                    widget.previous?.summary ?? '—',
                    style: LyftaTheme.caption.copyWith(fontSize: 12),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (isRecord) ...[
                  const SizedBox(width: 4),
                  const Icon(Icons.emoji_events_rounded,
                      size: 13, color: LyftaTheme.prAccent),
                ],
              ],
            ),
          ),
          _numberField(
            controller: _weightController,
            decimal: true,
            onChanged: (value) => sports.updateSetValues(
              widget.exerciseIndex,
              widget.setIndex,
              weight: double.tryParse(value.replaceAll(',', '.')),
            ),
          ),
          const SizedBox(width: 6),
          _numberField(
            controller: _repsController,
            decimal: false,
            onChanged: (value) => sports.updateSetValues(
              widget.exerciseIndex,
              widget.setIndex,
              reps: int.tryParse(value),
            ),
          ),
          const SizedBox(width: 6),
          GestureDetector(
            onTap: () {
              // Eingaben übernehmen, bevor der Satz zählt - sonst fehlt der zuletzt
              // getippte Wert, wenn das Feld noch den Fokus hat.
              sports.updateSetValues(
                widget.exerciseIndex,
                widget.setIndex,
                weight: double.tryParse(_weightController.text.replaceAll(',', '.')),
                reps: int.tryParse(_repsController.text),
              );
              sports.toggleSetDone(widget.exerciseIndex, widget.setIndex);
              HapticFeedback.selectionClick();
            },
            child: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: done ? LyftaTheme.primary : LyftaTheme.surfaceElevated,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                Icons.check_rounded,
                size: 18,
                color: done ? LyftaTheme.onPrimary : LyftaTheme.textTertiary,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _numberField({
    required TextEditingController controller,
    required bool decimal,
    required ValueChanged<String> onChanged,
  }) {
    return SizedBox(
      width: 62,
      height: 36,
      child: TextField(
        controller: controller,
        onChanged: onChanged,
        textAlign: TextAlign.center,
        keyboardType: TextInputType.numberWithOptions(decimal: decimal),
        inputFormatters: [
          FilteringTextInputFormatter.allow(
            decimal ? RegExp(r'[0-9.,]') : RegExp(r'[0-9]'),
          ),
        ],
        style: LyftaTheme.title.copyWith(fontSize: 14),
        decoration: const InputDecoration(
          isDense: true,
          contentPadding: EdgeInsets.symmetric(vertical: 8),
          hintText: '—',
        ),
      ),
    );
  }

  Future<void> _pickSetType(SportsProvider sports) async {
    final selected = await showModalBottomSheet<GymSetType>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: GymSetType.values
              .map((type) => ListTile(
                    leading: Text(
                      type.short.isEmpty ? '#' : type.short,
                      style: LyftaTheme.title.copyWith(color: LyftaTheme.primary),
                    ),
                    title: Text(
                      type.label,
                      style: LyftaTheme.subtitle.copyWith(
                        color: LyftaTheme.textPrimary,
                      ),
                    ),
                    onTap: () => Navigator.pop(sheetContext, type),
                  ))
              .toList(),
        ),
      ),
    );

    if (selected != null) {
      sports.setSetType(widget.exerciseIndex, widget.setIndex, selected);
    }
  }
}
