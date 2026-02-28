import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:table_calendar/table_calendar.dart';
import 'package:intl/intl.dart';
import '../../providers/calendar_provider.dart';
import '../../config/app_theme.dart';
import '../../models/calendar_event.dart';

class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key});

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen>
    with SingleTickerProviderStateMixin {
  CalendarFormat _calendarFormat = CalendarFormat.month;
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final cal = context.read<CalendarProvider>();
      cal.setSelectedDay(DateTime.now());
      cal.loadEventsForMonth(DateTime.now());
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cal = context.watch<CalendarProvider>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Kalender'),
        actions: [
          IconButton(
            icon: const Icon(Icons.auto_awesome),
            tooltip: 'Smart Schedule',
            onPressed: () => _showScheduleDialog(context),
          ),
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () => _showCreateEventDialog(context),
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Monat'),
            Tab(text: 'Woche'),
            Tab(text: 'Tag'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // Month View
          _MonthView(cal: cal),
          // Week View
          _WeekView(cal: cal),
          // Day View
          _DayView(cal: cal),
        ],
      ),
    );
  }

  void _showScheduleDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Row(children: [
          Icon(Icons.auto_awesome, color: Colors.amber),
          SizedBox(width: 8),
          Text('Smart Schedule'),
        ]),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Soll der intelligente Planer automatisch den optimalen '
              'Zeitplan für die nächste Woche erstellen?',
            ),
            SizedBox(height: 16),
            Text(
              'Er berücksichtigt alle deine Tasks, Habits, '
              'Workouts und Kurse.',
              style: TextStyle(fontSize: 13, color: Colors.grey),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () async {
              Navigator.pop(context);
              final now = DateTime.now();
              final nextWeek = now.add(const Duration(days: 7));
              final result = await context
                  .read<CalendarProvider>()
                  .generateSchedule(now, nextWeek);
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(result['success']
                        ? '✅ Zeitplan erfolgreich erstellt!'
                        : '❌ Fehler: ${result['error']}'),
                  ),
                );
              }
            },
            child: const Text('Jetzt planen'),
          ),
        ],
      ),
    );
  }

  void _showCreateEventDialog(BuildContext context) {
    final titleController = TextEditingController();
    DateTime startTime = DateTime.now();
    DateTime endTime = DateTime.now().add(const Duration(hours: 1));

    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Neues Event'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: titleController,
              decoration: const InputDecoration(
                labelText: 'Titel',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            const Text('Start- und Endzeit über Kalender auswählen',
                style: TextStyle(fontSize: 13, color: Colors.grey)),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () async {
              if (titleController.text.isEmpty) return;
              final event = CalendarEvent(
                title: titleController.text,
                startTime: startTime,
                endTime: endTime,
                eventType: 'OTHER',
              );
              await context.read<CalendarProvider>().addEvent(event);
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('Erstellen'),
          ),
        ],
      ),
    );
  }
}

// ─── Month View ────────────────────────────────────────────────────────────────

class _MonthView extends StatelessWidget {
  final CalendarProvider cal;
  const _MonthView({required this.cal});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        TableCalendar(
          firstDay: DateTime.utc(2020, 1, 1),
          lastDay: DateTime.utc(2030, 12, 31),
          focusedDay: cal.focusedDay,
          selectedDayPredicate: (day) => isSameDay(cal.selectedDay, day),
          calendarFormat: CalendarFormat.month,
          eventLoader: (day) => cal.getEventsForDay(day),
          startingDayOfWeek: StartingDayOfWeek.monday,
          calendarStyle: CalendarStyle(
            selectedDecoration: BoxDecoration(
              color: AppTheme.primaryColor,
              shape: BoxShape.circle,
            ),
            todayDecoration: BoxDecoration(
              color: AppTheme.primaryColor.withOpacity(0.3),
              shape: BoxShape.circle,
            ),
            markerDecoration: BoxDecoration(
              color: AppTheme.primaryColor,
              shape: BoxShape.circle,
            ),
          ),
          headerStyle: const HeaderStyle(
            formatButtonVisible: false,
            titleCentered: true,
          ),
          onDaySelected: (selectedDay, focusedDay) {
            cal.setSelectedDay(selectedDay);
            cal.setFocusedDay(focusedDay);
          },
          onPageChanged: (focusedDay) {
            cal.setFocusedDay(focusedDay);
            cal.loadEventsForMonth(focusedDay);
          },
        ),
        const Divider(),
        Expanded(
          child: _EventsList(
            events: cal.selectedDayEvents,
            selectedDay: cal.selectedDay ?? DateTime.now(),
          ),
        ),
      ],
    );
  }
}

// ─── Week View ─────────────────────────────────────────────────────────────────

class _WeekView extends StatelessWidget {
  final CalendarProvider cal;
  const _WeekView({required this.cal});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TableCalendar(
          firstDay: DateTime.utc(2020, 1, 1),
          lastDay: DateTime.utc(2030, 12, 31),
          focusedDay: cal.focusedDay,
          selectedDayPredicate: (day) => isSameDay(cal.selectedDay, day),
          calendarFormat: CalendarFormat.week,
          eventLoader: (day) => cal.getEventsForDay(day),
          startingDayOfWeek: StartingDayOfWeek.monday,
          calendarStyle: CalendarStyle(
            selectedDecoration: BoxDecoration(
              color: AppTheme.primaryColor,
              shape: BoxShape.circle,
            ),
            todayDecoration: BoxDecoration(
              color: AppTheme.primaryColor.withOpacity(0.3),
              shape: BoxShape.circle,
            ),
          ),
          headerStyle: const HeaderStyle(
            formatButtonVisible: false,
            titleCentered: true,
          ),
          onDaySelected: (selectedDay, focusedDay) {
            cal.setSelectedDay(selectedDay);
            cal.setFocusedDay(focusedDay);
          },
        ),
        const Divider(),
        Expanded(
          child: _EventsList(
            events: cal.selectedDayEvents,
            selectedDay: cal.selectedDay ?? DateTime.now(),
          ),
        ),
      ],
    );
  }
}

// ─── Day View ──────────────────────────────────────────────────────────────────

class _DayView extends StatelessWidget {
  final CalendarProvider cal;
  const _DayView({required this.cal});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final events = cal.selectedDayEvents;
    final day = cal.selectedDay ?? DateTime.now();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton(
                icon: const Icon(Icons.chevron_left),
                onPressed: () {
                  cal.setSelectedDay(day.subtract(const Duration(days: 1)));
                  cal.setFocusedDay(day.subtract(const Duration(days: 1)));
                },
              ),
              Text(
                DateFormat('EEEE, dd. MMMM', 'de_DE').format(day),
                style: theme.textTheme.titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold),
              ),
              IconButton(
                icon: const Icon(Icons.chevron_right),
                onPressed: () {
                  cal.setSelectedDay(day.add(const Duration(days: 1)));
                  cal.setFocusedDay(day.add(const Duration(days: 1)));
                },
              ),
            ],
          ),
        ),
        const Divider(),
        Expanded(
          child: _TimelineView(events: events),
        ),
      ],
    );
  }
}

// ─── Events List ───────────────────────────────────────────────────────────────

class _EventsList extends StatelessWidget {
  final List<CalendarEvent> events;
  final DateTime selectedDay;

  const _EventsList({required this.events, required this.selectedDay});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (events.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.event_available, size: 48, color: Colors.grey),
            const SizedBox(height: 16),
            Text('Keine Events für diesen Tag',
                style:
                    theme.textTheme.bodyLarge?.copyWith(color: Colors.grey)),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: events.length,
      itemBuilder: (_, i) {
        final event = events[i];
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            leading: Container(
              width: 4,
              height: 40,
              decoration: BoxDecoration(
                color: event.colorObject,
                borderRadius: BorderRadius.circular(4),
              ),
            ),
            title: Text(event.title,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            subtitle: Text(
              '${DateFormat('HH:mm').format(event.startTime)} - '
              '${DateFormat('HH:mm').format(event.endTime)}'
              '${event.location != null ? ' • ${event.location}' : ''}',
            ),
            trailing: Chip(
              label: Text(event.eventType,
                  style: const TextStyle(fontSize: 11)),
              padding: EdgeInsets.zero,
              backgroundColor: event.colorObject.withOpacity(0.1),
            ),
            onTap: () => _showEventDetails(context, event),
          ),
        );
      },
    );
  }

  void _showEventDetails(BuildContext context, CalendarEvent event) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => _EventDetailSheet(event: event),
    );
  }
}

// ─── Timeline View ─────────────────────────────────────────────────────────────

class _TimelineView extends StatelessWidget {
  final List<CalendarEvent> events;
  const _TimelineView({required this.events});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (events.isEmpty) {
      return const Center(
        child: Text('Keine Events', style: TextStyle(color: Colors.grey)),
      );
    }
    final sorted = [...events]
      ..sort((a, b) => a.startTime.compareTo(b.startTime));

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: sorted.length,
      itemBuilder: (_, i) {
        final event = sorted[i];
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 50,
              child: Text(
                DateFormat('HH:mm').format(event.startTime),
                style: theme.textTheme.bodySmall
                    ?.copyWith(fontWeight: FontWeight.bold),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: event.colorObject.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                  border:
                      Border.all(color: event.colorObject.withOpacity(0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(event.title,
                        style: const TextStyle(fontWeight: FontWeight.bold)),
                    Text(
                      '${event.durationInMinutes} Min.',
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

// ─── Event Detail Sheet ───────────────────────────────────────────────────────

class _EventDetailSheet extends StatelessWidget {
  final CalendarEvent event;
  const _EventDetailSheet({required this.event});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: event.colorObject,
                    shape: BoxShape.circle,
                  )),
              const SizedBox(width: 8),
              Expanded(
                child: Text(event.title,
                    style: theme.textTheme.titleLarge
                        ?.copyWith(fontWeight: FontWeight.bold)),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _DetailRow(icon: Icons.access_time, text:
              '${DateFormat('HH:mm').format(event.startTime)} - '
              '${DateFormat('HH:mm').format(event.endTime)} '
              '(${event.durationInMinutes} Min.)'),
          if (event.location != null)
            _DetailRow(icon: Icons.location_on, text: event.location!),
          if (event.description != null)
            _DetailRow(icon: Icons.notes, text: event.description!),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.edit),
                  label: const Text('Bearbeiten'),
                  onPressed: () => Navigator.pop(context),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.icon(
                  icon: const Icon(Icons.delete),
                  label: const Text('Löschen'),
                  style: FilledButton.styleFrom(backgroundColor: Colors.red),
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
        ],
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final IconData icon;
  final String text;
  const _DetailRow({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Colors.grey),
          const SizedBox(width: 8),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}