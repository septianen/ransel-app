package app.ransel.server.service

import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PackingItems
import app.ransel.server.db.Trips
import app.ransel.server.db.TripTemplates
import app.ransel.server.db.toItineraryItemDto
import app.ransel.server.db.toPackingItemDto
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.domain.templateItemsOf
import app.ransel.shared.domain.templatePackingOf
import app.ransel.shared.dto.CreateTripTemplateRequest
import app.ransel.shared.dto.TripTemplateDto
import app.ransel.shared.model.Role
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Trip templates ("Simpan jadi template trip" on the recap): a copy of a trip's itinerary by day number and its shared
 * packing list, owned by the user who saved it. Owner decision 26 Oct 2026: itinerary + packing.
 */
object TripTemplateService {
    fun list(userId: UUID): List<TripTemplateDto> = transaction {
        TripTemplates.selectAll().where { TripTemplates.ownerId eq userId }
            .orderBy(TripTemplates.createdAt to SortOrder.DESC)
            .map { it.toDto() }
    }

    /** Any member of the trip may save it for themselves. */
    fun create(userId: UUID, body: CreateTripTemplateRequest): TripTemplateDto {
        val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: throw validationFailed(mapOf("id" to "INVALID_UUID"))
        val tripId = runCatching { UUID.fromString(body.tripId) }.getOrNull() ?: throw notFound("Trip")
        val name = body.name.trim()
        requireValid(if (name.isEmpty()) mapOf("name" to "REQUIRED") else if (name.length > 60) mapOf("name" to "TOO_LONG") else emptyMap())
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            if (TripTemplates.selectAll().where { TripTemplates.id eq id }.any()) throw conflict("Template id already exists")
            val trip = Trips.selectAll().where { Trips.id eq tripId }.single()
            val start = trip[Trips.startDate].toKotlinLocalDate()
            val items = ItineraryItems.selectAll().where { (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }
                .map { it.toItineraryItemDto() }
            val packing = PackingItems.selectAll().where { (PackingItems.tripId eq tripId) and PackingItems.deletedAt.isNull() }
                .map { it.toPackingItemDto() }
            TripTemplates.insert {
                it[TripTemplates.id] = id
                it[ownerId] = userId
                it[TripTemplates.name] = name
                it[dayCount] = (java.time.temporal.ChronoUnit.DAYS.between(trip[Trips.startDate], trip[Trips.endDate]) + 1).toInt()
                it[destination] = trip[Trips.destination]
                it[TripTemplates.items] = templateItemsOf(start, items)
                it[TripTemplates.packing] = templatePackingOf(packing)
            }
            TripTemplates.selectAll().where { TripTemplates.id eq id }.single().toDto()
        }
    }

    fun delete(userId: UUID, id: UUID) {
        transaction {
            val deleted = TripTemplates.deleteWhere { (TripTemplates.id eq id) and (TripTemplates.ownerId eq userId) }
            if (deleted == 0) throw notFound("Template")
        }
    }

    private fun ResultRow.toDto() = TripTemplateDto(
        id = this[TripTemplates.id].toString(),
        name = this[TripTemplates.name],
        dayCount = this[TripTemplates.dayCount],
        destination = this[TripTemplates.destination],
        items = this[TripTemplates.items],
        packing = this[TripTemplates.packing],
        createdAt = this[TripTemplates.createdAt].toTimestamp(),
    )
}
