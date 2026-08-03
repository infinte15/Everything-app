import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import 'exercise_picker_sheet.dart';
import 'exercise_muscle_figure.dart';

/// Anlegen und Bearbeiten einer Routine: Name, Tag und die Übungsliste mit Zielsätzen.
class CreateRoutineSheet extends StatefulWidget {
  final GymRoutine? existing;

  const CreateRoutineSheet({super.key, this.existing});

  static Future<bool?> show(BuildContext context, {GymRoutine? existing}) {
    return showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => CreateRoutineSheet(existing: existing),
    );
  }

  @override
  State<CreateRoutineSheet> createState() => _CreateRoutineSheetState();
}

class _CreateRoutineSheetState extends State<CreateRoutineSheet> {
  static const List<String> _days = [
    'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So',
  ];

  late final TextEditingController _nameController =
      TextEditingController(text: widget.existing?.name ?? '');
  late final TextEditingController _durationController = TextEditingController(
    text: widget.existing?.estimatedDurationMinutes?.toString() ?? '',
  );

  late String? _dayLabel = widget.existing?.dayLabel;
  late List<GymRoutineExercise> _exercises =
      List<GymRoutineExercise>.from(widget.existing?.exercises ?? const []);

  bool _saving = false;

  @override
  void dispose() {
    _nameController.dispose();
    _durationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.85,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        builder: (context, scrollController) {
          return Column(
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
              const SizedBox(height: 16),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        widget.existing == null ? 'Neue Routine' : 'Routine bearbeiten',
                        style: LyftaTheme.headline.copyWith(fontSize: 22),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: ListView(
                  controller: scrollController,
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
                  children: [
                    TextField(
                      controller: _nameController,
                      textCapitalization: TextCapitalization.sentences,
                      decoration: const InputDecoration(hintText: 'Name, z. B. Push A'),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: _durationController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        hintText: 'Geplante Dauer in Minuten (optional)',
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text('TAG (OPTIONAL)', style: LyftaTheme.label),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: _days.map((day) {
                        final selected = _dayLabel == day;
                        return ChoiceChip(
                          label: Text(day),
                          selected: selected,
                          onSelected: (_) => setState(
                            () => _dayLabel = selected ? null : day,
                          ),
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 22),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Übungen (${_exercises.length})',
                            style: LyftaTheme.title,
                          ),
                        ),
                        TextButton.icon(
                          onPressed: _addExercise,
                          icon: const Icon(Icons.add, size: 18),
                          label: const Text('Hinzufügen'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    if (_exercises.isEmpty)
                      Container(
                        padding: const EdgeInsets.symmetric(vertical: 26),
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: LyftaTheme.surfaceElevated,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          'Noch keine Übungen ausgewählt',
                          style: LyftaTheme.subtitle,
                        ),
                      )
                    else
                      ...List.generate(_exercises.length, (i) {
                        return _EditableExerciseRow(
                          key: ValueKey('${_exercises[i].exerciseId}-$i'),
                          item: _exercises[i],
                          onChanged: (updated) =>
                              setState(() => _exercises[i] = updated),
                          onRemove: () => setState(() => _exercises.removeAt(i)),
                        );
                      }),
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _saving ? null : _save,
                      child: _saving
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: LyftaTheme.onPrimary,
                              ),
                            )
                          : const Text('Routine speichern'),
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _addExercise() async {
    final picked = await ExercisePickerSheet.show(context);
    if (picked == null || !mounted) return;

    setState(() {
      _exercises = [
        ..._exercises,
        for (final exercise in picked)
          GymRoutineExercise(
            exerciseId: exercise.id,
            exerciseName: exercise.name,
            imageUrl: exercise.imageUrl,
            equipment: exercise.equipment,
            primaryMuscles: exercise.primaryMuscles,
            targetSets: 3,
            targetRepsMin: 8,
            targetRepsMax: 12,
            restSeconds: exercise.defaultRestSeconds,
          ),
      ];
    });
  }

  Future<void> _save() async {
    final name = _nameController.text.trim();
    final messenger = ScaffoldMessenger.of(context);

    if (name.isEmpty) {
      messenger.showSnackBar(const SnackBar(content: Text('Bitte einen Namen eingeben')));
      return;
    }
    if (_exercises.isEmpty) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Bitte mindestens eine Übung hinzufügen')),
      );
      return;
    }

    setState(() => _saving = true);
    final sports = context.read<SportsProvider>();
    final navigator = Navigator.of(context);

    final saved = await sports.saveRoutine(
      id: widget.existing?.id,
      name: name,
      dayLabel: _dayLabel,
      estimatedDurationMinutes: int.tryParse(_durationController.text.trim()),
      exercises: _exercises,
    );

    if (!mounted) return;
    setState(() => _saving = false);

    if (saved) {
      navigator.pop(true);
    } else {
      messenger.showSnackBar(
        SnackBar(content: Text(sports.error ?? 'Speichern fehlgeschlagen')),
      );
    }
  }
}

/// Eine Zeile der Übungsliste mit Steppern für Sätze und Wiederholungen.
class _EditableExerciseRow extends StatelessWidget {
  final GymRoutineExercise item;
  final ValueChanged<GymRoutineExercise> onChanged;
  final VoidCallback onRemove;

  const _EditableExerciseRow({
    super.key,
    required this.item,
    required this.onChanged,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Row(
            children: [
              ExerciseMuscleFigure(
                primaryMuscles: item.primaryMuscles,
                secondaryMuscles: item.secondaryMuscles,
                size: 44,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  item.exerciseName,
                  style: LyftaTheme.title.copyWith(fontSize: 15),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close_rounded, size: 18),
                color: LyftaTheme.textTertiary,
                onPressed: onRemove,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _Stepper(
                label: 'Sätze',
                value: item.targetSets,
                min: 1,
                max: 12,
                onChanged: (v) => onChanged(item.copyWith(targetSets: v)),
              ),
              const SizedBox(width: 10),
              _Stepper(
                label: 'Wdh.',
                value: item.targetRepsMin ?? 8,
                min: 1,
                max: 50,
                onChanged: (v) => onChanged(item.copyWith(
                  targetRepsMin: v,
                  targetRepsMax: v > (item.targetRepsMax ?? 0) ? v + 4 : item.targetRepsMax,
                )),
              ),
              const SizedBox(width: 10),
              _Stepper(
                label: 'Pause',
                value: item.restSeconds ?? 90,
                min: 15,
                max: 300,
                step: 15,
                suffix: 's',
                onChanged: (v) => onChanged(item.copyWith(restSeconds: v)),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Stepper extends StatelessWidget {
  final String label;
  final int value;
  final int min;
  final int max;
  final int step;
  final String suffix;
  final ValueChanged<int> onChanged;

  const _Stepper({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.onChanged,
    this.step = 1,
    this.suffix = '',
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(), style: LyftaTheme.label),
          const SizedBox(height: 4),
          Container(
            decoration: BoxDecoration(
              color: LyftaTheme.surface,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _button(Icons.remove, value > min, () {
                  onChanged((value - step).clamp(min, max));
                }),
                Text('$value$suffix', style: LyftaTheme.title.copyWith(fontSize: 14)),
                _button(Icons.add, value < max, () {
                  onChanged((value + step).clamp(min, max));
                }),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _button(IconData icon, bool enabled, VoidCallback onTap) {
    return InkWell(
      onTap: enabled ? onTap : null,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.all(6),
        child: Icon(
          icon,
          size: 16,
          color: enabled ? LyftaTheme.textPrimary : LyftaTheme.textTertiary,
        ),
      ),
    );
  }
}
