import 'package:flutter_test/flutter_test.dart';
import 'package:everything_app/models/at_risk_item.dart';
import 'package:everything_app/models/calendar_event.dart';
import 'package:everything_app/providers/calendar_provider.dart';
import 'package:everything_app/services/calendar_service.dart';
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
  // reconcileDelay: zero, damit der Nachlade-Timer nicht als pending timer hängen bleibt.
  final provider = CalendarProvider(calendarService: fake, reconcileDelay: Duration.zero);
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

  group('CalendarProvider.setPinned', () {
    test('flips isFixed optimistically and syncs with the backend', () async {
      final original = _event(isFixed: true);
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      final future = provider.setPinned(1, false);
      // Optimistisch: schon vor dem await sichtbar.
      expect(provider.events.first.isFixed, isFalse);

      expect(await future, isTrue);
      expect(fake.setPinnedCallCount, 1);
      expect(fake.lastPinned, isFalse);
      expect(provider.events.first.isFixed, isFalse);
    });

    test('reverts when the backend rejects the change', () async {
      final original = _event(isFixed: true);
      final fake = _PinFailingCalendarService([original]);
      final provider = await _loadedProvider(fake);

      expect(await provider.setPinned(1, false), isFalse);
      expect(provider.events.first.isFixed, isTrue,
          reason: 'a failed pin change must not leave the UI showing the wrong state');
    });
  });

  group('CalendarProvider.setSkipped', () {
    test('markiert den Block als übersprungen und übernimmt die Serverantwort', () async {
      final original = _event(eventType: 'HABIT');
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      expect(await provider.setSkipped(1, true), isTrue);
      expect(fake.setSkippedCallCount, 1);
      expect(fake.lastSkipped, isTrue);
    });

    test('blendet den übersprungenen Block aus der Ansicht aus', () async {
      final original = _event(eventType: 'HABIT');
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      expect(provider.events, hasLength(1));
      await provider.setSkipped(1, true);

      expect(provider.events, isEmpty,
          reason: 'die Zeit ist wieder frei — der Tag soll auch so aussehen');
      expect(provider.getEventsForDay(original.startTime), isEmpty);
    });

    test('holt eine übersprungene Ausführung wieder zurück', () async {
      final original = _event(eventType: 'PROJECT');
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      await provider.setSkipped(1, true);
      expect(provider.events, isEmpty);

      expect(await provider.setSkipped(1, false), isTrue);
      expect(provider.events, hasLength(1),
          reason: 'das Rückgängig muss den Block wieder sichtbar machen');
      expect(provider.events.first.isSkipped, isFalse);
    });
  });

  group('CalendarProvider reconcile', () {
    test('updateEvent schedules a refetch so backend reflows become visible', () async {
      final original = _event();
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      final before = fake.getEventsInRangeCallCount;
      await provider.updateEvent(original.copyWith(startTime: original.startTime));
      // reconcileDelay ist zero — der Timer feuert im nächsten Event-Loop-Durchlauf. Der Nachlauf
      // fragt seither erst den Scheduler-Status ab und lädt den Monat nur nach, wenn tatsächlich
      // neu geplant wurde; das sind ein paar Mikrotask-Runden mehr als früher.
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(fake.getEventsInRangeCallCount, greaterThan(before),
          reason: 'the provider must refetch after a mutation, not wait for the 30s poll');
      expect(fake.getScheduleStatusCallCount, greaterThan(0),
          reason: 'der Nachlauf soll den billigen Status abfragen, nicht blind den ganzen Monat');
    });

    test('der Indikator geht wieder aus, wenn die Neuplanung durch ist', () async {
      final original = _event();
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      await provider.updateEvent(original.copyWith(startTime: original.startTime));
      expect(provider.isReplanning, isTrue,
          reason: 'unmittelbar nach der Mutation wartet der Kalender auf den Server');

      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(provider.isReplanning, isFalse,
          reason: 'nach dem gemeldeten Lauf darf die Anzeige nicht hängen bleiben');
    });

    test('at-risk aus dem Status landet im Provider', () async {
      final fake = FakeCalendarService([_event()]);
      fake.scheduleStatus = ScheduleStatus(
        lastRunAt: DateTime.now(),
        solverStatus: 'OPTIMAL',
        atRisk: const [
          AtRiskItem(taskId: 7, title: 'Regal bauen', minutes: 240, reason: 'NO_ROOM'),
        ],
      );
      final provider = await _loadedProvider(fake);

      await provider.updateEvent(_event().copyWith(startTime: _event().startTime));
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(provider.atRisk, hasLength(1));
      expect(provider.atRisk.first.title, 'Regal bauen');
      expect(provider.atRisk.first.reasonText, 'kein Platz im Zeitplan');
    });
  });
}

class _PinFailingCalendarService extends FakeCalendarService {
  _PinFailingCalendarService(super.initialEvents);

  @override
  Future<CalendarEvent?> setPinned(int id, bool pinned) async {
    setPinnedCallCount++;
    lastPinned = pinned;
    return null;
  }
}

class _ThrowingCalendarService extends FakeCalendarService {
  _ThrowingCalendarService(super.initialEvents);

  @override
  Future<CalendarEvent?> updateEvent(CalendarEvent event) {
    throw Exception('network down');
  }
}
