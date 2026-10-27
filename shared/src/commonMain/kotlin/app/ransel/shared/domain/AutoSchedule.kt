package app.ransel.shared.domain

import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * "Jadwalkan otomatis" (owner, 27 Oct 2026): puts the whole "Belum dijadwalkan" box into the days of a trip.
 * No AI: every place already has coordinates, a duration estimate and a category, and the result must be the same on
 * every device, offline, for free.
 *
 *  1. Places close to what a day already holds (within [ANCHOR_KM] of an activity) go to that day.
 *  2. The rest are put in one route (nearest neighbour, starting near the first anchor or the first place) and the
 *     route is cut into days in order, so each day gets places that are close to each other.
 *  3. A day takes places while they fit between 09:00 and 20:00 (duration plus travel), but stops early to keep the
 *     days about equally full, and when the next place is far (over 15 km) from the last one.
 *  4. Inside a day the order follows the route, but a place to eat comes at lunch (from 11:30) and at dinner
 *     (from 17:30) when there is one.
 *  5. Activities already on a day never change; new ones start when the last one ends (09:00 on an empty day).
 * What does not fit stays unscheduled and is reported in [AutoScheduleResult.leftOver].
 */

const val AUTO_DAY_START_MINUTES = 9 * 60
const val AUTO_DAY_END_MINUTES = 20 * 60
private const val LUNCH_FROM = 11 * 60 + 30
private const val DINNER_FROM = 17 * 60 + 30
private const val MAX_MEAL_WAIT = 60
private const val ANCHOR_KM = 5.0
private const val FAR_KM = 15.0
private const val BALANCE_SLACK_MIN = 30
private const val TRAVEL_KMH = 25.0
private const val TRAVEL_BUFFER_MIN = 15
private const val DEFAULT_DURATION_MIN = 60
private const val ROUND_TO_MIN = 5
private const val LAST_START_MINUTE = 23 * 60 + 59
private const val MINUTES_PER_DAY = 24 * 60

/** A place to schedule (from "Belum dijadwalkan"). */
data class AutoPlace(
    val id: String,
    val category: Category,
    val lat: Double? = null,
    val lng: Double? = null,
    val durationMin: Int? = null,
)

/** An activity a day already has; [endMinute] may be past midnight (overnight) and is null when it has no time. */
data class AutoExisting(
    val startMinute: Int?,
    val endMinute: Int?,
    val sortOrder: Double,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** A day that can take places. Leave days that are over out of the list. */
data class AutoDay(val date: LocalDate, val existing: List<AutoExisting> = emptyList())

data class AutoScheduled(
    val id: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val sortOrder: Double,
)

data class AutoScheduleResult(val scheduled: List<AutoScheduled>, val leftOver: List<String>)

fun autoSchedule(places: List<AutoPlace>, days: List<AutoDay>): AutoScheduleResult {
    if (places.isEmpty() || days.isEmpty()) return AutoScheduleResult(emptyList(), places.map { it.id })

    val plan = days.map { DayPlan(it) }
    val remaining = places.toMutableList()

    // 1. close to what a day already holds
    val anchored = plan.filter { it.anchors.isNotEmpty() }
    if (anchored.isNotEmpty()) {
        val route = routeOf(remaining.filter { it.hasPoint }, start = null)
        for (place in route) {
            val day = anchored
                .map { it to it.anchors.minOf { a -> km(a.lat!!, a.lng!!, place.lat!!, place.lng!!) } }
                .filter { (_, d) -> d <= ANCHOR_KM }
                .sortedBy { (_, d) -> d }
                .firstOrNull { (d, _) -> d.fits(place) }?.first ?: continue
            day.add(place)
            remaining.remove(place)
        }
    }

    // 2.–3. one route, cut into days
    val located = remaining.filter { it.hasPoint }
    val firstAnchor = anchored.firstOrNull()?.anchors?.firstOrNull()
    val route = routeOf(located, start = firstAnchor?.let { it.lat!! to it.lng!! })
    val loose = remaining.filter { !it.hasPoint }
    val left = mutableListOf<String>()
    var dayIndex = 0
    // about equal days: what the route needs, shared over the days that still have room
    val target = route.sumOf { it.minutes + TRAVEL_BUFFER_MIN } / plan.count { it.room > 60 }.coerceAtLeast(1)
    for (place in route) {
        while (dayIndex < plan.size) {
            val day = plan[dayIndex]
            val takes = if (dayIndex == plan.lastIndex) day.fits(place) else day.wantsMore(place, target)
            if (takes) break
            dayIndex++
        }
        if (dayIndex == plan.size) {
            // later places may still fit an earlier, emptier day, so look again from the first day
            val day = plan.firstOrNull { it.fits(place) }
            if (day == null) left += place.id else day.add(place)
        } else {
            plan[dayIndex].add(place)
        }
    }
    // no coordinates: the day with the most room, at the end of it
    for (place in loose) {
        val day = plan.filter { it.fits(place) }.maxByOrNull { it.room }
        if (day == null) left += place.id else day.add(place)
    }

    // 4.–5. times
    val scheduled = plan.flatMap { it.timeline() }
    return AutoScheduleResult(scheduled, left)
}

private val AutoPlace.hasPoint: Boolean get() = lat != null && lng != null
private val AutoPlace.minutes: Int get() = (durationMin ?: DEFAULT_DURATION_MIN).coerceIn(15, 10 * 60)

private class DayPlan(val day: AutoDay) {
    val anchors = day.existing.filter { it.lat != null && it.lng != null }
    private val added = mutableListOf<AutoPlace>()

    /** Where new activities start: when the last existing one ends, 09:00 on an empty (or all-untimed) day. */
    private val startMinute: Int = day.existing
        .mapNotNull { e ->
            val end = (e.endMinute ?: e.startMinute) ?: return@mapNotNull null
            if (e.startMinute != null && e.endMinute != null && e.endMinute < e.startMinute) end + MINUTES_PER_DAY else end
        }
        .maxOrNull()?.let { maxOf(it, AUTO_DAY_START_MINUTES) } ?: AUTO_DAY_START_MINUTES

    private val lastExisting = day.existing.filter { it.lat != null && it.lng != null }
        .maxByOrNull { it.endMinute ?: it.startMinute ?: 0 }

    private var used = 0
    private var previous: AutoPlace? = null

    val room: Int get() = AUTO_DAY_END_MINUTES - startMinute - used

    private fun cost(place: AutoPlace): Int = place.minutes + travelMin(previous, place)

    private fun travelMin(from: AutoPlace?, to: AutoPlace): Int {
        val a = from?.takeIf { it.hasPoint }?.let { it.lat!! to it.lng!! }
            ?: if (from == null) lastExisting?.let { it.lat!! to it.lng!! } else null
        if (a == null || !to.hasPoint) return if (from == null && day.existing.isEmpty()) 0 else TRAVEL_BUFFER_MIN
        return TRAVEL_BUFFER_MIN + ceil(km(a.first, a.second, to.lat!!, to.lng!!) / TRAVEL_KMH * 60).toInt()
    }

    /** A place fits while it ends by [AUTO_DAY_END_MINUTES]; a day with nothing yet takes one place however long. */
    fun fits(place: AutoPlace): Boolean = cost(place) <= room || (added.isEmpty() && day.existing.isEmpty())

    /** Like [fits], but also false once the day has its share, or when [place] is far from the last one. */
    fun wantsMore(place: AutoPlace, target: Int): Boolean {
        if (!fits(place)) return false
        if (added.isEmpty()) return true
        val last = previous
        val far = last != null && last.hasPoint && place.hasPoint && km(last.lat!!, last.lng!!, place.lat!!, place.lng!!) > FAR_KM
        return !far && used + cost(place) <= target + BALANCE_SLACK_MIN
    }

    fun add(place: AutoPlace) {
        used += cost(place)
        previous = place
        added += place
    }

    /** The day's new places in time order: the route, with a place to eat at lunch and at dinner. */
    fun timeline(): List<AutoScheduled> {
        if (added.isEmpty()) return emptyList()
        val food = added.filter { it.category == Category.FOOD }.toMutableList()
        val others = added.filter { it.category != Category.FOOD }.toMutableList()
        val ordered = mutableListOf<Pair<AutoPlace, Int>>() // place → start minute
        var cursor = startMinute
        var last: AutoPlace? = null
        var lunchDone = false
        var dinnerDone = false

        fun place(p: AutoPlace, waitUntil: Int = 0) {
            val travel = travelMin(last, p).let { if (last == null && day.existing.isEmpty()) 0 else it }
            val start = roundUp(maxOf(cursor + travel, waitUntil)).coerceAtMost(LAST_START_MINUTE)
            ordered += p to start
            cursor = start + p.minutes
            last = p
        }

        fun nearestFood(): AutoPlace {
            val from = last ?: lastExisting?.let { AutoPlace("", Category.OTHER, it.lat, it.lng) }
            val pick = if (from != null && from.hasPoint) {
                food.filter { it.hasPoint }.minByOrNull { km(from.lat!!, from.lng!!, it.lat!!, it.lng!!) } ?: food.first()
            } else {
                food.first()
            }
            food.remove(pick)
            return pick
        }

        // a meal is due when its time has come, or when the next place would run through it (and it is near enough)
        fun mealDue(from: Int, done: Boolean): Boolean =
            !done && food.isNotEmpty() && cursor >= from - MAX_MEAL_WAIT &&
                (cursor >= from || others.isEmpty() || cursor + others.first().minutes > from + 60)

        while (others.isNotEmpty() || food.isNotEmpty()) {
            when {
                mealDue(LUNCH_FROM, lunchDone) -> {
                    lunchDone = true
                    place(nearestFood(), waitUntil = LUNCH_FROM)
                }
                mealDue(DINNER_FROM, dinnerDone) -> {
                    dinnerDone = true
                    place(nearestFood(), waitUntil = DINNER_FROM)
                }
                others.isNotEmpty() -> place(others.removeAt(0))
                else -> place(nearestFood())
            }
        }

        val lastOrder = day.existing.maxOfOrNull { it.sortOrder } ?: 0.0
        return ordered.mapIndexed { index, (p, start) ->
            AutoScheduled(
                id = p.id,
                date = day.date,
                start = start.toTime(),
                end = ((start + p.minutes) % MINUTES_PER_DAY).toTime(),
                sortOrder = lastOrder + index + 1.0,
            )
        }
    }
}

/** Nearest-neighbour route over [places], beginning near [start] (or with the first place when null). */
private fun routeOf(places: List<AutoPlace>, start: Pair<Double, Double>?): List<AutoPlace> {
    if (places.size < 2) return places
    val left = places.toMutableList()
    val route = mutableListOf<AutoPlace>()
    var at = start ?: left.first().let { it.lat!! to it.lng!! }
    if (start == null) route += left.removeAt(0)
    while (left.isNotEmpty()) {
        val next = left.minBy { km(at.first, at.second, it.lat!!, it.lng!!) }
        left.remove(next)
        route += next
        at = next.lat!! to next.lng!!
    }
    return route
}

private fun roundUp(minutes: Int): Int = ((minutes + ROUND_TO_MIN - 1) / ROUND_TO_MIN) * ROUND_TO_MIN

private fun km(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = (lat2 - lat1) * PI / 180
    val dLng = (lng2 - lng1) * PI / 180
    val a = sin(dLat / 2).let { it * it } + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(a))
}
