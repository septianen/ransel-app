package app.ransel.server.service

import app.ransel.server.db.TripTastes
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.requireValid
import app.ransel.shared.domain.normalized
import app.ransel.shared.domain.validateTripTaste
import app.ransel.shared.dto.TripTasteDto
import app.ransel.shared.dto.TripTasteResponse
import app.ransel.shared.model.Interest
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.util.UUID

/**
 * A trip's Seleramu (`/trips/{id}/taste`, plan explore-trip-one-tap): every member reads it, OWNER and EDITOR save it.
 * A save replaces the whole taste (the sheet always sends all of it).
 */
object TripTasteService {
    fun get(userId: UUID, tripId: UUID): TripTasteResponse {
        requireRole(tripId, userId, Role.VIEWER)
        return load(tripId)
    }

    fun put(userId: UUID, tripId: UUID, body: TripTasteDto): TripTasteResponse {
        requireRole(tripId, userId, Role.EDITOR)
        requireValid(validateTripTaste(body))
        val taste = body.normalized()
        transaction {
            TripTastes.upsert {
                it[TripTastes.tripId] = tripId
                it[interests] = taste.interests.map { i -> i.name }
                it[extraInterests] = taste.extraInterests
                it[style] = taste.style
                it[priceLevel] = taste.priceLevel
                it[notes] = taste.notes
                it[updatedBy] = userId
                it[updatedAt] = Instant.now()
            }
        }
        return load(tripId)
    }

    private fun load(tripId: UUID): TripTasteResponse = transaction {
        TripTastes.selectAll().where { TripTastes.tripId eq tripId }.singleOrNull()?.let { row ->
            TripTasteResponse(
                taste = TripTasteDto(
                    // an unknown constant (a newer app wrote it) is skipped rather than failing the read
                    interests = row[TripTastes.interests].mapNotNull { n -> Interest.entries.firstOrNull { it.name == n } },
                    extraInterests = row[TripTastes.extraInterests],
                    style = row[TripTastes.style],
                    priceLevel = row[TripTastes.priceLevel],
                    notes = row[TripTastes.notes],
                ),
                updatedAt = row[TripTastes.updatedAt].toTimestamp(),
            )
        } ?: TripTasteResponse()
    }
}
