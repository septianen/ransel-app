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
    val startDate: LocalDate,
    val endDate: LocalDate,
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val coverUrl: String? = null,
    /** Role of the calling user. Always OWNER before Phase 4. */
    val myRole: Role = Role.OWNER,
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

/** POST /trips. Validation: title not blank, endDate >= startDate, budgetMinor >= 0, currency ^[A-Z]{3}$. */
@Serializable
data class CreateTripRequest(
    val id: String,
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val coverUrl: String? = null,
)

/** PUT /trips/{id} — full replacement of the editable fields. EDITOR or above. */
@Serializable
data class UpdateTripRequest(
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val baseCurrency: String,
    val budgetMinor: Long? = null,
    val coverUrl: String? = null,
)
