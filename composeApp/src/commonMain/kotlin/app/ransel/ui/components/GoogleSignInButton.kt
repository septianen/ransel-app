package app.ransel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.google_connecting
import ransel.composeapp.generated.resources.google_continue

/*
 * Google's brand colours and G (identity.google.com branding guidelines): the only hex values allowed outside
 * IrisTheme, kept here on purpose. Never recolour or redraw the G.
 */
private val LightFill = Color(0xFFFFFFFF)
private val LightBorder = Color(0xFF747775)
private val LightText = Color(0xFF1F1F1F)
private val DarkFill = Color(0xFF131314)
private val DarkBorder = Color(0xFF8E918F)
private val DarkText = Color(0xFFE3E3E3)

private val GoogleRed = Color(0xFFEA4335)
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

private const val RED = "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
private const val BLUE = "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
private const val YELLOW = "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
private const val GREEN = "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"

/**
 * "Lanjutkan dengan Google" (design `TombolGoogle`): Google's pill, 48 dp (web 52 dp through [height]); light and dark
 * follow the app theme. [loading] = "Menghubungkan…" with a spinner on the right (the G stays); the button is off
 * while loading or when [enabled] is false (the rest of the form is off at the same time).
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = if (dark) DarkFill else LightFill
    val text = if (dark) DarkText else LightText
    val border = if (dark) DarkBorder else LightBorder
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = height),
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            contentColor = text,
            disabledContainerColor = fill,
            // while loading the button keeps its look; only a really disabled one dims
            disabledContentColor = if (loading) text else text.copy(alpha = 0.38f),
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GoogleG(Modifier.size(20.dp))
            Text(
                stringResource(if (loading) Res.string.google_connecting else Res.string.google_continue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = text, trackColor = text.copy(alpha = 0.2f), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun GoogleG(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 48f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(PathParser().parsePathString(RED).toPath(), GoogleRed)
            drawPath(PathParser().parsePathString(BLUE).toPath(), GoogleBlue)
            drawPath(PathParser().parsePathString(YELLOW).toPath(), GoogleYellow)
            drawPath(PathParser().parsePathString(GREEN).toPath(), GoogleGreen)
        }
    }
}

@Preview
@Composable
private fun GoogleSignInButtonPreview() {
    IrisPreview {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoogleSignInButton(onClick = {})
            GoogleSignInButton(onClick = {}, loading = true)
            GoogleSignInButton(onClick = {}, enabled = false)
        }
    }
}
