import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/recipe.dart';
import '../../../providers/recipe_space_provider.dart';
import '../../../providers/shopping_list_provider.dart';
import '../../../theme/kinetic_theme.dart';
import 'plan_meal_sheet.dart';

/// Die zwei Wege, die aus einem Rezept herausführen: in den Wochenplan und auf
/// die Einkaufsliste.
///
/// Hier gebündelt, weil sie von vier Stellen aus gebraucht werden - dem
/// Zeilenmenü im Kochbuch, demselben in den Suchtreffern und den beiden Knöpfen
/// der Detailseite. Vier Kopien wären vier Gelegenheiten, es unterschiedlich zu
/// formulieren.

/// Plant [recipe] ein und meldet den Erfolg mit einem Weg in den Wochenplan.
///
/// [servings] überschreibt die Grundmenge des Rezepts - die Detailseite gibt
/// hier ihren Stepper-Wert mit.
///
/// [popAfterJump] gehört der Detailseite: sie liegt als aufgeschobene Route
/// über der Reiter-Hülle und muss sich schließen, damit der Reiterwechsel
/// sichtbar wird. Aus einem Reiter heraus wäre dasselbe `pop` ein Sprung aus
/// dem ganzen Space.
Future<void> planRecipeFlow(
  BuildContext context, {
  required Recipe recipe,
  int? servings,
  bool popAfterJump = false,
}) async {
  final planned = await PlanMealSheet.show(
    context,
    date: DateTime.now(),
    recipe: recipe,
    servings: servings,
  );
  if (planned != true || !context.mounted) return;

  _snack(context, 'Eingeplant', tab: 2, popAfterJump: popAfterJump);
}

/// Übernimmt die Zutaten von [recipe] auf die Einkaufsliste.
///
/// Die Meldung nennt **keine Anzahl**: der Server legt teils Zeilen an und
/// lässt teils vorhandene wachsen, das taucht in keiner Längendifferenz auf.
/// Ein wahrer unscharfer Satz schlägt eine präzise falsche Zahl.
Future<void> addToShoppingListFlow(
  BuildContext context, {
  required int recipeId,
  int? servings,
  bool popAfterJump = false,
}) async {
  final shopping = context.read<ShoppingListProvider>();
  final ok = await shopping.addFromRecipe(recipeId, servings: servings);
  if (!context.mounted) return;

  if (!ok) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(shopping.error ?? 'Die Zutaten ließen sich nicht übernehmen'),
      backgroundColor: KineticTheme.surfaceElevated,
    ));
    return;
  }

  _snack(context, 'Zutaten übernommen', tab: 3, popAfterJump: popAfterJump);
}

void _snack(
  BuildContext context,
  String message, {
  required int tab,
  required bool popAfterJump,
}) {
  final space = context.read<RecipeSpaceProvider>();
  final navigator = Navigator.of(context);

  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
    content: Text(message),
    backgroundColor: KineticTheme.surfaceElevated,
    action: SnackBarAction(
      label: 'Ansehen',
      textColor: KineticTheme.primary,
      onPressed: () {
        space.openTab(tab);
        if (popAfterJump && navigator.canPop()) navigator.pop();
      },
    ),
  ));
}
