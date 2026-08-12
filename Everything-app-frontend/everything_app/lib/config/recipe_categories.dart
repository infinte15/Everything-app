import 'package:flutter/material.dart';

/// Das Kategorien-Vokabular des Rezept-Space.
///
/// **Muss deckungsgleich mit dem ausgelieferten Katalog des Backends sein**
/// (`resources/data/recipe-categories.json`); ein Test in
/// `test/config/recipe_categories_test.dart` hält beide Listen gegeneinander.
/// Weichen sie ab, bekommt dieselbe Sache zwei Namen: die eine Hälfte der
/// Rezepte landet unter "Auflauf & Ofen", die andere unter "Auflauf", der
/// Kategorienfilter zeigt beide, und niemand sieht, warum die Zahlen nicht
/// aufgehen.
///
/// Die Reihenfolge ist die Anzeigereihenfolge. Eine **Farbe** bekommt eine
/// Kategorie hier nicht: der Space ist einfarbig, auf dem Bildschirm ist das
/// Rezeptfoto das einzig Bunte, und zwölf Tönungen daneben wären Dekoration.
/// Unterschieden werden die Kategorien über Symbol und Beschriftung.
const recipeCategories = <String>[
  'Hauptgericht',
  'Pasta & Reis',
  'Auflauf & Ofen',
  'Suppe & Eintopf',
  'Salat & Bowl',
  'Beilage & Sauce',
  'Vorspeise & Snack',
  'Frühstück',
  'Backen',
  'Dessert',
  'Getränk',
  'Sonstiges',
];

const _categoryIcons = <String, IconData>{
  'Hauptgericht': Icons.dinner_dining_outlined,
  'Pasta & Reis': Icons.ramen_dining_outlined,
  'Auflauf & Ofen': Icons.bakery_dining_outlined,
  'Suppe & Eintopf': Icons.soup_kitchen_outlined,
  'Salat & Bowl': Icons.rice_bowl_outlined,
  'Beilage & Sauce': Icons.kebab_dining_outlined,
  'Vorspeise & Snack': Icons.tapas_outlined,
  'Frühstück': Icons.egg_alt_outlined,
  'Backen': Icons.cake_outlined,
  'Dessert': Icons.icecream_outlined,
  'Getränk': Icons.local_cafe_outlined,
  'Sonstiges': Icons.restaurant_outlined,
};

/// Symbol einer Kategorie. Unbekannte bekommen das allgemeine Besteck.
IconData recipeCategoryIcon(String? category) =>
    _categoryIcons[category] ?? Icons.restaurant_outlined;

/// Die kanonischen Schwierigkeitsstufen - was der Editor zur Auswahl stellt.
const recipeDifficulties = <String>['Einfach', 'Mittel', 'Aufwendig'];

/// Anzeigeform einer gespeicherten Schwierigkeit.
///
/// Im Bestand stehen mehrere Vokabulare nebeneinander: der Demo-Seeder schrieb
/// "einfach"/"mittel" klein, ein früherer Import "Normal", chefkoch sagt
/// "simpel" und "pfiffig", englische Seiten "easy". Das Backend bildet das
/// inzwischen beim Import auf
/// Einfach/Mittel/Aufwendig - aber Zeilen von gestern verschwinden nicht,
/// weil man sich geeinigt hat. Also bildet die Oberfläche sie ab, statt sie
/// stehenzulassen und den Filter in vier Hälften zu teilen.
String? displayDifficulty(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  return switch (raw.trim().toLowerCase()) {
    'einfach' || 'simpel' || 'leicht' || 'easy' => 'Einfach',
    'mittel' || 'normal' || 'medium' => 'Mittel',
    'aufwendig' || 'pfiffig' || 'schwer' || 'schwierig' || 'hard' => 'Aufwendig',
    _ => raw.trim(),
  };
}
