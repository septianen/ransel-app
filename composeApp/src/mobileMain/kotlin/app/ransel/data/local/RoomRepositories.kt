package app.ransel.data.local

import androidx.room3.withWriteTransaction
import app.ransel.data.repository.AppLanguage
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PersonalItemRepository
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
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.ui.theme.ThemeMode
import app.ransel.util.now
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/*
 * Local-first repositories (Android + iOS). Every write sets `updatedAt = now` and marks the row
 * PENDING; Phase 3 sync reads that. Multi-row writes run in one transaction.
 */

class RoomTripRepository(
    private val db: RanselDatabase,
    /** Owner of new trips: the signed-in user, or the guest placeholder. */
    private val currentUserId: () -> String = { LOCAL_USER_ID },
) : TripRepository {
    override fun observeTrips(): Flow<List<TripDto>> = db.tripDao().observeAll().map { l -> l.map { it.toDto() } }

    override fun observeTrip(tripId: String): Flow<TripDto?> = db.tripDao().observe(tripId).map { it?.toDto() }

    override fun observeSummary(tripId: String): Flow<TripSummaryDto> = combine(
        db.tripDao().observe(tripId),
        db.itineraryDao().observe(tripId),
        db.packingDao().observe(tripId),
        db.expenseDao().observe(tripId),
    ) { trip, items, packing, expenses ->
        // A deleted trip emits null once; skip it so a screen that is leaving does not crash.
        trip?.let {
            buildTripSummary(
                trip = it.toDto(),
                items = items.map { row -> row.toDto() },
                packing = packing.map { row -> row.toDto() },
                expenses = expenses.map { row -> row.toDto() },
            )
        }
    }.filterNotNull()

    override suspend fun create(request: CreateTripRequest) {
        val stamp = now().toMillis()
        val entity = TripEntity(
            id = request.id, ownerId = currentUserId(), title = request.title.trim(),
            destination = request.destination.trim(), startDate = request.startDate.toString(),
            endDate = request.endDate.toString(), baseCurrency = request.baseCurrency,
            budgetMinor = request.budgetMinor, coverUrl = request.coverUrl,
            createdAt = stamp, updatedAt = stamp, deletedAt = null,
            countryCode = request.countryCode?.uppercase(), localCurrency = request.localCurrency, timeZone = request.timeZone,
        ).withPlace(request.destinationPlace)
        db.tripDao().upsert(entity)
    }

    override suspend fun update(tripId: String, request: UpdateTripRequest) {
        val current = db.tripDao().get(tripId) ?: return
        val stamp = now().toMillis()
        db.withWriteTransaction {
            db.tripDao().upsert(
                current.copy(
                    title = request.title.trim(), destination = request.destination.trim(),
                    startDate = request.startDate.toString(), endDate = request.endDate.toString(),
                    baseCurrency = request.baseCurrency, budgetMinor = request.budgetMinor, coverUrl = request.coverUrl,
                    updatedAt = stamp, syncState = SyncState.PENDING,
                ).let {
                    // same rule as the server: no place keeps the stored one while the name stays
                    if (request.destinationPlace != null || current.destination != request.destination.trim()) it.withPlace(request.destinationPlace) else it
                }.let {
                    val sent = request.countryCode != null || request.localCurrency != null || request.timeZone != null
                    if (sent || current.destination != request.destination.trim()) {
                        it.copy(countryCode = request.countryCode?.uppercase(), localCurrency = request.localCurrency, timeZone = request.timeZone)
                    } else it
                },
            )
            // Shortened dates never delete activities: they go back to "Belum dijadwalkan".
            val outside = itemsOutsideRange(
                db.itineraryDao().getByTrip(tripId).map { it.toDto() }, request.startDate, request.endDate,
            )
            outside.forEach { item ->
                val input = item.toUnscheduledInput()
                val row = db.itineraryDao().get(item.id) ?: return@forEach
                db.itineraryDao().upsert(
                    row.copy(
                        dayDate = null, startTime = null, endTime = null, durationMin = input.durationMin,
                        updatedAt = stamp, syncState = SyncState.PENDING,
                    ),
                )
            }
        }
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
                createdAt = stamp, updatedAt = stamp, deletedAt = null, assigneeMemberId = request.assigneeMemberId,
            ),
        )
    }

    override suspend fun bring(tripId: String, itemId: String, memberId: String) {
        val current = db.packingDao().get(itemId) ?: return
        if (current.assigneeMemberId != null) return
        db.packingDao().upsert(current.copy(assigneeMemberId = memberId, updatedAt = now().toMillis(), syncState = SyncState.PENDING))
    }

    override suspend fun giveBack(tripId: String, itemId: String) {
        val current = db.packingDao().get(itemId) ?: return
        db.packingDao().upsert(current.copy(assigneeMemberId = null, isPacked = false, updatedAt = now().toMillis(), syncState = SyncState.PENDING))
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

class RoomPersonalItemRepository(
    private val db: RanselDatabase,
    private val currentUserId: () -> String,
) : PersonalItemRepository {
    override fun observe(tripId: String): Flow<List<PersonalItemDto>> =
        db.personalItemDao().observe(tripId).map { l -> l.map { it.toDto() } }

    override suspend fun add(tripId: String, request: CreatePersonalItemRequest) {
        val stamp = now().toMillis()
        db.personalItemDao().upsert(
            PersonalItemEntity(
                id = request.id, tripId = tripId, ownerMemberId = memberIdFor(tripId, currentUserId()),
                category = request.category.name, name = request.name.trim(), quantity = request.quantity, isPacked = false,
                sortOrder = request.sortOrder, sourceItemId = null, required = false,
                createdAt = stamp, updatedAt = stamp, deletedAt = null,
            ),
        )
    }

    override suspend fun update(tripId: String, itemId: String, patch: PatchPersonalItemRequest) {
        val current = db.personalItemDao().get(itemId) ?: return
        // a line from the owner's list only takes its tick
        val own = current.sourceItemId == null
        db.personalItemDao().upsert(
            current.copy(
                category = patch.category?.name?.takeIf { own } ?: current.category,
                name = patch.name?.trim()?.takeIf { own } ?: current.name,
                quantity = patch.quantity?.takeIf { own } ?: current.quantity,
                isPacked = patch.isPacked ?: current.isPacked,
                sortOrder = patch.sortOrder ?: current.sortOrder,
                updatedAt = now().toMillis(), syncState = SyncState.PENDING,
            ),
        )
    }

    override suspend fun delete(tripId: String, itemId: String) {
        db.personalItemDao().softDelete(itemId, now().toMillis())
    }
}

class RoomExpenseRepository(
    private val db: RanselDatabase,
    private val currentUserId: () -> String = { LOCAL_USER_ID },
) : ExpenseRepository {
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
                spentAt = input.spentAt.toMillis(),
                // with several payers the first one is "who paid"
                paidByMemberId = input.payers.firstOrNull()?.memberId ?: input.paidByMemberId ?: memberIdFor(tripId, currentUserId()),
                splitsJson = input.splits.toSplitsJson(), payersJson = input.payers.toPayersJson(),
                isEstimate = input.isEstimate, itemsJson = input.items.toItemsJson(),
                fxSource = input.fxSource?.name.takeIf { input.fxRateToBase != null }, fxDate = input.fxDate?.toString().takeIf { input.fxRateToBase != null },
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

    override suspend fun getString(key: String): String? = db.preferenceDao().get(key)?.ifEmpty { null }

    override suspend fun putString(key: String, value: String?) {
        if (value == null) db.preferenceDao().remove(key) else put(key, value)
    }

    private suspend fun put(key: String, value: String) = db.preferenceDao().set(PreferenceEntity(key, value))

    private companion object {
        const val THEME = "theme_mode"
        const val CURRENCY = "default_currency"
        const val SKIP_REORDER_CONFIRM = "skip_reorder_confirmation"
        const val ONBOARDING_DONE = "onboarding_completed"
        const val LANGUAGE = "language"
    }
}
