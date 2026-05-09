import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../providers/calendar_provider.dart';
import '../../providers/task_provider.dart';
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
    case 'STRATEGY':
      return AppTheme.primaryColor;
    case 'HABIT':
    case 'FINANCE':
      return AppTheme.financeColor;
    case 'WORKOUT':
    case 'GYM':
      return AppTheme.sportsColor;
    case 'STUDY':
      return AppTheme.studyColor;
    default:
      return AppTheme.primaryColor;
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
  _CalView _view = _CalView.week;
  late ScrollController _timelineScrollController;
  late PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: 10000);
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
    final cal = context.watch<CalendarProvider>();
    final selected = cal.selectedDay ?? DateTime.now();

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
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

                _pageController.jumpToPage(10000);
              },
              onSchedule: () => _showScheduleDialog(context),
            ),
            if (_view == _CalView.week)
              _WeekStrip(
                selected: selected,
                onDayTap: (d) {
                  cal.setSelectedDay(d);
                  cal.setFocusedDay(d);
                },
              ),
            Expanded(
  child: PageView.builder(
    controller: _pageController,
    onPageChanged: (index) {
      // Nur den Provider informieren, damit der Header (Monat/Jahr) sich aktualisiert
      final cal = context.read<CalendarProvider>();
      final delta = index - 10000;
      final now = DateTime.now(); // Basis ist heute
      
      DateTime targetDate;
      if (_view == _CalView.day) {
        targetDate = now.add(Duration(days: delta));
      } else if (_view == _CalView.week) {
        targetDate = now.add(Duration(days: delta * 7));
      } else {
        targetDate = DateTime(now.year, now.month + delta, now.day);
      }
      
      cal.setSelectedDay(targetDate);
      cal.setFocusedDay(targetDate);
      if (_view != _CalView.day) cal.loadEventsForMonth(targetDate);
    },
    itemBuilder: (context, index) {
      final cal = context.watch<CalendarProvider>();
      final delta = index - 10000;
      final now = DateTime.now();

      DateTime pageDate;
      if (_view == _CalView.day) {
        pageDate = now.add(Duration(days: delta));
      } else if (_view == _CalView.week) {
        pageDate = now.add(Duration(days: delta * 7));
      } else {
        pageDate = DateTime(now.year, now.month + delta, 1);
      }


      return _view == _CalView.day
          ? _DayTimeline(cal: cal, selected: pageDate, scrollController: _timelineScrollController)
          : _view == _CalView.week
              ? _WeekTimeline(cal: cal, selected: pageDate)
              : _MonthView(
                  cal: cal,
                  selected: pageDate,
                  onDayTap: (d) {
                    final cal = context.read<CalendarProvider>();
                    cal.setSelectedDay(d);
                    cal.setFocusedDay(d);
  
                  final now = DateTime.now();
                  final differenceInDays = DateTime(d.year, d.month, d.day)
                    .difference(DateTime(now.year, now.month, now.day))
                    .inDays;
  

                  setState(() => _view = _CalView.day);
  

                  _pageController.jumpToPage(10000 + differenceInDays);
                },
      );
    },
  ),
),
          ],
        ),
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
}

// ─── Calendar Header ─────────────────────────────────────────────────────────

class _CalendarHeader extends StatelessWidget {
  final _CalView view;
  final DateTime selectedDay;
  final ValueChanged<_CalView> onViewChanged;
  final ValueChanged<int> onNavigate;
  final VoidCallback onToday;
  final VoidCallback onSchedule;

  const _CalendarHeader({
    required this.view,
    required this.selectedDay,
    required this.onViewChanged,
    required this.onNavigate,
    required this.onToday,
    required this.onSchedule,
  });

  int _getWeekNumber(DateTime date) {
    int dayOfYear = int.parse(DateFormat('D').format(date));
    return ((dayOfYear - date.weekday + 10) / 7).floor();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final surfaceColor = isDark ? const Color(0xFF0E0E0E) : Colors.white;
    final borderColor = const Color(0xFF484848).withValues(alpha: 0.15);

    String dateTitle;
    String dateSubtitle;
    if (view == _CalView.day) {
      dateTitle = DateFormat('MMMM d').format(selectedDay);
      dateSubtitle = DateFormat('EEEE').format(selectedDay).toUpperCase();
    } else if (view == _CalView.week) {
      dateTitle = DateFormat('MMMM yyyy').format(selectedDay);
      dateSubtitle = 'Week ${_getWeekNumber(selectedDay)}';
    } else {
      dateTitle = DateFormat('MMMM yyyy').format(selectedDay);
      dateSubtitle = 'Month View';
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Top Bar (unverändert)
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          decoration: BoxDecoration(
            color: surfaceColor,
            border: Border(bottom: BorderSide(color: borderColor)),
          ),
          child: Row(
            children: [
              Icon(Icons.calendar_today_outlined, color: theme.colorScheme.primary, size: 24),
              const SizedBox(width: 12),
              const Text('Calendar', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: Colors.white, fontFamily: 'Manrope')),
            ],
          ),
        ),
        
        // Der neue Sub-header Bereich
        Container(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
          color: surfaceColor,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
 

Expanded(
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (view == _CalView.day)
        Text(dateSubtitle, 
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, letterSpacing: 2.0, color: theme.colorScheme.onSurfaceVariant, fontFamily: 'Manrope'))
      else ...[
        // Jahreszahl über dem Monat (nur in Woche/Monat Ansicht)
        Text(DateFormat('yyyy').format(selectedDay), 
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 1.0, color: theme.colorScheme.primary.withValues(alpha: 0.8), fontFamily: 'Manrope')),
        const SizedBox(height: 2),
        Text(DateFormat('MMMM').format(selectedDay), // Nur der Monat, groß
          overflow: TextOverflow.ellipsis, 
          style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800, letterSpacing: -1.0, color: Colors.white, fontFamily: 'Manrope')),
      ],
      const SizedBox(height: 4),
      if (view == _CalView.day)
        Text(dateTitle, 
          style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, letterSpacing: -2.0, color: Colors.white, fontFamily: 'Manrope', height: 1.0))
      else
        Text(dateSubtitle, 
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: theme.colorScheme.onSurfaceVariant, fontFamily: 'Manrope')),
    ],
  ),
),

              
              // Rechte Seite: Kompakter Steuerungsblock
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      GestureDetector(
                        onTap: onToday,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          child: Text('TODAY', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w900, color: theme.colorScheme.primary, fontFamily: 'Manrope')),
                        ),
                      ),
                      const SizedBox(width: 4),
                      _ViewDropDown(current: view, onChanged: onViewChanged),
                    ],
                  ),
                  // Pfeile direkt unter TODAY & Kalender-Icon
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        onPressed: () => onNavigate(-1),
                        icon: const Icon(Icons.chevron_left_rounded, size: 22),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        visualDensity: VisualDensity.compact,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(width: 12),
                      IconButton(
                        onPressed: () => onNavigate(1),
                        icon: const Icon(Icons.chevron_right_rounded, size: 22),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        visualDensity: VisualDensity.compact,
                        color: theme.colorScheme.primary,
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ViewDropDown extends StatelessWidget {
  final _CalView current;
  final ValueChanged<_CalView> onChanged;

  const _ViewDropDown({required this.current, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    return PopupMenuButton<_CalView>(
      initialValue: current,
      onSelected: onChanged,
      // Das kleine Kalender-Icon oben rechts
      icon: Icon(Icons.calendar_view_day_rounded, color: theme.colorScheme.primary, size: 22),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 120), // Sorgt für ein schlichtes, schmales Menü
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), // Passend zu deinem restlichen Design
      itemBuilder: (context) => _CalView.values.map((view) {
        final isSelected = view == current;
        return PopupMenuItem<_CalView>(
          value: view,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                view.name[0].toUpperCase() + view.name.substring(1),
                style: const TextStyle(
                  fontFamily: 'Manrope',
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              if (isSelected)
                Icon(Icons.check, color: theme.colorScheme.primary, size: 18),
            ],
          ),
        );
      }).toList(),
    );
  }
}

// ─── Week Strip ───────────────────────────────────────────────────────────────

class _WeekStrip extends StatelessWidget {
  final DateTime selected;
  final ValueChanged<DateTime> onDayTap;
  const _WeekStrip({required this.selected, required this.onDayTap});

  DateTime _weekStart(DateTime d) {
    final wd = d.weekday;
    return DateTime(d.year, d.month, d.day - (wd - 1));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final surfaceColor = theme.scaffoldBackgroundColor;
    final selectedWeekStart = _weekStart(selected);

    return Container(
      color: theme.colorScheme.outlineVariant.withValues(alpha: 0.1), // background for gap-px
      child: Row(
        children: [
          Container(
            width: kTimeGutterWidth,
            height: 60,
            color: surfaceColor,
          ),
          Expanded(
            child: Row(
              children: List.generate(7, (i) {
                final day = selectedWeekStart.add(Duration(days: i));
                
                final isToday = isSameDay(day, DateTime.now());
                return Expanded(
                  child: GestureDetector(
                    onTap: () => onDayTap(day),
                    child: Container(
                      margin: const EdgeInsets.only(left: 1), // gap-px
                      height: 60,
                      color: surfaceColor,
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            DateFormat('E').format(day).substring(0, 3).toUpperCase(),
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w800,
                              color: 
                              isToday
                                  ? theme.colorScheme.primary
                                  :theme.colorScheme.onSurfaceVariant,
                            
                              letterSpacing: 1.0,
                              fontFamily: 'Manrope',
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${day.day}',
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w800,
                              fontFamily: 'Manrope',
                              color: isToday
                                  ? theme.colorScheme.primary
                                  : (day.weekday >= 6 ? theme.colorScheme.error : Colors.white),
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
    final gridLineColor = Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3);
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
                      child: Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: Text(
                          h == 0 ? '' : DateFormat('h a').format(DateTime(2000, 1, 1, h)),
                          textAlign: TextAlign.right,
                          style: TextStyle(fontSize: 10, color: timeTextColor, fontWeight: FontWeight.w500),
                        ),
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
                            color: h % 2 == 0 ? Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3) : Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.1),
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
                              return Stack(
                                children: [
                                  Positioned(
                                    left: c * colW,
                                    top: 0,
                                    width: colW,
                                    height: totalHeight,
                                    child: Container(color: AppTheme.primaryColor.withValues(alpha: 0.03)),
                                  ),
                                ],
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
      top: top - 6,
      left: 0,
      right: 0,
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            margin: const EdgeInsets.only(left: 2, right: 4),
            decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle, boxShadow: [BoxShadow(color: Colors.white, blurRadius: 4)]),
          ),
          Expanded(child: Container(height: 1, color: Colors.white.withValues(alpha: 0.5))),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
            color: Colors.white,
            child: const Text('NOW', style: TextStyle(color: Colors.black, fontSize: 8, fontWeight: FontWeight.w900, letterSpacing: -0.5)),
          ),
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
              borderRadius: BorderRadius.zero,
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
              borderRadius: BorderRadius.zero,
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
    final bg = isDark ? color.withValues(alpha: 0.20) : color.withValues(alpha: 0.15);
    final dmin = event.durationInMinutes;
    return Opacity(
      opacity: opacity,
      child: Container(
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.zero,
          border: Border(left: BorderSide(color: color, width: 4)),
        ),
        padding: const EdgeInsets.fromLTRB(6, 4, 4, 4),
        child: dmin < 30
            ? Row(children: [
                Expanded(
                  child: Text(
                    event.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: color),
                  ),
                ),
              ])
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    event.eventType.toUpperCase(),
                    style: TextStyle(fontSize: 8, fontWeight: FontWeight.w800, letterSpacing: 1.2, color: color.withValues(alpha: 0.8)),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    event.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: color, height: 1.1),
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
              borderRadius: BorderRadius.zero,
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
                    borderRadius: BorderRadius.zero,
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
                                    shape: BoxShape.rectangle,
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
          borderRadius: BorderRadius.zero,
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
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.zero),
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
              borderRadius: BorderRadius.zero,
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
                    borderRadius: BorderRadius.zero,
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
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
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
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
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
