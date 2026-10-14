package app.ransel.feature.itinerary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.Slot
import app.ransel.shared.domain.reorder
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.ui.category.itineraryLabel
import app.ransel.ui.components.CategoryIcon
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.format.DateFormatter
import app.ransel.ui.format.formatDurationApprox
import app.ransel.ui.format.formatDurationShort
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.format.durationMinutes
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_undo
import ransel.composeapp.generated.resources.capybara_world_map
import ransel.composeapp.generated.resources.itinerary_add_activity
import ransel.composeapp.generated.resources.itinerary_day_chip
import ransel.composeapp.generated.resources.itinerary_day_heading
import ransel.composeapp.generated.resources.itinerary_drag_hint
import ransel.composeapp.generated.resources.itinerary_drag_start
import ransel.composeapp.generated.resources.itinerary_empty_body
import ransel.composeapp.generated.resources.itinerary_empty_title
import ransel.composeapp.generated.resources.itinerary_schedule
import ransel.composeapp.generated.resources.itinerary_unscheduled
import ransel.composeapp.generated.resources.itinerary_unscheduled_see_all
import ransel.composeapp.generated.resources.list_and
import ransel.composeapp.generated.resources.reorder_body
import ransel.composeapp.generated.resources.reorder_confirm
import ransel.composeapp.generated.resources.reorder_dont_ask
import ransel.composeapp.generated.resources.reorder_done
import ransel.composeapp.generated.resources.reorder_moved
import ransel.composeapp.generated.resources.reorder_title
import ransel.composeapp.generated.resources.reorder_unchanged

/** Itinerary tab of a trip: day chips, the day's timeline (drag to reorder), Unscheduled box, FAB. */
@Composable
fun ItineraryTab(
    trip: TripDto,
    onAddActivity: (dayDate: String?) -> Unit,
    onEditActivity: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItineraryViewModel = koinViewModel(key = "itinerary-${trip.id}") { parametersOf(trip) },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(Res.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is ItineraryEvent.Reordered -> {
                    val result = snackbar.showSnackbar(
                        message = getString(Res.string.reorder_done, event.dayNumber),
                        actionLabel = undoLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoReorder(event.day, event.restore)
                }
                is ItineraryEvent.MovedToDay -> {
                    val result = snackbar.showSnackbar(
                        message = getString(Res.string.reorder_moved, event.dayNumber),
                        actionLabel = undoLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.restore)
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        ItineraryContent(
            state = state,
            onSelectDay = viewModel::selectDay,
            onReorder = viewModel::onReorder,
            onMoveToDay = viewModel::moveToDay,
            onAddActivity = { onAddActivity(state.selectedDay?.toString()) },
            onEditActivity = onEditActivity,
            viewModel = viewModel,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            snackbar = { data ->
                app.ransel.ui.components.UndoSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel,
                    onAction = { data.performAction() },
                )
            },
        )
    }

    state.pendingReorder?.let { request ->
        ReorderDialog(
            request = request,
            onConfirm = viewModel::confirmReorder,
            onDismiss = viewModel::cancelReorder,
        )
    }
}

@Composable
private fun ItineraryContent(
    state: ItineraryUiState,
    onSelectDay: (LocalDate) -> Unit,
    onReorder: (LocalDate, Int, Int) -> Unit,
    onMoveToDay: (String, LocalDate) -> Unit,
    onAddActivity: () -> Unit,
    onEditActivity: (String) -> Unit,
    viewModel: ItineraryViewModel,
) {
    val dates = rememberDateFormatter()
    val drag = remember(state.selectedDay) { DayDragState() }
    var showUnscheduled by rememberSaveable { mutableStateOf(false) }
    var scheduleItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val day = state.selectedDay
    val items = state.selectedItems
    val ids = items.map { it.id }

    val from = ids.indexOf(drag.draggingId)
    val hovered = drag.hoveredChip
    val target = if (from >= 0 && hovered == null) drag.targetIndex(ids).coerceIn(0, items.lastIndex.coerceAtLeast(0)) else from
    val preview = if (from >= 0 && hovered == null && target != from) {
        reorder(items.map { Slot(it.id, it.startTime, it.endTime) }, from, target).associateBy { it.id }
    } else {
        null
    }

    fun finishDrag() {
        // Read everything from the live drag state: this function may be called from a gesture that
        // started before the latest recomposition.
        val dragged = drag.draggingId
        val chip = drag.hoveredChip
        val fromIndex = ids.indexOf(dragged)
        val destination = if (fromIndex >= 0 && chip == null) drag.targetIndex(ids) else -1
        drag.finish()
        if (dragged == null || day == null) return
        when {
            chip != null -> state.days.getOrNull(chip)?.let { if (it != day) onMoveToDay(dragged, it) }
            destination >= 0 && destination != fromIndex -> onReorder(day, fromIndex, destination)
        }
    }

    Column(Modifier.fillMaxSize()) {
        DayChips(state, dates, drag, onSelectDay)
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = !drag.blocksScroll)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (state.unscheduled.isNotEmpty()) {
                    UnscheduledBox(
                        items = state.unscheduled,
                        onSeeAll = { showUnscheduled = true },
                        onSchedule = { scheduleItemId = it.id },
                    )
                }
                if (drag.isDragging) {
                    DragHint()
                }
                if (day != null) {
                    DaySection(
                        heading = stringResource(
                            Res.string.itinerary_day_heading, state.selectedIndex + 1, dates.fullWeekdayDayMonth(day),
                        ),
                        items = items,
                        drag = drag,
                        previewSlots = preview,
                        target = target,
                        from = from,
                        onFinish = ::finishDrag,
                        onEdit = onEditActivity,
                        onAddActivity = onAddActivity,
                        emptyTitle = stringResource(Res.string.itinerary_empty_title, state.selectedIndex + 1),
                    )
                }
            }
            if (items.isNotEmpty() && !drag.isDragging) {
                AddActivityFab(onAddActivity, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
        }
    }

    if (showUnscheduled) {
        UnscheduledPlacesSheet(
            items = state.unscheduled,
            onDismiss = { showUnscheduled = false },
            onSchedule = {
                showUnscheduled = false
                scheduleItemId = it.id
            },
            onDelete = { viewModel.deleteItem(it.id) },
        )
    }
    val toSchedule = state.unscheduled.firstOrNull { it.id == scheduleItemId }
    if (toSchedule != null) {
        SchedulePlaceSheet(
            item = toSchedule,
            state = state,
            preview = { d -> viewModel.previewSchedule(toSchedule, d) },
            onSchedule = { d, start, end ->
                viewModel.schedule(toSchedule, d, start, end)
                scheduleItemId = null
            },
            onDelete = {
                viewModel.deleteItem(toSchedule.id)
                scheduleItemId = null
            },
            onDismiss = { scheduleItemId = null },
        )
    }
}

// ── Day chips ────────────────────────────────────────────────────────────────

@Composable
private fun DayChips(
    state: ItineraryUiState,
    dates: DateFormatter,
    drag: DayDragState,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.days.forEachIndexed { index, date ->
            val selected = date == state.selectedDay
            val drop = drag.hoveredChip == index
            val container = if (selected || drop) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            val borderColor = if (selected || drop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            val content = if (selected || drop) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier
                    .height(56.dp)
                    .clip(IrisShape.lg)
                    .background(container)
                    .border(BorderStroke(if (drop) 2.dp else 1.dp, borderColor), IrisShape.lg)
                    .clickable { onSelectDay(date) }
                    .padding(horizontal = 16.dp)
                    .onGloballyPositioned { drag.chipBounds[index] = it.boundsInRoot() },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.itinerary_day_chip, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
                Text(dates.dayMonth(date), style = MaterialTheme.typography.bodySmall, color = content)
            }
        }
    }
}

@Composable
private fun DragHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.md)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(Symbols.swipeVertical, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(Res.string.itinerary_drag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── A day's timeline ─────────────────────────────────────────────────────────

@Composable
private fun DaySection(
    heading: String,
    items: List<ItineraryItemDto>,
    drag: DayDragState,
    previewSlots: Map<String, Slot>?,
    target: Int,
    from: Int,
    onFinish: () -> Unit,
    onEdit: (String) -> Unit,
    onAddActivity: () -> Unit,
    emptyTitle: String,
) {
    val finish by rememberUpdatedState(onFinish)
    val itemIds by rememberUpdatedState(items.map { it.id })
    var containerRoot by remember { mutableStateOf(Offset.Zero) }
    val handleZone = with(LocalDensity.current) { 56.dp.toPx() }
    Column(
        Modifier
            .onGloballyPositioned { containerRoot = it.positionInRoot() }
            .pointerInput(drag) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val rootPos = containerRoot + down.position
                    val hit = itemIds.firstOrNull { drag.rowBounds[it]?.contains(rootPos) == true }
                        ?: return@awaitEachGesture
                    val bounds = drag.rowBounds.getValue(hit)
                    val started: Boolean
                    var overshoot = Offset.Zero
                    if (rootPos.x >= bounds.right - handleZone) {
                        // handle: drag as soon as the touch slop is crossed
                        drag.armed = true
                        started = awaitTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            overshoot = over
                        } != null
                    } else {
                        started = awaitLongPressOrCancellation(down.id) != null
                    }
                    if (!started) {
                        drag.armed = false
                        return@awaitEachGesture
                    }
                    drag.start(hit, rootPos - bounds.topLeft)
                    // the movement that crossed the slop already happened: do not lose it
                    if (overshoot != Offset.Zero) drag.dragBy(overshoot.x, overshoot.y)
                    val completed = drag(down.id) { change ->
                        val delta = change.positionChange()
                        change.consume()
                        drag.dragBy(delta.x, delta.y)
                    }
                    if (completed) finish() else drag.finish()
                }
            },
    ) {
        Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        if (items.isEmpty()) {
            EmptyDay(title = emptyTitle, onAddActivity = onAddActivity)
            return@Column
        }
        items.forEachIndexed { index, item ->
            val isDragging = item.id == drag.draggingId
            val shift = when {
                from < 0 || isDragging || drag.hoveredChip != null -> 0
                from < index && index <= target -> -1
                target <= index && index < from -> 1
                else -> 0
            }
            val height = drag.rowBounds[drag.draggingId]?.height ?: 0f
            val animatedShift by animateFloatAsState(shift * height, label = "row-shift")
            val slot = previewSlots?.get(item.id)
            TimelineRow(
                item = item,
                isLast = index == items.lastIndex,
                shownStart = slot?.start ?: item.startTime,
                shownEnd = slot?.end ?: item.endTime,
                dragging = isDragging,
                showDuration = drag.isDragging,
                translationY = if (isDragging) drag.deltaY else animatedShift,
                drag = drag,
                onClick = { onEdit(item.id) },
            )
        }
    }
}

@Composable
private fun TimelineRow(
    item: ItineraryItemDto,
    isLast: Boolean,
    shownStart: kotlinx.datetime.LocalTime?,
    shownEnd: kotlinx.datetime.LocalTime?,
    dragging: Boolean,
    showDuration: Boolean,
    translationY: Float,
    drag: DayDragState,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val minutes = durationMinutes(shownStart, shownEnd) ?: item.durationMin
    val label = stringResource(item.category.itineraryLabel)
    val subtitle = when {
        dragging && shownStart != null && minutes != null ->
            stringResource(Res.string.itinerary_drag_start, formatTime(shownStart), formatDurationShort(minutes))
        showDuration && minutes != null -> "$label · ${formatDurationShort(minutes)}"
        else -> item.placeName?.takeIf { it.isNotBlank() } ?: label
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { this.translationY = translationY }
            .onGloballyPositioned { if (!drag.isDragging) drag.rowBounds[item.id] = it.boundsInRoot() },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Time column: start time and the line to the next activity.
        Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = shownStart?.let { formatTime(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (dragging || (drag.isDragging && shownStart != item.startTime)) primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Row(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
                .then(
                    if (dragging) {
                        Modifier
                            .clip(IrisShape.lg)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(2.dp, primary), IrisShape.lg)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryIcon(item.category)
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dragging) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Handle: touching it drags immediately; the rest of the row needs a long press.
            // Gestures are detected by the container in DaySection (this row moves while dragging).
            Box(Modifier.size(48.dp).testTag("drag-handle-${item.id}"), contentAlignment = Alignment.Center) {
                Symbol(Symbols.dragIndicator, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun EmptyDay(title: String, onAddActivity: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(Res.drawable.capybara_world_map), contentDescription = null, modifier = Modifier.size(180.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            stringResource(Res.string.itinerary_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddActivity, modifier = Modifier.heightIn(min = 48.dp)) {
            Symbol(Symbols.add, tint = MaterialTheme.colorScheme.onPrimary)
            Text(
                stringResource(Res.string.itinerary_add_activity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AddActivityFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.add, tint = MaterialTheme.colorScheme.onPrimary)
        Text(
            stringResource(Res.string.itinerary_add_activity),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

// ── Unscheduled box ──────────────────────────────────────────────────────────

@Composable
private fun UnscheduledBox(
    items: List<ItineraryItemDto>,
    onSeeAll: () -> Unit,
    onSchedule: (ItineraryItemDto) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .dashedBorder(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.itinerary_unscheduled), style = MaterialTheme.typography.titleSmall)
            Box(
                Modifier
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Box(Modifier.weight(1f))
            Text(
                stringResource(Res.string.itinerary_unscheduled_see_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = 32.dp).clickable(onClick = onSeeAll).padding(vertical = 6.dp),
            )
        }
        items.take(2).forEach { item ->
            UnscheduledRow(item, onSchedule = { onSchedule(item) })
        }
    }
}

@Composable
internal fun UnscheduledRow(
    item: ItineraryItemDto,
    onSchedule: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(item.category)
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val ai = item.source == app.ransel.shared.model.ItemSource.AI
                Symbol(
                    if (ai) Symbols.autoAwesome else Symbols.edit,
                    size = 14.dp,
                    filled = ai,
                    tint = if (ai) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val label = stringResource(item.category.itineraryLabel)
                Text(
                    item.durationMin?.let { "$label · ${formatDurationApprox(it)}" } ?: label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        OutlinedButton(
            onClick = onSchedule,
            modifier = Modifier.heightIn(min = 36.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) { Text(stringResource(Res.string.itinerary_schedule), style = MaterialTheme.typography.labelLarge) }
        trailing?.invoke()
    }
}

// ── Reorder confirmation ─────────────────────────────────────────────────────

@Composable
private fun ReorderDialog(
    request: PendingReorder,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontAsk by remember { mutableStateOf(false) }
    ConfirmDialog(
        title = stringResource(Res.string.reorder_title),
        body = stringResource(
            Res.string.reorder_body,
            request.dayNumber,
            request.packedFrom?.let { formatTime(it) }.orEmpty(),
        ),
        confirmLabel = stringResource(Res.string.reorder_confirm),
        dismissLabel = stringResource(Res.string.action_cancel),
        onConfirm = { onConfirm(dontAsk) },
        onDismiss = onDismiss,
        symbol = Symbols.schedule,
        extra = {
            request.changes.forEach { change ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryIcon(change.item.category)
                    Text(
                        change.item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    change.oldStart?.let {
                        Text(
                            formatTime(it),
                            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Symbol(Symbols.arrowForward, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    change.newStart?.let {
                        Text(
                            formatTime(it),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (request.unchangedTitles.isNotEmpty()) {
                Text(
                    stringResource(Res.string.reorder_unchanged, joinNames(request.unchangedTitles)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { dontAsk = !dontAsk },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = dontAsk,
                    onCheckedChange = { dontAsk = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
                Text(stringResource(Res.string.reorder_dont_ask), style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
private fun joinNames(names: List<String>): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    val and = stringResource(Res.string.list_and)
    return names.dropLast(1).joinToString(", ") + " $and " + names.last()
}
