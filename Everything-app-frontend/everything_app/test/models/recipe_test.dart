import 'package:everything_app/models/meal_type.dart';
import 'package:everything_app/models/recipe.dart';
import 'package:everything_app/screens/recipes/widgets/recipe_format.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  /// Eine Antwort, wie sie `RecipeDTO` tatsächlich liefert - mit strukturierten
  /// Zutaten und Schritten. Die alte Fassung des Modells las `ingredients` als
  /// `String` und warf hier, weshalb der Space stumm leer blieb.
  Map<String, dynamic> dtoJson() => {
        'id': 7,
        'name': 'Bolognese wie bei Oma',
        'description': 'Lange geschmort.',
        'prepTimeMinutes': 20,
        'cookTimeMinutes': 150,
        'servings': 4,
        'category': 'Pasta & Reis',
        'suitableFor': ['MITTAGESSEN', 'ABENDESSEN'],
        'ingredients': [
          {
            'id': 1,
            'amount': 500,
            'unit': 'g',
            'name': 'Rinderhackfleisch',
            'note': null,
            'rawText': '500 g Rinderhackfleisch',
            'groupLabel': null,
          },
          {
            'id': 2,
            'amount': null,
            'unit': null,
            'name': 'Salz, Pfeffer, Muskat',
            'note': null,
            'rawText': 'Salz, Pfeffer, Muskat',
            'groupLabel': null,
          },
        ],
        'steps': [
          {'id': 1, 'text': 'Gemüse würfeln.'},
          {'id': 2, 'text': 'Hack anbraten.'},
        ],
        'ingredientsText': '500 g Rinderhackfleisch\nSalz, Pfeffer, Muskat',
        'instructionsText': 'Gemüse würfeln.\nHack anbraten.',
        'calories': 640,
        'protein': 34.0,
        'carbs': 62.0,
        'fat': 24.0,
        'difficulty': 'Mittel',
        'imageUrl': null,
        'tags': 'italienisch,meal-prep',
        'isFavorite': true,
        'rating': 5,
        'cookCount': 11,
        'lastCookedAt': '2026-08-05T19:00:00',
        'notes': null,
        'sourceUrl': null,
        'sourceName': null,
        'createdAt': '2026-07-01T10:00:00',
        'updatedAt': '2026-08-05T19:00:00',
      };

  group('Recipe.fromJson', () {
    test('liest strukturierte Zutaten und Schritte', () {
      final recipe = Recipe.fromJson(dtoJson());

      expect(recipe.name, 'Bolognese wie bei Oma');
      expect(recipe.ingredients, hasLength(2));
      expect(recipe.ingredients.first.amount, 500);
      expect(recipe.ingredients.first.unit, 'g');
      expect(recipe.ingredients.first.name, 'Rinderhackfleisch');
      expect(recipe.steps.map((s) => s.text),
          ['Gemüse würfeln.', 'Hack anbraten.']);
      expect(recipe.suitableFor,
          {MealType.mittagessen, MealType.abendessen});
      expect(recipe.cookCount, 11);
      expect(recipe.rating, 5);
      expect(recipe.tagList, ['italienisch', 'meal-prep']);
    });

    test('überspringt eine unbekannte Mahlzeit, statt zu werfen', () {
      // Eine krumme Zeile darf nicht die ganze Liste umbringen.
      final json = dtoJson()..['suitableFor'] = ['ABENDESSEN', 'BRUNCH'];

      final recipe = Recipe.fromJson(json);

      expect(recipe.suitableFor, {MealType.abendessen});
    });

    test('verträgt fehlende Pflichtfelder mit Vorgaben', () {
      final recipe = Recipe.fromJson({'id': 1, 'name': 'Nur ein Name'});

      expect(recipe.servings, 1);
      expect(recipe.prepTimeMinutes, 0);
      expect(recipe.category, 'Sonstiges');
      expect(recipe.ingredients, isEmpty);
      expect(recipe.isFavorite, isFalse);
    });

    test('toJson schickt die gerenderten Textfelder nicht mit', () {
      // Der Server ignoriert sie - ein Abbild, das Felder erfindet, driftet.
      final json = Recipe.fromJson(dtoJson()).toJson();

      expect(json.containsKey('ingredientsText'), isFalse);
      expect(json.containsKey('instructionsText'), isFalse);
      expect(json['suitableFor'], ['MITTAGESSEN', 'ABENDESSEN']);
      expect((json['ingredients'] as List).first['name'], 'Rinderhackfleisch');
    });
  });

  group('scaledTo', () {
    final recipe = Recipe.fromJson(dtoJson());

    test('verdoppelt die Mengen bei doppelten Portionen', () {
      expect(recipe.scaledTo(8).first.amount, 1000);
    });

    test('halbiert bei halben Portionen', () {
      expect(recipe.scaledTo(2).first.amount, 250);
    });

    test('lässt eine Zutat ohne Menge ohne Menge', () {
      // Das Dreifache von "Salz, Pfeffer" ist immer noch "Salz, Pfeffer".
      expect(recipe.scaledTo(12).last.amount, isNull);
      expect(recipe.scaledTo(12).last.name, 'Salz, Pfeffer, Muskat');
    });

    test('gleiche Portionszahl ändert nichts', () {
      expect(recipe.scaledTo(4).first.amount, 500);
    });
  });

  group('formatAmount', () {
    test('macht aus Kommazahlen Küchenbrüche', () {
      // Der Grund für die Funktion: "0.5 EL Senf" steht in keinem Kochbuch.
      expect(formatAmount(0.5), '½');
      expect(formatAmount(1.5), '1 ½');
      expect(formatAmount(0.25), '¼');
      expect(formatAmount(0.333), '⅓');
      expect(formatAmount(2.75), '2 ¾');
    });

    test('ganze Zahlen bleiben ganz', () {
      expect(formatAmount(400), '400');
      expect(formatAmount(1), '1');
    });

    test('große Mengen werden gewogen, nicht gebrochen', () {
      // Unterhalb von zehn zählt man (Löffel, Zehen, Stücke), oberhalb wiegt
      // man - und "187 ½ g" schreibt keine Küchenwaage.
      expect(formatAmount(12.75), '12,75');
      expect(formatAmount(187.5), '187,5');
    });

    test('nichts wird zu nichts, nicht zu einer Null', () {
      expect(formatAmount(null), '');
      expect(formatAmount(0), '');
    });

    test('Einheit nur zusammen mit einer Menge', () {
      // "EL" allein sagt nichts.
      expect(formatAmountWithUnit(2, 'EL'), '2 EL');
      expect(formatAmountWithUnit(null, 'EL'), '');
      expect(formatAmountWithUnit(3, null), '3');
    });
  });

  group('formatDuration', () {
    test('rechnet in Stunden um', () {
      expect(formatDuration(25), '25 Min');
      expect(formatDuration(60), '1 Std');
      expect(formatDuration(75), '1 Std 15 Min');
      expect(formatDuration(0), '–');
    });
  });

  group('isoWeek', () {
    test('zählt nach ISO 8601 - Woche 1 ist die mit dem ersten Donnerstag', () {
      // 2026 beginnt an einem Donnerstag, der 1. Januar liegt also in KW 1.
      expect(isoWeek(DateTime(2026, 1, 1)), 1);
      expect(isoWeek(DateTime(2026, 1, 4)), 1);
      expect(isoWeek(DateTime(2026, 1, 5)), 2);
    });

    test('die Sommerzeitumstellung verschiebt die Woche nicht', () {
      // Gerechnet wurde mit `difference().inDays` auf Ortszeit: über die
      // Umstellung im März hinweg fehlte eine Stunde, `inDays` rundete ab, und
      // jede Woche danach war um eins zu klein.
      expect(isoWeek(DateTime(2026, 8, 17)), 34);
      expect(isoWeek(DateTime(2026, 8, 23)), 34);
      expect(isoWeek(DateTime(2026, 8, 24)), 35);
    });
  });

  test('MealType.wire ist ASCII wie im Backend', () {
    // Der frühere Provider schickte BREAKFAST/LUNCH/DINNER - kein einziger
    // Wochenplan-Eintrag hat je die Mahlzeit getroffen, die er meinte.
    expect(MealType.fruehstueck.wire, 'FRUEHSTUECK');
    expect(MealType.mittagessen.wire, 'MITTAGESSEN');
    expect(MealType.abendessen.wire, 'ABENDESSEN');
    expect(MealType.tryParse('SNACK'), MealType.snack);
    expect(MealType.tryParse('BREAKFAST'), isNull);
  });
}
