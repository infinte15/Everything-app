import 'package:flutter_test/flutter_test.dart';
import 'package:everything_app/utils/deadline_urgency.dart';

void main() {
  // Fester Bezugspunkt, damit die Tests nicht je nach Tageszeit kippen.
  final now = DateTime(2026, 8, 9, 10, 0);

  group('urgencyOf', () {
    test('ohne Deadline gibt es keine Dringlichkeit', () {
      expect(urgencyOf(null, now.add(const Duration(hours: 2)), now),
          DeadlineUrgency.none);
    });

    test('eine verstrichene Deadline ist überfällig', () {
      expect(
          urgencyOf(now.subtract(const Duration(minutes: 1)),
              now.add(const Duration(hours: 2)), now),
          DeadlineUrgency.overdue);
    });

    test('ein Block am Deadline-Tag ist immer die letzte Chance', () {
      // Block morgens um 08:00, Deadline am selben Tag um 23:59 — rechnerisch bleiben knapp
      // 16 Stunden, es ist aber trotzdem der letzte Arbeitstag dafür.
      final blockEnd = DateTime(2026, 8, 12, 9, 0);
      final deadline = DateTime(2026, 8, 12, 23, 59);
      expect(urgencyOf(deadline, blockEnd, now), DeadlineUrgency.lastChance);
    });

    test('23 Stunden Restzeit sind letzte Chance, 25 nicht mehr', () {
      final blockEnd = DateTime(2026, 8, 12, 10, 0);
      expect(urgencyOf(blockEnd.add(const Duration(hours: 23)), blockEnd, now),
          DeadlineUrgency.lastChance);
      expect(urgencyOf(blockEnd.add(const Duration(hours: 25)), blockEnd, now),
          isNot(DeadlineUrgency.lastChance));
    });

    test('71 Stunden sind bald fällig, 73 nicht mehr', () {
      final blockEnd = DateTime(2026, 8, 12, 10, 0);
      expect(urgencyOf(blockEnd.add(const Duration(hours: 71)), blockEnd, now),
          DeadlineUrgency.soon);
      expect(urgencyOf(blockEnd.add(const Duration(hours: 73)), blockEnd, now),
          DeadlineUrgency.none);
    });

    test('ein Block hinter der Deadline zählt als letzte Chance, nicht als harmlos', () {
      // Kommt beim Nachholen vor: die Deadline liegt noch in der Zukunft, der Block aber dahinter.
      final deadline = now.add(const Duration(hours: 2));
      final blockEnd = now.add(const Duration(hours: 6));
      expect(urgencyOf(deadline, blockEnd, now), DeadlineUrgency.lastChance);
    });
  });

  group('deadlineLabel', () {
    test('heute, morgen, später und überfällig', () {
      expect(deadlineLabel(DateTime(2026, 8, 9, 23, 59), now), 'HEUTE FÄLLIG');
      expect(deadlineLabel(DateTime(2026, 8, 10, 8, 0), now), 'MORGEN FÄLLIG');
      expect(deadlineLabel(DateTime(2026, 8, 14, 8, 0), now), 'IN 5 TAGEN');
      expect(deadlineLabel(DateTime(2026, 8, 7, 8, 0), now), 'ÜBERFÄLLIG (2 T.)');
    });

    test('die Tagesdifferenz zählt Kalendertage, nicht 24-Stunden-Blöcke', () {
      // 23:00 heute bis 01:00 morgen sind zwei Stunden, aber eben "morgen".
      final spaeterAbend = DateTime(2026, 8, 9, 23, 0);
      expect(deadlineLabel(DateTime(2026, 8, 10, 1, 0), spaeterAbend), 'MORGEN FÄLLIG');
    });
  });
}
