package app.ransel.feature.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ransel.content.stringResource
import app.ransel.platform.rememberShareText
import app.ransel.shared.dto.InviteDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.invite_copy
import ransel.composeapp.generated.resources.invite_code_label
import ransel.composeapp.generated.resources.invite_failed
import ransel.composeapp.generated.resources.invite_join_as
import ransel.composeapp.generated.resources.invite_share
import ransel.composeapp.generated.resources.invite_share_text
import ransel.composeapp.generated.resources.invite_subtitle
import ransel.composeapp.generated.resources.invite_title
import ransel.composeapp.generated.resources.invite_valid
import ransel.composeapp.generated.resources.members_role_editor_desc
import ransel.composeapp.generated.resources.members_role_viewer_desc
import ransel.composeapp.generated.resources.role_editor
import ransel.composeapp.generated.resources.role_viewer
import ransel.composeapp.generated.resources.state_retry

/** Design `UndangAnggota`: pick a role, get a code and a link, share it. The QR button is left out for now. */
@Composable
fun InviteMembersSheet(
    tripId: String,
    tripTitle: String,
    onDismiss: () -> Unit,
    viewModel: InviteSheetViewModel = koinViewModel(key = "invite-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val share = rememberShareText()
    val invite = state.invite
    val shareText = invite?.let { stringResource(Res.string.invite_share_text, tripTitle, it.url) }

    AdaptiveSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.invite_title, tripTitle),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(Res.string.invite_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = SemanticsRole.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close)) }
        }

        Column(Modifier.padding(top = 20.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.invite_join_as), style = MaterialTheme.typography.titleSmall)
            RoleOptionRow(
                symbol = Symbols.edit,
                title = stringResource(Res.string.role_editor),
                description = stringResource(Res.string.members_role_editor_desc),
                selected = state.role == Role.EDITOR,
                onClick = { viewModel.selectRole(Role.EDITOR) },
            )
            RoleOptionRow(
                symbol = Symbols.visibility,
                title = stringResource(Res.string.role_viewer),
                description = stringResource(Res.string.members_role_viewer_desc),
                selected = state.role == Role.VIEWER,
                onClick = { viewModel.selectRole(Role.VIEWER) },
            )
        }

        Column(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .clip(IrisShape.lg)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.invite_code_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.loading -> Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                invite == null -> {
                    Text(
                        stringResource(Res.string.invite_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::retry) { Text(stringResource(Res.string.state_retry)) }
                }
                else -> {
                    Text(
                        invite.code,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp,
                        ),
                    )
                    LinkRow(invite, onCopy = { clipboard.setText(AnnotatedString(invite.url)) })
                    Text(
                        stringResource(Res.string.invite_valid, INVITE_VALID_DAYS, invite.maxUses ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = { shareText?.let(share) },
            enabled = invite != null,
            modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(48.dp),
        ) {
            Symbol(Symbols.share, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
            Text(
                stringResource(Res.string.invite_share),
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun LinkRow(invite: InviteDto, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(IrisShape.md)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.md)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            invite.url.removePrefix("https://"),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onCopy) {
            Symbol(Symbols.contentCopy, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(Res.string.invite_copy), Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun RoleOptionRow(symbol: String, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, IrisShape.lg)
            .selectable(selected = selected, role = SemanticsRole.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(symbol, tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}
