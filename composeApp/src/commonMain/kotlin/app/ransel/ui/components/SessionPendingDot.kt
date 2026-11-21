package app.ransel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_session_pending

/**
 * The magenta dot of an unfinished swipe session (a `secondary` dot in a `surface` ring, read out as
 * "explore_session_pending"). Since PRD v0.8 it sits on the trip's "Cari inspirasi dengan AI" entry, not on the
 * Explore tab icon.
 */
@Composable
fun SessionPendingDot(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.explore_session_pending)
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
    }
}

@Preview
@Composable
private fun SessionPendingDotPreview() = IrisPreview { SessionPendingDot() }
