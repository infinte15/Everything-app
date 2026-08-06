/// Das Kategorien-Vokabular des Finance Space.
///
/// **Muss deckungsgleich mit dem ausgelieferten Regel-Katalog des Backends
/// sein** (`resources/data/category-rules.json`). Bietet die Oberfläche eine
/// Kategorie an, die keine Regel je vergibt, entstehen zwei Namen für dieselbe
/// Sache: die automatisch einsortierten Buchungen landen unter "Transport", die
/// von Hand korrigierten unter "Mobilität". Der Kategorien-Ring zeigt beide
/// nebeneinander, das Budget rechnet gegen eines von beiden, und niemand sieht,
/// warum die Zahlen nicht aufgehen.
///
/// Kommt im Backend eine Kategorie dazu, gehört sie hier ergänzt - und in
/// [FinanceTheme] eine Farbe dafür.
const financeCategories = <String>[
  'Lebensmittel',
  'Restaurant',
  'Wohnen',
  'Transport',
  'Gesundheit',
  'Unterhaltung',
  'Kleidung',
  'Einnahmen',
  'Sonstiges',
];

/// Auswahl für Ausgaben - "Einnahmen" gehört nicht dazu.
final financeExpenseCategories =
    financeCategories.where((c) => c != 'Einnahmen').toList(growable: false);
