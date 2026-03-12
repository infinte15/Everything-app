import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/study_provider.dart';
import '../../models/lesson_plan_entry.dart';

class LessonPlanPage extends StatelessWidget {
  const LessonPlanPage({super.key});

  static const _days = ['Mo', 'Di', 'Mi', 'Do', 'Fr'];
  static const _startHour = 7;
  static const _endHour = 20;
  static const _pixPerHour = 64.0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
            child: Row(
              children: [
                const Text('🗓️', style: TextStyle(fontSize: 28)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text('Stundenplan',
                      style: theme.textTheme.headlineSmall
                          ?.copyWith(fontWeight: FontWeight.bold)),
                ),
                FilledButton.icon(
                  onPressed: () => _showAddDialog(context, null),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Stunde'),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          // Timetable
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SizedBox(
                // time column + 5 day columns
                width: 60 + 5 * 148,
                child: Column(
                  children: [
                    // Day header row
                    Row(
                      children: [
                        const SizedBox(width: 60),
                        for (int d = 0; d < 5; d++)
                          Container(
                            width: 148,
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              border: Border(
                                bottom: BorderSide(
                                    color: theme.colorScheme.outline
                                        .withOpacity(0.2)),
                                right: BorderSide(
                                    color: theme.colorScheme.outline
                                        .withOpacity(0.15)),
                              ),
                            ),
                            child: Text(_days[d],
                                style: theme.textTheme.labelLarge?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.primary)),
                          ),
                      ],
                    ),
                    // Scrollable grid
                    Expanded(
                      child: SingleChildScrollView(
                        child: SizedBox(
                          height: (_endHour - _startHour) * _pixPerHour,
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // Time column
                              Column(
                                children: [
                                  for (int h = _startHour; h < _endHour; h++)
                                    SizedBox(
                                      width: 60,
                                      height: _pixPerHour,
                                      child: Align(
                                        alignment: Alignment.topRight,
                                        child: Padding(
                                          padding:
                                              const EdgeInsets.only(right: 8, top: 4),
                                          child: Text(
                                            '${h.toString().padLeft(2, '0')}:00',
                                            style: const TextStyle(
                                                fontSize: 11, color: Colors.grey),
                                          ),
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                              // Day columns
                              for (int d = 0; d < 5; d++)
                                _DayColumn(
                                  dayIndex: d,
                                  entries: provider.lessonsForDay(d),
                                  startHour: _startHour,
                                  pixPerHour: _pixPerHour,
                                  totalHours: _endHour - _startHour,
                                  onTap: (e) => _showEditDialog(context, e),
                                  onAdd: () => _showAddDialog(context, d),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _showAddDialog(BuildContext context, int? dayIndex) async {
    final subjectCtrl = TextEditingController();
    final roomCtrl = TextEditingController();
    final profCtrl = TextEditingController();
    int selectedDay = dayIndex ?? 0;
    int startHour = 8;
    int duration = 90;
    String type = 'Vorlesung';
    int colorVal = 0xFF3B82F6;

    final colors = [
      0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFF8B5CF6,
      0xFFEF4444, 0xFFEC4899, 0xFF14B8A6, 0xFF6366F1,
    ];
    final types = ['Vorlesung', 'Praktikum', 'Seminar', 'Übung', 'Tutorium'];

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(builder: (ctx, setSt) {
        return AlertDialog(
          title: const Text('Neue Stunde'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: subjectCtrl,
                  autofocus: true,
                  decoration: const InputDecoration(labelText: 'Fach'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: roomCtrl,
                  decoration: const InputDecoration(labelText: 'Raum'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: profCtrl,
                  decoration: const InputDecoration(labelText: 'Professor'),
                ),
                const SizedBox(height: 16),
                // Day picker
                Text('Tag', style: Theme.of(ctx).textTheme.labelMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  children: List.generate(5, (i) {
                    final sel = i == selectedDay;
                    return ChoiceChip(
                      label: Text(_days[i]),
                      selected: sel,
                      onSelected: (_) => setSt(() => selectedDay = i),
                    );
                  }),
                ),
                const SizedBox(height: 12),
                // Time
                Row(children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Beginn', style: Theme.of(ctx).textTheme.labelMedium),
                        Slider(
                          value: startHour.toDouble(),
                          min: 7,
                          max: 18,
                          divisions: 11,
                          label: '$startHour:00',
                          onChanged: (v) => setSt(() => startHour = v.round()),
                        ),
                        Text('$startHour:00',
                            style: const TextStyle(fontSize: 12)),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Dauer (min)', style: Theme.of(ctx).textTheme.labelMedium),
                        Slider(
                          value: duration.toDouble(),
                          min: 45,
                          max: 180,
                          divisions: 9,
                          label: '${duration}min',
                          onChanged: (v) => setSt(() => duration = v.round()),
                        ),
                        Text('${duration}min',
                            style: const TextStyle(fontSize: 12)),
                      ],
                    ),
                  ),
                ]),
                const SizedBox(height: 12),
                // Type
                Text('Typ', style: Theme.of(ctx).textTheme.labelMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  children: types.map((t) {
                    return ChoiceChip(
                      label: Text(t),
                      selected: t == type,
                      onSelected: (_) => setSt(() => type = t),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 12),
                // Color
                Text('Farbe', style: Theme.of(ctx).textTheme.labelMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: colors.map((c) {
                    final sel = c == colorVal;
                    return GestureDetector(
                      onTap: () => setSt(() => colorVal = c),
                      child: Container(
                        width: 26,
                        height: 26,
                        decoration: BoxDecoration(
                          color: Color(c),
                          shape: BoxShape.circle,
                          border: sel
                              ? Border.all(color: Colors.white, width: 3)
                              : null,
                          boxShadow: sel
                              ? [BoxShadow(color: Color(c), blurRadius: 6)]
                              : null,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Abbrechen')),
            FilledButton(
              onPressed: () {
                if (subjectCtrl.text.trim().isNotEmpty) {
                  context.read<StudyProvider>().addLesson(LessonPlanEntry(
                        id: 'lp${DateTime.now().millisecondsSinceEpoch}',
                        subject: subjectCtrl.text.trim(),
                        room:
                            roomCtrl.text.trim().isEmpty ? null : roomCtrl.text.trim(),
                        professor: profCtrl.text.trim().isEmpty
                            ? null
                            : profCtrl.text.trim(),
                        dayIndex: selectedDay,
                        startHour: startHour,
                        durationMinutes: duration,
                        colorValue: colorVal,
                        type: type,
                      ));
                  Navigator.pop(ctx);
                }
              },
              child: const Text('Hinzufügen'),
            ),
          ],
        );
      }),
    );
  }

  Future<void> _showEditDialog(BuildContext context, LessonPlanEntry entry) async {
    await showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(entry.subject),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (entry.room != null) _InfoRow(Icons.room_outlined, entry.room!),
            if (entry.professor != null)
              _InfoRow(Icons.person_outline, entry.professor!),
            _InfoRow(Icons.access_time_outlined,
                '${entry.startTimeLabel} – ${entry.endTimeLabel}'),
            _InfoRow(Icons.category_outlined, entry.type),
            _InfoRow(Icons.timelapse_outlined, '${entry.durationMinutes} Min.'),
          ],
        ),
        actions: [
          TextButton.icon(
            icon: const Icon(Icons.delete_outline, color: Colors.red),
            label: const Text('Löschen', style: TextStyle(color: Colors.red)),
            onPressed: () {
              context.read<StudyProvider>().deleteLesson(entry.id);
              Navigator.pop(ctx);
            },
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String text;
  const _InfoRow(this.icon, this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, size: 16, color: Colors.grey),
          const SizedBox(width: 8),
          Text(text),
        ],
      ),
    );
  }
}

// ── Day column ────────────────────────────────────────────────────────────────

class _DayColumn extends StatelessWidget {
  final int dayIndex;
  final List<LessonPlanEntry> entries;
  final int startHour;
  final double pixPerHour;
  final int totalHours;
  final void Function(LessonPlanEntry) onTap;
  final VoidCallback onAdd;

  const _DayColumn({
    required this.dayIndex,
    required this.entries,
    required this.startHour,
    required this.pixPerHour,
    required this.totalHours,
    required this.onTap,
    required this.onAdd,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GestureDetector(
      onTap: onAdd,
      child: Container(
        width: 148,
        height: totalHours * pixPerHour,
        decoration: BoxDecoration(
          border: Border(
            right: BorderSide(
                color: theme.colorScheme.outline.withOpacity(0.15)),
          ),
        ),
        child: Stack(
          children: [
            // Hour lines
            for (int h = 0; h < totalHours; h++)
              Positioned(
                top: h * pixPerHour,
                left: 0,
                right: 0,
                child: Divider(
                    height: 1,
                    color: theme.colorScheme.outline.withOpacity(0.1)),
              ),
            // Lesson blocks
            for (final entry in entries)
              _LessonBlock(
                entry: entry,
                startHour: startHour,
                pixPerHour: pixPerHour,
                onTap: () => onTap(entry),
              ),
          ],
        ),
      ),
    );
  }
}

class _LessonBlock extends StatelessWidget {
  final LessonPlanEntry entry;
  final int startHour;
  final double pixPerHour;
  final VoidCallback onTap;

  const _LessonBlock({
    required this.entry,
    required this.startHour,
    required this.pixPerHour,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final top = ((entry.startHour - startHour) + entry.startMinute / 60.0) * pixPerHour;
    final height = (entry.durationMinutes / 60.0) * pixPerHour - 2;
    final color = entry.color;

    return Positioned(
      top: top,
      left: 2,
      right: 2,
      height: height,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: color.withOpacity(0.15),
            borderRadius: BorderRadius.circular(8),
            border: Border(left: BorderSide(color: color, width: 3)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                entry.subject,
                style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: color),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              if (height > 35 && entry.room != null)
                Text(entry.room!,
                    style: const TextStyle(fontSize: 10, color: Colors.grey),
                    maxLines: 1),
              if (height > 48)
                Text(entry.startTimeLabel,
                    style: const TextStyle(fontSize: 10, color: Colors.grey)),
            ],
          ),
        ),
      ),
    );
  }
}
