package app.ransel.server.service

import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.toItineraryItemDto
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.dto.DayOrderRequest
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.model.Role
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

object ItineraryService {
    fun list(userId: UUID, tripId: UUID): List<ItineraryItemDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            ItineraryItems.selectAll()
                .where { liveIn(tripId) }
                .orderBy(ItineraryItems.dayDate to SortOrder.ASC_NULLS_FIRST, ItineraryItems.sortOrder to SortOrder.ASC)
                .map { it.toItineraryItemDto() }
        }
    }

    fun create(userId: UUID, tripId: UUID, itemId: UUID, body: ItineraryItemInput): ItineraryItemDto {
        requireRole(tripId, userId, Role.EDITOR)
        requireValid(validate(body))
        return transaction {
            if (ItineraryItems.selectAll().where { ItineraryItems.id eq itemId }.any()) throw conflict("Item id already exists")
            ItineraryItems.insert {
                it[id] = itemId
                it[ItineraryItems.tripId] = tripId
                fill(it, body)
            }
            load(tripId, itemId)
        }
    }

    /** Full replacement; also how an unscheduled place gets a day and time. */
    fun replace(userId: UUID, tripId: UUID, itemId: UUID, body: ItineraryItemInput): ItineraryItemDto {
        requireRole(tripId, userId, Role.EDITOR)
        if (body.id != itemId.toString()) throw validationFailed(mapOf("id" to "MUST_MATCH_PATH"))
        requireValid(validate(body))
        return transaction {
            load(tripId, itemId)
            ItineraryItems.update({ ItineraryItems.id eq itemId }) { fill(it, body) }
            load(tripId, itemId)
        }
    }

    fun delete(userId: UUID, tripId: UUID, itemId: UUID) {
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            load(tripId, itemId)
            ItineraryItems.update({ ItineraryItems.id eq itemId }) { it[deletedAt] = Instant.now() }
        }
    }

    /** Applies one drag result (order + times of a day) in a single transaction; returns that day, ordered. */
    fun reorderDay(userId: UUID, tripId: UUID, date: LocalDate, body: DayOrderRequest): List<ItineraryItemDto> {
        requireRole(tripId, userId, Role.EDITOR)
        val ids = body.items.map { UUID.fromString(it.id.also { raw -> parse(raw) }) }
        if (ids.toSet().size != ids.size) throw validationFailed(mapOf("items" to "DUPLICATE_ID"))
        val day = date.toJavaLocalDate()
        return transaction {
            val found = ItineraryItems.selectAll()
                .where { liveIn(tripId) and (ItineraryItems.dayDate eq day) and (ItineraryItems.id inList ids) }
                .map { it[ItineraryItems.id] }
                .toSet()
            val missing = ids.filterNot { it in found }
            if (missing.isNotEmpty()) throw validationFailed(mapOf("items" to "NOT_ON_THIS_DAY:${missing.first()}"))
            body.items.forEachIndexed { index, item ->
                ItineraryItems.update({ ItineraryItems.id eq ids[index] }) {
                    it[sortOrder] = item.sortOrder
                    it[startTime] = item.startTime?.toJavaLocalTime()
                    it[endTime] = item.endTime?.toJavaLocalTime()
                }
            }
            ItineraryItems.selectAll()
                .where { liveIn(tripId) and (ItineraryItems.dayDate eq day) }
                .orderBy(ItineraryItems.sortOrder to SortOrder.ASC)
                .map { it.toItineraryItemDto() }
        }
    }

    private fun parse(raw: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validationFailed(mapOf("items" to "INVALID_UUID"))

    private fun liveIn(tripId: UUID) = (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull()

    private fun load(tripId: UUID, itemId: UUID): ItineraryItemDto =
        ItineraryItems.selectAll()
            .where { liveIn(tripId) and (ItineraryItems.id eq itemId) }
            .singleOrNull()?.toItineraryItemDto() ?: throw notFound("Itinerary item")

    internal fun fill(it: UpdateBuilder<*>, body: ItineraryItemInput) {
        it[ItineraryItems.dayDate] = body.dayDate?.toJavaLocalDate()
        it[ItineraryItems.startTime] = body.startTime?.toJavaLocalTime()
        it[ItineraryItems.endTime] = body.endTime?.toJavaLocalTime()
        it[ItineraryItems.title] = body.title.trim()
        it[ItineraryItems.category] = body.category
        it[ItineraryItems.placeName] = body.placeName
        it[ItineraryItems.placeId] = body.placeId
        it[ItineraryItems.lat] = body.lat
        it[ItineraryItems.lng] = body.lng
        it[ItineraryItems.durationMin] = body.durationMin
        it[ItineraryItems.notes] = body.notes
        it[ItineraryItems.bookingUrl] = body.bookingUrl
        it[ItineraryItems.itemSource] = body.source
        it[ItineraryItems.sortOrder] = body.sortOrder
    }

    // endTime may be earlier than startTime (ends next day), so there is deliberately no time-order rule.
    internal fun validate(body: ItineraryItemInput): Map<String, String> = buildMap {
        if (body.title.isBlank()) put("title", "REQUIRED")
        if ((body.lat == null) != (body.lng == null)) put("lat", "LAT_LNG_BOTH_OR_NONE")
        if (body.lat != null && body.lat!! !in -90.0..90.0) put("lat", "OUT_OF_RANGE")
        if (body.lng != null && body.lng!! !in -180.0..180.0) put("lng", "OUT_OF_RANGE")
        if (body.durationMin != null && body.durationMin!! <= 0) put("durationMin", "MUST_BE_POSITIVE")
    }
}
