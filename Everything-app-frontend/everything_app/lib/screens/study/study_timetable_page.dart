import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/lesson_plan_entry.dart';

// Constants matching calendar screen styling
const double kHourHeight = 64.0;
const double kTimeGutterWidth = 52.0;
const int kDayStart = 0;
const int kDayEnd = 24;

class StudyTimetablePage extends StatefulWidget {
  const StudyTimetablePage({super.key});

  @override
  State<StudyTimetablePage> createState() => _StudyTimetablePageState();
}

class _StudyTimetablePageState extends State<StudyTimetablePage> {
  int _weekOffset = 0; // offset in weeks from current week
  bool _showWeekend = false;
  late PageController _pageController;

  static const List<String> _dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: 10000);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _navigate(int delta) {
    _pageController.animateToPage(
      _pageController.page!.toInt() + delta,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
    );
  }

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

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: Padding(
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
                      onPressed: () => _navigate(-1),
                    ),
                    const SizedBox(width: 4),
                    IconButton(
                      icon: const Icon(Icons.chevron_right),
                      style: IconButton.styleFrom(
                        backgroundColor: theme.colorScheme.surfaceContainerLow,
                        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      ),
                      onPressed: () => _navigate(1),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Weekly Calendar Grid low container
            Expanded(
              child: Container(
                color: theme.colorScheme.surfaceContainerLow,
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: [
                    // Days Header (Sticky, outside PageView, non-interactive)
                    Row(
                      children: [
                        const SizedBox(width: kTimeGutterWidth), // spacer for time ticks
                        Expanded(
                          child: Row(
                            children: List.generate(totalDaysToShow, (index) {
                              final date = monday.add(Duration(days: index));
                              final isToday = date.year == DateTime.now().year &&
                                  date.month == DateTime.now().month &&
                                  date.day == DateTime.now().day;

                              return Expanded(
                                child: Container(
                                  margin: const EdgeInsets.symmetric(horizontal: 2),
                                  padding: const EdgeInsets.symmetric(vertical: 8),
                                  decoration: BoxDecoration(
                                    color: isToday
                                        ? theme.colorScheme.primaryContainer.withValues(alpha: 0.3)
                                        : Colors.transparent,
                                  ),
                                  child: Column(
                                    children: [
                                      Text(
                                        _dayNames[index].toUpperCase(),
                                        style: theme.textTheme.labelSmall?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: isToday
                                              ? const Color(0xFFC2C1FF)
                                              : theme.colorScheme.onSurfaceVariant,
                                          letterSpacing: 1.0,
                                          fontSize: 10,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        '${date.day}',
                                        style: theme.textTheme.titleMedium?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: isToday
                                              ? const Color(0xFFC2C1FF)
                                              : theme.colorScheme.onSurface,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              );
                            }),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Grid and Timeline Content (with PageView for horizontal swipe)
                    Expanded(
                      child: PageView.builder(
                        controller: _pageController,
                        onPageChanged: (index) {
                          setState(() {
                            _weekOffset = index - 10000;
                          });
                        },
                        itemBuilder: (context, index) {
                          final pageOffset = index - 10000;
                          final pageNow = DateTime.now().add(Duration(days: pageOffset * 7));
                          final pageMonday = pageNow.subtract(Duration(days: pageNow.weekday - 1));
                          final pageDays = List.generate(totalDaysToShow, (i) => pageMonday.add(Duration(days: i)));

                          return _StudyWeekView(
                            provider: provider,
                            days: pageDays,
                            totalDaysToShow: totalDaysToShow,
                            isWide: isWide,
                            onLessonTap: (lesson) => _showLessonDetails(context, lesson),
                          );
                        },
                      ),
                    ),
                  ],
                ),
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
    TimeOfDay selectedStartTime = const TimeOfDay(hour: 8, minute: 0);
    TimeOfDay selectedEndTime = const TimeOfDay(hour: 9, minute: 30);
    int selectedColorValue = 0xFFC2C1FF;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) {
          final startMinutes = selectedStartTime.hour * 60 + selectedStartTime.minute;
          final endMinutes = selectedEndTime.hour * 60 + selectedEndTime.minute;
          final duration = endMinutes - startMinutes;
          final isValid = nameCtrl.text.trim().isNotEmpty && duration > 0;

          return AlertDialog(
            title: const Text('Veranstaltung hinzufügen'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: nameCtrl,
                    decoration: const InputDecoration(labelText: 'Fachname'),
                    onChanged: (_) => setSt(() {}),
                  ),
                  TextField(controller: profCtrl, decoration: const InputDecoration(labelText: 'Dozent')),
                  TextField(controller: roomCtrl, decoration: const InputDecoration(labelText: 'Raum')),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    initialValue: selectedDay,
                    decoration: const InputDecoration(labelText: 'Wochentag'),
                    items: List.generate(7, (i) => DropdownMenuItem(value: i, child: Text(_dayNames[i]))),
                    onChanged: (v) => setSt(() => selectedDay = v!),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Beginn', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 4),
                            OutlinedButton(
                              onPressed: () async {
                                final picked = await showTimePicker(
                                  context: context,
                                  initialTime: selectedStartTime,
                                );
                                if (picked != null) {
                                  setSt(() {
                                    selectedStartTime = picked;
                                    final startMins = selectedStartTime.hour * 60 + selectedStartTime.minute;
                                    final endMins = selectedEndTime.hour * 60 + selectedEndTime.minute;
                                    if (endMins <= startMins) {
                                      selectedEndTime = TimeOfDay(
                                        hour: (selectedStartTime.hour + 1).clamp(0, 23),
                                        minute: selectedStartTime.minute,
                                      );
                                    }
                                  });
                                }
                              },
                              style: OutlinedButton.styleFrom(
                                padding: const EdgeInsets.symmetric(vertical: 12),
                                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                              ),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.access_time, size: 14),
                                  const SizedBox(width: 6),
                                  Text(
                                    '${selectedStartTime.hour.toString().padLeft(2, '0')}:${selectedStartTime.minute.toString().padLeft(2, '0')}',
                                    style: const TextStyle(fontSize: 13),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Ende', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 4),
                            OutlinedButton(
                              onPressed: () async {
                                final picked = await showTimePicker(
                                  context: context,
                                  initialTime: selectedEndTime,
                                );
                                if (picked != null) {
                                  setSt(() {
                                    selectedEndTime = picked;
                                  });
                                }
                              },
                              style: OutlinedButton.styleFrom(
                                padding: const EdgeInsets.symmetric(vertical: 12),
                                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                              ),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.access_time, size: 14),
                                  const SizedBox(width: 6),
                                  Text(
                                    '${selectedEndTime.hour.toString().padLeft(2, '0')}:${selectedEndTime.minute.toString().padLeft(2, '0')}',
                                    style: const TextStyle(fontSize: 13),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  if (duration <= 0) ...[
                    const SizedBox(height: 8),
                    const Text(
                      'Endzeit muss nach der Startzeit liegen.',
                      style: TextStyle(color: Colors.red, fontSize: 11),
                    ),
                  ],
                  const SizedBox(height: 20),
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
                onPressed: isValid
                    ? () {
                        final lesson = LessonPlanEntry(
                          id: 'lp${DateTime.now().millisecondsSinceEpoch}',
                          subject: nameCtrl.text.trim(),
                          professor: profCtrl.text.trim(),
                          room: roomCtrl.text.trim(),
                          dayIndex: selectedDay,
                          startHour: selectedStartTime.hour,
                          startMinute: selectedStartTime.minute,
                          durationMinutes: duration,
                          colorValue: selectedColorValue,
                        );
                        context.read<StudyProvider>().addLesson(lesson);
                        Navigator.pop(ctx);
                      }
                    : null,
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
      _pageController.jumpToPage(10000 + _weekOffset);
    }
  }
}

// Stateful Sub-View representing a single week timeline
class _StudyWeekView extends StatefulWidget {
  final StudyProvider provider;
  final List<DateTime> days;
  final int totalDaysToShow;
  final bool isWide;
  final ValueChanged<LessonPlanEntry> onLessonTap;

  const _StudyWeekView({
    required this.provider,
    required this.days,
    required this.totalDaysToShow,
    required this.isWide,
    required this.onLessonTap,
  });

  @override
  State<_StudyWeekView> createState() => _StudyWeekViewState();
}

class _StudyWeekViewState extends State<_StudyWeekView> {
  late ScrollController _scrollController;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToCurrentTime();
    });
  }

  void _scrollToCurrentTime() {
    if (!mounted || !_scrollController.hasClients) return;
    final now = DateTime.now();
    final minutes = now.hour * 60 + now.minute;
    final y = (minutes / 60) * kHourHeight;
    final viewportHeight = _scrollController.position.viewportDimension;
    final offset = (y - viewportHeight / 2).clamp(
      0.0,
      _scrollController.position.maxScrollExtent,
    );
    _scrollController.jumpTo(offset);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final gridLineColor = theme.colorScheme.outlineVariant.withValues(alpha: 0.3);
    final timeTextColor = theme.colorScheme.onSurfaceVariant;
    final totalHeight = kHourHeight * (kDayEnd - kDayStart);

    return SingleChildScrollView(
      controller: _scrollController,
      child: SizedBox(
        height: totalHeight,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Time gutter (0:00 to 24:00)
            SizedBox(
              width: kTimeGutterWidth,
              height: totalHeight,
              child: Stack(
                children: [
                  for (int h = kDayStart; h < kDayEnd; h++)
                    Positioned(
                      top: (h - kDayStart) * kHourHeight - 7,
                      left: 0,
                      right: 0,
                      child: Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: Text(
                          h == 0 ? '' : '${h.toString().padLeft(2, '0')}:00',
                          textAlign: TextAlign.right,
                          style: TextStyle(
                            fontSize: 10,
                            color: timeTextColor,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            // Column(s)
            Expanded(
              child: Stack(
                children: [
                  // Hour grid lines
                  for (int h = kDayStart; h <= kDayEnd; h++)
                    Positioned(
                      top: (h - kDayStart) * kHourHeight,
                      left: 0,
                      right: 0,
                      child: Divider(
                        height: 1,
                        thickness: 1,
                        color: h % 2 == 0
                            ? theme.colorScheme.outlineVariant.withValues(alpha: 0.15)
                            : theme.colorScheme.outlineVariant.withValues(alpha: 0.05),
                      ),
                    ),

                  // Column separators
                  if (widget.totalDaysToShow > 1)
                    LayoutBuilder(builder: (ctx, constraints) {
                      final colW = constraints.maxWidth / widget.totalDaysToShow;
                      return Stack(
                        children: [
                          for (int c = 1; c < widget.totalDaysToShow; c++)
                            Positioned(
                              left: c * colW,
                              top: 0,
                              width: 1,
                              height: totalHeight,
                              child: Container(color: gridLineColor),
                            ),
                        ],
                      );
                    }),

                  // Today highlight in the grid
                  LayoutBuilder(builder: (ctx, constraints) {
                    final colW = constraints.maxWidth / widget.totalDaysToShow;
                    for (int c = 0; c < widget.days.length; c++) {
                      if (_isSameDay(widget.days[c], DateTime.now())) {
                        return Positioned(
                          left: c * colW,
                          top: 0,
                          width: colW,
                          height: totalHeight,
                          child: Container(
                            color: const Color(0xFFC2C1FF).withValues(alpha: 0.04),
                          ),
                        );
                      }
                    }
                    return const SizedBox.shrink();
                  }),

                  // Lessons blocks
                  LayoutBuilder(builder: (ctx, constraints) {
                    final colW = constraints.maxWidth / widget.totalDaysToShow;
                    return Stack(
                      children: [
                        for (int dayIdx = 0; dayIdx < widget.totalDaysToShow; dayIdx++)
                          ...widget.provider.lessonsForDay(dayIdx).map((lesson) {
                            final startMinutes = lesson.startHour * 60 + lesson.startMinute;
                            final top = (startMinutes / 60.0) * kHourHeight;
                            final height = (lesson.durationMinutes / 60.0) * kHourHeight;

                            return Positioned(
                              left: dayIdx * colW + 2,
                              top: top,
                              width: colW - 4,
                              height: height.clamp(24.0, double.infinity),
                              child: GestureDetector(
                                onTap: () => widget.onLessonTap(lesson),
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
                                          fontSize: widget.isWide ? 12 : 10,
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
                                      if (widget.isWide && lesson.professor != null && lesson.professor!.isNotEmpty) ...[
                                        const SizedBox(height: 4),
                                        Text(
                                          lesson.professor!,
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
                          }),
                      ],
                    );
                  }),

                  // Current time line (if page contains today)
                  if (widget.days.any((d) => _isSameDay(d, DateTime.now())))
                    const _CurrentTimeLine(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// Current Time Line widget matching calendar screen indicator
class _CurrentTimeLine extends StatefulWidget {
  const _CurrentTimeLine();

  @override
  State<_CurrentTimeLine> createState() => _CurrentTimeLineState();
}

class _CurrentTimeLineState extends State<_CurrentTimeLine> {
  late Timer _timer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (mounted) {
        setState(() {
          _now = DateTime.now();
        });
      }
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final minutes = _now.hour * 60 + _now.minute;
    final top = (minutes / 60) * kHourHeight;
    return Positioned(
      top: top - 6,
      left: 0,
      right: 0,
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            margin: const EdgeInsets.only(left: 2, right: 4),
            decoration: const BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
              boxShadow: [BoxShadow(color: Colors.white, blurRadius: 4)],
            ),
          ),
          Expanded(
            child: Container(
              height: 1,
              color: Colors.white.withValues(alpha: 0.5),
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
            color: Colors.white,
            child: const Text(
              'NOW',
              style: TextStyle(
                color: Colors.black,
                fontSize: 8,
                fontWeight: FontWeight.w900,
                letterSpacing: -0.5,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
