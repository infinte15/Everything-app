import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Die "Kinetic Mono"-Formensprache der Spaces: fast farblos, Periwinkle setzt
/// nur Akzente, Inter als einzige Schrift, Karten mit 12er-Radius und ohne
/// Schatten.
///
/// Herausgeloest aus `lyfta_theme.dart`, als der Finance Space dieselbe Sprache
/// bekommen sollte. [LyftaTheme] reicht die Werte von hier durch und behaelt nur
/// noch seine Anatomie-Fachfarben - so bleiben die Gym-Dateien unveraendert und
/// es gibt trotzdem nur eine Stelle, an der Grau und Periwinkle definiert sind.
///
/// Farbe traegt in dieser Oberflaeche Bedeutung und ist keine Dekoration. Wer
/// hier eine weitere Farbe hinzufuegt, sollte sagen koennen, wofuer sie steht.
abstract final class KineticTheme {
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

  /// Die uebliche Kartenrundung. Als Konstante, weil sie an ueber hundert
  /// Stellen wiederholt wird und ein abweichender Wert sofort auffaellt.
  static const radius = 12.0;

  static ThemeData get darkTheme => buildDarkTheme();

  /// [secondary] ist der einzige Freiheitsgrad: der Gym Space setzt dort sein
  /// Rekord-Gold ein. Alles andere ist fuer alle Spaces gleich.
  static ThemeData buildDarkTheme({Color secondary = primary}) {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: background,
      colorScheme: ColorScheme.dark(
        surface: surface,
        primary: primary,
        onPrimary: onPrimary,
        secondary: secondary,
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
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(radius))),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: onPrimary,
          minimumSize: const Size.fromHeight(50),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius)),
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

  /// Grosse Zahlen (Kontostand, verfuegbarer Betrag). Tabellarische Ziffern,
  /// damit ein sich aendernder Betrag nicht in der Breite springt.
  static TextStyle get figure => GoogleFonts.inter(
        fontSize: 34,
        fontWeight: FontWeight.w700,
        color: textPrimary,
        letterSpacing: -1,
        fontFeatures: const [FontFeature.tabularFigures()],
      );

  /// Betraege in Listen - dieselbe Ziffernbreite wie [figure], nur kleiner.
  static TextStyle get amount => GoogleFonts.inter(
        fontSize: 15,
        fontWeight: FontWeight.w600,
        color: textPrimary,
        fontFeatures: const [FontFeature.tabularFigures()],
      );
}
