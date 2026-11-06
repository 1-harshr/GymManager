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

private val LocalMyGymColors = staticCompositionLocalOf { MyGymLightColors }
private val LocalMyGymTypography = staticCompositionLocalOf<MyGymTypography> {
    error("MyGymTheme is missing")
}

object MyGymTheme {
    val colors: MyGymColors
        @Composable @ReadOnlyComposable get() = LocalMyGymColors.current
    val typography: MyGymTypography
        @Composable @ReadOnlyComposable get() = LocalMyGymTypography.current
}

/**
 * My Gym's own tokens, plus a Material scheme mapped from them so the few Material
 * components we use (date picker, text selection, ripples) don't show Material's defaults.
 */
@Composable
fun MyGymTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) MyGymDarkColors else MyGymLightColors
    val typography = myGymTypography()
    val scheme = colors.toMaterialScheme()
    CompositionLocalProvider(LocalMyGymColors provides colors, LocalMyGymTypography provides typography) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography.toMaterialTypography(),
            shapes = Shapes(
                extraSmall = MyGymRadius.fieldShape,
                small = MyGymRadius.fieldShape,
                medium = MyGymRadius.cardShape,
                large = MyGymRadius.cardShape,
                extraLarge = MyGymRadius.sheetShape,
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

private fun MyGymColors.toMaterialScheme() = if (isDark) {
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

private fun MyGymTypography.toMaterialTypography() = Typography(
    displayLarge = displayXl, displayMedium = displayXl, displaySmall = screenTitle,
    headlineLarge = screenTitle, headlineMedium = screenTitle, headlineSmall = stat,
    titleLarge = title, titleMedium = title, titleSmall = bodyStrong,
    bodyLarge = body, bodyMedium = body, bodySmall = caption,
    labelLarge = bodyStrong, labelMedium = caption, labelSmall = tab,
)
