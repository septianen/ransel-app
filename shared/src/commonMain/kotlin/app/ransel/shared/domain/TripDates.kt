package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import kotlinx.datetime.LocalDate

/*
 * Shortening a trip never deletes activities (design "Edit trip": "aktivitas di hari yang terhapus
 * pindah ke Belum dijadwalkan, tidak ikut terhapus"). Items on days that fall outside the new range
 * become unscheduled; their duration is kept so they can be scheduled again later.
 */

/** Scheduled items whose day is outside `start..end`. */
fun itemsOutsideRange(items: List<ItineraryItemDto>, start: LocalDate, end: LocalDate): List<ItineraryItemDto> =
    items.filter { it.deletedAt == null && it.dayDate != null && (it.dayDate!! < start || it.dayDate!! > end) }

/** The same item as an unscheduled one: no day, no clock times, `durationMin` from the old times when missing. */
fun ItineraryItemDto.toUnscheduledInput(): ItineraryItemInput {
    val minutes = if (startTime != null && endTime != null) {
        (endTime!!.minutes() - startTime!!.minutes()).takeIf { it > 0 }
    } else {
        null
    }
    return ItineraryItemInput(
        id = id, dayDate = null, startTime = null, endTime = null, title = title, category = category,
        placeName = placeName, placeId = placeId, lat = lat, lng = lng,
        durationMin = durationMin ?: minutes, notes = notes, bookingUrl = bookingUrl, source = source,
        sortOrder = sortOrder,
    )
}
