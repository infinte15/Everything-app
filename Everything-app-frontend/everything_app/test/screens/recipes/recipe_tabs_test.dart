import 'package:everything_app/models/meal_plan.dart';
import 'package:everything_app/models/meal_type.dart';
import 'package:everything_app/models/shopping_item.dart';
import 'package:everything_app/providers/recipe_provider.dart';
import 'package:everything_app/providers/recipe_space_provider.dart';
import 'package:everything_app/providers/shopping_list_provider.dart';
import 'package:everything_app/screens/recipes/pages/recipe_cookbook_tab.dart';
import 'package:everything_app/screens/recipes/pages/recipe_discover_tab.dart';
import 'package:everything_app/screens/recipes/pages/recipe_plan_tab.dart';
import 'package:everything_app/screens/recipes/pages/recipe_shopping_tab.dart';
import 'package:everything_app/theme/kinetic_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import '../../support/fake_recipe_service.dart';

/// Baut einen Reiter mit beiden Providern auf.
///
/// `Image.network` bekommt in Widget-Tests einen Fake-Client mit 400er-Antwort,
/// es greift also immer der `errorBuilder` von [RecipeImage]. Weil dort die
/// Buchstabenkachel steht, laufen diese Tests trotzdem durch - ohne den
/// Platzhalter wäre jeder einzelne rot.
Future<(RecipeProvider, ShoppingListProvider, RecipeSpaceProvider)> _pump(
  WidgetTester tester,
  Widget Function(RecipeProvider recipes) build, {
  FakeRecipeService? service,
}) async {
  // Hoher Schirm, damit die Reihen von "Entdecken" nicht unterhalb der
  // Bildkante liegen - eine ListView baut nur, was sichtbar ist, und ein
  // Finder findet dann nichts.
  await tester.binding.setSurfaceSize(const Size(500, 2400));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  final fake = service ?? FakeRecipeService();
  final recipes = RecipeProvider(service: fake);
  final shopping = ShoppingListProvider(service: fake);
  final space = RecipeSpaceProvider();
  await recipes.load();
  await shopping.load();

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
        home: Scaffold(body: build(recipes)),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (recipes, shopping, space);
}

void main() {
  setUpAll(() async {
    // Der Wochenplan schreibt deutsche Wochentags- und Monatsnamen.
    await initializeDateFormatting('de_DE');
  });

  group('Kochbuch', () {
    testWidgets('zeigt alle Rezepte, nicht nur die Favoriten', (tester) async {
      // Der Kernfehler der alten Oberfläche: sie zeigte ausschließlich
      // Favoriten, und ein neu angelegtes Rezept ohne Herz war unauffindbar.
      final service = FakeRecipeService(recipes: [
        testRecipe(id: 1, name: 'Bolognese', isFavorite: true),
        testRecipe(id: 2, name: 'Dal'),
        testRecipe(id: 3, name: 'Shakshuka'),
      ]);

      await _pump(tester, (_) => const RecipeCookbookTab(), service: service);

      expect(find.text('Bolognese'), findsOneWidget);
      expect(find.text('Dal'), findsOneWidget);
      expect(find.text('Shakshuka'), findsOneWidget);
      expect(find.text('3 Rezepte'), findsOneWidget);
    });

    testWidgets('der Herz-Filter blendet die übrigen aus', (tester) async {
      final service = FakeRecipeService(recipes: [
        testRecipe(id: 1, name: 'Bolognese', isFavorite: true),
        testRecipe(id: 2, name: 'Dal'),
      ]);
      final (recipes, _, _) =
          await _pump(tester, (_) => const RecipeCookbookTab(), service: service);

      recipes.setOnlyFavorites(true);
      await tester.pumpAndSettle();

      expect(find.text('Bolognese'), findsOneWidget);
      expect(find.text('Dal'), findsNothing);
    });

    testWidgets('ohne Rezepte steht dort ein Weg nach vorn', (tester) async {
      await _pump(tester, (_) => const RecipeCookbookTab());

      expect(find.text('Noch keine Rezepte'), findsOneWidget);
      expect(find.text('Rezept anlegen'), findsOneWidget);
    });

    // Aus dem Kochbuch führte bisher nur ein Weg, und der ging auf die
    // Detailseite - obwohl beide Sheets längst existierten.
    testWidgets('das Zeilenmenü bietet einplanen und auf die Liste',
        (tester) async {
      final service = FakeRecipeService(recipes: [testRecipe(id: 1)]);
      await _pump(tester, (_) => const RecipeCookbookTab(), service: service);

      await tester.tap(find.byTooltip('Mehr'));
      await tester.pumpAndSettle();

      expect(find.text('Einplanen…'), findsOneWidget);
      expect(find.text('Auf die Einkaufsliste'), findsOneWidget);
    });

    testWidgets('"Auf die Einkaufsliste" ruft den Sammel-Endpunkt',
        (tester) async {
      final service = FakeRecipeService(recipes: [testRecipe(id: 1)]);
      await _pump(tester, (_) => const RecipeCookbookTab(), service: service);

      await tester.tap(find.byTooltip('Mehr'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Auf die Einkaufsliste'));
      await tester.pumpAndSettle();

      expect(service.lastFromRecipeId, 1);
      // Aus dem Kochbuch ohne eingestellte Portionen - es gilt die Grundmenge.
      expect(service.lastFromRecipeServings, isNull);
      expect(find.text('Zutaten übernommen'), findsOneWidget);
    });

    testWidgets('"Einplanen…" öffnet das Sheet mit dem Rezept', (tester) async {
      final service = FakeRecipeService(
          recipes: [testRecipe(id: 1, name: 'Bolognese')]);
      await _pump(tester, (_) => const RecipeCookbookTab(), service: service);

      await tester.tap(find.byTooltip('Mehr'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Einplanen…'));
      await tester.pumpAndSettle();

      expect(find.text('Einplanen'), findsWidgets);
      expect(find.text('Bolognese'), findsWidgets);
    });
  });

  group('Entdecken', () {
    testWidgets('eine leere Reihe wird nicht gezeichnet', (tester) async {
      // Alle drei sind schnell und nie gekocht - "Deine besten" bleibt leer.
      final service = FakeRecipeService(recipes: [
        testRecipe(id: 1, name: 'Toast', prep: 2, cook: 3),
      ]);

      await _pump(tester, (_) => RecipeDiscoverTab(onOpenCookbook: () {}),
          service: service);

      expect(find.text('IN 30 MINUTEN FERTIG'), findsOneWidget);
      expect(find.text('DEINE BESTEN'), findsNothing);
    });

    testWidgets('die Suche ruft den Service mit der Eingabe', (tester) async {
      final service = FakeRecipeService(recipes: [
        testRecipe(id: 1, name: 'Kürbissuppe'),
        testRecipe(id: 2, name: 'Dal'),
      ]);
      await _pump(tester, (_) => RecipeDiscoverTab(onOpenCookbook: () {}),
          service: service);

      await tester.enterText(find.byType(TextField).first, 'Kürbis');
      // Die Entprellung liegt bei 300 ms.
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(service.lastSearch, 'Kürbis');
      // RecipeSection setzt seine Überschriften in Großbuchstaben.
      expect(find.text('1 TREFFER'), findsOneWidget);
      expect(find.text('Kürbissuppe'), findsWidgets);
    });

    testWidgets('ein Kategorie-Chip setzt den Filter und wechselt den Reiter',
        (tester) async {
      // Bei Chefkoch führt eine Kategorie in eine Liste - vorher hatten die
      // Chips gar kein onTap.
      final service = FakeRecipeService(recipes: [
        testRecipe(id: 1, name: 'Dal', category: 'Suppe & Eintopf'),
      ]);
      var wechsel = 0;
      final (recipes, _, _) = await _pump(
        tester,
        (_) => RecipeDiscoverTab(onOpenCookbook: () => wechsel++),
        service: service,
      );

      await tester.tap(find.text('Suppe & Eintopf'));
      await tester.pumpAndSettle();

      expect(recipes.categoryFilter, 'Suppe & Eintopf');
      expect(wechsel, 1);
    });

    testWidgets('ein Fehler steht als Banner da, nicht als leerer Schirm',
        (tester) async {
      final service = FakeRecipeService(recipes: [testRecipe()]);
      final (recipes, _, _) = await _pump(
          tester, (_) => RecipeDiscoverTab(onOpenCookbook: () {}),
          service: service);

      service.failAll = true;
      await recipes.load();
      await tester.pumpAndSettle();

      expect(find.text('Wiederholen'), findsOneWidget);
      expect(find.textContaining('Server nicht erreichbar'), findsOneWidget);
    });
  });

  group('Wochenplan', () {
    testWidgets('eine Mahlzeit steht unter dem richtigen Tag und Platz',
        (tester) async {
      // Genau der alte Fehler: die Seite war nach englischen Wochentagsnamen
      // aufgebaut und konnte einen Eintrag nie finden.
      final monday = RecipeProvider.mondayOf(DateTime.now());
      final service = FakeRecipeService(
        recipes: [testRecipe(id: 1, name: 'Bolognese')],
        mealPlan: [
          MealPlan(
            id: 10,
            date: monday.add(const Duration(days: 2)),
            mealType: MealType.mittagessen,
            recipeId: 1,
            recipeName: 'Bolognese',
            plannedServings: 2,
          ),
        ],
      );

      await _pump(tester, (_) => RecipePlanTab(onOpenShopping: () {}),
          service: service);

      expect(find.text('Bolognese'), findsOneWidget);
      expect(find.text('2 Portionen'), findsOneWidget);
      // Frei gebliebene Plätze laden ein.
      expect(find.text('+ Rezept wählen'), findsWidgets);
    });

    testWidgets('die Pfeile verschieben die Woche', (tester) async {
      final (recipes, _, _) =
          await _pump(tester, (_) => RecipePlanTab(onOpenShopping: () {}));
      final before = recipes.weekStart;

      await tester.tap(find.byTooltip('Nächste Woche'));
      await tester.pumpAndSettle();

      expect(recipes.weekStart, before.add(const Duration(days: 7)));
      expect(find.text('Heute'), findsOneWidget);
    });

    testWidgets('das Häkchen hakt die Mahlzeit ab', (tester) async {
      final monday = RecipeProvider.mondayOf(DateTime.now());
      final service = FakeRecipeService(
        recipes: [testRecipe(id: 1, name: 'Bolognese')],
        mealPlan: [
          MealPlan(
            id: 10,
            date: monday,
            mealType: MealType.abendessen,
            recipeId: 1,
            recipeName: 'Bolognese',
          ),
        ],
      );
      await _pump(tester, (_) => RecipePlanTab(onOpenShopping: () {}),
          service: service);

      // Der Haken öffnet jetzt dasselbe Sheet wie der Knopf auf der
      // Rezeptseite - vorher hakte er stumm ab, und eine Bewertung ging nur
      // über den Umweg über die Detailseite.
      await tester.tap(find.byTooltip('Als gekocht abhaken'));
      await tester.pumpAndSettle();
      expect(find.text('Eintragen'), findsOneWidget);

      await tester.tap(find.text('Eintragen'));
      await tester.pumpAndSettle();

      expect(service.lastCompletedMealId, 10);
      // Die geplanten Portionen stehen im Sheet vor und gehen mit ins
      // Kochprotokoll.
      expect(service.lastCompletedServings, 1);
    });
  });

  group('Einkaufsliste', () {
    testWidgets('gruppiert nach Regalen in Laufreihenfolge', (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Erbsen', category: 'Tiefkühl'),
        ShoppingItem(id: 2, name: 'Möhren', category: 'Obst & Gemüse'),
      ]);

      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      final gemuese = tester.getTopLeft(find.text('OBST & GEMÜSE')).dy;
      final tiefkuehl = tester.getTopLeft(find.text('TIEFKÜHL')).dy;
      expect(gemuese, lessThan(tiefkuehl));
    });

    testWidgets('ein Häkchen geht an den Server', (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Möhren', category: 'Obst & Gemüse'),
      ]);
      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      await tester.tap(find.text('Möhren'));
      await tester.pumpAndSettle();

      // Vorher lebte das Häkchen nur im Speicher und war beim nächsten Laden
      // wieder weg.
      expect(service.patchCount, 1);
      expect(service.shoppingList.single.isChecked, isTrue);
    });

    testWidgets('"Erledigte löschen" gibt es nur, wenn etwas erledigt ist',
        (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Möhren', category: 'Obst & Gemüse'),
      ]);
      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      await tester.tap(find.byIcon(Icons.more_horiz));
      await tester.pumpAndSettle();
      expect(find.text('Erledigte löschen'), findsNothing);
      expect(find.text('Aus Wochenplan aufbauen…'), findsOneWidget);
    });

    testWidgets('die Zähler stehen im Kopf', (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Möhren', category: 'Obst & Gemüse'),
        ShoppingItem(
            id: 2, name: 'Milch', category: 'Kühlregal', isChecked: true),
      ]);
      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      expect(find.text('1 offen · 1 erledigt'), findsOneWidget);
    });

    // Eine Zeile sagte nur ein nacktes "Wochenplan" - nicht aus welcher Woche,
    // und ohne Weg zurück.
    testWidgets('nach einem Aufbau nennt der Kopf die Woche', (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Möhren', category: 'Obst & Gemüse'),
      ]);
      final (_, shopping, _) = await _pump(
          tester, (_) => const RecipeShoppingTab(), service: service);

      // 17.8.2026 ist ein Montag, das ist KW 34.
      await shopping.rebuildFromWeek(DateTime(2026, 8, 17), DateTime(2026, 8, 23));
      await tester.pumpAndSettle();

      expect(find.textContaining('Aus KW 34'), findsOneWidget);
      expect(find.textContaining('17.–23. Aug'), findsOneWidget);
    });

    testWidgets('vorher steht dort der unscharfe Satz, wenn Plan-Zeilen da sind',
        (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(
          id: 1,
          name: 'Hackfleisch',
          category: 'Fleisch & Fisch',
          source: ShoppingItemSource.mealPlan,
        ),
      ]);
      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      expect(find.text('Enthält Zeilen aus einem Wochenplan'), findsOneWidget);
      expect(find.textContaining('Aus KW'), findsNothing);
    });

    testWidgets('ohne Plan-Zeilen und ohne Woche schweigt der Kopf',
        (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Möhren', category: 'Obst & Gemüse'),
      ]);
      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      expect(find.textContaining('Wochenplan'), findsNothing);
      expect(find.textContaining('Aus KW'), findsNothing);
    });

    testWidgets('der Leerzustand führt in den Wochenplan', (tester) async {
      var opened = false;
      await _pump(tester, (_) => RecipeShoppingTab(onOpenPlan: () => opened = true));

      await tester.tap(find.text('Zum Wochenplan'));
      await tester.pumpAndSettle();

      expect(opened, isTrue);
    });

    // Der Nutzer steht auf dem Einkaufsreiter und kann die Woche nicht sehen -
    // "der angezeigten Woche" half ihm nicht.
    testWidgets('der Aufbau-Dialog nennt die Woche konkret', (tester) async {
      final service = FakeRecipeService(shoppingList: const [
        ShoppingItem(id: 1, name: 'Möhren', category: 'Obst & Gemüse'),
      ]);
      await _pump(tester, (_) => const RecipeShoppingTab(), service: service);

      await tester.tap(find.byIcon(Icons.more_horiz));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Aus Wochenplan aufbauen…'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Zutaten der KW'), findsOneWidget);
    });
  });

  group('Bilder', () {
    testWidgets('ein Rezept ohne Bild zeigt die Buchstabenkachel',
        (tester) async {
      final service = FakeRecipeService(recipes: [
        testRecipe(id: 1, name: 'Rote-Linsen-Dal', imageUrl: null),
      ]);

      await _pump(tester, (_) => const RecipeCookbookTab(), service: service);

      // Zwei Wörter, zwei Anfangsbuchstaben - keine Buchstabensuppe.
      expect(find.text('RL'), findsOneWidget);
    });
  });
}
