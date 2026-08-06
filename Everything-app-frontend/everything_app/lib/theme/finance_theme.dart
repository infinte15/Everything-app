import 'package:flutter/material.dart';

import 'kinetic_theme.dart';

/// Die Fachfarben des Finance Space - duenn, weil fast alles aus
/// [KineticTheme] kommt.
///
/// Der Space ist bewusst fast farblos, wie der Gym Space. Gruen (#4ADE80) bleibt
/// die Space-Farbe im Raster und im Kalender, kommt hier drinnen aber nicht mehr
/// vor: eine gruen-rote Oberflaeche macht aus jeder Ausgabe eine schlechte
/// Nachricht, und die meisten Ausgaben sind schlicht normal.
///
/// Farbe traegt deshalb nur noch drei Bedeutungen:
/// [income] fuer Geld, das hereinkommt, [expense] (neutral) fuer alles Uebrige
/// und [shortfall], wenn die Prognose ins Minus laeuft.
abstract final class FinanceTheme {
  /// Einnahmen - der App-Akzent. Es ist die einzige eingefaerbte Zahl in einer
  /// Liste, und genau deshalb faellt sie auf.
  static const income = KineticTheme.primary;

  /// Ausgaben. Neutral: sie sind der Normalfall und muessen sich nicht
  /// rechtfertigen.
  static const expense = KineticTheme.textPrimary;

  /// Nur, wenn das Geld bis Monatsende nicht reicht.
  static const shortfall = KineticTheme.danger;

  /// Farben des Kategorien-Donuts.
  ///
  /// Entsaettigt gegenueber den frueheren Material-500-Toenen (#4CAF50, #2196F3,
  /// …), die auf #0E0E0E wie Leuchtreklame wirkten. Die Reihe ist so gewaehlt,
  /// dass benachbarte Segmente sich im Farbton deutlich unterscheiden - in einem
  /// Ring nebeneinander sind zwei aehnliche Blautoene nicht auseinanderzuhalten.
  static const categoryPalette = <Color>[
    Color(0xFFA8A7E8), // Periwinkle, gedeckt
    Color(0xFF8FB0C9), // Staubblau
    Color(0xFFC9A98F), // Sand
    Color(0xFF9FC1AE), // Salbei
    Color(0xFFC99FB4), // Altrosa
    Color(0xFFB6B98F), // Oliv
    Color(0xFF9F9FC9), // Lavendelgrau
    Color(0xFFC9B48F), // Ocker
    Color(0xFF8FC9C4), // Petrol, hell
  ];

  /// Feste Zuordnung fuer die Kategorien, die der Regel-Katalog ausliefert
  /// (siehe `config/finance_categories.dart`).
  ///
  /// Damit behaelt "Lebensmittel" ueber alle Monate hinweg dieselbe Farbe. Eine
  /// Zuordnung ueber die Reihenfolge im Diagramm waere von Monat zu Monat
  /// anders, und der Wiedererkennungswert - der einzige Zweck von Farbe im
  /// Donut - waere dahin.
  static const _fixed = <String, int>{
    'Lebensmittel': 0,
    'Restaurant': 1,
    'Wohnen': 2,
    'Transport': 3,
    'Gesundheit': 4,
    'Unterhaltung': 5,
    'Kleidung': 6,
    'Einnahmen': 7,
    'Sonstiges': 8,
  };

  /// Farbe einer Kategorie. Unbekannte Kategorien bekommen ueber den Hash des
  /// Namens einen stabilen Platz in der Reihe - stabil ist hier das Wichtige.
  static Color categoryColor(String? category) {
    if (category == null || category.isEmpty) {
      return KineticTheme.textTertiary;
    }
    final fixed = _fixed[category];
    if (fixed != null) {
      return categoryPalette[fixed];
    }
    return categoryPalette[category.hashCode.abs() % categoryPalette.length];
  }

  /// Farbe eines Betrags nach Richtung. Ausgaben bleiben neutral.
  static Color amountColor({required bool income}) =>
      income ? FinanceTheme.income : FinanceTheme.expense;
}
