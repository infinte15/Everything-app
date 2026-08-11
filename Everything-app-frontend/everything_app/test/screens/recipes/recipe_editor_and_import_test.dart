import 'package:everything_app/models/recipe.dart';
import 'package:everything_app/models/recipe_import_preview.dart';
import 'package:everything_app/providers/recipe_provider.dart';
import 'package:everything_app/screens/recipes/pages/recipe_editor_page.dart';
import 'package:everything_app/screens/recipes/pages/recipe_import_preview_page.dart';
import 'package:everything_app/theme/kinetic_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import '../../support/fake_recipe_service.dart';

Future<RecipeProvider> _pump(
  WidgetTester tester,
  Widget page, {
  FakeRecipeService? service,
}) async {
  // Telefonformat, nicht überhoch: der Editor öffnet Bottom Sheets, und die
  // rechnen ihre Höhe aus der Schirmhöhe - auf einem 3200px-Schirm platzt das
  // Sheet, ohne dass die Seite selbst etwas falsch macht.
  await tester.binding.setSurfaceSize(const Size(420, 900));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  final provider = RecipeProvider(service: service ?? FakeRecipeService());
  await tester.pumpWidget(
    ChangeNotifierProvider<RecipeProvider>.value(
      value: provider,
      child: MaterialApp(theme: KineticTheme.darkTheme, home: page),
    ),
  );
  await tester.pumpAndSettle();
  return provider;
}

/// Scrollt, bis das Ziel im Bild ist - die Seiten sind länger als ein Schirm.
///
/// [delta] negativ, wenn das Ziel oberhalb liegt: `scrollUntilVisible` sucht
/// nur in eine Richtung und wirft sonst `Bad state: No element`.
Future<void> _scrollTo(WidgetTester tester, Finder target,
    {double delta = 300}) async {
  await tester.scrollUntilVisible(target, delta,
      scrollable: find.byType(Scrollable).first);
  await tester.pumpAndSettle();
}

void main() {
  group('Editor', () {
    testWidgets('ohne Namen wird nicht gespeichert', (tester) async {
      final service = FakeRecipeService();
      await _pump(tester, const RecipeEditorPage(), service: service);

      await _scrollTo(tester, find.text('Speichern'));
      await tester.tap(find.text('Speichern'));
      await tester.pumpAndSettle();

      // Als Meldung unten am Schirm und nicht nur als Text oben am Feld: das
      // Feld ist aus dem Bild gescrollt, wenn man den Speichern-Knopf erreicht.
      expect(find.text('Ohne Namen lässt sich das Rezept nicht speichern.'),
          findsOneWidget);
      expect(service.recipes, isEmpty);
    });

    testWidgets('ohne Zutat und Schritt sagt er, was fehlt', (tester) async {
      // Das DTO hat @NotEmpty auf beiden Listen - ein 400 vom Server darf nie
      // die erste Rückmeldung auf zehn Minuten Tipparbeit sein.
      final service = FakeRecipeService();
      await _pump(tester, const RecipeEditorPage(), service: service);

      await tester.enterText(find.byType(TextFormField).first, 'Testgericht');
      await _scrollTo(tester, find.text('Speichern'));
      await tester.tap(find.text('Speichern'));
      await tester.pumpAndSettle();

      expect(find.text('Mindestens eine Zutat und ein Schritt sind nötig.'),
          findsOneWidget);
      expect(service.recipes, isEmpty);
    });

    testWidgets('ein vollständiges Rezept wird angelegt', (tester) async {
      final service = FakeRecipeService();
      await _pump(tester, const RecipeEditorPage(), service: service);

      await tester.enterText(find.byType(TextFormField).first, 'Testgericht');
      await _scrollTo(tester, find.widgetWithText(TextField, 'Zutat'));
      await tester.enterText(find.widgetWithText(TextField, 'Zutat'), 'Mehl');
      await _scrollTo(tester, find.widgetWithText(TextField, 'Was ist zu tun?'));
      await tester.enterText(
          find.widgetWithText(TextField, 'Was ist zu tun?'), 'Verrühren.');
      await _scrollTo(tester, find.text('Speichern'));
      await tester.tap(find.text('Speichern'));
      await tester.pumpAndSettle();

      expect(service.recipes, hasLength(1));
      expect(service.recipes.single.name, 'Testgericht');
      expect(service.recipes.single.ingredients.single.name, 'Mehl');
      expect(service.recipes.single.steps.single.text, 'Verrühren.');
    });

    testWidgets('"Zeilen einfügen" hängt zerlegte Zutaten an', (tester) async {
      // Nutzt POST /api/recipes/ingredients/parse - den Endpunkt gab es schon,
      // aufgerufen hat ihn bisher niemand.
      await _pump(tester, const RecipeEditorPage());

      await _scrollTo(tester, find.text('Zeilen einfügen…'));
      await tester.tap(find.text('Zeilen einfügen…'));
      await tester.pumpAndSettle();
      await tester.enterText(
          find.byType(TextField).last, '400 g Mehl\n1 Prise Salz');
      await tester.tap(find.text('Zerlegen'));
      await tester.pumpAndSettle();

      // Der Fake gibt je Zeile eine Zutat zurück - zwei Zeilen werden zu zwei
      // Zutatenzeilen, jede mit ihrem Text im Namensfeld.
      final gefuellt = tester
          .widgetList<TextField>(find.byType(TextField, skipOffstage: false))
          .map((field) => field.controller?.text)
          .toList();
      expect(gefuellt, contains('400 g Mehl'));
      expect(gefuellt, contains('1 Prise Salz'));
    });

    testWidgets('beim Bearbeiten stehen die Felder gefüllt da', (tester) async {
      final recipe = testRecipe(id: 5, name: 'Bolognese', servings: 6);
      final service = FakeRecipeService(recipes: [recipe]);

      await _pump(tester, RecipeEditorPage(initial: recipe), service: service);

      expect(find.text('Rezept bearbeiten'), findsOneWidget);
      expect(find.text('Bolognese'), findsOneWidget);
      await _scrollTo(tester, find.widgetWithText(TextField, 'Mehl'));
      expect(find.widgetWithText(TextField, 'Mehl'), findsOneWidget);
    });
  });

  group('Import-Vorschau', () {
    RecipeImportPreview preview({
      List<RecipeIngredient>? ingredients,
      List<RecipeStep>? steps,
      List<String> warnings = const [],
    }) {
      return RecipeImportPreview(
        recipe: Recipe(
          name: 'Zitronen-Pasta',
          prepTimeMinutes: 20,
          cookTimeMinutes: 0,
          servings: 2,
          category: 'Sonstiges',
          sourceName: 'Instagram',
          ingredients: ingredients ??
              const [RecipeIngredient(amount: 250, unit: 'g', name: 'Spaghetti')],
          steps: steps ?? const [RecipeStep(text: 'Nudeln kochen.')],
        ),
        warnings: warnings,
      );
    }

    testWidgets('zeigt die Warnungen des Servers', (tester) async {
      await _pump(
        tester,
        RecipeImportPreviewPage(
          preview: preview(warnings: const [
            'Kategorie nicht erkennbar - bitte auswählen.',
            'Keine Zeitangabe gefunden - bitte selbst eintragen.',
          ]),
        ),
      );

      expect(find.text('BEIM LESEN AUFGEFALLEN'), findsOneWidget);
      expect(find.text('Kategorie nicht erkennbar - bitte auswählen.'),
          findsOneWidget);
      expect(find.text('Zitronen-Pasta'), findsOneWidget);
      expect(find.text('250 g'), findsOneWidget);
    });

    testWidgets('speichert das gelesene Rezept', (tester) async {
      final service = FakeRecipeService();
      await _pump(tester, RecipeImportPreviewPage(preview: preview()),
          service: service);

      await _scrollTo(tester, find.text('Speichern'));
      await tester.tap(find.text('Speichern'));
      await tester.pumpAndSettle();

      expect(service.recipes.single.name, 'Zitronen-Pasta');
      expect(service.recipes.single.sourceName, 'Instagram');
    });

    testWidgets('ohne Zutaten ist Speichern gesperrt und erklärt', (tester) async {
      // Ohne diese Sperre wäre die einzige Rückmeldung ein unerklärtes 400.
      final service = FakeRecipeService();
      await _pump(
        tester,
        RecipeImportPreviewPage(preview: preview(ingredients: const [])),
        service: service,
      );

      expect(
        find.textContaining('lässt sich das Rezept nicht speichern'),
        findsOneWidget,
      );
      await _scrollTo(tester, find.text('Speichern'));
      final button = tester.widget<FilledButton>(
          find.ancestor(of: find.text('Speichern'), matching: find.byType(FilledButton)));
      expect(button.onPressed, isNull);
    });
  });
}
