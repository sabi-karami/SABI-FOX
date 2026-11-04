package com.sabifox.vpn.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// SABI-FOX "Nocturne Aurora" palette
val SabiNight = Color(0xFF070B14)
val SabiMint = Color(0xFF3DF5C2)
val SabiViolet = Color(0xFFA78BFA)
val SabiIvory = Color(0xFFEAFBF5)

val md_theme_light_primary = Color(0xFF006B53)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF86F8D3)
val md_theme_light_onPrimaryContainer = Color(0xFF002118)
val md_theme_light_inversePrimary = Color(0xFF3DF5C2)

val md_theme_light_secondary = Color(0xFF3E5BA9)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDCE3FF)
val md_theme_light_onSecondaryContainer = Color(0xFF001849)

val md_theme_light_tertiary = Color(0xFF6B3FD1)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFEADDFF)
val md_theme_light_onTertiaryContainer = Color(0xFF22005D)

val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)

val md_theme_light_background = Color(0xFFF4F7FB)
val md_theme_light_onBackground = Color(0xFF0D1626)
val md_theme_light_surface = Color(0xFFF4F7FB)
val md_theme_light_onSurface = Color(0xFF0D1626)
val md_theme_light_surfaceVariant = Color(0xFFDDE3EE)
val md_theme_light_onSurfaceVariant = Color(0xFF424D63)
val md_theme_light_surfaceTint = md_theme_light_primary

val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFEEF2F8)
val md_theme_light_surfaceContainer = Color(0xFFE7ECF4)
val md_theme_light_surfaceContainerHigh = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerHighest = Color(0xFFD9E0EC)
val md_theme_light_surfaceBright = Color(0xFFF9FBFE)
val md_theme_light_surfaceDim = Color(0xFFD5DCE8)

val md_theme_light_outline = Color(0xFF6E7A90)
val md_theme_light_outlineVariant = Color(0xFFC3CCDB)
val md_theme_light_inverseSurface = Color(0xFF1A2233)
val md_theme_light_inverseOnSurface = Color(0xFFEAF0FA)
val md_theme_light_scrim = Color(0xFF000000)

val md_theme_dark_primary = SabiMint
val md_theme_dark_onPrimary = Color(0xFF00382A)
val md_theme_dark_primaryContainer = Color(0xFF0E5A47)
val md_theme_dark_onPrimaryContainer = Color(0xFFB6FFE8)
val md_theme_dark_inversePrimary = Color(0xFF006B53)

val md_theme_dark_secondary = Color(0xFF9DB4FF)
val md_theme_dark_onSecondary = Color(0xFF102B6B)
val md_theme_dark_secondaryContainer = Color(0xFF283F84)
val md_theme_dark_onSecondaryContainer = Color(0xFFDCE3FF)

val md_theme_dark_tertiary = SabiViolet
val md_theme_dark_onTertiary = Color(0xFF2E0B6E)
val md_theme_dark_tertiaryContainer = Color(0xFF4C2A9E)
val md_theme_dark_onTertiaryContainer = Color(0xFFEEDCFF)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = SabiNight
val md_theme_dark_onBackground = Color(0xFFE6EEFA)
val md_theme_dark_surface = SabiNight
val md_theme_dark_onSurface = Color(0xFFE6EEFA)
val md_theme_dark_surfaceVariant = Color(0xFF2A3550)
val md_theme_dark_onSurfaceVariant = Color(0xFFA3B1C8)
val md_theme_dark_surfaceTint = md_theme_dark_primary

val md_theme_dark_surfaceContainerLowest = Color(0xFF04070D)
val md_theme_dark_surfaceContainerLow = Color(0xFF0D1320)
val md_theme_dark_surfaceContainer = Color(0xFF111827)
val md_theme_dark_surfaceContainerHigh = Color(0xFF151D2E)
val md_theme_dark_surfaceContainerHighest = Color(0xFF1E293D)
val md_theme_dark_surfaceBright = Color(0xFF253049)
val md_theme_dark_surfaceDim = SabiNight

val md_theme_dark_outline = Color(0xFF5C6B85)
val md_theme_dark_outlineVariant = Color(0xFF2A3550)
val md_theme_dark_inverseSurface = Color(0xFFE6EEFA)
val md_theme_dark_inverseOnSurface = Color(0xFF1A2233)
val md_theme_dark_scrim = Color(0xFF000000)

data class SabiStatusColors(val connected: Color, val connecting: Color)

val LightStatusColors =
    SabiStatusColors(connected = Color(0xFF00875F), connecting = Color(0xFFA86A00))

val DarkStatusColors = SabiStatusColors(connected = SabiMint, connecting = Color(0xFFFFC857))

val LocalSabiStatusColors = staticCompositionLocalOf { LightStatusColors }
