import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

class ActiveWorkoutPage extends StatefulWidget {
  const ActiveWorkoutPage({super.key});

  @override
  State<ActiveWorkoutPage> createState() => _ActiveWorkoutPageState();
}

class _ActiveWorkoutPageState extends State<ActiveWorkoutPage> {
  Timer? _elapsedTimer;
  String _elapsed = '00:00';

  @override
  void initState() {
    super.initState();
    _tick();
    _elapsedTimer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    final sports = context.read<SportsProvider>();
    final start = sports.currentWorkout?['startTime'] as DateTime?;
    if (start == null || !mounted) return;
    final d = DateTime.now().difference(start);
    setState(() {
      _elapsed =
          '${d.inMinutes.remainder(60).toString().padLeft(2, '0')}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
    });
  }

  @override
  void dispose() {
    _elapsedTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final workout = sports.currentWorkout;

    if (workout == null) {
      return Theme(
        data: LyftaTheme.darkTheme,
        child: Scaffold(
          backgroundColor: LyftaTheme.background,
          body: Center(
            child: Text('No active workout', style: LyftaTheme.subtitle),
          ),
        ),
      );
    }

    final stats = sports.getActiveWorkoutStats();
    final exercises = workout['exercises'] as List;

    return Theme(
      data: LyftaTheme.darkTheme,
      child: Scaffold(
        backgroundColor: LyftaTheme.background,
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 32),
            onPressed: () => Navigator.pop(context),
          ),
          title: Column(
            children: [
              Text(workout['name'] as String? ?? 'Workout', style: LyftaTheme.title.copyWith(fontSize: 16)),
              Text(_elapsed, style: LyftaTheme.caption.copyWith(color: LyftaTheme.primary)),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => _finish(context),
              child: const Text('Finish', style: TextStyle(color: LyftaTheme.primary, fontWeight: FontWeight.w600)),
            ),
          ],
        ),
        body: Column(
          children: [
            if (sports.isResting) _RestBanner(sports: sports),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _stat('Duration', _elapsed),
                  _stat('Volume', '${(stats['volume'] as double).round()} kg'),
                  _stat('Sets', '${stats['completedSets']}/${stats['totalSets']}'),
                ],
              ),
            ),
            Expanded(
              child: exercises.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text('Add your first exercise', style: LyftaTheme.subtitle),
                          const SizedBox(height: 16),
                          FilledButton.icon(
                            onPressed: () => _addExercise(context),
                            icon: const Icon(Icons.add),
                            label: const Text('Add Exercise'),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
                      itemCount: exercises.length,
                      itemBuilder: (_, i) => _ExerciseBlock(
                        exerciseIndex: i,
                        exercise: exercises[i] as Map<String, dynamic>,
                        sports: sports,
                      ),
                    ),
            ),
            _BottomBar(onAddExercise: () => _addExercise(context), onCancel: () => _cancel(context)),
          ],
        ),
      ),
    );
  }

  Widget _stat(String label, String value) {
    return Column(
      children: [
        Text(label.toUpperCase(), style: LyftaTheme.label),
        const SizedBox(height: 4),
        Text(value, style: LyftaTheme.title),
      ],
    );
  }

  void _addExercise(BuildContext context) {
    final sports = context.read<SportsProvider>();
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      builder: (ctx) => ListView.builder(
        itemCount: sports.exercises.length,
        itemBuilder: (_, i) {
          final ex = sports.exercises[i];
          return ListTile(
            title: Text(ex['name'] as String? ?? '', style: const TextStyle(color: LyftaTheme.textPrimary)),
            subtitle: Text('${ex['category']}', style: LyftaTheme.subtitle),
            onTap: () {
              sports.addExerciseToWorkout(ex);
              Navigator.pop(ctx);
            },
          );
        },
      ),
    );
  }

  void _cancel(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: const Text('Discard workout?'),
        content: const Text('All progress will be lost.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Keep going')),
          TextButton(
            onPressed: () {
              context.read<SportsProvider>().cancelWorkout();
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
            child: const Text('Discard', style: TextStyle(color: LyftaTheme.danger)),
          ),
        ],
      ),
    );
  }

  void _finish(BuildContext context) {
    final notesCtrl = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: LyftaTheme.surface,
        title: const Text('Finish workout'),
        content: TextField(
          controller: notesCtrl,
          maxLines: 3,
          style: const TextStyle(color: LyftaTheme.textPrimary),
          decoration: const InputDecoration(hintText: 'How did it go? (optional)'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              await context.read<SportsProvider>().finishWorkout(notes: notesCtrl.text.trim());
              if (ctx.mounted) Navigator.pop(ctx);
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}

class _RestBanner extends StatelessWidget {
  final SportsProvider sports;
  const _RestBanner({required this.sports});

  @override
  Widget build(BuildContext context) {
    final secs = sports.restSecondsRemaining;
    final m = (secs ~/ 60).toString().padLeft(2, '0');
    final s = (secs % 60).toString().padLeft(2, '0');

    return Material(
      color: LyftaTheme.primary,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('REST TIMER', style: TextStyle(color: LyftaTheme.onPrimary, fontSize: 11, fontWeight: FontWeight.bold)),
                  Text('$m:$s', style: const TextStyle(color: LyftaTheme.onPrimary, fontSize: 28, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
            IconButton(
              onPressed: () => sports.adjustRest(-15),
              icon: const Icon(Icons.remove, color: LyftaTheme.onPrimary),
            ),
            IconButton(
              onPressed: () => sports.adjustRest(15),
              icon: const Icon(Icons.add, color: LyftaTheme.onPrimary),
            ),
            TextButton(
              onPressed: sports.skipRest,
              child: const Text('Skip', style: TextStyle(color: LyftaTheme.onPrimary, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      ),
    );
  }
}

class _ExerciseBlock extends StatefulWidget {
  final int exerciseIndex;
  final Map<String, dynamic> exercise;
  final SportsProvider sports;

  const _ExerciseBlock({
    required this.exerciseIndex,
    required this.exercise,
    required this.sports,
  });

  @override
  State<_ExerciseBlock> createState() => _ExerciseBlockState();
}

class _ExerciseBlockState extends State<_ExerciseBlock> {
  bool _expanded = true;

  @override
  Widget build(BuildContext context) {
    final name = widget.exercise['name'] as String? ?? '';
    final sets = widget.exercise['loggedSets'] as List;
    final previous = widget.sports.getPreviousPerformance(name);
    final done = sets.where((s) => (s as Map)['done'] == true).length;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          InkWell(
            onTap: () => setState(() => _expanded = !_expanded),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: LyftaTheme.surfaceElevated,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.fitness_center, color: LyftaTheme.textSecondary, size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(name, style: LyftaTheme.title.copyWith(fontSize: 16)),
                        Text('$done/${sets.length} sets', style: LyftaTheme.subtitle),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.add, color: LyftaTheme.primary),
                    onPressed: () => widget.sports.addSetToExercise(widget.exerciseIndex),
                  ),
                  Icon(_expanded ? Icons.expand_less : Icons.expand_more, color: LyftaTheme.textTertiary),
                ],
              ),
            ),
          ),
          if (_expanded) ...[
            const Divider(height: 1, color: LyftaTheme.divider),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(
                children: [
                  const SizedBox(width: 36, child: Text('SET', style: TextStyle(color: LyftaTheme.textTertiary, fontSize: 11, fontWeight: FontWeight.bold))),
                  Expanded(child: Text('PREVIOUS', style: LyftaTheme.label)),
                  const SizedBox(width: 64, child: Text('KG', textAlign: TextAlign.center, style: TextStyle(color: LyftaTheme.textTertiary, fontSize: 11, fontWeight: FontWeight.bold))),
                  const SizedBox(width: 64, child: Text('REPS', textAlign: TextAlign.center, style: TextStyle(color: LyftaTheme.textTertiary, fontSize: 11, fontWeight: FontWeight.bold))),
                  const SizedBox(width: 40),
                ],
              ),
            ),
            ...sets.asMap().entries.map((e) {
              return _SetRow(
                exerciseIndex: widget.exerciseIndex,
                setIndex: e.key,
                setMap: Map<String, dynamic>.from(e.value as Map),
                previous: previous,
                sports: widget.sports,
              );
            }),
            const SizedBox(height: 8),
          ],
        ],
      ),
    );
  }
}

class _SetRow extends StatelessWidget {
  final int exerciseIndex;
  final int setIndex;
  final Map<String, dynamic> setMap;
  final String? previous;
  final SportsProvider sports;

  const _SetRow({
    required this.exerciseIndex,
    required this.setIndex,
    required this.setMap,
    required this.previous,
    required this.sports,
  });

  @override
  Widget build(BuildContext context) {
    final done = setMap['done'] as bool? ?? false;
    final type = GymSetType.values.firstWhere(
      (t) => t.name == setMap['type'],
      orElse: () => GymSetType.normal,
    );
    final typeLabel = type.short;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 150),
      color: done ? LyftaTheme.primary.withValues(alpha: 0.12) : Colors.transparent,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Row(
        children: [
          SizedBox(
            width: 36,
            child: Row(
              children: [
                Text(
                  '${setMap['set']}',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: done ? LyftaTheme.primary : LyftaTheme.textPrimary,
                  ),
                ),
                if (typeLabel.isNotEmpty)
                  Text(typeLabel, style: const TextStyle(color: LyftaTheme.warning, fontSize: 10)),
              ],
            ),
          ),
          Expanded(
            child: Text(
              previous ?? '—',
              style: LyftaTheme.caption,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          SizedBox(
            width: 64,
            child: _numField(
              '${setMap['weight']}',
              (v) => sports.updateSetValues(exerciseIndex, setIndex, weight: v),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 64,
            child: _numField(
              '${setMap['reps']}',
              (v) => sports.updateSetValues(exerciseIndex, setIndex, reps: v.round()),
              isInt: true,
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: () => sports.toggleSetDone(exerciseIndex, setIndex),
            onLongPress: () => _showSetTypeMenu(context),
            child: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: done ? LyftaTheme.primary : LyftaTheme.surfaceElevated,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: done ? LyftaTheme.primary : LyftaTheme.divider),
              ),
              child: done
                  ? const Icon(Icons.check, color: LyftaTheme.onPrimary, size: 20)
                  : null,
            ),
          ),
        ],
      ),
    );
  }

  void _showSetTypeMenu(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: GymSetType.values.map((t) {
            return ListTile(
              title: Text(t.label, style: const TextStyle(color: LyftaTheme.textPrimary)),
              onTap: () {
                sports.setSetType(exerciseIndex, setIndex, t);
                Navigator.pop(ctx);
              },
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _numField(String initial, void Function(double) onChanged, {bool isInt = false}) {
    return TextFormField(
      initialValue: initial,
      keyboardType: TextInputType.numberWithOptions(decimal: !isInt),
      textAlign: TextAlign.center,
      style: const TextStyle(color: LyftaTheme.textPrimary, fontWeight: FontWeight.w600, fontSize: 15),
      inputFormatters: [FilteringTextInputFormatter.allow(RegExp(isInt ? r'[0-9]' : r'[0-9.,]'))],
      decoration: InputDecoration(
        filled: true,
        fillColor: LyftaTheme.surfaceElevated,
        contentPadding: const EdgeInsets.symmetric(vertical: 8),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
      ),
      onChanged: (v) {
        final n = double.tryParse(v.replaceAll(',', '.'));
        if (n != null) onChanged(n);
      },
    );
  }
}

class _BottomBar extends StatelessWidget {
  final VoidCallback onAddExercise;
  final VoidCallback onCancel;

  const _BottomBar({required this.onAddExercise, required this.onCancel});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: const BoxDecoration(
        color: LyftaTheme.surface,
        border: Border(top: BorderSide(color: LyftaTheme.divider)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: onAddExercise,
                icon: const Icon(Icons.add, color: LyftaTheme.primary, size: 20),
                label: const Text('Add Exercise', style: TextStyle(color: LyftaTheme.primary)),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: LyftaTheme.primary),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
