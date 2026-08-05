import 'dart:ui';

/// Das Study-Lila aus der Kinetic-Mono-Palette; Rückfall, wenn ein Modul keine Farbe hat.
const Color kDefaultModuleColor = Color(0xFFC2C1FF);

/// Wandelt die Modulfarbe des Servers (`"#3B82F6"` oder `"3B82F6"`, auch achtstellig mit
/// Alpha) in eine [Color].
///
/// Liegt hier statt in einem der Modelle, weil inzwischen drei davon dieselbe Farbe aus
/// demselben Feld lesen — Stundenplan, Lernziel und Modulkachel dürfen bei einer kaputten
/// Angabe nicht unterschiedlich reagieren.
Color parseModuleColor(String? hex, {Color fallback = kDefaultModuleColor}) {
  final cleaned = hex?.replaceAll('#', '');
  if (cleaned != null && (cleaned.length == 6 || cleaned.length == 8)) {
    final value = int.tryParse(cleaned.length == 6 ? 'FF$cleaned' : cleaned, radix: 16);
    if (value != null) return Color(value);
  }
  return fallback;
}
