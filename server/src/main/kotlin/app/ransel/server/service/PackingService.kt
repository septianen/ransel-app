package app.ransel.server.service

import app.ransel.server.db.PackingItems
import app.ransel.server.db.TripMembers
import app.ransel.server.db.toPackingItemDto
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

object PackingService {
    fun list(userId: UUID, tripId: UUID): List<PackingItemDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            PackingItems.selectAll()
                .where { liveIn(tripId) }
                .orderBy(PackingItems.category to SortOrder.ASC, PackingItems.sortOrder to SortOrder.ASC)
                .map { it.toPackingItemDto() }
        }
    }

    fun create(userId: UUID, tripId: UUID, itemId: UUID, body: CreatePackingItemRequest): PackingItemDto {
        requireRole(tripId, userId, Role.EDITOR)
        requireValid(validate(body.name, body.quantity))
        return transaction {
            if (PackingItems.selectAll().where { PackingItems.id eq itemId }.any()) throw conflict("Packing item id already exists")
            val bringer = body.assigneeMemberId?.let { requireActiveMember(tripId, it) }
            PackingItems.insert {
                it[assigneeMemberId] = bringer
                it[id] = itemId
                it[PackingItems.tripId] = tripId
                it[category] = body.category
                it[name] = body.name.trim()
                it[quantity] = body.quantity
                it[sortOrder] = body.sortOrder
            }
            load(tripId, itemId)
        }
    }

    fun patch(userId: UUID, tripId: UUID, itemId: UUID, body: PatchPackingItemRequest): PackingItemDto {
        val role = requireRole(tripId, userId, Role.VIEWER)
        requireValid(validate(body.name ?: "x", body.quantity ?: 1))
        return transaction {
            val before = load(tripId, itemId)
            // a VIEWER only ticks what they bring
            val onlyTick = body.category == null && body.name == null && body.quantity == null && body.sortOrder == null
            if (!role.atLeast(Role.EDITOR) && !(onlyTick && before.assigneeMemberId == callerMemberId(tripId, userId).toString())) throw forbidden()
            if (body.isPacked != null) requireMayTick(tripId, userId, before)
            val changes = listOfNotNull(body.category, body.name, body.quantity, body.isPacked, body.sortOrder)
            if (changes.isNotEmpty()) {
                PackingItems.update({ PackingItems.id eq itemId }) {
                    body.category?.let { v -> it[category] = v }
                    body.name?.let { v -> it[name] = v.trim() }
                    body.quantity?.let { v -> it[quantity] = v }
                    body.isPacked?.let { v -> it[isPacked] = v }
                    body.sortOrder?.let { v -> it[sortOrder] = v }
                }
            }
            load(tripId, itemId)
        }
    }

    fun delete(userId: UUID, tripId: UUID, itemId: UUID) {
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            load(tripId, itemId)
            PackingItems.update({ PackingItems.id eq itemId }) { it[deletedAt] = Instant.now() }
        }
    }

    fun uncheckAll(userId: UUID, tripId: UUID) {
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            PackingItems.update({ liveIn(tripId) and (PackingItems.isPacked eq true) }) { it[isPacked] = false }
        }
    }

    /** "Saya bawa": any member takes an item nobody brings yet (or one they already bring). */
    fun bring(userId: UUID, tripId: UUID, itemId: UUID): PackingItemDto {
        requireRole(tripId, userId, Role.VIEWER)
        val me = callerMemberId(tripId, userId)
        return transaction {
            val item = load(tripId, itemId)
            if (item.assigneeMemberId != null && item.assigneeMemberId != me.toString()) throw conflict("Someone else brings it already")
            PackingItems.update({ PackingItems.id eq itemId }) { it[assigneeMemberId] = me }
            load(tripId, itemId)
        }
    }

    /** Gives an item back: the bringer, or an EDITOR+ for anyone. Its check mark goes with it. */
    fun unbring(userId: UUID, tripId: UUID, itemId: UUID): PackingItemDto {
        val role = requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val item = load(tripId, itemId)
            if (item.assigneeMemberId != callerMemberId(tripId, userId).toString() && !role.atLeast(Role.EDITOR)) throw forbidden()
            PackingItems.update({ PackingItems.id eq itemId }) {
                it[assigneeMemberId] = null
                it[isPacked] = false
            }
            load(tripId, itemId)
        }
    }

    /** Once someone brings an item, only they tick it (PRD "Shared packing items"); unclaimed items work as before. */
    internal fun requireMayTick(tripId: UUID, userId: UUID, item: PackingItemDto) {
        val bringer = item.assigneeMemberId ?: return
        if (bringer != callerMemberId(tripId, userId).toString()) throw forbidden()
    }

    /** "Siapa yang bawa?" names a member of this trip. */
    internal fun requireActiveMember(tripId: UUID, raw: String): UUID {
        val id = runCatching { UUID.fromString(raw) }.getOrNull()
        val ok = id != null && TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and (TripMembers.id eq id) and TripMembers.active() }.any()
        requireValid(if (ok) emptyMap() else mapOf("assigneeMemberId" to "NOT_A_MEMBER"))
        return id!!
    }

    private fun liveIn(tripId: UUID) = (PackingItems.tripId eq tripId) and PackingItems.deletedAt.isNull()

    private fun load(tripId: UUID, itemId: UUID): PackingItemDto =
        PackingItems.selectAll().where { liveIn(tripId) and (PackingItems.id eq itemId) }
            .singleOrNull()?.toPackingItemDto() ?: throw notFound("Packing item")

    internal fun validate(name: String, quantity: Int): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "REQUIRED")
        if (quantity <= 0) put("quantity", "MUST_BE_POSITIVE")
    }
}
