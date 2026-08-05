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
  int getEventsInRangeCallCount = 0;
  int setPinnedCallCount = 0;
  bool? lastPinned;
  int setCompletedCallCount = 0;
  bool? lastCompleted;

  @override
  Future<List<CalendarEvent>> getEventsInRange(DateTime startDate, DateTime endDate) async {
    getEventsInRangeCallCount++;
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

  // Pflicht-Override: keine Methode hier ruft super auf, ein fehlendes Override würde also
  // im Widget-Test die echte Netzwerk-Implementierung ausführen.
  @override
  Future<CalendarEvent?> setPinned(int id, bool pinned) async {
    setPinnedCallCount++;
    lastPinned = pinned;
    final idx = events.indexWhere((e) => e.id == id);
    if (idx == -1) return null;
    events[idx] = events[idx].copyWith(isFixed: pinned);
    return events[idx];
  }

  @override
  Future<CalendarEvent?> setCompleted(int id, bool completed) async {
    setCompletedCallCount++;
    lastCompleted = completed;
    final idx = events.indexWhere((e) => e.id == id);
    if (idx == -1) return null;
    // copyWith kann completedAt nicht auf null zuruecksetzen, deshalb hier neu gebaut.
    final e = events[idx];
    events[idx] = CalendarEvent(
      id: e.id,
      title: e.title,
      description: e.description,
      startTime: e.startTime,
      endTime: e.endTime,
      location: e.location,
      eventType: e.eventType,
      isFixed: e.isFixed,
      color: e.color,
      notes: e.notes,
      relatedTaskId: e.relatedTaskId,
      relatedHabitId: e.relatedHabitId,
      relatedWorkoutId: e.relatedWorkoutId,
      completedAt: completed ? DateTime.now() : null,
    );
    return events[idx];
  }

  @override
  Future<bool> deleteEvent(int id) async {
    events.removeWhere((e) => e.id == id);
    return true;
  }

  @override
  Future<Map<String, dynamic>> generateSchedule(
    DateTime startDate, {
    DateTime? endDate,
  }) async {
    generateScheduleCallCount++;
    if (!generateScheduleSucceeds) {
      return {'success': false, 'error': 'boom'};
    }
    return {'success': true, 'data': {}};
  }
}
