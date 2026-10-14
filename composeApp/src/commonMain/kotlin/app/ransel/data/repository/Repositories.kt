package app.ransel.data.repository

import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.DayOrderItem
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/*
 * Repository interfaces (commonMain). Two implementations per interface:
 *  - mobileMain: Room 3, local-first (Android + iOS)
 *  - wasmJsMain: remote, calls the API (Phase 2)
 * The shared DTOs are the domain models, so Phase 3 sync can push exactly what is stored.
 */

interface TripRepository {
    /** Not deleted, newest start date first. */
    fun observeTrips(): Flow<List<TripDto>>
    fun observeTrip(tripId: String): Flow<TripDto?>
    fun observeSummary(tripId: String): Flow<TripSummaryDto>
    suspend fun create(request: CreateTripRequest): TripDto
    suspend fun update(tripId: String, request: UpdateTripRequest)
    /** Soft delete; takes the trip's itinerary, packing and expenses with it. */
    suspend fun delete(tripId: String)
}

interface ItineraryRepository {
    /** Unscheduled first (dayDate = null), then by day and sortOrder. */
    fun observe(tripId: String): Flow<List<ItineraryItemDto>>
    /** Insert or replace by `input.id`. Also used to schedule an unscheduled place or move it to another day. */
    suspend fun save(tripId: String, input: ItineraryItemInput)
    suspend fun delete(tripId: String, itemId: String)
    /** Result of one drag (domain.reorder), applied in one transaction. Undo sends the previous list again. */
    suspend fun applyDayOrder(tripId: String, date: LocalDate, items: List<DayOrderItem>)
}

interface PackingRepository {
    fun observe(tripId: String): Flow<List<PackingItemDto>>
    suspend fun add(tripId: String, request: CreatePackingItemRequest)
    suspend fun update(tripId: String, itemId: String, patch: PatchPackingItemRequest)
    suspend fun delete(tripId: String, itemId: String)
    suspend fun uncheckAll(tripId: String)
}

interface ExpenseRepository {
    /** Newest first. */
    fun observe(tripId: String): Flow<List<ExpenseDto>>
    /** Insert or replace by `input.id`. `paidByUserId == null` = the local user. */
    suspend fun save(tripId: String, input: ExpenseInput)
    suspend fun delete(tripId: String, expenseId: String)
}
