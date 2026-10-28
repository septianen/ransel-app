package app.ransel.feature.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ransel.feature.invite.InviteMembersSheet
import app.ransel.shared.domain.whatsappLink
import app.ransel.shared.dto.InviteDto
import app.ransel.platform.rememberShareText
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import app.ransel.data.remote.ApiFailure
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.member_guest_browser
import ransel.composeapp.generated.resources.member_release_guest
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.group_guest_tag
import ransel.composeapp.generated.resources.invite_button
import ransel.composeapp.generated.resources.member_invite_message
import ransel.composeapp.generated.resources.member_send_link
import ransel.composeapp.generated.resources.members_add_button
import ransel.composeapp.generated.resources.members_claim_banner
import ransel.composeapp.generated.resources.members_claim_undo
import ransel.composeapp.generated.resources.invites_active_title
import ransel.composeapp.generated.resources.invite_revoke
import ransel.composeapp.generated.resources.invite_meta_used
import ransel.composeapp.generated.resources.invite_meta_used_unlimited
import ransel.composeapp.generated.resources.invite_ends_today
import ransel.composeapp.generated.resources.invite_ends_in_days
import ransel.composeapp.generated.resources.members_change_role
import ransel.composeapp.generated.resources.members_count
import ransel.composeapp.generated.resources.members_error_connection
import ransel.composeapp.generated.resources.members_error_generic
import ransel.composeapp.generated.resources.members_leave
import ransel.composeapp.generated.resources.members_leave_body
import ransel.composeapp.generated.resources.members_leave_confirm
import ransel.composeapp.generated.resources.members_leave_title
import ransel.composeapp.generated.resources.members_make_owner
import ransel.composeapp.generated.resources.members_new_owner_body
import ransel.composeapp.generated.resources.members_new_owner_confirm
import ransel.composeapp.generated.resources.members_new_owner_title
import ransel.composeapp.generated.resources.members_no_new_owner_body
import ransel.composeapp.generated.resources.members_no_new_owner_delete
import ransel.composeapp.generated.resources.members_no_new_owner_title
import ransel.composeapp.generated.resources.members_make_owner_body
import ransel.composeapp.generated.resources.members_make_owner_confirm
import ransel.composeapp.generated.resources.members_make_owner_title
import ransel.composeapp.generated.resources.members_only_owner_note
import ransel.composeapp.generated.resources.members_permissions_editor
import ransel.composeapp.generated.resources.members_permissions_owner
import ransel.composeapp.generated.resources.members_permissions_title
import ransel.composeapp.generated.resources.members_permissions_viewer
import ransel.composeapp.generated.resources.members_remove
import ransel.composeapp.generated.resources.members_remove_body
import ransel.composeapp.generated.resources.members_remove_confirm
import ransel.composeapp.generated.resources.members_remove_note
import ransel.composeapp.generated.resources.members_remove_title
import ransel.composeapp.generated.resources.members_role_editor_hint
import ransel.composeapp.generated.resources.members_role_viewer_hint
import ransel.composeapp.generated.resources.members_title
import ransel.composeapp.generated.resources.members_you
import ransel.composeapp.generated.resources.role_editor
import ransel.composeapp.generated.resources.role_owner
import ransel.composeapp.generated.resources.role_viewer

/** Trip members (design `AnggotaTrip` for the owner, `AnggotaEditor` for everyone else). */
@Composable
fun MembersScreen(
    tripId: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set when opened from a MEMBER_CLAIMED notification: lets the OWNER undo that claim. */
    claimedGuestId: String? = null,
    claimedGuestName: String? = null,
    claimerName: String? = null,
    /** From AjakTeman's "Tambah nama teman saja": the add-by-name sheet opens once. */
    openAdd: Boolean = false,
    /** The OWNER cannot hand the trip over (nobody else has an account): open the trip settings, where it can be deleted. */
    onDeleteTrip: () -> Unit = {},
    viewModel: MembersViewModel = koinViewModel(key = "members-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    var addOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openAdd) {
        if (openAdd && !addOpened) {
            addOpened = true
            viewModel.openAddForm()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val generic = stringResource(Res.string.members_error_generic)
    val connection = stringResource(Res.string.members_error_connection)
    val uriHandler = LocalUriHandler.current
    val share = rememberShareText()

    LaunchedEffect(claimedGuestId) {
        if (claimedGuestId != null && claimedGuestName != null) viewModel.showClaimUndo(claimedGuestId, claimedGuestName, claimerName)
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                MembersEvent.Left -> onLeft()
                MembersEvent.OpenDeleteTrip -> onDeleteTrip()
                is MembersEvent.SendLink -> {
                    val message = getString(Res.string.member_invite_message, event.member.displayName, event.tripTitle, event.url)
                    // WhatsApp straight to the number when there is one, otherwise the system share sheet.
                    whatsappLink(event.member.phone, message)?.let { uriHandler.openUri(it) } ?: share(message)
                }
                is MembersEvent.Problem -> {
                    snackbar.currentSnackbarData?.dismiss()
                    val offline = event.code == app.ransel.shared.api.ErrorCode.NETWORK_TIMEOUT ||
                        event.code == app.ransel.shared.api.ErrorCode.NETWORK_UNAVAILABLE
                    snackbar.showSnackbar(if (offline) connection else generic)
                }
            }
        }
    }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        MembersContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::load,
            onChangeRole = viewModel::changeRole,
            onAsk = viewModel::ask,
            onInvite = viewModel::openInviteSheet,
            onRevokeInvite = viewModel::revokeInvite,
            onAddByName = viewModel::openAddForm,
            onSendLink = viewModel::sendLink,
            onReleaseGuest = viewModel::releaseGuest,
            onUndoClaim = viewModel::undoClaim,
            onLeave = viewModel::askLeave,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }

    state.addForm?.let { form ->
        AddMemberSheet(
            form = form,
            onName = viewModel::onAddName,
            onPhone = viewModel::onAddPhone,
            onSubmit = viewModel::submitAdd,
            onDismiss = viewModel::closeAddForm,
        )
    }

    if (state.inviteSheetOpen) {
        InviteMembersSheet(tripId = tripId, tripTitle = state.tripTitle, onDismiss = viewModel::closeInviteSheet)
    }

    when (val dialog = state.dialog) {
        is MembersDialog.Remove -> ConfirmDialog(
            title = stringResource(Res.string.members_remove_title, dialog.member.displayName),
            body = stringResource(Res.string.members_remove_body, dialog.member.displayName),
            confirmLabel = stringResource(Res.string.members_remove_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            destructive = true,
            symbol = Symbols.personRemove,
            extra = {
                // The design also quotes the member's balance here; it comes with the balances screen (F4.3).
                InfoNote(Symbols.receiptLong, stringResource(Res.string.members_remove_note, dialog.member.displayName))
            },
        )
        is MembersDialog.MakeOwner -> ConfirmDialog(
            title = stringResource(Res.string.members_make_owner_title, dialog.member.displayName),
            body = stringResource(Res.string.members_make_owner_body, dialog.member.displayName),
            confirmLabel = stringResource(Res.string.members_make_owner_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            symbol = Symbols.person,
        )
        MembersDialog.Leave -> ConfirmDialog(
            title = stringResource(Res.string.members_leave_title, state.tripTitle),
            body = stringResource(Res.string.members_leave_body),
            confirmLabel = stringResource(Res.string.members_leave_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            destructive = true,
            symbol = Symbols.logout,
        )
        is MembersDialog.PickNewOwner -> NewOwnerDialog(
            dialog = dialog,
            leaving = state.leaving,
            onPick = viewModel::pickNewOwner,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        MembersDialog.NoNewOwner -> ConfirmDialog(
            title = stringResource(Res.string.members_no_new_owner_title),
            body = stringResource(Res.string.members_no_new_owner_body),
            confirmLabel = stringResource(Res.string.members_no_new_owner_delete),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            destructive = true,
            symbol = Symbols.person,
        )
        null -> Unit
    }
}

/** Stateless body, so previews can render every state. */
@Composable
fun MembersContent(
    state: MembersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onChangeRole: (TripMemberDto, Role) -> Unit,
    onAsk: (MembersDialog) -> Unit,
    modifier: Modifier = Modifier,
    onInvite: () -> Unit = {},
    onRevokeInvite: (InviteDto) -> Unit = {},
    onAddByName: () -> Unit = {},
    onSendLink: (TripMemberDto) -> Unit = {},
    onUndoClaim: () -> Unit = {},
    onReleaseGuest: (TripMemberDto) -> Unit = {},
    onLeave: () -> Unit = { onAsk(MembersDialog.Leave) },
) {
    Column(modifier.fillMaxSize()) {
        MembersTopBar(state, onBack)
        when {
            state.loading && state.members.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.failure != null && state.members.isEmpty() -> LoadFailure(state.failure, onRetry, onBack)
            else -> Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                state.claimBanner?.takeIf { state.isOwner }?.let { ClaimBannerCard(it, onUndoClaim) }
                if (state.isOwner) {
                    Button(
                        onClick = onInvite,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Symbol(Symbols.personAdd, size = 22.dp, tint = MaterialTheme.colorScheme.onPrimary)
                        Text(
                            stringResource(Res.string.invite_button),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (state.isOwner) {
                    OutlinedButton(
                        onClick = onAddByName,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Symbol(Symbols.personAdd, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(Res.string.members_add_button),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Column {
                    state.members.forEachIndexed { index, member ->
                        MemberRow(
                            member = member,
                            isMe = member.userId == state.myUserId,
                            canManage = state.isOwner && member.role != Role.OWNER,
                            enabled = !state.busy,
                            showDivider = index != state.members.lastIndex,
                            onChangeRole = { onChangeRole(member, it) },
                            onSendLink = { onSendLink(member) },
                            onReleaseGuest = { onReleaseGuest(member) },
                            onMakeOwner = { onAsk(MembersDialog.MakeOwner(member)) },
                            onRemove = { onAsk(MembersDialog.Remove(member)) },
                        )
                    }
                }
                if (state.isOwner && state.invites.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.invites_active_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.invites.forEach { InviteRow(it, onRevoke = { onRevokeInvite(it) }) }
                    }
                }
                PermissionsCard()
                if (!state.isOwner) {
                    InfoNote(Symbols.info, stringResource(Res.string.members_only_owner_note), muted = true)
                }
                // The owner leaves too, after handing the trip over (MembersDialog.PickNewOwner).
                if (state.me != null) {
                    OutlinedButton(
                        onClick = onLeave,
                        enabled = !state.busy && state.me != null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Symbol(Symbols.logout, size = 20.dp, tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(Res.string.members_leave),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTopBar(state: MembersUiState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = SemanticsRole.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(stringResource(Res.string.members_title), style = MaterialTheme.typography.titleLarge)
            val count = pluralStringResource(Res.plurals.members_count, state.members.size, state.members.size)
            Text(
                text = if (state.tripTitle.isEmpty()) count else "${state.tripTitle} · $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadFailure(failure: ApiFailure, onRetry: () -> Unit, onBack: () -> Unit) {
    if (failure.isConnection) {
        ConnectionErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onBack)
    } else {
        ServerErrorState(onRetry = onRetry, errorCode = failure.code, onBack = onBack)
    }
}

@Composable
private fun MemberRow(
    member: TripMemberDto,
    isMe: Boolean,
    canManage: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onChangeRole: (Role) -> Unit,
    onSendLink: () -> Unit,
    onMakeOwner: () -> Unit,
    onRemove: () -> Unit,
    onReleaseGuest: () -> Unit = {},
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MemberAvatar(member.displayName, member.memberId, isOwner = member.role == Role.OWNER, isGuest = member.isGuest)
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        member.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isMe) {
                        Text(
                            " " + stringResource(Res.string.members_you),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (member.isGuest) {
                    Text(
                        listOfNotNull(
                            stringResource(Res.string.group_guest_tag),
                            member.phone?.let { "+$it" },
                            if (member.guestBrowser) stringResource(Res.string.member_guest_browser) else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                member.email?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canManage && member.isGuest) {
                GuestMenu(member, enabled, onSendLink, onRemove, onReleaseGuest)
            } else if (canManage) {
                RoleMenuChip(member, enabled, onChangeRole, onMakeOwner, onRemove)
            } else {
                RoleChip(member.role)
            }
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.OWNER -> Res.string.role_owner
        Role.EDITOR -> Res.string.role_editor
        Role.VIEWER -> Res.string.role_viewer
    },
)

/** Read-only role pill: green for the owner, neutral for the others. */
@Composable
private fun RoleChip(role: Role) {
    val owner = role == Role.OWNER
    Box(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (owner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            roleLabel(role),
            style = MaterialTheme.typography.labelMedium,
            color = if (owner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Menu of a person added by name: send them the trip link, or remove them. They have no role to change. */
@Composable
private fun GuestMenu(member: TripMemberDto, enabled: Boolean, onSendLink: () -> Unit, onRemove: () -> Unit, onRelease: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.members_change_role, member.displayName)
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = SemanticsRole.Button) { open = true }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.moreVert) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = IrisShape.md,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            ActionOption(Symbols.share, stringResource(Res.string.member_send_link), danger = false) {
                open = false; onSendLink()
            }
            // a browser holds this name on the guest page
            if (member.guestBrowser) {
                ActionOption(Symbols.refresh, stringResource(Res.string.member_release_guest), danger = false) {
                    open = false; onRelease()
                }
            }
            ActionOption(Symbols.personRemove, stringResource(Res.string.members_remove), danger = true) {
                open = false; onRemove()
            }
        }
    }
}

/** Shown to the OWNER after someone picked a name: who took it, with a way back if it was the wrong person. */
@Composable
private fun ClaimBannerCard(banner: ClaimBanner, onUndo: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.members_claim_banner, banner.claimerName ?: "?", banner.guestName),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onUndo, enabled = !banner.undoing, modifier = Modifier.height(40.dp)) {
            Text(stringResource(Res.string.members_claim_undo), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Outlined role pill with a menu (design `MenuAnggota`): Editor / Viewer, then make owner, remove. */
@Composable
private fun RoleMenuChip(
    member: TripMemberDto,
    enabled: Boolean,
    onChangeRole: (Role) -> Unit,
    onMakeOwner: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.members_change_role, member.displayName)
    Box {
        Row(
            Modifier
                .height(32.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(enabled = enabled, role = SemanticsRole.Button) { open = true }
                .semantics { contentDescription = description }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(roleLabel(member.role), style = MaterialTheme.typography.labelMedium)
            Symbol(Symbols.expandMore, size = 18.dp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = IrisShape.md,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RoleOption(Role.EDITOR, Res.string.members_role_editor_hint, member.role == Role.EDITOR) {
                open = false; onChangeRole(Role.EDITOR)
            }
            RoleOption(Role.VIEWER, Res.string.members_role_viewer_hint, member.role == Role.VIEWER) {
                open = false; onChangeRole(Role.VIEWER)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ActionOption(Symbols.person, stringResource(Res.string.members_make_owner), danger = false) {
                open = false; onMakeOwner()
            }
            ActionOption(Symbols.personRemove, stringResource(Res.string.members_remove), danger = true) {
                open = false; onRemove()
            }
        }
    }
}

@Composable
private fun RoleOption(role: Role, hint: StringResource, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .width(240.dp)
            .heightIn(min = 56.dp)
            .clickable(role = SemanticsRole.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                roleLabel(role),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) Symbol(Symbols.check, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ActionOption(symbol: String, label: String, danger: Boolean, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .width(240.dp)
            .height(48.dp)
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(symbol, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun InviteRow(invite: InviteDto, onRevoke: () -> Unit) {
    val role = stringResource(if (invite.role == Role.VIEWER) Res.string.role_viewer else Res.string.role_editor)
    val usage = invite.maxUses?.let { stringResource(Res.string.invite_meta_used, role, invite.usedCount, it) }
        ?: stringResource(Res.string.invite_meta_used_unlimited, role, invite.usedCount)
    val daysLeft = (invite.expiresAt - Clock.System.now()).inWholeDays.toInt()
    val ends = if (daysLeft < 1) stringResource(Res.string.invite_ends_today)
    else pluralStringResource(Res.plurals.invite_ends_in_days, daysLeft, daysLeft)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IrisShape.lg)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.link) }
        Column(Modifier.weight(1f)) {
            Text(
                invite.code,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            )
            Text(
                "$usage · $ends",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRevoke) {
            Text(
                stringResource(Res.string.invite_revoke),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * "Pilih pemilik baru dulu" (not designed yet): the OWNER picks who takes over before leaving.
 * Only members with an account are listed; confirming hands the trip over, then leaves.
 */
@Composable
private fun NewOwnerDialog(
    dialog: MembersDialog.PickNewOwner,
    leaving: Boolean,
    onPick: (TripMemberDto) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.members_new_owner_title),
        body = stringResource(Res.string.members_new_owner_body),
        confirmLabel = stringResource(Res.string.members_new_owner_confirm),
        dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
        symbol = Symbols.logout,
        confirmLoading = leaving,
        extra = { NewOwnerOptions(dialog, enabled = !leaving, onPick = onPick) },
    )
}

@Composable
private fun NewOwnerOptions(dialog: MembersDialog.PickNewOwner, enabled: Boolean, onPick: (TripMemberDto) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        dialog.candidates.forEach { member ->
            val selected = member.userId == dialog.selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(IrisShape.md)
                    .selectable(selected = selected, enabled = enabled, role = SemanticsRole.RadioButton) { onPick(member) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                MemberAvatar(member.displayName, member.memberId, isOwner = false, isGuest = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        member.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        roleLabel(member.role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(Res.string.members_permissions_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            Res.string.members_permissions_owner,
            Res.string.members_permissions_editor,
            Res.string.members_permissions_viewer,
        ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun InfoNote(symbol: String, text: String, muted: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.md)
            .background(
                if (muted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(symbol, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val previewMembers = listOf(
    TripMemberDto("t", "m1", "u1", "Septian", "septian@contoh.id", Role.OWNER, Instant.parse("2026-10-01T00:00:00Z")),
    TripMemberDto("t", "m2", "u2", "Dinda", "dinda@contoh.id", Role.EDITOR, Instant.parse("2026-10-02T00:00:00Z")),
    TripMemberDto("t", "m3", "u3", "Maya", "maya@contoh.id", Role.VIEWER, Instant.parse("2026-10-03T00:00:00Z")),
    TripMemberDto("t", "m4", null, "Rina", null, Role.VIEWER, Instant.parse("2026-10-04T00:00:00Z"), phone = "6281234567890"),
)

@Preview
@Composable
private fun MembersOwnerPreview() = IrisPreview {
    MembersContent(
        state = MembersUiState(loading = false, tripTitle = "Bali 4 hari", members = previewMembers, myUserId = "u1"),
        onBack = {}, onRetry = {}, onChangeRole = { _, _ -> }, onAsk = {},
    )
}

@Preview
@Composable
private fun MembersClaimPreview() = IrisPreview {
    MembersContent(
        state = MembersUiState(
            loading = false, tripTitle = "Bali 4 hari", members = previewMembers, myUserId = "u1",
            claimBanner = ClaimBanner("m4", "Rina", "Dinda"),
        ),
        onBack = {}, onRetry = {}, onChangeRole = { _, _ -> }, onAsk = {},
    )
}

// Dialogs open their own window, so the previews render the dialog's list directly.
@Preview
@Composable
private fun NewOwnerOptionsPreview() = IrisPreview {
    val candidates = newOwnerCandidates(previewMembers, myUserId = "u1")
    NewOwnerOptions(MembersDialog.PickNewOwner(candidates, selected = "u2"), enabled = true, onPick = {})
}

@Preview
@Composable
private fun NewOwnerOptionsLongNamePreview() = IrisPreview {
    val long = previewMembers[1].copy(displayName = "Dinda Ayu Kusumawardhani Prameswari Putri")
    NewOwnerOptions(MembersDialog.PickNewOwner(listOf(long, previewMembers[2]), selected = null), enabled = false, onPick = {})
}

@Preview
@Composable
private fun MembersEditorPreview() = IrisPreview {
    MembersContent(
        state = MembersUiState(loading = false, tripTitle = "Bali 4 hari", members = previewMembers, myUserId = "u2"),
        onBack = {}, onRetry = {}, onChangeRole = { _, _ -> }, onAsk = {},
    )
}
