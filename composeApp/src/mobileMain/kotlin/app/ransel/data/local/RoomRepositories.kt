package app.ransel.data.local

import androidx.room3.withWriteTransaction
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.repository.buildTripSummary
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.DayOrderItem
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.LOCAL_USER_ID
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.ui.theme.ThemeMode
import app.ransel.util.now
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/*
 * Local-first repositories (Android + iOS). Every write sets `updatedAt = now` and marks the row
 * PENDING; Phase 3 sync reads that. Multi-row writes run in one transaction.
 */

class RoomTripRepository(private val db: RanselDatabase) : TripRepository {
    override fun observeTrips(): Flow<List<TripDto>> = db.tripDao().observeAll().map { l -> l.map { it.toDto() } }

    override fun observeTrip(tripId: String): Flow<TripDto?> = db.tripDao().observe(tripId).map { it?.toDto() }

    override fun observeSummary(tripId: String): Flow<TripSummaryDto> = combine(
        db.tripDao().observe(tripId),
        db.itineraryDao().observe(tripId),
        db.packingDao().observe(tripId),
        db.expenseDao().observe(tripId),
    ) { trip, items, packing, expenses ->
        buildTripSummary(
            trip = requireNotNull(trip) { "trip $tripId not found" }.toDto(),
            items = items.map { it.toDto() },
            packing = packing.map { it.toDto() },
            expenses = expenses.map { it.toDto() },
        )
    }

    override suspend fun create(request: CreateTripRequest): TripDto {
        val stamp = now().toMillis()
        val entity = TripEntity(
            id = request.id, ownerId = LOCAL_USER_ID, title = request.title.trim(),
            destination = request.destination.trim(), startDate = request.startDate.toString(),
            endDate = request.endDate.toString(), baseCurrency = request.baseCurrency,
            budgetMinor = request.budgetMinor, coverUrl = request.coverUrl,
            createdAt = stamp, updatedAt = stamp, deletedAt = null,
        )
        db.tripDao().upsert(entity)
        return entity.toDto()
    }

    override suspend fun update(tripId: String, request: UpdateTripRequest) {
        val current = db.tripDao().get(tripId) ?: return
        db.tripDao().upsert(
            current.copy(
                title = request.title.trim(), destination = request.destination.trim(),
                startDate = request.startDate.toString(), endDate = request.endDate.toString(),
                baseCurrency = request.baseCurrency, budgetMinor = request.budgetMinor, coverUrl = request.coverUrl,
                updatedAt = now().toMillis(), syncState = SyncState.PENDING,
            ),
        )
    }

    override suspend fun delete(tripId: String) {
        val stamp = now().toMillis()
        db.withWriteTransaction {
            db.itineraryDao().softDeleteByTrip(tripId, stamp)
            db.packingDao().softDeleteByTrip(tripId, stamp)
            db.expenseDao().softDeleteByTrip(tripId, stamp)
            db.tripDao().softDelete(tripId, stamp)
        }
    }
}

class RoomItineraryRepository(private val db: RanselDatabase) : ItineraryRepository {
    override fun observe(tripId: String): Flow<List<ItineraryItemDto>> =
        db.itineraryDao().observe(tripId).map { l -> l.map { it.toDto() } }

    override suspend fun save(tripId: String, input: ItineraryItemInput) {
        val dao = db.itineraryDao()
        val stamp = now().toMillis()
        val created = dao.get(input.id)?.createdAt ?: stamp
        dao.upsert(
            ItineraryItemEntity(
                id = input.id, tripId = tripId, dayDate = input.dayDate?.toString(),
                startTime = input.startTime?.toString(), endTime = input.endTime?.toString(),
                title = input.title.trim(), category = input.category.name, placeName = input.placeName,
                placeId = input.placeId, lat = input.lat, lng = input.lng, durationMin = input.durationMin,
                notes = input.notes, bookingUrl = input.bookingUrl, source = input.source.name,
                sortOrder = input.sortOrder, createdAt = created, updatedAt = stamp, deletedAt = null,
            ),
        )
    }

    override suspend fun delete(tripId: String, itemId: String) {
        db.itineraryDao().softDelete(itemId, now().toMillis())
    }

    override suspend fun applyDayOrder(tripId: String, date: LocalDate, items: List<DayOrderItem>) {
        val stamp = now().toMillis()
        db.withWriteTransaction {
            items.forEach {
                db.itineraryDao().updateOrder(
                    tripId, it.id, it.sortOrder, it.startTime?.toString(), it.endTime?.toString(), stamp,
                )
            }
        }
    }
}

class RoomPackingRepository(private val db: RanselDatabase) : PackingRepository {
    override fun observe(tripId: String): Flow<List<PackingItemDto>> =
        db.packingDao().observe(tripId).map { l -> l.map { it.toDto() } }

    override suspend fun add(tripId: String, request: CreatePackingItemRequest) {
        val stamp = now().toMillis()
        db.packingDao().upsert(
            PackingItemEntity(
                id = request.id, tripId = tripId, category = request.category.name, name = request.name.trim(),
                quantity = request.quantity, isPacked = false, sortOrder = request.sortOrder,
                createdAt = stamp, updatedAt = stamp, deletedAt = null,
            ),
        )
    }

    override suspend fun update(tripId: String, itemId: String, patch: PatchPackingItemRequest) {
        val current = db.packingDao().get(itemId) ?: return
        db.packingDao().upsert(
            current.copy(
                category = patch.category?.name ?: current.category,
                name = patch.name?.trim() ?: current.name,
                quantity = patch.quantity ?: current.quantity,
                isPacked = patch.isPacked ?: current.isPacked,
                sortOrder = patch.sortOrder ?: current.sortOrder,
                updatedAt = now().toMillis(), syncState = SyncState.PENDING,
            ),
        )
    }

    override suspend fun delete(tripId: String, itemId: String) {
        db.packingDao().softDelete(itemId, now().toMillis())
    }

    override suspend fun uncheckAll(tripId: String) {
        db.packingDao().uncheckAll(tripId, now().toMillis())
    }
}

class RoomExpenseRepository(private val db: RanselDatabase) : ExpenseRepository {
    override fun observe(tripId: String): Flow<List<ExpenseDto>> =
        db.expenseDao().observe(tripId).map { l -> l.map { it.toDto() } }

    override suspend fun save(tripId: String, input: ExpenseInput) {
        val dao = db.expenseDao()
        val stamp = now().toMillis()
        val created = dao.get(input.id)?.createdAt ?: stamp
        dao.upsert(
            ExpenseEntity(
                id = input.id, tripId = tripId, amountMinor = input.amountMinor, currency = input.currency,
                fxRateToBase = input.fxRateToBase, category = input.category.name,
                paymentMethod = input.paymentMethod.name, note = input.note?.trim()?.ifEmpty { null },
                spentAt = input.spentAt.toMillis(), paidByUserId = input.paidByUserId ?: LOCAL_USER_ID,
                createdAt = created, updatedAt = stamp, deletedAt = null,
            ),
        )
    }

    override suspend fun delete(tripId: String, expenseId: String) {
        db.expenseDao().softDelete(expenseId, now().toMillis())
    }
}

class RoomPreferencesRepository(private val db: RanselDatabase) : PreferencesRepository {
    override fun observe(): Flow<AppPreferences> = db.preferenceDao().observeAll().map { rows ->
        val map = rows.associate { it.key to it.value }
        AppPreferences(
            themeMode = map[THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            defaultCurrency = map[CURRENCY] ?: "IDR",
            skipReorderConfirmation = map[SKIP_REORDER_CONFIRM] == "true",
            onboardingCompleted = map[ONBOARDING_DONE] == "true",
            language = map[LANGUAGE]?.let { v -> AppLanguage.entries.firstOrNull { it.name == v } } ?: AppLanguage.SYSTEM,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) = put(THEME, mode.name)
    override suspend fun setDefaultCurrency(currency: String) = put(CURRENCY, currency)
    override suspend fun setSkipReorderConfirmation(skip: Boolean) = put(SKIP_REORDER_CONFIRM, skip.toString())
    override suspend fun setOnboardingCompleted(completed: Boolean) = put(ONBOARDING_DONE, completed.toString())
    override suspend fun setLanguage(language: AppLanguage) = put(LANGUAGE, language.name)

    private suspend fun put(key: String, value: String) = db.preferenceDao().set(PreferenceEntity(key, value))

    private companion object {
        const val THEME = "theme_mode"
        const val CURRENCY = "default_currency"
        const val SKIP_REORDER_CONFIRM = "skip_reorder_confirmation"
        const val ONBOARDING_DONE = "onboarding_completed"
        const val LANGUAGE = "language"
    }
}
