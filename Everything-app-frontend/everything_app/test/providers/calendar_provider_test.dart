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
  // Kein reconcileDelay mehr: der Nachlauf ist seit dem Long-Poll ein Future statt einer
  // Timer-Leiter, es kann also gar kein pending timer mehr hängen bleiben.
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
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(fake.getEventsInRangeCallCount, greaterThan(before),
          reason: 'the provider must refetch after a mutation, not wait for the 30s poll');
      expect(fake.awaitScheduleRunCallCount, 1,
          reason: 'der Nachlauf wartet auf den Server, statt ihn in einer Leiter abzufragen');
    });

    test('fünf schnelle Änderungen öffnen genau eine Warte-Anfrage', () async {
      final original = _event();
      final fake = FakeCalendarService([original]);
      final provider = await _loadedProvider(fake);

      // Ohne die _wartet-Sperre lägen jetzt fünf HTTP-Anfragen gleichzeitig beim Server, die alle
      // auf dasselbe Ereignis warten — genau das, was der Koordinator serverseitig auch vermeidet.
      for (var i = 0; i < 5; i++) {
        provider.scheduleReconcile();
      }
      expect(fake.awaitScheduleRunCallCount, lessThanOrEqualTo(1));

      for (var i = 0; i < 10; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      // Die zwischenzeitlichen Meldungen sind nicht verloren: nach der ersten Antwort wird genau
      // einmal nachgefasst, nicht viermal.
      expect(fake.awaitScheduleRunCallCount, 2);
    });

    test('meldet der Server 0 geänderte Blöcke, entfällt der Monatsabruf', () async {
      final fake = FakeCalendarService([_event()]);
      final provider = await _loadedProvider(fake);

      final zeit = DateTime.now().add(const Duration(minutes: 1));
      fake.awaitFallback = ScheduleStatus(
        lastRunAt: zeit,
        lastRunAtRaw: zeit.toIso8601String(),
        solverStatus: 'OPTIMAL',
        changedBlocks: 0,
      );

      final before = fake.getEventsInRangeCallCount;
      provider.scheduleReconcile();
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(fake.getEventsInRangeCallCount, before,
          reason: 'der Lauf hat nichts bewegt — den ganzen Monat zu laden wäre reine Verschwendung');
      expect(provider.isReplanning, isFalse);
    });

    test('null geänderte Blöcke heißt unbekannt und wird geladen', () async {
      final fake = FakeCalendarService([_event()]);
      final provider = await _loadedProvider(fake);

      final zeit = DateTime.now().add(const Duration(minutes: 1));
      // changedBlocks fehlt: ein Server, der nicht mitzählt, darf keinen falschen Kalender
      // erzeugen — im Zweifel wird geladen.
      fake.awaitFallback = ScheduleStatus(
        lastRunAt: zeit,
        lastRunAtRaw: zeit.toIso8601String(),
        solverStatus: 'OPTIMAL',
      );

      final before = fake.getEventsInRangeCallCount;
      provider.scheduleReconcile();
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(fake.getEventsInRangeCallCount, greaterThan(before));
    });

    test('eine leere Antwort (204) lässt die Anzeige nicht hängen', () async {
      final fake = FakeCalendarService([_event()]);
      final provider = await _loadedProvider(fake);

      fake.awaitResponses.add(null);

      final before = fake.getEventsInRangeCallCount;
      provider.scheduleReconcile();
      expect(provider.isReplanning, isTrue);

      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(provider.isReplanning, isFalse,
          reason: 'ohne Lauf im Zeitfenster darf der Balken nicht stehen bleiben');
      expect(fake.getEventsInRangeCallCount, before,
          reason: 'es ist nichts passiert, also gibt es auch nichts nachzuladen');
    });

    test('der zuletzt gesehene Lauf geht als since zurück', () async {
      final fake = FakeCalendarService([_event()]);
      final provider = await _loadedProvider(fake);

      provider.scheduleReconcile();
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }
      final ersterWert = fake.lastAwaitSince;

      provider.scheduleReconcile();
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(fake.lastAwaitSince, isNotNull);
      expect(fake.lastAwaitSince, isNot(ersterWert),
          reason: 'die zweite Anfrage muss den Lauf kennen, den die erste gemeldet hat — sonst '
              'antwortet der Server sofort mit demselben Ergebnis und die App fragt endlos nach');
    });

    test('nach dispose während einer offenen Anfrage fliegt nichts', () async {
      final fake = FakeCalendarService([_event()]);
      final provider = CalendarProvider(calendarService: fake);
      await provider.loadEventsForMonth(DateTime.now());

      provider.scheduleReconcile();
      // Eine Warte-Anfrage kann 35 Sekunden offen sein und überlebt damit jeden Screenwechsel.
      provider.dispose();

      for (var i = 0; i < 10; i++) {
        await Future<void>.delayed(Duration.zero);
      }
      // Kein expect nötig: ein notifyListeners nach dispose würde den Test von selbst umwerfen.
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

    test('nur echte Deadline-Risiken kommen ins Warnband', () async {
      final fake = FakeCalendarService([_event()]);
      fake.scheduleStatus = ScheduleStatus(
        lastRunAt: DateTime.now(),
        solverStatus: 'OPTIMAL',
        atRisk: const [
          AtRiskItem(taskId: 7, title: 'Regal bauen', minutes: 240, reason: 'OUTSIDE_HORIZON'),
          AtRiskItem(taskId: 8, title: 'Abgabe', minutes: 60, reason: 'WOULD_MISS_DEADLINE'),
        ],
      );
      final provider = await _loadedProvider(fake);

      await provider.updateEvent(_event().copyWith(startTime: _event().startTime));
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(provider.atRisk, hasLength(2), reason: 'die Kachel zeigt beides an');
      expect(provider.atRiskDeadlines.map((e) => e.title), ['Abgabe'],
          reason: 'eine Aufgabe ohne Deadline kann keinen Termin reißen — kein Warnband');
    });

    test('überfällig und gefährdet landen in getrennten Bändern', () async {
      // Fest auf heute, nicht "in drei Stunden": sonst faellt der Test um, sobald er spaet
      // abends laeuft und die drei Stunden ueber Mitternacht reichen.
      final jetzt = DateTime.now();
      final nachholtermin = DateTime(jetzt.year, jetzt.month, jetzt.day, 21, 30);
      final fake = FakeCalendarService([_event()]);
      fake.scheduleStatus = ScheduleStatus(
        lastRunAt: DateTime.now(),
        solverStatus: 'OPTIMAL',
        atRisk: [
          AtRiskItem(
            taskId: 8,
            title: 'Abgabe',
            minutes: 60,
            reason: 'WOULD_MISS_DEADLINE',
          ),
          AtRiskItem(
            taskId: 9,
            title: 'Steuererklärung',
            minutes: 0,
            reason: 'PAST_DEADLINE',
            plannedStart: nachholtermin,
          ),
        ],
      );
      final provider = await _loadedProvider(fake);

      await provider.updateEvent(_event().copyWith(startTime: _event().startTime));
      for (var i = 0; i < 6; i++) {
        await Future<void>.delayed(Duration.zero);
      }

      expect(provider.atRiskOverdue.map((e) => e.title), ['Steuererklärung']);
      expect(provider.atRiskUpcoming.map((e) => e.title), ['Abgabe']);
      expect(provider.atRiskOverdue.first.plannedStartText, startsWith('heute '),
          reason: 'die überfällige Aufgabe muss ihren Nachholtermin nennen');
    });
  });

  group('CalendarProvider.getUpcomingEventsForDay', () {
    // Ein Homescreen-Tag hat drei Sorten Block, und nur eine davon interessiert am Nachmittag.
    test('heute bleibt nur, was noch nicht vorbei und nicht abgehakt ist', () async {
      final now = DateTime.now();
      final vorbei = _event(
        id: 1,
        start: now.subtract(const Duration(hours: 3)),
        end: now.subtract(const Duration(hours: 2)),
      );
      final laeuft = _event(
        id: 2,
        start: now.subtract(const Duration(minutes: 20)),
        end: now.add(const Duration(minutes: 40)),
      );
      final kommt = _event(
        id: 3,
        start: now.add(const Duration(hours: 2)),
        end: now.add(const Duration(hours: 3)),
      );
      final abgehakt = _event(
        id: 4,
        start: now.add(const Duration(hours: 4)),
        end: now.add(const Duration(hours: 5)),
      ).copyWith(completedAt: now);

      final provider = await _loadedProvider(
          FakeCalendarService([vorbei, laeuft, kommt, abgehakt]));

      expect(provider.getUpcomingEventsForDay(now).map((e) => e.id), [2, 3],
          reason: 'der laufende Block gehört dazu, der beendete und der abgehakte nicht');
    });

    test('sortiert nach Startzeit, egal wie der Server liefert', () async {
      final now = DateTime.now();
      final morgen = DateTime(now.year, now.month, now.day).add(const Duration(days: 1));
      // Absichtlich verdreht: ohne ORDER BY liefert Postgres die Zeilen in Speicherreihenfolge,
      // und die aendert sich bei jedem Scheduler-Lauf.
      final spaet = _event(id: 1, start: morgen.add(const Duration(hours: 16)));
      final frueh = _event(id: 2, start: morgen.add(const Duration(hours: 8)));
      final mittag = _event(id: 3, start: morgen.add(const Duration(hours: 12)));

      final provider = await _loadedProvider(FakeCalendarService([spaet, frueh, mittag]));

      expect(provider.getUpcomingEventsForDay(morgen).map((e) => e.id), [2, 3, 1]);
    });

    test('ein verschobener Termin rutscht in der Liste an seine neue Stelle', () async {
      final now = DateTime.now();
      final morgen = DateTime(now.year, now.month, now.day).add(const Duration(days: 1));
      final frueh = _event(id: 1, start: morgen.add(const Duration(hours: 8)));
      final spaet = _event(id: 2, start: morgen.add(const Duration(hours: 16)));

      final provider = await _loadedProvider(FakeCalendarService([frueh, spaet]));

      // Der optimistische Pfad ersetzt den Eintrag an Ort und Stelle — ohne Sortierung stuende
      // der nach hinten geschobene Termin weiterhin oben.
      final verschoben = morgen.add(const Duration(hours: 20));
      await provider.updateEvent(frueh.copyWith(
        startTime: verschoben,
        endTime: verschoben.add(const Duration(hours: 1)),
      ));

      expect(provider.getUpcomingEventsForDay(morgen).map((e) => e.id), [2, 1]);
    });

    test('ein anderer Tag zeigt unverändert alles', () async {
      final now = DateTime.now();
      // Ein Tag desselben Monats, der sicher nicht heute ist.
      final andererTag = now.day == 1 ? DateTime(now.year, now.month, 2)
                                      : DateTime(now.year, now.month, 1);
      final frueh = _event(
        id: 1,
        start: andererTag.add(const Duration(hours: 8)),
        end: andererTag.add(const Duration(hours: 9)),
      );
      final spaet = _event(
        id: 2,
        start: andererTag.add(const Duration(hours: 18)),
        end: andererTag.add(const Duration(hours: 19)),
      );

      final provider = await _loadedProvider(FakeCalendarService([frueh, spaet]));

      expect(provider.getUpcomingEventsForDay(andererTag).map((e) => e.id), [1, 2],
          reason: '"was kommt noch" ergibt nur für heute Sinn — sonst wäre der Tag leer');
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
