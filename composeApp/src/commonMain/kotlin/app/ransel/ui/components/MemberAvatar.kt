package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.data.remote.MemberPhotos
import app.ransel.data.remote.apiImageUrl
import app.ransel.ui.theme.IrisTheme
import coil3.compose.AsyncImage

/**
 * Initial on a colored circle. The color is stable per person (hash of [key], e.g. the member id); the owner is always
 * the brand green like in the design. A member without an account ([isGuest]) is grey and white. Decorative: the name
 * is always next to it. With a profile photo ([avatarUrl], by default the one [MemberPhotos] knows for [key]) the photo
 * covers the circle, cropped from the centre; the initial stays underneath while it loads or when it cannot.
 */
@Composable
fun MemberAvatar(
    name: String,
    key: String,
    modifier: Modifier = Modifier,
    isOwner: Boolean = false,
    isGuest: Boolean = false,
    size: Dp = 40.dp,
    avatarUrl: String? = MemberPhotos.urlFor(key),
) {
    val accents = IrisTheme.accents
    val palette = listOf(accents.purple, accents.brown, accents.magenta, accents.teal, accents.blue, accents.orange)
    val color = if (isOwner) accents.green else palette[(key.hashCode() and Int.MAX_VALUE) % palette.size]
    val background = if (isGuest) MaterialTheme.colorScheme.surfaceVariant else color
    Box(
        modifier.size(size).clip(CircleShape).background(background)
            .then(if (isGuest) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = (if (size >= 40.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium)
                .copy(fontWeight = FontWeight.ExtraBold),
            color = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant else accents.onAccent,
        )
        apiImageUrl(avatarUrl)?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
