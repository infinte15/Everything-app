import 'package:everything_app/models/meal_plan.dart';
import 'package:everything_app/models/meal_type.dart';
import 'package:everything_app/models/recipe.dart';
import 'package:everything_app/providers/recipe_provider.dart';
import 'package:everything_app/providers/recipe_space_provider.dart';
import 'package:everything_app/providers/shopping_list_provider.dart';
import 'package:everything_app/screens/recipes/pages/recipe_detail_page.dart';
import 'package:everything_app/theme/kinetic_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import '../../support/fake_recipe_service.dart';

Future<(RecipeProvider, ShoppingListProvider, RecipeSpaceProvider)> _pump(
  WidgetTester tester,
  FakeRecipeService service, {
  int recipeId = 1,
}) async {
  await tester.binding.setSurfaceSize(const Size(500, 2600));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  final recipes = RecipeProvider(service: service);
  final shopping = ShoppingListProvider(service: service);
  final space = RecipeSpaceProvider();
  await recipes.load();

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<RecipeProvider>.value(value: recipes),
        ChangeNotifierProvider<ShoppingListProvider>.value(value: shopping),
        // Hängt in der App über MaterialApp.router und ist damit auch über
        // aufgeschobenen Routen erreichbar - die Detailseite ist eine.
        ChangeNotifierProvider<RecipeSpaceProvider>.value(value: space),
      ],
      child: MaterialApp(
        theme: KineticTheme.darkTheme,
        home: RecipeDetailPage(recipeId: recipeId),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (recipes, shopping, space);
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('de_DE');
  });

  FakeRecipeService serviceWith({
    int servings = 4,
    int? calories = 640,
    int? rating,
  }) {
    return FakeRecipeService(recipes: [
      testRecipe(
        id: 1,
        name: 'Bolognese',
        servings: servings,
        calories: calories,
        rating: rating,
        ingredients: const [
          RecipeIngredient(amount: 250, unit: 'g', name: 'Mehl'),
          RecipeIngredient(amount: 1, unit: 'EL', name: 'Senf'),
          RecipeIngredient(name: 'Salz, Pfeffer'),
        ],
      ),
    ]);
  }

  group('Portionsrechner', () {
    testWidgets('verdoppeln rechnet die Mengen hoch', (tester) async {
      await _pump(tester, serviceWith());

      expect(find.text('250 g'), findsOneWidget);

      // 4 → 8 Portionen.
      for (var i = 0; i < 4; i++) {
        await tester.tap(find.bySemanticsLabel('Mehr Portionen'));
        await tester.pumpAndSettle();
      }

      expect(find.text('500 g'), findsOneWidget);
      expect(find.text('250 g'), findsNothing);
    });

    testWidgets('halbieren macht aus "1 EL" ein "½ EL"', (tester) async {
      // Der eigentliche Grund für formatAmount: "0.5 EL Senf" steht in keinem
      // Kochbuch. Die alte Fassung rechnete mit einer Regex auf dem Zutaten-
      // *Text* und schrieb genau das.
      await _pump(tester, serviceWith());

      expect(find.text('1 EL'), findsOneWidget);

      for (var i = 0; i < 2; i++) {
        await tester.tap(find.bySemanticsLabel('Weniger Portionen'));
        await tester.pumpAndSettle();
      }

      expect(find.text('½ EL'), findsOneWidget);
      expect(find.text('125 g'), findsOneWidget);
    });

    testWidgets('eine Zutat ohne Menge bekommt keine', (tester) async {
      await _pump(tester, serviceWith());

      await tester.tap(find.bySemanticsLabel('Mehr Portionen'));
      await tester.pumpAndSettle();

      expect(find.text('Salz, Pfeffer'), findsOneWidget);
    });

    testWidgets('die Nährwerte skalieren nicht mit', (tester) async {
      // Sie sind je Portion, und eine Portion bleibt eine Portion, egal wie
      // viele man kocht.
      await _pump(tester, serviceWith(calories: 640));

      expect(find.text('640'), findsOneWidget);

      for (var i = 0; i < 4; i++) {
        await tester.tap(find.bySemanticsLabel('Mehr Portionen'));
        await tester.pumpAndSettle();
      }

      expect(find.text('640'), findsOneWidget);
      expect(find.text('je Portion'), findsOneWidget);
    });
  });

  group('Bewertung', () {
    testWidgets('ein Stern-Tipp geht an den Server', (tester) async {
      final service = serviceWith();
      await _pump(tester, service);

      // Die interaktiven Sterne sind die großen im Kopf.
      await tester.tap(find.byIcon(Icons.star_outline_rounded).at(3));
      await tester.pumpAndSettle();

      expect(service.rateCount, 1);
      expect(service.recipes.single.rating, 4);
    });
  });

  group('Einkaufsliste', () {
    // Skaliert und zusammengelegt wird jetzt auf dem Server: eine Anfrage mit
    // den eingestellten Portionen statt N einzelner POSTs ohne Regal. Vorher
    // rechnete der Client mit double und der Server mit BigDecimal, und
    // dasselbe Rezept ergab je nach Weg andere Mengen.
    testWidgets('schickt die eingestellten Portionen an den Server',
        (tester) async {
      final service = serviceWith();
      await _pump(tester, service);

      await tester.tap(find.bySemanticsLabel('Mehr Portionen'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Auf die Einkaufsliste'));
      await tester.pumpAndSettle();

      expect(service.lastFromRecipeId, 1);
      expect(service.lastFromRecipeServings, 5);
      // Keine Anzahl in der Meldung: zusammengelegte Zeilen tauchen in keiner
      // Längendifferenz auf.
      expect(find.text('Zutaten übernommen'), findsOneWidget);
    });
  });

  group('Bild', () {
    testWidgets('ohne Foto steht die Buchstabenkachel im Kopf', (tester) async {
      await _pump(tester, serviceWith());

      // "Bolognese" ist ein Wort - zwei Anfangsbuchstaben.
      expect(find.text('BO'), findsOneWidget);
    });
  });

  group('nicht gefunden', () {
    testWidgets('sagt es und bietet einen Weg zurück', (tester) async {
      await _pump(tester, serviceWith(), recipeId: 999);

      expect(find.textContaining('Rezept nicht gefunden'), findsOneWidget);
      expect(find.text('Zurück'), findsOneWidget);
    });
  });

  // "Gekocht" gab es zweimal, und die beiden wussten nichts voneinander: die
  // Detailseite schrieb ein Kochprotokoll, der Wochenplan hakte ab (und schrieb
  // serverseitig ebenfalls eins). Wer beides tat, zählte doppelt.
  group('Gekocht', () {
    /// Ein Rezept mit einer Mahlzeit im Wochenplan.
    FakeRecipeService serviceWithMeal({
      required DateTime date,
      bool completed = false,
    }) {
      final service = serviceWith();
      service.mealPlan.add(MealPlan(
        id: 10,
        date: date,
        mealType: MealType.abendessen,
        recipeId: 1,
        recipeName: 'Bolognese',
        plannedServings: 6,
        isCompleted: completed,
      ));
      return service;
    }

    Future<void> tapCooked(WidgetTester tester) async {
      await tester.tap(find.text('Gekocht'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Eintragen'));
      await tester.pumpAndSettle();
    }

    testWidgets('mit einer heute offenen Mahlzeit wird abgehakt, nicht geloggt',
        (tester) async {
      final service = serviceWithMeal(date: DateTime.now());
      await _pump(tester, service);

      await tapCooked(tester);

      expect(service.lastCompletedMealId, 10);
      // Genau einer der beiden Wege geht raus - sonst stiege cookCount um zwei.
      expect(service.logCookedCount, 0);
    });

    testWidgets('ohne passende Mahlzeit wird geloggt, nicht abgehakt',
        (tester) async {
      final service = serviceWith();
      await _pump(tester, service);

      await tapCooked(tester);

      expect(service.logCookedCount, 1);
      expect(service.lastCompletedMealId, isNull);
    });

    // Bewusst eng auf heute: eine Mahlzeit rückwirkend in eine Woche zu legen,
    // die man gerade nicht ansieht, wäre eine Überraschung.
    testWidgets('eine Mahlzeit an einem anderen Tag wird nicht abgehakt',
        (tester) async {
      final service = serviceWithMeal(
          date: DateTime.now().add(const Duration(days: 2)));
      await _pump(tester, service);

      await tapCooked(tester);

      expect(service.logCookedCount, 1);
      expect(service.lastCompletedMealId, isNull);
    });

    testWidgets('eine schon abgehakte Mahlzeit wird nicht zweimal abgehakt',
        (tester) async {
      final service = serviceWithMeal(date: DateTime.now(), completed: true);
      await _pump(tester, service);

      await tapCooked(tester);

      expect(service.lastCompletedMealId, isNull);
      expect(service.logCookedCount, 1);
    });
  });

  group('Diese Woche geplant', () {
    testWidgets('nennt Tag und Platz und erscheint nur bei offenen Mahlzeiten',
        (tester) async {
      final service = serviceWith();
      service.mealPlan.add(MealPlan(
        id: 10,
        date: RecipeProvider.mondayOf(DateTime.now()),
        mealType: MealType.abendessen,
        recipeId: 1,
        recipeName: 'Bolognese',
      ));
      await _pump(tester, service);

      expect(find.textContaining('Diese Woche geplant'), findsOneWidget);
      expect(find.textContaining('Abendessen'), findsWidgets);
    });

    testWidgets('ohne geplante Mahlzeit steht dort nichts', (tester) async {
      await _pump(tester, serviceWith());

      expect(find.textContaining('Diese Woche geplant'), findsNothing);
    });
  });

  group('Wochenplan', () {
    // Die eingestellten Portionen gingen beim Einplanen verloren - das Sheet
    // überschrieb sie mit der Grundmenge des Rezepts.
    testWidgets('der Plan-Knopf reicht die Portionen des Steppers durch',
        (tester) async {
      final service = serviceWith();
      await _pump(tester, service);

      await tester.tap(find.bySemanticsLabel('Mehr Portionen'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Zum Wochenplan'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Einplanen'));
      await tester.pumpAndSettle();

      // 5 statt der Grundmenge 4: vorher überschrieb das Sheet den Wert.
      expect(service.mealPlan.single.plannedServings, 5);
      expect(find.text('Eingeplant'), findsOneWidget);
    });
  });
}
