package com.harsh.gymmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAkhadaColors = staticCompositionLocalOf { AkhadaLightColors }
private val LocalAkhadaTypography = staticCompositionLocalOf<AkhadaTypography> {
    error("AkhadaTheme is missing")
}

object AkhadaTheme {
    val colors: AkhadaColors
        @Composable @ReadOnlyComposable get() = LocalAkhadaColors.current
    val typography: AkhadaTypography
        @Composable @ReadOnlyComposable get() = LocalAkhadaTypography.current
}

/**
 * Akhada's own tokens, plus a Material scheme mapped from them so the few Material
 * components we use (date picker, text selection, ripples) don't show Material's defaults.
 */
@Composable
fun AkhadaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) AkhadaDarkColors else AkhadaLightColors
    val typography = akhadaTypography()
    val scheme = colors.toMaterialScheme()
    CompositionLocalProvider(LocalAkhadaColors provides colors, LocalAkhadaTypography provides typography) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography.toMaterialTypography(),
            shapes = Shapes(
                extraSmall = AkhadaRadius.fieldShape,
                small = AkhadaRadius.fieldShape,
                medium = AkhadaRadius.cardShape,
                large = AkhadaRadius.cardShape,
                extraLarge = AkhadaRadius.sheetShape,
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

private fun AkhadaColors.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = onAccentSoft,
        secondary = ink, onSecondary = onInk, secondaryContainer = surfaceSunken, onSecondaryContainer = ink,
        tertiary = statusActive, onTertiary = onInk,
        error = statusDue, onError = onInk, errorContainer = statusDueSoft, onErrorContainer = statusDue,
        background = bg, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted, surfaceTint = surface,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surfaceSunken,
        outline = inkMuted, outlineVariant = line, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = onInk, inversePrimary = accent,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = onAccentSoft,
        secondary = ink, onSecondary = onInk, secondaryContainer = surfaceSunken, onSecondaryContainer = ink,
        tertiary = statusActive, onTertiary = onInk,
        error = statusDue, onError = onInk, errorContainer = statusDueSoft, onErrorContainer = statusDue,
        background = bg, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted, surfaceTint = surface,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surfaceSunken,
        outline = inkMuted, outlineVariant = line, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = onInk, inversePrimary = accent,
    )
}

private fun AkhadaTypography.toMaterialTypography() = Typography(
    displayLarge = displayXl, displayMedium = displayXl, displaySmall = screenTitle,
    headlineLarge = screenTitle, headlineMedium = screenTitle, headlineSmall = stat,
    titleLarge = title, titleMedium = title, titleSmall = bodyStrong,
    bodyLarge = body, bodyMedium = body, bodySmall = caption,
    labelLarge = bodyStrong, labelMedium = caption, labelSmall = tab,
)
