package app.ransel.shared.dto

import app.ransel.shared.model.CommentTarget
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Comments on itinerary items and expenses (no group chat: groups already chat on WhatsApp). Online only: they are
 * not part of /sync. Any member with an account may read and write, whatever the role; only the author edits or deletes.
 */

@Serializable
data class CommentDto(
    val id: String,
    val tripId: String,
    val targetKind: CommentTarget,
    val targetId: String,
    /** trip_members.id of the author. */
    val authorMemberId: String,
    val authorName: String,
    /** At most domain.MAX_COMMENT_LENGTH characters. */
    val body: String,
    /** Member ids named with "@Name" in [body]; each one got a COMMENT_MENTION notification. */
    val mentions: List<String> = emptyList(),
    val createdAt: Timestamp,
    val editedAt: Timestamp? = null,
)

/** POST /trips/{id}/comments → 201 CommentDto. 404 NOT_FOUND when the target is not in this trip. */
@Serializable
data class CreateCommentRequest(
    val id: String,
    val targetKind: CommentTarget,
    val targetId: String,
    val body: String,
    /** Ids of the mentioned members (domain.extractMentions); the server keeps only members of this trip. */
    val mentions: List<String> = emptyList(),
)

/** PATCH /comments/{id} (author only). Mentions that are new notify their members; old ones do not notify again. */
@Serializable
data class UpdateCommentRequest(
    val body: String,
    val mentions: List<String> = emptyList(),
)

/** GET /trips/{id}/comments/counts?targetKind= : only targets that have at least one comment. */
@Serializable
data class CommentCountDto(
    val targetId: String,
    val count: Int,
)
