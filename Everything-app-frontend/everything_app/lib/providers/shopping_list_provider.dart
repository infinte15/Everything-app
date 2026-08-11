import 'package:flutter/material.dart';

import '../config/recipe_aisles.dart';
import '../models/recipe.dart';
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

  /// Nach Ladenregalen gruppiert, in Laufreihenfolge - und innerhalb einer
  /// Gruppe rutscht Abgehaktes ans Ende. Leere Gruppen kommen nicht vor.
  Map<String, List<ShoppingItem>> get byAisle {
    final grouped = <String, List<ShoppingItem>>{};
    for (final item in _items) {
      grouped.putIfAbsent(item.category, () => []).add(item);
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
        // Ohne Angabe sortiert der Server selbst ins Regal ein.
        category: category ?? 'Sonstiges',
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

  /// Zutaten einer Rezeptseite übernehmen.
  ///
  /// Einzelne POSTs über `Future.wait`: der Controller hat keinen
  /// Sammel-Endpunkt, und für fünfzehn Zeilen über WLAN ist das in Ordnung. Ein
  /// `POST /shopping-list/batch` wäre sauberer, lohnt aber erst, wenn es
  /// messbar stört.
  Future<int> addIngredients(List<RecipeIngredient> ingredients) async {
    try {
      final created = await Future.wait(ingredients.map(
        (ingredient) => _service.addShoppingItem(ShoppingItem(
          name: ingredient.name,
          amount: ingredient.amount,
          unit: ingredient.unit,
        )),
      ));
      _items = [..._items, ...created];
      _error = null;
      notifyListeners();
      return created.length;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      // Ein Teil kann durchgegangen sein - deshalb neu laden statt raten.
      await load();
      return -1;
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
