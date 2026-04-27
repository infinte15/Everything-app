import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  // Kinetic Mono Core Palette
  static const Color primaryColor = Color(0xFFC2C1FF);
  static const Color primaryContainer = Color(0xFF3631B4);
  static const Color secondaryColor = Color(0xFF9F9DA1);
  static const Color errorColor = Color(0xFFEC7C8A);
  
  static const Color surfaceColor = Color(0xFF0E0E0E);
  static const Color surfaceContainerLow = Color(0xFF131313);
  static const Color surfaceContainerHigh = Color(0xFF1F2020);
  static const Color surfaceContainerHighest = Color(0xFF252626);
  
  static const Color onSurfaceColor = Color(0xFFE7E5E5);
  static const Color onSurfaceVariant = Color(0xFFACABAA);
  static const Color outlineVariant = Color(0xFF484848);

  // Space Colors
  static const Color studyColor = Color(0xFFC2C1FF); // Primary
  static const Color sportsColor = Color(0xFFE5B580); // Orange/Amber
  static const Color tasksColor = Color(0xFFD1D9F8);  // Tertiary
  static const Color recipesColor = Color(0xFFC3CBE9); 
  static const Color financeColor = Color(0xFF4ADE80); // Green
  
  // Light Theme (Fallback, Kinetic Mono is inherently dark but we provide a base)
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF5856D6),
        brightness: Brightness.light,
        secondary: secondaryColor,
        error: errorColor,
      ),
      textTheme: GoogleFonts.manropeTextTheme(
        ThemeData.light().textTheme,
      ),
      appBarTheme: const AppBarTheme(
        centerTitle: false,
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        shape: Border(bottom: BorderSide(color: Color(0xFFE8EAF0))),
      ),
      cardTheme: const CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        clipBehavior: Clip.antiAlias,
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        elevation: 4,
      ),
      inputDecorationTheme: const InputDecorationTheme(
        filled: true,
        fillColor: Color(0xFFF7F8FC),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(color: Color(0xFFE8EAF0)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(color: Color(0xFFE8EAF0)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(color: Color(0xFF5856D6), width: 2),
        ),
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        type: BottomNavigationBarType.fixed,
        selectedItemColor: Color(0xFF5856D6),
        unselectedItemColor: Colors.grey,
        showSelectedLabels: true,
        showUnselectedLabels: true,
        elevation: 8,
      ),
    );
  }
  
  // Dark Theme (Kinetic Mono)
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: surfaceColor,
      colorScheme: const ColorScheme.dark(
        primary: primaryColor,
        primaryContainer: primaryContainer,
        secondary: secondaryColor,
        error: errorColor,
        surface: surfaceColor,
        onSurface: onSurfaceColor,
        onSurfaceVariant: onSurfaceVariant,
        outlineVariant: outlineVariant,
      ),
      textTheme: GoogleFonts.manropeTextTheme(
        ThemeData.dark().textTheme,
      ).apply(
        bodyColor: onSurfaceColor,
        displayColor: onSurfaceColor,
      ),
      appBarTheme: const AppBarTheme(
        centerTitle: false,
        elevation: 0,
        backgroundColor: surfaceColor,
        foregroundColor: Colors.white,
        shape: Border(bottom: BorderSide(color: outlineVariant)),
      ),
      cardTheme: const CardThemeData(
        elevation: 0,
        color: surfaceContainerLow,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        clipBehavior: Clip.antiAlias,
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: primaryColor,
        foregroundColor: Color(0xFF2D27AD), // on-primary
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        elevation: 10,
      ),
      inputDecorationTheme: const InputDecorationTheme(
        filled: true,
        fillColor: surfaceContainerLow,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(color: outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(color: outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(color: primaryColor, width: 2),
        ),
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        type: BottomNavigationBarType.fixed,
        selectedItemColor: Colors.white,
        unselectedItemColor: onSurfaceVariant,
        showSelectedLabels: true,
        showUnselectedLabels: true,
        elevation: 20,
        backgroundColor: Color(0xFF131313), // surface-container-low with blur usually
      ),
    );
  }
  
  static Color getSpaceColor(String spaceType) {
    switch (spaceType.toUpperCase()) {
      case 'STUDY':
        return studyColor;
      case 'SPORTS':
        return sportsColor;
      case 'TASKS':
        return tasksColor;
      case 'RECIPES':
        return recipesColor;
      case 'FINANCE':
        return financeColor;
      default:
        return outlineVariant;
    }
  }
  
  static Color getPriorityColor(int priority) {
    if (priority >= 5) return errorColor;
    if (priority >= 4) return sportsColor;
    if (priority >= 3) return financeColor;
    if (priority >= 2) return studyColor;
    return outlineVariant;
  }
}