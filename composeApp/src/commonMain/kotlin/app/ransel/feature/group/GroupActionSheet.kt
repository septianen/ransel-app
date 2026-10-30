package app.ransel.feature.group

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.group_action_document
import ransel.composeapp.generated.resources.group_action_document_hint
import ransel.composeapp.generated.resources.group_action_invite
import ransel.composeapp.generated.resources.group_action_invite_hint
import ransel.composeapp.generated.resources.group_action_poll
import ransel.composeapp.generated.resources.group_action_poll_hint
import ransel.composeapp.generated.resources.group_action_soon
import ransel.composeapp.generated.resources.group_sheet_title

/**
 * What the + button on the Group tab offers (design: Buat polling, Ajak anggota, Unggah dokumen). Documents arrive in a
 * later step: that row shows "Segera hadir" and does nothing. Only the owner can invite.
 */
@Composable
fun GroupActionSheet(canInvite: Boolean, onPoll: (() -> Unit)?, onInvite: () -> Unit, onDismiss: () -> Unit, onDocument: (() -> Unit)? = null) {
    AdaptiveSheet(onDismiss = onDismiss) { GroupActionContent(canInvite, onPoll, onInvite, onDocument) }
}

@Composable
private fun GroupActionContent(canInvite: Boolean, onPoll: (() -> Unit)?, onInvite: () -> Unit, onDocument: (() -> Unit)? = null) {
    val accents = IrisTheme.accents
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.group_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (onPoll != null) ActionRow(Symbols.checklist, accents.magenta, Res.string.group_action_poll, Res.string.group_action_poll_hint, soon = false, onClick = onPoll)
        if (canInvite) {
            ActionRow(Symbols.personAdd, accents.green, Res.string.group_action_invite, Res.string.group_action_invite_hint, soon = false, onClick = onInvite)
        }
        // editors add documents; everyone else sees them on the Group tab
        if (onDocument != null) {
            ActionRow(Symbols.cloudUpload, accents.blue, Res.string.group_action_document, Res.string.group_action_document_hint, soon = false, onClick = onDocument)
        }
    }
}

@Composable
private fun ActionRow(
    symbol: String,
    tile: androidx.compose.ui.graphics.Color,
    title: StringResource,
    hint: StringResource,
    soon: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(IrisShape.md)
            .clickable(enabled = !soon, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(IrisShape.md).background(if (soon) MaterialTheme.colorScheme.surfaceVariant else tile),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(symbol, tint = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else IrisTheme.accents.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (soon) {
            Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(
                    stringResource(Res.string.group_action_soon),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GroupActionSheetPreview() = IrisPreview {
    Box(Modifier.padding(20.dp)) { GroupActionContent(canInvite = true, onPoll = {}, onInvite = {}) }
}
