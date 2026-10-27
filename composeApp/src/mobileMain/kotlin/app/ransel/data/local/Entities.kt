package app.ransel.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * Local tables mirror server V1 (docs/TRD.md "Data model") plus a local `syncState`, unused until
 * Phase 3. Dates/times are ISO text ("2026-11-20", "09:00"); timestamps are epoch millis so ordering
 * and comparison are numeric. Soft delete everywhere (`deletedAt`).
 */

object SyncState {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"

    /** The server refused this row (not retryable: forbidden, invalid, trip gone). Kept locally, never re-sent. */
    const val REJECTED = "REJECTED"
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val baseCurrency: String,
    val budgetMinor: Long?,
    val coverUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String = SyncState.PENDING,
    /** The signed-in user's role in this trip (Phase 4, DB v3); trips made on this device are always OWNER. */
    @ColumnInfo(defaultValue = "OWNER") val myRole: String = "OWNER",
    /** The picked destination's place (DB v7); all null for a typed name. */
    val destPlaceId: String? = null,
    val destLat: Double? = null,
    val destLng: Double? = null,
    /** JSON of `ViewportDto`. */
    val destViewportJson: String? = null,
    /** Destination country, its currency and IANA zone (DB v8); null = home. */
    val countryCode: String? = null,
    val localCurrency: String? = null,
    val timeZone: String? = null,
)

@Entity(tableName = "itinerary_items", indices = [Index("tripId")])
data class ItineraryItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val dayDate: String?,
    val startTime: String?,
    val endTime: String?,
    val title: String,
    val category: String,
    val placeName: String?,
    val placeId: String?,
    val lat: Double?,
    val lng: Double?,
    val durationMin: Int?,
    val notes: String?,
    val bookingUrl: String?,
    val source: String,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String = SyncState.PENDING,
)

@Entity(tableName = "packing_items", indices = [Index("tripId")])
data class PackingItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val category: String,
    val name: String,
    val quantity: Int,
    val isPacked: Boolean,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String = SyncState.PENDING,
    /** Bersama: who brings it (DB v9). */
    @ColumnInfo(defaultValue = "NULL") val assigneeMemberId: String? = null,
)

/** "Pribadi": the signed-in member's own lines in a group trip (DB v9). Lines with [sourceItemId] came from the owner's list and are the member's too. */
@Entity(tableName = "personal_items", indices = [Index("tripId")])
data class PersonalItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val ownerMemberId: String,
    val category: String,
    val name: String,
    val quantity: Int,
    val isPacked: Boolean,
    val sortOrder: Double,
    val sourceItemId: String?,
    val required: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String = SyncState.PENDING,
)

@Entity(tableName = "expenses", indices = [Index("tripId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateToBase: String?,
    val category: String,
    val paymentMethod: String,
    val note: String?,
    val spentAt: Long,
    /** `trip_members.id` of who paid (`memberIdFor(tripId, userId)` for an account; DB v5, was paidByUserId). */
    val paidByMemberId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String = SyncState.PENDING,
    /** JSON list of `ExpenseSplitDto` (Phase 4, DB v2); null = paid and carried by `paidByMemberId` alone. */
    val splitsJson: String? = null,
    /** JSON list of `ExpensePayerDto` (DB v4) when several people paid; null = `paidByMemberId` paid all. */
    val payersJson: String? = null,
    /** "Ambil dari kas" (DB v6): `paidByMemberId` is then the treasurer. */
    /** A planned cost (DB v6), e.g. the stay chosen in a poll: not in balances, not in "spent". */
    @ColumnInfo(defaultValue = "0") val isEstimate: Boolean = false,
    /** JSON list of `ExpenseItemDto` (DB v6): the receipt lines of a "per item" split; null = none. */
    val itemsJson: String? = null,
    /** Where the rate came from, `FxSource` name (DB v8), and the day of the reference rate as ISO text. */
    val fxSource: String? = null,
    val fxDate: String? = null,
)

/** Key/value store for per-device preferences (theme, default currency, ...). Never synced. */
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)
