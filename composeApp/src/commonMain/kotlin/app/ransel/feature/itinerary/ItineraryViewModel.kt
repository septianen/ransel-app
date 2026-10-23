package app.ransel.feature.itinerary

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.domain.Slot
import app.ransel.shared.domain.activityDurationMinutes
import app.ransel.shared.domain.changedSlots
import app.ransel.shared.domain.reorder
import app.ransel.shared.domain.scheduleAtEndOfDay
import app.ransel.shared.dto.DayOrderItem
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.TripDto
import app.ransel.util.plusDays
import app.ransel.util.today
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil

/** Everything the itinerary needs for a trip: days, the items of each day and the unscheduled box. */
data class ItineraryUiState(
    val days: List<LocalDate> = emptyList(),
    val selectedDay: LocalDate? = null,
    val itemsByDay: Map<LocalDate, List<ItineraryItemDto>> = emptyMap(),
    val unscheduled: List<ItineraryItemDto> = emptyList(),
    val skipReorderConfirmation: Boolean = false,
    val pendingReorder: PendingReorder? = null,
    val loading: Boolean = true,
) {
    val selectedIndex: Int get() = days.indexOf(selectedDay).coerceAtLeast(0)
    val selectedItems: List<ItineraryItemDto> get() = selectedDay?.let { itemsByDay[it] }.orEmpty()
}

/** A drag that changes times and waits for the user's OK ("Ubah urutan dan jam?"). */
data class PendingReorder(
    val day: LocalDate,
    val dayNumber: Int,
    val from: Int,
    val to: Int,
    val before: List<ItineraryItemDto>,
    val after: List<Slot>,
    /** (item, oldStart → newStart) for items whose time changes. */
    val changes: List<ReorderChange>,
    val unchangedTitles: List<String>,
    val packedFrom: LocalTime?,
)

data class ReorderChange(val item: ItineraryItemDto, val oldStart: LocalTime?, val newStart: LocalTime?)

sealed interface ItineraryEvent {
    /** "Jadwal Hari 1 dirapatkan" + Undo. [restore] is the day order to send back. */
    data class Reordered(val dayNumber: Int, val day: LocalDate, val restore: List<DayOrderItem>) : ItineraryEvent
    data class MovedToDay(val dayNumber: Int, val restore: ItineraryItemInput) : ItineraryEvent
}

class ItineraryViewModel(
    private val trip: TripDto,
    private val itinerary: ItineraryRepository,
    private val preferences: PreferencesRepository,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val pending = MutableStateFlow<PendingReorder?>(null)
    private val events = Channel<ItineraryEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val days: List<LocalDate> =
        (0..trip.startDate.daysUntil(trip.endDate)).map { trip.startDate.plusDays(it) }

    val state: StateFlow<ItineraryUiState> = combine(
        itinerary.observe(trip.id),
        selectedDay,
        pending,
        preferences.observe(),
    ) { items, selected, pendingReorder, prefs ->
        val scheduled = items.filter { it.dayDate != null }
        val byDay = days.associateWith { day ->
            scheduled.filter { it.dayDate == day }.sortedWith(compareBy({ it.sortOrder }, { it.startTime }))
        }
        val chosen = selected?.takeIf { it in days } ?: today().takeIf { it in days } ?: days.first()
        ItineraryUiState(
            days = days,
            selectedDay = chosen,
            itemsByDay = byDay,
            unscheduled = items.filter { it.dayDate == null },
            skipReorderConfirmation = prefs.skipReorderConfirmation,
            pendingReorder = pendingReorder,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItineraryUiState(days = days))

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun dayNumber(day: LocalDate): Int = days.indexOf(day) + 1

    // ── Reorder (drag within a day) ──────────────────────────────────────────

    fun onReorder(day: LocalDate, from: Int, to: Int) {
        if (from == to) return
        val items = state.value.itemsByDay[day].orEmpty()
        if (from !in items.indices || to !in items.indices) return
        val before = items.map { Slot(it.id, it.startTime, it.endTime) }
        val after = reorder(before, from, to)
        val changed = changedSlots(before, after)
        val byId = items.associateBy { it.id }
        val changes = changed.map { (old, new) -> ReorderChange(byId.getValue(new.id), old.start, new.start) }
        val request = PendingReorder(
            day = day,
            dayNumber = dayNumber(day),
            from = from,
            to = to,
            before = items,
            after = after,
            changes = changes,
            unchangedTitles = items.filter { item -> changes.none { it.item.id == item.id } }.map { it.title },
            packedFrom = items.subList(minOf(from, to), maxOf(from, to) + 1).mapNotNull { it.startTime }.minOrNull(),
        )
        when {
            changes.isEmpty() -> apply(request, announce = false) // order only, nothing to confirm
            state.value.skipReorderConfirmation -> apply(request, announce = true)
            else -> pending.value = request
        }
    }

    fun confirmReorder(dontAskAgain: Boolean) {
        val request = pending.value ?: return
        pending.value = null
        viewModelScope.launch {
            if (dontAskAgain) preferences.setSkipReorderConfirmation(true)
        }
        apply(request, announce = true)
    }

    fun cancelReorder() {
        pending.value = null
    }

    private fun apply(request: PendingReorder, announce: Boolean) {
        val restore = request.before.map { DayOrderItem(it.id, it.sortOrder, it.startTime, it.endTime) }
        val order = request.after.mapIndexed { index, slot ->
            DayOrderItem(slot.id, sortOrder = index + 1.0, startTime = slot.start, endTime = slot.end)
        }
        viewModelScope.launch {
            itinerary.applyDayOrder(trip.id, request.day, order)
            if (announce) events.send(ItineraryEvent.Reordered(request.dayNumber, request.day, restore))
        }
    }

    fun undoReorder(day: LocalDate, restore: List<DayOrderItem>) {
        viewModelScope.launch { itinerary.applyDayOrder(trip.id, day, restore) }
    }

    // ── Move to another day (drop on a day chip) ─────────────────────────────

    fun moveToDay(itemId: String, target: LocalDate) {
        val item = state.value.itemsByDay.values.flatten().firstOrNull { it.id == itemId } ?: return
        if (item.dayDate == target) return
        val targetItems = state.value.itemsByDay[target].orEmpty()
        val duration = durationOf(item)
        val timed = item.startTime != null
        val slots = targetItems.map { Slot(it.id, it.startTime, it.endTime) }
        // End of the target day; an empty day keeps the activity's own start time. Untimed stays untimed.
        val scheduled = if (timed) {
            scheduleAtEndOfDay(slots, duration, emptyDayStart = item.startTime!!)
        } else {
            null
        }
        val lastOrder = targetItems.maxOfOrNull { it.sortOrder } ?: 0.0
        val moved = item.toInput().copy(
            dayDate = target,
            startTime = scheduled?.start,
            endTime = if (item.endTime != null) scheduled?.end else null,
            sortOrder = lastOrder + 1.0,
        )
        viewModelScope.launch {
            itinerary.save(trip.id, moved)
            events.send(ItineraryEvent.MovedToDay(dayNumber(target), item.toInput()))
        }
    }

    fun restore(input: ItineraryItemInput) {
        viewModelScope.launch { itinerary.save(trip.id, input) }
    }

    // ── Unscheduled places ───────────────────────────────────────────────────

    /** Where a place would land if scheduled on [day] (end of the day, 09:00 when empty). */
    fun previewSchedule(item: ItineraryItemDto, day: LocalDate): app.ransel.shared.domain.ScheduledTime {
        val slots = state.value.itemsByDay[day].orEmpty().map { Slot(it.id, it.startTime, it.endTime) }
        return scheduleAtEndOfDay(slots, durationOf(item))
    }

    fun schedule(item: ItineraryItemDto, day: LocalDate, start: LocalTime, end: LocalTime) {
        // "Ubah" may pick a time earlier than the day's last activity: keep the day chronological.
        val order = sortOrderForStart(state.value.itemsByDay[day].orEmpty(), start)
        viewModelScope.launch {
            itinerary.save(
                trip.id,
                item.toInput().copy(dayDate = day, startTime = start, endTime = end, sortOrder = order),
            )
            if (item.dayDate == null) analytics.event(AnalyticsEvent.ActivityScheduled)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { itinerary.delete(trip.id, itemId) }
    }

    private fun durationOf(item: ItineraryItemDto): Int? =
        item.durationMin ?: activityDurationMinutes(item.startTime, item.endTime)?.takeIf { it > 0 }
}

fun ItineraryItemDto.toInput() = ItineraryItemInput(
    id = id, dayDate = dayDate, startTime = startTime, endTime = endTime, title = title, category = category,
    placeName = placeName, placeId = placeId, lat = lat, lng = lng, durationMin = durationMin, notes = notes,
    bookingUrl = bookingUrl, source = source, sortOrder = sortOrder,
)
