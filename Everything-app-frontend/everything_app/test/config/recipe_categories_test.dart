import 'dart:convert';
import 'dart:io';

import 'package:everything_app/config/recipe_categories.dart';
import 'package:flutter_test/flutter_test.dart';

/// Hält den Kategorienkatalog der App an dem des Backends fest.
///
/// Die beiden Listen stehen in getrennten Projekten und driften auseinander,
/// ohne dass etwas kaputtgeht: der Import sortiert nach "Auflauf & Ofen", die
/// Oberfläche bietet "Auflauf" an, und der Filter zeigt beide nebeneinander.
void main() {
  final catalog = File(
    '../../Everything-app-backend/everything-app/src/main/resources/data/recipe-categories.json',
  );

  test('deckt sich mit dem ausgelieferten Katalog des Backends', () {
    if (!catalog.existsSync()) {
      // Nur die Flutter-App ausgecheckt - dann lässt sich hier nichts prüfen.
      markTestSkipped('Kategorienkatalog nicht gefunden: ${catalog.path}');
      return;
    }

    final fromBackend =
        (json.decode(catalog.readAsStringSync())['categories'] as List)
            .cast<String>();

    // Inklusive Reihenfolge: sie ist die Anzeigereihenfolge des Filters.
    expect(recipeCategories, fromBackend);
  });

  test('jede Kategorie hat ein eigenes Symbol', () {
    // Der Space ist einfarbig - das Symbol ist das einzige, was zwei
    // Kategorien im Filter unterscheidet, wenn man nicht liest.
    final icons = recipeCategories.map(recipeCategoryIcon).toList();

    expect(icons.toSet().length, recipeCategories.length,
        reason: 'zwei Kategorien teilen sich ein Symbol');
  });

  group('displayDifficulty', () {
    test('bildet die Altbestände auf das kanonische Vokabular ab', () {
      // Der Demo-Seeder schrieb klein, der chefkoch-Import "Normal", chefkoch
      // selbst "simpel"/"pfiffig".
      expect(displayDifficulty('einfach'), 'Einfach');
      expect(displayDifficulty('simpel'), 'Einfach');
      expect(displayDifficulty('mittel'), 'Mittel');
      expect(displayDifficulty('Normal'), 'Mittel');
      expect(displayDifficulty('pfiffig'), 'Aufwendig');
    });

    test('lässt Unbekanntes stehen, statt es zu verschlucken', () {
      expect(displayDifficulty('Oma-Niveau'), 'Oma-Niveau');
      expect(displayDifficulty(null), isNull);
      expect(displayDifficulty('  '), isNull);
    });

    test('die kanonischen Stufen bilden auf sich selbst ab', () {
      for (final difficulty in recipeDifficulties) {
        expect(displayDifficulty(difficulty), difficulty);
      }
    });
  });
}
