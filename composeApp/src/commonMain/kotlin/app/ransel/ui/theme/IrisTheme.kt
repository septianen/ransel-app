package app.ransel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Account → Tema: Ikuti sistem / Terang / Gelap. Stored in local preferences. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** IRIS radius: rounder than M3 default. Buttons, chips, switches, avatars = full (CircleShape). */
object IrisShape {
    val xs = RoundedCornerShape(6.dp)   // checkbox, square badge, small tag
    val sm = RoundedCornerShape(8.dp)   // small thumbnail, snackbar
    val md = RoundedCornerShape(12.dp)  // text field, category tile, menu
    val lg = RoundedCornerShape(16.dp)  // card, FAB, accent card
    val xl = RoundedCornerShape(24.dp)  // dialog, bottom sheet top, hero card
}

private val IrisShapes = Shapes(
    extraSmall = IrisShape.xs,
    small = IrisShape.sm,
    medium = IrisShape.md,
    large = IrisShape.lg,
    extraLarge = IrisShape.xl,
)

/** 4 dp grid. screen = side margin on phones (16), tablet/web use 24. */
@Immutable
data class IrisSpacing(
    val s4: Dp = 4.dp,
    val s8: Dp = 8.dp,
    val s12: Dp = 12.dp,
    val s16: Dp = 16.dp,
    val s20: Dp = 20.dp,
    val s24: Dp = 24.dp,
    val s32: Dp = 32.dp,
    val touchTarget: Dp = 48.dp,
)

val LocalIrisSpacing = staticCompositionLocalOf { IrisSpacing() }

/*
 * Elevation: IRIS uses soft shadows, not tonal surfaces (surfaceTint is transparent).
 * Cards: see [irisCard]. elevation-3 FAB/dialog/sheet ≈ 8.dp; elevation-5 dragged/swiped card ≈ 16.dp.
 * The CSS shadows in docs/design/iris/tokens.md read much stronger in a browser than the same numbers
 * do in Compose, so the values below are tuned on a device, not copied.
 */

private val CardShadowElevation = 6.dp
private const val CardShadowAmbientAlpha = 0.10f
private const val CardShadowSpotAlpha = 0.18f

/**
 * Standard raised card: soft shadow, card colour and a hairline edge.
 * Light theme: white card with a visible shadow on the white page. Dark theme: a shadow cannot show on
 * a dark page, so the card takes the IRIS dark card colour (`surfaceContainer`) and the edge carries it.
 * Apply before `clip`/`clickable`/padding so the shadow is not clipped.
 */
@Composable
fun Modifier.irisCard(
    shape: Shape,
    /** Overrides the theme's card colour, e.g. the green "in progress" card. */
    containerColor: Color? = null,
    /** Hairline edge; turn off for bold coloured cards that define their own edge. */
    edge: Boolean = true,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    return this
        .shadow(
            elevation = CardShadowElevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = CardShadowAmbientAlpha),
            spotColor = Color.Black.copy(alpha = CardShadowSpotAlpha),
        )
        .background(containerColor ?: if (dark) colors.surfaceContainer else colors.surface, shape)
        .then(if (edge) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
}

@Composable
fun IrisTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalIrisAccents provides IrisAccents(),
        LocalIrisSpacing provides IrisSpacing(),
    ) {
        MaterialTheme(
            colorScheme = if (dark) IrisDarkColors else IrisLightColors,
            typography = irisTypography(),
            shapes = IrisShapes,
            content = content,
        )
    }
}

/** Shortcuts: IrisTheme.accents.teal, IrisTheme.spacing.s16. */
object IrisTheme {
    val accents: IrisAccents
        @Composable @ReadOnlyComposable get() = LocalIrisAccents.current
    val spacing: IrisSpacing
        @Composable @ReadOnlyComposable get() = LocalIrisSpacing.current
}
