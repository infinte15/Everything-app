import 'package:everything_app/models/shopping_item.dart';
import 'package:everything_app/providers/shopping_list_provider.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_recipe_service.dart';

void main() {
  late FakeRecipeService service;
  late ShoppingListProvider provider;

  ShoppingItem item(int id, String name, String aisle, {bool checked = false}) =>
      ShoppingItem(id: id, name: name, category: aisle, isChecked: checked);

  setUp(() {
    service = FakeRecipeService(shoppingList: [
      item(1, 'Tiefkühlerbsen', 'Tiefkühl'),
      item(2, 'Möhren', 'Obst & Gemüse'),
      item(3, 'Milch', 'Kühlregal', checked: true),
      item(4, 'Weltraumnahrung', 'Astronautenkost'),
    ]);
    provider = ShoppingListProvider(service: service);
  });

  group('byAisle', () {
    test('gruppiert in Laufreihenfolge, nicht alphabetisch', () async {
      await provider.load();

      // Der Server liefert alphabetisch - dort stünde "Tiefkühl" zwischen
      // "Sonstiges" und "Trockenware", und im Laden läuft man zweimal quer.
      expect(provider.byAisle.keys.toList(),
          ['Obst & Gemüse', 'Kühlregal', 'Tiefkühl', 'Astronautenkost']);
    });

    test('ein unbekanntes Regal fällt auf, statt hinten zu verschwinden',
        () async {
      await provider.load();

      final keys = provider.byAisle.keys.toList();
      expect(keys.indexOf('Astronautenkost'), keys.length - 1);
    });

    test('Abgehaktes rutscht innerhalb seiner Gruppe ans Ende', () async {
      service.shoppingList.add(item(5, 'Butter', 'Kühlregal'));
      await provider.load();

      expect(provider.byAisle['Kühlregal']!.map((i) => i.name),
          ['Butter', 'Milch']);
    });
  });

  group('Zähler', () {
    test('zählt offen und erledigt getrennt', () async {
      await provider.load();

      expect(provider.openCount, 3);
      expect(provider.checkedCount, 1);
      expect(provider.hasChecked, isTrue);
    });
  });

  group('Häkchen', () {
    test('schlägt sofort durch und schickt ein PATCH', () async {
      await provider.load();
      final target = provider.items.firstWhere((i) => i.id == 2);

      final future = provider.toggle(target);
      expect(provider.items.firstWhere((i) => i.id == 2).isChecked, isTrue);

      await future;
      expect(service.patchCount, 1);
      // Und es hält: der alte Provider hakte nur im Speicher ab, beim nächsten
      // Laden war das Häkchen weg.
      expect(service.shoppingList.firstWhere((i) => i.id == 2).isChecked, isTrue);
    });

    test('nimmt sich bei einem Fehler zurück', () async {
      await provider.load();
      final target = provider.items.firstWhere((i) => i.id == 2);
      service.failAll = true;

      await provider.toggle(target);

      expect(provider.items.firstWhere((i) => i.id == 2).isChecked, isFalse);
      expect(provider.error, isNotNull);
    });
  });

  group('Löschen', () {
    test('clearChecked entfernt nur Abgehaktes', () async {
      await provider.load();

      await provider.clearChecked();

      expect(provider.items, hasLength(3));
      expect(provider.items.any((i) => i.isChecked), isFalse);
    });

    test('remove stellt bei einem Fehler wieder her', () async {
      await provider.load();
      service.failAll = true;

      await provider.remove(2);

      expect(provider.items, hasLength(4));
    });
  });

  group('rebuildFromWeek', () {
    test('setzt isRebuilding, während es läuft', () async {
      await provider.load();
      var sawRebuilding = false;
      provider.addListener(() {
        if (provider.isRebuilding) sawRebuilding = true;
      });

      await provider.rebuildFromWeek(DateTime.now(), DateTime.now());

      // Eigener Zustand, weil der Aufbau Zeilen verwirft - das darf nicht wie
      // ein gewöhnliches Nachladen aussehen.
      expect(sawRebuilding, isTrue);
      expect(provider.isRebuilding, isFalse);
    });
  });

  group('addFromRecipe', () {
    test('ersetzt die Liste durch die Antwort des Servers', () async {
      await provider.load();
      final before = provider.items.length;

      final ok = await provider.addFromRecipe(7, servings: 6);

      expect(ok, isTrue);
      expect(service.lastFromRecipeId, 7);
      expect(service.lastFromRecipeServings, 6);
      // Der Server legt teils an und lässt teils wachsen - deshalb die ganze
      // Liste und kein Anhängen von Hand.
      expect(provider.items, hasLength(before + 1));
      expect(provider.items.last.name, 'Zutat aus Rezept 7');
    });

    test('ohne Portionsangabe geht kein servings raus', () async {
      await provider.load();

      await provider.addFromRecipe(7);

      expect(service.lastFromRecipeServings, isNull);
    });

    test('ein Fehler setzt error und lässt die Liste stehen', () async {
      await provider.load();
      final before = provider.items;
      service.failAll = true;

      final ok = await provider.addFromRecipe(7);

      expect(ok, isFalse);
      expect(provider.error, isNotNull);
      expect(provider.items, before);
    });
  });

  // Der Server ordnet **nur** dann selbst ins Regal ein, wenn die Kategorie
  // leer ankommt. Solange der Client immer 'Sonstiges' mitschickte, ist der
  // Klassifizierer mit acht Regalen und ~200 Stichwörtern nie gelaufen.
  group('Regal-Zuordnung', () {
    test('add ohne Regal schickt gar keinen category-Schlüssel', () async {
      await provider.load();

      await provider.add('Kokosmilch', amount: 400, unit: 'ml');

      expect(service.lastAddedShoppingJson!.containsKey('category'), isFalse);
    });

    test('ein gewähltes Regal geht mit', () async {
      await provider.load();

      await provider.add('Kokosmilch', category: 'Konserven');

      expect(service.lastAddedShoppingJson!['category'], 'Konserven');
    });

    test('eine Zeile ohne Regal wird als Sonstiges angezeigt', () {
      const bare = ShoppingItem(name: 'Wunderpulver');

      expect(bare.category, isNull);
      expect(bare.aisle, 'Sonstiges');
    });
  });

  group('Herkunft der Liste', () {
    test('rebuildFromWeek merkt sich die Woche', () async {
      final from = DateTime(2026, 8, 17);
      final to = DateTime(2026, 8, 23);
      await provider.load();

      await provider.rebuildFromWeek(from, to);

      expect(provider.builtFromStart, from);
      expect(provider.builtFromEnd, to);
    });

    test('ein fehlgeschlagener Aufbau merkt sich nichts', () async {
      await provider.load();
      service.failAll = true;

      await provider.rebuildFromWeek(DateTime(2026, 8, 17), DateTime(2026, 8, 23));

      expect(provider.builtFromStart, isNull);
    });

    // Zutaten eines Rezepts sind keine Woche - der Kopf darf danach nicht
    // behaupten, die Liste stamme aus einem Wochenplan.
    test('addFromRecipe rührt die Woche nicht an', () async {
      await provider.load();
      await provider.rebuildFromWeek(DateTime(2026, 8, 17), DateTime(2026, 8, 23));

      await provider.addFromRecipe(7);

      expect(provider.builtFromStart, DateTime(2026, 8, 17));
    });

    test('hasMealPlanItems erkennt Zeilen aus dem Wochenplan', () async {
      service.shoppingList.add(const ShoppingItem(
        id: 6,
        name: 'Hackfleisch',
        source: ShoppingItemSource.mealPlan,
      ));
      await provider.load();

      expect(provider.hasMealPlanItems, isTrue);
    });
  });
}
