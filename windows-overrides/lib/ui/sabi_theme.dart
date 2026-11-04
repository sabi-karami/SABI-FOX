import 'package:flutter/material.dart';

// SABI-FOX "Nocturne Aurora" palette and Material themes, ported from the
// Android app (ui/theme/Color.kt and Theme.kt) so the Windows preview looks
// like the phone app.

/// Brand colours shared by both themes.
abstract final class SabiPalette {
  static const Color night = Color(0xFF070B14);
  static const Color mint = Color(0xFF3DF5C2);
  static const Color violet = Color(0xFFA78BFA);
  static const Color ivory = Color(0xFFEAFBF5);
}

/// Bundled family with Persian, Arabic and Latin glyphs (SIL OFL 1.1, see
/// assets/fonts/OFL.txt), so one family serves both locales.
const String sabiFontFamily = 'Vazirmatn';

const ColorScheme sabiLightScheme = ColorScheme(
  brightness: Brightness.light,
  primary: Color(0xFF006B53),
  onPrimary: Color(0xFFFFFFFF),
  primaryContainer: Color(0xFF86F8D3),
  onPrimaryContainer: Color(0xFF002118),
  secondary: Color(0xFF3E5BA9),
  onSecondary: Color(0xFFFFFFFF),
  secondaryContainer: Color(0xFFDCE3FF),
  onSecondaryContainer: Color(0xFF001849),
  tertiary: Color(0xFF6B3FD1),
  onTertiary: Color(0xFFFFFFFF),
  tertiaryContainer: Color(0xFFEADDFF),
  onTertiaryContainer: Color(0xFF22005D),
  error: Color(0xFFBA1A1A),
  onError: Color(0xFFFFFFFF),
  errorContainer: Color(0xFFFFDAD6),
  onErrorContainer: Color(0xFF410002),
  surface: Color(0xFFF4F7FB),
  onSurface: Color(0xFF0D1626),
  onSurfaceVariant: Color(0xFF424D63),
  surfaceContainerLowest: Color(0xFFFFFFFF),
  surfaceContainerLow: Color(0xFFEEF2F8),
  surfaceContainer: Color(0xFFE7ECF4),
  surfaceContainerHigh: Color(0xFFFFFFFF),
  surfaceContainerHighest: Color(0xFFD9E0EC),
  surfaceBright: Color(0xFFF9FBFE),
  surfaceDim: Color(0xFFD5DCE8),
  outline: Color(0xFF6E7A90),
  outlineVariant: Color(0xFFC3CCDB),
  inverseSurface: Color(0xFF1A2233),
  onInverseSurface: Color(0xFFEAF0FA),
  inversePrimary: SabiPalette.mint,
  surfaceTint: Color(0xFF006B53),
  shadow: Color(0xFF000000),
  scrim: Color(0xFF000000),
);

const ColorScheme sabiDarkScheme = ColorScheme(
  brightness: Brightness.dark,
  primary: SabiPalette.mint,
  onPrimary: Color(0xFF00382A),
  primaryContainer: Color(0xFF0E5A47),
  onPrimaryContainer: Color(0xFFB6FFE8),
  secondary: Color(0xFF9DB4FF),
  onSecondary: Color(0xFF102B6B),
  secondaryContainer: Color(0xFF283F84),
  onSecondaryContainer: Color(0xFFDCE3FF),
  tertiary: SabiPalette.violet,
  onTertiary: Color(0xFF2E0B6E),
  tertiaryContainer: Color(0xFF4C2A9E),
  onTertiaryContainer: Color(0xFFEEDCFF),
  error: Color(0xFFFFB4AB),
  onError: Color(0xFF690005),
  errorContainer: Color(0xFF93000A),
  onErrorContainer: Color(0xFFFFDAD6),
  surface: SabiPalette.night,
  onSurface: Color(0xFFE6EEFA),
  onSurfaceVariant: Color(0xFFA3B1C8),
  surfaceContainerLowest: Color(0xFF04070D),
  surfaceContainerLow: Color(0xFF0D1320),
  surfaceContainer: Color(0xFF111827),
  surfaceContainerHigh: Color(0xFF151D2E),
  surfaceContainerHighest: Color(0xFF1E293D),
  surfaceBright: Color(0xFF253049),
  surfaceDim: SabiPalette.night,
  outline: Color(0xFF5C6B85),
  outlineVariant: Color(0xFF2A3550),
  inverseSurface: Color(0xFFE6EEFA),
  onInverseSurface: Color(0xFF1A2233),
  inversePrimary: Color(0xFF006B53),
  surfaceTint: SabiPalette.mint,
  shadow: Color(0xFF000000),
  scrim: Color(0xFF000000),
);

/// Connection-state colours, as `SabiStatusColors` on Android.
@immutable
class SabiStatusColors extends ThemeExtension<SabiStatusColors> {
  const SabiStatusColors({required this.connected, required this.connecting});

  final Color connected;
  final Color connecting;

  static const SabiStatusColors light = SabiStatusColors(
    connected: Color(0xFF00875F),
    connecting: Color(0xFFA86A00),
  );

  static const SabiStatusColors dark = SabiStatusColors(
    connected: SabiPalette.mint,
    connecting: Color(0xFFFFC857),
  );

  static SabiStatusColors of(BuildContext context) {
    final theme = Theme.of(context);
    return theme.extension<SabiStatusColors>() ??
        (theme.brightness == Brightness.dark ? dark : light);
  }

  @override
  SabiStatusColors copyWith({Color? connected, Color? connecting}) {
    return SabiStatusColors(
      connected: connected ?? this.connected,
      connecting: connecting ?? this.connecting,
    );
  }

  @override
  SabiStatusColors lerp(
    covariant ThemeExtension<SabiStatusColors>? other,
    double t,
  ) {
    if (other is! SabiStatusColors) return this;
    return SabiStatusColors(
      connected: Color.lerp(connected, other.connected, t) ?? connected,
      connecting: Color.lerp(connecting, other.connecting, t) ?? connecting,
    );
  }
}

// Letter-spacing tears Persian glyph joins apart, so every style that can
// carry Persian text keeps it at zero.
const TextTheme _sabiTextTheme = TextTheme(
  displaySmall: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0),
  headlineMedium: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0),
  headlineSmall: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0),
  titleLarge: TextStyle(fontWeight: FontWeight.w700, letterSpacing: 0),
  titleMedium: TextStyle(fontWeight: FontWeight.w600, letterSpacing: 0),
  titleSmall: TextStyle(fontWeight: FontWeight.w700, letterSpacing: 0),
  bodyLarge: TextStyle(letterSpacing: 0),
  bodyMedium: TextStyle(letterSpacing: 0),
  bodySmall: TextStyle(letterSpacing: 0, height: 1.45),
  labelLarge: TextStyle(fontWeight: FontWeight.w700, letterSpacing: 0),
  labelMedium: TextStyle(fontWeight: FontWeight.w600, letterSpacing: 0),
  labelSmall: TextStyle(fontWeight: FontWeight.w600, letterSpacing: 0),
);

/// Light or dark SABI-FOX theme.
ThemeData buildSabiTheme(Brightness brightness) {
  final dark = brightness == Brightness.dark;
  final scheme = dark ? sabiDarkScheme : sabiLightScheme;
  final hairline = dark
      ? scheme.outlineVariant.withValues(alpha: 0.85)
      : scheme.outlineVariant;
  final cardColor = dark
      ? scheme.surfaceContainer.withValues(alpha: 0.78)
      : scheme.surfaceContainerLowest.withValues(alpha: 0.88);

  OutlineInputBorder fieldBorder(Color color, [double width = 1]) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(14),
      borderSide: BorderSide(color: color, width: width),
    );
  }

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    fontFamily: sabiFontFamily,
    visualDensity: VisualDensity.standard,
    scaffoldBackgroundColor: scheme.surface,
    textTheme: _sabiTextTheme,
    extensions: <ThemeExtension<dynamic>>[
      dark ? SabiStatusColors.dark : SabiStatusColors.light,
    ],
    appBarTheme: AppBarThemeData(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      foregroundColor: scheme.onSurface,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: true,
      titleTextStyle: TextStyle(
        fontFamily: sabiFontFamily,
        fontSize: 18,
        fontWeight: FontWeight.w800,
        color: scheme.onSurface,
      ),
    ),
    cardTheme: CardThemeData(
      color: cardColor,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(22),
        side: BorderSide(color: hairline),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: scheme.surfaceContainerHigh,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(26),
        side: BorderSide(color: hairline),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 68,
      elevation: 0,
      backgroundColor: scheme.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      indicatorColor: scheme.primary.withValues(alpha: dark ? 0.16 : 0.14),
      indicatorShape: const StadiumBorder(),
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      labelTextStyle: WidgetStateProperty.resolveWith<TextStyle?>((states) {
        final selected = states.contains(WidgetState.selected);
        return TextStyle(
          fontFamily: sabiFontFamily,
          fontSize: 12,
          fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
          color: selected ? scheme.primary : scheme.onSurfaceVariant,
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith<IconThemeData?>((states) {
        final selected = states.contains(WidgetState.selected);
        return IconThemeData(
          size: 24,
          color: selected ? scheme.primary : scheme.onSurfaceVariant,
        );
      }),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(64, 50),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: const TextStyle(
          fontFamily: sabiFontFamily,
          fontSize: 15,
          fontWeight: FontWeight.w800,
        ),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(64, 46),
        foregroundColor: scheme.onSurface,
        side: BorderSide(color: scheme.outline.withValues(alpha: 0.7)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: const TextStyle(
          fontFamily: sabiFontFamily,
          fontSize: 14,
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(
          fontFamily: sabiFontFamily,
          fontSize: 14,
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    inputDecorationTheme: InputDecorationThemeData(
      filled: true,
      fillColor: dark
          ? scheme.surfaceContainerLowest.withValues(alpha: 0.7)
          : scheme.surfaceContainerLowest,
      border: fieldBorder(hairline),
      enabledBorder: fieldBorder(hairline),
      focusedBorder: fieldBorder(scheme.primary, 1.6),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      helperMaxLines: 3,
    ),
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: ButtonStyle(
        side: WidgetStatePropertyAll<BorderSide>(BorderSide(color: hairline)),
        backgroundColor: WidgetStateProperty.resolveWith<Color?>((states) {
          return states.contains(WidgetState.selected)
              ? scheme.primary.withValues(alpha: dark ? 0.18 : 0.14)
              : Colors.transparent;
        }),
        foregroundColor: WidgetStateProperty.resolveWith<Color?>((states) {
          if (states.contains(WidgetState.disabled)) {
            return scheme.onSurface.withValues(alpha: 0.38);
          }
          return states.contains(WidgetState.selected)
              ? scheme.primary
              : scheme.onSurfaceVariant;
        }),
        textStyle: const WidgetStatePropertyAll<TextStyle>(
          TextStyle(
            fontFamily: sabiFontFamily,
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    ),
    listTileTheme: ListTileThemeData(
      iconColor: scheme.onSurfaceVariant,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    expansionTileTheme: ExpansionTileThemeData(
      shape: const Border(),
      collapsedShape: const Border(),
      iconColor: scheme.primary,
      collapsedIconColor: scheme.onSurfaceVariant,
      textColor: scheme.onSurface,
      collapsedTextColor: scheme.onSurface,
      tilePadding: const EdgeInsetsDirectional.fromSTEB(14, 4, 10, 4),
      childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: Colors.transparent,
      side: BorderSide(color: hairline),
      shape: const StadiumBorder(),
      labelStyle: TextStyle(
        fontFamily: sabiFontFamily,
        fontSize: 12,
        fontWeight: FontWeight.w600,
        color: scheme.onSurface,
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: scheme.inverseSurface,
      contentTextStyle: TextStyle(
        fontFamily: sabiFontFamily,
        fontSize: 13,
        color: scheme.onInverseSurface,
      ),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    dividerTheme: DividerThemeData(color: hairline, thickness: 1, space: 1),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: scheme.surfaceContainerHighest,
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: scheme.inverseSurface,
        borderRadius: BorderRadius.circular(10),
      ),
      textStyle: TextStyle(
        fontFamily: sabiFontFamily,
        fontSize: 12,
        color: scheme.onInverseSurface,
      ),
      waitDuration: const Duration(milliseconds: 400),
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: scheme.surfaceContainerHigh,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: hairline),
      ),
    ),
  );
}
