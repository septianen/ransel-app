package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.TripDto
import kotlinx.datetime.LocalDate

/*
 * Shortening a trip never deletes activities (design "Edit trip": "aktivitas di hari yang terhapus
 * pindah ke Belum dijadwalkan, tidak ikut terhapus"). Items on days that fall outside the new range
 * become unscheduled; their duration is kept so they can be scheduled again later.
 *
 * "Tanggal belum pasti" (PRD "Date poll"): a trip may also have no dates while the group polls them. Both dates are
 * set or neither is. Without dates the trip has no days, so every item is unscheduled and day-based things
 * (countdown, daily rate, recap, reminders) wait until a DATE poll is decided or the dates are typed in.
 */

/** True when the trip has its dates. */
val TripDto.hasDates: Boolean get() = startDate != null && endDate != null

/**
 * Whether an itinerary item may sit on [day] in a trip from [startDate] to [endDate]. Unscheduled (null) always may;
 * a trip without dates has no days, so nothing may be scheduled.
 */
fun dayFitsTrip(day: LocalDate?, startDate: LocalDate?, endDate: LocalDate?): Boolean =
    day == null || (startDate != null && endDate != null && day in startDate..endDate)

/** Scheduled items whose day is outside `start..end`; with no dates, every scheduled item. */
fun itemsOutsideRange(items: List<ItineraryItemDto>, start: LocalDate?, end: LocalDate?): List<ItineraryItemDto> =
    items.filter { it.deletedAt == null && it.dayDate != null && !dayFitsTrip(it.dayDate, start, end) }

/** The same item as an unscheduled one: no day, no clock times, `durationMin` from the old times when missing. */
fun ItineraryItemDto.toUnscheduledInput(): ItineraryItemInput {
    val minutes = activityDurationMinutes(startTime, endTime)?.takeIf { it > 0 }
    return ItineraryItemInput(
        id = id, dayDate = null, startTime = null, endTime = null, title = title, category = category,
        placeName = placeName, placeId = placeId, lat = lat, lng = lng,
        durationMin = durationMin ?: minutes, notes = notes, bookingUrl = bookingUrl, source = source,
        sortOrder = sortOrder,
    )
}
