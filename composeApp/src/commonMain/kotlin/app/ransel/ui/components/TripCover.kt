package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import app.ransel.data.remote.apiImageUrl
import app.ransel.ui.preview.IrisPreview
import coil3.compose.AsyncImage
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Generated trip cover (design: background + two circles + a rounded "hill"), used until someone adds a photo. The
 * palette is picked from [seed] (the trip id) so a trip always looks the same. With [coverUrl] the photo fills the
 * frame, cropped from the centre, over the generated cover (which stays while it loads, offline without a cached copy,
 * or when it fails). Decorative: the title next to it already names the trip.
 */
@Composable
fun TripCover(
    seed: String,
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 12.dp,
    coverUrl: String? = null,
) {
    val a = IrisTheme.accents
    val palettes = listOf(
        listOf(a.teal, a.yellow, a.blue, a.green),
        listOf(a.orange, a.yellow, a.purple, a.brown),
        listOf(a.purple, a.magenta, a.yellow, a.blue),
        listOf(a.blue, a.yellow, a.teal, a.brown),
        listOf(a.brown, a.orange, a.yellow, a.green),
    )
    val p = palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(p[0])
            .clearAndSetSemantics { },
    ) {
        val s = minOf(maxWidth, maxHeight)
        Circle(p[1], s * 0.9f, Modifier.align(Alignment.TopEnd).offset(x = s * 0.3f, y = -(s * 0.4f)))
        Circle(p[2], s * 0.6f, Modifier.align(Alignment.BottomStart).offset(x = -(s * 0.16f), y = s * 0.3f))
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = maxWidth * 0.4f)
                .size(width = s * 0.5f, height = s * 0.25f)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(p[3]),
        )
        apiImageUrl(coverUrl)?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Circle(color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Preview
@Composable
private fun TripCoverPreview() = IrisPreview {
    Box(Modifier.fillMaxSize()) {
        TripCover(seed = "bali", modifier = Modifier.size(64.dp))
    }
}
