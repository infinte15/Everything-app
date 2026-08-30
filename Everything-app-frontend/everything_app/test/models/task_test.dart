import 'package:everything_app/models/task.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Planungsfelder', () {
    /// Die Felder gab es im Backend von Anfang an, im Modell hier aber nicht: `TaskDTO` nimmt
    /// `splittable`, `notBefore` und die Chunk-Grenzen an, und der Solver wertet sie aus — der
    /// Anlege-Dialog hat die Eingaben trotzdem stillschweigend weggeworfen, weil sie auf dem Weg
    /// durch dieses Modell verloren gingen.
    test('ueberleben den Weg durch toJson', () {
      final task = Task(
        title: 'Bericht',
        splittable: false,
        notBefore: DateTime(2026, 8, 20, 9, 0),
        minChunkMinutes: 45,
        maxChunkMinutes: 90,
        maxChunksPerDay: 2,
      );

      final json = task.toJson();

      expect(json['splittable'], isFalse);
      expect(json['notBefore'], '2026-08-20T09:00:00.000');
      expect(json['minChunkMinutes'], 45);
      expect(json['maxChunkMinutes'], 90);
      expect(json['maxChunksPerDay'], 2);
    });

    test('kommen aus fromJson zurueck', () {
      final task = Task.fromJson({
        'id': 1,
        'title': 'Bericht',
        'splittable': false,
        'notBefore': '2026-08-20T09:00:00',
        'minChunkMinutes': 45,
        'maxChunkMinutes': 90,
        'maxChunksPerDay': 2,
        'completedMinutes': 30,
      });

      expect(task.splittable, isFalse);
      expect(task.notBefore, DateTime(2026, 8, 20, 9, 0));
      expect(task.minChunkMinutes, 45);
      expect(task.maxChunkMinutes, 90);
      expect(task.maxChunksPerDay, 2);
      expect(task.completedMinutes, 30);
    });

    /// `null` heisst hier ueberall "Vorgabe des Backends" und darf nicht zu `false` oder `0`
    /// werden — sonst schaltet ein Bestandstask beim ersten Speichern still das Aufteilen ab.
    test('bleiben null, wenn sie nicht gesetzt sind', () {
      final task = Task.fromJson({'id': 1, 'title': 'Ohne alles'});

      expect(task.splittable, isNull);
      expect(task.notBefore, isNull);
      expect(task.minChunkMinutes, isNull);
      expect(task.toJson()['splittable'], isNull);
    });
  });
}
