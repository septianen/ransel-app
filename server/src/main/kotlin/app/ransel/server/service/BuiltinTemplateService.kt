package app.ransel.server.service

import app.ransel.server.db.PackingTemplates
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.domain.validateBuiltinTemplate
import app.ransel.shared.dto.AdminPackingTemplateDto
import app.ransel.shared.dto.AdminPackingTemplatesDto
import app.ransel.shared.dto.SaveBuiltinTemplateRequest
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.model.AdminAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/**
 * Built-in packing templates (`owner_id` NULL) as Karung edits them (owner, 3 Nov 2026). Changes are live at once:
 * `GET /packing-templates` reads the same rows, and items already copied into a trip stay as they are. A template a
 * user made is never read here except to count it (admin-spec: Karung never shows trip content).
 */
object BuiltinTemplateService {
    fun list(): AdminPackingTemplatesDto = transaction {
        val builtins = PackingTemplates.selectAll().where { PackingTemplates.ownerId.isNull() }
            .orderBy(PackingTemplates.name to SortOrder.ASC)
            .map { it.toAdminDto() }
            .sortedBy { it.deletedAt != null }
        val userLive = PackingTemplates.ownerId.isNotNull() and PackingTemplates.deletedAt.isNull()
        AdminPackingTemplatesDto(
            templates = builtins,
            userTemplateCount = PackingTemplates.select(PackingTemplates.id).where { userLive }.count().toInt(),
            sharedTemplateCount = PackingTemplates.select(PackingTemplates.id)
                .where { userLive and PackingTemplates.sharedTripId.isNotNull() }.count().toInt(),
        )
    }

    fun create(admin: AdminPrincipal, body: SaveBuiltinTemplateRequest): AdminPackingTemplateDto {
        val id = body.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw validationFailed(mapOf("id" to "INVALID_UUID"))
        val (name, items) = cleaned(body)
        return transaction {
            if (PackingTemplates.select(PackingTemplates.id).where { PackingTemplates.id eq id }.any()) {
                throw conflict("Template id already exists")
            }
            PackingTemplates.insert {
                it[PackingTemplates.id] = id
                it[ownerId] = null
                it[PackingTemplates.name] = name
                it[PackingTemplates.items] = items
            }
            val created = builtin(id)
            writeAudit(admin, AdminAction.TEMPLATE_CREATE, TARGET, id.toString(), null, snapshot(created))
            created
        }
    }

    fun update(admin: AdminPrincipal, id: UUID, body: SaveBuiltinTemplateRequest): AdminPackingTemplateDto {
        val (name, items) = cleaned(body)
        return transaction {
            val before = builtin(id)
            PackingTemplates.update({ PackingTemplates.id eq id }) {
                it[PackingTemplates.name] = name
                it[PackingTemplates.items] = items
                it[updatedAt] = Instant.now()
            }
            val after = builtin(id)
            writeAudit(admin, AdminAction.TEMPLATE_UPDATE, TARGET, id.toString(), snapshot(before), snapshot(after))
            after
        }
    }

    /** Soft delete: the apps stop listing it, the seed never brings it back, [restore] does. */
    fun delete(admin: AdminPrincipal, id: UUID): AdminPackingTemplateDto = transaction {
        val before = builtin(id)
        if (before.deletedAt == null) {
            PackingTemplates.update({ PackingTemplates.id eq id }) { it[deletedAt] = Instant.now() }
            writeAudit(admin, AdminAction.TEMPLATE_DELETE, TARGET, id.toString(), snapshot(before), null)
        }
        builtin(id)
    }

    fun restore(admin: AdminPrincipal, id: UUID): AdminPackingTemplateDto = transaction {
        val before = builtin(id)
        if (before.deletedAt != null) {
            PackingTemplates.update({ PackingTemplates.id eq id }) { it[deletedAt] = null }
            writeAudit(admin, AdminAction.TEMPLATE_RESTORE, TARGET, id.toString(), null, snapshot(before))
        }
        builtin(id)
    }

    private fun cleaned(body: SaveBuiltinTemplateRequest): Pair<String, List<TemplateItem>> {
        val problems = validateBuiltinTemplate(body.name, body.items)
        if (problems.isNotEmpty()) throw validationFailed(problems)
        return body.name.trim() to body.items.map { it.copy(name = it.name.trim()) }
    }

    /** A user's template answers 404 like a missing one: Karung must not learn that it exists. */
    private fun builtin(id: UUID): AdminPackingTemplateDto =
        PackingTemplates.selectAll().where { (PackingTemplates.id eq id) and PackingTemplates.ownerId.isNull() }
            .singleOrNull()?.toAdminDto() ?: throw notFound("Template")

    private fun snapshot(t: AdminPackingTemplateDto): JsonElement = buildJsonObject {
        put("name", t.name)
        put("items", t.items.size)
        put("itemsJson", Json.encodeToString(t.items))
    }

    private fun ResultRow.toAdminDto() = AdminPackingTemplateDto(
        id = this[PackingTemplates.id].toString(),
        name = this[PackingTemplates.name],
        items = this[PackingTemplates.items],
        updatedAt = this[PackingTemplates.updatedAt].toTimestamp(),
        deletedAt = this[PackingTemplates.deletedAt]?.toTimestamp(),
    )

    private const val TARGET = "TEMPLATE"
}
