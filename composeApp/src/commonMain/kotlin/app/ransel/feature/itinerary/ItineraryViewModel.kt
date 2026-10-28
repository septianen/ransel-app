package app.ransel.feature.itinerary

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.domain.Slot
import app.ransel.shared.domain.activityDurationMinutes
import app.ransel.shared.domain.changedSlots
import app.ransel.shared.domain.reorder
import app.ransel.shared.domain.AutoDay
import app.ransel.shared.domain.AutoExisting
import app.ransel.shared.domain.AutoPlace
import app.ransel.shared.domain.AutoScheduleResult
import app.ransel.shared.domain.autoSchedule
import app.ransel.shared.domain.scheduleAtEndOfDay
import app.ransel.shared.dto.DayOrderItem
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.PollKind
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
    /** "Jadwalkan otomatis" is saving: the dialog shows progress and ignores taps. */
    val autoScheduling: Boolean = false,
    val loading: Boolean = true,
    /**
     * The trip has no dates yet ("Tanggal belum pasti", design ItineraryPolling): no days, no day chips, no scheduling;
     * everything goes to "Belum dijadwalkan".
     */
    val undated: Boolean = false,
    /** The open DATE poll of an undated trip (signed in, online); null = none or not known. */
    val datePoll: PollDto? = null,
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

/** What "Jadwalkan otomatis" would do, for its confirmation dialog. */
data class AutoPlan(val result: AutoScheduleResult, val dayNumbers: List<Int>) {
    val count: Int get() = result.scheduled.size
    val leftOver: Int get() = result.leftOver.size
}

data class ReorderChange(val item: ItineraryItemDto, val oldStart: LocalTime?, val newStart: LocalTime?)

sealed interface ItineraryEvent {
    /** "Jadwal Hari 1 dirapatkan" + Undo. [restore] is the day order to send back. */
    data class Reordered(val dayNumber: Int, val day: LocalDate, val restore: List<DayOrderItem>) : ItineraryEvent
    data class MovedToDay(val dayNumber: Int, val restore: ItineraryItemInput) : ItineraryEvent
    /** "5 tempat dijadwalkan" + Undo. [restore] are the places as they were (unscheduled). */
    data class AutoScheduled(val count: Int, val leftOver: Int, val restore: List<ItineraryItemInput>) : ItineraryEvent
}

class ItineraryViewModel(
    private val trip: TripDto,
    private val itinerary: ItineraryRepository,
    private val preferences: PreferencesRepository,
    private val analytics: Analytics = NoAnalytics,
    /** Polls are server data: the open DATE poll of an undated trip is asked for when signed in (null = never). */
    private val api: RanselApi? = null,
    private val session: SessionManager? = null,
) : ViewModel() {
    private val datePoll = MutableStateFlow<PollDto?>(null)
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val pending = MutableStateFlow<PendingReorder?>(null)
    private val autoRunning = MutableStateFlow(false)
    private val events = Channel<ItineraryEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** The trip's days; none for a trip without dates. */
    private val days: List<LocalDate> = run {
        val start = trip.startDate ?: return@run emptyList()
        val end = trip.endDate ?: return@run emptyList()
        (0..start.daysUntil(end)).map { start.plusDays(it) }
    }
    private val undated = days.isEmpty()

    val state: StateFlow<ItineraryUiState> = combine(
        combine(itinerary.observe(trip.id), datePoll, ::Pair),
        selectedDay,
        pending,
        preferences.observe(),
        autoRunning,
    ) { (items, poll), selected, pendingReorder, prefs, auto ->
        val scheduled = items.filter { it.dayDate != null }
        val byDay = days.associateWith { day ->
            scheduled.filter { it.dayDate == day }.sortedWith(compareBy({ it.sortOrder }, { it.startTime }))
        }
        val chosen = selected?.takeIf { it in days } ?: today().takeIf { it in days } ?: days.firstOrNull()
        ItineraryUiState(
            days = days,
            selectedDay = chosen,
            itemsByDay = byDay,
            unscheduled = items.filter { it.dayDate == null },
            skipReorderConfirmation = prefs.skipReorderConfirmation,
            pendingReorder = pendingReorder,
            autoScheduling = auto,
            loading = false,
            undated = undated,
            datePoll = poll,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItineraryUiState(days = days, undated = undated))

    init {
        if (undated) loadDatePoll()
    }

    /** My member id in this trip (signed in), to tell my own date poll from someone else's. */
    val myMemberId: String? get() = session?.user?.id?.let { app.ransel.shared.domain.memberIdFor(trip.id, it) }

    /** The newest open DATE poll, for the card above "Belum dijadwalkan"; quietly nothing when offline or a guest. */
    fun loadDatePoll() {
        val api = api ?: return
        if (session?.user == null) return
        viewModelScope.launch {
            val polls = (api.polls(trip.id) as? ApiResult.Success)?.value ?: return@launch
            datePoll.value = polls.filter { it.kind == PollKind.DATE && it.isOpen }.maxByOrNull { it.createdAt }
        }
    }

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
        if (day !in days) return
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

    // ── "Jadwalkan otomatis" ─────────────────────────────────────────────────

    /** Days that can still take places: all of them before the trip, from today on while it runs. */
    private fun autoDays(s: ItineraryUiState): List<AutoDay> {
        val open = days.filter { it >= today() }.ifEmpty { days }
        return open.map { d ->
            AutoDay(
                d,
                s.itemsByDay[d].orEmpty().map { item ->
                    val start = item.startTime?.let { it.hour * 60 + it.minute }
                    val end = item.endTime?.let { it.hour * 60 + it.minute }?.let { e -> if (start != null && e < start) e + 24 * 60 else e }
                    AutoExisting(start, end, item.sortOrder, item.lat, item.lng)
                },
            )
        }
    }

    /** The plan for the dialog; nothing is saved. Null when there is nothing to schedule. */
    fun previewAutoSchedule(): AutoPlan? {
        val s = state.value
        if (s.unscheduled.isEmpty() || undated) return null
        val places = s.unscheduled.map { AutoPlace(it.id, it.category, it.lat, it.lng, it.durationMin) }
        val result = autoSchedule(places, autoDays(s))
        return AutoPlan(result, result.scheduled.map { dayNumber(it.date) }.distinct().sorted())
    }

    /** Saves the plan place by place (each goes through the normal local-first path), then offers Undo. */
    fun runAutoSchedule() {
        if (autoRunning.value) return
        val s = state.value
        val plan = previewAutoSchedule() ?: return
        val byId = s.unscheduled.associateBy { it.id }
        autoRunning.value = true
        viewModelScope.launch {
            try {
                plan.result.scheduled.forEach { a ->
                    val item = byId[a.id] ?: return@forEach
                    itinerary.save(
                        trip.id,
                        item.toInput().copy(dayDate = a.date, startTime = a.start, endTime = a.end, sortOrder = a.sortOrder),
                    )
                    analytics.event(AnalyticsEvent.ActivityScheduled)
                }
                val restore = plan.result.scheduled.mapNotNull { byId[it.id]?.toInput() }
                events.send(ItineraryEvent.AutoScheduled(plan.count, plan.leftOver, restore))
            } finally {
                autoRunning.value = false
            }
        }
    }

    fun undoAutoSchedule(restore: List<ItineraryItemInput>) {
        viewModelScope.launch { restore.forEach { itinerary.save(trip.id, it) } }
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
