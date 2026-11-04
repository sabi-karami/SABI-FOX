package com.sabifox.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sabifox.vpn.data.ThemeMode
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import org.jetbrains.compose.resources.Font

private val LightColors =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        inversePrimary = md_theme_light_inversePrimary,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        onError = md_theme_light_onError,
        errorContainer = md_theme_light_errorContainer,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        surfaceTint = md_theme_light_surfaceTint,
        surfaceBright = md_theme_light_surfaceBright,
        surfaceDim = md_theme_light_surfaceDim,
        surfaceContainer = md_theme_light_surfaceContainer,
        surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
        surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
        surfaceContainerLow = md_theme_light_surfaceContainerLow,
        surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
        outline = md_theme_light_outline,
        outlineVariant = md_theme_light_outlineVariant,
        inverseSurface = md_theme_light_inverseSurface,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        scrim = md_theme_light_scrim,
    )

private val DarkColors =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        inversePrimary = md_theme_dark_inversePrimary,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        onError = md_theme_dark_onError,
        errorContainer = md_theme_dark_errorContainer,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        surfaceTint = md_theme_dark_surfaceTint,
        surfaceBright = md_theme_dark_surfaceBright,
        surfaceDim = md_theme_dark_surfaceDim,
        surfaceContainer = md_theme_dark_surfaceContainer,
        surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
        surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
        surfaceContainerLow = md_theme_dark_surfaceContainerLow,
        surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
        outline = md_theme_dark_outline,
        outlineVariant = md_theme_dark_outlineVariant,
        inverseSurface = md_theme_dark_inverseSurface,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        scrim = md_theme_dark_scrim,
    )


@Composable
private fun vazirmatn(): FontFamily =
    FontFamily(
        Font(Res.font.vazirmatn_regular, FontWeight.Normal),
        Font(Res.font.vazirmatn_medium, FontWeight.Medium),
        Font(Res.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(Res.font.vazirmatn_bold, FontWeight.Bold),
        Font(Res.font.vazirmatn_extrabold, FontWeight.ExtraBold),
        Font(Res.font.vazirmatn_black, FontWeight.Black),
    )

private fun TextStyle.withFamily(family: FontFamily) = copy(fontFamily = family)

private fun Typography.withFamily(f: FontFamily) =
    Typography(
        displayLarge = displayLarge.withFamily(f),
        displayMedium = displayMedium.withFamily(f),
        displaySmall = displaySmall.withFamily(f),
        headlineLarge = headlineLarge.withFamily(f),
        headlineMedium = headlineMedium.withFamily(f),
        headlineSmall = headlineSmall.withFamily(f),
        titleLarge = titleLarge.withFamily(f),
        titleMedium = titleMedium.withFamily(f),
        titleSmall = titleSmall.withFamily(f),
        bodyLarge = bodyLarge.withFamily(f),
        bodyMedium = bodyMedium.withFamily(f),
        bodySmall = bodySmall.withFamily(f),
        labelLarge = labelLarge.withFamily(f),
        labelMedium = labelMedium.withFamily(f),
        labelSmall = labelSmall.withFamily(f),
    )

/**
 * Same palette and type scale as the Android app. Persian uses the bundled Vazirmatn face and
 * a right-to-left layout.
 */
@Composable
fun SabiFoxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    rtl: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val family = vazirmatn()
    val typography = remember(rtl, family) { if (rtl) SabiTypography.withFamily(family) else SabiTypography }
    CompositionLocalProvider(
        LocalSabiStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            content = content,
        )
    }
}
