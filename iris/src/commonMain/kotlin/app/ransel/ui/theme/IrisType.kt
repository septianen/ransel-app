package app.ransel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import app.ransel.iris.generated.resources.Res
import app.ransel.iris.generated.resources.plus_jakarta_sans_bold
import app.ransel.iris.generated.resources.plus_jakarta_sans_extrabold
import app.ransel.iris.generated.resources.plus_jakarta_sans_regular
import app.ransel.iris.generated.resources.plus_jakarta_sans_semibold

/*
 * IRIS type scale (docs/design/iris/tokens.md). Font: Plus Jakarta Sans (OFL), weights
 * 400/600/700/800 as TTF in iris/src/commonMain/composeResources/font/ with the file
 * names below (module `iris`, shared by composeApp and adminApp).
 *
 * Screen titles in the designs use 22/28 weight 800 (headlineMedium with ExtraBold).
 * No text smaller than 12 sp.
 */

@Composable
fun plusJakartaSans(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)

@Composable
fun irisTypography(): Typography {
    val f = plusJakartaSans()
    return Typography(
        displayLarge = style(f, 44, 56, FontWeight.ExtraBold),
        displayMedium = style(f, 34, 44, FontWeight.ExtraBold),
        displaySmall = style(f, 28, 36, FontWeight.ExtraBold),
        headlineLarge = style(f, 26, 32, FontWeight.Bold),
        headlineMedium = style(f, 22, 28, FontWeight.Bold),
        headlineSmall = style(f, 20, 28, FontWeight.Bold),
        titleLarge = style(f, 18, 24, FontWeight.Bold),
        titleMedium = style(f, 16, 24, FontWeight.Bold),
        titleSmall = style(f, 14, 20, FontWeight.Bold),
        bodyLarge = style(f, 16, 24, FontWeight.Normal),
        bodyMedium = style(f, 14, 20, FontWeight.Normal),
        bodySmall = style(f, 12, 16, FontWeight.Normal),
        labelLarge = style(f, 14, 20, FontWeight.Bold),
        labelMedium = style(f, 12, 16, FontWeight.Bold),
        labelSmall = style(f, 12, 16, FontWeight.SemiBold),
    )
}
