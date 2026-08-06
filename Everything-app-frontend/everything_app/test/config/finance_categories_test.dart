import 'dart:convert';
import 'dart:io';

import 'package:everything_app/config/finance_categories.dart';
import 'package:everything_app/theme/finance_theme.dart';
import 'package:flutter_test/flutter_test.dart';

/// Hält das Kategorien-Vokabular der App an dem des Backends fest.
///
/// Die beiden Listen stehen zwangsläufig in getrennten Projekten, und sie
/// driften auseinander, ohne dass irgendetwas kaputtgeht: die Oberfläche bietet
/// "Mobilität" an, der Regel-Katalog vergibt "Transport", und plötzlich stehen
/// zwei Namen für dieselbe Sache im Kategorien-Ring. Das Budget rechnet gegen
/// einen davon, und niemand sieht, warum die Zahlen nicht aufgehen.
void main() {
  final catalog = File(
    '../../Everything-app-backend/everything-app/src/main/resources/data/category-rules.json',
  );

  test('deckt sich mit dem ausgelieferten Regel-Katalog des Backends', () {
    if (!catalog.existsSync()) {
      // Nur die Flutter-App ausgecheckt - dann lässt sich hier nichts prüfen.
      markTestSkipped('Regel-Katalog nicht gefunden: ${catalog.path}');
      return;
    }

    final groups = json.decode(catalog.readAsStringSync()) as List;
    final fromBackend = groups
        .map((group) => (group as Map<String, dynamic>)['category'] as String)
        .toSet();

    expect(
      financeCategories.toSet(),
      fromBackend,
      reason: 'Die Auswahl in den Sheets muss genau die Kategorien anbieten, '
          'die der Regel-Katalog auch vergibt.',
    );
  });

  test('jede Kategorie hat eine eigene, feste Farbe', () {
    // Ohne feste Zuordnung wechselt "Lebensmittel" von Monat zu Monat die Farbe,
    // und der Wiedererkennungswert im Ring - der einzige Zweck der Farbe - ist
    // dahin.
    final colors = financeCategories.map(FinanceTheme.categoryColor).toList();

    expect(colors.toSet().length, financeCategories.length,
        reason: 'zwei Kategorien teilen sich eine Farbe');
  });
}
