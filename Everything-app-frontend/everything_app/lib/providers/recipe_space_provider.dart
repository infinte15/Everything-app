import 'package:flutter/foundation.dart';

/// Welcher Reiter im Rezepte-Space offen ist.
///
/// Sieht nach zu viel für eine einzige Zahl aus, ist aber der einzige Weg, der
/// funktioniert: Die Rezept-Detailseite ist eine *aufgeschobene Route* über der
/// Reiter-Hülle, kein Kind von ihr. Ein `InheritedWidget` aus `RecipesScreen`
/// wäre damit kein Vorfahre, eine `Notification` liefe in den Navigator statt in
/// die Hülle, und ein durchgereichter Rückruf scheitert daran, dass
/// `/recipes/:id` ein Tiefenlink ist und nie eine Closure bekommt.
/// `context.go('/recipes?tab=3')` wiederum behält den `State` — go_router
/// schlüsselt auf den Pfad, `initState` läuft nicht erneut — und die Adresse
/// trüge dauerhaft `?tab=3`.
///
/// Der `MultiProvider` in `main.dart` umschließt `MaterialApp.router` und ist
/// damit Vorfahre **jeder** Route, auch der aufgeschobenen. Vier Aufrufstellen
/// in drei Widget-Ebenen hängen daran: die Zeilenmenüs in Kochbuch und
/// Suchtreffern, die beiden SnackBars der Detailseite und der Rückweg aus der
/// Einkaufsliste.
class RecipeSpaceProvider extends ChangeNotifier {
  int _tab = 0;

  /// 0 Entdecken, 1 Kochbuch, 2 Wochenplan, 3 Einkaufsliste.
  int get tab => _tab;

  void openTab(int index) {
    if (index == _tab) return;
    _tab = index;
    notifyListeners();
  }
}
