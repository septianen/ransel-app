package app.ransel.server.service

import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * The one authorization check every trip route calls (TRD "Authorization per trip").
 * Not a member (or trip deleted, or removed from it) -> 404 TRIP_NOT_FOUND, member with a role below [min] -> 403 FORBIDDEN.
 * Returns the caller's role.
 */
fun requireRole(tripId: UUID, userId: UUID, min: Role): Role {
    val role = transaction {
        TripMembers.join(Trips, JoinType.INNER, TripMembers.tripId, Trips.id)
            .select(TripMembers.role)
            .where { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) and TripMembers.active() and Trips.deletedAt.isNull() }
            .singleOrNull()?.get(TripMembers.role)
    } ?: throw tripNotFound()
    if (!role.atLeast(min)) throw forbidden()
    return role
}

/** Membership role regardless of whether the trip is deleted (sync needs to see tombstones); null = not a member (any more). */
fun memberRole(tripId: UUID, userId: UUID): Role? = transaction {
    TripMembers.select(TripMembers.role)
        .where { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) and TripMembers.active() }
        .singleOrNull()?.get(TripMembers.role)
}

/** The caller's own member id in [tripId]: the same value the apps compute offline (`memberIdFor`). */
fun callerMemberId(tripId: UUID, userId: UUID): UUID = UUID.fromString(memberIdFor(tripId.toString(), userId.toString()))

/** Member id -> account id, for members that have an account. Members added by name are left out. */
fun userIdsOfMembers(memberIds: Collection<UUID>): Map<UUID, UUID> =
    if (memberIds.isEmpty()) emptyMap() else transaction {
        TripMembers.select(TripMembers.id, TripMembers.userId)
            .where { TripMembers.id inList memberIds.distinct() }
            .mapNotNull { r -> r[TripMembers.userId]?.let { r[TripMembers.id] to it } }
            .toMap()
    }

/** Member id -> name (the name given when added, or the account's), removed members included so old expenses keep a name. */
fun memberNames(memberIds: Collection<UUID>): Map<UUID, String> =
    if (memberIds.isEmpty()) emptyMap() else transaction {
        TripMembers.join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .select(TripMembers.id, TripMembers.displayName, Users.displayName)
            .where { TripMembers.id inList memberIds.distinct() }
            .associate { it[TripMembers.id] to (it[TripMembers.displayName] ?: it[Users.displayName]) }
    }

/** Accounts that are current members of the trip. */
fun activeMemberUserIds(tripId: UUID): List<UUID> = transaction {
    TripMembers.select(TripMembers.userId)
        .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
        .mapNotNull { it[TripMembers.userId] }
}
