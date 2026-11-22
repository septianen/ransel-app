package app.ransel.feature.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_buddy_body
import ransel.composeapp.generated.resources.explore_buddy_consent
import ransel.composeapp.generated.resources.explore_buddy_later
import ransel.composeapp.generated.resources.explore_buddy_notify
import ransel.composeapp.generated.resources.explore_buddy_sheet_title

/**
 * "Cari buddy · segera hadir — Kabari saya" (PRD v0.8 "Output", spec Screen 3 "Buddy link"): honest in its first sentence,
 * the consent line for logging the interest, then "Kabari saya" (records it) or "Nanti saja".
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BuddySheet(onNotify: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = IrisShape.xl) {
        BuddySheetContent(onNotify, onDismiss)
    }
}

@Composable
fun BuddySheetContent(onNotify: () -> Unit = {}, onDismiss: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.explore_buddy_sheet_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.explore_buddy_body), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(Res.string.explore_buddy_consent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onNotify, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(Res.string.explore_buddy_notify))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(Res.string.explore_buddy_later))
        }
    }
}

@Preview
@Composable
private fun BuddySheetPreview() = IrisPreview { BuddySheetContent() }
