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
    val paidByUserId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String = SyncState.PENDING,
    /** JSON list of `ExpenseSplitDto` (Phase 4, DB v2); null = paid and carried by `paidByUserId` alone. */
    val splitsJson: String? = null,
    /** JSON list of `ExpensePayerDto` (DB v4) when several people paid; null = `paidByUserId` paid all. */
    val payersJson: String? = null,
)

/** Key/value store for per-device preferences (theme, default currency, ...). Never synced. */
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)
