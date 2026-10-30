package app.ransel.feature.packing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import ransel.composeapp.generated.resources.packing_collapse
import ransel.composeapp.generated.resources.packing_expand
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.SegmentedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.countdownLabel
import app.ransel.feature.trips.daysUntilStart
import app.ransel.feature.trips.phase
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.ui.category.label
import app.ransel.ui.clickableSemantics
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.today
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.packing_item_delete_confirm
import ransel.composeapp.generated.resources.packing_item_delete_body
import ransel.composeapp.generated.resources.packing_item_delete_body_own
import ransel.composeapp.generated.resources.packing_item_delete_title
import ransel.composeapp.generated.resources.packing_item_delete
import ransel.composeapp.generated.resources.packing_item_edit
import ransel.composeapp.generated.resources.packing_item_more
import ransel.composeapp.generated.resources.ic_packing_empty
import ransel.composeapp.generated.resources.packing_add
import ransel.composeapp.generated.resources.packing_required_count
import ransel.composeapp.generated.resources.packing_complete
import ransel.composeapp.generated.resources.packing_departs
import ransel.composeapp.generated.resources.packing_empty_body
import ransel.composeapp.generated.resources.packing_empty_title
import ransel.composeapp.generated.resources.packing_menu
import ransel.composeapp.generated.resources.packing_progress_count
import ransel.composeapp.generated.resources.packing_progress_title
import ransel.composeapp.generated.resources.packing_uncheck_all
import ransel.composeapp.generated.resources.packing_tab_personal
import ransel.composeapp.generated.resources.packing_tab_shared
import ransel.composeapp.generated.resources.packing_shared_empty_title
import ransel.composeapp.generated.resources.packing_shared_empty_body
import ransel.composeapp.generated.resources.packing_only_bringer
import ransel.composeapp.generated.resources.packing_shared_title
import ransel.composeapp.generated.resources.packing_shared_count
import ransel.composeapp.generated.resources.packing_shared_mine
import ransel.composeapp.generated.resources.packing_shared_open
import ransel.composeapp.generated.resources.packing_bringer
import ransel.composeapp.generated.resources.packing_bringer_me
import ransel.composeapp.generated.resources.packing_bringer_none
import ransel.composeapp.generated.resources.packing_give_back
import ransel.composeapp.generated.resources.packing_bring
import ransel.composeapp.generated.resources.packing_bring_confirm_body
import ransel.composeapp.generated.resources.packing_bring_confirm_title
import kotlinx.coroutines.launch
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.template_added
import ransel.composeapp.generated.resources.template_apply_title
import ransel.composeapp.generated.resources.template_error
import ransel.composeapp.generated.resources.template_save_title
import ransel.composeapp.generated.resources.template_saved

/** Pribadi | Bersama in a group trip (signed in, more than one member); the plain list otherwise. */
enum class PackingSection { PERSONAL, SHARED }

@Composable
fun PackingTab(
    trip: TripDto,
    modifier: Modifier = Modifier,
    viewModel: PackingViewModel = koinViewModel(key = "packing-${trip.id}") { parametersOf(trip.id) },
) {
    val session: app.ransel.data.auth.SessionManager = org.koin.compose.koinInject()
    val signedIn = session.state.collectAsState().value is app.ransel.data.auth.SessionState.SignedIn
    if (!signedIn) {
        PackingList(trip, modifier, group = null, viewModel = viewModel)
        return
    }
    val group: GroupPackingViewModel = koinViewModel(key = "group-packing-${trip.id}") { parametersOf(trip.id) }
    val groupState by group.state.collectAsState()
    // a group trip as far as this device knows: someone else is in it, or the user was invited, or has own lines
    val isGroup = groupState.members.size > 1 || groupState.personal.isNotEmpty() || trip.myRole != app.ransel.shared.model.Role.OWNER
    if (!isGroup) {
        PackingList(trip, modifier, group = null, viewModel = viewModel)
        return
    }
    var section by rememberSaveable { mutableStateOf(PackingSection.PERSONAL) }
    Column(modifier.fillMaxSize()) {
        app.ransel.ui.components.SegmentedTabs(
            options = PackingSection.entries,
            selected = section,
            label = { stringResource(if (it == PackingSection.PERSONAL) Res.string.packing_tab_personal else Res.string.packing_tab_shared) },
            onSelect = { section = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        when (section) {
            PackingSection.PERSONAL -> PersonalPackingTab(trip, group, Modifier.weight(1f))
            PackingSection.SHARED -> PackingList(trip, Modifier.weight(1f), group = group, viewModel = viewModel)
        }
    }
}

/** The trip-wide list: the whole packing list of a solo trip, or "Bersama" when [group] is set. */
@Composable
private fun PackingList(
    trip: TripDto,
    modifier: Modifier,
    group: GroupPackingViewModel?,
    viewModel: PackingViewModel,
) {
    val state by viewModel.state.collectAsState()
    val groupState = group?.state?.collectAsState()?.value
    val myMemberId = groupState?.myMemberId
    val canEdit = trip.myRole.atLeast(app.ransel.shared.model.Role.EDITOR)
    // Templates live on the server: they need an account (guests keep the plain list).
    val session: app.ransel.data.auth.SessionManager = org.koin.compose.koinInject()
    val signedIn = session.state.collectAsState().value is app.ransel.data.auth.SessionState.SignedIn
    val canUseTemplates = canEdit && signedIn
    val onlyBringerText = stringResource(Res.string.packing_only_bringer, "%s")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val templates: PackingTemplatesViewModel =
        koinViewModel(key = "packing-templates-${trip.id}") { parametersOf(trip.id) }
    val templateState by templates.state.collectAsState()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val addedText = stringResource(Res.string.template_added)
    val savedText = stringResource(Res.string.template_saved)
    val errorText = stringResource(Res.string.template_error)
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    androidx.compose.runtime.LaunchedEffect(templates) {
        templates.eventFlow.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is TemplateEvent.Applied -> { sheet = null; snackbar.showSnackbar(addedText.replace("%1\$d", event.count.toString())) }
                TemplateEvent.Saved -> { sheet = null; snackbar.showSnackbar(savedText) }
                is TemplateEvent.Problem -> snackbar.showSnackbar(errorText)
            }
        }
    }
    val editing = state.groups.flatMap { it.items }.firstOrNull { it.id == (sheet as? SheetTarget.Edit)?.itemId }
    val today = remember { today() }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.total == 0 && group != null -> FullScreenState(
                title = stringResource(Res.string.packing_shared_empty_title),
                body = stringResource(Res.string.packing_shared_empty_body, trip.title),
                secondaryLabel = if (canUseTemplates) stringResource(Res.string.template_apply_title) else null,
                onSecondary = if (canUseTemplates) ({ templates.load(); sheet = SheetTarget.UseTemplate }) else null,
                illustration = painterResource(Res.drawable.ic_packing_empty),
            )
            state.total == 0 -> FullScreenState(
                title = stringResource(Res.string.packing_empty_title),
                body = stringResource(Res.string.packing_empty_body, trip.title),
                // The ⋮ menu is not on screen yet, so an empty list offers templates here (the main reason to use them).
                secondaryLabel = if (canUseTemplates) stringResource(Res.string.template_apply_title) else null,
                onSecondary = if (canUseTemplates) ({ templates.load(); sheet = SheetTarget.UseTemplate }) else null,
                illustration = painterResource(Res.drawable.ic_packing_empty),
            )
            else -> {
                val shared = if (groupState != null && myMemberId != null) SharedMode(
                    myMemberId = myMemberId,
                    members = groupState.members,
                    canEdit = canEdit,
                    onBring = { viewModel.bring(it, myMemberId) },
                    onGiveBack = viewModel::giveBack,
                    onDelete = viewModel::delete,
                ) else null
                PackingItems(
                    trip = trip,
                    state = state,
                    today = today,
                    shared = shared,
                    onToggle = { item ->
                        val bringer = item.assigneeMemberId
                        if (shared != null && bringer != null && bringer != myMemberId) {
                            val name = groupState?.member(bringer)?.displayName.orEmpty()
                            scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(onlyBringerText.replace("%s", name)) }
                        } else {
                            viewModel.toggle(item)
                        }
                    },
                    onEdit = { sheet = SheetTarget.Edit(it.id) },
                    onDelete = viewModel::delete,
                    onUncheckAll = viewModel::uncheckAll,
                    canEdit = canEdit,
                    onUseTemplate = if (canUseTemplates) ({ templates.load(); sheet = SheetTarget.UseTemplate }) else null,
                    onSaveTemplate = if (canUseTemplates) ({ sheet = SheetTarget.SaveTemplate }) else null,
                )
            }
        }
        if (canEdit && !state.loading) {
            IconFab(
                Symbols.add,
                label = stringResource(Res.string.packing_add),
                onClick = { sheet = SheetTarget.Add },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    androidx.compose.material3.SnackbarHost(
        hostState = snackbar,
        modifier = Modifier.padding(16.dp),
        snackbar = { data -> app.ransel.ui.components.UndoSnackbar(message = data.visuals.message) },
    )

    val allItems = state.groups.flatMap { it.items }
    when (val target = sheet) {
        SheetTarget.UseTemplate -> ApplyTemplateSheet(
            state = templateState,
            existing = allItems,
            onSelect = templates::select,
            onSkipChange = templates::setSkipExisting,
            onRetry = templates::load,
            onApply = templates::apply,
            onSaveCurrent = { sheet = SheetTarget.SaveTemplate },
            onDismiss = { sheet = null },
        )
        SheetTarget.SaveTemplate -> SaveTemplateSheet(
            defaultName = trip.title,
            items = allItems,
            busy = templateState.busy,
            onSave = templates::save,
            onDismiss = { sheet = null },
        )
        SheetTarget.Add -> AddPackingItemSheet(
            tripTitle = trip.title,
            itemCount = state.total,
            existing = null,
            onSave = { name, category, quantity, keepOpen ->
                viewModel.add(name, category, quantity)
                if (!keepOpen) sheet = null
            },
            bringers = if (group != null && groupState != null && myMemberId != null) {
                BringerChoice(groupState.members.filter { it.userId != null }, myMemberId) { name, category, quantity, bringer, keepOpen ->
                    group.addShared(name, category, quantity, bringer, (allItems.maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0)
                    if (!keepOpen) sheet = null
                }
            } else null,
            onDelete = null,
            onDismiss = { sheet = null },
        )
        is SheetTarget.Edit -> if (editing != null) {
            AddPackingItemSheet(
                tripTitle = trip.title,
                itemCount = state.total,
                existing = editing,
                onSave = { name, category, quantity, _ ->
                    viewModel.edit(editing, name, category, quantity)
                    sheet = null
                },
                onDelete = {
                    viewModel.delete(editing)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
        null -> Unit
    }
}

/** Bersama: who brings what. */
class SharedMode(
    val myMemberId: String,
    val members: List<app.ransel.shared.dto.TripMemberDto>,
    val canEdit: Boolean,
    val onBring: (PackingItemDto) -> Unit,
    val onGiveBack: (PackingItemDto) -> Unit,
    /** The ⋮ menu's "Hapus" (OWNER and EDITOR), after a confirmation. */
    val onDelete: (PackingItemDto) -> Unit = {},
)

@Composable
private fun PackingItems(
    trip: TripDto,
    state: PackingUiState,
    today: kotlinx.datetime.LocalDate,
    shared: SharedMode?,
    onToggle: (PackingItemDto) -> Unit,
    onEdit: (PackingItemDto) -> Unit,
    onDelete: (PackingItemDto) -> Unit,
    onUncheckAll: () -> Unit,
    canEdit: Boolean,
    onUseTemplate: (() -> Unit)?,
    onSaveTemplate: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (shared != null) {
            SharedSummaryCard(state, shared.myMemberId, onUseTemplate, onSaveTemplate)
        } else {
            PackingProgressCard(
                trip, today, state.packed, state.total,
                onUseTemplate = onUseTemplate, onSaveTemplate = onSaveTemplate, onUncheckAll = onUncheckAll.takeIf { canEdit },
            )
        }
        state.groups.forEach { group ->
            PackingGroupSection(group) { item ->
                if (shared != null) {
                    SharedPackingRow(item, shared, onToggle = { onToggle(item) }, onLongPress = { if (canEdit) onEdit(item) })
                } else {
                    PackingRow(
                        item, onToggle = { onToggle(item) }, onLongPress = { onEdit(item) }, enabled = canEdit,
                        menu = if (canEdit) ({ ItemMenu(item.name, ownList = true, onEdit = { onEdit(item) }, onDelete = { onDelete(item) }) }) else null,
                    )
                }
            }
        }
    }
}

/**
 * One category: a header that collapses/expands its items, and the items indented under the category
 * name (so they read as children of the header). Expanded state survives rotation, one flag per category.
 * The one group component of every packing list (owner, 27 Oct 2026): solo, Bersama and Pribadi differ only in [row].
 */
@Composable
internal fun PackingGroupSection(
    group: PackingGroup,
    row: @Composable (PackingItemDto) -> Unit,
) {
    var expanded by rememberSaveable(group.category.name) { mutableStateOf(true) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "packing-group-chevron")
    val toggleLabel = stringResource(if (expanded) Res.string.packing_collapse else Res.string.packing_expand)

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(IrisShape.md)
                .clickable(onClickLabel = toggleLabel, role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PackingCategoryIcon(group.category)
            Text(
                stringResource(group.category.label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (group.isComplete) {
                Row(
                    Modifier
                        .clip(IrisShape.xs)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Symbol(
                        Symbols.checkCircle, size = 14.dp, filled = true,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(Res.string.packing_complete),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Text(
                    "${group.packed}/${group.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Symbol(
                Symbols.expandMore,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            // Items start under the category name (tile 32 dp + gap 12 dp), with room on the right.
            Column(Modifier.padding(start = 44.dp, end = 12.dp)) {
                group.items.forEach { item -> row(item) }
            }
        }
    }
}

/**
 * "Siap dibawa": the progress of a list the user ticks (solo, Pribadi), with "Wajib x/y" when some lines are required
 * and the ⋮ menu: use or save a template, untick everything (owner, 27 Oct 2026: the same card in solo and group
 * trips). The menu shows when any action is given.
 */
@Composable
internal fun PackingProgressCard(
    trip: TripDto,
    today: kotlinx.datetime.LocalDate,
    packed: Int,
    total: Int,
    requiredDone: Int = 0,
    requiredTotal: Int = 0,
    onUseTemplate: (() -> Unit)? = null,
    onSaveTemplate: (() -> Unit)? = null,
    onUncheckAll: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val percent = if (total == 0) 0 else (packed * 100 + total / 2) / total
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, IrisShape.lg, ambientColor = Color.Black.copy(alpha = .06f), spotColor = Color.Black.copy(alpha = .06f))
            .background(scheme.surface, IrisShape.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.packing_progress_title), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.packing_progress_count, packed, total), style = MaterialTheme.typography.displaySmall)
            }
            if (requiredTotal > 0) {
                Text(
                    stringResource(Res.string.packing_required_count, requiredDone, requiredTotal),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (requiredDone == requiredTotal) scheme.primary else scheme.error,
                )
            } else {
                Text("$percent%", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            }
            PackingListMenu(onUseTemplate, onSaveTemplate, onUncheckAll, uncheckEnabled = packed > 0)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).clip(CircleShape).background(scheme.primary))
        }
        if (trip.phase(today) == TripPhase.UPCOMING && trip.startDate != null) {
            Text(
                stringResource(Res.string.packing_departs, countdownLabel(trip.daysUntilStart(today))),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The ⋮ of a packing card: "Pakai template", "Simpan sebagai template", "Hapus semua centang"; nothing when no action. */
@Composable
private fun PackingListMenu(
    onUseTemplate: (() -> Unit)?,
    onSaveTemplate: (() -> Unit)?,
    onUncheckAll: (() -> Unit)?,
    uncheckEnabled: Boolean,
) {
    if (onUseTemplate == null && onSaveTemplate == null && onUncheckAll == null) return
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(40.dp).clickableSemantics(stringResource(Res.string.packing_menu)) { menu = true },
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.moreVert, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onUseTemplate != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.template_apply_title)) },
                    leadingIcon = { Symbol(Symbols.contentCopy) },
                    onClick = { menu = false; onUseTemplate() },
                )
            }
            if (onSaveTemplate != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.template_save_title)) },
                    leadingIcon = { Symbol(Symbols.bookmarkAdd) },
                    onClick = { menu = false; onSaveTemplate() },
                )
            }
            if (onUncheckAll != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.packing_uncheck_all)) },
                    leadingIcon = { Symbol(Symbols.checklist) },
                    enabled = uncheckEnabled,
                    onClick = { menu = false; onUncheckAll() },
                )
            }
        }
    }
}

/**
 * A ticked line of a packing list (solo and Pribadi). [badge] = a small red label such as "Wajib"; [menu] = the ⋮
 * button with Ubah / Hapus ([ItemMenu]) when the user may change the line.
 */
@Composable
internal fun PackingRow(
    item: PackingItemDto,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean,
    badge: String? = null,
    menu: (@Composable () -> Unit)? = null,
    /** Replaces the check box (Bersama: a dashed box while nobody brings the item). */
    leading: (@Composable () -> Unit)? = null,
    /** A line under the name (Bersama: who brings it). */
    subtitle: (@Composable () -> Unit)? = null,
    /** Null for a row a tap does not tick. */
    role: Role? = Role.Checkbox,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(enabled = enabled, role = role, onClick = onToggle, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val packed = item.isPacked
        if (leading != null) {
            leading()
        } else {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(IrisShape.xs)
                    .then(
                        if (packed) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.outline, IrisShape.xs)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (packed) Symbol(Symbols.check, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (packed) FontWeight.Normal else FontWeight.SemiBold,
                    textDecoration = if (packed) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (packed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.invoke()
        }
        app.ransel.ui.components.PendingMarker(item.id)
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clip(IrisShape.xs).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            "×${item.quantity}",
            style = MaterialTheme.typography.titleSmall,
            color = if (packed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        menu?.invoke()
    }
}

/** Design "PackingBersama": how many items have a bringer, and how many the user brings. */
@Composable
private fun SharedSummaryCard(state: PackingUiState, myMemberId: String, onUseTemplate: (() -> Unit)?, onSaveTemplate: (() -> Unit)?) {
    val items = state.groups.flatMap { it.items }
    val claimed = items.count { it.assigneeMemberId != null }
    val mine = items.count { it.assigneeMemberId == myMemberId }
    val percent = if (items.isEmpty()) 0 else claimed * 100 / items.size
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, IrisShape.lg, ambientColor = Color.Black.copy(alpha = .06f), spotColor = Color.Black.copy(alpha = .06f))
            .background(MaterialTheme.colorScheme.surface, IrisShape.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.packing_shared_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.packing_shared_count, claimed, items.size), style = MaterialTheme.typography.titleLarge)
            }
            // templates for the shared list (owner and editors); the ticks belong to the bringers, so no "untick all"
            PackingListMenu(onUseTemplate, onSaveTemplate, onUncheckAll = null, uncheckEnabled = false)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Text(
            listOf(stringResource(Res.string.packing_shared_mine, mine), stringResource(Res.string.packing_shared_open, items.size - claimed)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A Bersama row is the same [PackingRow] as every other list (owner, 27 Oct 2026). Left: the check box once someone
 * brings the item (only the bringer ticks it), or a hand while nobody does: one tap on it is "Saya bawa". The ⋮ menu
 * is there for every role: "Saya bawa" or "Batal bawa" (the bringer, and editors on anyone's), who brings it for
 * the others, and "Ubah" / "Hapus" for editors.
 */
@Composable
private fun SharedPackingRow(item: PackingItemDto, shared: SharedMode, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bringer = item.assigneeMemberId
    val mine = bringer == shared.myMemberId
    val member = bringer?.let { id -> shared.members.firstOrNull { it.memberId == id } }
    val bringerName = member?.displayName.orEmpty()
    var confirmBring by rememberSaveable(item.id) { mutableStateOf(false) }
    PackingRow(
        item,
        // nobody brings it: a tap offers to bring it, after a confirmation (owner, 30 Oct 2026); then a tap ticks.
        // A long press edits, also before anyone brings it.
        onToggle = { if (bringer != null) onToggle() else confirmBring = true },
        onLongPress = onLongPress,
        enabled = true,
        role = if (bringer != null) Role.Checkbox else Role.Button,
        leading = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (bringer == null) {
                    // nobody brings it yet: a dashed check box, free to take
                    val dash = scheme.outlineVariant
                    Box(
                        Modifier.size(22.dp).drawBehind {
                            val stroke = 2.dp.toPx()
                            drawRoundRect(
                                color = dash,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        },
                    )
                } else {
                    val packed = item.isPacked
                    Box(
                        Modifier.size(22.dp).clip(IrisShape.xs).then(
                            when {
                                packed -> Modifier.background(if (mine) scheme.primary else scheme.outline)
                                else -> Modifier.border(2.dp, if (mine) scheme.outline else scheme.outlineVariant, IrisShape.xs)
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) { if (packed) Symbol(Symbols.check, size = 16.dp, tint = scheme.onPrimary) }
                }
            }
        },
        // who brings it; nobody yet = an empty avatar and "Belum ada yang bawa" (owner, 30 Oct 2026)
        subtitle = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bringer != null) {
                    app.ransel.ui.components.MemberAvatar(bringerName.ifEmpty { "?" }, bringer, size = 20.dp)
                } else {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.person, size = 14.dp, tint = scheme.onSurfaceVariant)
                    }
                }
                Text(
                    when {
                        bringer == null -> stringResource(Res.string.packing_bringer_none)
                        mine -> stringResource(Res.string.packing_bringer_me)
                        // just the name, "Casey Stoner" (owner, 30 Oct 2026)
                        else -> bringerName.ifEmpty { "?" }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
        menu = {
            ItemMenu(
                itemName = item.name,
                ownList = false,
                onEdit = if (shared.canEdit) onLongPress else null,
                onDelete = if (shared.canEdit) ({ shared.onDelete(item) }) else null,
            ) { close ->
                when {
                    bringer == null -> DropdownMenuItem(
                        text = { Text(stringResource(Res.string.packing_bring)) },
                        leadingIcon = { Symbol(Symbols.frontHand) },
                        onClick = { close(); shared.onBring(item) },
                    )
                    mine || shared.canEdit -> DropdownMenuItem(
                        text = { Text(stringResource(Res.string.packing_give_back)) },
                        leadingIcon = { Symbol(Symbols.close) },
                        onClick = { close(); shared.onGiveBack(item) },
                    )
                    // someone else brings it and this person cannot change that: say who, so the menu is never empty
                    else -> DropdownMenuItem(
                        text = { Text(stringResource(Res.string.packing_bringer, bringerName)) },
                        leadingIcon = { Symbol(Symbols.person) },
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        },
    )
    if (confirmBring) {
        app.ransel.ui.components.ConfirmDialog(
            title = stringResource(Res.string.packing_bring_confirm_title, item.name),
            body = stringResource(Res.string.packing_bring_confirm_body),
            confirmLabel = stringResource(Res.string.packing_bring),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = { confirmBring = false; shared.onBring(item) },
            onDismiss = { confirmBring = false },
            filledConfirm = true,
        )
    }
}

/**
 * The ⋮ button of a packing line: "Ubah" and "Hapus" (which asks first). [ownList] = a list only the user sees (solo,
 * Pribadi); otherwise the confirmation says the item leaves the shared list for everyone.
 */
@Composable
internal fun ItemMenu(
    itemName: String,
    ownList: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** Items above Ubah / Hapus (Bersama: Saya bawa, Batal bawa); gets a function that closes the menu. */
    extra: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            Symbol(Symbols.moreVert, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.packing_item_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            extra?.invoke(this) { open = false }
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.packing_item_edit)) },
                    leadingIcon = { Symbol(Symbols.edit) },
                    onClick = { open = false; onEdit() },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.packing_item_delete), color = scheme.error) },
                    leadingIcon = { Symbol(Symbols.delete, tint = scheme.error) },
                    onClick = { open = false; confirmDelete = true },
                )
            }
        }
    }
    if (confirmDelete) {
        app.ransel.ui.components.ConfirmDialog(
            title = stringResource(Res.string.packing_item_delete_title, itemName),
            body = stringResource(if (ownList) Res.string.packing_item_delete_body_own else Res.string.packing_item_delete_body),
            confirmLabel = stringResource(Res.string.packing_item_delete_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = { confirmDelete = false; onDelete?.invoke() },
            onDismiss = { confirmDelete = false },
            destructive = true,
            symbol = Symbols.delete,
        )
    }
}

/** What the add/edit sheet is showing. */
sealed interface SheetTarget {
    data object Add : SheetTarget
    data object UseTemplate : SheetTarget
    data object SaveTemplate : SheetTarget
    data class Edit(val itemId: String) : SheetTarget
}

@Preview
@Composable
private fun SharedPackingRowPreview() = IrisPreview {
    val stamp = kotlin.time.Instant.parse("2026-12-01T00:00:00Z")
    val members = listOf(
        app.ransel.shared.dto.TripMemberDto("t", "r", "r", "Raka", role = app.ransel.shared.model.Role.EDITOR, joinedAt = stamp),
    )
    val mode = SharedMode("me", members, canEdit = true, onBring = {}, onGiveBack = {})
    fun item(id: String, name: String, bringer: String?, packed: Boolean = false) =
        PackingItemDto(id, "t", name = name, quantity = 2, isPacked = packed, assigneeMemberId = bringer, createdAt = stamp, updatedAt = stamp)
    Column(Modifier.padding(16.dp)) {
        SharedPackingRow(item("1", "Snorkel set", null), mode, {}, {})
        SharedPackingRow(item("2", "Speaker portable", "r"), mode, {}, {})
        SharedPackingRow(item("3", "Colokan T", "me", packed = true), mode, {}, {})
    }
}
