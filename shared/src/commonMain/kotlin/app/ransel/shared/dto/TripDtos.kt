package app.ransel.shared.dto

import app.ransel.shared.model.Role
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Conventions for every DTO:
 *  - ids are UUID strings created on the client (offline-first), also for POST;
 *  - money is Long in the minor unit of its currency (see domain/Money.kt);
 *  - currency is an ISO 4217 code, 3 upper-case letters;
 *  - createdAt / updatedAt / deletedAt are set by the server; deletedAt != null = soft-deleted.
 */

@Serializable
data class TripDto(
    val id: String,
    val ownerId: String,
    val title: String,
    val destination: String,
    /**
     * Both null = "Tanggal belum pasti": the group picks the dates with a DATE poll, whose result sets them. Until then
     * the trip has no days, so every itinerary item is unscheduled. Never only one of the two (see [hasDates]).
     */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val coverUrl: String? = null,
    /** Role of the calling user. Always OWNER before Phase 4. */
    val myRole: Role = Role.OWNER,
    /** Where [destination] is, when it was picked from the suggestions; null for a typed name. */
    val destinationPlace: TripPlaceDto? = null,
    /** Destination country, ISO 3166-1 alpha-2 ("JP"); null = unknown (treated as Indonesia). */
    val countryCode: String? = null,
    /** Currency spent at the destination (ISO 4217, "JPY"); new expenses default to it. Null = [baseCurrency]. */
    val localCurrency: String? = null,
    /** IANA zone of the destination ("Asia/Tokyo"); itinerary times are local to it. Null = the device's zone. */
    val timeZone: String? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** Numbers for Home ("Persiapan trip"), My trips cards and the delete dialog. Computed, never stored. */
@Serializable
data class TripSummaryDto(
    val daysTotal: Int,
    /** Days with at least one scheduled activity. */
    val daysFilled: Int,
    val activityCount: Int,
    /** Itinerary items with dayDate == null. */
    val unscheduledCount: Int,
    val packingTotal: Int,
    val packingPacked: Int,
    val expenseCount: Int,
    /** Sum of expenses converted to baseCurrency. */
    val spentMinor: Long,
    /** budgetMinor - spentMinor, null when the trip has no budget. */
    val remainingMinor: Long? = null,
    val memberCount: Int = 1,
)

/** GET /trips/{id} */
@Serializable
data class TripDetailDto(
    val trip: TripDto,
    val summary: TripSummaryDto,
)

/**
 * POST /trips. Validation: title not blank, both dates or neither (an undated trip), endDate >= startDate,
 * budgetMinor >= 0, currency ^[A-Z]{3}$; lat -90..90, lng -180..180.
 */
@Serializable
data class CreateTripRequest(
    val id: String,
    val title: String,
    val destination: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val coverUrl: String? = null,
    val destinationPlace: TripPlaceDto? = null,
    val countryCode: String? = null,
    val localCurrency: String? = null,
    val timeZone: String? = null,
)

/**
 * PUT /trips/{id} — full replacement of the editable fields. EDITOR or above. [destinationPlace] null keeps the stored place
 * while [destination] is unchanged (older apps do not send it) and clears it when the name changed; the same goes for
 * [countryCode], [localCurrency] and [timeZone] together.
 */
@Serializable
data class UpdateTripRequest(
    val title: String,
    val destination: String,
    /** Both null makes the trip undated again: its scheduled activities become unscheduled. */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val coverUrl: String? = null,
    val destinationPlace: TripPlaceDto? = null,
    val countryCode: String? = null,
    val localCurrency: String? = null,
    val timeZone: String? = null,
)
