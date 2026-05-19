import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/lesson_plan_entry.dart';

class StudyTimetablePage extends StatefulWidget {
  const StudyTimetablePage({super.key});

  @override
  State<StudyTimetablePage> createState() => _StudyTimetablePageState();
}

class _StudyTimetablePageState extends State<StudyTimetablePage> {
  int _weekOffset = 0; // offset in weeks from current week
  bool _showWeekend = false;
  int? _selectedDayIndex;

  static const List<String> _dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();

    // Calculate dates of the active week
    final now = DateTime.now().add(Duration(days: _weekOffset * 7));
    final monday = now.subtract(Duration(days: now.weekday - 1));
    final sunday = monday.add(const Duration(days: 6));

    // Formatted date range
    final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final startStr = '${months[monday.month - 1]} ${monday.day}';
    final endStr = '${months[sunday.month - 1]} ${sunday.day}';

    // Calculate calendar week number
    final dayOfYear = now.difference(DateTime(now.year, 1, 1)).inDays + 1;
    final weekNum = ((dayOfYear - now.weekday + 10) / 7).floor();

    // Responsive configuration
    final isWide = MediaQuery.of(context).size.width > 700;
    final totalDaysToShow = isWide ? 7 : (_showWeekend ? 7 : 5);

    // Timeline configuration
    const startHour = 8;
    const endHour = 16;
    const hourHeight = 80.0;
    const timelineHeight = (endHour - startHour) * hourHeight;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Week Selector Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                InkWell(
                  onTap: () => _selectDate(context),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            'Woche $weekNum',
                            style: theme.textTheme.headlineSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.onSurface,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Icon(
                            Icons.calendar_month,
                            color: theme.colorScheme.primary,
                            size: 20,
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '$startStr - $endStr',
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ],
                  ),
                ),
                Row(
                  children: [
                    if (!isWide) ...[
                      IconButton(
                        icon: Icon(_showWeekend ? Icons.grid_3x3 : Icons.grid_4x4),
                        style: IconButton.styleFrom(
                          backgroundColor: theme.colorScheme.surfaceContainerLow,
                          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                        ),
                        tooltip: _showWeekend ? 'Wochenende ausblenden' : 'Wochenende einblenden',
                        onPressed: () {
                          setState(() {
                            _showWeekend = !_showWeekend;
                          });
                        },
                      ),
                      const SizedBox(width: 4),
                    ],
                    IconButton(
                      icon: const Icon(Icons.chevron_left),
                      style: IconButton.styleFrom(
                        backgroundColor: theme.colorScheme.surfaceContainerLow,
                        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      ),
                      onPressed: () {
                        setState(() {
                          _weekOffset--;
                        });
                      },
                    ),
                    const SizedBox(width: 4),
                    IconButton(
                      icon: const Icon(Icons.chevron_right),
                      style: IconButton.styleFrom(
                        backgroundColor: theme.colorScheme.surfaceContainerLow,
                        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      ),
                      onPressed: () {
                        setState(() {
                          _weekOffset++;
                        });
                      },
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Weekly Calendar Grid low container
            Container(
              color: theme.colorScheme.surfaceContainerLow,
              padding: const EdgeInsets.all(12),
              child: Column(
                children: [
                  // Days Header
                  Row(
                    children: [
                      const SizedBox(width: 50), // spacer for time ticks
                      Expanded(
                        child: Row(
                          children: List.generate(totalDaysToShow, (index) {
                            final date = monday.add(Duration(days: index));
                            final isToday = date.year == DateTime.now().year &&
                                date.month == DateTime.now().month &&
                                date.day == DateTime.now().day;
                            final isSelected = _selectedDayIndex == index;

                            return Expanded(
                              child: InkWell(
                                onTap: () {
                                  setState(() {
                                    _selectedDayIndex = _selectedDayIndex == index ? null : index;
                                  });
                                },
                                child: Container(
                                  margin: const EdgeInsets.symmetric(horizontal: 2),
                                  padding: const EdgeInsets.symmetric(vertical: 8),
                                  decoration: BoxDecoration(
                                    color: isSelected
                                        ? theme.colorScheme.primary.withValues(alpha: 0.15)
                                        : (isToday
                                            ? theme.colorScheme.primaryContainer.withValues(alpha: 0.3)
                                            : Colors.transparent),
                                    border: isSelected
                                        ? Border.all(color: theme.colorScheme.primary, width: 1.5)
                                        : null,
                                  ),
                                  child: Column(
                                    children: [
                                      Text(
                                        _dayNames[index].toUpperCase(),
                                        style: theme.textTheme.labelSmall?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: isSelected
                                              ? theme.colorScheme.primary
                                              : (isToday
                                                  ? theme.colorScheme.primary
                                                  : theme.colorScheme.onSurfaceVariant),
                                          letterSpacing: 1.0,
                                          fontSize: 10,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        '${date.day}',
                                        style: theme.textTheme.titleMedium?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: isSelected || isToday
                                              ? theme.colorScheme.primary
                                              : theme.colorScheme.onSurface,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          }),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Grid and Timeline Content
                  Stack(
                    children: [
                      // Background grid lines
                      Padding(
                        padding: const EdgeInsets.only(left: 50),
                        child: SizedBox(
                          height: timelineHeight,
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: List.generate(endHour - startHour + 1, (i) {
                              return Container(
                                height: 1,
                                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                              );
                            }),
                          ),
                        ),
                      ),

                      // Main schedule area
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // Time Column ticks
                          SizedBox(
                            width: 50,
                            height: timelineHeight,
                            child: Stack(
                              children: List.generate(endHour - startHour, (i) {
                                final hour = startHour + i;
                                final label = '${hour.toString().padLeft(2, '0')}:00';
                                return Positioned(
                                  top: i * hourHeight - 8,
                                  left: 0,
                                  child: Text(
                                    label,
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                      fontSize: 10,
                                    ),
                                  ),
                                );
                              }),
                            ),
                          ),

                          // Days Columns with lesson blocks
                          Expanded(
                            child: SizedBox(
                              height: timelineHeight,
                              child: Row(
                                children: List.generate(totalDaysToShow, (dayIdx) {
                                  final lessons = provider.lessonsForDay(dayIdx);
                                  final isSelected = _selectedDayIndex == dayIdx;

                                  return Expanded(
                                    child: Container(
                                      decoration: BoxDecoration(
                                        color: isSelected
                                            ? theme.colorScheme.primary.withValues(alpha: 0.05)
                                            : Colors.transparent,
                                        border: isSelected
                                            ? Border(
                                                left: BorderSide(color: theme.colorScheme.primary.withValues(alpha: 0.15), width: 1),
                                                right: BorderSide(color: theme.colorScheme.primary.withValues(alpha: 0.15), width: 1),
                                              )
                                            : null,
                                      ),
                                      child: Stack(
                                        clipBehavior: Clip.none,
                                        children: lessons.map((lesson) {
                                          // Calculate top & height
                                          final startMinutes = (lesson.startHour - startHour) * 60 + lesson.startMinute;
                                          final top = (startMinutes / 60.0) * hourHeight;
                                          final height = (lesson.durationMinutes / 60.0) * hourHeight;

                                          return Positioned(
                                            top: top,
                                            left: 2,
                                            right: 2,
                                            height: height,
                                            child: GestureDetector(
                                              onTap: () => _showLessonDetails(context, lesson),
                                              child: Container(
                                                decoration: BoxDecoration(
                                                  color: lesson.colorValue == 0xFF3B82F6
                                                      ? theme.colorScheme.primaryContainer.withValues(alpha: 0.4)
                                                      : theme.colorScheme.surfaceContainerHighest,
                                                  border: Border(
                                                    left: BorderSide(
                                                      color: Color(lesson.colorValue),
                                                      width: 3,
                                                    ),
                                                  ),
                                                ),
                                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
                                                child: Column(
                                                  crossAxisAlignment: CrossAxisAlignment.start,
                                                  children: [
                                                    Text(
                                                      lesson.subject,
                                                      style: theme.textTheme.labelSmall?.copyWith(
                                                        fontWeight: FontWeight.bold,
                                                        color: theme.colorScheme.onSurface,
                                                        fontSize: isWide ? 12 : 10,
                                                      ),
                                                      maxLines: 1,
                                                      overflow: TextOverflow.ellipsis,
                                                    ),
                                                    const SizedBox(height: 2),
                                                    Text(
                                                      '${lesson.startHour}:${lesson.startMinute.toString().padLeft(2, '0')}',
                                                      style: theme.textTheme.labelSmall?.copyWith(
                                                        color: theme.colorScheme.onSurfaceVariant,
                                                        fontSize: 9,
                                                      ),
                                                    ),
                                                    if (isWide) ...[
                                                      const SizedBox(height: 4),
                                                      Text(
                                                        lesson.professor ?? '',
                                                        style: theme.textTheme.labelSmall?.copyWith(
                                                          color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.7),
                                                          fontSize: 8,
                                                        ),
                                                        maxLines: 1,
                                                        overflow: TextOverflow.ellipsis,
                                                      ),
                                                    ],
                                                  ],
                                                ),
                                              ),
                                            ),
                                          );
                                        }).toList(),
                                      ),
                                    ),
                                  );
                                }),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddLessonDialog(context),
        backgroundColor: theme.colorScheme.primary,
        foregroundColor: theme.colorScheme.surface,
        elevation: 6,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        child: const Icon(Icons.edit_calendar),
      ),
    );
  }

  void _showLessonDetails(BuildContext context, LessonPlanEntry lesson) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(lesson.subject),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Dozent: ${lesson.professor ?? 'Unbekannt'}'),
            const SizedBox(height: 8),
            Text('Raum: ${lesson.room ?? 'Kein Raum'}'),
            const SizedBox(height: 8),
            Text('Typ: ${lesson.type.isEmpty ? 'Vorlesung' : lesson.type}'),
            const SizedBox(height: 8),
            Text('Dauer: ${lesson.durationMinutes} Minuten'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              context.read<StudyProvider>().deleteLesson(lesson.id);
              Navigator.pop(ctx);
            },
            child: const Text('Löschen', style: TextStyle(color: Colors.red)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Schließen'),
          ),
        ],
      ),
    );
  }

  void _showAddLessonDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final profCtrl = TextEditingController();
    final roomCtrl = TextEditingController();
    int selectedDay = 0;
    int selectedHour = 8;
    int selectedMinute = 0;
    int duration = 90;
    int selectedColorValue = 0xFFC2C1FF;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) {
          return AlertDialog(
            title: const Text('Veranstaltung hinzufügen'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Fachname')),
                  TextField(controller: profCtrl, decoration: const InputDecoration(labelText: 'Dozent')),
                  TextField(controller: roomCtrl, decoration: const InputDecoration(labelText: 'Raum')),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<int>(
                    value: selectedDay,
                    decoration: const InputDecoration(labelText: 'Wochentag'),
                    items: List.generate(7, (i) => DropdownMenuItem(value: i, child: Text(_dayNames[i]))),
                    onChanged: (v) => setSt(() => selectedDay = v!),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<int>(
                          value: selectedHour,
                          decoration: const InputDecoration(labelText: 'Stunde'),
                          items: List.generate(8, (i) => DropdownMenuItem(value: i + 8, child: Text('${i + 8} Uhr'))),
                          onChanged: (v) => setSt(() => selectedHour = v!),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: DropdownButtonFormField<int>(
                          value: selectedMinute,
                          decoration: const InputDecoration(labelText: 'Minute'),
                          items: [0, 15, 30, 45].map((m) => DropdownMenuItem(value: m, child: Text('$m'))).toList(),
                          onChanged: (v) => setSt(() => selectedMinute = v!),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<int>(
                    value: duration,
                    decoration: const InputDecoration(labelText: 'Dauer'),
                    items: [45, 90, 120, 180].map((d) => DropdownMenuItem(value: d, child: Text('$d Min'))).toList(),
                    onChanged: (v) => setSt(() => duration = v!),
                  ),
                  const SizedBox(height: 16),
                  const Text('Farbe wählen', style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFFEF4444, 0xFF8B5CF6, 0xFFC2C1FF].map((colorVal) {
                      final isSel = selectedColorValue == colorVal;
                      return InkWell(
                        onTap: () {
                          setSt(() {
                            selectedColorValue = colorVal;
                          });
                        },
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            color: Color(colorVal),
                            shape: BoxShape.circle,
                            border: isSel
                                ? Border.all(color: Colors.white, width: 2)
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
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
              FilledButton(
                onPressed: () {
                  if (nameCtrl.text.trim().isNotEmpty) {
                    final lesson = LessonPlanEntry(
                      id: 'lp${DateTime.now().millisecondsSinceEpoch}',
                      subject: nameCtrl.text.trim(),
                      professor: profCtrl.text.trim(),
                      room: roomCtrl.text.trim(),
                      dayIndex: selectedDay,
                      startHour: selectedHour,
                      startMinute: selectedMinute,
                      durationMinutes: duration,
                      colorValue: selectedColorValue,
                    );
                    context.read<StudyProvider>().addLesson(lesson);
                    Navigator.pop(ctx);
                  }
                },
                child: const Text('Hinzufügen'),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _selectDate(BuildContext context) async {
    final nowToday = DateTime.now();
    final initialDate = nowToday.add(Duration(days: _weekOffset * 7));
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: DateTime(2020),
      lastDate: DateTime(2030),
    );
    if (picked != null) {
      // Calculate difference in weeks
      final selectedMonday = picked.subtract(Duration(days: picked.weekday - 1));
      final todayMonday = nowToday.subtract(Duration(days: nowToday.weekday - 1));
      final diffDays = selectedMonday.difference(todayMonday).inDays;
      setState(() {
        _weekOffset = (diffDays / 7).round();
      });
    }
  }
}
