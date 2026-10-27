package app.ransel.server.service

import app.ransel.server.db.MemberListItems
import app.ransel.server.db.MemberListRecipients
import app.ransel.server.db.MemberLists
import app.ransel.server.db.PackingTemplates
import app.ransel.server.db.PersonalItems
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.toMemberListItemDto
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.diffMemberList
import app.ransel.shared.domain.validateMemberList
import app.ransel.shared.dto.MemberListDto
import app.ransel.shared.dto.MemberListItemDto
import app.ransel.shared.dto.MemberListProgressDto
import app.ransel.shared.dto.SaveMemberListRequest
import app.ransel.shared.dto.SendMemberListRequest
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.model.NotificationType
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/**
 * "Daftar bawaan anggota" (PRD "Packing list from the trip owner"): the OWNER writes one list per trip and sends it
 * (owner only since 27 Oct 2026); every recipient gets a copy of each line in their own list, theirs to tick, edit or
 * delete. Saving a sent list adds new lines everywhere and removes or changes only the copies a member has not edited
 * (`customized`); a copy the member deleted is never brought back. Check marks and own lines stay.
 * Members without an account get copies too and tick them on the guest page.
 */
object MemberListService {
    fun get(userId: UUID, tripId: UUID): MemberListDto {
        requireRole(tripId, userId, Role.OWNER)
        return transaction { load(tripId) ?: throw notFound("Member list") }
    }

    fun save(userId: UUID, tripId: UUID, body: SaveMemberListRequest): MemberListDto {
        requireRole(tripId, userId, Role.OWNER)
        requireValid(validateMemberList(body.name, body.items))
        val ids = body.items.map { runCatching { UUID.fromString(it.id) }.getOrNull() }
        requireValid(if (ids.any { it == null }) mapOf("items" to "INVALID_UUID") else emptyMap())
        val me = callerMemberId(tripId, userId)
        return transaction {
            val row = MemberLists.selectAll().where { MemberLists.tripId eq tripId }.forUpdate().singleOrNull()
            if ((row?.get(MemberLists.version)) != body.baseVersion) throw stale()
            val before = items(tripId)
            if (row == null) {
                MemberLists.insert {
                    it[MemberLists.tripId] = tripId
                    it[name] = body.name.trim()
                    it[version] = 1
                    it[sentToAll] = false
                    it[createdByMemberId] = me
                }
            } else {
                MemberLists.update({ MemberLists.tripId eq tripId }) {
                    it[name] = body.name.trim()
                    it[version] = row[version] + 1
                }
            }
            // a line id that belongs to another trip's list would move it: refuse
            val foreign = MemberListItems.select(MemberListItems.id)
                .where { (MemberListItems.id inList ids.filterNotNull()) and (MemberListItems.tripId neq tripId) }.any()
            requireValid(if (foreign) mapOf("items" to "DUPLICATE_ID") else emptyMap())
            MemberListItems.deleteWhere { MemberListItems.tripId eq tripId }
            body.items.forEach { item -> MemberListItems.insert {
                it[MemberListItems.id] = UUID.fromString(item.id)
                it[MemberListItems.tripId] = tripId
                it[MemberListItems.category] = item.category
                it[MemberListItems.name] = item.name.trim()
                it[MemberListItems.quantity] = item.quantity
                it[MemberListItems.required] = item.required
                it[MemberListItems.sortOrder] = item.sortOrder
            } }
            val sent = row?.get(MemberLists.sentAt) != null
            if (sent) {
                val diff = diffMemberList(before, body.items)
                if (!diff.isEmpty) {
                    applyDiff(tripId, diff.removedIds, diff.changed)
                    recipients(tripId).forEach { copyMissing(tripId, it) }
                    tell(tripId, userId, recipients(tripId), "UPDATED", "packing-list-updated:$tripId:${row[MemberLists.version] + 1}")
                }
            }
            load(tripId)!!
        }
    }

    fun send(userId: UUID, tripId: UUID, body: SendMemberListRequest): MemberListDto {
        requireRole(tripId, userId, Role.OWNER)
        val wanted = body.memberIds.map { runCatching { UUID.fromString(it) }.getOrNull() }
        requireValid(if (wanted.any { it == null }) mapOf("memberIds" to "INVALID_UUID") else emptyMap())
        return transaction {
            load(tripId) ?: throw notFound("Member list")
            val active = TripMembers.select(TripMembers.id).where { (TripMembers.tripId eq tripId) and TripMembers.active() }.map { it[TripMembers.id] }.toSet()
            requireValid(if (!active.containsAll(wanted.filterNotNull())) mapOf("memberIds" to "NOT_A_MEMBER") else emptyMap())
            val all = wanted.isEmpty()
            MemberLists.update({ MemberLists.tripId eq tripId }) {
                it[sentToAll] = all
                it[sentAt] = Instant.now()
            }
            MemberListRecipients.deleteWhere { MemberListRecipients.tripId eq tripId }
            if (!all) {
                wanted.filterNotNull().distinct().forEach { id -> MemberListRecipients.insert {
                    it[MemberListRecipients.tripId] = tripId
                    it[MemberListRecipients.memberId] = id
                } }
            }
            val to = recipients(tripId)
            to.forEach { copyMissing(tripId, it) }
            if (body.saveAsTemplate) {
                val list = load(tripId)!!
                PackingTemplates.insert {
                    it[id] = UUID.randomUUID()
                    it[ownerId] = userId
                    it[name] = (body.templateName?.trim()?.takeIf { n -> n.isNotEmpty() } ?: list.name).take(60)
                    // check marks are never part of a template
                    it[items] = list.items.map { item -> TemplateItem(item.category, item.name, item.quantity, item.required) }
                }
            }
            tell(tripId, userId, to, "SENT", "packing-list-sent:$tripId:${Instant.now().epochSecond / 60}")
            load(tripId)!!
        }
    }

    /** One row per recipient: how many required and all lines from the list they ticked. */
    fun progress(userId: UUID, tripId: UUID): List<MemberListProgressDto> {
        requireRole(tripId, userId, Role.OWNER)
        return transaction {
            load(tripId) ?: throw notFound("Member list")
            val copies = PersonalItems.selectAll()
                .where { (PersonalItems.tripId eq tripId) and PersonalItems.sourceItemId.isNotNull() and PersonalItems.deletedAt.isNull() }
                .groupBy { it[PersonalItems.ownerMemberId] }
            recipients(tripId).map { member ->
                val rows = copies[member].orEmpty()
                val required = rows.filter { it[PersonalItems.required] }
                MemberListProgressDto(
                    memberId = member.toString(),
                    requiredDone = required.count { it[PersonalItems.isPacked] },
                    requiredTotal = required.size,
                    done = rows.count { it[PersonalItems.isPacked] },
                    total = rows.size,
                )
            }
        }
    }

    /** "Ingatkan": a push to each chosen recipient, at most once per 12 hours each (the dedupe key holds the window). */
    fun remind(userId: UUID, tripId: UUID, memberIds: List<String>) {
        requireRole(tripId, userId, Role.OWNER)
        val ids = memberIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        transaction {
            load(tripId) ?: throw notFound("Member list")
            val window = Instant.now().epochSecond / (12 * 3600)
            val progress = progress(userId, tripId).associateBy { UUID.fromString(it.memberId) }
            recipients(tripId).filter { it in ids }.forEach { member ->
                val left = progress[member]?.let { it.requiredTotal - it.requiredDone } ?: 0
                tell(tripId, userId, listOf(member), "REMIND", "packing-list-remind:$tripId:$member:$window", mapOf("requiredLeft" to left.toString()))
            }
        }
    }

    /** A member who joins a trip whose list went to everyone gets it too. Call inside the join transaction. */
    internal fun onMemberJoined(tripId: UUID, memberId: UUID) {
        val row = MemberLists.selectAll().where { MemberLists.tripId eq tripId }.singleOrNull() ?: return
        if (row[MemberLists.sentAt] != null && row[MemberLists.sentToAll]) copyMissing(tripId, memberId)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun load(tripId: UUID): MemberListDto? {
        val row = MemberLists.selectAll().where { MemberLists.tripId eq tripId }.singleOrNull() ?: return null
        return MemberListDto(
            tripId = tripId.toString(),
            name = row[MemberLists.name],
            version = row[MemberLists.version],
            items = items(tripId),
            sentToAll = row[MemberLists.sentToAll],
            recipientMemberIds = MemberListRecipients.selectAll().where { MemberListRecipients.tripId eq tripId }.map { it[MemberListRecipients.memberId].toString() },
            sentAt = row[MemberLists.sentAt]?.toTimestamp(),
            createdByMemberId = row[MemberLists.createdByMemberId].toString(),
            updatedAt = row[MemberLists.updatedAt].toTimestamp(),
        )
    }

    private fun items(tripId: UUID): List<MemberListItemDto> =
        MemberListItems.selectAll().where { MemberListItems.tripId eq tripId }
            .orderBy(MemberListItems.sortOrder to SortOrder.ASC)
            .map { it.toMemberListItemDto() }

    /**
     * Members who get the list: everyone but its author when sent to all, else the chosen ones. Members without an
     * account get it too and tick it on the guest page (stage 8).
     */
    private fun recipients(tripId: UUID): List<UUID> {
        val row = MemberLists.selectAll().where { MemberLists.tripId eq tripId }.singleOrNull() ?: return emptyList()
        if (row[MemberLists.sentAt] == null) return emptyList()
        val chosen = if (row[MemberLists.sentToAll]) null
        else MemberListRecipients.selectAll().where { MemberListRecipients.tripId eq tripId }.map { it[MemberListRecipients.memberId] }.toSet()
        return TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .orderBy(TripMembers.joinedAt)
            .map { it[TripMembers.id] }
            .filter { if (chosen == null) it != row[MemberLists.createdByMemberId] else it in chosen }
    }

    /** A copy of every list line the member never had; one they deleted counts as had, so it does not come back. */
    private fun copyMissing(tripId: UUID, memberId: UUID) {
        val have = PersonalItems.select(PersonalItems.sourceItemId)
            .where { (PersonalItems.ownerMemberId eq memberId) and PersonalItems.sourceItemId.isNotNull() }
            .mapNotNull { it[PersonalItems.sourceItemId] }.toSet()
        val missing = MemberListItems.selectAll().where { MemberListItems.tripId eq tripId }.filter { it[MemberListItems.id] !in have }
        if (missing.isEmpty()) return
        missing.forEach { line -> PersonalItems.insert {
            it[PersonalItems.id] = UUID.randomUUID()
            it[PersonalItems.tripId] = tripId
            it[PersonalItems.ownerMemberId] = memberId
            it[PersonalItems.category] = line[MemberListItems.category]
            it[PersonalItems.name] = line[MemberListItems.name]
            it[PersonalItems.quantity] = line[MemberListItems.quantity]
            it[PersonalItems.isPacked] = false
            it[PersonalItems.sortOrder] = line[MemberListItems.sortOrder]
            it[PersonalItems.sourceItemId] = line[MemberListItems.id]
            it[PersonalItems.required] = line[MemberListItems.required]
            it[PersonalItems.customized] = false
        } }
    }

    /**
     * Removed lines leave every list (checked or not) and changed lines change in place keeping their check mark, but
     * only in the copies the member has not edited.
     */
    private fun applyDiff(tripId: UUID, removed: List<String>, changed: List<MemberListItemDto>) {
        val now = Instant.now()
        val gone = removed.map(UUID::fromString)
        if (gone.isNotEmpty()) {
            PersonalItems.update({
                (PersonalItems.tripId eq tripId) and (PersonalItems.sourceItemId inList gone) and PersonalItems.deletedAt.isNull() and
                    (PersonalItems.customized eq false)
            }) {
                it[deletedAt] = now
            }
        }
        changed.forEach { line ->
            PersonalItems.update({
                (PersonalItems.tripId eq tripId) and (PersonalItems.sourceItemId eq UUID.fromString(line.id)) and PersonalItems.deletedAt.isNull() and
                    (PersonalItems.customized eq false)
            }) {
                it[category] = line.category
                it[name] = line.name.trim()
                it[quantity] = line.quantity
                it[required] = line.required
                it[sortOrder] = line.sortOrder
            }
        }
    }

    private fun tell(tripId: UUID, actor: UUID, members: List<UUID>, stage: String, key: String, extra: Map<String, String> = emptyMap()) {
        val users = userIdsOfMembers(members).values.filter { it != actor }
        if (users.isEmpty()) return
        val title = Trips.select(Trips.title).where { Trips.id eq tripId }.singleOrNull()?.get(Trips.title).orEmpty()
        val actorName = app.ransel.server.db.Users.select(app.ransel.server.db.Users.displayName)
            .where { app.ransel.server.db.Users.id eq actor }.singleOrNull()?.get(app.ransel.server.db.Users.displayName).orEmpty()
        Notify.emit {
            notify(users, NotificationType.PACKING_LIST, tripId, key) {
                mapOf("tripTitle" to title, "actorName" to actorName, "stage" to stage) + extra
            }
        }
    }

    private fun stale() = ApiException(HttpStatusCode.Conflict, ErrorCode.LIST_STALE, "The list was saved by someone else; load it again")
}
