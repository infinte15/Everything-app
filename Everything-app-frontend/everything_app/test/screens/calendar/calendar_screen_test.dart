import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:everything_app/models/calendar_event.dart';
import 'package:everything_app/providers/calendar_provider.dart';
import 'package:everything_app/providers/task_provider.dart';
import 'package:everything_app/screens/calendar/calendar_screen.dart';
import '../../support/fake_calendar_service.dart';

/// [CalendarScreen] positions events using [DateTime.now()]-relative navigation, and
/// [CalendarProvider.ensureScheduleGenerated] re-fetches with a window starting at the
/// exact moment it runs (a little after these fixtures are built). Anchoring fixtures
/// to "minutes from now" rather than a fixed clock time keeps them (a) inside that
/// re-fetch window, since it's always in the future relative to fixture construction,
/// and (b) within the currently-displayed week almost always, since the offsets are small.
DateTime _soon(int minutes) => DateTime.now().add(Duration(minutes: minutes));

Future<CalendarProvider> _pumpCalendar(
  WidgetTester tester, {
  required FakeCalendarService fake,
}) async {
  // The default 800x600 test surface is narrower/shorter than any real device this
  // screen targets and clips the event detail sheet by a couple of pixels; give it
  // realistic room instead of shrinking the sheet's content to fit a synthetic size.
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  // Disposed explicitly by each test (not via addTearDown) so the 30s polling Timer
  // is guaranteed cancelled before flutter_test's end-of-test "no pending timers"
  // invariant check runs, rather than depending on addTearDown/invariant-check ordering.
  final provider = CalendarProvider(calendarService: fake);
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
        startTime: _soon(30),
        endTime: _soon(90),
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
      // LongPressDraggable requires holding past its configured delay before a
      // move is recognized as a drag rather than a tap.
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

    testWidgets('a fixed (pinned) event cannot be dragged', (tester) async {
      final pinned = CalendarEvent(
        id: 2,
        title: 'Pinned Meeting',
        startTime: _soon(30),
        endTime: _soon(90),
        eventType: 'FIXED',
        isFixed: true,
      );
      final fake = FakeCalendarService([pinned]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Pinned Meeting');
      expect(eventFinder, findsOneWidget);

      // No LongPressDraggable should wrap a fixed event at all.
      final draggableAncestor = find.ancestor(
        of: eventFinder,
        matching: find.byType(LongPressDraggable<CalendarEvent>),
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

    testWidgets('a movable (non-fixed) event is wrapped in a LongPressDraggable', (tester) async {
      final movable = CalendarEvent(
        id: 3,
        title: 'Movable Task',
        startTime: _soon(30),
        endTime: _soon(90),
        eventType: 'TASK',
        isFixed: false,
      );
      final fake = FakeCalendarService([movable]);
      final provider = await _pumpCalendar(tester, fake: fake);

      final eventFinder = find.text('Movable Task');
      final draggableAncestor = find.ancestor(
        of: eventFinder,
        matching: find.byType(LongPressDraggable<CalendarEvent>),
      );
      expect(draggableAncestor, findsOneWidget);

      provider.dispose();
    });
  });

  group('CalendarScreen event detail sheet', () {
    testWidgets('tapping an event opens the detail sheet with its info', (tester) async {
      final event = CalendarEvent(
        id: 4,
        title: 'Standup',
        description: 'Daily sync',
        startTime: _soon(30),
        endTime: _soon(60),
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
        startTime: _soon(30),
        endTime: _soon(60),
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
