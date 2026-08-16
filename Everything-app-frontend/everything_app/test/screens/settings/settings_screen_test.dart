import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:everything_app/models/user_preferences.dart';
import 'package:everything_app/providers/auth_provider.dart';
import 'package:everything_app/providers/calendar_provider.dart';
import 'package:everything_app/providers/preferences_provider.dart';
import 'package:everything_app/screens/settings/settings_screen.dart';
import '../../support/fake_calendar_service.dart';
import '../../support/fake_preferences_service.dart';

Future<CalendarProvider> _pumpSettings(
  WidgetTester tester, {
  required FakePreferencesService fake,
}) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  // Der CalendarProvider wird nach dem Speichern für scheduleReconcile() gebraucht.
  final calendar = CalendarProvider(
    calendarService: FakeCalendarService(),
    reconcileDelay: Duration.zero,
  );

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<PreferencesProvider>(
          create: (_) => PreferencesProvider(service: fake),
        ),
        ChangeNotifierProvider<CalendarProvider>.value(value: calendar),
        ChangeNotifierProvider<AuthProvider>(create: (_) => AuthProvider()),
      ],
      child: const MaterialApp(home: SettingsScreen()),
    ),
  );
  await tester.pumpAndSettle();
  return calendar;
}

void main() {
  testWidgets('loads the current preferences and shows the working hours', (tester) async {
    final fake = FakePreferencesService(const UserPreferences(
      workdayStart: TimeOfDay(hour: 9, minute: 0),
      workdayEnd: TimeOfDay(hour: 17, minute: 30),
      personalHoursStart: TimeOfDay(hour: 6, minute: 30),
      personalHoursEnd: TimeOfDay(hour: 22, minute: 30),
      bufferMinutes: 10,
    ));
    final calendar = await _pumpSettings(tester, fake: fake);

    expect(find.text('09:00'), findsOneWidget);
    expect(find.text('17:30'), findsOneWidget);
    expect(find.text('10 min'), findsOneWidget);
    // Die Privatzeiten sind ein eigenes Zeitpaar und duerfen nicht mit den Arbeitszeiten
    // verwechselt werden: an ihnen haengen Gewohnheiten und Trainings.
    expect(find.text('06:30'), findsOneWidget);
    expect(find.text('22:30'), findsOneWidget);

    calendar.dispose();
  });

  testWidgets('changing the buffer and saving sends the new value', (tester) async {
    final fake = FakePreferencesService(const UserPreferences(
      workdayStart: TimeOfDay(hour: 8, minute: 0),
      workdayEnd: TimeOfDay(hour: 22, minute: 0),
      bufferMinutes: 0,
      maxTaskMinutesPerDay: 480,
    ));
    final calendar = await _pumpSettings(tester, fake: fake);

    // "+" der Puffer-Zeile: die erste Add-Schaltfläche auf dem Bildschirm.
    await tester.tap(find.byIcon(Icons.add_rounded).first);
    await tester.pumpAndSettle();
    expect(find.text('5 min'), findsOneWidget);

    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(fake.saveCallCount, 1);
    expect(fake.lastSaved?.bufferMinutes, 5,
        reason: 'the edited buffer must reach the backend');
    expect(fake.lastSaved?.maxTaskMinutesPerDay, 480,
        reason: 'untouched fields must be sent unchanged, not dropped');
    expect(find.text('Settings saved'), findsOneWidget);

    calendar.dispose();
  });

  testWidgets('the break between blocks can be set and reaches the backend', (tester) async {
    final fake = FakePreferencesService(const UserPreferences(
      workdayStart: TimeOfDay(hour: 8, minute: 0),
      workdayEnd: TimeOfDay(hour: 22, minute: 0),
      bufferMinutes: 0,
      breakDurationMinutes: 0,
    ));
    final calendar = await _pumpSettings(tester, fake: fake);

    expect(find.text('Break between blocks'), findsOneWidget);

    // Zweite Stepper-Zeile: Puffer, dann Pause.
    await tester.tap(find.byIcon(Icons.add_rounded).at(1));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(fake.lastSaved?.breakDurationMinutes, 5);
    expect(fake.lastSaved?.bufferMinutes, 0, reason: 'die Puffer-Zeile bleibt unberührt');

    calendar.dispose();
  });

  testWidgets('a failed save surfaces the error instead of pretending it worked',
      (tester) async {
    final fake = FakePreferencesService(const UserPreferences(
      workdayStart: TimeOfDay(hour: 8, minute: 0),
      workdayEnd: TimeOfDay(hour: 22, minute: 0),
    ))
      ..failSaves = true;
    final calendar = await _pumpSettings(tester, fake: fake);

    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(find.text('Settings saved'), findsNothing);
    expect(find.textContaining('Fehler beim Speichern'), findsOneWidget);

    calendar.dispose();
  });
}
