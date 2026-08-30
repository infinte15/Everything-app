import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/exercise_detail_sheet.dart';
import '../widgets/exercise_picker_sheet.dart';
import '../widgets/exercise_media.dart';
import 'progression_banner.dart';
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
          body: Center(child: Text('Kein laufendes Training', style: LyftaTheme.subtitle)),
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
          title: Text(workout.name, style: LyftaTheme.title.copyWith(fontSize: 15)),
          actions: [
            TextButton(onPressed: sports.isSaving ? null : _finish, child: const Text('Fertig')),
          ],
        ),
        body: Column(
          children: [
            const RestTimerBanner(),
            _supersetBanner(sports, workout),
            // Die drei Zahlen, die Lyfta über dem Satzraster stehen hat - als eigene Karte,
            // nicht als lose Zeile: sie gehören zusammen und beantworten "wie weit bin ich".
            // Die Dauer steht hier statt unter dem Namen, damit die laufende Uhr dieselbe
            // Größe hat wie die beiden Zahlen, mit denen man sie vergleicht.
            Container(
              margin: const EdgeInsets.fromLTRB(16, 12, 16, 6),
              padding: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: LyftaTheme.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  _headerStat('Dauer', _elapsed),
                  _headerStat('Volumen', '${stats.volumeKg.round()} kg'),
                  _headerStat('Sätze', '${stats.completedSets}/${stats.totalSets}'),
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
                        key: ValueKey('${workout.exercises[index].exerciseId}-$index'),
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
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
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

  /// Im Supersatz kommt nach dem Satz keine Pause, sondern die nächste Übung. Ohne diesen
  /// Hinweis sieht der Bildschirm aus, als wäre nichts passiert - der Pausen-Timer, den man
  /// sonst erwartet, bleibt ja bewusst aus.
  Widget _supersetBanner(SportsProvider sports, ActiveWorkout workout) {
    final next = sports.supersetNextIndex;
    if (next == null || next >= workout.exercises.length) {
      return const SizedBox.shrink();
    }
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 10, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          const Icon(Icons.swap_horiz_rounded, size: 17, color: LyftaTheme.primary),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('SUPERSATZ', style: LyftaTheme.label),
                const SizedBox(height: 2),
                Text(
                  'Ohne Pause weiter mit ${workout.exercises[next].name}',
                  style: LyftaTheme.title.copyWith(fontSize: 14),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _emptyState() => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.fitness_center_rounded, size: 34, color: LyftaTheme.textTertiary),
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
      messenger.showSnackBar(SnackBar(content: Text(sports.error ?? 'Speichern fehlgeschlagen')));
    }
  }

  Future<void> _cancel() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: const Text('Training verwerfen?'),
        content: Text('Alle Eingaben dieses Trainings gehen verloren.', style: LyftaTheme.subtitle),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Zurück'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Verwerfen', style: TextStyle(color: LyftaTheme.danger)),
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

  const _ExerciseBlock({super.key, required this.exerciseIndex, required this.block});

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
      decoration: BoxDecoration(color: LyftaTheme.surface, borderRadius: BorderRadius.circular(12)),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                GestureDetector(
                  onTap: () => _openDetail(sports, block),
                  child: ExerciseThumb(
                    imageUrl: block.imageUrl,
                    primaryMuscles: block.primaryMuscles,
                    secondaryMuscles: block.secondaryMuscles,
                    size: 44,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      GestureDetector(
                        onTap: () => _openDetail(sports, block),
                        child: Text(
                          block.name,
                          style: LyftaTheme.title.copyWith(fontSize: 15),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (block.supersetGroup != null)
                            Text(
                              'Supersatz ${block.supersetGroup} · ',
                              style: LyftaTheme.caption.copyWith(color: LyftaTheme.primary),
                            ),
                          Text(
                            '${block.completedSets}/${block.sets.length} Sätze',
                            style: LyftaTheme.caption,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: Icon(
                    _expanded ? Icons.keyboard_arrow_up_rounded : Icons.keyboard_arrow_down_rounded,
                  ),
                  color: LyftaTheme.textTertiary,
                  onPressed: () => setState(() => _expanded = !_expanded),
                ),
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_vert_rounded, color: LyftaTheme.textTertiary),
                  color: LyftaTheme.surfaceElevated,
                  onSelected: (value) {
                    if (value == 'add') {
                      sports.addSetToExercise(widget.exerciseIndex);
                    } else if (value == 'warmup') {
                      sports.applyWarmupRamp(widget.exerciseIndex);
                    } else if (value == 'remove') {
                      sports.removeExerciseFromWorkout(widget.exerciseIndex);
                    }
                  },
                  itemBuilder: (_) => [
                    const PopupMenuItem(value: 'add', child: Text('Satz hinzufügen')),
                    if (_canWarmUp(block))
                      const PopupMenuItem(value: 'warmup', child: Text('Aufwärmsätze einfügen')),
                    const PopupMenuItem(value: 'remove', child: Text('Übung entfernen')),
                  ],
                ),
              ],
            ),
          ),
          if (_expanded) ...[
            const Divider(height: 1, color: LyftaTheme.divider),
            // Hier steht bewusst keine Ausführungsanimation mehr. Sie war 180 px hoch und
            // schob bei drei Übungen das Satzraster aus dem Bild - im laufenden Training
            // schaut man auf Gewicht und Wiederholungen, nicht auf eine Schleife. Wer die
            // Ausführung sehen will, tippt die Übung an; dort läuft sie in voller Größe.

            // Notiz und Pause stehen als eigene Zeilen zwischen Übungsname und Satzraster -
            // dieselbe Reihenfolge wie bei Lyfta. Die Notiz sagt, wie die Übung aufgebaut wird
            // ("Bank auf Stufe 3"), und das gehört vor das Gewicht.
            //
            // Die Notizzeile steht auch dann da, wenn es keine Notiz gibt: sonst ist sie nur
            // zu finden, wenn schon eine existiert - und man müsste die Übungsdetails
            // aufmachen, um die erste anzulegen.
            _InlineRow(
              icon: Icons.sticky_note_2_outlined,
              text: block.exerciseNote?.isNotEmpty == true
                  ? block.exerciseNote!
                  : 'Notiz hinzufügen …',
              muted: block.exerciseNote?.isNotEmpty != true,
              onTap: () => _editNote(sports, block),
            ),
            _InlineRow(
              icon: Icons.timer_outlined,
              text: 'Pause: ${_restLabel(sports.restSecondsFor(widget.exerciseIndex))}',
              onTap: () => _editRestSeconds(sports),
            ),
            if (block.progression != null && block.progression!.hasAdvice)
              ProgressionBanner(
                suggestion: block.progression!,
                onApplyWarmup: _canWarmUp(block)
                    ? () => sports.applyWarmupRamp(widget.exerciseIndex)
                    : null,
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 4),
              child: Row(
                children: [
                  SizedBox(width: 34, child: Text('SATZ', style: LyftaTheme.label)),
                  const SizedBox(width: 6),
                  Expanded(child: Text('VORHER', style: LyftaTheme.label)),
                  SizedBox(width: 62, child: Text('KG', style: LyftaTheme.label)),
                  const SizedBox(width: 6),
                  SizedBox(width: 62, child: Text('WDH.', style: LyftaTheme.label)),
                  if (sports.showRpe) ...[
                    const SizedBox(width: 6),
                    SizedBox(width: 44, child: Text('RPE', style: LyftaTheme.label)),
                  ],
                  const SizedBox(width: 40),
                ],
              ),
            ),
            // Die "vorher"-Spalte gehört Satz für Satz zum Arbeitssatz - Aufwärm- und
            // Zusatzsätze haben kein Gegenstück in der letzten Einheit und würden die
            // Zuordnung sonst um ihre eigene Anzahl verschieben.
            ...() {
              var workIndex = 0;
              return List.generate(block.sets.length, (setIndex) {
                final set = block.sets[setIndex];
                final isWork = set.setType != GymSetType.warmup && set.parentSetNumber == null;
                final previous = isWork && workIndex < block.previous.length
                    ? block.previous[workIndex]
                    : null;
                if (isWork) workIndex++;
                return _SetRow(
                  key: ValueKey('set-${widget.exerciseIndex}-$setIndex'),
                  exerciseIndex: widget.exerciseIndex,
                  setIndex: setIndex,
                  set: set,
                  previous: previous,
                );
              });
            }(),
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

  /// Rampe anbietbar? Nur wenn es eine gibt und noch kein Aufwärmsatz steht.
  bool _canWarmUp(GymWorkoutExercise block) =>
      (block.progression?.warmup.isNotEmpty ?? false) &&
      !block.sets.any((s) => s.setType == GymSetType.warmup);

  /// Übungsblatt mit Ausführung, Anleitung und Verlauf.
  ///
  /// Der Block im Training kennt nur, was fürs Protokollieren nötig ist - Anleitung und
  /// Verlauf hängen an der Katalogzeile und werden erst hier geholt. Das ist der Grund,
  /// warum das Blatt und nicht der Block die Animation zeigt.
  Future<void> _openDetail(SportsProvider sports, GymWorkoutExercise block) async {
    final exercise = await sports.loadExercise(block.exerciseId);
    if (!mounted || exercise == null) return;
    await ExerciseDetailSheet.show(context, exercise);
  }

  /// "90s" bis zur Minute, darüber "2:00 min" - eine Pause von 150 Sekunden liest sich
  /// schlechter als 2:30.
  static String _restLabel(int seconds) {
    if (seconds < 60) return '${seconds}s';
    final minutes = seconds ~/ 60;
    final rest = seconds % 60;
    return rest == 0
        ? '$minutes:00 min'
        : '$minutes:${rest.toString().padLeft(2, '0')} min';
  }

  /// Stehende Notiz zur Übung - dieselbe, die auch im Übungsblatt steht.
  Future<void> _editNote(SportsProvider sports, GymWorkoutExercise block) async {
    final controller = TextEditingController(text: block.exerciseNote ?? '');

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

    final ok = await sports.saveExerciseNote(block.exerciseId, text);
    if (ok) sports.setExerciseNoteInWorkout(widget.exerciseIndex, text);
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
  late final TextEditingController _rpeController = TextEditingController(
    text: widget.set.rpe?.toString() ?? '',
  );

  @override
  void dispose() {
    _weightController.dispose();
    _repsController.dispose();
    _rpeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final done = widget.set.isCompleted;
    final isRecord = sports.isPersonalRecord(widget.exerciseIndex, widget.setIndex);
    // Abfall- und Rest-Pause-Sätze hängen sichtbar am Arbeitssatz darüber: eingerückt,
    // damit man sie nicht für einen eigenen Arbeitssatz hält.
    final isChild = widget.set.parentSetNumber != null;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 150),
      margin: EdgeInsets.fromLTRB(isChild ? 24 : 8, 2, 8, 2),
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
                  const Icon(Icons.emoji_events_rounded, size: 13, color: LyftaTheme.prAccent),
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
          if (sports.showRpe) ...[
            const SizedBox(width: 6),
            _numberField(
              controller: _rpeController,
              decimal: false,
              width: 44,
              onChanged: (value) =>
                  sports.setSetRpe(widget.exerciseIndex, widget.setIndex, int.tryParse(value)),
            ),
          ],
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
    double width = 62,
  }) {
    return SizedBox(
      width: width,
      height: 36,
      child: TextField(
        controller: controller,
        onChanged: onChanged,
        textAlign: TextAlign.center,
        keyboardType: TextInputType.numberWithOptions(decimal: decimal),
        inputFormatters: [
          FilteringTextInputFormatter.allow(decimal ? RegExp(r'[0-9.,]') : RegExp(r'[0-9]')),
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

  /// Satzart wählen - und, für Arbeitssätze, einen Zusatzsatz anhängen.
  ///
  /// Dropsatz und Rest-Pause stehen bewusst *nicht* in der Typenliste: sie sind keine Wahl
  /// für diese Zeile, sondern legen eine neue an, die an dieser hängt. Als Typ gewählt
  /// hätten sie kein Elternteil und wären in der Auswertung nicht zuzuordnen.
  Future<void> _pickSetType(SportsProvider sports) async {
    const hidden = {GymSetType.drop, GymSetType.restpause};
    final isChild = widget.set.parentSetNumber != null;

    final selected = await showModalBottomSheet<Object>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      // Scrollbar: mit den beiden Zusatzsatz-Zeilen ist die Liste höher als das Blatt.
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ...GymSetType.values
                  .where((t) => !hidden.contains(t))
                  .map(
                    (type) => ListTile(
                      leading: Text(
                        type.short.isEmpty ? '#' : type.short,
                        style: LyftaTheme.title.copyWith(color: LyftaTheme.primary),
                      ),
                      title: Text(
                        type.label,
                        style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary),
                      ),
                      onTap: () => Navigator.pop(sheetContext, type),
                    ),
                  ),
              if (!isChild) ...[
                const Divider(height: 1, color: LyftaTheme.divider),
                ListTile(
                  leading: Text('D', style: LyftaTheme.title.copyWith(color: LyftaTheme.primary)),
                  title: Text(
                    'Dropsatz anhängen',
                    style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary),
                  ),
                  subtitle: Text(
                    'Gewicht runter, direkt weiter. Zählt zusätzlich ins Volumen.',
                    style: LyftaTheme.caption,
                  ),
                  onTap: () => Navigator.pop(sheetContext, #drop),
                ),
                ListTile(
                  leading: Text('RP', style: LyftaTheme.title.copyWith(color: LyftaTheme.primary)),
                  title: Text(
                    'Rest-Pause anhängen',
                    style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary),
                  ),
                  subtitle: Text(
                    'Gleiches Gewicht, kurze Pause. Gehört zum Satz darüber.',
                    style: LyftaTheme.caption,
                  ),
                  onTap: () => Navigator.pop(sheetContext, #restpause),
                ),
              ],
            ],
          ),
        ),
      ),
    );

    if (selected is GymSetType) {
      sports.setSetType(widget.exerciseIndex, widget.setIndex, selected);
    } else if (selected == #drop) {
      sports.addDropSet(widget.exerciseIndex, widget.setIndex);
    } else if (selected == #restpause) {
      sports.addRestPauseSet(widget.exerciseIndex, widget.setIndex);
    }
  }
}

/// Eine Zeile zwischen Übungsname und Satzraster: Symbol, Text, antippbar.
///
/// Notiz und Pausenzeit teilen sich dieselbe Form, weil sie dieselbe Rolle haben - beides
/// sind Angaben zur Übung, die man vor dem ersten Satz setzt und danach selten wieder anfasst.
class _InlineRow extends StatelessWidget {
  final IconData icon;
  final String text;

  /// Platzhaltertext statt Inhalt - dann tritt die Zeile zurück.
  final bool muted;
  final VoidCallback onTap;

  const _InlineRow({
    required this.icon,
    required this.text,
    this.muted = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 9, 12, 0),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 15, color: LyftaTheme.textTertiary),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                text,
                style: LyftaTheme.caption.copyWith(
                  fontSize: 12,
                  color: muted ? LyftaTheme.textTertiary : LyftaTheme.textSecondary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
