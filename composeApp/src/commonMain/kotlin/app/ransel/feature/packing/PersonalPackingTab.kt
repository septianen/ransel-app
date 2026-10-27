package app.ransel.feature.packing

import ransel.composeapp.generated.resources.member_progress_remind_one
import ransel.composeapp.generated.resources.member_progress_all
import ransel.composeapp.generated.resources.member_list_draft_row
import ransel.composeapp.generated.resources.member_progress_summary
import ransel.composeapp.generated.resources.member_progress_title
import ransel.composeapp.generated.resources.template_apply_title
import ransel.composeapp.generated.resources.template_error
import ransel.composeapp.generated.resources.template_saved
import ransel.composeapp.generated.resources.template_added
import ransel.composeapp.generated.resources.action_back
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import ransel.composeapp.generated.resources.packing_personal_count
import ransel.composeapp.generated.resources.packing_personal_empty_body
import ransel.composeapp.generated.resources.packing_personal_empty_title
import ransel.composeapp.generated.resources.packing_personal_ready
import ransel.composeapp.generated.resources.packing_required
import ransel.composeapp.generated.resources.packing_required_count

/** What the Pribadi tab is showing on top of the list. */
private sealed interface PersonalSheet {
    data object Add : PersonalSheet
    data class Edit(val itemId: String) : PersonalSheet
    data object Start : PersonalSheet
    data class Editor(val name: String?, val items: List<MemberListItemDto>, val sendAfter: Boolean) : PersonalSheet
    data object Send : PersonalSheet
    data object UseTemplate : PersonalSheet
    data object SaveTemplate : PersonalSheet
}

/**
 * "Pribadi" in a group trip: the user's own bag, one list by category like every packing list. Lines the owner sent
 * look and behave like the member's own (owner, 27 Oct 2026): tick, edit, delete; only "Wajib" shows. The OWNER also
 * sees the members' list card with each member's required progress ("PackingOwnerGunung").
 */
@Composable
fun PersonalPackingTab(trip: TripDto, viewModel: GroupPackingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val canManage = trip.myRole == TripRole.OWNER
    var sheet by remember { mutableStateOf<PersonalSheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val texts = mapOf(
        "sent" to stringResource(Res.string.member_list_sent),
        "reminded" to stringResource(Res.string.member_list_reminded),
        "stale" to stringResource(Res.string.member_list_stale),
        "error" to stringResource(Res.string.member_list_error),
    )
    val requiredText = stringResource(Res.string.packing_required)
    var progressOpen by remember { mutableStateOf(false) }
    // templates for my own list ("Pakai template", "Simpan sebagai template"); they live on the server
    val templates: PackingTemplatesViewModel = koinViewModel(key = "personal-templates-${trip.id}") { parametersOf(trip.id, true) }
    val templateState by templates.state.collectAsState()
    val templateTexts = mapOf(
        "added" to stringResource(Res.string.template_added),
        "saved" to stringResource(Res.string.template_saved),
        "error" to stringResource(Res.string.template_error),
    )
    LaunchedEffect(templates) {
        templates.eventFlow.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is TemplateEvent.Applied -> { sheet = null; snackbar.showSnackbar(templateTexts.getValue("added").replace("%1\$d", event.count.toString())) }
                TemplateEvent.Saved -> { sheet = null; snackbar.showSnackbar(templateTexts.getValue("saved")) }
                is TemplateEvent.Problem -> snackbar.showSnackbar(templateTexts.getValue("error"))
            }
        }
    }
    val today = remember { app.ransel.util.today() }
    val useTemplate = { templates.load(); sheet = PersonalSheet.UseTemplate }

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
            // the owner's members' list: a card to start one, then a single line that opens "Progres anggota"
            if (canManage && !state.offline && !state.loading) {
                val list = state.list
                if (list == null) {
                    MemberListCard(list = null, state = state, onCreate = { viewModel.loadTemplates(); sheet = PersonalSheet.Start }, onEdit = {}, onSend = {})
                } else {
                    MemberListSummaryRow(list, state) { progressOpen = true }
                }
            }
            if (state.total > 0) {
                PackingProgressCard(
                    trip, today, state.packed, state.total, state.requiredDone, state.requiredTotal,
                    onUseTemplate = useTemplate,
                    onSaveTemplate = { sheet = PersonalSheet.SaveTemplate },
                    onUncheckAll = viewModel::uncheckAll,
                )
            } else if (!state.loading) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.packing_personal_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.packing_personal_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = useTemplate) { Text(stringResource(Res.string.template_apply_title)) }
                }
            }
            val required = state.requiredIds
            state.groups.forEach { group ->
                PackingGroupSection(group) { item ->
                    val line = state.personal.first { it.id == item.id }
                    PackingRow(
                        item,
                        onToggle = { viewModel.toggle(line) },
                        onLongPress = { sheet = PersonalSheet.Edit(item.id) },
                        enabled = true,
                        badge = requiredText.takeIf { item.id in required },
                        menu = { ItemMenu(item.name, ownList = true, onEdit = { sheet = PersonalSheet.Edit(item.id) }, onDelete = { viewModel.delete(line) }) },
                    )
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

    if (progressOpen) {
        state.list?.let { list ->
            MemberProgressScreen(
                list = list,
                state = state,
                onEdit = { sheet = PersonalSheet.Editor(list.name, list.items, sendAfter = list.sentAt == null) },
                onSend = { sheet = PersonalSheet.Send },
                onRemind = viewModel::remind,
                onRemindAll = viewModel::remindNotReady,
                onBack = { progressOpen = false },
            )
        }
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
        is PersonalSheet.Edit -> state.personal.firstOrNull { it.id == target.itemId }?.let { item ->
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
        PersonalSheet.UseTemplate -> ApplyTemplateSheet(
            state = templateState,
            existing = state.personal.map { it.asPackingItem() },
            onSelect = templates::select,
            onSkipChange = templates::setSkipExisting,
            onRetry = templates::load,
            onApply = templates::apply,
            onSaveCurrent = { sheet = PersonalSheet.SaveTemplate },
            onDismiss = { sheet = null },
        )
        PersonalSheet.SaveTemplate -> SaveTemplateSheet(
            defaultName = trip.title,
            items = state.personal.map { it.asPackingItem() },
            busy = templateState.busy,
            onSave = templates::save,
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

/** The OWNER's members' list: a card to start one, or the list itself (items, Ubah, Kirim while it is a draft). */
@Composable
private fun MemberListCard(
    list: MemberListDto?,
    state: GroupPackingUiState,
    onCreate: () -> Unit,
    onEdit: (MemberListDto) -> Unit,
    onSend: () -> Unit,
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
        MemberListCard(list, state, {}, {}, {})
        MemberListCard(null, state, {}, {}, {})
        MemberListSummaryRow(list, state) {}
    }
}

/** One line on the owner's Pribadi tab: "2 dari 5 anggota siap" with who is not ready yet, or the unsent draft. */
@Composable
private fun MemberListSummaryRow(list: MemberListDto, state: GroupPackingUiState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val notReady = state.progress.filterNot { it.ready }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.lg).background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(Symbols.checklist, tint = scheme.primary)
        Text(
            if (list.sentAt == null) stringResource(Res.string.member_list_draft_row, list.items.size)
            else stringResource(Res.string.member_progress_summary, state.progress.size - notReady.size, state.progress.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        // who is not ready yet, at most three faces
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            notReady.take(3).forEach { row ->
                MemberAvatar(state.member(row.memberId)?.displayName ?: "?", row.memberId, size = 24.dp)
            }
        }
        if (notReady.size > 3) Text("+${notReady.size - 3}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
    }
}

/**
 * "Progres anggota" (owner only): the members' list card with Ubah / Kirim, then every recipient, the not-ready ones
 * first, with a bell each, and "Ingatkan yang belum siap". A full screen over the Pribadi tab.
 */
@Composable
private fun MemberProgressScreen(
    list: MemberListDto,
    state: GroupPackingUiState,
    onEdit: () -> Unit,
    onSend: () -> Unit,
    onRemind: (List<String>) -> Unit,
    onRemindAll: () -> Unit,
    onBack: () -> Unit,
) {
    app.ransel.navigation.SheetBackHandler(onBack)
    androidx.compose.ui.window.Dialog(onDismissRequest = onBack, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            MemberProgressContent(list, state, onEdit, onSend, onRemind, onRemindAll, onBack)
        }
    }
}

@Composable
private fun MemberProgressContent(
    list: MemberListDto,
    state: GroupPackingUiState,
    onEdit: () -> Unit,
    onSend: () -> Unit,
    onRemind: (List<String>) -> Unit,
    onRemindAll: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Text(stringResource(Res.string.member_progress_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MemberListCard(list = list, state = state, onCreate = {}, onEdit = { onEdit() }, onSend = onSend)
            state.progress.sortedBy { it.ready }.forEach { row ->
                val member = state.member(row.memberId)
                val name = member?.displayName.orEmpty()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemberAvatar(name.ifEmpty { "?" }, row.memberId, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            listOfNotNull(
                                stringResource(Res.string.packing_required_count, row.requiredDone, row.requiredTotal).takeIf { row.requiredTotal > 0 },
                                stringResource(Res.string.member_progress_all, row.done, row.total),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.ready) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                    if (!row.ready && member?.userId != null) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).border(1.dp, scheme.outline, CircleShape)
                                .clickable(role = Role.Button, enabled = !state.busy) { onRemind(listOf(row.memberId)) },
                            contentAlignment = Alignment.Center,
                        ) { Symbol(Symbols.notificationsActive, size = 18.dp, contentDescription = stringResource(Res.string.member_progress_remind_one, name)) }
                    }
                }
            }
            if (state.progress.any { !it.ready }) {
                OutlinedButton(onClick = onRemindAll, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Symbol(Symbols.notifications)
                    Text(stringResource(Res.string.member_list_remind), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
