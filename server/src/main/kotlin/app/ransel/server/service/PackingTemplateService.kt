package app.ransel.server.service

import app.ransel.server.db.PackingItems
import app.ransel.server.db.PackingTemplates
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.toPackingItemDto
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.dto.ApplyTemplateRequest
import app.ransel.shared.dto.CreatePackingTemplateRequest
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** A template is visible when it is built-in, yours, or shared with a trip you belong to. */
object PackingTemplateService {
    fun list(userId: UUID): List<PackingTemplateDto> = transaction {
        PackingTemplates.selectAll()
            .where { visibleTo(userId) and PackingTemplates.deletedAt.isNull() }
            // built-in first (NULL owner), then yours and shared ones, each by name
            .orderBy(PackingTemplates.ownerId to SortOrder.ASC_NULLS_FIRST, PackingTemplates.name to SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(userId: UUID, id: UUID, body: CreatePackingTemplateRequest): PackingTemplateDto {
        val shared = body.sharedTripId?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
                ?: throw app.ransel.server.plugins.validationFailed(mapOf("sharedTripId" to "INVALID_UUID"))
        }
        requireValid(
            buildMap {
                if (body.name.isBlank()) put("name", "REQUIRED")
                if (body.items.isEmpty()) put("items", "MUST_NOT_BE_EMPTY")
                if (body.items.any { it.name.isBlank() }) put("items", "NAME_REQUIRED")
                if (body.items.any { it.quantity != null && it.quantity!! <= 0 }) put("items", "QUANTITY_MUST_BE_POSITIVE")
            },
        )
        // Sharing needs a trip you belong to (any role: a template is a list of names, not trip content).
        if (shared != null) requireRole(shared, userId, Role.VIEWER)
        return transaction {
            if (PackingTemplates.select(PackingTemplates.id).where { PackingTemplates.id eq id }.any()) {
                throw conflict("Template id already exists")
            }
            PackingTemplates.insert {
                it[PackingTemplates.id] = id
                it[ownerId] = userId
                it[sharedTripId] = shared
                it[name] = body.name.trim()
                it[items] = body.items.map { item -> item.copy(name = item.name.trim()) }
            }
            PackingTemplates.selectAll().where { PackingTemplates.id eq id }.single().toDto()
        }
    }

    /** Returns only the created items. `skipExisting` compares name (case-insensitive) + category. */
    fun apply(userId: UUID, tripId: UUID, body: ApplyTemplateRequest): List<PackingItemDto> {
        requireRole(tripId, userId, Role.EDITOR)
        val templateId = runCatching { UUID.fromString(body.templateId) }.getOrNull() ?: throw notFound("Template")
        return transaction {
            val template = PackingTemplates.selectAll()
                .where { (PackingTemplates.id eq templateId) and visibleTo(userId) and PackingTemplates.deletedAt.isNull() }
                .singleOrNull() ?: throw notFound("Template")
            val live = PackingItems.selectAll()
                .where { (PackingItems.tripId eq tripId) and PackingItems.deletedAt.isNull() }.toList()
            val taken = live.map { it[PackingItems.category] to it[PackingItems.name].lowercase() }.toMutableSet()
            val nextOrder = PackingCategory.entries.associateWith { c ->
                (live.filter { it[PackingItems.category] == c }.maxOfOrNull { it[PackingItems.sortOrder] } ?: 0.0)
            }.toMutableMap()

            val created = mutableListOf<UUID>()
            template[PackingTemplates.items].forEach { item ->
                val key = item.category to item.name.lowercase()
                if (body.skipExisting && key in taken) return@forEach
                taken += key
                val id = UUID.randomUUID()
                val order = nextOrder.getValue(item.category) + 1.0
                nextOrder[item.category] = order
                PackingItems.insert {
                    it[PackingItems.id] = id
                    it[PackingItems.tripId] = tripId
                    it[category] = item.category
                    it[name] = item.name
                    it[quantity] = item.quantity ?: 1
                    it[sortOrder] = order
                }
                created += id
            }
            if (created.isEmpty()) emptyList()
            else PackingItems.selectAll().where { PackingItems.id inList created }
                .orderBy(PackingItems.category to SortOrder.ASC, PackingItems.sortOrder to SortOrder.ASC)
                .map { it.toPackingItemDto() }
        }
    }

    private fun visibleTo(userId: UUID) =
        PackingTemplates.ownerId.isNull() or
            (PackingTemplates.ownerId eq userId) or
            (PackingTemplates.sharedTripId inSubQuery TripMembers.select(TripMembers.tripId).where { TripMembers.userId eq userId })

    private fun ResultRow.toDto() = PackingTemplateDto(
        id = this[PackingTemplates.id].toString(),
        ownerId = this[PackingTemplates.ownerId]?.toString(),
        sharedTripId = this[PackingTemplates.sharedTripId]?.toString(),
        name = this[PackingTemplates.name],
        items = this[PackingTemplates.items],
        createdAt = this[PackingTemplates.createdAt].toTimestamp(),
        updatedAt = this[PackingTemplates.updatedAt].toTimestamp(),
    )
}
