package app.ransel.data.fake

import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PersonalItemRepository
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.repository.buildTripSummary
import app.ransel.shared.domain.itemsOutsideRange
import app.ransel.shared.domain.toUnscheduledInput
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.DayOrderItem
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.LOCAL_USER_ID
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.ui.theme.ThemeMode
import app.ransel.util.now
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * In-memory repositories. Used by previews, UI tests, and (temporarily) the web target until the
 * remote repositories of Phase 2 / F2.2 replace them. Nothing here is persisted.
 */
class InMemoryStore {
    val trips = MutableStateFlow<List<TripDto>>(emptyList())
    val items = MutableStateFlow<List<ItineraryItemDto>>(emptyList())
    val packing = MutableStateFlow<List<PackingItemDto>>(emptyList())
    val expenses = MutableStateFlow<List<ExpenseDto>>(emptyList())
    val personal = MutableStateFlow<List<PersonalItemDto>>(emptyList())
}

class InMemoryPersonalItemRepository(private val store: InMemoryStore, private val ownerMemberId: (String) -> String) : PersonalItemRepository {
    override fun observe(tripId: String): Flow<List<PersonalItemDto>> = store.personal.map { list ->
        list.filter { it.tripId == tripId && it.deletedAt == null }.sortedWith(compareBy({ it.category }, { it.sortOrder }))
    }

    override suspend fun add(tripId: String, request: CreatePersonalItemRequest) {
        val stamp = now()
        store.personal.update {
            it + PersonalItemDto(
                id = request.id, tripId = tripId, ownerMemberId = ownerMemberId(tripId), category = request.category,
                name = request.name, quantity = request.quantity, sortOrder = request.sortOrder, createdAt = stamp, updatedAt = stamp,
            )
        }
    }

    override suspend fun update(tripId: String, itemId: String, patch: PatchPersonalItemRequest) {
        val stamp = now()
        store.personal.update { list ->
            list.map {
                if (it.id != itemId) it else it.copy(
                    category = patch.category ?: it.category, name = patch.name ?: it.name,
                    quantity = patch.quantity ?: it.quantity, isPacked = patch.isPacked ?: it.isPacked,
                    sortOrder = patch.sortOrder ?: it.sortOrder, updatedAt = stamp,
                )
            }
        }
    }

    override suspend fun delete(tripId: String, itemId: String) {
        val stamp = now()
        store.personal.update { l -> l.map { if (it.id == itemId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
    }
}

class InMemoryTripRepository(private val store: InMemoryStore) : TripRepository {
    override fun observeTrips(): Flow<List<TripDto>> =
        store.trips.map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.startDate } }

    override fun observeTrip(tripId: String): Flow<TripDto?> =
        store.trips.map { list -> list.firstOrNull { it.id == tripId && it.deletedAt == null } }

    override fun observeSummary(tripId: String): Flow<TripSummaryDto> =
        combine(store.trips, store.items, store.packing, store.expenses) { trips, items, packing, expenses ->
            trips.firstOrNull { it.id == tripId && it.deletedAt == null }?.let { trip ->
                buildTripSummary(
                    trip,
                    items.filter { it.tripId == tripId },
                    packing.filter { it.tripId == tripId },
                    expenses.filter { it.tripId == tripId },
                )
            }
        }.filterNotNull()

    override suspend fun create(request: CreateTripRequest) {
        val stamp = now()
        val trip = TripDto(
            id = request.id, ownerId = LOCAL_USER_ID, title = request.title, destination = request.destination,
            startDate = request.startDate, endDate = request.endDate, baseCurrency = request.baseCurrency,
            budgetMinor = request.budgetMinor, createdAt = stamp, updatedAt = stamp,
            destinationPlace = request.destinationPlace,
            countryCode = request.countryCode, localCurrency = request.localCurrency, timeZone = request.timeZone,
        )
        store.trips.update { it + trip }
    }

    override suspend fun update(tripId: String, request: UpdateTripRequest) {
        val outside = itemsOutsideRange(
            store.items.value.filter { it.tripId == tripId }, request.startDate, request.endDate,
        ).map { it.id }.toSet()
        if (outside.isNotEmpty()) {
            store.items.update { list ->
                list.map { row ->
                    if (row.id !in outside) row else {
                        val input = row.toUnscheduledInput()
                        row.copy(
                            dayDate = null, startTime = null, endTime = null,
                            durationMin = input.durationMin, updatedAt = now(),
                        )
                    }
                }
            }
        }
        store.trips.update { list ->
            list.map {
                if (it.id != tripId) it else it.copy(
                    title = request.title, destination = request.destination, startDate = request.startDate,
                    endDate = request.endDate, baseCurrency = request.baseCurrency, budgetMinor = request.budgetMinor,
                    updatedAt = now(),
                    destinationPlace = if (request.destinationPlace != null || it.destination != request.destination) request.destinationPlace else it.destinationPlace,
                ).let { t ->
                    val sent = request.countryCode != null || request.localCurrency != null || request.timeZone != null
                    if (sent || it.destination != request.destination) {
                        t.copy(countryCode = request.countryCode, localCurrency = request.localCurrency, timeZone = request.timeZone)
                    } else t
                }
            }
        }
    }

    override suspend fun delete(tripId: String) {
        val stamp = now()
        store.trips.update { l -> l.map { if (it.id == tripId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
        store.items.update { l -> l.map { if (it.tripId == tripId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
        store.packing.update { l -> l.map { if (it.tripId == tripId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
        store.expenses.update { l -> l.map { if (it.tripId == tripId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
    }

    override suspend fun applyCover(tripId: String, coverUrl: String?) {
        store.trips.update { l -> l.map { if (it.id == tripId) it.copy(coverUrl = coverUrl) else it } }
    }
}

class InMemoryItineraryRepository(private val store: InMemoryStore) : ItineraryRepository {
    override fun observe(tripId: String): Flow<List<ItineraryItemDto>> = store.items.map { list ->
        list.filter { it.tripId == tripId && it.deletedAt == null }
            .sortedWith(compareBy<ItineraryItemDto, LocalDate?>(nullsFirst()) { it.dayDate }.thenBy { it.sortOrder })
    }

    override suspend fun save(tripId: String, input: ItineraryItemInput) {
        val stamp = now()
        store.items.update { list ->
            val existing = list.firstOrNull { it.id == input.id }
            val item = ItineraryItemDto(
                id = input.id, tripId = tripId, dayDate = input.dayDate, startTime = input.startTime,
                endTime = input.endTime, title = input.title, category = input.category, placeName = input.placeName,
                placeId = input.placeId, lat = input.lat, lng = input.lng, durationMin = input.durationMin,
                notes = input.notes, bookingUrl = input.bookingUrl, source = input.source, sortOrder = input.sortOrder,
                createdAt = existing?.createdAt ?: stamp, updatedAt = stamp,
            )
            if (existing == null) list + item else list.map { if (it.id == input.id) item else it }
        }
    }

    override suspend fun delete(tripId: String, itemId: String) {
        val stamp = now()
        store.items.update { l -> l.map { if (it.id == itemId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
    }

    override suspend fun applyDayOrder(tripId: String, date: LocalDate, items: List<DayOrderItem>) {
        val byId = items.associateBy { it.id }
        val stamp = now()
        store.items.update { list ->
            list.map { row ->
                val o = byId[row.id] ?: return@map row
                row.copy(sortOrder = o.sortOrder, startTime = o.startTime, endTime = o.endTime, updatedAt = stamp)
            }
        }
    }
}

class InMemoryPackingRepository(private val store: InMemoryStore) : PackingRepository {
    override fun observe(tripId: String): Flow<List<PackingItemDto>> = store.packing.map { list ->
        list.filter { it.tripId == tripId && it.deletedAt == null }.sortedWith(compareBy({ it.category }, { it.sortOrder }))
    }

    override suspend fun add(tripId: String, request: CreatePackingItemRequest) {
        val stamp = now()
        store.packing.update {
            it + PackingItemDto(
                id = request.id, tripId = tripId, category = request.category, name = request.name,
                quantity = request.quantity, sortOrder = request.sortOrder, assigneeMemberId = request.assigneeMemberId,
                createdAt = stamp, updatedAt = stamp,
            )
        }
    }

    override suspend fun bring(tripId: String, itemId: String, memberId: String) {
        val stamp = now()
        store.packing.update { l -> l.map { if (it.id == itemId && it.assigneeMemberId == null) it.copy(assigneeMemberId = memberId, updatedAt = stamp) else it } }
    }

    override suspend fun giveBack(tripId: String, itemId: String) {
        val stamp = now()
        store.packing.update { l -> l.map { if (it.id == itemId) it.copy(assigneeMemberId = null, isPacked = false, updatedAt = stamp) else it } }
    }

    override suspend fun update(tripId: String, itemId: String, patch: PatchPackingItemRequest) {
        val stamp = now()
        store.packing.update { list ->
            list.map {
                if (it.id != itemId) it else it.copy(
                    category = patch.category ?: it.category, name = patch.name ?: it.name,
                    quantity = patch.quantity ?: it.quantity, isPacked = patch.isPacked ?: it.isPacked,
                    sortOrder = patch.sortOrder ?: it.sortOrder, updatedAt = stamp,
                )
            }
        }
    }

    override suspend fun delete(tripId: String, itemId: String) {
        val stamp = now()
        store.packing.update { l -> l.map { if (it.id == itemId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
    }

    override suspend fun uncheckAll(tripId: String) {
        val stamp = now()
        store.packing.update { l ->
            l.map { if (it.tripId == tripId && it.isPacked) it.copy(isPacked = false, updatedAt = stamp) else it }
        }
    }
}

class InMemoryExpenseRepository(private val store: InMemoryStore) : ExpenseRepository {
    override fun observe(tripId: String): Flow<List<ExpenseDto>> = store.expenses.map { list ->
        list.filter { it.tripId == tripId && it.deletedAt == null }.sortedByDescending { it.spentAt }
    }

    override suspend fun save(tripId: String, input: ExpenseInput) {
        val stamp = now()
        store.expenses.update { list ->
            val existing = list.firstOrNull { it.id == input.id }
            val row = ExpenseDto(
                id = input.id, tripId = tripId, amountMinor = input.amountMinor, currency = input.currency,
                fxRateToBase = input.fxRateToBase, category = input.category, paymentMethod = input.paymentMethod,
                note = input.note, spentAt = input.spentAt,
                paidByMemberId = input.payers.firstOrNull()?.memberId ?: input.paidByMemberId ?: memberIdFor(tripId, LOCAL_USER_ID),
                payers = input.payers, splits = input.splits, isEstimate = input.isEstimate,
                items = input.items, fxSource = input.fxSource, fxDate = input.fxDate, createdAt = existing?.createdAt ?: stamp, updatedAt = stamp,
            )
            if (existing == null) list + row else list.map { if (it.id == input.id) row else it }
        }
    }

    override suspend fun delete(tripId: String, expenseId: String) {
        val stamp = now()
        store.expenses.update { l -> l.map { if (it.id == expenseId) it.copy(deletedAt = stamp, updatedAt = stamp) else it } }
    }
}

class InMemoryPreferencesRepository(initial: AppPreferences = AppPreferences()) : PreferencesRepository {
    private val state = MutableStateFlow(initial)
    override fun observe(): Flow<AppPreferences> = state
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setDefaultCurrency(currency: String) = state.update { it.copy(defaultCurrency = currency) }
    override suspend fun setSkipReorderConfirmation(skip: Boolean) = state.update { it.copy(skipReorderConfirmation = skip) }
    override suspend fun setOnboardingCompleted(completed: Boolean) = state.update { it.copy(onboardingCompleted = completed) }
    override suspend fun setLanguage(language: AppLanguage) = state.update { it.copy(language = language) }
    private val strings = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = strings[key]
    override suspend fun putString(key: String, value: String?) { if (value == null) strings.remove(key) else strings[key] = value }
}
