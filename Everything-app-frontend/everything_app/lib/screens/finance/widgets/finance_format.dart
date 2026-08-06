import 'package:intl/intl.dart';

/// Formatierungen, die im ganzen Space gleich aussehen müssen.
///
/// Beträge stehen hier an vielen Stellen nebeneinander - eine Karte mit
/// "1.234,5 €" neben einer mit "1234.50" fällt sofort auf.
abstract final class FinanceFormat {
  static final _currency =
      NumberFormat.currency(locale: 'de_DE', symbol: '€', decimalDigits: 2);
  static final _compact =
      NumberFormat.currency(locale: 'de_DE', symbol: '€', decimalDigits: 0);

  /// "1.234,56 €"
  static String money(double value) => _currency.format(value);

  /// Ohne Nachkommastellen - für Achsen und große Kacheln, wo die Cent nur
  /// Platz kosten.
  static String moneyShort(double value) => _compact.format(value);

  /// Mit Vorzeichen: "+2.480,00 €" bzw. "−13,99 €".
  ///
  /// Das Minus ist ein echtes Minuszeichen (U+2212), kein Bindestrich - auf
  /// derselben Höhe wie das Plus und in der Breite einer Ziffer.
  static String signed(double value, {required bool income}) {
    final formatted = _currency.format(value.abs());
    return income ? '+$formatted' : '−$formatted';
  }

  /// "August 2026"
  static String month(DateTime date) => DateFormat('MMMM yyyy', 'de_DE').format(date);

  /// "6. Aug."
  static String shortDate(DateTime date) => DateFormat('d. MMM', 'de_DE').format(date);

  /// Tagesüberschrift in der Buchungsliste.
  static String dayLabel(DateTime date) {
    final now = DateTime.now();
    final day = DateTime(date.year, date.month, date.day);
    final today = DateTime(now.year, now.month, now.day);
    final difference = today.difference(day).inDays;

    if (difference == 0) {
      return 'Heute · ${DateFormat('d. MMMM', 'de_DE').format(date)}';
    }
    if (difference == 1) {
      return 'Gestern · ${DateFormat('d. MMMM', 'de_DE').format(date)}';
    }
    return DateFormat('EEEE · d. MMMM', 'de_DE').format(date);
  }

  /// "zuletzt aktualisiert 14:32" bzw. "… gestern 14:32".
  ///
  /// Wichtiger als es aussieht: erreicht der Abruf das Tageslimit der Bank,
  /// zeigt die Oberfläche diesen Stand statt einer Fehlermeldung.
  static String lastUpdated(DateTime? at) {
    if (at == null) return 'noch nicht abgerufen';
    final now = DateTime.now();
    final time = DateFormat('HH:mm').format(at);
    final days = DateTime(now.year, now.month, now.day)
        .difference(DateTime(at.year, at.month, at.day))
        .inDays;

    if (days == 0) return 'aktualisiert $time';
    if (days == 1) return 'aktualisiert gestern $time';
    return 'aktualisiert ${DateFormat('d. MMM', 'de_DE').format(at)}';
  }
}
