import 'package:intl/intl.dart';

/// Zahlen und Zeiten des Rezept-Space als Text.

/// Die Gegenrichtung zu `IngredientParser.VULGAR_FRACTIONS` im Backend.
///
/// Nicht `const`: Dart lässt `double` nicht als Schlüssel einer konstanten
/// Karte zu, weil der Typ `==` überschreibt.
final _fractions = <double, String>{
  0.125: '⅛',
  0.2: '⅕',
  0.25: '¼',
  0.333: '⅓',
  0.375: '⅜',
  0.5: '½',
  0.625: '⅝',
  0.667: '⅔',
  0.75: '¾',
  0.8: '⅘',
  0.875: '⅞',
};

final _decimal = NumberFormat('0.##', 'de_DE');

/// Ab hier wird nicht mehr gebrochen, sondern gerechnet.
///
/// Kleine Mengen sind Stücke, Löffel und Zehen - dort ist der Bruch die
/// natürliche Schreibweise. Größere Mengen sind Gramm und Milliliter, und
/// "187 ½ g" schreibt keine Küchenwaage. Die Grenze ist eine Setzung, aber eine
/// mit einem Grund: unterhalb von zehn zählt man, oberhalb wiegt man.
const _fractionLimit = 10;

/// Eine Menge als Text: `400` → "400", `0.5` → "½", `1.5` → "1 ½",
/// `12.75` → "12,75".
///
/// Die Brüche sind der Grund, aus dem es diese Funktion gibt. Wer ein
/// Vier-Portionen-Rezept halbiert, bekommt sonst "0.5 EL Senf" auf den Tisch -
/// und ein halber Esslöffel heißt in jeder Küche der Welt "½ EL".
String formatAmount(double? amount) {
  if (amount == null) return '';
  if (amount <= 0) return '';

  final whole = amount.floor();
  final rest = amount - whole;

  if (rest < 0.01) return '$whole';

  if (amount < _fractionLimit) {
    for (final entry in _fractions.entries) {
      if ((rest - entry.key).abs() < 0.01) {
        return whole == 0 ? entry.value : '$whole ${entry.value}';
      }
    }
  }
  return _decimal.format(amount);
}

/// Menge und Einheit zusammen. Ohne Menge bleibt die Einheit weg - "EL" allein
/// sagt nichts.
String formatAmountWithUnit(double? amount, String? unit) {
  final value = formatAmount(amount);
  if (value.isEmpty) return '';
  if (unit == null || unit.isEmpty) return value;
  return '$value $unit';
}

/// "25 Min", "1 Std", "1 Std 15 Min".
String formatDuration(int minutes) {
  if (minutes <= 0) return '–';
  if (minutes < 60) return '$minutes Min';
  final hours = minutes ~/ 60;
  final rest = minutes % 60;
  return rest == 0 ? '$hours Std' : '$hours Std $rest Min';
}

String formatServings(int portions) =>
    portions == 1 ? '1 Portion' : '$portions Portionen';

/// "3× gekocht" - oder nichts, solange es noch nie gekocht wurde.
String formatCookCount(int count) => count <= 0 ? '' : '$count× gekocht';
