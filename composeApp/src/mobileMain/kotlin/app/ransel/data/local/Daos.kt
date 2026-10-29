package app.ransel.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE deletedAt IS NULL ORDER BY startDate DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): TripEntity?

    @Upsert
    suspend fun upsert(entity: TripEntity)

    @Query("UPDATE trips SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface ItineraryDao {
    /** Unscheduled (dayDate NULL) first, then by day and sortOrder. */
    @Query(
        "SELECT * FROM itinerary_items WHERE tripId = :tripId AND deletedAt IS NULL " +
            "ORDER BY dayDate IS NOT NULL, dayDate, sortOrder",
    )
    fun observe(tripId: String): Flow<List<ItineraryItemEntity>>

    @Query("SELECT * FROM itinerary_items WHERE id = :id")
    suspend fun get(id: String): ItineraryItemEntity?

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId AND deletedAt IS NULL")
    suspend fun getByTrip(tripId: String): List<ItineraryItemEntity>

    @Upsert
    suspend fun upsert(entity: ItineraryItemEntity)

    @Query(
        "UPDATE itinerary_items SET sortOrder = :sortOrder, startTime = :startTime, endTime = :endTime, " +
            "updatedAt = :now, syncState = 'PENDING' WHERE id = :id AND tripId = :tripId",
    )
    suspend fun updateOrder(tripId: String, id: String, sortOrder: Double, startTime: String?, endTime: String?, now: Long)

    @Query("UPDATE itinerary_items SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query(
        "UPDATE itinerary_items SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' " +
            "WHERE tripId = :tripId AND deletedAt IS NULL",
    )
    suspend fun softDeleteByTrip(tripId: String, now: Long)
}

@Dao
interface PersonalItemDao {
    @Query("SELECT * FROM personal_items WHERE tripId = :tripId AND deletedAt IS NULL ORDER BY category, sortOrder")
    fun observe(tripId: String): Flow<List<PersonalItemEntity>>

    @Query("SELECT * FROM personal_items WHERE id = :id")
    suspend fun get(id: String): PersonalItemEntity?

    @Upsert
    suspend fun upsert(entity: PersonalItemEntity)

    @Query("UPDATE personal_items SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface PackingDao {
    @Query("SELECT * FROM packing_items WHERE tripId = :tripId AND deletedAt IS NULL ORDER BY category, sortOrder")
    fun observe(tripId: String): Flow<List<PackingItemEntity>>

    @Query("SELECT * FROM packing_items WHERE id = :id")
    suspend fun get(id: String): PackingItemEntity?

    @Upsert
    suspend fun upsert(entity: PackingItemEntity)

    @Query("UPDATE packing_items SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query(
        "UPDATE packing_items SET isPacked = 0, updatedAt = :now, syncState = 'PENDING' " +
            "WHERE tripId = :tripId AND isPacked = 1 AND deletedAt IS NULL",
    )
    suspend fun uncheckAll(tripId: String, now: Long)

    @Query(
        "UPDATE packing_items SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' " +
            "WHERE tripId = :tripId AND deletedAt IS NULL",
    )
    suspend fun softDeleteByTrip(tripId: String, now: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND deletedAt IS NULL ORDER BY spentAt DESC")
    fun observe(tripId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsert(entity: ExpenseEntity)

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query(
        "UPDATE expenses SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING' " +
            "WHERE tripId = :tripId AND deletedAt IS NULL",
    )
    suspend fun softDeleteByTrip(tripId: String, now: Long)
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Upsert
    suspend fun set(entity: PreferenceEntity)

    @Query("SELECT value FROM preferences WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("DELETE FROM preferences WHERE `key` = :key")
    suspend fun remove(key: String)
}

/**
 * Everything the sync engine needs across the four content tables. A row is pending when its
 * latest local change was not accepted yet; it becomes SYNCED only if `updatedAt` is unchanged
 * since the snapshot that was pushed (an edit made during the request stays pending).
 */
@Dao
interface SyncDao {
    @Query("SELECT * FROM trips WHERE syncState = 'PENDING'")
    suspend fun pendingTrips(): List<TripEntity>

    @Query("SELECT * FROM itinerary_items WHERE syncState = 'PENDING'")
    suspend fun pendingItems(): List<ItineraryItemEntity>

    @Query("SELECT * FROM packing_items WHERE syncState = 'PENDING'")
    suspend fun pendingPacking(): List<PackingItemEntity>

    @Query("SELECT * FROM expenses WHERE syncState = 'PENDING'")
    suspend fun pendingExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM personal_items WHERE syncState = 'PENDING'")
    suspend fun pendingPersonal(): List<PersonalItemEntity>

    @Query(
        "SELECT id FROM trips WHERE syncState = 'PENDING' UNION ALL " +
            "SELECT id FROM itinerary_items WHERE syncState = 'PENDING' UNION ALL " +
            "SELECT id FROM packing_items WHERE syncState = 'PENDING' UNION ALL " +
            "SELECT id FROM expenses WHERE syncState = 'PENDING' UNION ALL " +
            "SELECT id FROM personal_items WHERE syncState = 'PENDING'",
    )
    fun observePendingIds(): Flow<List<String>>

    @Query("UPDATE trips SET syncState = :state WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING'")
    suspend fun setTripState(id: String, updatedAt: Long, state: String)

    @Query("UPDATE itinerary_items SET syncState = :state WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING'")
    suspend fun setItemState(id: String, updatedAt: Long, state: String)

    @Query("UPDATE packing_items SET syncState = :state WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING'")
    suspend fun setPackingState(id: String, updatedAt: Long, state: String)

    @Query("UPDATE expenses SET syncState = :state WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING'")
    suspend fun setExpenseState(id: String, updatedAt: Long, state: String)

    @Query("UPDATE personal_items SET syncState = :state WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING'")
    suspend fun setPersonalState(id: String, updatedAt: Long, state: String)

    /** Rows the server refused, with their trip (a trip row is its own trip). */
    @Query(
        "SELECT id, id AS tripId, 'trip' AS entity FROM trips WHERE syncState = 'REJECTED' UNION ALL " +
            "SELECT id, tripId, 'itineraryItem' AS entity FROM itinerary_items WHERE syncState = 'REJECTED' UNION ALL " +
            "SELECT id, tripId, 'packingItem' AS entity FROM packing_items WHERE syncState = 'REJECTED' UNION ALL " +
            "SELECT id, tripId, 'expense' AS entity FROM expenses WHERE syncState = 'REJECTED' UNION ALL " +
            "SELECT id, tripId, 'personalItem' AS entity FROM personal_items WHERE syncState = 'REJECTED'",
    )
    suspend fun rejectedRows(): List<RejectedRow>

    @Query("DELETE FROM trips WHERE syncState = 'REJECTED'")
    suspend fun deleteRejectedTrips()

    @Query("DELETE FROM itinerary_items WHERE syncState = 'REJECTED'")
    suspend fun deleteRejectedItems()

    @Query("DELETE FROM packing_items WHERE syncState = 'REJECTED'")
    suspend fun deleteRejectedPacking()

    @Query("DELETE FROM expenses WHERE syncState = 'REJECTED'")
    suspend fun deleteRejectedExpenses()

    @Query("DELETE FROM personal_items WHERE syncState = 'REJECTED'")
    suspend fun deleteRejectedPersonal()

    @Query("SELECT * FROM personal_items WHERE id = :id")
    suspend fun personal(id: String): PersonalItemEntity?

    @Upsert
    suspend fun putPersonal(entity: PersonalItemEntity)

    @Query("DELETE FROM personal_items")
    suspend fun clearPersonal()

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: String): TripEntity?

    @Query("SELECT * FROM itinerary_items WHERE id = :id")
    suspend fun item(id: String): ItineraryItemEntity?

    @Query("SELECT * FROM packing_items WHERE id = :id")
    suspend fun packing(id: String): PackingItemEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: String): ExpenseEntity?

    @Upsert
    suspend fun putTrip(entity: TripEntity)

    @Upsert
    suspend fun putItem(entity: ItineraryItemEntity)

    @Upsert
    suspend fun putPacking(entity: PackingItemEntity)

    @Upsert
    suspend fun putExpense(entity: ExpenseEntity)

    // Guest data becomes the account's data on first sign-in: re-own, then send everything.
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :guestId")
    suspend fun guestRowCount(guestId: String): Int

    @Query("SELECT id FROM trips WHERE ownerId = :guestId")
    suspend fun guestTripIds(guestId: String): List<String>

    @Query("UPDATE trips SET ownerId = :userId WHERE ownerId = :guestId")
    suspend fun reownTrips(guestId: String, userId: String)

    /**
     * In one trip the guest's member (`memberIdFor(trip, LOCAL_USER_ID)`) becomes the account's: who paid, and the
     * id inside the splits, payers and receipt items JSON. SQL `replace` keeps a NULL column NULL.
     */
    @Query(
        "UPDATE expenses SET " +
            "paidByMemberId = CASE WHEN paidByMemberId = :oldId THEN :newId ELSE paidByMemberId END, " +
            "splitsJson = replace(splitsJson, :oldId, :newId), payersJson = replace(payersJson, :oldId, :newId), " +
            "itemsJson = replace(itemsJson, :oldId, :newId) " +
            "WHERE tripId = :tripId",
    )
    suspend fun reownExpenseMember(tripId: String, oldId: String, newId: String)

    /** The guest's own packing lines: the server only takes lines whose owner is the caller's member. */
    @Query("UPDATE personal_items SET ownerMemberId = :newId WHERE tripId = :tripId AND ownerMemberId = :oldId")
    suspend fun reownPersonalMember(tripId: String, oldId: String, newId: String)

    /** Bersama items the guest said they bring. */
    @Query("UPDATE packing_items SET assigneeMemberId = :newId WHERE tripId = :tripId AND assigneeMemberId = :oldId")
    suspend fun reownPackingAssignee(tripId: String, oldId: String, newId: String)

    @Query("UPDATE personal_items SET syncState = 'PENDING'")
    suspend fun markAllPersonalPending()

    @Query("UPDATE trips SET syncState = 'PENDING'")
    suspend fun markAllTripsPending()

    @Query("UPDATE itinerary_items SET syncState = 'PENDING'")
    suspend fun markAllItemsPending()

    @Query("UPDATE packing_items SET syncState = 'PENDING'")
    suspend fun markAllPackingPending()

    @Query("UPDATE expenses SET syncState = 'PENDING'")
    suspend fun markAllExpensesPending()

    @Query("DELETE FROM trips")
    suspend fun clearTrips()

    @Query("DELETE FROM itinerary_items")
    suspend fun clearItems()

    @Query("DELETE FROM packing_items")
    suspend fun clearPacking()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()
}

/** One row with syncState REJECTED; [entity] uses the sync wire names ("expense", "itineraryItem", ...). */
data class RejectedRow(val id: String, val tripId: String, val entity: String)
