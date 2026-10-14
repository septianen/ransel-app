package app.ransel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * IRIS color tokens → Material 3 ColorScheme.
 * Source: docs/design/iris/tokens.md (IRIS design system, version 1791867814).
 * Screens never use hex directly: MaterialTheme.colorScheme.* or IrisTheme.accents.*.
 */

val IrisLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF008808),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2F9C4),
    onPrimaryContainer = Color(0xFF013A00),
    secondary = Color(0xFFD92D7B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE8ED),
    onSecondaryContainer = Color(0xFFCD1D6F),
    tertiary = Color(0xFF7C2D92),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE6FF),
    onTertiaryContainer = Color(0xFF7C2D92),
    error = Color(0xFFBA1A1D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE9E6),
    onErrorContainer = Color(0xFF660508),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF525252),
    surfaceDim = Color(0xFFDADADA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF3F3F3),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    surfaceTint = Color.Transparent, // IRIS uses shadows, not tonal elevation
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFE2E2E2),
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF1F1F1),
    inversePrimary = Color(0xFF6FDF5B),
    scrim = Color(0xFF000000),
)

val IrisDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5ED051),
    onPrimary = Color(0xFF012D00),
    primaryContainer = Color(0xFF014604),
    onPrimaryContainer = Color(0xFFBAF1AB),
    secondary = Color(0xFFFF9BBC),
    onSecondary = Color(0xFF4F0428),
    secondaryContainer = Color(0xFF6C1D3D),
    onSecondaryContainer = Color(0xFFFF9BBC),
    tertiary = Color(0xFFEE99FF),
    onTertiary = Color(0xFF420652),
    tertiaryContainer = Color(0xFF5A226B),
    onTertiaryContainer = Color(0xFFEE99FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF660508),
    errorContainer = Color(0xFF87201B),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131313),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFC1C1C1),
    surfaceDim = Color(0xFF131313),
    surfaceBright = Color(0xFF353535),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF191919),
    surfaceContainer = Color(0xFF1D1D1D),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF303030),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF848484),
    outlineVariant = Color(0xFF3B3B3B),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF262626),
    inversePrimary = Color(0xFF016100),
    scrim = Color(0xFF000000),
)

/**
 * Bold accents for category tiles, cards, charts and illustrations. Same in both themes.
 * Text/icons on top always use [onAccent] (white). Below 4.5:1 (teal, blue, yellow, orange)
 * only for icons and large bold text.
 */
@Immutable
data class IrisAccents(
    val green: Color = Color(0xFF008808),
    val magenta: Color = Color(0xFFD92D7B),
    val purple: Color = Color(0xFF7C2D92),
    val teal: Color = Color(0xFF00A896),
    val blue: Color = Color(0xFF00A5EC),
    val yellow: Color = Color(0xFFFFB800),
    val orange: Color = Color(0xFFFF5722),
    val red: Color = Color(0xFFD82D3B),
    val brown: Color = Color(0xFF844C1C),
    val onAccent: Color = Color(0xFFFFFFFF),
)

val LocalIrisAccents = staticCompositionLocalOf { IrisAccents() }
