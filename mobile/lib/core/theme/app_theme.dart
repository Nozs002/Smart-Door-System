import 'package:flutter/material.dart';

class AppTheme {
  static ThemeData theme(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xff0099cc),
        brightness: brightness,
        surface: dark ? const Color(0xff111827) : Colors.white,
      ),
      scaffoldBackgroundColor: dark
          ? const Color(0xff0a0f1e)
          : const Color(0xfff4f2ec),
      appBarTheme: const AppBarTheme(
        centerTitle: false,
        scrolledUnderElevation: 0,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: dark ? Colors.white : const Color(0xff1c1c1e),
          foregroundColor: dark ? const Color(0xff1c1c1e) : Colors.white,
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: dark ? const Color(0xff1a2234) : const Color(0xfff8fafc),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
      ),
    );
  }
}
