package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.ui.theme.IrisTheme

/**
 * Initial on a colored circle. The color is stable per person (hash of [userId]); the owner is always the brand
 * green like in the design. Decorative: the name is always next to it.
 */
@Composable
fun MemberAvatar(
    name: String,
    userId: String,
    modifier: Modifier = Modifier,
    isOwner: Boolean = false,
    size: Dp = 40.dp,
) {
    val accents = IrisTheme.accents
    val palette = listOf(accents.purple, accents.brown, accents.magenta, accents.teal, accents.blue, accents.orange)
    val color = if (isOwner) accents.green else palette[(userId.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = (if (size >= 40.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium)
                .copy(fontWeight = FontWeight.ExtraBold),
            color = accents.onAccent,
        )
    }
}
