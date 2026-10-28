package app.ransel.server.service

import app.ransel.server.db.PaymentMethods
import app.ransel.server.db.Settlements
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.toPaymentMethodDto
import app.ransel.server.db.toSettlementDto
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.MemberBalance
import app.ransel.shared.domain.fewestTransfers
import app.ransel.shared.domain.tripBalances
import app.ransel.shared.dto.BalancesDto
import app.ransel.shared.dto.ClaimSettlementRequest
import app.ransel.shared.dto.CreateSettlementRequest
import app.ransel.shared.dto.MemberBalanceDto
import app.ransel.shared.dto.PayInfoDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.model.Role
import app.ransel.shared.model.SettlementStatus
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
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
import kotlin.time.toJavaInstant

/**
 * Balances in the trip's base currency, always through the shared `tripBalances` / `fewestTransfers` so the numbers
 * equal what the app shows offline: estimates are left out, settlements count once CONFIRMED.
 */
object BalanceService {
    fun balances(userId: UUID, tripId: UUID): BalancesDto {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val base = baseCurrency(tripId)
            val computed = compute(tripId, base)
            val names = memberNames(computed.map { UUID.fromString(it.memberId) }).mapKeys { it.key.toString() }
            BalancesDto(
                baseCurrency = base,
                members = computed.map {
                    MemberBalanceDto(it.memberId, names[it.memberId].orEmpty(), it.paidMinor, it.shareMinor, it.balanceMinor)
                },
                transfers = fewestTransfers(computed).map { TransferDto(it.fromMemberId, it.toMemberId, it.amountMinor) },
            )
        }
    }

    fun listSettlements(userId: UUID, tripId: UUID): List<SettlementDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            Settlements.selectAll()
                .where { (Settlements.tripId eq tripId) and Settlements.deletedAt.isNull() }
                .orderBy(Settlements.settledAt to SortOrder.DESC, Settlements.createdAt to SortOrder.DESC)
                .map { it.toSettlementDto() }
        }
    }

    /** "Tandai lunas": CONFIRMED at once. Any member may record a payment ("Take part in cost splitting" is open to every role). */
    fun createSettlement(userId: UUID, tripId: UUID, id: UUID, body: CreateSettlementRequest): SettlementDto {
        requireRole(tripId, userId, Role.VIEWER)
        val (from, to) = parsePair(body.fromMemberId, body.toMemberId, body.amountMinor)
        return transaction {
            insert(tripId, id, from, to, body.amountMinor, SettlementStatus.CONFIRMED, body.settledAt?.toJavaInstant())
            Notify.emit { settlementMarked(tripId, id, userId) }
            load(id)
        }
    }

    /** "Saya sudah bayar": the caller's own member paid [ClaimSettlementRequest.toMemberId]; waits for the receiver. */
    fun claim(userId: UUID, tripId: UUID, body: ClaimSettlementRequest): SettlementDto {
        requireRole(tripId, userId, Role.VIEWER)
        val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: throw validation("id" to "INVALID_UUID")
        val (from, to) = parsePair(callerMemberId(tripId, userId).toString(), body.toMemberId, body.amountMinor)
        return transaction {
            insert(tripId, id, from, to, body.amountMinor, SettlementStatus.CLAIMED_PAID, null)
            Notify.emit { settlementMarked(tripId, id, userId) }
            load(id)
        }
    }

    /** The receiver confirms the money arrived; the OWNER may do it for a receiver without an account. */
    fun confirm(userId: UUID, tripId: UUID, settlementId: UUID): SettlementDto {
        val role = requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val row = liveRow(tripId, settlementId)
            if (!mayConfirm(tripId, userId, role, row[Settlements.toMemberId])) throw forbidden()
            if (row[Settlements.status] != SettlementStatus.CLAIMED_PAID) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.SETTLEMENT_NOT_CLAIMED, "Settlement is not waiting for confirmation")
            }
            Settlements.update({ Settlements.id eq settlementId }) {
                it[status] = SettlementStatus.CONFIRMED
                it[settledAt] = Instant.now()
            }
            Notify.emit { settlementMarked(tripId, settlementId, userId) }
            load(settlementId)
        }
    }

    /** The payer, the receiver or the OWNER removes a settlement (a claim whose money never arrived, or a mistake). */
    fun deleteSettlement(userId: UUID, tripId: UUID, settlementId: UUID) {
        val role = requireRole(tripId, userId, Role.VIEWER)
        transaction {
            val row = liveRow(tripId, settlementId)
            if (!mayRemove(tripId, userId, role, row[Settlements.fromMemberId], row[Settlements.toMemberId])) throw forbidden()
            Settlements.update({ Settlements.id eq settlementId }) { it[deletedAt] = Instant.now() }
        }
    }

    /** Who may remove (or, through sync, restore) a settlement: its payer, its receiver or the OWNER. Call inside a transaction. */
    internal fun mayRemove(tripId: UUID, userId: UUID, role: Role, from: UUID, to: UUID): Boolean {
        if (role == Role.OWNER) return true
        val me = callerMemberId(tripId, userId)
        return me == from || me == to
    }

    /**
     * "Bayar ke": the open transfer from the caller's member to [to] and the receiver's payment info. Only while the
     * caller owes [to] (per fewest transfers); anyone else gets 403, so payment info never leaks to other members.
     */
    fun payInfo(userId: UUID, tripId: UUID, from: UUID, to: UUID): PayInfoDto {
        requireRole(tripId, userId, Role.VIEWER)
        if (from != callerMemberId(tripId, userId)) throw forbidden()
        return transaction {
            val base = baseCurrency(tripId)
            val transfer = fewestTransfers(compute(tripId, base))
                .firstOrNull { it.fromMemberId == from.toString() && it.toMemberId == to.toString() } ?: throw forbidden()
            val receiver = userIdsOfMembers(listOf(to))[to]
            val methods = if (receiver == null) emptyList() else PaymentMethods.selectAll()
                .where { (PaymentMethods.userId eq receiver) and PaymentMethods.deletedAt.isNull() }
                .orderBy(PaymentMethods.isPrimary to SortOrder.DESC, PaymentMethods.createdAt to SortOrder.ASC)
                .map { it.toPaymentMethodDto() }
            PayInfoDto(to.toString(), memberNames(listOf(to))[to].orEmpty(), transfer.amountMinor, base, methods)
        }
    }

    /** The receiver themselves, or the OWNER when the receiver has no account. Used by the endpoint and by sync. */
    internal fun mayConfirm(tripId: UUID, userId: UUID, role: Role, receiver: UUID): Boolean =
        callerMemberId(tripId, userId) == receiver || (role == Role.OWNER && userIdsOfMembers(listOf(receiver))[receiver] == null)

    /** Both people must belong to the trip. Used by the REST endpoint and by sync. */
    internal fun memberProblems(tripId: UUID, from: UUID, to: UUID): Map<String, String> {
        val members = TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.id inList listOf(from, to)) }
            .map { it[TripMembers.id] }.toSet()
        return buildMap {
            if (from !in members) put("fromMemberId", "NOT_A_MEMBER")
            if (to !in members) put("toMemberId", "NOT_A_MEMBER")
        }
    }

    /** Balances of a trip in its base currency. Call inside a transaction. */
    internal fun compute(tripId: UUID, base: String): List<MemberBalance> {
        val memberIds = TripMembers.select(TripMembers.id).where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .map { it[TripMembers.id].toString() }
        val settlements = Settlements.selectAll()
            .where { (Settlements.tripId eq tripId) and Settlements.deletedAt.isNull() }
            .map { it.toSettlementDto() }
        return tripBalances(memberIds, ExpenseService.loadAll(tripId), settlements, base)
    }

    private fun parsePair(fromRaw: String, toRaw: String, amountMinor: Long): Pair<UUID, UUID> {
        val from = runCatching { UUID.fromString(fromRaw) }.getOrNull()
        val to = runCatching { UUID.fromString(toRaw) }.getOrNull()
        requireValid(
            buildMap {
                if (from == null) put("fromMemberId", "INVALID_UUID")
                if (to == null) put("toMemberId", "INVALID_UUID")
                if (from != null && from == to) put("toMemberId", "MUST_DIFFER_FROM_PAYER")
                if (amountMinor <= 0) put("amountMinor", "MUST_BE_POSITIVE")
            },
        )
        return from!! to to!!
    }

    private fun insert(tripId: UUID, id: UUID, from: UUID, to: UUID, amount: Long, status: SettlementStatus, settledAt: Instant?) {
        requireValid(memberProblems(tripId, from, to))
        if (Settlements.select(Settlements.id).where { Settlements.id eq id }.any()) throw conflict("Settlement id already exists")
        Settlements.insert {
            it[Settlements.id] = id
            it[Settlements.tripId] = tripId
            it[fromMemberId] = from
            it[toMemberId] = to
            it[amountMinor] = amount
            it[Settlements.status] = status
            if (status == SettlementStatus.CLAIMED_PAID) it[claimedAt] = Instant.now()
            settledAt?.let { at -> it[Settlements.settledAt] = at }
        }
    }

    private fun liveRow(tripId: UUID, settlementId: UUID) =
        Settlements.selectAll()
            .where { (Settlements.id eq settlementId) and (Settlements.tripId eq tripId) and Settlements.deletedAt.isNull() }
            .singleOrNull() ?: throw notFound("Settlement")

    private fun load(id: UUID): SettlementDto = Settlements.selectAll().where { Settlements.id eq id }.single().toSettlementDto()

    private fun baseCurrency(tripId: UUID): String =
        Trips.select(Trips.baseCurrency).where { Trips.id eq tripId }.singleOrNull()?.get(Trips.baseCurrency) ?: throw tripNotFound()

    private fun validation(vararg pairs: Pair<String, String>) =
        ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.VALIDATION_FAILED, "Validation failed", mapOf(*pairs))
}
