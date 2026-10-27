package app.ransel.shared.dto

import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Phase 3 offline-first sync (Android/iOS only; web talks to the REST endpoints directly).
 *
 * Push: the client sends every row it marked `pending`, deletes included (deletedAt set).
 * The server applies a row when it has access to the trip (EDITOR+, or OWNER for trips)
 * and the incoming updatedAt is newer than the stored one (last write wins per row).
 * Reorders arrive as several itinerary rows and are applied in one transaction.
 *
 * Pull: GET /sync?since=<serverTime of the previous pull> returns every row the user can
 * see that changed after `since` (server clock), deletes included. First pull: omit `since`.
 *
 * Guest data ("Pakai tanpa akun dulu") uses LOCAL_USER_ID as ownerId / paidByMemberId (as memberIdFor(tripId, LOCAL_USER_ID));
 * the client rewrites it to the real user id before the first push.
 */

const val LOCAL_USER_ID = "00000000-0000-0000-0000-000000000001"

@Serializable
data class SyncChanges(
    val trips: List<TripDto> = emptyList(),
    val itineraryItems: List<ItineraryItemDto> = emptyList(),
    val packingItems: List<PackingItemDto> = emptyList(),
    /** Splits travel inside each expense and are replaced whole. */
    val expenses: List<ExpenseDto> = emptyList(),
    /**
     * A new row may be CONFIRMED ("Tandai lunas") or CLAIMED_PAID (only by its payer). CLAIMED_PAID → CONFIRMED only by
     * the receiver, or by the OWNER when the receiver has no account; otherwise the row is rejected with FORBIDDEN.
     */
    val settlements: List<SettlementDto> = emptyList(),
    /** The caller's own packing lines only, both ways; every line is the member's to edit or delete, also those from the owner's list. */
    val personalItems: List<PersonalItemDto> = emptyList(),
    /** Pull only: membership of the user's trips (Phase 4). Ignored on push. */
    val members: List<TripMemberDto> = emptyList(),
)

/** POST /sync */
@Serializable
data class SyncPushRequest(
    val changes: SyncChanges,
)

@Serializable
data class SyncPushResponse(
    val accepted: List<SyncRowRef>,
    val rejected: List<SyncRejection>,
    val serverTime: Timestamp,
)

@Serializable
data class SyncRowRef(
    /** "trip", "itineraryItem", "packingItem", "personalItem", "expense", "settlement" */
    val entity: String,
    val id: String,
)

@Serializable
data class SyncRejection(
    val entity: String,
    val id: String,
    /** An ErrorCode, e.g. FORBIDDEN, TRIP_NOT_FOUND, VALIDATION_FAILED, CONFLICT (server copy is newer). */
    val code: String,
    val message: String? = null,
)

/** GET /sync?since= */
@Serializable
data class SyncPullResponse(
    val changes: SyncChanges,
    /** Store as lastSyncAt and send as `since` next time. */
    val serverTime: Timestamp,
)
