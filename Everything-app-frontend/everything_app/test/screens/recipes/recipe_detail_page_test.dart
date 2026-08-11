import 'package:everything_app/models/recipe.dart';
import 'package:everything_app/providers/recipe_provider.dart';
import 'package:everything_app/providers/shopping_list_provider.dart';
import 'package:everything_app/screens/recipes/pages/recipe_detail_page.dart';
import 'package:everything_app/theme/kinetic_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import '../../support/fake_recipe_service.dart';

Future<(RecipeProvider, ShoppingListProvider)> _pump(
  WidgetTester tester,
  FakeRecipeService service, {
  int recipeId = 1,
}) async {
  await tester.binding.setSurfaceSize(const Size(500, 2600));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  final recipes = RecipeProvider(service: service);
  final shopping = ShoppingListProvider(service: service);
  await recipes.load();

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<RecipeProvider>.value(value: recipes),
        ChangeNotifierProvider<ShoppingListProvider>.value(value: shopping),
      ],
      child: MaterialApp(
        theme: KineticTheme.darkTheme,
        home: RecipeDetailPage(recipeId: recipeId),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (recipes, shopping);
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
    testWidgets('übernimmt die skalierten Zutaten', (tester) async {
      final service = serviceWith();
      final (_, shopping) = await _pump(tester, service);

      await tester.tap(find.bySemanticsLabel('Mehr Portionen'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Auf die Einkaufsliste'));
      await tester.pumpAndSettle();

      expect(shopping.items, hasLength(3));
      // 5 statt 4 Portionen: 250 g × 1,25.
      expect(shopping.items.first.amount, closeTo(312.5, 0.01));
      expect(find.text('3 Zutaten hinzugefügt'), findsOneWidget);
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
}
