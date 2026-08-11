/// Die Ladenregale der Einkaufsliste, in Laufreihenfolge.
///
/// Spiegelt die Schlüssel von `resources/data/ingredient-aisles.json`; ein Test
/// in `test/config/recipe_aisles_test.dart` hält beide gegeneinander.
///
/// Gruppiert wird hier und nicht im Server: der liefert seine Zeilen mit
/// `findByUserIdOrderByCategoryAsc…`, also alphabetisch, und alphabetisch steht
/// "Tiefkühl" zwischen "Sonstiges" und "Trockenware". Im Laden ist das
/// unbrauchbar - eine Einkaufsliste ist nach dem Weg durch den Markt sortiert
/// oder nach gar nichts.
const recipeAisles = <String>[
  'Obst & Gemüse',
  'Kühlregal',
  'Fleisch & Fisch',
  'Trockenware',
  'Konserven',
  'Tiefkühl',
  'Getränke',
  'Gewürze & Backen',
  'Sonstiges',
];

/// Position eines Regals. Unbekanntes landet vor "Sonstiges", nicht dahinter:
/// ein Regal, das der Server kennt und diese Liste nicht, ist ein Versehen und
/// keine Restekiste.
int recipeAisleOrder(String? aisle) {
  final index = recipeAisles.indexOf(aisle ?? '');
  if (index >= 0) return index;
  return recipeAisles.length - 1;
}
