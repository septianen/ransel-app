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
}
