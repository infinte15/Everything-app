import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:everything_app/models/calendar_event.dart';
import 'package:everything_app/providers/calendar_provider.dart';
import 'package:everything_app/providers/task_provider.dart';
import 'package:everything_app/screens/calendar/calendar_screen.dart';
import 'package:everything_app/widgets/pointer_aware_draggable.dart';
import '../../support/fake_calendar_service.dart';

/// Fixture-Zeit: [minutes] Minuten nach 09:00 des heutigen Tages.
///
/// Bewusst eine feste Uhrzeit statt "jetzt + n Minuten". Mit der Uhrzeit als Basis
/// rutschten die Fixtures am Abend ans untere Ende der Leinwand, und ein Drag nach
/// unten landete hinter Mitternacht — also außerhalb des DragTarget, wo gar kein Drop
/// mehr ankommt. Die Drag-Tests fielen dadurch ab etwa 22 Uhr reihenweise um.
///
/// Der heutige Tag genügt: [CalendarProvider.ensureScheduleGenerated] lädt anschließend
/// den kompletten Monat nach, nicht nur ein Fenster ab jetzt.
DateTime _at(int minutes) {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day, 9).add(Duration(minutes: minutes));
}

Future<CalendarProvider> _pumpCalendar(
  WidgetTester tester, {
  required FakeCalendarService fake,
  // The default 800x600 test surface is narrower/shorter than any real device this
  // screen targets and clips the event detail sheet by a couple of pixels; give it
  // realistic room instead of shrinking the sheet's content to fit a synthetic size.
  // Tall enough that the 1536px timeline canvas fits entirely — tests that need the
  // timeline to actually scroll have to ask for a shorter surface.
  Size surface = const Size(1080, 2400),
}) async {
  tester.view.physicalSize = surface;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  // Disposed explicitly by each test (not via addTearDown) so the 30s polling Timer
  // is guaranteed cancelled before flutter_test's end-of-test "no pending timers"
  // invariant check runs, rather than depending on addTearDown/invariant-check ordering.
  // reconcileDelay: zero, damit der Nachlade-Timer nach jeder Mutation nicht als
  // "pending timer" am Testende hängen bleibt.
  final provider = CalendarProvider(calendarService: fake, reconcileDelay: Duration.zero);
  // Pre-load synchronously so the first pump already has real data, instead of
  // racing CalendarScreen's own async initState load.
  await provider.loadEventsForMonth(DateTime.now());

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<CalendarProvider>.value(value: provider),
        ChangeNotifierProvider<TaskProvider>(create: (_) => TaskProvider()),
      ],
      child: const MaterialApp(home: CalendarScreen()),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

void main() {
  group('CalendarScreen drag-and-drop', () {
    testWidgets('dragging a movable event reschedules it to the drop time', (tester) async {
      final original = CalendarEvent(
        id: 1,
        title: 'Draggable Event',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([original]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Draggable Event');
      expect(eventFinder, findsOneWidget);

      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();

      final start = tester.getCenter(eventFinder);
      final gesture = await tester.startGesture(start);
      // Touch requires holding past the configured delay before a move is
      // recognized as a drag rather than a tap.
      await tester.pump(const Duration(milliseconds: 500));
      await gesture.moveBy(const Offset(0, 32));
      await tester.pump(const Duration(milliseconds: 50));
      await gesture.moveBy(const Offset(0, 32));
      await tester.pump(const Duration(milliseconds: 50));
      await gesture.up();
      await tester.pumpAndSettle();

      expect(fake.updateCallCount, greaterThanOrEqualTo(1),
          reason: 'dropping the event should call CalendarProvider.updateEvent');

      final updated = fake.events.firstWhere((e) => e.id == 1);
      expect(updated.startTime.isAfter(original.startTime), isTrue,
          reason: 'the event should have moved later after being dragged downward');
      // 15-minute snap grid — the drop should land on a quarter-hour boundary.
      expect(updated.startTime.minute % 15, 0);

      provider.dispose();
    });

    testWidgets('the drop lands on the time it was dropped at, not at the end of the day',
        (tester) async {
      // Regression: _globalToTime addierte _scroll.offset auf eine Koordinate, die den
      // Scroll-Offset über die Paint-Transform schon enthielt. Das Raster startet bei
      // "jetzt − 2h", also war der doppelt gezählte Wert fast immer jenseits der Leinwand
      // und der clamp zog jeden Drop auf 23:45. Der alte Test prüfte nur "später als vorher"
      // und ging deshalb mit.
      final now = DateTime.now();
      final start = DateTime(now.year, now.month, now.day, 9, 0);
      final original = CalendarEvent(
        id: 40,
        title: 'Precise Event',
        startTime: start,
        endTime: start.add(const Duration(minutes: 60)),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([original]);
      // Kurze Bühne, damit das Raster überhaupt gescrollt ist — ohne Scrollweg wäre der
      // doppelt gezählte Offset null und der Fehler unsichtbar.
      final provider =
          await _pumpCalendar(tester, fake: fake, surface: const Size(1080, 900));

      final eventFinder = find.text('Precise Event');
      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();

      // Den Block mittig ins Sichtfeld holen: gescrollt muss das Raster sein, damit der
      // doppelt gezählte Offset überhaupt auffällt — und mittig, damit der Randscroll
      // nicht mitmischt und das Ergebnis verwischt.
      final timeline = find.ancestor(of: eventFinder, matching: find.byType(Scrollable)).first;
      final pos = tester.state<ScrollableState>(timeline).position;
      pos.jumpTo(9 * kHourHeight - pos.viewportDimension / 2 + kHourHeight / 2);
      await tester.pumpAndSettle();
      expect(pos.pixels, greaterThan(0),
          reason: 'die Regression zeigt sich nur bei gescrolltem Raster');

      // Genau eine Stundenhöhe nach unten ziehen == genau eine Stunde später.
      final gesture = await tester.startGesture(
        tester.getCenter(eventFinder),
        kind: PointerDeviceKind.mouse,
      );
      for (var i = 0; i < 4; i++) {
        await gesture.moveBy(const Offset(0, kHourHeight / 4));
        await tester.pump(const Duration(milliseconds: 16));
      }
      await gesture.up();
      await tester.pumpAndSettle();

      final updated = fake.events.firstWhere((e) => e.id == 40);
      expect(updated.startTime, DateTime(now.year, now.month, now.day, 10, 0),
          reason: 'ein Block, der um eine Stundenhöhe nach unten gezogen wird, '
              'muss eine Stunde später liegen');
      expect(updated.endTime, DateTime(now.year, now.month, now.day, 11, 0));

      provider.dispose();
    });

    testWidgets('a mouse drag reschedules the event without any hold', (tester) async {
      // Regression: mit LongPressDraggable kam mit der Maus nie ein Drag zustande.
      // Dessen DelayedMultiDragGestureRecognizer verwirft die Geste, sobald sich der
      // Zeiger während der Verzögerung weiter als den Hit-Slop bewegt — und der ist
      // für PointerDeviceKind.mouse genau 1 Pixel.
      final original = CalendarEvent(
        id: 30,
        title: 'Mouse Event',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([original]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Mouse Event');
      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();

      final gesture = await tester.startGesture(
        tester.getCenter(eventFinder),
        kind: PointerDeviceKind.mouse,
      );
      // Bewusst ohne Halten und in kleinen Schritten — genau das, was eine Maus tut.
      for (var i = 0; i < 6; i++) {
        await gesture.moveBy(const Offset(0, 12));
        await tester.pump(const Duration(milliseconds: 16));
      }
      await gesture.up();
      await tester.pumpAndSettle();

      expect(fake.updateCallCount, greaterThanOrEqualTo(1),
          reason: 'a mouse drag must reschedule the event just like a touch drag');
      final updated = fake.events.firstWhere((e) => e.id == 30);
      expect(updated.startTime.isAfter(original.startTime), isTrue,
          reason: 'dragging downward with the mouse must move the event later');
      expect(updated.startTime.minute % 15, 0);
      // Die Dauer darf ein Verschieben nicht verändern.
      expect(updated.endTime.difference(updated.startTime),
          original.endTime.difference(original.startTime));

      provider.dispose();
    });

    testWidgets('a mouse click with a tiny jitter still opens the detail sheet', (tester) async {
      // Der Maus-Slop muss größer als das eine Framework-Pixel sein, sonst wird aus
      // jedem leicht verwackelten Klick ein Mini-Drag statt eines Taps.
      final event = CalendarEvent(
        id: 31,
        title: 'Jitter Click',
        description: 'Daily sync',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([event]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Jitter Click');
      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();

      final gesture = await tester.startGesture(
        tester.getCenter(eventFinder),
        kind: PointerDeviceKind.mouse,
      );
      await gesture.moveBy(const Offset(1.5, 1.5));
      await tester.pump(const Duration(milliseconds: 16));
      await gesture.up();
      await tester.pumpAndSettle();

      expect(find.text('Daily sync'), findsOneWidget,
          reason: 'a jittery click must still count as a tap');
      expect(fake.updateCallCount, 0, reason: 'a click must not reschedule anything');

      provider.dispose();
    });

    testWidgets('a touch swipe without holding scrolls instead of dragging', (tester) async {
      // Die Gegenprobe zum Maus-Test: am Finger muss die Verzögerung erhalten bleiben,
      // sonst wird jede Wischgeste, die auf einem Event beginnt, zum Verschieben.
      final event = CalendarEvent(
        id: 32,
        title: 'Swipe Event',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([event]);
      // Kurze Bühne, damit die 1536px hohe Leinwand überhaupt Scrollweg hat.
      final provider =
          await _pumpCalendar(tester, fake: fake, surface: const Size(1080, 900));

      final eventFinder = find.text('Swipe Event');
      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();

      // Der innerste Scrollable über dem Event ist das Zeitraster; byType(...).first
      // wäre die PageView, die auf eine senkrechte Wischgeste gar nicht reagiert.
      final timeline = find.ancestor(of: eventFinder, matching: find.byType(Scrollable)).first;
      final before = tester.state<ScrollableState>(timeline).position.pixels;

      // Nach unten wischen: ensureVisible hat die Leinwand ans Ende gescrollt, nach oben
      // gäbe es keinen Weg mehr. Es ist zugleich die Richtung, die beim Ziehen das Event
      // nach hinten verschieben würde — genau der Unterschied, um den es hier geht.
      final gesture = await tester.startGesture(tester.getCenter(eventFinder));
      for (var i = 0; i < 6; i++) {
        await gesture.moveBy(const Offset(0, 12));
        await tester.pump(const Duration(milliseconds: 16));
      }
      await gesture.up();
      await tester.pumpAndSettle();

      expect(fake.updateCallCount, 0,
          reason: 'a swipe without holding must not move the event');
      expect(tester.state<ScrollableState>(timeline).position.pixels, isNot(before),
          reason: 'the swipe should have scrolled the timeline instead');

      provider.dispose();
    });

    testWidgets('a fixed (pinned) event cannot be dragged', (tester) async {
      final pinned = CalendarEvent(
        id: 2,
        title: 'Pinned Meeting',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'FIXED',
        isFixed: true,
      );
      final fake = FakeCalendarService([pinned]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Pinned Meeting');
      expect(eventFinder, findsOneWidget);

      // No draggable should wrap a fixed event at all.
      final draggableAncestor = find.ancestor(
        of: eventFinder,
        matching: find.byType(PointerAwareDraggable<CalendarEvent>),
      );
      expect(draggableAncestor, findsNothing);

      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();
      final start = tester.getCenter(eventFinder);
      final gesture = await tester.startGesture(start);
      await tester.pump(const Duration(milliseconds: 500));
      await gesture.moveBy(const Offset(0, 64));
      await tester.pump(const Duration(milliseconds: 50));
      await gesture.up();
      await tester.pumpAndSettle();

      expect(fake.updateCallCount, 0,
          reason: 'a pinned event must never trigger a reschedule, even on drag attempt');

      provider.dispose();
    });

    testWidgets('a movable (non-fixed) event is wrapped in a draggable', (tester) async {
      final movable = CalendarEvent(
        id: 3,
        title: 'Movable Task',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([movable]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Movable Task');
      final draggableAncestor = find.ancestor(
        of: eventFinder,
        matching: find.byType(PointerAwareDraggable<CalendarEvent>),
      );
      expect(draggableAncestor, findsOneWidget);

      provider.dispose();
    });

    testWidgets('overlapping events are laid out side by side and each stay draggable',
        (tester) async {
      // Vorher lagen beide auf voller Spaltenbreite übereinander, sodass nur das
      // zuletzt gezeichnete antippbar oder ziehbar war.
      final first = CalendarEvent(
        id: 10,
        title: 'Overlap A',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final second = CalendarEvent(
        id: 11,
        title: 'Overlap B',
        startTime: _at(15),
        endTime: _at(75),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([first, second]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final a = find.text('Overlap A');
      final b = find.text('Overlap B');
      expect(a, findsOneWidget);
      expect(b, findsOneWidget);

      // Unterschiedliche Spuren => unterschiedliche linke Kante.
      expect(tester.getTopLeft(a).dx == tester.getTopLeft(b).dx, isFalse,
          reason: 'overlapping events must be placed in separate lanes');

      for (final finder in [a, b]) {
        expect(
          find.ancestor(of: finder, matching: find.byType(PointerAwareDraggable<CalendarEvent>)),
          findsOneWidget,
          reason: 'each overlapping event must remain independently draggable',
        );
      }

      provider.dispose();
    });

    testWidgets('the week grid keeps its scroll position across a provider notification',
        (tester) async {
      // Regression: _WeekTimeline hat den ScrollController in build() gebaut, sodass jedes
      // notifyListeners() (optimistisches Update, 30s-Poll) zurück auf "jetzt − 2h" sprang.
      final event = CalendarEvent(
        id: 12,
        title: 'Anchor',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([event]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final scrollable = find.byType(Scrollable).first;
      final position = tester.state<ScrollableState>(scrollable).position;
      position.jumpTo(position.pixels + 200);
      await tester.pumpAndSettle();
      final afterScroll = tester.state<ScrollableState>(scrollable).position.pixels;

      // Provider-Update erzwingen, wie es nach einem Drop passiert.
      provider.setSelectedDay(DateTime.now());
      await tester.pumpAndSettle();

      expect(tester.state<ScrollableState>(find.byType(Scrollable).first).position.pixels,
          afterScroll,
          reason: 'a provider notification must not reset the timeline scroll offset');

      provider.dispose();
    });
  });

  group('CalendarScreen pinning', () {
    testWidgets('unpinning a fixed event calls setPinned(false)', (tester) async {
      final pinned = CalendarEvent(
        id: 20,
        title: 'Pinned Task',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: true,
      );
      final fake = FakeCalendarService([pinned]);
      final provider = await _pumpCalendar(tester, fake: fake);

      await tester.tap(find.text('Pinned Task'));
      await tester.pumpAndSettle();

      expect(find.text('Unpin — let AI reschedule'), findsOneWidget);
      await tester.tap(find.text('Unpin — let AI reschedule'));
      await tester.pumpAndSettle();

      expect(fake.setPinnedCallCount, 1);
      expect(fake.lastPinned, isFalse);

      provider.dispose();
    });

    testWidgets('a movable event offers to pin it instead', (tester) async {
      final movable = CalendarEvent(
        id: 21,
        title: 'Movable Task',
        startTime: _at(0),
        endTime: _at(60),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([movable]);
      final provider = await _pumpCalendar(tester, fake: fake);

      await tester.tap(find.text('Movable Task'));
      await tester.pumpAndSettle();

      expect(find.text('Pin to this time'), findsOneWidget);
      await tester.tap(find.text('Pin to this time'));
      await tester.pumpAndSettle();

      expect(fake.lastPinned, isTrue);

      provider.dispose();
    });
  });

  group('CalendarScreen event detail sheet', () {
    testWidgets('tapping an event opens the detail sheet with its info', (tester) async {
      final event = CalendarEvent(
        id: 4,
        title: 'Standup',
        description: 'Daily sync',
        startTime: _at(0),
        endTime: _at(30),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([event]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Standup');
      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();

      // Tapping too fast is interpreted as the start of a long-press-drag; a plain
      // tap (down+up with no hold) exercises the GestureDetector.onTap path instead.
      await tester.tap(eventFinder);
      await tester.pumpAndSettle();

      expect(find.text('Daily sync'), findsOneWidget);
      expect(find.text('Edit'), findsOneWidget);
      expect(find.text('Delete'), findsOneWidget);

      provider.dispose();
    });

    testWidgets('Edit opens a pre-filled form and Save updates the event', (tester) async {
      final event = CalendarEvent(
        id: 5,
        title: 'Standup',
        description: 'Daily sync',
        startTime: _at(0),
        endTime: _at(30),
        eventType: 'STUDY',
        isFixed: false,
      );
      final fake = FakeCalendarService([event]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Standup');
      await tester.ensureVisible(eventFinder);
      await tester.pumpAndSettle();
      await tester.tap(eventFinder);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Edit'));
      await tester.pumpAndSettle();

      expect(find.text('Edit Event'), findsOneWidget);
      // Pre-filled with the existing title.
      expect(find.widgetWithText(TextField, 'Standup'), findsOneWidget);
      // Type selector is hidden in edit mode — type shouldn't change post-creation.
      expect(find.text('Type'), findsNothing);

      await tester.enterText(find.widgetWithText(TextField, 'Standup'), 'Standup Renamed');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();

      final updated = fake.events.firstWhere((e) => e.id == 5);
      expect(updated.title, 'Standup Renamed');
      // Fields not exposed by the edit form must survive the round-trip unchanged.
      expect(updated.eventType, 'STUDY');

      provider.dispose();
    });
  });
}
