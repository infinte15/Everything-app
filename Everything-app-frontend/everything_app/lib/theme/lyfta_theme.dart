import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Layout und Formensprache sind an Lyfta angelehnt (dunkler Gym-Tracker), die
/// Farben kommen aus der "Kinetic Mono"-Palette der restlichen App ([AppTheme]).
///
/// Die Oberflaeche bleibt bewusst weitgehend farblos - Periwinkle setzt nur
/// Akzente. Davon ausgenommen ist die Anatomie: die Muskelfarben unten sind
/// Fachfarben (rot = beansprucht, blau = unterstuetzend) und folgen der
/// Oberflaechenfarbe absichtlich *nicht*.
abstract final class LyftaTheme {
  static const background = Color(0xFF0E0E0E); // AppTheme.surfaceColor
  static const surface = Color(0xFF131313); // surfaceContainerLow
  static const surfaceElevated = Color(0xFF1F2020); // surfaceContainerHigh
  static const surfaceHighlight = Color(0xFF252626); // surfaceContainerHighest

  /// [AppTheme.primaryColor] - der App-Akzent. Sparsam einsetzen.
  static const primary = Color(0xFFC2C1FF);

  /// Dunkles Indigo auf hellem Periwinkle, wie der FAB im App-Theme.
  static const onPrimary = Color(0xFF2D27AD);

  static const textPrimary = Color(0xFFE7E5E5); // onSurfaceColor
  static const textSecondary = Color(0xFFACABAA); // onSurfaceVariant

  /// Bewusst nicht `outlineVariant` (#484848): das waere auf #0E0E0E nur 2,1:1
  /// und damit fuer den 11px-[label]-Stil unlesbar. #7A7877 liegt bei 4,5:1.
  static const textTertiary = Color(0xFF7A7877);
  static const divider = Color(0xFF484848); // outlineVariant
  static const danger = Color(0xFFEC7C8A); // AppTheme.errorColor

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

  static ThemeData get darkTheme {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: background,
      colorScheme: const ColorScheme.dark(
        surface: surface,
        primary: primary,
        onPrimary: onPrimary,
        secondary: prAccent,
        error: danger,
        onSurface: textPrimary,
        onSurfaceVariant: textSecondary,
        outlineVariant: divider,
      ),
      dividerColor: divider,
      appBarTheme: AppBarTheme(
        backgroundColor: background,
        foregroundColor: textPrimary,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: GoogleFonts.inter(
          fontSize: 17,
          fontWeight: FontWeight.w600,
          color: textPrimary,
        ),
      ),
      cardTheme: const CardThemeData(
        color: surface,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(12))),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: onPrimary,
          minimumSize: const Size.fromHeight(50),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 16),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceElevated,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide.none,
        ),
        hintStyle: GoogleFonts.inter(color: textTertiary, fontSize: 15),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceElevated,
        selectedColor: primary.withValues(alpha: 0.2),
        labelStyle: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w500),
        side: const BorderSide(color: divider),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
    );
    return base.copyWith(
      textTheme: GoogleFonts.interTextTheme(base.textTheme).apply(
        bodyColor: textPrimary,
        displayColor: textPrimary,
      ),
    );
  }

  static TextStyle get headline => GoogleFonts.inter(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        color: textPrimary,
        letterSpacing: -0.5,
      );

  static TextStyle get title => GoogleFonts.inter(
        fontSize: 17,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      );

  static TextStyle get subtitle => GoogleFonts.inter(
        fontSize: 15,
        fontWeight: FontWeight.w400,
        color: textSecondary,
      );

  static TextStyle get caption => GoogleFonts.inter(
        fontSize: 13,
        fontWeight: FontWeight.w500,
        color: textSecondary,
      );

  static TextStyle get label => GoogleFonts.inter(
        fontSize: 11,
        fontWeight: FontWeight.w600,
        color: textTertiary,
        letterSpacing: 0.5,
      );
}
