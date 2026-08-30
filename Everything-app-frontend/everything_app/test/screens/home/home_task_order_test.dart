import 'package:everything_app/models/task.dart';
import 'package:everything_app/screens/home/home_screen.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Task task(String titel, {DateTime? deadline, int prio = 3}) =>
      Task(title: titel, deadline: deadline, priority: prio);

  final gestern = DateTime.now().subtract(const Duration(days: 1));
  final morgen = DateTime.now().add(const Duration(days: 1));
  final naechsteWoche = DateTime.now().add(const Duration(days: 7));

  group('dringendZuerst', () {
    /// Der eigentliche Befund: auf dem Homescreen stand `todoTasks.take(4)`, und eine
    /// ueberfaellige Aufgabe konnte damit unter vier beliebigen anderen verschwinden.
    test('stellt Ueberfaelliges nach ganz oben', () {
      final sortiert = dringendZuerst([
        task('Irgendwas'),
        task('Naechste Woche', deadline: naechsteWoche),
        task('Laengst faellig', deadline: gestern),
        task('Morgen', deadline: morgen),
      ]);

      expect(sortiert.first.title, 'Laengst faellig');
    });

    test('danach die naehere Deadline', () {
      final sortiert = dringendZuerst([
        task('Naechste Woche', deadline: naechsteWoche),
        task('Morgen', deadline: morgen),
      ]);

      expect(sortiert.map((t) => t.title), ['Morgen', 'Naechste Woche']);
    });

    test('eine Aufgabe mit Termin geht einer ohne vor', () {
      final sortiert = dringendZuerst([
        task('Ohne Termin', prio: 5),
        task('Mit Termin', deadline: naechsteWoche, prio: 1),
      ]);

      expect(sortiert.first.title, 'Mit Termin');
    });

    test('bei gleichem Termin entscheidet die hoehere Prioritaet', () {
      final sortiert = dringendZuerst([
        task('Nebensache', deadline: morgen, prio: 1),
        task('Wichtig', deadline: morgen, prio: 5),
      ]);

      expect(sortiert.map((t) => t.title), ['Wichtig', 'Nebensache']);
    });

    /// Sortiert wird auf einer Kopie: die Liste des Providers gehoert ihm, und eine an Ort und
    /// Stelle sortierte Provider-Liste wuerde jeden anderen Screen mitveraendern.
    test('laesst die uebergebene Liste unangetastet', () {
      final original = [task('B', deadline: naechsteWoche), task('A', deadline: morgen)];

      dringendZuerst(original);

      expect(original.map((t) => t.title), ['B', 'A']);
    });
  });
}
