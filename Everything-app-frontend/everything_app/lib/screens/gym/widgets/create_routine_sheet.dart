import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

class CreateRoutineSheet extends StatefulWidget {
  const CreateRoutineSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => const CreateRoutineSheet(),
    );
  }

  @override
  State<CreateRoutineSheet> createState() => _CreateRoutineSheetState();
}

class _CreateRoutineSheetState extends State<CreateRoutineSheet> {
  final _nameCtrl = TextEditingController();
  String _day = 'Mon';
  final List<Map<String, dynamic>> _exercises = [];
  final _days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  @override
  void dispose() {
    _nameCtrl.dispose();
    super.dispose();
  }

  void _pickExercise() {
    final sports = context.read<SportsProvider>();
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: LyftaTheme.surfaceElevated,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        builder: (_, scroll) => ListView.builder(
          controller: scroll,
          itemCount: sports.exercises.length,
          itemBuilder: (_, i) {
            final ex = sports.exercises[i];
            return ListTile(
              title: Text(ex['name'] as String? ?? '', style: const TextStyle(color: LyftaTheme.textPrimary)),
              subtitle: Text('${ex['category']} · ${ex['equipment']}', style: LyftaTheme.subtitle),
              onTap: () {
                setState(() {
                  _exercises.add({
                    'name': ex['name'],
                    'exerciseId': ex['id'],
                    'sets': 3,
                    'reps': 10,
                    'weight': 0,
                  });
                });
                Navigator.pop(ctx);
              },
            );
          },
        ),
      ),
    );
  }

  void _save() {
    if (_nameCtrl.text.trim().isEmpty) return;
    context.read<SportsProvider>().addRoutine(
          name: _nameCtrl.text.trim(),
          day: _day,
          exercises: _exercises.isEmpty
              ? [
                  {'name': 'Squat', 'sets': 3, 'reps': 5, 'weight': 60, 'exerciseId': 2},
                ]
              : _exercises,
        );
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 12, 20, bottom + 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
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
          const SizedBox(height: 16),
          Text('New Routine', style: LyftaTheme.title),
          const SizedBox(height: 16),
          TextField(
            controller: _nameCtrl,
            style: const TextStyle(color: LyftaTheme.textPrimary),
            decoration: const InputDecoration(hintText: 'Routine name'),
          ),
          const SizedBox(height: 12),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _days.map((d) {
                final sel = _day == d;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(d),
                    selected: sel,
                    onSelected: (_) => setState(() => _day = d),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Exercises', style: LyftaTheme.title.copyWith(fontSize: 16)),
              TextButton.icon(
                onPressed: _pickExercise,
                icon: const Icon(Icons.add, color: LyftaTheme.primary, size: 18),
                label: const Text('Add', style: TextStyle(color: LyftaTheme.primary)),
              ),
            ],
          ),
          ..._exercises.asMap().entries.map((e) {
            return ListTile(
              dense: true,
              title: Text(e.value['name'] as String, style: const TextStyle(color: LyftaTheme.textPrimary)),
              trailing: IconButton(
                icon: const Icon(Icons.close, size: 18, color: LyftaTheme.textTertiary),
                onPressed: () => setState(() => _exercises.removeAt(e.key)),
              ),
            );
          }),
          const SizedBox(height: 20),
          FilledButton(onPressed: _save, child: const Text('Save Routine')),
        ],
      ),
    );
  }
}
