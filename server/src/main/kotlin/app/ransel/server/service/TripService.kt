package app.ransel.server.service

import app.ransel.server.db.Expenses
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PackingItems
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.toExpenseDto
import app.ransel.server.db.toItineraryItemDto
import app.ransel.server.db.toTripDto
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.domain.itemsOutsideRange
import app.ransel.shared.domain.summarizeExpenses
import app.ransel.shared.domain.toUnscheduledInput
import app.ransel.shared.domain.validateTrip
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.TripDetailDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.shared.model.Role
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/** Trip rules. Authorization (`requireRole`) is done here so routes stay thin. */
object TripService {
    /** Timezone used for "which day is this expense on" until trips store one (owner decision, B2.7). */
    val SERVER_TIME_ZONE: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.of("Asia/Jakarta")

    fun list(userId: UUID): List<TripDto> = transaction {
        TripMembers.join(Trips, JoinType.INNER, TripMembers.tripId, Trips.id)
            .selectAll()
            .where { (TripMembers.userId eq userId) and Trips.deletedAt.isNull() }
            .orderBy(Trips.startDate to SortOrder.DESC, Trips.createdAt to SortOrder.DESC)
            .map { it.toTripDto(it[TripMembers.role]) }
    }

    fun create(userId: UUID, id: UUID, body: CreateTripRequest): TripDto = transaction {
        requireValid(validateTrip(body.title, body.startDate, body.endDate, body.baseCurrency, body.budgetMinor))
        if (Trips.select(Trips.id).where { Trips.id eq id }.any()) throw conflict("Trip id already exists")
        Trips.insert {
            it[Trips.id] = id
            it[ownerId] = userId
            it[title] = body.title.trim()
            it[destination] = body.destination
            it[startDate] = body.startDate.toJavaLocalDate()
            it[endDate] = body.endDate.toJavaLocalDate()
            it[baseCurrency] = body.baseCurrency
            it[budgetMinor] = body.budgetMinor
            it[coverUrl] = body.coverUrl
        }
        TripMembers.insert {
            it[tripId] = id
            it[TripMembers.userId] = userId
            it[role] = Role.OWNER
        }
        TripEvents.onTripSaved(body.destination, body.startDate)
        load(id, Role.OWNER)
    }

    fun detail(userId: UUID, tripId: UUID): TripDetailDto {
        val role = requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val trip = load(tripId, role)
            TripDetailDto(trip, summary(trip))
        }
    }

    fun update(userId: UUID, tripId: UUID, body: UpdateTripRequest): TripDto {
        val role = requireRole(tripId, userId, Role.EDITOR)
        return transaction {
            requireValid(validateTrip(body.title, body.startDate, body.endDate, body.baseCurrency, body.budgetMinor))
            Trips.update({ Trips.id eq tripId }) {
                it[title] = body.title.trim()
                it[destination] = body.destination
                it[startDate] = body.startDate.toJavaLocalDate()
                it[endDate] = body.endDate.toJavaLocalDate()
                it[baseCurrency] = body.baseCurrency
                it[budgetMinor] = body.budgetMinor
                it[coverUrl] = body.coverUrl
            }
            unscheduleOutsideRange(tripId, body.startDate, body.endDate)
            TripEvents.onTripSaved(body.destination, body.startDate)
            load(tripId, role)
        }
    }

    fun delete(userId: UUID, tripId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            val now = Instant.now()
            Trips.update({ Trips.id eq tripId }) { it[deletedAt] = now }
            ItineraryItems.update({ (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }) { it[deletedAt] = now }
            PackingItems.update({ (PackingItems.tripId eq tripId) and PackingItems.deletedAt.isNull() }) { it[deletedAt] = now }
            Expenses.update({ (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() }) { it[deletedAt] = now }
        }
    }

    /** Shortening a trip never deletes activities: those on removed days become unscheduled. Runs in the caller's transaction. */
    internal fun unscheduleOutsideRange(tripId: UUID, start: kotlinx.datetime.LocalDate, end: kotlinx.datetime.LocalDate) {
        val live = ItineraryItems.selectAll()
            .where { (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() and ItineraryItems.dayDate.isNotNull() }
            .map { it.toItineraryItemDto() }
        itemsOutsideRange(live, start, end).forEach { item ->
            val input = item.toUnscheduledInput()
            ItineraryItems.update({ ItineraryItems.id eq UUID.fromString(item.id) }) {
                it[dayDate] = null
                it[startTime] = null
                it[endTime] = null
                it[durationMin] = input.durationMin
            }
        }
    }

    private fun load(tripId: UUID, role: Role): TripDto =
        Trips.selectAll().where { Trips.id eq tripId }.singleOrNull()?.toTripDto(role) ?: throw tripNotFound()

    /** Counts come from SQL; money goes through the shared [summarizeExpenses] so it equals the app's numbers. */
    private fun summary(trip: TripDto): TripSummaryDto {
        val tripId = UUID.fromString(trip.id)
        val start = trip.startDate.toJavaLocalDate()
        val end = trip.endDate.toJavaLocalDate()
        val liveItems = ItineraryItems.tripId eq tripId and ItineraryItems.deletedAt.isNull()
        val scheduled = ItineraryItems.selectAll().where { liveItems and ItineraryItems.dayDate.isNotNull() }.count().toInt()
        val total = ItineraryItems.selectAll().where { liveItems }.count().toInt()
        val daysFilled = ItineraryItems.select(ItineraryItems.dayDate).withDistinct().where {
            liveItems and (ItineraryItems.dayDate greaterEq start) and (ItineraryItems.dayDate lessEq end)
        }.count().toInt()
        val livePacking = PackingItems.tripId eq tripId and PackingItems.deletedAt.isNull()
        val packingTotal = PackingItems.selectAll().where { livePacking }.count().toInt()
        val packingPacked = PackingItems.selectAll().where { livePacking and (PackingItems.isPacked eq true) }.count().toInt()
        val expenses = Expenses.selectAll()
            .where { (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() }
            .map { it.toExpenseDto() }
        val money = summarizeExpenses(trip.baseCurrency, trip.budgetMinor, expenses, SERVER_TIME_ZONE)
        return TripSummaryDto(
            daysTotal = trip.startDate.daysUntil(trip.endDate) + 1,
            daysFilled = daysFilled,
            activityCount = scheduled,
            unscheduledCount = total - scheduled,
            packingTotal = packingTotal,
            packingPacked = packingPacked,
            expenseCount = expenses.size,
            spentMinor = money.spentMinor,
            remainingMinor = money.remainingMinor,
            memberCount = TripMembers.selectAll().where { TripMembers.tripId eq tripId }.count().toInt(),
        )
    }
}
