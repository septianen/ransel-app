package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.TripInvites
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.db.toTripDto
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.generateInviteCode
import app.ransel.shared.domain.memberInitials
import app.ransel.shared.domain.summarizeExpenses
import app.ransel.shared.dto.CreateInviteRequest
import app.ransel.shared.dto.InviteDto
import app.ransel.shared.dto.InvitePreviewDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaInstant

/** Invite codes: `BALI-7K2Q`. Only the OWNER creates, lists and revokes; any signed-in user can preview and accept. */
class InviteService(private val config: AppConfig) {
    fun create(userId: UUID, tripId: UUID, body: CreateInviteRequest): InviteDto {
        requireRole(tripId, userId, Role.OWNER)
        requireValid(
            buildMap {
                if (body.role == Role.OWNER) put("role", "MUST_BE_EDITOR_OR_VIEWER")
                if (body.maxUses != null && body.maxUses!! <= 0) put("maxUses", "MUST_BE_POSITIVE")
                if (body.expiresAt.toJavaInstant() <= Instant.now()) put("expiresAt", "MUST_BE_IN_THE_FUTURE")
            },
        )
        return transaction {
            val title = Trips.selectAll().where { Trips.id eq tripId }.single()[Trips.title]
            val code = generateSequence { generateInviteCode(title) }
                .take(20)
                .firstOrNull { candidate -> TripInvites.selectAll().where { TripInvites.code eq candidate }.empty() }
                ?: throw ApiException(HttpStatusCode.InternalServerError, ErrorCode.INTERNAL_ERROR, "Could not generate a code")
            TripInvites.insert {
                it[TripInvites.code] = code
                it[TripInvites.tripId] = tripId
                it[role] = body.role
                it[createdBy] = userId
                it[maxUses] = body.maxUses
                it[expiresAt] = body.expiresAt.toJavaInstant()
            }
            TripInvites.selectAll().where { TripInvites.code eq code }.single().toDto()
        }
    }

    fun list(userId: UUID, tripId: UUID): List<InviteDto> {
        requireRole(tripId, userId, Role.OWNER)
        return transaction {
            TripInvites.selectAll()
                .where { TripInvites.tripId eq tripId }
                .orderBy(TripInvites.createdAt to SortOrder.DESC)
                .map { it.toDto() }
        }
    }

    /** Revoked invites stay in the list (with `revokedAt`) so the owner sees what happened. */
    fun revoke(userId: UUID, tripId: UUID, code: String) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            val invite = TripInvites.selectAll()
                .where { (TripInvites.code eq normalize(code)) and (TripInvites.tripId eq tripId) }
                .singleOrNull() ?: throw notFound("Invite")
            if (invite[TripInvites.revokedAt] == null) {
                TripInvites.update({ TripInvites.code eq invite[TripInvites.code] }) { it[revokedAt] = Instant.now() }
            }
        }
    }

    /** Public (no sign-in): enough to decide to join, never an amount per person, a phone number or payment info. */
    fun preview(code: String): InvitePreviewDto = transaction {
        val invite = load(code, lock = false)
        checkUsable(invite)
        val trip = Trips.selectAll().where { Trips.id eq invite[TripInvites.tripId] }.single()
        val tripId = trip[Trips.id]
        val people = TripMembers.join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .orderBy(TripMembers.joinedAt to SortOrder.ASC, TripMembers.id to SortOrder.ASC)
            .map { (it[TripMembers.displayName] ?: it[Users.displayName]) to it }
        val ownerName = people.first { (_, row) -> row[TripMembers.role] == Role.OWNER }.first
        val activityCount = ItineraryItems.selectAll()
            .where { (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }.count().toInt()
        val expenses = ExpenseService.loadAll(tripId)
        val spent = if (expenses.isEmpty()) 0L else summarizeExpenses(
            baseCurrency = trip[Trips.baseCurrency], budgetMinor = null, expenses = expenses, timeZone = TripService.SERVER_TIME_ZONE,
        ).spentMinor
        InvitePreviewDto(
            code = invite[TripInvites.code],
            tripId = tripId.toString(),
            tripTitle = trip[Trips.title],
            destination = trip[Trips.destination],
            startDate = trip[Trips.startDate].let { kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth) },
            endDate = trip[Trips.endDate].let { kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth) },
            ownerName = ownerName,
            memberCount = people.size,
            role = invite[TripInvites.role],
            memberInitials = people.map { (name, _) -> memberInitials(name) },
            guestNames = people.filter { (_, row) -> row[TripMembers.userId] == null }.map { it.first },
            activityCount = activityCount,
            baseCurrency = if (expenses.isEmpty()) null else trip[Trips.baseCurrency].trim(),
            sharedSpendMinor = spent,
        )
    }

    /** Checks in order: revoked -> expired -> full -> already a member; then membership + used_count in one transaction. */
    fun accept(userId: UUID, code: String): TripDto = transaction {
        val invite = load(code, lock = true) // row lock: two people taking the last seat cannot both succeed
        checkUsable(invite)
        val tripId = invite[TripInvites.tripId]
        val existing = TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and (TripMembers.userId eq userId) }.singleOrNull()
        if (existing != null && existing[TripMembers.removedAt] == null) {
            throw ApiException(HttpStatusCode.Conflict, ErrorCode.ALREADY_MEMBER, "You are already a member of this trip")
        }
        if (existing == null) {
            TripMembers.insert {
                it[TripMembers.id] = callerMemberId(tripId, userId)
                it[TripMembers.tripId] = tripId
                it[TripMembers.userId] = userId
                it[role] = invite[TripInvites.role]
            }
        } else {
            // Someone who left or was removed comes back to the same member row, so their old expenses are theirs again.
            TripMembers.update({ TripMembers.id eq existing[TripMembers.id] }) {
                it[role] = invite[TripInvites.role]
                it[removedAt] = null
                it[joinedAt] = Instant.now()
            }
        }
        TripInvites.update({ TripInvites.code eq invite[TripInvites.code] }) {
            it[usedCount] = invite[TripInvites.usedCount] + 1
        }
        Notify.emit { memberJoined(tripId, userId) }
        Trips.selectAll().where { Trips.id eq tripId }.single().toTripDto(invite[TripInvites.role])
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun normalize(code: String) = code.trim().uppercase()

    private fun load(code: String, lock: Boolean): ResultRow {
        val query = TripInvites.selectAll().where { TripInvites.code eq normalize(code) }
        val row = (if (lock) query.forUpdate() else query).singleOrNull() ?: throw notFound("Invite")
        return row
    }

    private fun checkUsable(invite: ResultRow) {
        val tripAlive = Trips.selectAll()
            .where { (Trips.id eq invite[TripInvites.tripId]) and Trips.deletedAt.isNull() }.any()
        if (!tripAlive || invite[TripInvites.revokedAt] != null) {
            throw ApiException(HttpStatusCode.Gone, ErrorCode.INVITE_REVOKED, "This invite was revoked")
        }
        if (invite[TripInvites.expiresAt] <= Instant.now()) {
            throw ApiException(HttpStatusCode.Gone, ErrorCode.INVITE_EXPIRED, "This invite has expired")
        }
        val max = invite[TripInvites.maxUses]
        if (max != null && invite[TripInvites.usedCount] >= max) {
            throw ApiException(HttpStatusCode.Conflict, ErrorCode.INVITE_FULL, "This invite has no seats left")
        }
    }

    private fun ResultRow.toDto() = InviteDto(
        code = this[TripInvites.code],
        tripId = this[TripInvites.tripId].toString(),
        role = this[TripInvites.role],
        maxUses = this[TripInvites.maxUses],
        usedCount = this[TripInvites.usedCount],
        expiresAt = this[TripInvites.expiresAt].toTimestamp(),
        revokedAt = this[TripInvites.revokedAt]?.toTimestamp(),
        url = "${config.appBaseUrl}/j/${this[TripInvites.code]}",
        createdAt = this[TripInvites.createdAt].toTimestamp(),
    )
}
