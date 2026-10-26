package app.ransel.server.service

import app.ransel.server.db.PersonalItems
import app.ransel.server.db.toPersonalItemDto
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.PersonalItemDto
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

/**
 * "Pribadi": a member's own packing list. Any member (VIEWER too: it is their own bag) reads and writes only their
 * own lines. A line from the owner's list follows that list; the member only ticks it.
 */
object PersonalItemService {
    fun list(userId: UUID, tripId: UUID): List<PersonalItemDto> {
        requireRole(tripId, userId, Role.VIEWER)
        val me = callerMemberId(tripId, userId)
        return transaction {
            PersonalItems.selectAll()
                .where { (PersonalItems.ownerMemberId eq me) and (PersonalItems.tripId eq tripId) and PersonalItems.deletedAt.isNull() }
                .orderBy(PersonalItems.category to SortOrder.ASC, PersonalItems.sortOrder to SortOrder.ASC)
                .map { it.toPersonalItemDto() }
        }
    }

    fun create(userId: UUID, tripId: UUID, id: UUID, body: CreatePersonalItemRequest): PersonalItemDto {
        requireRole(tripId, userId, Role.VIEWER)
        requireValid(PackingService.validate(body.name, body.quantity))
        val me = callerMemberId(tripId, userId)
        return transaction {
            if (PersonalItems.selectAll().where { PersonalItems.id eq id }.any()) throw conflict("Packing item id already exists")
            PersonalItems.insert {
                it[PersonalItems.id] = id
                it[PersonalItems.tripId] = tripId
                it[ownerMemberId] = me
                it[category] = body.category
                it[name] = body.name.trim()
                it[quantity] = body.quantity
                it[isPacked] = false
                it[sortOrder] = body.sortOrder
                it[required] = false
            }
            load(id)
        }
    }

    fun patch(userId: UUID, tripId: UUID, id: UUID, body: PatchPersonalItemRequest): PersonalItemDto {
        requireRole(tripId, userId, Role.VIEWER)
        requireValid(PackingService.validate(body.name ?: "x", body.quantity ?: 1))
        return transaction {
            val item = mine(userId, tripId, id)
            if (item.sourceItemId != null && (body.name != null || body.quantity != null || body.category != null)) throw forbidden()
            PersonalItems.update({ PersonalItems.id eq id }) {
                body.category?.let { v -> it[category] = v }
                body.name?.let { v -> it[name] = v.trim() }
                body.quantity?.let { v -> it[quantity] = v }
                body.isPacked?.let { v -> it[isPacked] = v }
                body.sortOrder?.let { v -> it[sortOrder] = v }
            }
            load(id)
        }
    }

    /** Own lines only: a line from the owner's list goes when the owner removes it. */
    fun delete(userId: UUID, tripId: UUID, id: UUID) {
        requireRole(tripId, userId, Role.VIEWER)
        transaction {
            val item = mine(userId, tripId, id)
            if (item.sourceItemId != null) throw forbidden()
            PersonalItems.update({ PersonalItems.id eq id }) { it[deletedAt] = Instant.now() }
        }
    }

    private fun mine(userId: UUID, tripId: UUID, id: UUID): PersonalItemDto {
        val item = PersonalItems.selectAll()
            .where { (PersonalItems.id eq id) and (PersonalItems.tripId eq tripId) and PersonalItems.deletedAt.isNull() }
            .singleOrNull()?.toPersonalItemDto() ?: throw notFound("Packing item")
        // someone else's line does not exist for the caller
        if (item.ownerMemberId != callerMemberId(tripId, userId).toString()) throw notFound("Packing item")
        return item
    }

    private fun load(id: UUID): PersonalItemDto = PersonalItems.selectAll().where { PersonalItems.id eq id }.single().toPersonalItemDto()
}
