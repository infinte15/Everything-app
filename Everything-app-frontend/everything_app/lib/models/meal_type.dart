/// Wann eine geplante Mahlzeit gegessen wird.
///
/// [wire] ist der Wert, der über die Leitung geht, und er ist ASCII - genau wie
/// das Enum im Backend. Der frühere Provider schickte `BREAKFAST`/`LUNCH`/
/// `DINNER`, das Backend kennt `FRUEHSTUECK`/`MITTAGESSEN`/`ABENDESSEN`: kein
/// einziger Wochenplan-Eintrag hat je die Mahlzeit getroffen, die er meinte.
enum MealType {
  fruehstueck('FRUEHSTUECK', 'Frühstück'),
  mittagessen('MITTAGESSEN', 'Mittagessen'),
  abendessen('ABENDESSEN', 'Abendessen'),
  snack('SNACK', 'Snack');

  const MealType(this.wire, this.label);

  final String wire;
  final String label;

  /// Die drei Plätze, die der Wochenplan je Tag zeigt. Ein Snack bekommt keinen
  /// festen Platz - er wird geplant, wenn es ihn gibt, und steht dann zusätzlich.
  static const slots = [fruehstueck, mittagessen, abendessen];

  /// `null` statt Ausnahme: ein unbekannter Wert darf nicht die ganze Liste
  /// umbringen, sondern nur seine eigene Zeile.
  static MealType? tryParse(String? value) {
    if (value == null) return null;
    for (final type in MealType.values) {
      if (type.wire == value) return type;
    }
    return null;
  }
}
