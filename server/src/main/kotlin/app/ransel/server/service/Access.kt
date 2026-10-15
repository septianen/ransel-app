package app.ransel.server.service

import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * The one authorization check every trip route calls (TRD "Authorization per trip").
 * Not a member (or trip deleted) -> 404 TRIP_NOT_FOUND, member with a role below [min] -> 403 FORBIDDEN.
 * Returns the caller's role.
 */
fun requireRole(tripId: UUID, userId: UUID, min: Role): Role {
    val role = transaction {
        TripMembers.join(Trips, org.jetbrains.exposed.v1.core.JoinType.INNER, TripMembers.tripId, Trips.id)
            .select(TripMembers.role)
            .where { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) and Trips.deletedAt.isNull() }
            .singleOrNull()?.get(TripMembers.role)
    } ?: throw tripNotFound()
    if (!role.atLeast(min)) throw forbidden()
    return role
}

/** Membership role regardless of whether the trip is deleted (sync needs to see tombstones); null = not a member. */
fun memberRole(tripId: UUID, userId: UUID): Role? = transaction {
    TripMembers.select(TripMembers.role)
        .where { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) }
        .singleOrNull()?.get(TripMembers.role)
}
