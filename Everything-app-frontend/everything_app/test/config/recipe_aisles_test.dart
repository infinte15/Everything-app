import 'dart:convert';
import 'dart:io';

import 'package:everything_app/config/recipe_aisles.dart';
import 'package:flutter_test/flutter_test.dart';

/// Hält die Regale der Einkaufsliste an denen des Backends fest.
///
/// Kennt der Server ein Regal, das diese Liste nicht kennt, landen dessen
/// Zeilen im Laden an einer beliebigen Stelle.
void main() {
  final catalog = File(
    '../../Everything-app-backend/everything-app/src/main/resources/data/ingredient-aisles.json',
  );

  test('deckt sich mit den Regalen des Backends', () {
    if (!catalog.existsSync()) {
      markTestSkipped('Regal-Katalog nicht gefunden: ${catalog.path}');
      return;
    }

    final fromBackend =
        (json.decode(catalog.readAsStringSync())['aisles'] as Map).keys.toSet();

    // "Sonstiges" ist der Rückfall des Klassifizierers und steht nicht in der
    // Tabelle - es muss aber in der Anzeige vorkommen.
    expect(recipeAisles.toSet().difference({'Sonstiges'}), fromBackend);
    expect(recipeAisles.last, 'Sonstiges',
        reason: 'Unsortiertes gehört ans Ende des Einkaufs');
  });

  test('unbekannte Regale landen vor "Sonstiges"', () {
    // Ein Regal, das der Server kennt und diese Liste nicht, ist ein Versehen -
    // keine Restekiste. Es soll auffallen, indem es über "Sonstiges" steht.
    expect(recipeAisleOrder('Frisch aus der Zukunft'),
        lessThanOrEqualTo(recipeAisleOrder('Sonstiges')));
    expect(recipeAisleOrder('Obst & Gemüse'), 0);
    expect(recipeAisleOrder('Kühlregal'),
        lessThan(recipeAisleOrder('Tiefkühl')),
        reason: 'Kühlregal kommt im Markt vor der Tiefkühltruhe');
  });
}
