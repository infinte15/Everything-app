import 'package:flutter_test/flutter_test.dart';
import 'package:everything_app/models/calendar_event.dart';
import 'package:everything_app/providers/calendar_provider.dart';
import '../support/fake_calendar_service.dart';

CalendarEvent _event({
  int id = 1,
  DateTime? start,
  DateTime? end,
  bool isFixed = false,
  String eventType = 'TASK',
}) {
  // Provider tests populate _events via loadEventsForMonth(DateTime.now()), which
  // queries the current real month — fixtures must fall within it, not a fixed date.
  final now = DateTime.now();
  final s = start ?? DateTime(now.year, now.month, 15, 10, 0);
  final e = end ?? s.add(const Duration(hours: 1));
  return CalendarEvent(
    id: id,
    title: 'Event $id',
    startTime: s,
    endTime: e,
    eventType: eventType,
    isFixed: isFixed,
  );
}

Future<CalendarProvider> _loadedProvider(FakeCalendarService fake) async {
  final provider = CalendarProvider(calendarService: fake);
  addTearDown(provider.dispose);
  await provider.loadEventsForMonth(DateTime.now());
  return provider;
}

void main() {
  group('CalendarProvider.updateEvent', () {
    test('applies the change optimistically before the network call resolves', () async {
      final original = _event();
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      final movedStart = original.startTime.add(const Duration(hours: 4));
      final moved = original.copyWith(startTime: movedStart, endTime: movedStart.add(const Duration(hours: 1)));

      // Don't await yet — the optimistic update should already be visible synchronously.
      final future = provider.updateEvent(moved);
      expect(provider.events.first.startTime, movedStart);

      final success = await future;
      expect(success, isTrue);
      expect(provider.events.first.startTime, movedStart);
      expect(fake.updateCallCount, 1);
    });

    test('reverts the optimistic update when the backend call fails', () async {
      final original = _event();
      final fake = FakeCalendarService([original])..failUpdates = true;
      final provider = await _loadedProvider(fake);

      final movedStart = original.startTime.add(const Duration(hours: 4));
      final moved = original.copyWith(startTime: movedStart);
      final success = await provider.updateEvent(moved);

      expect(success, isFalse);
      expect(provider.events.first.startTime, original.startTime,
          reason: 'a failed backend update must roll back the optimistic local change');
    });

    test('reverts on network exception, not just a null response', () async {
      final original = _event();
      final fake = _ThrowingCalendarService([original]);
      final provider = await _loadedProvider(fake);

      final movedStart = original.startTime.add(const Duration(hours: 4));
      final moved = original.copyWith(startTime: movedStart);
      final success = await provider.updateEvent(moved);

      expect(success, isFalse);
      expect(provider.events.first.startTime, original.startTime);
    });
  });

  group('CalendarProvider.ensureScheduleGenerated', () {
    test('triggers exactly one generateSchedule call across repeated invocations', () async {
      final fake = FakeCalendarService();
      final provider = CalendarProvider(calendarService: fake);
      addTearDown(provider.dispose);

      await provider.ensureScheduleGenerated();
      await provider.ensureScheduleGenerated();
      await provider.ensureScheduleGenerated();

      expect(fake.generateScheduleCallCount, 1,
          reason: 'the automatic backfill must run once per app session, not on every call');
    });
  });
}

class _ThrowingCalendarService extends FakeCalendarService {
  _ThrowingCalendarService(super.initialEvents);

  @override
  Future<CalendarEvent?> updateEvent(CalendarEvent event) {
    throw Exception('network down');
  }
}
