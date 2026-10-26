package app.ransel.server.service

import app.ransel.server.db.Kitties
import app.ransel.server.db.KittyDeposits
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.toKittyDepositDto
import app.ransel.server.db.toKittyDto
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.tripNotFound
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.domain.kittySummary
import app.ransel.shared.dto.KittyDepositDto
import app.ransel.shared.dto.KittyDepositInput
import app.ransel.shared.dto.KittyInput
import app.ransel.shared.dto.KittyOverviewDto
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaInstant

/**
 * The trip's kitty (PRD "Group kitty"). The OWNER sets the contribution and the treasurer; deposits are recorded by an
 * EDITOR+ or by the treasurer (who may be a VIEWER: they hold the money). Amounts are in the trip's base currency.
 */
object KittyService {
    fun overview(userId: UUID, tripId: UUID): KittyOverviewDto {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction { load(tripId) }
    }

    fun put(userId: UUID, tripId: UUID, body: KittyInput): KittyOverviewDto {
        requireRole(tripId, userId, Role.OWNER)
        val treasurer = runCatching { UUID.fromString(body.treasurerMemberId) }.getOrNull()
        requireValid(
            buildMap {
                if (body.amountPerMemberMinor <= 0) put("amountPerMemberMinor", "MUST_BE_POSITIVE")
                if (treasurer == null) put("treasurerMemberId", "INVALID_UUID")
            },
        )
        return transaction {
            if (!isActiveMember(tripId, treasurer!!)) requireValid(mapOf("treasurerMemberId" to "NOT_A_MEMBER"))
            val exists = Kitties.select(Kitties.tripId).where { Kitties.tripId eq tripId }.any()
            if (exists) {
                Kitties.update({ Kitties.tripId eq tripId }) {
                    it[amountPerMemberMinor] = body.amountPerMemberMinor
                    it[treasurerMemberId] = treasurer
                    it[deletedAt] = null
                }
            } else {
                Kitties.insert {
                    it[Kitties.tripId] = tripId
                    it[amountPerMemberMinor] = body.amountPerMemberMinor
                    it[treasurerMemberId] = treasurer
                }
            }
            load(tripId)
        }
    }

    /** Removes the kitty settings; deposits and kitty expenses stay, so balances do not change. */
    fun delete(userId: UUID, tripId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            Kitties.update({ (Kitties.tripId eq tripId) and Kitties.deletedAt.isNull() }) { it[deletedAt] = Instant.now() }
        }
    }

    fun addDeposit(userId: UUID, tripId: UUID, body: KittyDepositInput): KittyDepositDto {
        val id = runCatching { UUID.fromString(body.id) }.getOrNull()
        val member = runCatching { UUID.fromString(body.memberId) }.getOrNull()
        requireValid(
            buildMap {
                if (id == null) put("id", "INVALID_UUID")
                if (member == null) put("memberId", "INVALID_UUID")
                if (body.amountMinor <= 0) put("amountMinor", "MUST_BE_POSITIVE")
            },
        )
        return transaction {
            val treasurer = requireDepositRecorder(tripId, userId)
            if (!isActiveMember(tripId, member!!)) requireValid(mapOf("memberId" to "NOT_A_MEMBER"))
            if (KittyDeposits.select(KittyDeposits.id).where { KittyDeposits.id eq id!! }.any()) throw conflict("Deposit id already exists")
            KittyDeposits.insert {
                it[KittyDeposits.id] = id!!
                it[KittyDeposits.tripId] = tripId
                it[memberId] = member
                it[amountMinor] = body.amountMinor
                it[receivedByMemberId] = treasurer
                body.receivedAt?.let { at -> it[receivedAt] = at.toJavaInstant() }
            }
            KittyDeposits.selectAll().where { KittyDeposits.id eq id!! }.single().toKittyDepositDto()
        }
    }

    fun deleteDeposit(userId: UUID, tripId: UUID, depositId: UUID) {
        transaction {
            requireDepositRecorder(tripId, userId)
            val updated = KittyDeposits.update({
                (KittyDeposits.id eq depositId) and (KittyDeposits.tripId eq tripId) and KittyDeposits.deletedAt.isNull()
            }) { it[deletedAt] = Instant.now() }
            if (updated == 0) throw notFound("Deposit")
        }
    }

    /** The current treasurer, or null when the trip has no kitty. Call inside a transaction. */
    internal fun treasurerOf(tripId: UUID): UUID? =
        Kitties.select(Kitties.treasurerMemberId)
            .where { (Kitties.tripId eq tripId) and Kitties.deletedAt.isNull() }
            .singleOrNull()?.get(Kitties.treasurerMemberId)

    /**
     * EDITOR+ or the treasurer may record deposits; returns the treasurer who receives them. A trip without a kitty
     * answers 422 `NO_KITTY`. Call inside a transaction.
     */
    internal fun requireDepositRecorder(tripId: UUID, userId: UUID): UUID {
        val role = requireRole(tripId, userId, Role.VIEWER)
        val treasurer = treasurerOf(tripId) ?: throw validationFailed(mapOf("kitty" to "NO_KITTY"))
        if (!role.atLeast(Role.EDITOR) && callerMemberId(tripId, userId) != treasurer) throw forbidden()
        return treasurer
    }

    internal fun load(tripId: UUID): KittyOverviewDto {
        val trip = Trips.select(Trips.baseCurrency).where { Trips.id eq tripId }.singleOrNull() ?: throw tripNotFound()
        val kitty = Kitties.selectAll().where { (Kitties.tripId eq tripId) and Kitties.deletedAt.isNull() }.singleOrNull()?.toKittyDto()
        val deposits = KittyDeposits.selectAll()
            .where { (KittyDeposits.tripId eq tripId) and KittyDeposits.deletedAt.isNull() }
            .orderBy(KittyDeposits.receivedAt to SortOrder.DESC, KittyDeposits.createdAt to SortOrder.DESC)
            .map { it.toKittyDepositDto() }
        val memberIds = TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .orderBy(TripMembers.joinedAt)
            .map { it[TripMembers.id].toString() }
        val summary = kittySummary(kitty, deposits, ExpenseService.loadAll(tripId), memberIds, trip[Trips.baseCurrency])
        return KittyOverviewDto(kitty, deposits, summary)
    }

    internal fun loadDeposits(tripId: UUID): List<KittyDepositDto> =
        KittyDeposits.selectAll()
            .where { (KittyDeposits.tripId eq tripId) and KittyDeposits.deletedAt.isNull() }
            .map { it.toKittyDepositDto() }

    private fun isActiveMember(tripId: UUID, memberId: UUID): Boolean =
        TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and (TripMembers.id eq memberId) and TripMembers.active() }
            .any()
}
