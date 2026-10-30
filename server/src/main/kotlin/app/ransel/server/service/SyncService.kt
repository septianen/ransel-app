package app.ransel.server.service

import app.ransel.server.db.ExpenseSplits
import app.ransel.server.db.Expenses
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PackingItems
import app.ransel.server.db.PersonalItems
import app.ransel.server.db.toPersonalItemDto
import app.ransel.server.db.Settlements
import app.ransel.server.db.toSettlementDto
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
import app.ransel.shared.domain.dayFitsTrip
import app.ransel.shared.domain.toUnscheduledInput
import app.ransel.shared.domain.validateTrip
import app.ransel.shared.domain.validateTripLocale
import app.ransel.shared.domain.validateTripPlace
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.SYNC_MAX_TRIPS_PER_PAGE
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.dto.SyncRejection
import app.ransel.shared.dto.SyncRowRef
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.shared.model.SettlementStatus
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
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
        val total = changes.trips.size + changes.itineraryItems.size + changes.packingItems.size + changes.personalItems.size +
            changes.expenses.size + changes.settlements.size
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
            changes.personalItems.forEach { dto -> attempt(ENTITY_PERSONAL, dto.id) { pushPersonal(userId, dto) } }
            changes.expenses.forEach { dto -> attempt(ENTITY_EXPENSE, dto.id) { pushExpense(userId, dto) } }
            changes.settlements.forEach { dto -> attempt(ENTITY_SETTLEMENT, dto.id) { pushSettlement(userId, dto) } }

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

    /**
     * Changes since [since]. With [tripLimit] only that many of the user's trips (id order, after [afterTripId]) are
     * covered and `nextTripId` names where the next page starts; without it every trip comes at once.
     */
    fun pull(userId: UUID, since: kotlin.time.Instant?, tripLimit: Int? = null, afterTripId: UUID? = null): SyncPullResponse = transaction {
        // Taken before reading: a concurrent transaction that started earlier but commits after our snapshot
        // would otherwise fall between two pulls. Re-delivered rows are harmless (LWW).
        val serverTime = pullCursor()
        val sinceJava = since?.toJavaInstant()

        val allRoles = TripMembers.selectAll().where { (TripMembers.userId eq userId) and TripMembers.active() }
            .associate { it[TripMembers.tripId] to it[TripMembers.role] }
        // Ordered by the id's text, which the client sends back as afterTripId (UUID.compareTo is signed, so not that).
        val ordered = allRoles.keys.sortedBy { it.toString() }
        val remaining = if (afterTripId == null) ordered else ordered.filter { it.toString() > afterTripId.toString() }
        val pageSize = tripLimit?.coerceAtMost(SYNC_MAX_TRIPS_PER_PAGE)
        val page = if (pageSize == null) remaining else remaining.take(pageSize)
        val nextTripId = if (pageSize != null && remaining.size > pageSize) page.last().toString() else null
        val myRoles = allRoles.filterKeys { it in page }
        val allTripIds = page
        // Trips I joined after `since` (invite accepted): their rows are older than my cursor, so a plain
        // `updated_at > since` filter would never deliver them. Send those trips whole.
        val newTripIds = if (sinceJava == null) emptyList() else TripMembers.selectAll()
            .where { (TripMembers.userId eq userId) and TripMembers.active() and (TripMembers.updatedAt greater sinceJava) }
            .map { it[TripMembers.tripId] }
            .filter { it in myRoles }

        val tripRows = if (allTripIds.isEmpty()) emptyList() else TripMembers.join(Trips, JoinType.INNER, TripMembers.tripId, Trips.id)
            .selectAll()
            .where {
                val mine = (TripMembers.userId eq userId) and TripMembers.active() and (Trips.id inList allTripIds)
                if (sinceJava == null) mine else mine and ((Trips.updatedAt greater sinceJava) or (Trips.id inList newTripIds))
            }
            .toList()
        // same order as the pages, so a page reads in id order too
        val trips = tripRows.map { it.toTripDto(it[TripMembers.role]) }.sortedBy { it.id }

        val items = if (allTripIds.isEmpty()) emptyList() else ItineraryItems.selectAll()
            .where {
                val inTrips = ItineraryItems.tripId inList allTripIds
                if (sinceJava == null) inTrips
                else inTrips and ((ItineraryItems.updatedAt greater sinceJava) or (ItineraryItems.tripId inList newTripIds))
            }.map { it.toItineraryItemDto() }

        val packing = if (allTripIds.isEmpty()) emptyList() else PackingItems.selectAll()
            .where {
                val inTrips = PackingItems.tripId inList allTripIds
                if (sinceJava == null) inTrips
                else inTrips and ((PackingItems.updatedAt greater sinceJava) or (PackingItems.tripId inList newTripIds))
            }.map { it.toPackingItemDto() }

        // my own lines only: another member's bag is theirs
        val myMemberIds = allTripIds.map { callerMemberId(it, userId) }
        val personal = if (allTripIds.isEmpty()) emptyList() else PersonalItems.selectAll()
            .where {
                val mine = PersonalItems.ownerMemberId inList myMemberIds
                if (sinceJava == null) mine
                else mine and ((PersonalItems.updatedAt greater sinceJava) or (PersonalItems.tripId inList newTripIds))
            }.map { it.toPersonalItemDto() }

        val expenseRows = if (allTripIds.isEmpty()) emptyList() else Expenses.selectAll()
            .where {
                val inTrips = Expenses.tripId inList allTripIds
                if (sinceJava == null) inTrips
                else inTrips and ((Expenses.updatedAt greater sinceJava) or (Expenses.tripId inList newTripIds))
            }.toList()
        val splits = if (expenseRows.isEmpty()) emptyMap() else ExpenseSplits.selectAll()
            .where { ExpenseSplits.expenseId inList expenseRows.map { it[Expenses.id] } }
            .groupBy({ it[ExpenseSplits.expenseId] }, { ExpenseSplitDto(it[ExpenseSplits.memberId].toString(), it[ExpenseSplits.shareMinor]) })
        val payers = ExpenseService.loadPayers(expenseRows.map { it[Expenses.id] })
        val expenseItems = ExpenseService.loadItems(expenseRows.map { it[Expenses.id] })
        val expenses = expenseRows.map {
            it.toExpenseDto(splits[it[Expenses.id]].orEmpty().sortedBy { s -> s.memberId }, payers[it[Expenses.id]].orEmpty(), expenseItems[it[Expenses.id]].orEmpty())
        }

        val settlements = if (allTripIds.isEmpty()) emptyList() else Settlements.selectAll()
            .where {
                val inTrips = Settlements.tripId inList allTripIds
                if (sinceJava == null) inTrips
                else inTrips and ((Settlements.updatedAt greater sinceJava) or (Settlements.tripId inList newTripIds))
            }.map { it.toSettlementDto() }

        // Membership rows of my trips (current members only; the OWNER also gets the phone of members added by name).
        // A removed member leaves no tombstone: that user simply stops receiving the trip, and their next push of its
        // rows is rejected TRIP_NOT_FOUND.
        val members = if (allTripIds.isEmpty()) emptyList() else TripMembers
            .join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .selectAll()
            .where {
                val inTrips = (TripMembers.tripId inList allTripIds) and TripMembers.active()
                if (sinceJava == null) inTrips
                else inTrips and ((TripMembers.updatedAt greater sinceJava) or (TripMembers.tripId inList newTripIds))
            }.map {
                val iAmOwner = myRoles[it[TripMembers.tripId]] == Role.OWNER
                TripMemberDto(
                    tripId = it[TripMembers.tripId].toString(),
                    memberId = it[TripMembers.id].toString(),
                    userId = it[TripMembers.userId]?.toString(),
                    displayName = it[TripMembers.displayName] ?: it[Users.displayName],
                    email = if (iAmOwner) it[Users.email] else null,
                    role = it[TripMembers.role],
                    joinedAt = it[TripMembers.joinedAt].toTimestamp(),
                    phone = if (iAmOwner) it[TripMembers.phone] else null,
                )
            }

        SyncPullResponse(
            changes = SyncChanges(
                trips = trips, itineraryItems = items, packingItems = packing, personalItems = personal, expenses = expenses, settlements = settlements,
                members = members,
            ),
            serverTime = serverTime.toTimestamp(),
            nextTripId = nextTripId,
        )
    }

    // ── push, per entity ────────────────────────────────────────────────────

    private fun pushTrip(userId: UUID, dto: TripDto) {
        val id = uuid(dto.id, "id")
        val problems = validateTrip(dto.title, dto.startDate, dto.endDate, dto.baseCurrency, dto.budgetMinor) + validateTripPlace(dto.destinationPlace) +
            validateTripLocale(dto.countryCode, dto.localCurrency, dto.timeZone)
        if (problems.isNotEmpty()) throw validation(problems)

        val existing = Trips.selectAll().where { Trips.id eq id }.singleOrNull()
        if (existing == null) {
            Trips.insert {
                it[Trips.id] = id
                it[ownerId] = userId // never trust the DTO: the caller owns what they create
                it[title] = dto.title.trim()
                it[destination] = dto.destination
                it[startDate] = dto.startDate?.toJavaLocalDate()
                it[endDate] = dto.endDate?.toJavaLocalDate()
                it[baseCurrency] = dto.baseCurrency
                it[budgetMinor] = dto.budgetMinor
                it[coverUrl] = dto.coverUrl
                TripService.fillPlace(it, dto.destinationPlace)
                TripService.fillLocale(it, dto.countryCode, dto.localCurrency, dto.timeZone)
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
            if (dto.deletedAt == null) TripEvents.onTripSaved(dto.destination, dto.startDate)
            TripMembers.insert {
                it[TripMembers.id] = callerMemberId(id, userId)
                it[tripId] = id
                it[TripMembers.userId] = userId
                it[role] = Role.OWNER
            }
            return
        }

        val role = memberRole(id, userId) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCode.TRIP_NOT_FOUND, "Trip not found")
        // Deleting is the OWNER's, and so is anything on a deleted trip: an EDITOR must not bring it back.
        val needed = if (dto.deletedAt != null || existing[Trips.deletedAt] != null) Role.OWNER else Role.EDITOR
        if (!role.atLeast(needed)) throw forbidden()
        requireNewer(dto.updatedAt, existing[Trips.updatedAt])

        Trips.update({ Trips.id eq id }) {
            TripService.placeUpdate(it, dto.destinationPlace, nameChanged = existing[Trips.destination] != dto.destination)
            TripService.localeUpdate(it, dto.countryCode, dto.localCurrency, dto.timeZone, nameChanged = existing[Trips.destination] != dto.destination)
            it[title] = dto.title.trim()
            it[destination] = dto.destination
            it[startDate] = dto.startDate?.toJavaLocalDate()
            it[endDate] = dto.endDate?.toJavaLocalDate()
            it[baseCurrency] = dto.baseCurrency
            it[budgetMinor] = dto.budgetMinor
            it[coverUrl] = dto.coverUrl
            it[deletedAt] = dto.deletedAt?.toJavaInstant()
        }
        if (dto.deletedAt == null) TripEvents.onTripSaved(dto.destination, dto.startDate)
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
        val asSent = ItineraryItemInput(
            id = dto.id, dayDate = dto.dayDate, startTime = dto.startTime, endTime = dto.endTime, title = dto.title,
            category = dto.category, placeName = dto.placeName, placeId = dto.placeId, lat = dto.lat, lng = dto.lng,
            durationMin = dto.durationMin, notes = dto.notes, bookingUrl = dto.bookingUrl, source = dto.source,
            sortOrder = dto.sortOrder,
        )
        val problems = ItineraryService.validate(asSent)
        if (problems.isNotEmpty()) throw validation(problems)
        // A day that is not (or no longer) a day of the trip, e.g. another member cleared or moved the dates while this
        // device was offline: the item is kept but unscheduled, like when a trip is shortened. The changed row comes
        // back on the next pull. (REST answers 422 OUTSIDE_TRIP instead.)
        val (start, end) = ItineraryService.tripDates(tripId)
        val input = if (dayFitsTrip(asSent.dayDate, start, end)) asSent else dto.toUnscheduledInput()

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

    /**
     * EDITOR+ write packing items; any member may bring an item (assignee = themselves) or give back their own. Once an
     * item has a bringer, only they tick it, as in PackingService.
     */
    private fun pushPacking(userId: UUID, dto: PackingItemDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        val role = requireMemberOnLiveTrip(tripId, userId)
        val problems = PackingService.validate(dto.name, dto.quantity)
        if (problems.isNotEmpty()) throw validation(problems)
        val me = callerMemberId(tripId, userId)
        val assignee = dto.assigneeMemberId?.let { uuid(it, "assigneeMemberId") }
        val existing = PackingItems.selectAll().where { PackingItems.id eq id }.singleOrNull()
        val before = existing?.get(PackingItems.assigneeMemberId)
        if (existing == null && assignee != null && role.atLeast(Role.EDITOR)) {
            // "Siapa yang bawa?" when an editor adds it
            PackingService.requireActiveMember(tripId, assignee.toString())
        } else if (assignee != before) {
            val allowed = (before == null && assignee == me) || (assignee == null && (before == me || role.atLeast(Role.EDITOR)))
            if (!allowed) throw forbidden()
        }
        // giving an item back takes its check mark with it
        val packed = dto.isPacked && !(assignee == null && before != null)
        if (assignee != null && assignee != me && packed != (existing?.get(PackingItems.isPacked) ?: false)) throw forbidden()

        if (existing == null) {
            if (!role.atLeast(Role.EDITOR)) throw forbidden()
            PackingItems.insert {
                it[PackingItems.id] = id
                it[PackingItems.tripId] = tripId
                it[category] = dto.category
                it[name] = dto.name.trim()
                it[quantity] = dto.quantity
                it[isPacked] = packed
                it[sortOrder] = dto.sortOrder
                it[assigneeMemberId] = assignee
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
        } else {
            if (existing[PackingItems.tripId] != tripId) throw conflict("Item belongs to another trip")
            requireNewer(dto.updatedAt, existing[PackingItems.updatedAt])
            if (!role.atLeast(Role.EDITOR)) {
                // a viewer only brings, gives back and ticks
                val same = existing[PackingItems.category] == dto.category && existing[PackingItems.name] == dto.name.trim() &&
                    existing[PackingItems.quantity] == dto.quantity && existing[PackingItems.sortOrder] == dto.sortOrder &&
                    (existing[PackingItems.deletedAt] == null) == (dto.deletedAt == null)
                if (!same) throw forbidden()
            }
            PackingItems.update({ PackingItems.id eq id }) {
                it[category] = dto.category
                it[name] = dto.name.trim()
                it[quantity] = dto.quantity
                it[isPacked] = packed
                it[sortOrder] = dto.sortOrder
                it[assigneeMemberId] = assignee
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
        }
    }

    /** The caller's own lines only. Lines from the owner's list are created by the server and only change their tick. */
    private fun pushPersonal(userId: UUID, dto: PersonalItemDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        requireMemberOnLiveTrip(tripId, userId)
        val me = callerMemberId(tripId, userId)
        if (uuid(dto.ownerMemberId, "ownerMemberId") != me) throw forbidden()
        val problems = PackingService.validate(dto.name, dto.quantity)
        if (problems.isNotEmpty()) throw validation(problems)
        val existing = PersonalItems.selectAll().where { PersonalItems.id eq id }.singleOrNull()
        if (existing == null) {
            if (dto.sourceItemId != null) throw forbidden()
            PersonalItems.insert {
                it[PersonalItems.id] = id
                it[PersonalItems.tripId] = tripId
                it[ownerMemberId] = me
                it[category] = dto.category
                it[name] = dto.name.trim()
                it[quantity] = dto.quantity
                it[isPacked] = dto.isPacked
                it[sortOrder] = dto.sortOrder
                it[required] = false
                it[customized] = false
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
            return
        }
        // someone else's line is not there for the caller
        if (existing[PersonalItems.tripId] != tripId || existing[PersonalItems.ownerMemberId] != me) throw conflict("Item belongs to someone else")
        requireNewer(dto.updatedAt, existing[PersonalItems.updatedAt])
        // a copy of the owner's list is the member's too; editing it detaches it from later changes of the list
        val fromList = existing[PersonalItems.sourceItemId] != null
        if (fromList && existing[PersonalItems.deletedAt] != null && dto.deletedAt == null) throw conflict("The line was removed from the list")
        val edited = dto.name.trim() != existing[PersonalItems.name] || dto.quantity != existing[PersonalItems.quantity] ||
            dto.category != existing[PersonalItems.category]
        PersonalItems.update({ PersonalItems.id eq id }) {
            if (fromList && edited) it[customized] = true
            it[category] = dto.category
            it[name] = dto.name.trim()
            it[quantity] = dto.quantity
            it[isPacked] = dto.isPacked
            it[sortOrder] = dto.sortOrder
            it[deletedAt] = dto.deletedAt?.toJavaInstant()
        }
    }

    private fun pushExpense(userId: UUID, dto: ExpenseDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        requireEditorOnLiveTrip(tripId, userId)
        val sent = ExpenseInput(
            id = dto.id, amountMinor = dto.amountMinor, currency = dto.currency, fxRateToBase = dto.fxRateToBase,
            category = dto.category, paymentMethod = dto.paymentMethod, note = dto.note, spentAt = dto.spentAt,
            paidByMemberId = dto.paidByMemberId, payers = dto.payers, splits = dto.splits,
            isEstimate = dto.isEstimate, items = dto.items, itemsSplit = dto.itemsSplit,
            fxSource = dto.fxSource, fxDate = dto.fxDate,
        )
        val existing = Expenses.selectAll().where { Expenses.id eq id }.singleOrNull()
        val payer = uuid(dto.paidByMemberId, "paidByMemberId")
        val input = ExpenseService.withItemSplits(sent.copy(paidByMemberId = payer.toString()), payer)
        val keep = if (existing == null) emptySet() else buildSet {
            add(existing[Expenses.paidByMemberId])
            ExpenseSplits.selectAll().where { ExpenseSplits.expenseId eq id }.forEach { add(it[ExpenseSplits.memberId]) }
            ExpenseService.loadPayers(listOf(id))[id].orEmpty().forEach { add(UUID.fromString(it.memberId)) }
        }
        // throws ApiException with the right code (SPLIT_TOTAL_MISMATCH, VALIDATION_FAILED)
        ExpenseService.validate(input, payer, tripId, keep)
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
        ExpenseService.replaceSplits(id, input.splits)
        ExpenseService.replacePayers(id, input.payers)
        ExpenseService.replaceItems(id, input.items)
        // Only a new expense notifies (or an estimate that became a payment); a retry or another edit does not.
        val wasEstimate = existing?.get(Expenses.isEstimate) == true
        if (dto.deletedAt == null && !input.isEstimate && (existing == null || wasEstimate)) Notify.emit { expenseAdded(tripId, id, userId) }
    }

    private fun pushSettlement(userId: UUID, dto: SettlementDto) {
        val id = uuid(dto.id, "id")
        val tripId = uuid(dto.tripId, "tripId")
        // Like the REST endpoint: any member (even a VIEWER) may record a payment, but the trip must be live.
        val trip = Trips.select(Trips.deletedAt).where { Trips.id eq tripId }.singleOrNull()
        if (trip == null || trip[Trips.deletedAt] != null || memberRole(tripId, userId) == null) {
            throw ApiException(HttpStatusCode.NotFound, ErrorCode.TRIP_NOT_FOUND, "Trip not found")
        }
        val from = uuid(dto.fromMemberId, "fromMemberId")
        val to = uuid(dto.toMemberId, "toMemberId")
        val problems = buildMap {
            if (dto.amountMinor <= 0) put("amountMinor", "MUST_BE_POSITIVE")
            if (from == to) put("toMemberId", "MUST_DIFFER_FROM_PAYER")
            putAll(BalanceService.memberProblems(tripId, from, to))
        }
        if (problems.isNotEmpty()) throw validation(problems)

        val role = memberRole(tripId, userId)!!
        val existing = Settlements.selectAll().where { Settlements.id eq id }.singleOrNull()
        if (existing == null) {
            // "Saya sudah bayar" is only said by the payer themselves
            if (dto.status == SettlementStatus.CLAIMED_PAID && from != callerMemberId(tripId, userId)) throw forbidden()
            Settlements.insert {
                it[Settlements.id] = id
                it[Settlements.tripId] = tripId
                it[fromMemberId] = from
                it[toMemberId] = to
                it[amountMinor] = dto.amountMinor
                it[settledAt] = dto.settledAt.toJavaInstant()
                it[status] = dto.status
                it[claimedAt] = if (dto.status == SettlementStatus.CLAIMED_PAID) (dto.claimedAt ?: dto.createdAt).toJavaInstant() else null
                it[createdAt] = dto.createdAt.toJavaInstant()
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
            if (dto.deletedAt == null) Notify.emit { settlementMarked(tripId, id, userId) }
        } else {
            if (existing[Settlements.tripId] != tripId) throw conflict("Settlement belongs to another trip")
            requireNewer(dto.updatedAt, existing[Settlements.updatedAt])
            val before = existing[Settlements.status]
            if (before == SettlementStatus.CONFIRMED && dto.status == SettlementStatus.CLAIMED_PAID) throw conflict("Settlement is already confirmed")
            val confirming = before == SettlementStatus.CLAIMED_PAID && dto.status == SettlementStatus.CONFIRMED
            if (confirming && !BalanceService.mayConfirm(tripId, userId, role, existing[Settlements.toMemberId])) throw forbidden()
            // Like REST, which has no edit: the people and the amount are fixed once recorded; only status and deletion change.
            val oldFrom = existing[Settlements.fromMemberId]
            val oldTo = existing[Settlements.toMemberId]
            if (from != oldFrom || to != oldTo || dto.amountMinor != existing[Settlements.amountMinor]) throw forbidden()
            // Removing or restoring one is for the people in it or the OWNER, as with the REST delete.
            val removedBefore = existing[Settlements.deletedAt] != null
            if (removedBefore != (dto.deletedAt != null) && !BalanceService.mayRemove(tripId, userId, role, oldFrom, oldTo)) throw forbidden()
            Settlements.update({ Settlements.id eq id }) {
                it[settledAt] = dto.settledAt.toJavaInstant()
                it[status] = dto.status
                it[deletedAt] = dto.deletedAt?.toJavaInstant()
            }
            if (confirming && dto.deletedAt == null) Notify.emit { settlementMarked(tripId, id, userId) }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun requireMemberOnLiveTrip(tripId: UUID, userId: UUID): Role {
        val trip = Trips.select(Trips.deletedAt).where { Trips.id eq tripId }.singleOrNull()
        val role = memberRole(tripId, userId)
        if (trip == null || role == null || trip[Trips.deletedAt] != null) {
            throw ApiException(HttpStatusCode.NotFound, ErrorCode.TRIP_NOT_FOUND, "Trip not found")
        }
        return role
    }

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

    /**
     * The `since` for the next pull. `updated_at` is `now()`, the start of the writing transaction, so a push batch
     * still running can commit rows stamped before any fixed margin. The cursor therefore never passes the start of
     * a transaction that is still open (the server is the only client of its database), minus a small margin.
     */
    private fun pullCursor(): Instant =
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
            """
            SELECT LEAST(now(), COALESCE(
                (SELECT min(xact_start) FROM pg_stat_activity
                  WHERE datname = current_database() AND xact_start IS NOT NULL AND pid <> pg_backend_pid()),
                now()))
            """.trimIndent(),
        ) { rs -> rs.next(); rs.getTimestamp(1).toInstant() }!!.minusSeconds(PULL_MARGIN_SECONDS)

    private companion object {
        const val MAX_ROWS = 1000
        const val PULL_MARGIN_SECONDS = 5L
        const val ENTITY_TRIP = "trip"
        const val ENTITY_ITEM = "itineraryItem"
        const val ENTITY_PACKING = "packingItem"
        const val ENTITY_PERSONAL = "personalItem"
        const val ENTITY_EXPENSE = "expense"
        const val ENTITY_SETTLEMENT = "settlement"
    }
}
