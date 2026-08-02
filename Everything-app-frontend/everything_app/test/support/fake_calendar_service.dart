import 'package:everything_app/models/calendar_event.dart';
import 'package:everything_app/services/calendar_service.dart';

/// In-memory stand-in for [CalendarService] so provider/widget tests don't hit the network.
/// Extends the real service and overrides every method it uses; [ApiService] internals are
/// never touched because none of the overrides call super.
class FakeCalendarService extends CalendarService {
  FakeCalendarService([List<CalendarEvent>? initialEvents])
      : events = List.of(initialEvents ?? const []);

  final List<CalendarEvent> events;

  bool failUpdates = false;
  int updateCallCount = 0;
  int generateScheduleCallCount = 0;
  bool generateScheduleSucceeds = true;

  @override
  Future<List<CalendarEvent>> getEventsInRange(DateTime startDate, DateTime endDate) async {
    return events
        .where((e) => !e.startTime.isBefore(startDate) && !e.startTime.isAfter(endDate))
        .toList();
  }

  @override
  Future<CalendarEvent?> createEvent(CalendarEvent event) async {
    final created = event.copyWith(id: event.id ?? events.length + 1);
    events.add(created);
    return created;
  }

  @override
  Future<CalendarEvent?> updateEvent(CalendarEvent event) async {
    updateCallCount++;
    if (failUpdates) return null;
    final idx = events.indexWhere((e) => e.id == event.id);
    if (idx != -1) events[idx] = event;
    return event;
  }

  @override
  Future<bool> deleteEvent(int id) async {
    events.removeWhere((e) => e.id == id);
    return true;
  }

  @override
  Future<Map<String, dynamic>> generateSchedule(DateTime startDate, DateTime endDate) async {
    generateScheduleCallCount++;
    if (!generateScheduleSucceeds) {
      return {'success': false, 'error': 'boom'};
    }
    return {'success': true, 'data': {}};
  }
}
