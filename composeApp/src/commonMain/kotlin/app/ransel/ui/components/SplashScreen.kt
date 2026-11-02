package app.ransel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.ransel.content.stringResource
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.splash_logo_tile
import ransel.composeapp.generated.resources.ic_binocular
import ransel.composeapp.generated.resources.splash_tagline

/*
 * The lockup "Mengintip dari balik logo" (canvas "RANSEL — Logo & Maskot", option 1, picked by the owner on 1 Nov 2026):
 * the capybara with binoculars peeks over the green logo tile, with "RANSEL" and the tagline below. Positions are the
 * board's, in a 600 × 1030 frame scaled to the lockup width.
 */
private const val FRAME_WIDTH = 600f
private const val FRAME_HEIGHT = 1030f

/**
 * Opening screen while preferences and the stored session load: the logo lockup centred on the surface. The wordmark
 * is green on the light theme and white on the dark one, the tagline gray, as on the board.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        val width = min(maxWidth - 64.dp, 280.dp) // at 280 the tagline is 12.6 sp, above the 12 sp floor
        val u = width / FRAME_WIDTH
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        Box(Modifier.size(width, u * FRAME_HEIGHT)) {
            Image(
                painterResource(Res.drawable.ic_binocular),
                contentDescription = null,
                modifier = Modifier.offset(u * 142.6f, u * 14.8f).size(u * 315f, u * 411f),
            )
            Image(
                painterResource(Res.drawable.splash_logo_tile),
                contentDescription = null,
                modifier = Modifier.offset(u * 60f, u * 330f).size(u * 480f),
            )
            Text(
                "RANSEL",
                modifier = Modifier.offset(y = u * 861f).width(width),
                color = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                style = lockupStyle(u * 120f, u * 120f, FontWeight.ExtraBold),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                stringResource(Res.string.splash_tagline),
                modifier = Modifier.offset(y = u * 988f).width(width),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = lockupStyle(u * 27f, u * 36f, FontWeight.Bold),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Lockup text keeps its size with the font scale, like the drawn parts around it. */
@Composable
private fun lockupStyle(size: Dp, line: Dp, weight: FontWeight): TextStyle = with(LocalDensity.current) {
    MaterialTheme.typography.displayLarge.copy(
        fontSize = size.toSp(),
        lineHeight = line.toSp(),
        fontWeight = weight,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun SplashScreenPreview() = IrisPreview { SplashScreen() }
