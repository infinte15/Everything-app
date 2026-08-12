import 'package:everything_app/models/meal_plan.dart';
import 'package:everything_app/models/meal_type.dart';
import 'package:everything_app/providers/recipe_provider.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_recipe_service.dart';

void main() {
  late FakeRecipeService service;
  late RecipeProvider provider;

  setUp(() {
    service = FakeRecipeService(recipes: [
      testRecipe(id: 1, name: 'Bolognese', category: 'Pasta & Reis',
          prep: 20, cook: 150, rating: 5, cookCount: 11, isFavorite: true),
      testRecipe(id: 2, name: 'Dal', category: 'Suppe & Eintopf',
          prep: 10, cook: 20, rating: 4, cookCount: 3),
      testRecipe(id: 3, name: 'Ananas-Toast', category: 'Frühstück',
          prep: 5, cook: 5, cookCount: 0),
    ]);
    provider = RecipeProvider(service: service);
  });

  group('load', () {
    test('füllt Rezepte und alle Entdecken-Reihen', () async {
      await provider.load();

      expect(provider.recipes, hasLength(3));
      expect(provider.quick.map((r) => r.name), ['Dal', 'Ananas-Toast']);
      expect(provider.bestRated.map((r) => r.name), ['Bolognese', 'Dal']);
      expect(provider.neverCooked.map((r) => r.name), ['Ananas-Toast']);
      expect(provider.error, isNull);
    });

    test('ein Fehler behält die alte Liste und setzt error', () async {
      await provider.load();
      service.failAll = true;

      await provider.load();

      // Eine geleerte Liste plus Banner liest sich als "du hast keine
      // Rezepte" - das ist eine andere Aussage als "der Server war nicht da".
      expect(provider.recipes, hasLength(3));
      expect(provider.error, isNotNull);
    });
  });

  group('Kochbuch-Filter', () {
    setUp(() => provider.load());

    test('zeigt ohne Filter alle Rezepte, nicht nur Favoriten', () async {
      await provider.load();

      // Der Kernfehler der alten Oberfläche.
      expect(provider.filtered, hasLength(3));
      expect(provider.favorites, hasLength(1));
    });

    test('Herz-Filter zeigt nur Favoriten', () async {
      await provider.load();
      provider.setOnlyFavorites(true);

      expect(provider.filtered.map((r) => r.name), ['Bolognese']);
    });

    test('Kategorienfilter schaltet beim zweiten Tippen wieder ab', () async {
      await provider.load();

      provider.setCategoryFilter('Frühstück');
      expect(provider.filtered.map((r) => r.name), ['Ananas-Toast']);

      provider.setCategoryFilter('Frühstück');
      expect(provider.filtered, hasLength(3));
    });

    test('Sortierung greift', () async {
      await provider.load();

      provider.setSort(RecipeSort.name);
      expect(provider.filtered.first.name, 'Ananas-Toast');

      provider.setSort(RecipeSort.schnellste);
      expect(provider.filtered.first.name, 'Ananas-Toast');
      expect(provider.filtered.last.name, 'Bolognese');

      provider.setSort(RecipeSort.beste);
      expect(provider.filtered.first.name, 'Bolognese');
    });
  });

  group('optimistische Änderungen', () {
    test('toggleFavorite schlägt sofort durch', () async {
      await provider.load();

      final future = provider.toggleFavorite(2);
      // Noch vor der Antwort des Servers.
      expect(provider.byId(2)!.isFavorite, isTrue);

      await future;
      expect(provider.byId(2)!.isFavorite, isTrue);
      expect(service.favoriteCount, 1);
    });

    test('toggleFavorite nimmt sich bei einem Fehler zurück', () async {
      await provider.load();
      service.failAll = true;

      await provider.toggleFavorite(2);

      expect(provider.byId(2)!.isFavorite, isFalse);
      expect(provider.error, isNotNull);
    });

    test('rate schlägt sofort durch und ruft den Server', () async {
      await provider.load();

      await provider.rate(3, 4);

      expect(provider.byId(3)!.rating, 4);
      expect(service.rateCount, 1);
    });

    test('rate nimmt sich bei einem Fehler zurück', () async {
      await provider.load();
      service.failAll = true;

      await provider.rate(1, 2);

      expect(provider.byId(1)!.rating, 5);
    });
  });

  group('Wochenplan', () {
    test('weekStart ist immer ein Montag', () {
      expect(provider.weekStart.weekday, DateTime.monday);
      expect(RecipeProvider.mondayOf(DateTime(2026, 8, 13)).day, 10);
    });

    test('planMeal legt einen Eintrag mit echtem Datum und Typ an', () async {
      await provider.load();
      final day = provider.weekStart.add(const Duration(days: 2));

      final ok = await provider.planMeal(
        recipeId: 1,
        date: day,
        mealType: MealType.mittagessen,
      );

      expect(ok, isTrue);
      final planned = provider.mealsOn(day, MealType.mittagessen);
      expect(planned, hasLength(1));
      expect(planned.first.recipeId, 1);
      // Der alte Provider schickte hier BREAKFAST/LUNCH/DINNER.
      expect(planned.first.mealType.wire, 'MITTAGESSEN');
    });

    test('completeMeal lädt die Rezepte nach', () async {
      await provider.load();
      final day = provider.weekStart;
      await provider.planMeal(
          recipeId: 1, date: day, mealType: MealType.abendessen);
      final loadsBefore = service.loadCount;

      await provider.completeMeal(provider.mealPlan.first.id!);

      // Der Server schreibt beim Abhaken in das Rezept (cookCount,
      // lastCookedAt) - ohne Nachladen zeigte das Kochbuch daneben eine
      // veraltete Zahl.
      expect(service.loadCount, greaterThan(loadsBefore));
      expect(provider.mealPlan.first.isCompleted, isTrue);
    });

    // Der Haken im Wochenplan soll dasselbe können wie der Knopf auf der
    // Rezeptseite - vorher hakte er nur ab.
    test('completeMeal reicht Portionen, Sterne und Notiz durch', () async {
      await provider.load();
      await provider.planMeal(
          recipeId: 1, date: provider.weekStart, mealType: MealType.abendessen);

      await provider.completeMeal(
        provider.mealPlan.first.id!,
        servings: 8,
        rating: 5,
        note: 'Mehr Butter',
      );

      expect(service.lastCompletedServings, 8);
      expect(service.lastCompletedRating, 5);
      expect(service.lastCompletedNote, 'Mehr Butter');
    });

    // Die Detailseite darf die Woche nicht überschreiben, die der Plan-Reiter
    // gerade anzeigt - sonst springt er dem Nutzer unter den Fingern weg.
    test('mealsForRecipeThisWeek filtert, ohne weekStart oder mealPlan anzufassen',
        () async {
      final monday = RecipeProvider.mondayOf(DateTime.now());
      service.mealPlan.addAll([
        MealPlan(id: 10, date: monday, mealType: MealType.abendessen, recipeId: 1),
        MealPlan(id: 11, date: monday, mealType: MealType.mittagessen, recipeId: 2),
      ]);
      await provider.load();
      await provider.nextWeek();
      final weekBefore = provider.weekStart;
      final planBefore = provider.mealPlan;

      final meals = await provider.mealsForRecipeThisWeek(1);

      expect(meals.map((m) => m.id), [10]);
      expect(provider.weekStart, weekBefore);
      expect(provider.mealPlan, same(planBefore));
    });

    test('deleteMeal entfernt optimistisch und stellt bei Fehler wieder her',
        () async {
      await provider.load();
      await provider.planMeal(
          recipeId: 1, date: provider.weekStart, mealType: MealType.abendessen);
      final id = provider.mealPlan.first.id!;
      service.failAll = true;

      await provider.deleteMeal(id);

      expect(provider.mealPlan, hasLength(1));
      expect(provider.error, isNotNull);
    });

    test('mealsOn trennt nach Tag und Mahlzeit', () async {
      service.mealPlan.addAll([
        MealPlan(
            id: 10,
            date: provider.weekStart,
            mealType: MealType.fruehstueck,
            recipeId: 3),
        MealPlan(
            id: 11,
            date: provider.weekStart,
            mealType: MealType.abendessen,
            recipeId: 1),
      ]);
      await provider.load();

      expect(provider.mealsOn(provider.weekStart), hasLength(2));
      expect(
        provider.mealsOn(provider.weekStart, MealType.fruehstueck).single.recipeId,
        3,
      );
    });
  });

  group('Suche', () {
    test('reicht den Suchbegriff durch und füllt die Treffer', () async {
      await provider.load();

      await provider.search('dal');

      expect(service.lastSearch, 'dal');
      expect(provider.searchResults.map((r) => r.name), ['Dal']);
    });

    test('ein leerer Suchbegriff fragt gar nicht erst', () async {
      await provider.search('   ');

      expect(service.lastSearch, isNull);
      expect(provider.searchResults, isEmpty);
    });
  });

  group('anlegen und löschen', () {
    test('create hängt das Rezept an', () async {
      await provider.load();

      final created = await provider.create(testRecipe(id: 0, name: 'Neu'));

      expect(created, isNotNull);
      expect(provider.recipes.any((r) => r.name == 'Neu'), isTrue);
    });

    test('delete entfernt auch die Wochenplan-Einträge des Rezepts', () async {
      await provider.load();
      await provider.planMeal(
          recipeId: 1, date: provider.weekStart, mealType: MealType.abendessen);

      await provider.delete(1);

      expect(provider.byId(1), isNull);
      expect(provider.mealPlan.where((m) => m.recipeId == 1), isEmpty);
    });
  });
}
