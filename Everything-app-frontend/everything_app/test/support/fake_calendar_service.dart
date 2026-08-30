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
  int setSkippedCallCount = 0;
  bool? lastSkipped;

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
  Future<CalendarEvent?> setSkipped(int id, bool skipped) async {
    setSkippedCallCount++;
    lastSkipped = skipped;
    final idx = events.indexWhere((e) => e.id == id);
    if (idx == -1) return null;
    // copyWith kann skippedAt nicht auf null zuruecksetzen, deshalb hier neu gebaut.
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
      relatedProjectId: e.relatedProjectId,
      completedAt: e.completedAt,
      skippedAt: skipped ? DateTime.now() : null,
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

  /// Pflicht-Override wie oben — ohne ihn liefe im Test die echte Netzwerkfassung, und der
  /// Nachlauf nach einer Mutation bräche an der fehlenden Binding-Initialisierung ab.
  ///
  /// Vorgabe ist "ein neuer Lauf bei jedem Abruf": der Nachlauf im Provider bricht ab, sobald der
  /// Server einen neueren Lauf meldet, und genau dieser Normalfall soll in den Tests gelten. Wer
  /// das Gegenteil braucht (Nachlauf läuft ins Leere), setzt [scheduleStatus] auf einen festen
  /// Wert oder null.
  int getScheduleStatusCallCount = 0;
  ScheduleStatus? scheduleStatus;
  bool scheduleStatusAdvances = true;

  @override
  Future<ScheduleStatus?> getScheduleStatus() async {
    getScheduleStatusCallCount++;
    if (!scheduleStatusAdvances) return scheduleStatus;
    if (scheduleStatus != null) return scheduleStatus;
    final zeit = DateTime.now().add(Duration(seconds: getScheduleStatusCallCount));
    return ScheduleStatus(
      lastRunAt: zeit,
      // lastRunAtRaw gehoert dazu, auch wenn kein Test danach fragt: die echte
      // ScheduleStatus.fromJson setzt IMMER beide Felder. Fehlte es hier, loeschte der
      // Statusabruf im Nachlauf den Rohwert wieder, den der Long-Poll gerade gesetzt hat — und
      // die naechste Warte-Anfrage ginge ohne `since` raus. Ein Fehler, den nur die Untreue der
      // Attrappe erzeugt haette.
      lastRunAtRaw: zeit.toIso8601String(),
      solverStatus: 'OPTIMAL',
      scheduledBlocks: events.length,
    );
  }

  /// Pflicht-Override, aus demselben Grund wie [getScheduleStatus] — und hier waere ein Vergessen
  /// besonders unangenehm: die echte Fassung wartet bis zu 35 Sekunden, der Test bliebe ohne jede
  /// Ausgabe haengen.
  int awaitScheduleRunCallCount = 0;
  String? lastAwaitSince;

  /// Antwortschlange. Ist sie leer, gilt [awaitFallback]; ist AUCH die null, heisst das "204,
  /// es ist nichts passiert".
  final List<ScheduleStatus?> awaitResponses = [];

  /// Vorgabe: bei jedem Abruf ein neuerer Lauf. Der Provider laedt dann nach, was in den meisten
  /// Tests der interessante Fall ist. [changedBlocks] bleibt null = "unbekannt", damit sich
  /// Bestandstests wie vorher verhalten.
  ScheduleStatus? awaitFallback;

  @override
  Future<ScheduleStatus?> awaitScheduleRun({String? since}) async {
    awaitScheduleRunCallCount++;
    lastAwaitSince = since;
    if (awaitResponses.isNotEmpty) return awaitResponses.removeAt(0);
    if (awaitFallback != null) return awaitFallback;

    // Immer ein Zeitstempel, der neuer ist als alles Vorherige — sonst gilt die Antwort im
    // Provider als "kein neuer Lauf" und er laedt nicht nach.
    final zeit = DateTime.now().add(Duration(seconds: awaitScheduleRunCallCount));

    // [scheduleStatus] bleibt die eine Stellschraube fuer den Inhalt, damit ein Test nicht zwei
    // Felder setzen muss, um dasselbe zu sagen. Uebernommen wird alles ausser der Zeit.
    final vorlage = scheduleStatus;
    return ScheduleStatus(
      lastRunAt: zeit,
      lastRunAtRaw: zeit.toIso8601String(),
      solverStatus: vorlage?.solverStatus ?? 'OPTIMAL',
      scheduledBlocks: vorlage?.scheduledBlocks ?? events.length,
      changedBlocks: vorlage?.changedBlocks,
      atRisk: vorlage?.atRisk ?? const [],
    );
  }
}
