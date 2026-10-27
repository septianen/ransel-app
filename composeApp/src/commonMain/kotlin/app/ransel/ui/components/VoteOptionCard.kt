package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One option of a voting, the same in a poll and in a place-voting round (owner, 27 Oct 2026: they looked different):
 * title, optional subtitle, a bar of [voteCount] out of [memberCount], and the count as text ([countLabel]).
 * Slots: [leading] (radio or checkbox), [tile] (category tile of a place), [headerTrailing] (winner chip, comment
 * button), [avatars] (who voted, when the voting is not anonymous) and [footer] (edit and decide buttons).
 * [highlighted] draws the 2 dp primary border (selected or winner); [onClick] applies to the header row.
 */
@Composable
fun VoteOptionCard(
    title: String,
    voteCount: Int,
    memberCount: Int,
    countLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    winner: Boolean = false,
    faded: Boolean = false,
    clickRole: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    tile: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    avatars: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (memberCount <= 0) 0f else (voteCount.toFloat() / memberCount).coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (faded) 0.75f else 1f)
            .clip(IrisShape.lg)
            .border(if (highlighted || winner) 2.dp else 1.dp, if (highlighted || winner) scheme.primary else scheme.outlineVariant, IrisShape.lg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = clickRole, onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke(this)
            tile?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            headerTrailing?.invoke(this)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (winner) scheme.primary else scheme.primary.copy(alpha = 0.6f)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(countLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            avatars?.invoke(this)
        }
        footer?.invoke(this)
    }
}

@Preview
@Composable
private fun VoteOptionCardPreview() = IrisPreview {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VoteOptionCard("Kereta", voteCount = 4, memberCount = 5, countLabel = "4 suara", subtitle = "Rp250.000", winner = true)
        VoteOptionCard("Pantai Melasti", voteCount = 2, memberCount = 5, countLabel = "2 dari 5 suka", subtitle = "Wisata")
        VoteOptionCard("Pesawat", voteCount = 0, memberCount = 5, countLabel = "0 suara")
    }
}
