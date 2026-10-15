package app.ransel.server.service

import app.ransel.server.db.ExpenseSplits
import app.ransel.server.db.Expenses
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PackingItems
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toExpenseDto
import app.ransel.server.db.toItineraryItemDto
import app.ransel.server.db.toPackingItemDto
import app.ransel.server.db.toTimestamp
import app.ransel.server.db.toTripDto
import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.validateTrip
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.dto.SyncRejection
import app.ransel.shared.dto.SyncRowRef
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaInstant

/**
 * Offline-first sync (TRD "Offline-first sync").
 *
 * Push: last write wins per row. `updated_at` in the database is the SERVER clock (trigger), so the
 * incoming `updatedAt` (device clock, time of the edit) is compared with the time the server last wrote
 * the row. Rejections never abort the batch; only a database failure rolls the whole batch back.
 *
 * Pull: everything the user can see with `updated_at > since`, tombstones included.
 */
class SyncService {
    fun push(userId: UUID, changes: SyncChanges): SyncPushResponse {
        val total = changes.trips.size + changes.itineraryItems.size + changes.packingItems.size + changes.expenses.size
        if (total > MAX_ROWS) {
            throw ApiException(
                HttpStatusCode.UnprocessableEntity, ErrorCode.VALIDATION_FAILED, "Too many rows in one batch",
                mapOf("changes" to "TOO_MANY_ROWS_MAX_$MAX_ROWS"),
            )
        }
        val accepted = mutableListOf<SyncRowRef>()
        val rejected = mutableListOf<SyncRejection>()

        fun attempt(entity: String, id: String, block: () -> Unit) {
            try {
                block()
                accepted += SyncRowRef(entity, id)
            } catch (e: ApiException) {
                rejected += SyncRejection(entity, id, e.code, e.message)
            }
        }

        val serverTime = transaction {
            val touchedTrips = mutableListOf<TripDto>()
            // Order matters: a new trip must exist (with its OWNER row) before its children are checked.
            changes.trips.forEach { dto ->
                attempt(ENTITY_TRIP, dto.id) {
                    pushTrip(userId, dto)
                    if (dto.deletedAt == null) touchedTrips += dto
                }
            }
            changes.itineraryItems.forEach { dto -> attempt(ENTITY_ITEM, dto.id) { pushItem(userId, dto) } }
            changes.packingItems.forEach { dto -> attempt(ENTITY_PACKING, dto.id) { pushPacking(userId, dto) } }
            changes.expenses.forEach { dto -> attempt(ENTITY_EXPENSE, dto.id) { pushExpense(userId, dto) } }

            // A shortened trip must not keep activities on removed days, even if the client did not move them.
            touchedTrips.forEach { dto ->
                val id = UUID.fromString(dto.id)
                if (accepted.any { it.entity == ENTITY_TRIP && it.id == dto.id }) {
                    TripService.unscheduleOutsideRange(id, dto.startDate, dto.endDate)
                }
            }
            dbNow()
        }
        return SyncPushResponse(accepted, rejected, serverTime.toTimestamp())
    }

    fun pull(userId: UUID, since: kotlin.time.Instant?): SyncPullResponse = transaction {
        // Taken before reading, minus a margin: a concurrent transaction that started earlier but commits
        // after our snapshot would otherwise fall between two pulls. Re-delivered rows are harmless (LWW).
        val serverTime = dbNow().minusSeconds(PULL_MARGIN_SECONDS)
        val sinceJava = since?.toJavaInstant()

        val tripRows = TripMembers.join(Trips, JoinType.INNER, TripMembers.tripId, Trips.id)
            .selectAll()
            .where {
                val mine = TripMembers.userId eq userId
                if (sinceJava == null) mine else mine and (Trips.updatedAt greater sinceJava)
            }
            .toList()
        val myRoles = TripMembers.selectAll().where { TripMembers.userId eq userId }
            .associate { it[TripMembers.tripId] to it[TripMembers.role] }
        val allTripIds = myRoles.keys.toList()
        val trips = tripRows.map { it.toTripDto(it[TripMembers.role]) }

        val items = if (allTripIds.isEmpty()) emptyList() else ItineraryItems.selectAll()
            .where {
                val inTrips = ItineraryItems.tripId inList allTripIds
                if (sinceJava == null) inTrips else inTrips and (ItineraryItems.updatedAt greater sinceJava)
            }.map { it.toItineraryItemDto() }

        val packing = if (allTripIds.isEmpty()) emptyList() else PackingItems.selectAll()
            .where {
                val inTrips = PackingItems.tripId inList allTripIds
                if (sinceJava == null) inTrips else inTrips and (PackingItems.updatedAt greater sinceJava)
            }.map { it.toPackingItemDto() }

        val expenseRows = if (allTripIds.isEmpty()) emptyList() else Expenses.selectAll()
            .where {
                val inTrips = Expenses.tripId inList allTripIds
                if (sinceJava == null) inTrips else inTrips and (Expenses.updatedAt greater sinceJava)
            }.toList()
        val splits = if (expenseRows.isEmpty()) emptyMap() else ExpenseSplits.selectAll()
            .where { ExpenseSplits.expenseId inList expenseRows.map { it[Expenses.id] } }
            .groupBy({ it[ExpenseSplits.expenseId] }, { ExpenseSplitDto(it[ExpenseSplits.userId].toString(), it[ExpenseSplits.shareMinor]) })
        val expenses = expenseRows.map { it.toExpenseDto(splits[it[Expenses.id]].orEmpty().sortedBy { s -> s.userId }) }

        // Membership rows of my trips. A removed member leaves no tombstone (the table has no deleted_at):
        // that user simply stops receiving the trip, and their next push of its rows is rejected TRIP_NOT_FOUND.
        val members = if (allTripIds.isEmpty()) emptyList() else TripMembers
            .join(Users, JoinType.INNER, TripMembers.userId, Users.id)
            .selectAll()
            .where {
                val inTrips = TripMembers.tripId inList allTripIds
                if (sinceJava == null) inTrips else inTrips and (TripMembers.updatedAt greater sinceJava)
            }.map {
                TripMemberDto(
                    tripId = it[TripMembers.tripId].toString(),
                    userId = it[TripMembers.userId].toString(),
                    displayName = it[Users.displayName],
                    email = if (myRoles[it[TripMembers.tripId]] == Role.OWNER) it[Users.email] else null,
                    role = it[TripMembers.role],
                    joinedAt = it[TripMembers.joinedAt].toTimestamp(),
                )
            }

        SyncPullResponse(
            changes = SyncChanges(trips = trips, itineraryItems = items, packingItems = packing, expenses = expenses, members = members),
            serverTime = serverTime.toTimestamp(),
        )
    }

    // ── push, per entity ────────────────────────────────────────────────────

    private fun pushTrip(userId: UUID, dto: TripDto) {
        val id = uuid(dto.id, "id")
        val problems = validateTrip(dto.title, dto.startDate, dto.endDate, dto.baseCurrency, dto.budgetMinor)
        if (problems.isNotEmpty()) throw validation(problems)

        val existing = Trips.selectAll().where { Trips.id eq id }.singleOrNull()
        if (existing == null) {
            Trips.insert {
                it[Trips.id] = id
                it[ownerId] = userId // never trust the DTO: the caller owns what they create
                it[title] = dto.title.trim()
                it[destination] = dto.destination
                it[startDate] = dto.startDate.toJavaLocalDate()
                it[endDate] = dto.endDate.toJavaLocalDate()
                it[baseCurrency] = dto.baseCurrency
                it[budgetMinor] = dto.budgetMinor
                it[coverUrl] = dto.coverUrl
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
            TripMembers.insert {
                it[tripId] = id
                it[TripMembers.userId] = userId
                it[role] = Role.OWNER
            }
            return
        }

        val role = memberRole(id, userId) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCode.TRIP_NOT_FOUND, "Trip not found")
        val needed = if (dto.deletedAt != null) Role.OWNER else Role.EDITOR
        if (!role.atLeast(needed)) throw forbidden()
        requireNewer(dto.updatedAt, existing[Trips.updatedAt])

        Trips.update({ Trips.id eq id }) {
            it[title] = dto.title.trim()
            it[destination] = dto.destination
            it[startDate] = dto.startDate.toJavaLocalDate()
            it[endDate] = dto.endDate.toJavaLocalDate()
            it[baseCurrency] = dto.baseCurrency
            it[budgetMinor] = dto.budgetMinor
            it[coverUrl] = dto.coverUrl
            it[deletedAt] = dto.deletedAt?.toJavaInstant()
        }
        if (dto.deletedAt != null && existing[Trips.deletedAt] == null) {
            val now = Instant.now()
            ItineraryItems.update({ (ItineraryItems.tripId eq id) and ItineraryItems.deletedAt.isNull() }) { it[deletedAt] = now }
            PackingItems.update({ (PackingItems.tripId eq id) and PackingItems.deletedAt.isNull() }) { it[deletedAt] = now }
            Expenses.update({ (Expenses.tripId eq id) and Expenses.deletedAt.isNull() }) { it[deletedAt] = now }
        }
    }

    private fun pushItem(userId: UUID, dto: ItineraryItemDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        requireEditorOnLiveTrip(tripId, userId)
        val input = ItineraryItemInput(
            id = dto.id, dayDate = dto.dayDate, startTime = dto.startTime, endTime = dto.endTime, title = dto.title,
            category = dto.category, placeName = dto.placeName, placeId = dto.placeId, lat = dto.lat, lng = dto.lng,
            durationMin = dto.durationMin, notes = dto.notes, bookingUrl = dto.bookingUrl, source = dto.source,
            sortOrder = dto.sortOrder,
        )
        val problems = ItineraryService.validate(input)
        if (problems.isNotEmpty()) throw validation(problems)

        val existing = ItineraryItems.selectAll().where { ItineraryItems.id eq id }.singleOrNull()
        if (existing == null) {
            ItineraryItems.insert {
                it[ItineraryItems.id] = id
                it[ItineraryItems.tripId] = tripId
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
                ItineraryService.fill(it, input)
            }
        } else {
            if (existing[ItineraryItems.tripId] != tripId) throw conflict("Item belongs to another trip")
            requireNewer(dto.updatedAt, existing[ItineraryItems.updatedAt])
            ItineraryItems.update({ ItineraryItems.id eq id }) {
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
                ItineraryService.fill(it, input)
            }
        }
    }

    private fun pushPacking(userId: UUID, dto: PackingItemDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        requireEditorOnLiveTrip(tripId, userId)
        val problems = PackingService.validate(dto.name, dto.quantity)
        if (problems.isNotEmpty()) throw validation(problems)

        val existing = PackingItems.selectAll().where { PackingItems.id eq id }.singleOrNull()
        if (existing == null) {
            PackingItems.insert {
                it[PackingItems.id] = id
                it[PackingItems.tripId] = tripId
                it[category] = dto.category
                it[name] = dto.name.trim()
                it[quantity] = dto.quantity
                it[isPacked] = dto.isPacked
                it[sortOrder] = dto.sortOrder
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
        } else {
            if (existing[PackingItems.tripId] != tripId) throw conflict("Item belongs to another trip")
            requireNewer(dto.updatedAt, existing[PackingItems.updatedAt])
            PackingItems.update({ PackingItems.id eq id }) {
                it[category] = dto.category
                it[name] = dto.name.trim()
                it[quantity] = dto.quantity
                it[isPacked] = dto.isPacked
                it[sortOrder] = dto.sortOrder
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
        }
    }

    private fun pushExpense(userId: UUID, dto: ExpenseDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        requireEditorOnLiveTrip(tripId, userId)
        val payer = uuid(dto.paidByUserId, "paidByUserId")
        val input = ExpenseInput(
            id = dto.id, amountMinor = dto.amountMinor, currency = dto.currency, fxRateToBase = dto.fxRateToBase,
            category = dto.category, paymentMethod = dto.paymentMethod, note = dto.note, spentAt = dto.spentAt,
            paidByUserId = dto.paidByUserId, splits = dto.splits,
        )
        ExpenseService.validate(input, payer) // throws ApiException with the right code (e.g. SPLIT_TOTAL_MISMATCH)

        val existing = Expenses.selectAll().where { Expenses.id eq id }.singleOrNull()
        if (existing == null) {
            Expenses.insert {
                it[Expenses.id] = id
                it[Expenses.tripId] = tripId
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
                ExpenseService.fill(it, input, payer)
            }
        } else {
            if (existing[Expenses.tripId] != tripId) throw conflict("Expense belongs to another trip")
            requireNewer(dto.updatedAt, existing[Expenses.updatedAt])
            Expenses.update({ Expenses.id eq id }) {
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
                ExpenseService.fill(it, input, payer)
            }
        }
        ExpenseService.replaceSplits(id, dto.splits)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun requireEditorOnLiveTrip(tripId: UUID, userId: UUID) {
        val trip = Trips.select(Trips.deletedAt).where { Trips.id eq tripId }.singleOrNull()
        val role = memberRole(tripId, userId)
        if (trip == null || role == null || trip[Trips.deletedAt] != null) {
            throw ApiException(HttpStatusCode.NotFound, ErrorCode.TRIP_NOT_FOUND, "Trip not found")
        }
        if (!role.atLeast(Role.EDITOR)) throw forbidden()
    }

    /** Last write wins: the incoming edit must be newer than what the server last wrote. */
    private fun requireNewer(incoming: kotlin.time.Instant, stored: Instant) {
        if (incoming.toJavaInstant() <= stored) throw conflict("Server copy is newer")
    }

    private fun uuid(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validation(mapOf(field to "INVALID_UUID"))

    private fun validation(details: Map<String, String>) =
        ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.VALIDATION_FAILED, "Validation failed", details)

    private fun forbidden() = ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "Your role does not allow this action")

    private fun conflict(message: String) = ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, message)

    /** The database clock: the same clock the `updated_at` trigger uses. */
    private fun dbNow(): Instant =
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
            .exec("SELECT now()") { rs -> rs.next(); rs.getTimestamp(1).toInstant() }!!

    private companion object {
        const val MAX_ROWS = 1000
        const val PULL_MARGIN_SECONDS = 5L
        const val ENTITY_TRIP = "trip"
        const val ENTITY_ITEM = "itineraryItem"
        const val ENTITY_PACKING = "packingItem"
        const val ENTITY_EXPENSE = "expense"
    }
}
