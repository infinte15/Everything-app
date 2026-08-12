import 'package:flutter/material.dart';

import '../config/recipe_aisles.dart';
import '../models/shopping_item.dart';
import '../services/recipe_service.dart';

/// Zustand der Einkaufsliste.
///
/// Eigener Provider, obwohl die Zeilen aus demselben Controller kommen: sie
/// teilen mit den Rezepten keinen Zustand, werden im Supermarkt benutzt,
/// während der Rest des Space niemanden interessiert, und ein gemeinsames
/// `isLoading` ließe den Ladebalken über dem ganzen Space blinken, sobald ein
/// Häkchen gesetzt wird.
class ShoppingListProvider with ChangeNotifier {
  ShoppingListProvider({RecipeService? service})
      : _service = service ?? RecipeService();

  final RecipeService _service;

  List<ShoppingItem> _items = [];
  bool _isLoading = false;

  /// Eigener Zustand, weil "Aus Wochenplan aufbauen" Zeilen **verwirft** und
  /// länger dauert - das darf nicht wie ein gewöhnliches Nachladen aussehen.
  bool _isRebuilding = false;
  String? _error;

  List<ShoppingItem> get items => _items;
  bool get isLoading => _isLoading;
  bool get isRebuilding => _isRebuilding;
  String? get error => _error;

  int get openCount => _items.where((i) => !i.isChecked).length;
  int get checkedCount => _items.where((i) => i.isChecked).length;
  bool get hasChecked => checkedCount > 0;

  /// Aus welcher Woche die Liste aufgebaut wurde - gesetzt **nur** von
  /// [rebuildFromWeek] und nur bei Erfolg.
  ///
  /// Bewusst nicht `RecipeProvider.weekStart`: das ist die Woche, die der
  /// *Wochenplan-Reiter* gerade anzeigt. Blättert man dort auf KW 40,
  /// behauptete der Einkaufskopf sofort, die Liste käme aus KW 40. Ein Kopf,
  /// der lügt, ist schlimmer als einer, der schweigt.
  ///
  /// Sitzungszustand: nach einem Neustart ist die Angabe weg, die Liste selbst
  /// bleibt richtig. Über den `PreferencesService` zu überdauern wären ~10
  /// Zeilen - hier bewusst weggelassen, leicht nachzurüsten.
  DateTime? _builtFromStart;
  DateTime? _builtFromEnd;

  DateTime? get builtFromStart => _builtFromStart;
  DateTime? get builtFromEnd => _builtFromEnd;

  /// Enthält die Liste überhaupt Zeilen aus irgendeinem Wochenplan? Der
  /// unscharfe Rückfall, wenn die Woche nicht (mehr) bekannt ist.
  bool get hasMealPlanItems =>
      _items.any((i) => i.source == ShoppingItemSource.mealPlan);

  /// Nach Ladenregalen gruppiert, in Laufreihenfolge - und innerhalb einer
  /// Gruppe rutscht Abgehaktes ans Ende. Leere Gruppen kommen nicht vor.
  Map<String, List<ShoppingItem>> get byAisle {
    final grouped = <String, List<ShoppingItem>>{};
    for (final item in _items) {
      grouped.putIfAbsent(item.aisle, () => []).add(item);
    }

    final aisles = grouped.keys.toList()
      ..sort((a, b) {
        final order = recipeAisleOrder(a).compareTo(recipeAisleOrder(b));
        return order != 0 ? order : a.compareTo(b);
      });

    return {
      for (final aisle in aisles)
        aisle: grouped[aisle]!
          ..sort((a, b) {
            if (a.isChecked != b.isChecked) return a.isChecked ? 1 : -1;
            return a.name.toLowerCase().compareTo(b.name.toLowerCase());
          }),
    };
  }

  Future<void> load() async {
    _isLoading = true;
    notifyListeners();
    try {
      _items = await _service.getShoppingList();
      _error = null;
    } catch (e) {
      // Wie beim RecipeProvider: die alte Liste bleibt stehen.
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<bool> add(
    String name, {
    double? amount,
    String? unit,
    String? category,
  }) async {
    try {
      final created = await _service.addShoppingItem(ShoppingItem(
        name: name,
        amount: amount,
        unit: unit,
        // Ohne Angabe sortiert der Server selbst ins Regal ein - deshalb hier
        // kein `?? 'Sonstiges'`, das genau das verhinderte.
        category: category,
      ));
      _items = [..._items, created];
      _error = null;
      notifyListeners();
      return true;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  /// Häkchen setzen - optimistisch. Im Laden ist eine Verzögerung zwischen
  /// Tippen und Durchstreichen der Unterschied zwischen "hab ich" und "hm?".
  Future<void> toggle(ShoppingItem item) async {
    if (item.id == null) return;
    final before = item;
    _replace(item.copyWith(isChecked: !item.isChecked));
    notifyListeners();

    try {
      final updated = await _service.updateShoppingItem(
        item.id!,
        isChecked: !item.isChecked,
      );
      _replace(updated);
    } catch (e) {
      _replace(before);
      _error = e.toString();
    }
    notifyListeners();
  }

  Future<bool> remove(int id) async {
    final before = _items;
    _items = _items.where((i) => i.id != id).toList();
    notifyListeners();

    try {
      await _service.deleteShoppingItem(id);
      return true;
    } catch (e) {
      _items = before;
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  Future<bool> clearChecked() async {
    final before = _items;
    _items = _items.where((i) => !i.isChecked).toList();
    notifyListeners();

    try {
      await _service.deleteCheckedShoppingItems();
      return true;
    } catch (e) {
      _items = before;
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  /// Baut die Zeilen aus dem Wochenplan neu auf.
  ///
  /// Der Server ersetzt dabei die noch nicht abgehakten Wochenplan-Zeilen und
  /// lässt eigene Einträge und Abgehaktes stehen; die Oberfläche sagt genau
  /// diesen Satz, bevor sie es tut.
  Future<int> rebuildFromWeek(DateTime from, DateTime to) async {
    _isRebuilding = true;
    notifyListeners();
    try {
      _items = await _service.rebuildFromMealPlan(from, to);
      _builtFromStart = from;
      _builtFromEnd = to;
      _error = null;
      return _items.length;
    } catch (e) {
      _error = e.toString();
      return -1;
    } finally {
      _isRebuilding = false;
      notifyListeners();
    }
  }

  /// Zutaten eines Rezepts übernehmen - in einer Anfrage, vom Server gerechnet.
  ///
  /// Der Vorgänger schickte N einzelne POSTs ohne Regal und ohne
  /// Zusammenfassen: dasselbe Rezept zweimal ergab zwei Zeilen "200 g Mehl",
  /// eine fehlgeschlagene Anfrage hinterließ eine halb gefüllte Liste, und die
  /// Mengen wichen von denen des Wochenplan-Wegs ab, weil hier mit `double`
  /// und dort mit `BigDecimal` skaliert wurde.
  ///
  /// Gibt **true/false** zurück und keine Anzahl: der Server legt teils neue
  /// Zeilen an und lässt teils vorhandene wachsen, eine Längendifferenz zählt
  /// das nicht. Die Oberfläche sagt deshalb "Zutaten übernommen" - ein wahrer
  /// unscharfer Satz schlägt eine präzise falsche Zahl.
  Future<bool> addFromRecipe(int recipeId, {int? servings}) async {
    try {
      _items = await _service.addRecipeToShoppingList(recipeId, servings: servings);
      _error = null;
      notifyListeners();
      return true;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }

  void _replace(ShoppingItem item) {
    _items = _items.map((i) => i.id == item.id ? item : i).toList();
  }
}
