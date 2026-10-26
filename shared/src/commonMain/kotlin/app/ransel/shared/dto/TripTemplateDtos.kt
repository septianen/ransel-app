package app.ransel.shared.dto

import app.ransel.shared.model.Category
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/*
 * Trip templates (PRD P2 "Trip recap": "saved as a trip template"): the itinerary by day number and the shared packing
 * list of a trip, owned by the user who saved it, to start a new trip from ("Pakai template trip" on Buat trip).
 * Owner decision 26 Oct 2026: itinerary + packing.
 */

/** One activity of a template. [dayIndex] 0 = the first day; null = unscheduled. */
@Serializable
data class TripTemplateItem(
    val dayIndex: Int? = null,
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
)

@Serializable
data class TripTemplateDto(
    val id: String,
    val name: String,
    /** Days of the trip it was saved from. */
    val dayCount: Int,
    val destination: String = "",
    val items: List<TripTemplateItem> = emptyList(),
    /** The shared packing list (quantities kept, ticks never). */
    val packing: List<TemplateItem> = emptyList(),
    val createdAt: Timestamp,
)

/** POST /trip-templates: the server copies [tripId]'s itinerary and shared packing list (any member of it may save). */
@Serializable
data class CreateTripTemplateRequest(
    val id: String,
    val name: String,
    val tripId: String,
)
