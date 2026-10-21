package app.ransel.server.service

import app.ransel.server.db.TripMembers
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/**
 * Members of a trip. Removing a member (or leaving) never touches their expenses or splits: those rows
 * keep pointing at the user so balances stay correct.
 */
object MemberService {
    /** Everyone sees the members; only the OWNER sees their email addresses. */
    fun list(userId: UUID, tripId: UUID): List<TripMemberDto> {
        val myRole = requireRole(tripId, userId, Role.VIEWER)
        return transaction { members(tripId, includeEmail = myRole == Role.OWNER) }
    }

    fun updateRole(userId: UUID, tripId: UUID, targetId: UUID, newRole: Role): TripMemberDto {
        requireRole(tripId, userId, Role.OWNER)
        if (newRole == Role.OWNER) throw validationFailed(mapOf("role" to "USE_TRANSFER_OWNERSHIP"))
        return transaction {
            val current = roleOf(tripId, targetId) ?: throw notFound("Member")
            if (current == Role.OWNER) throw validationFailed(mapOf("userId" to "OWNER_ROLE_IS_FIXED"))
            TripMembers.update({ (TripMembers.tripId eq tripId) and (TripMembers.userId eq targetId) }) { it[role] = newRole }
            if (current != newRole) Notify.emit { roleChanged(tripId, targetId, newRole) }
            members(tripId, includeEmail = true).single { it.userId == targetId.toString() }
        }
    }

    fun remove(userId: UUID, tripId: UUID, targetId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            val current = roleOf(tripId, targetId) ?: throw notFound("Member")
            if (current == Role.OWNER) throw ownerCannotLeave()
            TripMembers.deleteWhere { (TripMembers.tripId eq tripId) and (TripMembers.userId eq targetId) }
            Notify.emit { memberRemoved(tripId, targetId) }
        }
    }

    fun leave(userId: UUID, tripId: UUID) {
        val role = requireRole(tripId, userId, Role.VIEWER)
        if (role == Role.OWNER) throw ownerCannotLeave()
        transaction {
            TripMembers.deleteWhere { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) }
        }
    }

    /** Old owner becomes EDITOR, the new owner OWNER, in one transaction (a trip never has zero or two owners). */
    fun transferOwnership(userId: UUID, tripId: UUID, newOwnerId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        if (newOwnerId == userId) throw validationFailed(mapOf("newOwnerUserId" to "ALREADY_OWNER"))
        transaction {
            roleOf(tripId, newOwnerId) ?: throw validationFailed(mapOf("newOwnerUserId" to "NOT_A_MEMBER"))
            // Demote first: the unique index allows one OWNER row per trip.
            TripMembers.update({ (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) }) { it[role] = Role.EDITOR }
            TripMembers.update({ (TripMembers.tripId eq tripId) and (TripMembers.userId eq newOwnerId) }) { it[role] = Role.OWNER }
        }
    }

    internal fun members(tripId: UUID, includeEmail: Boolean): List<TripMemberDto> =
        TripMembers.join(Users, JoinType.INNER, TripMembers.userId, Users.id)
            .selectAll()
            .where { TripMembers.tripId eq tripId }
            .orderBy(TripMembers.joinedAt to SortOrder.ASC)
            .map {
                TripMemberDto(
                    tripId = tripId.toString(),
                    userId = it[TripMembers.userId].toString(),
                    displayName = it[Users.displayName],
                    email = if (includeEmail) it[Users.email] else null,
                    role = it[TripMembers.role],
                    joinedAt = it[TripMembers.joinedAt].toTimestamp(),
                )
            }

    private fun roleOf(tripId: UUID, userId: UUID): Role? =
        TripMembers.selectAll().where { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) }
            .singleOrNull()?.get(TripMembers.role)

    private fun ownerCannotLeave() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.OWNER_CANNOT_LEAVE, "Transfer ownership before leaving the trip")
}
