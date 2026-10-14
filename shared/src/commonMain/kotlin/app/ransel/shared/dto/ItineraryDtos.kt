package app.ransel.shared.dto

import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class ItineraryItemDto(
    val id: String,
    val tripId: String,
    /** null = "Belum dijadwalkan" (Unscheduled). */
    val dayDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    /** Earlier than [startTime] = the activity ends the next day (23:00–01:00). The day is [dayDate], the day it starts. */
    val endTime: LocalTime? = null,
    val title: String,
    val category: Category = Category.OTHER,
    val placeName: String? = null,
    /** Google placeId; set for AI items, optional for manual ones. */
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Estimated duration in minutes (from the AI); used when scheduling an unscheduled place. */
    val durationMin: Int? = null,
    val notes: String? = null,
    val bookingUrl: String? = null,
    val source: ItemSource = ItemSource.MANUAL,
    /** Fractional; insert between A and B = (A + B) / 2. Ordered per (tripId, dayDate). */
    val sortOrder: Double = 0.0,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/**
 * POST /trips/{id}/itinerary (create) and PUT /trips/{id}/itinerary/{itemId} (replace).
 * Scheduling an unscheduled place = PUT with dayDate + startTime + endTime + sortOrder set
 * (values from domain.scheduleAtEndOfDay).
 * Validation: title not blank; lat/lng both set or both null. endTime may be earlier than startTime:
 * that means the activity ends the next day (domain.activityDurationMinutes); equal times = zero duration.
 */
@Serializable
data class ItineraryItemInput(
    val id: String,
    val dayDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val title: String,
    val category: Category = Category.OTHER,
    val placeName: String? = null,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val durationMin: Int? = null,
    val notes: String? = null,
    val bookingUrl: String? = null,
    val source: ItemSource = ItemSource.MANUAL,
    val sortOrder: Double = 0.0,
)

/**
 * PUT /trips/{id}/days/{date}/order — the result of one drag (domain.reorder),
 * applied in ONE transaction. Undo = send the previous list again.
 * Moving an item to another day = PUT the item itself with the new dayDate.
 */
@Serializable
data class DayOrderRequest(
    val items: List<DayOrderItem>,
)

@Serializable
data class DayOrderItem(
    val id: String,
    val sortOrder: Double,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
)
