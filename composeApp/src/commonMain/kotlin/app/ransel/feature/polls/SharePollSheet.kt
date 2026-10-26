package app.ransel.feature.polls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.whatsappShareLink
import app.ransel.shared.dto.PollDto
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.format.deadlineText
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.*

/**
 * Design "BagikanPolling": a ready message with the poll's guest link, sent from the user's own WhatsApp (RANSEL sends
 * nothing), or the link copied. Members without an account are named: they vote in a browser after picking their name.
 */
@Composable
fun SharePollSheet(
    poll: PollDto,
    share: ShareState,
    tripTitle: String,
    zone: TimeZone,
    guestNames: List<String>,
    canTurnOff: Boolean,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val link = share.link
    val message = link?.let { stringResource(Res.string.poll_share_message, poll.question, tripTitle, deadlineText(poll.closesAt, zone), it.url) }
    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (share.justCreated) Res.string.poll_share_title_new else Res.string.poll_share_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.poll_share_subtitle), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(Symbols.close, contentDescription = stringResource(Res.string.itinerary_close))
            }
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.poll_share_message_label), style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp)) {
                if (message == null) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center))
                else Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            Note(stringResource(Res.string.poll_share_preview_note))
            if (guestNames.isNotEmpty()) Note(stringResource(Res.string.poll_share_guest_note, guestNames.joinToString(", ")))
            Button(
                onClick = { message?.let { uriHandler.openUri(whatsappShareLink(it)) } },
                enabled = message != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Symbol(Symbols.share, tint = scheme.onPrimary)
                Text(stringResource(Res.string.poll_share_whatsapp), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { link?.let { clipboard.setText(AnnotatedString(it.url)); copied = true } },
                enabled = link != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Symbol(Symbols.contentCopy)
                Text(stringResource(if (copied) Res.string.poll_share_copied else Res.string.poll_share_copy), modifier = Modifier.padding(start = 8.dp))
            }
            if (canTurnOff && link != null) {
                TextButton(onClick = onTurnOff, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.poll_share_off), color = scheme.error)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol(Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
