package app.ransel.shared.domain

import kotlinx.datetime.LocalTime

/*
 * Itinerary time rules (PRD "Business rules", docs/specs/itinerary-spec.md):
 *  - reorder: durations kept; the affected range [min(from,to)..max(from,to)] is packed
 *    back to back from the earliest start time in that range; items outside the range
 *    never change; items without a time are only moved.
 *  - schedule a place: end of the chosen day, starting when the last activity ends
 *    (09:00 on an empty day), duration from the AI estimate.
 * Times are clamped to 23:59 so a long day never wraps past midnight.
 */

data class Slot(
    val id: String,
    val start: LocalTime?,
    val end: LocalTime?,
)

const val DEFAULT_DAY_START_MINUTES = 9 * 60
private const val LAST_MINUTE = 23 * 60 + 59

fun reorder(items: List<Slot>, from: Int, to: Int): List<Slot> {
    require(from in items.indices && to in items.indices) { "from/to out of range" }
    if (from == to) return items
    val lo = minOf(from, to)
    val hi = maxOf(from, to)
    val moved = items.toMutableList().apply { add(to, removeAt(from)) }

    val rangeStarts = items.subList(lo, hi + 1).mapNotNull { it.start?.minutes() }
    if (rangeStarts.isEmpty()) return moved // nothing timed: order only
    var cursor = rangeStarts.min()

    for (i in lo..hi) {
        val slot = moved[i]
        val start = slot.start ?: continue
        val end = slot.end ?: start
        val duration = (end.minutes() - start.minutes()).coerceAtLeast(0)
        val newStart = cursor.coerceAtMost(LAST_MINUTE)
        val newEnd = (cursor + duration).coerceAtMost(LAST_MINUTE)
        moved[i] = slot.copy(start = newStart.toTime(), end = if (slot.end == null) null else newEnd.toTime())
        cursor += duration
    }
    return moved
}

/** Items whose time changed between [before] and [after] — fills the "Ubah urutan dan jam?" dialog. */
fun changedSlots(before: List<Slot>, after: List<Slot>): List<Pair<Slot, Slot>> {
    val old = before.associateBy { it.id }
    return after.mapNotNull { new ->
        val prev = old[new.id] ?: return@mapNotNull null
        if (prev.start != new.start || prev.end != new.end) prev to new else null
    }
}

data class ScheduledTime(val start: LocalTime, val end: LocalTime)

/**
 * Time for a place scheduled at the end of a day (from "Belum dijadwalkan" or moved
 * from another day). [dayItems] = the items already on that day, any order.
 * [emptyDayStart] = 09:00 for unscheduled places; the item's own start when moving days.
 */
fun scheduleAtEndOfDay(
    dayItems: List<Slot>,
    durationMin: Int?,
    emptyDayStart: LocalTime = DEFAULT_DAY_START_MINUTES.toTime(),
    defaultDurationMin: Int = 60,
): ScheduledTime {
    val lastEnd = dayItems.mapNotNull { (it.end ?: it.start)?.minutes() }.maxOrNull()
    val start = lastEnd ?: emptyDayStart.minutes()
    val duration = (durationMin ?: defaultDurationMin).coerceAtLeast(0)
    return ScheduledTime(
        start = start.coerceAtMost(LAST_MINUTE).toTime(),
        end = (start + duration).coerceAtMost(LAST_MINUTE).toTime(),
    )
}

/** sort_order for an item placed between two neighbours (null = list edge). */
fun sortOrderBetween(before: Double?, after: Double?): Double = when {
    before == null && after == null -> 1.0
    before == null -> after!! - 1.0
    after == null -> before + 1.0
    else -> (before + after) / 2.0
}

internal fun LocalTime.minutes(): Int = hour * 60 + minute
internal fun Int.toTime(): LocalTime = LocalTime(this / 60, this % 60)
