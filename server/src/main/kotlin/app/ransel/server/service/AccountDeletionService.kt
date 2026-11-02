package app.ransel.server.service

import app.ransel.server.db.AiSearchLog
import app.ransel.server.db.CreditDaily
import app.ransel.server.db.CreditLedger
import app.ransel.server.db.CreditVoucherRedemptions
import app.ransel.server.db.CreditWallets
import app.ransel.server.db.Devices
import app.ransel.server.db.EmailVerificationTokens
import app.ransel.server.db.ErrorCountUsers
import app.ransel.server.db.GuestSessions
import app.ransel.server.db.Notifications
import app.ransel.server.db.NotificationSettings
import app.ransel.server.db.PackingTemplates
import app.ransel.server.db.PasswordResetTokens
import app.ransel.server.db.PaymentMethods
import app.ransel.server.db.PersonalItems
import app.ransel.server.db.RecommendationFeedback
import app.ransel.server.db.RefreshTokens
import app.ransel.server.db.TripMembers
import app.ransel.server.db.TripTemplates
import app.ransel.server.db.Trips
import app.ransel.server.db.UserActiveDays
import app.ransel.server.db.Users
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.validationFailed
import app.ransel.server.security.PasswordHasher
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AccountDeletionPreviewDto
import app.ransel.shared.dto.DELETED_MEMBER_NAME
import app.ransel.shared.dto.DeleteAccountRequest
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Users whose account was deleted while this server runs. An access token cannot be revoked and lives 15 minutes, so
 * authentication refuses these at once (one server instance; after a restart the deleted user's rows still refuse them).
 */
object DeletedAccounts {
    private val ids = ConcurrentHashMap.newKeySet<UUID>()
    fun add(id: UUID) { ids += id }
    operator fun contains(id: UUID) = id in ids
}

/**
 * Deletes an account at once (owner decisions, 2 Nov 2026). The `users` row stays as an empty shell because trips and
 * logs point at it; what identifies the person goes. Member rows stay under [DELETED_MEMBER_NAME] without the account,
 * so expenses, splits and the balances of the others do not change. Avatar files leave with the hourly photo purge.
 */
class AccountDeletionService(private val hasher: PasswordHasher) {
    fun preview(userId: UUID): AccountDeletionPreviewDto = transaction {
        requireLive(userId)
        val (handed, deleted) = ownedTrips(userId).partition { nextOwner(it, userId) != null }
        AccountDeletionPreviewDto(
            credits = CreditWallets.select(CreditWallets.balance).where { CreditWallets.userId eq userId }
                .singleOrNull()?.get(CreditWallets.balance) ?: 0,
            tripsHandedOver = titles(handed),
            tripsDeleted = titles(deleted),
        )
    }

    fun delete(userId: UUID, body: DeleteAccountRequest) {
        val stored = transaction { requireLive(userId)[Users.passwordHash] }
        if (stored == null || !hasher.verify(body.password, stored)) throw validationFailed(mapOf("password" to "WRONG"))
        transaction {
            ownedTrips(userId).forEach { tripId ->
                val next = nextOwner(tripId, userId)
                if (next == null) {
                    TripService.delete(userId, tripId)
                } else {
                    // Demote first: the unique index allows one OWNER row per trip.
                    TripMembers.update({ (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) }) { it[role] = Role.EDITOR }
                    TripMembers.update({ TripMembers.id eq next[TripMembers.id] }) { it[role] = Role.OWNER }
                    Trips.update({ Trips.id eq tripId }) { it[ownerId] = next[TripMembers.userId]!! }
                }
            }
            anonymiseMembers(userId)
            eraseOwnRows(userId)
            Users.update({ Users.id eq userId }) {
                it[displayName] = DELETED_MEMBER_NAME
                it[email] = null
                it[passwordHash] = null
                it[avatarUrl] = null
                it[emailVerifiedAt] = null
                it[lastSeenAt] = null
                it[deletedAt] = Instant.now()
            }
        }
        DeletedAccounts.add(userId)
    }

    private fun anonymiseMembers(userId: UUID) {
        val now = Instant.now()
        val rows = TripMembers.selectAll().where { TripMembers.userId eq userId }.toList()
        rows.forEach { row ->
            val tripId = row[TripMembers.tripId]
            val memberId = row[TripMembers.id]
            if (row[TripMembers.removedAt] == null) PollService.handOver(tripId, memberId)
            // a name-only member this person claimed may carry their number
            TripMembers.update({ TripMembers.claimedByMemberId eq memberId }) { it[phone] = null }
            TripMembers.update({ TripMembers.id eq memberId }) {
                it[TripMembers.userId] = null
                it[role] = Role.VIEWER // the only role a member without an account may have
                it[displayName] = DELETED_MEMBER_NAME
                it[phone] = null
                if (row[TripMembers.removedAt] == null) it[removedAt] = now
            }
        }
        val memberIds = rows.map { it[TripMembers.id] }
        PersonalItems.deleteWhere { PersonalItems.ownerMemberId inList memberIds }
        GuestSessions.deleteWhere { GuestSessions.memberId inList memberIds }
    }

    private fun eraseOwnRows(userId: UUID) {
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
        PasswordResetTokens.deleteWhere { PasswordResetTokens.userId eq userId }
        EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId }
        Devices.deleteWhere { Devices.userId eq userId }
        Notifications.deleteWhere { Notifications.userId eq userId }
        NotificationSettings.deleteWhere { NotificationSettings.userId eq userId }
        PaymentMethods.deleteWhere { PaymentMethods.userId eq userId }
        CreditWallets.deleteWhere { CreditWallets.userId eq userId }
        CreditDaily.deleteWhere { CreditDaily.userId eq userId }
        CreditLedger.deleteWhere { CreditLedger.userId eq userId }
        CreditVoucherRedemptions.deleteWhere { CreditVoucherRedemptions.userId eq userId }
        TripTemplates.deleteWhere { TripTemplates.ownerId eq userId }
        PackingTemplates.deleteWhere { PackingTemplates.ownerId eq userId }
        RecommendationFeedback.deleteWhere { RecommendationFeedback.userId eq userId }
        AiSearchLog.deleteWhere { AiSearchLog.userId eq userId }
        UserActiveDays.deleteWhere { UserActiveDays.userId eq userId }
        ErrorCountUsers.deleteWhere { ErrorCountUsers.userId eq userId }
    }

    private fun ownedTrips(userId: UUID): List<UUID> =
        TripMembers.join(Trips, JoinType.INNER, TripMembers.tripId, Trips.id).select(TripMembers.tripId)
            .where { (TripMembers.userId eq userId) and (TripMembers.role eq Role.OWNER) and TripMembers.active() and Trips.deletedAt.isNull() }
            .map { it[TripMembers.tripId] }

    /** The member with an account who joined first. */
    private fun nextOwner(tripId: UUID, userId: UUID): ResultRow? =
        TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and TripMembers.userId.isNotNull() and (TripMembers.userId neq userId) }
            .orderBy(TripMembers.joinedAt to SortOrder.ASC, TripMembers.id to SortOrder.ASC)
            .firstOrNull()

    private fun titles(ids: List<UUID>): List<String> =
        if (ids.isEmpty()) emptyList()
        else Trips.select(Trips.title).where { Trips.id inList ids }.map { it[Trips.title] }.sorted()

    private fun requireLive(userId: UUID): ResultRow =
        Users.selectAll().where { (Users.id eq userId) and Users.deletedAt.isNull() }.singleOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "Account not found")
}
