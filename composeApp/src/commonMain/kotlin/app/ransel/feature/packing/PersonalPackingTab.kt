package app.ransel.feature.packing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.MemberListDto
import app.ransel.shared.dto.MemberListItemDto
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Role as TripRole
import app.ransel.ui.category.label
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.components.PendingMarker
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import app.ransel.shared.dto.MemberListProgressDto
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.member_list_create
import ransel.composeapp.generated.resources.member_list_create_hint
import ransel.composeapp.generated.resources.member_list_draft
import ransel.composeapp.generated.resources.member_list_edit
import ransel.composeapp.generated.resources.member_list_error
import ransel.composeapp.generated.resources.member_list_own
import ransel.composeapp.generated.resources.member_list_ready
import ransel.composeapp.generated.resources.member_list_remind
import ransel.composeapp.generated.resources.member_list_reminded
import ransel.composeapp.generated.resources.member_list_send_now
import ransel.composeapp.generated.resources.member_list_sent
import ransel.composeapp.generated.resources.member_list_stale
import ransel.composeapp.generated.resources.member_list_summary
import ransel.composeapp.generated.resources.member_list_title
import ransel.composeapp.generated.resources.packing_add
import ransel.composeapp.generated.resources.packing_from_list_locked
import ransel.composeapp.generated.resources.packing_from_owner
import ransel.composeapp.generated.resources.packing_from_owner_body
import ransel.composeapp.generated.resources.packing_my_extras
import ransel.composeapp.generated.resources.packing_only_you
import ransel.composeapp.generated.resources.packing_personal_count
import ransel.composeapp.generated.resources.packing_personal_empty_body
import ransel.composeapp.generated.resources.packing_personal_empty_title
import ransel.composeapp.generated.resources.packing_personal_ready
import ransel.composeapp.generated.resources.packing_required
import ransel.composeapp.generated.resources.packing_required_count
import ransel.composeapp.generated.resources.packing_trip_owner

/** What the Pribadi tab is showing on top of the list. */
private sealed interface PersonalSheet {
    data object Add : PersonalSheet
    data class Edit(val itemId: String) : PersonalSheet
    data object Start : PersonalSheet
    data class Editor(val name: String?, val items: List<MemberListItemDto>, val sendAfter: Boolean) : PersonalSheet
    data object Send : PersonalSheet
}

/**
 * "Pribadi" in a group trip: the user's own bag. Editors see the members' list card with each member's required
 * progress ("PackingOwnerGunung"); a member who received a list sees where it came from ("PackingPribadiGunung").
 */
@Composable
fun PersonalPackingTab(trip: TripDto, viewModel: GroupPackingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val canManage = trip.myRole.atLeast(TripRole.EDITOR)
    var sheet by remember { mutableStateOf<PersonalSheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val texts = mapOf(
        "sent" to stringResource(Res.string.member_list_sent),
        "reminded" to stringResource(Res.string.member_list_reminded),
        "stale" to stringResource(Res.string.member_list_stale),
        "error" to stringResource(Res.string.member_list_error),
    )
    val ownerName = state.member(state.list?.createdByMemberId)?.displayName ?: stringResource(Res.string.packing_trip_owner)
    val lockedText = stringResource(Res.string.packing_from_list_locked, ownerName)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                GroupPackingEvent.Saved -> Unit
                GroupPackingEvent.Sent -> { sheet = null; snackbar.showSnackbar(texts.getValue("sent")) }
                GroupPackingEvent.Reminded -> snackbar.showSnackbar(texts.getValue("reminded"))
                is GroupPackingEvent.Problem ->
                    snackbar.showSnackbar(texts.getValue(if (event.code == ErrorCode.LIST_STALE) "stale" else "error"))
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (canManage && !state.offline && !state.loading) {
                MemberListCard(
                    list = state.list,
                    state = state,
                    onCreate = { viewModel.loadTemplates(); sheet = PersonalSheet.Start },
                    onEdit = { list -> sheet = PersonalSheet.Editor(list.name, list.items, sendAfter = list.sentAt == null) },
                    onSend = { sheet = PersonalSheet.Send },
                    onRemind = viewModel::remindNotReady,
                )
            } else if (state.receivedList) {
                FromOwnerCard(ownerName, state.list?.createdByMemberId ?: "owner", state.fromList.sumOf { it.items.size }, state.requiredTotal)
            }
            if (state.total > 0) {
                PersonalProgressCard(state, title = stringResource(if (canManage && state.list != null) Res.string.member_list_own else Res.string.packing_personal_ready))
            } else if (!state.loading) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.packing_personal_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.packing_personal_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.fromList.forEach { group ->
                PersonalSection(PackingCategoryTitle(group), group.items, onToggle = viewModel::toggle) {
                    scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(lockedText) }
                }
            }
            if (state.receivedList) {
                if (state.own.isNotEmpty()) {
                    PersonalSection(SectionTitle.Extras, state.own, onToggle = viewModel::toggle) { sheet = PersonalSheet.Edit(it.id) }
                }
            } else {
                state.ownGroups.forEach { group ->
                    PersonalSection(PackingCategoryTitle(group), group.items, onToggle = viewModel::toggle) { sheet = PersonalSheet.Edit(it.id) }
                }
            }
        }
        IconFab(
            Symbols.add,
            label = stringResource(Res.string.packing_add),
            onClick = { sheet = PersonalSheet.Add },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data -> UndoSnackbar(message = data.visuals.message) }
    }

    when (val target = sheet) {
        PersonalSheet.Add -> AddPackingItemSheet(
            tripTitle = trip.title,
            itemCount = state.total,
            existing = null,
            onSave = { name, category, quantity, keepOpen ->
                viewModel.add(name, category, quantity)
                if (!keepOpen) sheet = null
            },
            onDelete = null,
            onDismiss = { sheet = null },
        )
        is PersonalSheet.Edit -> state.own.firstOrNull { it.id == target.itemId }?.let { item ->
            AddPackingItemSheet(
                tripTitle = trip.title,
                itemCount = state.total,
                existing = item.asPackingItem(),
                onSave = { name, category, quantity, _ -> viewModel.edit(item, name, category, quantity); sheet = null },
                onDelete = { viewModel.delete(item); sheet = null },
                onDismiss = { sheet = null },
            )
        }
        PersonalSheet.Start -> MemberListStartSheet(
            tripTitle = trip.title,
            memberCount = state.members.size,
            mineCount = state.personal.size,
            templates = state.templates,
            onCopyMine = { sheet = PersonalSheet.Editor(null, viewModel.linesFromMine(), sendAfter = true) },
            onTemplate = { sheet = PersonalSheet.Editor(it.name, viewModel.linesFromTemplate(it), sendAfter = true) },
            onBlank = { sheet = PersonalSheet.Editor(null, emptyList(), sendAfter = true) },
            onDismiss = { sheet = null },
        )
        is PersonalSheet.Editor -> MemberListEditor(
            tripTitle = trip.title,
            initialName = target.name,
            initialItems = target.items,
            sendAfter = target.sendAfter,
            busy = state.busy,
            onSave = { name, items ->
                viewModel.save(name, items) { saved -> sheet = if (saved.sentAt == null) PersonalSheet.Send else null }
            },
            onDismiss = { sheet = null },
        )
        PersonalSheet.Send -> state.list?.let { list ->
            MemberListSendSheet(
                list = list,
                members = state.members.filter { it.memberId != state.myMemberId },
                busy = state.busy,
                onSend = viewModel::send,
                onDismiss = { sheet = null },
            )
        }
        null -> Unit
    }
}

/** A personal line shown in the shared add/edit sheet (same fields). */
private fun PersonalItemDto.asPackingItem() = PackingItemDto(
    id = id, tripId = tripId, category = category, name = name, quantity = quantity, isPacked = isPacked,
    sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

private sealed interface SectionTitle {
    data object Extras : SectionTitle
}

private data class PackingCategoryTitle(val group: PersonalGroup) : SectionTitle

@Composable
private fun PersonalSection(title: SectionTitle, items: List<PersonalItemDto>, onToggle: (PersonalItemDto) -> Unit, onLongPress: (PersonalItemDto) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (title) {
                is PackingCategoryTitle -> {
                    PackingCategoryIcon(title.group.category)
                    Text(stringResource(title.group.category.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                SectionTitle.Extras -> {
                    Box(Modifier.size(32.dp).clip(IrisShape.sm).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.person, size = 18.dp, tint = scheme.onSurfaceVariant)
                    }
                    Text(stringResource(Res.string.packing_my_extras), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            Text("${items.count { it.isPacked }}/${items.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Column(Modifier.padding(start = 44.dp, end = 12.dp)) {
            items.forEach { item ->
                PersonalRow(item, extras = title == SectionTitle.Extras, onToggle = { onToggle(item) }, onLongPress = { onLongPress(item) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonalRow(item: PersonalItemDto, extras: Boolean, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).combinedClickable(role = Role.Checkbox, onClick = onToggle, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val packed = item.isPacked
        Box(
            Modifier.size(22.dp).clip(IrisShape.xs)
                .then(if (packed) Modifier.background(scheme.primary) else Modifier.border(2.dp, scheme.outline, IrisShape.xs)),
            contentAlignment = Alignment.Center,
        ) { if (packed) Symbol(Symbols.check, size = 16.dp, tint = scheme.onPrimary) }
        Text(
            item.name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (packed) FontWeight.Normal else FontWeight.SemiBold,
                textDecoration = if (packed) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (packed) scheme.onSurfaceVariant else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        PendingMarker(item.id)
        when {
            item.required -> Text(
                stringResource(Res.string.packing_required),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onErrorContainer,
                modifier = Modifier.clip(IrisShape.xs).background(scheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp),
            )
            extras -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Symbol(Symbols.lock, size = 14.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.packing_only_you), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
        }
        if (item.quantity > 1) Text("×${item.quantity}", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, IrisShape.lg, ambientColor = Color.Black.copy(alpha = .06f), spotColor = Color.Black.copy(alpha = .06f))
            .background(MaterialTheme.colorScheme.surface, IrisShape.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** "Siap dibawa · 7 dari 25 barang · Wajib 3/8" with a bar. */
@Composable
private fun PersonalProgressCard(state: GroupPackingUiState, title: String) = Card {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(stringResource(Res.string.packing_personal_count, state.packed, state.total), style = MaterialTheme.typography.titleLarge)
        }
        if (state.requiredTotal > 0) {
            Text(
                stringResource(Res.string.packing_required_count, state.requiredDone, state.requiredTotal),
                style = MaterialTheme.typography.titleSmall,
                color = if (state.requiredDone == state.requiredTotal) scheme.primary else scheme.error,
            )
        }
    }
    val percent = if (state.total == 0) 0f else state.packed.toFloat() / state.total
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(percent).height(8.dp).clip(CircleShape).background(scheme.primary))
    }
}

/** A member's view: "Daftar bawaan dari Septian". */
@Composable
private fun FromOwnerCard(ownerName: String, ownerKey: String, count: Int, required: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.primaryContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(ownerName, ownerKey, isOwner = true)
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.packing_from_owner, ownerName), style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
            Text(stringResource(Res.string.packing_from_owner_body, count, required), style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
        }
    }
}

/** An editor's view: start a list, or the list with each recipient's required progress and "Ingatkan". */
@Composable
private fun MemberListCard(
    list: MemberListDto?,
    state: GroupPackingUiState,
    onCreate: () -> Unit,
    onEdit: (MemberListDto) -> Unit,
    onSend: () -> Unit,
    onRemind: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (list == null) {
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.lg).dashedBorder(scheme.outline)
                .clickable(role = Role.Button, onClick = onCreate).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.checklist, tint = scheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.member_list_create), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.member_list_create_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
        }
        return
    }
    val required = list.items.count { it.required }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                Symbol(Symbols.checklist, tint = scheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.member_list_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (list.sentAt == null) stringResource(Res.string.member_list_draft, list.items.size, required)
                    else stringResource(Res.string.member_list_summary, list.items.size, required, state.progress.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onEdit(list) }) { Text(stringResource(Res.string.member_list_edit)) }
        }
        if (list.sentAt == null) {
            OutlinedButton(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.share)
                Text(stringResource(Res.string.member_list_send_now), modifier = Modifier.padding(start = 8.dp))
            }
            return@Card
        }
        state.progress.forEach { row ->
            val member = state.member(row.memberId)
            val ready = row.requiredDone >= row.requiredTotal
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberAvatar(member?.displayName ?: "?", row.memberId, size = 32.dp)
                Text(member?.displayName.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (ready) stringResource(Res.string.member_list_ready, row.requiredDone, row.requiredTotal)
                    else stringResource(Res.string.packing_required_count, row.requiredDone, row.requiredTotal),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ready) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
        if (state.progress.any { it.requiredDone < it.requiredTotal }) {
            OutlinedButton(onClick = onRemind, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.notifications)
                Text(stringResource(Res.string.member_list_remind), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val previewStamp = kotlin.time.Instant.parse("2026-12-01T00:00:00Z")

private fun previewMember(id: String, name: String) = app.ransel.shared.dto.TripMemberDto(
    tripId = "t", memberId = id, userId = id, displayName = name, role = TripRole.EDITOR, joinedAt = previewStamp,
)

@Preview
@Composable
private fun MemberListCardPreview() = IrisPreview {
    val list = MemberListDto(
        tripId = "t", name = "Gunung", version = 2, sentToAll = true, sentAt = previewStamp, createdByMemberId = "me", updatedAt = previewStamp,
        items = listOf(MemberListItemDto("a", PackingCategory.CLOTHING, "Jaket", 1, true), MemberListItemDto("b", PackingCategory.OTHER, "Matras", 1, false)),
    )
    val state = GroupPackingUiState(
        loading = false,
        members = listOf(previewMember("d", "Dinda"), previewMember("r", "Raka")),
        list = list,
        progress = listOf(MemberListProgressDto("d", 0, 1, 0, 2), MemberListProgressDto("r", 1, 1, 2, 2)),
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MemberListCard(list, state, {}, {}, {}, {})
        MemberListCard(null, state, {}, {}, {}, {})
        FromOwnerCard("Septian", "me", 24, 8)
    }
}

@Preview
@Composable
private fun PersonalSectionPreview() = IrisPreview {
    val lines = listOf(
        PersonalItemDto("1", "t", "me", PackingCategory.CLOTHING, "Jaket tebal", sourceItemId = "a", required = true, isPacked = true, createdAt = previewStamp, updatedAt = previewStamp),
        PersonalItemDto("2", "t", "me", PackingCategory.CLOTHING, "Kaos kaki", quantity = 3, sourceItemId = "b", createdAt = previewStamp, updatedAt = previewStamp),
    )
    val own = listOf(PersonalItemDto("3", "t", "me", PackingCategory.ELECTRONICS, "Kamera mirrorless", createdAt = previewStamp, updatedAt = previewStamp))
    Column(Modifier.padding(16.dp)) {
        PersonalSection(PackingCategoryTitle(PersonalGroup(PackingCategory.CLOTHING, lines)), lines, {}, {})
        PersonalSection(SectionTitle.Extras, own, {}, {})
    }
}
