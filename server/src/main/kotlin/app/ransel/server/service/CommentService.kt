package app.ransel.server.service

import app.ransel.server.db.Comments
import app.ransel.server.db.Expenses
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.TripMembers
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.domain.validateCommentBody
import app.ransel.shared.dto.CommentCountDto
import app.ransel.shared.dto.CommentDto
import app.ransel.shared.dto.CreateCommentRequest
import app.ransel.shared.dto.UpdateCommentRequest
import app.ransel.shared.model.CommentTarget
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/**
 * Comments on itinerary items and expenses (no group chat). Any member with an account may read and write, whatever the
 * role (a VIEWER can still say "setuju"); only the author edits or deletes. An "@Name" mention notifies that member.
 */
object CommentService {
    fun list(userId: UUID, tripId: UUID, kind: CommentTarget, targetId: UUID): List<CommentDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            requireTarget(tripId, kind, targetId)
            val rows = Comments.selectAll()
                .where { (Comments.tripId eq tripId) and (Comments.targetKind eq kind) and (Comments.targetId eq targetId) and Comments.deletedAt.isNull() }
                .orderBy(Comments.createdAt to SortOrder.ASC, Comments.id to SortOrder.ASC)
                .toList()
            toDtos(rows)
        }
    }

    /** Only the targets that have comments, so the lists can show a count next to each row. */
    fun counts(userId: UUID, tripId: UUID, kind: CommentTarget): List<CommentCountDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val total = Comments.id.count()
            Comments.select(Comments.targetId, total)
                .where { (Comments.tripId eq tripId) and (Comments.targetKind eq kind) and Comments.deletedAt.isNull() }
                .groupBy(Comments.targetId)
                .map { CommentCountDto(it[Comments.targetId].toString(), it[total].toInt()) }
        }
    }

    fun create(userId: UUID, tripId: UUID, commentId: UUID, body: CreateCommentRequest): CommentDto {
        requireRole(tripId, userId, Role.VIEWER)
        requireValid(validateCommentBody(body.body))
        val targetId = runCatching { UUID.fromString(body.targetId) }.getOrNull()
            ?: throw app.ransel.server.plugins.validationFailed(mapOf("targetId" to "INVALID_UUID"))
        return transaction {
            requireTarget(tripId, body.targetKind, targetId)
            if (Comments.select(Comments.id).where { Comments.id eq commentId }.any()) throw conflict("Comment id already exists")
            val author = callerMemberId(tripId, userId)
            val mentions = validMentions(tripId, body.mentions, author)
            Comments.insert {
                it[id] = commentId
                it[Comments.tripId] = tripId
                it[targetKind] = body.targetKind
                it[Comments.targetId] = targetId
                it[authorMemberId] = author
                it[Comments.body] = body.body.trim()
                it[Comments.mentions] = mentions.map(UUID::toString)
            }
            notifyMentions(tripId, commentId, userId, mentions, body.targetKind, targetId, body.body)
            toDtos(listOf(Comments.selectAll().where { Comments.id eq commentId }.single())).single()
        }
    }

    fun update(userId: UUID, commentId: UUID, body: UpdateCommentRequest): CommentDto {
        val (tripId, row) = findOwn(userId, commentId)
        requireValid(validateCommentBody(body.body))
        return transaction {
            val mentions = validMentions(tripId, body.mentions, row[Comments.authorMemberId])
            Comments.update({ Comments.id eq commentId }) {
                it[Comments.body] = body.body.trim()
                it[Comments.mentions] = mentions.map(UUID::toString)
                it[editedAt] = Instant.now()
            }
            // One notification per person and comment (dedupe key), so only people mentioned for the first time hear about it.
            notifyMentions(tripId, commentId, userId, mentions, row[Comments.targetKind], row[Comments.targetId], body.body)
            toDtos(listOf(Comments.selectAll().where { Comments.id eq commentId }.single())).single()
        }
    }

    fun delete(userId: UUID, commentId: UUID) {
        findOwn(userId, commentId)
        transaction { Comments.update({ Comments.id eq commentId }) { it[deletedAt] = Instant.now() } }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** The comment must exist, belong to a trip the caller is in, and be the caller's own. */
    private fun findOwn(userId: UUID, commentId: UUID): Pair<UUID, ResultRow> {
        val row = transaction {
            Comments.selectAll().where { (Comments.id eq commentId) and Comments.deletedAt.isNull() }.singleOrNull()
        } ?: throw notFound("Comment")
        val tripId = row[Comments.tripId]
        requireRole(tripId, userId, Role.VIEWER)
        if (row[Comments.authorMemberId] != callerMemberId(tripId, userId)) throw forbidden()
        return tripId to row
    }

    private fun requireTarget(tripId: UUID, kind: CommentTarget, targetId: UUID) {
        val exists = when (kind) {
            CommentTarget.ITINERARY_ITEM -> ItineraryItems.select(ItineraryItems.id)
                .where { (ItineraryItems.id eq targetId) and (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }.any()
            CommentTarget.EXPENSE -> Expenses.select(Expenses.id)
                .where { (Expenses.id eq targetId) and (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() }.any()
        }
        if (!exists) throw notFound("Comment target")
    }

    /** Mentions the server keeps: valid ids of current members of this trip, not the author, each once. */
    private fun validMentions(tripId: UUID, raw: List<String>, author: UUID): List<UUID> {
        val ids = raw.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.distinct().filter { it != author }
        if (ids.isEmpty()) return emptyList()
        return TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.id inList ids) }
            .map { it[TripMembers.id] }
    }

    private fun notifyMentions(
        tripId: UUID, commentId: UUID, authorUser: UUID, mentioned: List<UUID>,
        kind: CommentTarget, targetId: UUID, text: String,
    ) {
        if (mentioned.isEmpty()) return
        val users = userIdsOfMembers(mentioned).values
        Notify.emit {
            commentMention(
                tripId, commentId, authorUser, users,
                mapOf("targetKind" to kind.name, "targetId" to targetId.toString(), "snippet" to text.trim().take(SNIPPET)),
            )
        }
    }

    private fun toDtos(rows: List<ResultRow>): List<CommentDto> {
        val names = memberNames(rows.map { it[Comments.authorMemberId] })
        return rows.map {
            CommentDto(
                id = it[Comments.id].toString(),
                tripId = it[Comments.tripId].toString(),
                targetKind = it[Comments.targetKind],
                targetId = it[Comments.targetId].toString(),
                authorMemberId = it[Comments.authorMemberId].toString(),
                authorName = names[it[Comments.authorMemberId]].orEmpty(),
                body = it[Comments.body],
                mentions = it[Comments.mentions],
                createdAt = it[Comments.createdAt].toTimestamp(),
                editedAt = it[Comments.editedAt]?.toTimestamp(),
            )
        }
    }

    private const val SNIPPET = 80
}
