package app.ransel.server.service

import app.ransel.server.db.GuestSessions
import app.ransel.server.db.PersonalItems
import app.ransel.server.db.Polls
import app.ransel.server.db.TripInvites
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toPersonalItemDto
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.fewestTransfers
import app.ransel.shared.domain.guestNameProblem
import app.ransel.shared.domain.sameNameMember
import app.ransel.shared.dto.GuestIdentityDto
import app.ransel.shared.dto.GuestIdentityRequest
import app.ransel.shared.dto.GuestMemberDto
import app.ransel.shared.dto.GuestMoneyDto
import app.ransel.shared.dto.GuestPageDto
import app.ransel.shared.dto.GuestTransferDto
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.VotePollRequest
import app.ransel.shared.model.NotificationType
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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlinx.datetime.toKotlinLocalDate
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * The guest page (PRD "Sharing to WhatsApp"). A link code leads to a trip: a poll's share code (/p/) or a live invite
 * code (/j/, "Lihat tanpa akun"). A guest is a member without an account; the browser proves which one with a token,
 * of which only the hash is stored. One browser per member: a second browser gets GUEST_NAME_TAKEN until the OWNER
 * releases the name. Guests see names, dates and polls, and only their own amounts ("Bagianmu"); never other
 * members' balances, phones, emails or payment info.
 */
object GuestService {
    private val random = SecureRandom()

    /** Where a code leads. */
    private data class Target(val tripId: UUID, val pollId: UUID?, val code: String)

    fun page(code: String, token: String?): GuestPageDto = transaction {
        val target = resolve(code)
        build(target, session(target.tripId, token))
    }

    fun identify(code: String, token: String?, body: GuestIdentityRequest): GuestIdentityDto = transaction {
        val target = resolve(code)
        val tripId = target.tripId
        val members = activeMembers(tripId)
        val pickedId = body.memberId
        val written = body.name
        val memberId = when {
            pickedId != null -> {
                val id = runCatching { UUID.fromString(pickedId) }.getOrNull()
                val row = members.firstOrNull { it[TripMembers.id] == id } ?: throw notFound("Member")
                // members with an account sign in instead
                if (row[TripMembers.userId] != null) throw ApiException(HttpStatusCode.Conflict, ErrorCode.MEMBER_HAS_ACCOUNT, "This member has an account")
                row[TripMembers.id]
            }
            written != null -> {
                val name = written.trim().replace(Regex("\\s+"), " ")
                requireValid(guestNameProblem(name)?.let { mapOf("name" to it) }.orEmpty())
                val existing = sameNameMember(name, members.map { it[TripMembers.id].toString() to nameOf(it) })
                if (existing != null) {
                    throw ApiException(HttpStatusCode.Conflict, ErrorCode.GUEST_NAME_EXISTS, "A member with this name exists", mapOf("memberId" to existing))
                }
                val id = UUID.randomUUID()
                TripMembers.insert {
                    it[TripMembers.id] = id
                    it[TripMembers.tripId] = tripId
                    it[role] = Role.VIEWER
                    it[displayName] = name
                }
                MemberListService.onMemberJoined(tripId, id)
                guestJoined(tripId, name)
                id
            }
            else -> throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.VALIDATION_FAILED, "Pick a name", mapOf("name" to "REQUIRED"))
        }
        // this browser leaves the name it had (if any) and takes the new one, unless another browser holds it
        token?.let { t -> GuestSessions.deleteWhere { GuestSessions.tokenHash eq TokenService.sha256(t) } }
        if (GuestSessions.select(GuestSessions.memberId).where { GuestSessions.memberId eq memberId }.any()) {
            throw ApiException(HttpStatusCode.Conflict, ErrorCode.GUEST_NAME_TAKEN, "Another browser uses this name")
        }
        val fresh = newToken()
        GuestSessions.insert {
            it[tokenHash] = TokenService.sha256(fresh)
            it[GuestSessions.tripId] = tripId
            it[GuestSessions.memberId] = memberId
        }
        val row = activeMembers(tripId).first { it[TripMembers.id] == memberId }
        GuestIdentityDto(fresh, GuestMemberDto(memberId.toString(), nameOf(row), hasAccount = false))
    }

    fun vote(code: String, token: String?, pollId: UUID, body: VotePollRequest): PollDto = transaction {
        val target = resolve(code)
        val me = session(target.tripId, token) ?: throw nameRequired()
        val inTrip = Polls.select(Polls.id).where { (Polls.id eq pollId) and (Polls.tripId eq target.tripId) and Polls.deletedAt.isNull() }.any()
        if (!inTrip) throw notFound("Poll")
        PollService.voteAs(pollId, me, body)
    }

    /** A guest ticks a line of their own list. */
    fun tick(code: String, token: String?, itemId: UUID, isPacked: Boolean): PersonalItemDto = transaction {
        val target = resolve(code)
        val me = session(target.tripId, token) ?: throw nameRequired()
        val updated = PersonalItems.update({
            (PersonalItems.id eq itemId) and (PersonalItems.ownerMemberId eq me) and PersonalItems.deletedAt.isNull()
        }) { it[PersonalItems.isPacked] = isPacked }
        if (updated == 0) throw notFound("Packing item")
        PersonalItems.selectAll().where { PersonalItems.id eq itemId }.single().toPersonalItemDto()
    }

    /** The OWNER frees a name so another browser (the guest's new phone) can pick it. */
    fun release(userId: UUID, tripId: UUID, memberId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        transaction { GuestSessions.deleteWhere { (GuestSessions.tripId eq tripId) and (GuestSessions.memberId eq memberId) } }
    }

    /** Members of [tripId] whose name a browser holds (for the OWNER's member list). */
    internal fun browserMembers(tripId: UUID): Set<UUID> =
        GuestSessions.select(GuestSessions.memberId).where { GuestSessions.tripId eq tripId }.map { it[GuestSessions.memberId] }.toSet()

    // ── helpers ─────────────────────────────────────────────────────────────

    /** The trip a link code leads to (404 NOT_FOUND "Link" otherwise), for routes outside this service. */
    fun tripOf(code: String): UUID = transaction { resolve(code).tripId }

    /** A poll share code, else a live invite code (revoked or expired ones lead nowhere). */
    private fun resolve(raw: String): Target {
        val code = raw.trim().uppercase()
        Polls.select(Polls.id, Polls.tripId).where { (Polls.shareCode eq code) and Polls.deletedAt.isNull() }.singleOrNull()?.let {
            return Target(it[Polls.tripId], it[Polls.id], code).also(::requireLiveTrip)
        }
        val invite = TripInvites.selectAll().where { TripInvites.code eq code }.singleOrNull()
        if (invite == null || invite[TripInvites.revokedAt] != null || invite[TripInvites.expiresAt] <= Instant.now()) throw notFound("Link")
        return Target(invite[TripInvites.tripId], null, code).also(::requireLiveTrip)
    }

    private fun requireLiveTrip(target: Target) {
        if (!Trips.select(Trips.id).where { (Trips.id eq target.tripId) and Trips.deletedAt.isNull() }.any()) throw notFound("Link")
    }

    /** The member this browser is in [tripId], if the token is known and the member is still a guest of the trip. */
    private fun session(tripId: UUID, token: String?): UUID? {
        if (token.isNullOrBlank()) return null
        val hash = TokenService.sha256(token)
        val row = GuestSessions.selectAll().where { (GuestSessions.tokenHash eq hash) and (GuestSessions.tripId eq tripId) }.singleOrNull() ?: return null
        val member = row[GuestSessions.memberId]
        // claimed by an account or removed: the name is no longer a guest's
        val stillGuest = TripMembers.select(TripMembers.id)
            .where { (TripMembers.id eq member) and TripMembers.active() and TripMembers.userId.isNull() }.any()
        if (!stillGuest) {
            GuestSessions.deleteWhere { GuestSessions.tokenHash eq hash }
            return null
        }
        GuestSessions.update({ GuestSessions.tokenHash eq hash }) { it[lastSeenAt] = Instant.now() }
        return member
    }

    private fun build(target: Target, me: UUID?): GuestPageDto {
        val trip = Trips.selectAll().where { Trips.id eq target.tripId }.single()
        val members = activeMembers(target.tripId)
        val held = browserMembers(target.tripId)
        val dtos = members.map { row ->
            val id = row[TripMembers.id]
            GuestMemberDto(
                id.toString(), nameOf(row), hasAccount = row[TripMembers.userId] != null, taken = id in held && id != me,
                avatarUrl = if (row[TripMembers.userId] != null) row.getOrNull(Users.avatarUrl) else null,
            )
        }
        return GuestPageDto(
            code = target.code,
            tripId = target.tripId.toString(),
            tripTitle = trip[Trips.title],
            destination = trip[Trips.destination],
            startDate = trip[Trips.startDate]?.toKotlinLocalDate(),
            endDate = trip[Trips.endDate]?.toKotlinLocalDate(),
            coverUrl = trip[Trips.coverUrl],
            ownerName = members.firstOrNull { it[TripMembers.role] == Role.OWNER }?.let(::nameOf).orEmpty(),
            members = dtos,
            me = dtos.firstOrNull { it.memberId == me?.toString() },
            focusPollId = target.pollId?.toString(),
            polls = PollService.forGuest(target.tripId, me, target.pollId),
            packing = if (me == null) emptyList() else PersonalItems.selectAll()
                .where { (PersonalItems.ownerMemberId eq me) and PersonalItems.deletedAt.isNull() }
                .orderBy(PersonalItems.category to SortOrder.ASC, PersonalItems.sortOrder to SortOrder.ASC)
                .map { it.toPersonalItemDto() },
            myMoney = me?.let { myMoney(target.tripId, it, trip[Trips.baseCurrency]) },
            albums = AlbumService.forTrip(target.tripId),
        )
    }

    /**
     * [me]'s own balance and the "Supaya lunas" transfers involving [me], through the same `BalanceService.compute`
     * and `fewestTransfers` as `GET /balances`. Null when [me] has no part in any expense or settlement.
     */
    private fun myMoney(tripId: UUID, me: UUID, base: String): GuestMoneyDto? {
        val balances = BalanceService.compute(tripId, base)
        val mine = balances.firstOrNull { it.memberId == me.toString() } ?: return null
        if (mine.paidMinor == 0L && mine.shareMinor == 0L && mine.balanceMinor == 0L) return null
        val transfers = fewestTransfers(balances).filter { it.fromMemberId == mine.memberId || it.toMemberId == mine.memberId }
        // memberNames also knows members who left but still appear in old expenses
        val names = memberNames(transfers.flatMap { listOf(UUID.fromString(it.fromMemberId), UUID.fromString(it.toMemberId)) })
            .mapKeys { it.key.toString() }
        return GuestMoneyDto(
            baseCurrency = base,
            paidMinor = mine.paidMinor,
            shareMinor = mine.shareMinor,
            balanceMinor = mine.balanceMinor,
            transfers = transfers.map { GuestTransferDto(it.fromMemberId, names[it.fromMemberId].orEmpty(), it.toMemberId, names[it.toMemberId].orEmpty(), it.amountMinor) },
        )
    }

    private fun activeMembers(tripId: UUID): List<ResultRow> =
        TripMembers.join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .orderBy(TripMembers.joinedAt to SortOrder.ASC, TripMembers.id to SortOrder.ASC)
            .toList()

    private fun nameOf(row: ResultRow): String = row[TripMembers.displayName] ?: row.getOrNull(Users.displayName).orEmpty()

    /** "Rina bergabung lewat link": to the OWNER and editors. */
    private fun guestJoined(tripId: UUID, name: String) {
        val audience = TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.role inList listOf(Role.OWNER, Role.EDITOR)) and TripMembers.userId.isNotNull() }
            .mapNotNull { it[TripMembers.userId] }
        val title = Trips.select(Trips.title).where { Trips.id eq tripId }.single()[Trips.title]
        Notify.emit {
            notify(audience, NotificationType.MEMBER_JOINED, tripId, "guest-joined:$tripId:${UUID.randomUUID()}") {
                mapOf("tripTitle" to title, "actorName" to name, "via" to "LINK")
            }
        }
    }

    private fun newToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun nameRequired() = ApiException(HttpStatusCode.Unauthorized, ErrorCode.GUEST_NAME_REQUIRED, "Pick your name first")
}
