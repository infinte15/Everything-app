import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../providers/calendar_provider.dart';
import '../../config/app_theme.dart';
import '../../models/calendar_event.dart';

// ─── Constants ───────────────────────────────────────────────────────────────

const double kHourHeight = 64.0;
const double kTimeGutterWidth = 52.0;
const int kDayStart = 0;
const int kDayEnd = 24;

// ─── Event Type Colours ──────────────────────────────────────────────────────

Color _typeColor(String type) {
  switch (type.toUpperCase()) {
    case 'TASK':
      return const Color(0xFF6366F1);
    case 'HABIT':
      return const Color(0xFF10B981);
    case 'WORKOUT':
      return const Color(0xFFF59E0B);
    case 'STUDY':
      return const Color(0xFF3B82F6);
    default:
      return const Color(0xFF8B5CF6);
  }
}

IconData _typeIcon(String type) {
  switch (type.toUpperCase()) {
    case 'TASK':
      return Icons.check_circle_outline_rounded;
    case 'HABIT':
      return Icons.repeat_rounded;
    case 'WORKOUT':
      return Icons.fitness_center_rounded;
    case 'STUDY':
      return Icons.menu_book_rounded;
    default:
      return Icons.event_rounded;
  }
}

enum _CalView { day, week, month }

// ─── Root Screen ─────────────────────────────────────────────────────────────

class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key});

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  _CalView _view = _CalView.day;
  late ScrollController _timelineScrollController;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    // scroll to make current hour visible (~2 hours before now)
    final offset = ((now.hour - 2).clamp(0, 22)) * kHourHeight;
    _timelineScrollController = ScrollController(initialScrollOffset: offset);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final cal = context.read<CalendarProvider>();
      cal.setSelectedDay(now);
      cal.loadEventsForMonth(now);
    });
  }

  @override
  void dispose() {
    _timelineScrollController.dispose();
    super.dispose();
  }

  void _navigate(int delta) {
    final cal = context.read<CalendarProvider>();
    final cur = cal.selectedDay ?? DateTime.now();
    final Duration d = _view == _CalView.day
        ? Duration(days: delta)
        : _view == _CalView.week
            ? Duration(days: delta * 7)
            : Duration(days: delta * 30);
    final next = cur.add(d);
    cal.setSelectedDay(next);
    cal.setFocusedDay(next);
    if (_view == _CalView.month || _view == _CalView.week) {
      cal.loadEventsForMonth(next);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final cal = context.watch<CalendarProvider>();
    final selected = cal.selectedDay ?? DateTime.now();

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F0F13) : const Color(0xFFF7F8FC),
      body: SafeArea(
        child: Column(
          children: [
            _CalendarHeader(
              view: _view,
              selectedDay: selected,
              onViewChanged: (v) => setState(() => _view = v),
              onNavigate: _navigate,
              onToday: () {
                final now = DateTime.now();
                cal.setSelectedDay(now);
                cal.setFocusedDay(now);
                cal.loadEventsForMonth(now);
              },
              onSchedule: () => _showScheduleDialog(context),
              onAdd: () => _showCreateSheet(context),
            ),
            if (_view != _CalView.month)
              _WeekStrip(
                selected: selected,
                onDayTap: (d) {
                  cal.setSelectedDay(d);
                  cal.setFocusedDay(d);
                },
              ),
            Expanded(
              child: _view == _CalView.day
                  ? _DayTimeline(
                      cal: cal,
                      selected: selected,
                      scrollController: _timelineScrollController,
                    )
                  : _view == _CalView.week
                      ? _WeekTimeline(cal: cal, selected: selected)
                      : _MonthView(
                          cal: cal,
                          selected: selected,
                          onDayTap: (d) {
                            cal.setSelectedDay(d);
                            cal.setFocusedDay(d);
                            setState(() => _view = _CalView.day);
                          },
                        ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateSheet(context),
        backgroundColor: AppTheme.primaryColor,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add_rounded),
        label: const Text('New Event', style: TextStyle(fontWeight: FontWeight.w600)),
        elevation: 4,
      ),
    );
  }

  void _showScheduleDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Row(children: [
          Icon(Icons.auto_awesome_rounded, color: Color(0xFFF59E0B)),
          SizedBox(width: 10),
          Text('Smart Schedule'),
        ]),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Let AI automatically plan the optimal schedule for the next 7 days.'),
            SizedBox(height: 10),
            Text('It considers all your tasks, habits, workouts and study sessions.',
                style: TextStyle(fontSize: 13, color: Colors.grey)),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              Navigator.pop(context);
              final now = DateTime.now();
              final result = await context.read<CalendarProvider>().generateSchedule(now, now.add(const Duration(days: 7)));
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                  content: Text(result['success'] == true ? '✅ Schedule created!' : '❌ Error: ${result['error']}'),
                  behavior: SnackBarBehavior.floating,
                ));
              }
            },
            child: const Text('Schedule Now'),
          ),
        ],
      ),
    );
  }

  void _showCreateSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _CreateEventSheet(selectedDay: context.read<CalendarProvider>().selectedDay ?? DateTime.now()),
    );
  }
}

// ─── Calendar Header ─────────────────────────────────────────────────────────

class _CalendarHeader extends StatelessWidget {
  final _CalView view;
  final DateTime selectedDay;
  final ValueChanged<_CalView> onViewChanged;
  final ValueChanged<int> onNavigate;
  final VoidCallback onToday;
  final VoidCallback onSchedule;
  final VoidCallback onAdd;

  const _CalendarHeader({
    required this.view,
    required this.selectedDay,
    required this.onViewChanged,
    required this.onNavigate,
    required this.onToday,
    required this.onSchedule,
    required this.onAdd,
  });

  String get _title {
    switch (view) {
      case _CalView.day:
        return DateFormat('MMMM yyyy').format(selectedDay);
      case _CalView.week:
        return 'Week of ${DateFormat('MMM d').format(selectedDay)}';
      case _CalView.month:
        return DateFormat('MMMM yyyy').format(selectedDay);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final surfaceColor = isDark ? const Color(0xFF1A1A24) : Colors.white;

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 12, 8),
      decoration: BoxDecoration(
        color: surfaceColor,
        border: Border(
          bottom: BorderSide(
            color: isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0),
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  _title,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.4,
                  ),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.auto_awesome_rounded, size: 20),
                tooltip: 'Smart Schedule',
                color: const Color(0xFFF59E0B),
                onPressed: onSchedule,
                style: IconButton.styleFrom(
                  backgroundColor: const Color(0xFFF59E0B).withValues(alpha: 0.1),
                ),
              ),
              const SizedBox(width: 4),
              TextButton(
                onPressed: onToday,
                style: TextButton.styleFrom(
                  foregroundColor: AppTheme.primaryColor,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                ),
                child: const Text('Today', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
              ),
              IconButton(icon: const Icon(Icons.chevron_left_rounded), onPressed: () => onNavigate(-1), iconSize: 22),
              IconButton(icon: const Icon(Icons.chevron_right_rounded), onPressed: () => onNavigate(1), iconSize: 22),
            ],
          ),
          const SizedBox(height: 8),
          _ViewToggle(current: view, onChanged: onViewChanged),
        ],
      ),
    );
  }
}

class _ViewToggle extends StatelessWidget {
  final _CalView current;
  final ValueChanged<_CalView> onChanged;
  const _ViewToggle({required this.current, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF252532) : const Color(0xFFF0F1F6),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final v in _CalView.values) _ToggleItem(v: v, current: current, onChanged: onChanged),
        ],
      ),
    );
  }
}

class _ToggleItem extends StatelessWidget {
  final _CalView v;
  final _CalView current;
  final ValueChanged<_CalView> onChanged;
  const _ToggleItem({required this.v, required this.current, required this.onChanged});

  String get _label => v.name[0].toUpperCase() + v.name.substring(1);

  @override
  Widget build(BuildContext context) {
    final isSelected = v == current;
    return GestureDetector(
      onTap: () => onChanged(v),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? AppTheme.primaryColor : Colors.transparent,
          borderRadius: BorderRadius.circular(9),
          boxShadow: isSelected
              ? [BoxShadow(color: AppTheme.primaryColor.withValues(alpha: 0.3), blurRadius: 8, offset: const Offset(0, 2))]
              : null,
        ),
        child: Text(
          _label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: isSelected ? Colors.white : Colors.grey,
          ),
        ),
      ),
    );
  }
}

// ─── Week Strip ───────────────────────────────────────────────────────────────

class _WeekStrip extends StatefulWidget {
  final DateTime selected;
  final ValueChanged<DateTime> onDayTap;
  const _WeekStrip({required this.selected, required this.onDayTap});

  @override
  State<_WeekStrip> createState() => _WeekStripState();
}

class _WeekStripState extends State<_WeekStrip> {
  late PageController _pageController;
  late int _basePage;

  DateTime _weekStart(DateTime d) {
    final wd = d.weekday;
    return DateTime(d.year, d.month, d.day - (wd - 1));
  }

  @override
  void initState() {
    super.initState();
    _basePage = 1000;
    _pageController = PageController(initialPage: _basePage);
  }

  @override
  void didUpdateWidget(covariant _WeekStrip oldWidget) {
    super.didUpdateWidget(oldWidget);
    // keep page in sync when header arrows are pressed
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final surfaceColor = isDark ? const Color(0xFF1A1A24) : Colors.white;
    final selectedWeekStart = _weekStart(widget.selected);

    return Container(
      height: 70,
      color: surfaceColor,
      child: Row(
        children: List.generate(7, (i) {
          final day = selectedWeekStart.add(Duration(days: i));
          final isSelected = isSameDay(day, widget.selected);
          final isToday = isSameDay(day, DateTime.now());
          return Expanded(
            child: GestureDetector(
              onTap: () => widget.onDayTap(day),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    DateFormat('E').format(day).substring(0, 2).toUpperCase(),
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: isToday ? AppTheme.primaryColor : Colors.grey,
                      letterSpacing: 0.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: isSelected ? AppTheme.primaryColor : Colors.transparent,
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: Text(
                        '${day.day}',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: isSelected
                              ? Colors.white
                              : isToday
                                  ? AppTheme.primaryColor
                                  : null,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        }),
      ),
    );
  }
}

bool isSameDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;

// ─── Day Timeline ─────────────────────────────────────────────────────────────

class _DayTimeline extends StatelessWidget {
  final CalendarProvider cal;
  final DateTime selected;
  final ScrollController scrollController;

  const _DayTimeline({required this.cal, required this.selected, required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final events = cal.getEventsForDay(selected);
    return _TimelineGrid(
      events: events,
      columns: 1,
      columnDates: [selected],
      scrollController: scrollController,
    );
  }
}

// ─── Week Timeline ────────────────────────────────────────────────────────────

class _WeekTimeline extends StatelessWidget {
  final CalendarProvider cal;
  final DateTime selected;

  const _WeekTimeline({required this.cal, required this.selected});

  @override
  Widget build(BuildContext context) {
    final wd = selected.weekday;
    final monday = DateTime(selected.year, selected.month, selected.day - (wd - 1));
    final days = List.generate(7, (i) => monday.add(Duration(days: i)));
    final now = DateTime.now();
    final offset = ((now.hour - 2).clamp(0, 22)) * kHourHeight;

    return _TimelineGrid(
      events: cal.events,
      columns: 7,
      columnDates: days,
      scrollController: ScrollController(initialScrollOffset: offset),
    );
  }
}

// ─── Shared Timeline Grid ─────────────────────────────────────────────────────

class _TimelineGrid extends StatefulWidget {
  final List<CalendarEvent> events;
  final int columns;
  final List<DateTime> columnDates;
  final ScrollController scrollController;

  const _TimelineGrid({
    required this.events,
    required this.columns,
    required this.columnDates,
    required this.scrollController,
  });

  @override
  State<_TimelineGrid> createState() => _TimelineGridState();
}

class _TimelineGridState extends State<_TimelineGrid> {
  // The event currently being dragged (to show ghost).
  CalendarEvent? _draggingEvent;
  // The snapped DateTime the drag is hovering over.
  DateTime? _hoverTime;
  // GlobalKey lets us convert global coords → local column coords.
  final _columnKey = GlobalKey();

  // ── Coord helpers ──────────────────────────────────────────────────────────

  /// Convert a global [offset] into a snapped [DateTime] within the timeline column.
  DateTime? _globalToTime(Offset globalOffset) {
    final ro = _columnKey.currentContext?.findRenderObject() as RenderBox?;
    if (ro == null) return null;
    final local = ro.globalToLocal(globalOffset);
    // Account for scroll offset in the SingleChildScrollView.
    final scrollY = widget.scrollController.hasClients ? widget.scrollController.offset : 0.0;
    final y = local.dy + scrollY;
    // Clamp y to valid range.
    final clampedY = y.clamp(0.0, kHourHeight * (kDayEnd - kDayStart) - 1);
    final totalMinutes = (clampedY / kHourHeight * 60).round();
    // Snap to 15-minute grid.
    final snapped = ((totalMinutes / 15).round() * 15).clamp(0, 23 * 60 + 45);
    // Determine which column date the x coord lands on.
    final colW = ro.size.width / widget.columns;
    final colIdx = (local.dx / colW).floor().clamp(0, widget.columns - 1);
    final colDate = widget.columnDates[colIdx];
    return DateTime(colDate.year, colDate.month, colDate.day, snapped ~/ 60, snapped % 60);
  }

  // ── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final gridLineColor = isDark ? const Color(0xFF252532) : const Color(0xFFEEEFF5);
    final timeTextColor = isDark ? Colors.grey.shade600 : Colors.grey.shade400;
    final totalHeight = kHourHeight * (kDayEnd - kDayStart);

    return SingleChildScrollView(
      controller: widget.scrollController,
      child: SizedBox(
        height: totalHeight,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Time gutter
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
                      child: Text(
                        h == 0 ? '' : DateFormat('h a').format(DateTime(2000, 1, 1, h)),
                        textAlign: TextAlign.right,
                        style: TextStyle(fontSize: 10, color: timeTextColor, fontWeight: FontWeight.w500),
                      ),
                    ),
                ],
              ),
            ),
            // Column(s) — wrapped in DragTarget
            Expanded(
              child: DragTarget<CalendarEvent>(
                onWillAcceptWithDetails: (_) => true,
                onMove: (details) {
                  final t = _globalToTime(details.offset);
                  if (t != _hoverTime) setState(() => _hoverTime = t);
                },
                onLeave: (_) => setState(() => _hoverTime = null),
                onAcceptWithDetails: (details) async {
                  final event = details.data;
                  final newStart = _globalToTime(details.offset);
                  setState(() {
                    _draggingEvent = null;
                    _hoverTime = null;
                  });
                  if (newStart == null) return;
                  final duration = event.endTime.difference(event.startTime);
                  final newEnd = newStart.add(duration);
                  final updated = event.copyWith(startTime: newStart, endTime: newEnd);
                  await context.read<CalendarProvider>().updateEvent(updated);
                },
                builder: (ctx, candidateData, rejectedData) {
                  final isOver = candidateData.isNotEmpty;
                  return Stack(
                    key: _columnKey,
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
                            color: h % 6 == 0 ? gridLineColor : gridLineColor.withValues(alpha: 0.5),
                          ),
                        ),
                      // Column separators (week view)
                      if (widget.columns > 1)
                        LayoutBuilder(builder: (ctx2, constraints) {
                          final colW = constraints.maxWidth / widget.columns;
                          return Stack(
                            children: [
                              for (int c = 1; c < widget.columns; c++)
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
                      // Today highlight (week view)
                      if (widget.columns > 1)
                        LayoutBuilder(builder: (ctx2, constraints) {
                          final now = DateTime.now();
                          for (int c = 0; c < widget.columnDates.length; c++) {
                            if (isSameDay(widget.columnDates[c], now)) {
                              final colW = constraints.maxWidth / widget.columns;
                              return Positioned(
                                left: c * colW,
                                top: 0,
                                width: colW,
                                height: totalHeight,
                                child: Container(color: AppTheme.primaryColor.withValues(alpha: 0.03)),
                              );
                            }
                          }
                          return const SizedBox.shrink();
                        }),
                      // Events
                      LayoutBuilder(builder: (ctx2, constraints) {
                        final colW = constraints.maxWidth / widget.columns;
                        return Stack(
                          children: [
                            for (final event in widget.events) ...[  
                              Builder(builder: (bctx) {
                                int colIdx = 0;
                                if (widget.columns > 1) {
                                  colIdx = widget.columnDates.indexWhere((d) => isSameDay(d, event.startTime));
                                  if (colIdx < 0) return const SizedBox.shrink();
                                }
                                final startMinutes = event.startTime.hour * 60 + event.startTime.minute;
                                final durationMin = event.durationInMinutes.clamp(15, 1440);
                                final top = (startMinutes / 60) * kHourHeight;
                                final height = (durationMin / 60) * kHourHeight;
                                final isDragging = _draggingEvent?.id == event.id;
                                return Positioned(
                                  left: colIdx * colW + 2,
                                  top: top,
                                  width: colW - 4,
                                  height: height.clamp(24.0, double.infinity),
                                  child: AnimatedOpacity(
                                    duration: const Duration(milliseconds: 150),
                                    opacity: isDragging ? 0.35 : 1.0,
                                    child: _EventBlock(
                                      event: event,
                                      onDragStarted: () => setState(() => _draggingEvent = event),
                                      onDragEnd: () => setState(() => _draggingEvent = null),
                                    ),
                                  ),
                                );
                              }),
                            ],
                            // Drag drop-zone indicator
                            if (isOver && _hoverTime != null)
                              _DragIndicator(
                                hoverTime: _hoverTime!,
                                draggingEvent: candidateData.first,
                                colW: colW,
                                columns: widget.columns,
                                columnDates: widget.columnDates,
                              ),
                            // Current time line
                            _CurrentTimeLine(colW: constraints.maxWidth),
                          ],
                        );
                      }),
                    ],
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Current Time Line ────────────────────────────────────────────────────────

class _CurrentTimeLine extends StatefulWidget {
  final double colW;
  const _CurrentTimeLine({required this.colW});

  @override
  State<_CurrentTimeLine> createState() => _CurrentTimeLineState();
}

class _CurrentTimeLineState extends State<_CurrentTimeLine> {
  late Timer _timer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (_) => setState(() => _now = DateTime.now()));
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
      top: top,
      left: 0,
      right: 0,
      child: Row(
        children: [
          Container(
            width: 10,
            height: 10,
            decoration: const BoxDecoration(color: Color(0xFFEF4444), shape: BoxShape.circle),
          ),
          Expanded(child: Container(height: 1.5, color: const Color(0xFFEF4444))),
        ],
      ),
    );
  }
}

// ─── Drag Indicator ──────────────────────────────────────────────────────────

class _DragIndicator extends StatelessWidget {
  final DateTime hoverTime;
  final CalendarEvent? draggingEvent;
  final double colW;
  final int columns;
  final List<DateTime> columnDates;

  const _DragIndicator({
    required this.hoverTime,
    required this.draggingEvent,
    required this.colW,
    required this.columns,
    required this.columnDates,
  });

  @override
  Widget build(BuildContext context) {
    final colIdx = columnDates.indexWhere((d) => isSameDay(d, hoverTime));
    final left = colIdx < 0 ? 0.0 : colIdx * colW;
    final w = colIdx < 0 ? colW : colW;
    final top = ((hoverTime.hour * 60 + hoverTime.minute) / 60) * kHourHeight;
    final durMin = draggingEvent?.durationInMinutes ?? 60;
    final height = (durMin / 60) * kHourHeight;
    final color = draggingEvent != null
        ? (draggingEvent!.color != null ? draggingEvent!.colorObject : _typeColor(draggingEvent!.eventType))
        : AppTheme.primaryColor;

    return Stack(
      children: [
        // Ghost block
        Positioned(
          left: left + 2,
          top: top,
          width: w - 4,
          height: height.clamp(24.0, double.infinity),
          child: Container(
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.25),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: color, width: 1.5),
            ),
          ),
        ),
        // Time pill label
        Positioned(
          left: left + 6,
          top: top - 20,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(20),
              boxShadow: [BoxShadow(color: color.withValues(alpha: 0.4), blurRadius: 8)],
            ),
            child: Text(
              DateFormat('h:mm a').format(hoverTime),
              style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700),
            ),
          ),
        ),
      ],
    );
  }
}

// ─── Event Block ──────────────────────────────────────────────────────────────

class _EventBlock extends StatelessWidget {
  final CalendarEvent event;
  final VoidCallback? onDragStarted;
  final VoidCallback? onDragEnd;

  const _EventBlock({
    required this.event,
    this.onDragStarted,
    this.onDragEnd,
  });

  Widget _buildCard(BuildContext context, {double opacity = 1.0}) {
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = isDark ? color.withValues(alpha: 0.18) : color.withValues(alpha: 0.12);
    final dmin = event.durationInMinutes;
    return Opacity(
      opacity: opacity,
      child: Container(
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(6),
          border: Border(left: BorderSide(color: color, width: 3)),
        ),
        padding: const EdgeInsets.fromLTRB(6, 4, 4, 4),
        child: dmin < 30
            ? Row(children: [
                Expanded(
                  child: Text(
                    event.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: color),
                  ),
                ),
              ])
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    event.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: color),
                  ),
                  if (dmin >= 45)
                    Text(
                      DateFormat('h:mm a').format(event.startTime),
                      style: TextStyle(fontSize: 10, color: color.withValues(alpha: 0.75)),
                    ),
                ],
              ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    return LongPressDraggable<CalendarEvent>(
      data: event,
      delay: const Duration(milliseconds: 350),
      onDragStarted: onDragStarted,
      onDraggableCanceled: (v, _) => onDragEnd?.call(),
      onDragCompleted: onDragEnd,
      hapticFeedbackOnStart: true,
      // The widget shown under the finger while dragging
      feedback: Material(
        color: Colors.transparent,
        child: SizedBox(
          width: 180,
          child: Container(
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.9),
              borderRadius: BorderRadius.circular(8),
              boxShadow: [BoxShadow(color: color.withValues(alpha: 0.45), blurRadius: 16, offset: const Offset(0, 6))],
            ),
            padding: const EdgeInsets.fromLTRB(10, 8, 8, 8),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(event.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Colors.white)),
                Text(DateFormat('h:mm a').format(event.startTime),
                    style: const TextStyle(fontSize: 11, color: Colors.white70)),
              ],
            ),
          ),
        ),
      ),
      // What remains in place (ghosted)
      childWhenDragging: _buildCard(context, opacity: 0.3),
      child: GestureDetector(
        onTap: () => showModalBottomSheet(
          context: context,
          backgroundColor: Colors.transparent,
          isScrollControlled: true,
          builder: (_) => _EventDetailSheet(event: event),
        ),
        child: _buildCard(context),
      ),
    );
  }
}


// ─── Month View ───────────────────────────────────────────────────────────────

class _MonthView extends StatelessWidget {
  final CalendarProvider cal;
  final DateTime selected;
  final ValueChanged<DateTime> onDayTap;

  const _MonthView({required this.cal, required this.selected, required this.onDayTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final firstDay = DateTime(selected.year, selected.month, 1);
    final daysInMonth = DateTime(selected.year, selected.month + 1, 0).day;
    final startOffset = (firstDay.weekday - 1) % 7;
    final totalCells = startOffset + daysInMonth;
    final rows = (totalCells / 7).ceil();
    const dayHeaders = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

    return Column(
      children: [
        // Day labels
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          child: Row(
            children: dayHeaders.map((d) => Expanded(
              child: Center(
                child: Text(d,
                    style: const TextStyle(
                        fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 0.5)),
              ),
            )).toList(),
          ),
        ),
        Divider(height: 1, color: isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0)),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(8),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 7,
              childAspectRatio: 0.75,
              mainAxisSpacing: 4,
              crossAxisSpacing: 4,
            ),
            itemCount: rows * 7,
            itemBuilder: (ctx, idx) {
              final dayNum = idx - startOffset + 1;
              if (dayNum < 1 || dayNum > daysInMonth) return const SizedBox.shrink();
              final day = DateTime(selected.year, selected.month, dayNum);
              final isSel = isSameDay(day, selected);
              final isToday = isSameDay(day, DateTime.now());
              final evts = cal.getEventsForDay(day);
              return GestureDetector(
                onTap: () => onDayTap(day),
                child: Container(
                  decoration: BoxDecoration(
                    color: isSel
                        ? AppTheme.primaryColor
                        : isToday
                            ? AppTheme.primaryColor.withValues(alpha: 0.1)
                            : Colors.transparent,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  padding: const EdgeInsets.all(4),
                  child: Column(
                    children: [
                      Text(
                        '$dayNum',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: isSel ? Colors.white : isToday ? AppTheme.primaryColor : null,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Wrap(
                        spacing: 2,
                        runSpacing: 2,
                        children: evts
                            .take(3)
                            .map((e) => Container(
                                  width: 6,
                                  height: 6,
                                  decoration: BoxDecoration(
                                    color: isSel
                                        ? Colors.white.withValues(alpha: 0.8)
                                        : (e.color != null ? e.colorObject : _typeColor(e.eventType)),
                                    shape: BoxShape.circle,
                                  ),
                                ))
                            .toList(),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
        // Selected day events preview
        if (cal.selectedDayEvents.isNotEmpty) ...[
          Divider(height: 1, color: isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0)),
          SizedBox(
            height: 180,
            child: ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: cal.selectedDayEvents.length,
              itemBuilder: (_, i) => _EventListTile(event: cal.selectedDayEvents[i]),
            ),
          ),
        ],
      ],
    );
  }
}

// ─── Event List Tile (Month preview) ─────────────────────────────────────────

class _EventListTile extends StatelessWidget {
  final CalendarEvent event;
  const _EventListTile({required this.event});

  @override
  Widget build(BuildContext context) {
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    return GestureDetector(
      onTap: () => showModalBottomSheet(
        context: context,
        backgroundColor: Colors.transparent,
        isScrollControlled: true,
        builder: (_) => _EventDetailSheet(event: event),
      ),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(10),
          border: Border(left: BorderSide(color: color, width: 3)),
        ),
        child: Row(
          children: [
            Icon(_typeIcon(event.eventType), size: 14, color: color),
            const SizedBox(width: 8),
            Expanded(
              child: Text(event.title,
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: color)),
            ),
            Text(
              DateFormat('h:mm a').format(event.startTime),
              style: TextStyle(fontSize: 11, color: color.withValues(alpha: 0.75)),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Event Detail Sheet ───────────────────────────────────────────────────────

class _EventDetailSheet extends StatelessWidget {
  final CalendarEvent event;
  const _EventDetailSheet({required this.event});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    final bg = isDark ? const Color(0xFF1A1A24) : Colors.white;

    return Container(
      margin: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(24)),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 12, bottom: 8),
              width: 40,
              height: 4,
              decoration: BoxDecoration(color: Colors.grey.withValues(alpha: 0.3), borderRadius: BorderRadius.circular(2)),
            ),
          ),
          // Header stripe
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 0),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(14),
              border: Border(left: BorderSide(color: color, width: 4)),
            ),
            child: Row(
              children: [
                Icon(_typeIcon(event.eventType), color: color, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(event.title,
                      style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: color)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(event.eventType,
                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: color, letterSpacing: 0.5)),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                _SheetRow(
                  icon: Icons.access_time_rounded,
                  text: '${DateFormat('EEE, MMM d · h:mm a').format(event.startTime)} → ${DateFormat('h:mm a').format(event.endTime)}',
                  sub: '${event.durationInMinutes} minutes',
                ),
                if (event.location != null) _SheetRow(icon: Icons.location_on_rounded, text: event.location!),
                if (event.description != null) _SheetRow(icon: Icons.notes_rounded, text: event.description!),
                if (event.isFixed) _SheetRow(icon: Icons.lock_rounded, text: 'Fixed time — cannot be rescheduled'),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.edit_rounded, size: 16),
                        label: const Text('Edit'),
                        onPressed: () => Navigator.pop(context),
                        style: OutlinedButton.styleFrom(
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.icon(
                        icon: const Icon(Icons.delete_rounded, size: 16),
                        label: const Text('Delete'),
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFFEF4444),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        onPressed: () async {
                          if (event.id != null) {
                            await context.read<CalendarProvider>().deleteEvent(event.id!);
                          }
                          if (context.mounted) Navigator.pop(context);
                        },
                      ),
                    ),
                  ],
                ),
                SizedBox(height: MediaQuery.of(context).viewInsets.bottom),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SheetRow extends StatelessWidget {
  final IconData icon;
  final String text;
  final String? sub;
  const _SheetRow({required this.icon, required this.text, this.sub});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 16, color: Colors.grey),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(text, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                if (sub != null) Text(sub!, style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Create Event Sheet ───────────────────────────────────────────────────────

class _CreateEventSheet extends StatefulWidget {
  final DateTime selectedDay;
  const _CreateEventSheet({required this.selectedDay});

  @override
  State<_CreateEventSheet> createState() => _CreateEventSheetState();
}

class _CreateEventSheetState extends State<_CreateEventSheet> {
  final _titleController = TextEditingController();
  final _descController = TextEditingController();
  late DateTime _start;
  late DateTime _end;
  String _type = 'OTHER';
  bool _isFixed = false;

  static const _types = ['TASK', 'HABIT', 'WORKOUT', 'STUDY', 'OTHER'];

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _start = DateTime(widget.selectedDay.year, widget.selectedDay.month, widget.selectedDay.day, now.hour, 0);
    _end = _start.add(const Duration(hours: 1));
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickTime(bool isStart) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(isStart ? _start : _end),
    );
    if (picked == null) return;
    setState(() {
      if (isStart) {
        _start = DateTime(_start.year, _start.month, _start.day, picked.hour, picked.minute);
        if (_end.isBefore(_start)) _end = _start.add(const Duration(hours: 1));
      } else {
        _end = DateTime(_end.year, _end.month, _end.day, picked.hour, picked.minute);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = isDark ? const Color(0xFF1A1A24) : Colors.white;

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        margin: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(24)),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 40, height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text('New Event', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, letterSpacing: -0.4)),
              const SizedBox(height: 16),
              // Title
              TextField(
                controller: _titleController,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: 'Event title',
                  prefixIcon: const Icon(Icons.title_rounded, size: 18),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
              const SizedBox(height: 12),
              // Type chips
              const Text('Type', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.grey)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: _types.map((t) {
                  final selected = _type == t;
                  final color = _typeColor(t);
                  return GestureDetector(
                    onTap: () => setState(() => _type = t),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 150),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(
                        color: selected ? color : color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: selected ? color : Colors.transparent),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_typeIcon(t), size: 13, color: selected ? Colors.white : color),
                          const SizedBox(width: 5),
                          Text(t,
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: selected ? Colors.white : color,
                              )),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 14),
              // Time row
              Row(
                children: [
                  Expanded(
                    child: _TimePicker(
                      label: 'Start',
                      time: _start,
                      onTap: () => _pickTime(true),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _TimePicker(
                      label: 'End',
                      time: _end,
                      onTap: () => _pickTime(false),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Fixed toggle
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  border: Border.all(color: isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0)),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.lock_outline_rounded, size: 16, color: Colors.grey),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Fixed time', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                          Text('AI won\'t reschedule this event', style: TextStyle(fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                    ),
                    Switch(
                      value: _isFixed,
                      onChanged: (v) => setState(() => _isFixed = v),
                      activeThumbColor: AppTheme.primaryColor,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              // Description
              TextField(
                controller: _descController,
                maxLines: 2,
                decoration: InputDecoration(
                  hintText: 'Notes (optional)',
                  prefixIcon: const Icon(Icons.notes_rounded, size: 18),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: AppTheme.primaryColor,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  onPressed: () async {
                    if (_titleController.text.trim().isEmpty) return;
                    final event = CalendarEvent(
                      title: _titleController.text.trim(),
                      description: _descController.text.trim().isEmpty ? null : _descController.text.trim(),
                      startTime: _start,
                      endTime: _end,
                      eventType: _type,
                      isFixed: _isFixed,
                    );
                    await context.read<CalendarProvider>().addEvent(event);
                    if (context.mounted) Navigator.pop(context);
                  },
                  child: const Text('Create Event',
                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: Colors.white)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TimePicker extends StatelessWidget {
  final String label;
  final DateTime time;
  final VoidCallback onTap;
  const _TimePicker({required this.label, required this.time, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          border: Border.all(color: isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0)),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text(
              DateFormat('h:mm a').format(time),
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
            ),
          ],
        ),
      ),
    );
  }
}