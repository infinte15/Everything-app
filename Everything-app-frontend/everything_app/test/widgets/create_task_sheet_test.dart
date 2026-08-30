import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:everything_app/models/task.dart';
import 'package:everything_app/providers/task_provider.dart';
import 'package:everything_app/widgets/create_task_sheet.dart';
import '../support/fake_task_service.dart';

Task _aufgabe({
  int id = 1,
  DateTime? deadline,
  DateTime? notBefore,
  String? description,
  int? minChunk,
  int? maxChunk,
  int? maxChunksPerDay,
}) {
  return Task(
    id: id,
    title: 'Abgabe schreiben',
    description: description,
    priority: 4,
    deadline: deadline,
    estimatedDurationMinutes: 90,
    status: 'TODO',
    spaceType: 'TASKS',
    category: 'Studium',
    splittable: true,
    notBefore: notBefore,
    minChunkMinutes: minChunk,
    maxChunkMinutes: maxChunk,
    maxChunksPerDay: maxChunksPerDay,
  );
}

Future<TaskProvider> _pumpSheet(
  WidgetTester tester,
  FakeTaskService fake, {
  Task? existingTask,
}) async {
  // Hohe Testflaeche: das Sheet ist laenger als die voreingestellten 600 px, und der
  // Speichern-Knopf sitzt ganz unten. Mit ensureVisible allein reicht es nicht — sobald ein
  // Textfeld den Fokus bekommt, verschiebt sich der Inhalt wieder unter den Rand.
  tester.view.physicalSize = const Size(800, 2000);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  final provider = TaskProvider(taskService: fake);
  await tester.pumpWidget(
    MaterialApp(
      home: ChangeNotifierProvider<TaskProvider>.value(
        value: provider,
        child: Scaffold(
          body: CreateTaskSheet(existingTask: existingTask, spaceType: 'TASKS'),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

/// Das Sheet ist hoeher als die 600 px der Testflaeche und scrollt.
///
/// Ohne dieses Heranscrollen tippt der Test auf einen Punkt ausserhalb des Baums und meldet nur
/// eine Warnung — der Tap passiert schlicht nicht, und der Test scheitert an einer Stelle, die
/// mit der eigentlichen Sache nichts zu tun hat.
Future<void> _tippe(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  group('CreateTaskSheet im Bearbeiten-Modus', () {
    testWidgets('übernimmt die Werte der bestehenden Aufgabe', (tester) async {
      final task = _aufgabe(
        deadline: DateTime.now().add(const Duration(days: 2)),
        description: 'Kapitel 3',
      );
      await _pumpSheet(tester, FakeTaskService([task]), existingTask: task);

      expect(find.text('Aufgabe bearbeiten'), findsOneWidget);
      expect(find.text('Speichern'), findsOneWidget);
      expect(find.text('Create'), findsNothing);
      // Titel und Notiz stehen in den Textfeldern.
      expect(find.text('Abgabe schreiben'), findsOneWidget);
      expect(find.text('Kapitel 3'), findsOneWidget);
    });

    testWidgets('das "x" an der Deadline meldet DEADLINE als zu leeren', (tester) async {
      final task = _aufgabe(deadline: DateTime.now().add(const Duration(days: 2)));
      final fake = FakeTaskService([task]);
      await _pumpSheet(tester, fake, existingTask: task);

      // Das "x" neben dem Datum, nicht das Schließen-Kreuz oben: jenes hat Größe 20.
      final leeren = find.byWidgetPredicate(
        (w) => w is Icon && w.icon == Icons.close && w.size == 15,
      );
      expect(leeren, findsOneWidget, reason: 'gesetzte Deadline muss sich entfernen lassen');

      await _tippe(tester, leeren);
      expect(find.text('Keine'), findsOneWidget);

      await _tippe(tester, find.text('Speichern'));

      expect(fake.updateCallCount, 1);
      expect(fake.lastClear, contains('DEADLINE'),
          reason: 'im Rumpf heißt null "unverändert" — leeren muss ausdrücklich gesagt werden');
      expect(fake.lastUpdated!.deadline, isNull);
    });

    testWidgets('ohne Änderung wird nichts als zu leeren gemeldet', (tester) async {
      final task = _aufgabe(
        deadline: DateTime.now().add(const Duration(days: 2)),
        description: 'Kapitel 3',
        notBefore: DateTime.now().add(const Duration(hours: 1)),
      );
      final fake = FakeTaskService([task]);
      await _pumpSheet(tester, fake, existingTask: task);

      await _tippe(tester, find.text('Speichern'));

      expect(fake.lastClear, isEmpty,
          reason: 'ein Speichern ohne Änderung darf nichts löschen');
    });

    testWidgets('eine gelöschte Notiz wird gemeldet', (tester) async {
      final task = _aufgabe(description: 'Kapitel 3');
      final fake = FakeTaskService([task]);
      await _pumpSheet(tester, fake, existingTask: task);

      await tester.enterText(find.widgetWithText(TextField, 'Kapitel 3'), '');
      await _tippe(tester, find.text('Speichern'));

      // Ohne diesen Weg ließe sich eine Notiz gar nicht entfernen: "" wird zu null, und null
      // heißt im Rumpf "unverändert".
      expect(fake.lastClear, contains('DESCRIPTION'));
    });

    testWidgets('ein zu kurzer Höchstblock blockiert das Speichern', (tester) async {
      final task = _aufgabe(minChunk: 60, maxChunk: 30);
      final fake = FakeTaskService([task]);
      await _pumpSheet(tester, fake, existingTask: task);

      await _tippe(tester, find.text('Speichern'));

      expect(fake.updateCallCount, 0);
      expect(find.textContaining('kürzeste Block'), findsOneWidget);
    });

    testWidgets('die Chunk-Werte gehen mit', (tester) async {
      final task = _aufgabe(minChunk: 30, maxChunk: 90, maxChunksPerDay: 2);
      final fake = FakeTaskService([task]);
      await _pumpSheet(tester, fake, existingTask: task);

      await _tippe(tester, find.text('Speichern'));

      expect(fake.lastUpdated!.minChunkMinutes, 30);
      expect(fake.lastUpdated!.maxChunkMinutes, 90);
      expect(fake.lastUpdated!.maxChunksPerDay, 2);
    });
  });

  group('CreateTaskSheet im Anlege-Modus', () {
    testWidgets('verhält sich unverändert', (tester) async {
      final fake = FakeTaskService();
      await _pumpSheet(tester, fake);

      expect(find.text('Neue Aufgabe'), findsOneWidget);
      expect(find.text('Create'), findsOneWidget);
      expect(find.text('Speichern'), findsNothing);

      await tester.enterText(find.byType(TextField).first, 'Neue Sache');
      await _tippe(tester, find.text('Create'));

      expect(fake.createCallCount, 1);
      expect(fake.lastCreated!.title, 'Neue Sache');
      // Beim Anlegen ist die Deadline weiterhin vorbelegt.
      expect(fake.lastCreated!.deadline, isNotNull);
    });

    testWidgets('ohne Titel wird nicht angelegt', (tester) async {
      final fake = FakeTaskService();
      await _pumpSheet(tester, fake);

      await _tippe(tester, find.text('Create'));

      expect(fake.createCallCount, 0);
    });
  });
}
