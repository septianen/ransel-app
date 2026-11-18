package app.ransel.server.service

import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.TravelModes
import app.ransel.server.db.toTravelModeDto
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.domain.validateTravelMode
import app.ransel.shared.dto.TravelModeDto
import app.ransel.shared.dto.TravelModeInput
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

object TravelModeService {
    fun list(userId: UUID, tripId: UUID): List<TravelModeDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            TravelModes.selectAll()
                .where { (TravelModes.tripId eq tripId) and TravelModes.deletedAt.isNull() }
                .orderBy(TravelModes.updatedAt to SortOrder.ASC)
                .map { it.toTravelModeDto() }
        }
    }

    fun upsert(userId: UUID, tripId: UUID, body: TravelModeInput): TravelModeDto {
        requireRole(tripId, userId, Role.EDITOR)
        val id = parse(body.id, "id")
        val fromId = parse(body.fromItemId, "fromItemId")
        val toId = parse(body.toItemId, "toItemId")
        requireValid(validateTravelMode(body.fromItemId, body.toItemId, body.mode) + itemsInTrip(tripId, fromId, toId))
        return transaction {
            val byPair = TravelModes.selectAll()
                .where { (TravelModes.fromItemId eq fromId) and (TravelModes.toItemId eq toId) }
                .singleOrNull()
            val byId = TravelModes.selectAll().where { TravelModes.id eq id }.singleOrNull()
            when {
                byPair != null -> {
                    if (byPair[TravelModes.tripId] != tripId) throw validationFailed(mapOf("fromItemId" to "NOT_IN_TRIP"))
                    TravelModes.update({ TravelModes.id eq byPair[TravelModes.id] }) {
                        it[mode] = body.mode
                        it[deletedAt] = null
                    }
                    load(tripId, byPair[TravelModes.id])
                }
                byId != null -> {
                    if (byId[TravelModes.tripId] != tripId) throw validationFailed(mapOf("id" to "NOT_IN_TRIP"))
                    TravelModes.update({ TravelModes.id eq id }) {
                        it[TravelModes.fromItemId] = fromId
                        it[TravelModes.toItemId] = toId
                        it[mode] = body.mode
                        it[deletedAt] = null
                    }
                    load(tripId, id)
                }
                else -> {
                    TravelModes.insert {
                        it[TravelModes.id] = id
                        it[TravelModes.tripId] = tripId
                        it[fromItemId] = fromId
                        it[toItemId] = toId
                        it[mode] = body.mode
                    }
                    load(tripId, id)
                }
            }
        }
    }

    fun delete(userId: UUID, tripId: UUID, modeId: UUID) {
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            load(tripId, modeId)
            TravelModes.update({ TravelModes.id eq modeId }) { it[deletedAt] = Instant.now() }
        }
    }

    /** The item exists on this trip (a soft-deleted one still counts, so a saved pair is not lost). */
    fun itemsInTrip(tripId: UUID, fromId: UUID, toId: UUID): Map<String, String> = buildMap {
        if (!itemInTrip(tripId, fromId)) put("fromItemId", "NOT_IN_TRIP")
        if (!itemInTrip(tripId, toId)) put("toItemId", "NOT_IN_TRIP")
    }

    private fun itemInTrip(tripId: UUID, itemId: UUID): Boolean =
        ItineraryItems.selectAll().where { (ItineraryItems.id eq itemId) and (ItineraryItems.tripId eq tripId) }.any()

    private fun parse(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validationFailed(mapOf(field to "INVALID_UUID"))

    private fun load(tripId: UUID, id: UUID): TravelModeDto =
        TravelModes.selectAll()
            .where { (TravelModes.tripId eq tripId) and (TravelModes.id eq id) and TravelModes.deletedAt.isNull() }
            .singleOrNull()?.toTravelModeDto() ?: throw notFound("Travel mode")
}
