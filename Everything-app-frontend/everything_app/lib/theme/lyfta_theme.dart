import 'package:flutter/material.dart';

import 'kinetic_theme.dart';

/// Layout und Formensprache sind an Lyfta angelehnt (dunkler Gym-Tracker), die
/// Farben kommen aus der "Kinetic Mono"-Palette der restlichen App ([AppTheme]).
///
/// Die gemeinsame Basis liegt seit dem Umbau des Finance Space in
/// [KineticTheme]; hier stehen nur noch die Fachfarben des Gym Space und die
/// Durchreichungen. Die Durchreichungen bleiben bewusst bestehen, statt die rund
/// zwanzig Gym-Dateien auf [KineticTheme] umzuschreiben: es waere eine grosse
/// Aenderung ohne jeden sichtbaren Unterschied.
///
/// Die Anatomie folgt der Oberflaechenfarbe absichtlich *nicht*: die
/// Muskelfarben unten sind Fachfarben (rot = beansprucht, blau = unterstuetzend).
abstract final class LyftaTheme {
  static const background = KineticTheme.background;
  static const surface = KineticTheme.surface;
  static const surfaceElevated = KineticTheme.surfaceElevated;
  static const surfaceHighlight = KineticTheme.surfaceHighlight;

  static const primary = KineticTheme.primary;
  static const onPrimary = KineticTheme.onPrimary;

  static const textPrimary = KineticTheme.textPrimary;
  static const textSecondary = KineticTheme.textSecondary;
  static const textTertiary = KineticTheme.textTertiary;
  static const divider = KineticTheme.divider;
  static const danger = KineticTheme.danger;

  /// Persoenliche Bestleistungen. Gold statt Periwinkle, damit ein Rekord nicht
  /// wie ein weiteres Bedienelement aussieht - entsaettigt, damit es sich in die
  /// ansonsten farbarme Oberflaeche einfuegt.
  static const prAccent = Color(0xFFE5C07B);

  // ── Anatomie ───────────────────────────────────────────────────────────────
  // Heller Koerper auf dunklem Grund, Muskeln rot/blau - so wie in der Vorlage.

  /// Grundfarbe der Muskelflaechen: hell, damit die Figur auf #0E0E0E steht.
  static const bodyBase = Color(0xFFCFCFD6);

  /// Kopf, Haende, Fuesse, Knie - nie eingefaerbt, etwas dunkler fuer Tiefe.
  static const bodyNeutral = Color(0xFF9B9BA6);

  /// Trennlinien zwischen den Muskelflaechen.
  static const bodyOutline = Color(0xFF56565E);

  /// Beanspruchter Muskel.
  static const musclePrimary = Color(0xFFE5484D);

  /// Unterstuetzender Muskel.
  static const muscleSecondary = Color(0xFF4C8DF6);

  /// Wie [KineticTheme.darkTheme], nur mit dem Rekord-Gold als `secondary`.
  static ThemeData get darkTheme => KineticTheme.buildDarkTheme(secondary: prAccent);

  static TextStyle get headline => KineticTheme.headline;
  static TextStyle get title => KineticTheme.title;
  static TextStyle get subtitle => KineticTheme.subtitle;
  static TextStyle get caption => KineticTheme.caption;
  static TextStyle get label => KineticTheme.label;
}
