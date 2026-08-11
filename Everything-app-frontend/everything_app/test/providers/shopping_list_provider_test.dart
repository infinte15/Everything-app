import 'package:everything_app/models/recipe.dart';
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

  group('addIngredients', () {
    test('legt für jede Zutat eine Zeile an', () async {
      await provider.load();

      final count = await provider.addIngredients(const [
        RecipeIngredient(amount: 250, unit: 'g', name: 'Mehl'),
        RecipeIngredient(name: 'Salz'),
      ]);

      expect(count, 2);
      expect(provider.items.any((i) => i.name == 'Mehl' && i.amount == 250),
          isTrue);
      expect(provider.items.any((i) => i.name == 'Salz'), isTrue);
    });
  });
}
