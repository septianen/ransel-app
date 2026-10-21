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
import ransel.composeapp.generated.resources.ic_packing_empty
import ransel.composeapp.generated.resources.packing_add
import ransel.composeapp.generated.resources.packing_complete
import ransel.composeapp.generated.resources.packing_departs
import ransel.composeapp.generated.resources.packing_empty_body
import ransel.composeapp.generated.resources.packing_empty_title
import ransel.composeapp.generated.resources.packing_menu
import ransel.composeapp.generated.resources.packing_progress_count
import ransel.composeapp.generated.resources.packing_progress_title
import ransel.composeapp.generated.resources.packing_uncheck_all
import ransel.composeapp.generated.resources.template_added
import ransel.composeapp.generated.resources.template_apply_title
import ransel.composeapp.generated.resources.template_error
import ransel.composeapp.generated.resources.template_save_title
import ransel.composeapp.generated.resources.template_saved

@Composable
fun PackingTab(
    trip: TripDto,
    modifier: Modifier = Modifier,
    viewModel: PackingViewModel = koinViewModel(key = "packing-${trip.id}") { parametersOf(trip.id) },
) {
    val state by viewModel.state.collectAsState()
    val canEdit = trip.myRole.atLeast(app.ransel.shared.model.Role.EDITOR)
    // Templates live on the server: they need an account (guests keep the plain list).
    val session: app.ransel.data.auth.SessionManager = org.koin.compose.koinInject()
    val signedIn = session.state.collectAsState().value is app.ransel.data.auth.SessionState.SignedIn
    val canUseTemplates = canEdit && signedIn
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
            state.total == 0 -> FullScreenState(
                title = stringResource(Res.string.packing_empty_title),
                body = stringResource(Res.string.packing_empty_body, trip.title),
                // The ⋮ menu is not on screen yet, so an empty list offers templates here (the main reason to use them).
                secondaryLabel = if (canUseTemplates) stringResource(Res.string.template_apply_title) else null,
                onSecondary = if (canUseTemplates) ({ templates.load(); sheet = SheetTarget.UseTemplate }) else null,
                illustration = painterResource(Res.drawable.ic_packing_empty),
            )
            else -> {
                PackingList(
                    trip = trip,
                    state = state,
                    today = today,
                    onToggle = viewModel::toggle,
                    onEdit = { sheet = SheetTarget.Edit(it.id) },
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

@Composable
private fun PackingList(
    trip: TripDto,
    state: PackingUiState,
    today: kotlinx.datetime.LocalDate,
    onToggle: (PackingItemDto) -> Unit,
    onEdit: (PackingItemDto) -> Unit,
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
        ProgressCard(trip, state, today, onUncheckAll, canEdit, onUseTemplate, onSaveTemplate)
        state.groups.forEach { group ->
            PackingGroupSection(group, onToggle = onToggle, onEdit = onEdit, canEdit = canEdit)
        }
    }
}

/**
 * One category: a header that collapses/expands its items, and the items indented under the category
 * name (so they read as children of the header). Expanded state survives rotation, one flag per category.
 */
@Composable
private fun PackingGroupSection(
    group: PackingGroup,
    onToggle: (PackingItemDto) -> Unit,
    onEdit: (PackingItemDto) -> Unit,
    canEdit: Boolean,
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
                group.items.forEach { item ->
                    PackingRow(item, onToggle = { onToggle(item) }, onLongPress = { onEdit(item) }, enabled = canEdit)
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    trip: TripDto,
    state: PackingUiState,
    today: kotlinx.datetime.LocalDate,
    onUncheckAll: () -> Unit,
    canEdit: Boolean,
    onUseTemplate: (() -> Unit)?,
    onSaveTemplate: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, IrisShape.lg, ambientColor = Color.Black.copy(alpha = .06f), spotColor = Color.Black.copy(alpha = .06f))
            .background(MaterialTheme.colorScheme.surface, IrisShape.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.packing_progress_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.packing_progress_count, state.packed, state.total),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            Text(
                "${state.percent}%",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (canEdit) Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clickableSemantics(stringResource(Res.string.packing_menu)) { menu = true },
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
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.packing_uncheck_all)) },
                        leadingIcon = { Symbol(Symbols.checklist) },
                        enabled = state.packed > 0,
                        onClick = {
                            menu = false
                            onUncheckAll()
                        },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(
                Modifier
                    .fillMaxWidth(state.percent / 100f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (trip.phase(today) == TripPhase.UPCOMING) {
            Text(
                stringResource(Res.string.packing_departs, countdownLabel(trip.daysUntilStart(today))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PackingRow(item: PackingItemDto, onToggle: () -> Unit, onLongPress: () -> Unit, enabled: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val packed = item.isPacked
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
        Text(
            item.name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (packed) FontWeight.Normal else FontWeight.SemiBold,
                textDecoration = if (packed) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (packed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        app.ransel.ui.components.PendingMarker(item.id)
        Text(
            "×${item.quantity}",
            style = MaterialTheme.typography.titleSmall,
            color = if (packed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
