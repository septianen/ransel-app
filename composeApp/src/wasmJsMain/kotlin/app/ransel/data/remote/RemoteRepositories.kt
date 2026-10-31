package app.ransel.data.remote

import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PersonalItemRepository
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.data.repository.TripRepository
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.DayOrderItem
import app.ransel.shared.dto.DayOrderRequest
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
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalDate

/*
 * Web (online-only) repositories: every call goes to the API (docs/TRD.md "Architecture"). Same
 * interfaces as the Room ones, so ViewModels and screens are shared.
 */

internal class RemoteTripRepository(private val gateway: RemoteGateway) : TripRepository {
    private val api get() = gateway.api

    override fun observeTrips(): Flow<List<TripDto>> = gateway.observe { api.trips() }

    override fun observeTrip(tripId: String): Flow<TripDto?> =
        gateway.observe<TripDto?>(whenMissing = { null }) { api.tripDetail(tripId).map { it.trip } }

    override fun observeSummary(tripId: String): Flow<TripSummaryDto> =
        gateway.observe { api.tripDetail(tripId).map { it.summary } }

    override suspend fun create(request: CreateTripRequest) = gateway.mutate { api.createTrip(request) }

    override suspend fun update(tripId: String, request: UpdateTripRequest) =
        gateway.mutate { api.updateTrip(tripId, request) }

    override suspend fun delete(tripId: String) = gateway.mutate { api.deleteTrip(tripId) }

    /** The upload already went to the server; every trip on screen just loads again. */
    override suspend fun applyCover(tripId: String, coverUrl: String?) = gateway.mutate { ApiResult.Success(Unit) }
}

internal class RemoteItineraryRepository(private val gateway: RemoteGateway) : ItineraryRepository {
    private val api get() = gateway.api

    /** Ids seen in the latest list: PUT (replace) for those, POST (create) for new ones. */
    private val known = HashSet<String>()

    override fun observe(tripId: String): Flow<List<ItineraryItemDto>> =
        gateway.observe(whenMissing = { emptyList() }) { api.itinerary(tripId) }
            .onEach {
                known.clear()
                known.addAll(it.map { item -> item.id })
            }

    override suspend fun save(tripId: String, input: ItineraryItemInput) = gateway.mutate {
        if (input.id in known) {
            val replaced = api.replaceItineraryItem(tripId, input)
            // Deleted elsewhere in the meantime: create it again instead of failing.
            if (replaced is ApiResult.Failure && replaced.error.isNotFound) api.createItineraryItem(tripId, input) else replaced
        } else {
            api.createItineraryItem(tripId, input)
        }
    }

    override suspend fun delete(tripId: String, itemId: String) =
        gateway.mutate { api.deleteItineraryItem(tripId, itemId) }

    override suspend fun applyDayOrder(tripId: String, date: LocalDate, items: List<DayOrderItem>) =
        gateway.mutate { api.reorderDay(tripId, date, DayOrderRequest(items)) }
}

internal class RemotePackingRepository(private val gateway: RemoteGateway) : PackingRepository {
    private val api get() = gateway.api

    override fun observe(tripId: String): Flow<List<PackingItemDto>> =
        gateway.observe(whenMissing = { emptyList() }) { api.packing(tripId) }

    override suspend fun add(tripId: String, request: CreatePackingItemRequest) =
        gateway.mutate { api.createPackingItem(tripId, request) }

    override suspend fun update(tripId: String, itemId: String, patch: PatchPackingItemRequest) =
        gateway.mutate { api.patchPackingItem(tripId, itemId, patch) }

    override suspend fun delete(tripId: String, itemId: String) =
        gateway.mutate { api.deletePackingItem(tripId, itemId) }

    override suspend fun uncheckAll(tripId: String) = gateway.mutate { api.uncheckAllPacking(tripId) }

    override suspend fun bring(tripId: String, itemId: String, memberId: String) =
        gateway.mutate { api.bringPackingItem(tripId, itemId) }

    override suspend fun giveBack(tripId: String, itemId: String) = gateway.mutate { api.giveBackPackingItem(tripId, itemId) }
}

internal class RemotePersonalItemRepository(private val gateway: RemoteGateway) : PersonalItemRepository {
    private val api get() = gateway.api

    override fun observe(tripId: String): Flow<List<PersonalItemDto>> =
        gateway.observe(whenMissing = { emptyList() }) { api.personalItems(tripId) }

    override suspend fun add(tripId: String, request: CreatePersonalItemRequest) =
        gateway.mutate { api.createPersonalItem(tripId, request) }

    override suspend fun update(tripId: String, itemId: String, patch: PatchPersonalItemRequest) =
        gateway.mutate { api.patchPersonalItem(tripId, itemId, patch) }

    override suspend fun delete(tripId: String, itemId: String) = gateway.mutate { api.deletePersonalItem(tripId, itemId) }
}

internal class RemoteExpenseRepository(private val gateway: RemoteGateway) : ExpenseRepository {
    private val api get() = gateway.api
    private val known = HashSet<String>()

    override fun observe(tripId: String): Flow<List<ExpenseDto>> =
        gateway.observe(whenMissing = { emptyList() }) { api.expenses(tripId) }
            .onEach {
                known.clear()
                known.addAll(it.map { expense -> expense.id })
            }

    override suspend fun save(tripId: String, input: ExpenseInput) = gateway.mutate {
        if (input.id in known) {
            val replaced = api.replaceExpense(tripId, input)
            if (replaced is ApiResult.Failure && replaced.error.isNotFound) api.createExpense(tripId, input) else replaced
        } else {
            api.createExpense(tripId, input)
        }
    }

    override suspend fun delete(tripId: String, expenseId: String) =
        gateway.mutate { api.deleteExpense(tripId, expenseId) }
}
