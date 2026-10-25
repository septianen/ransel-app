package app.ransel.server.service

import app.ransel.server.db.ExpenseSplits
import app.ransel.server.db.Expenses
import app.ransel.server.db.Settlements
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toExpenseDto
import app.ransel.server.db.toSettlementDto
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.domain.BalanceExpense
import app.ransel.shared.domain.BalanceSettlement
import app.ransel.shared.domain.computeBalances
import app.ransel.shared.domain.convertPayers
import app.ransel.shared.domain.convertMinor
import app.ransel.shared.domain.fewestTransfers
import app.ransel.shared.dto.BalancesDto
import app.ransel.shared.dto.CreateSettlementRequest
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.MemberBalanceDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.toJavaInstant

/**
 * Balances in the trip's base currency, always through the shared `computeBalances` / `fewestTransfers`
 * so the numbers equal what the app shows offline.
 */
object BalanceService {
    fun balances(userId: UUID, tripId: UUID): BalancesDto {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val trip = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull() ?: throw tripNotFound()
            val base = trip[Trips.baseCurrency]
            val memberIds = TripMembers.select(TripMembers.id).where { (TripMembers.tripId eq tripId) and TripMembers.active() }
                .map { it[TripMembers.id].toString() }

            val expenseRows = Expenses.selectAll()
                .where { (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() }.toList()
            val splits = if (expenseRows.isEmpty()) emptyMap() else ExpenseSplits.selectAll()
                .where { ExpenseSplits.expenseId inList expenseRows.map { it[Expenses.id] } }
                .groupBy({ it[ExpenseSplits.expenseId] }, { ExpenseSplitDto(it[ExpenseSplits.memberId].toString(), it[ExpenseSplits.shareMinor]) })
            val payers = ExpenseService.loadPayers(expenseRows.map { it[Expenses.id] })
            val expenses = expenseRows.map { it.toExpenseDto(splits[it[Expenses.id]].orEmpty(), payers[it[Expenses.id]].orEmpty()) }.map { toBalanceExpense(it, base) }

            val settlements = Settlements.selectAll()
                .where { (Settlements.tripId eq tripId) and Settlements.deletedAt.isNull() }
                .map { BalanceSettlement(it[Settlements.fromMemberId].toString(), it[Settlements.toMemberId].toString(), it[Settlements.amountMinor]) }

            val computed = computeBalances(memberIds, expenses, settlements)
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

    /** Any member may record a payment ("Take part in cost splitting" is open to every role). */
    fun createSettlement(userId: UUID, tripId: UUID, id: UUID, body: CreateSettlementRequest): SettlementDto {
        requireRole(tripId, userId, Role.VIEWER)
        val from = runCatching { UUID.fromString(body.fromMemberId) }.getOrNull()
        val to = runCatching { UUID.fromString(body.toMemberId) }.getOrNull()
        requireValid(
            buildMap {
                if (from == null) put("fromMemberId", "INVALID_UUID")
                if (to == null) put("toMemberId", "INVALID_UUID")
                if (from != null && from == to) put("toMemberId", "MUST_DIFFER_FROM_PAYER")
                if (body.amountMinor <= 0) put("amountMinor", "MUST_BE_POSITIVE")
            },
        )
        return transaction {
            requireValid(memberProblems(tripId, from!!, to!!))
            if (Settlements.select(Settlements.id).where { Settlements.id eq id }.any()) throw conflict("Settlement id already exists")
            Settlements.insert {
                it[Settlements.id] = id
                it[Settlements.tripId] = tripId
                it[fromMemberId] = from
                it[toMemberId] = to
                it[amountMinor] = body.amountMinor
                body.settledAt?.let { at -> it[settledAt] = at.toJavaInstant() }
            }
            Notify.emit { settlementMarked(tripId, id, userId) }
            Settlements.selectAll().where { Settlements.id eq id }.single().toSettlementDto()
        }
    }

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

    /**
     * Converts one expense to the base currency. Shares are converted one by one and the rounding difference
     * goes to the payer (or the largest share), so shares always add up to the converted amount and balances
     * always add up to zero.
     */
    private fun toBalanceExpense(e: ExpenseDto, base: String): BalanceExpense {
        val amount = convertMinor(e.amountMinor, e.currency, base, e.fxRateToBase)
        // what each payer paid, in base currency (empty = the one payer paid all)
        val paid = if (e.payers.isEmpty()) emptyMap() else convertPayers(e.payers.map { it.memberId to it.amountMinor }, e.amountMinor, amount)
        if (e.splits.isEmpty()) return BalanceExpense(e.paidByMemberId, amount, emptyMap(), paid)
        val converted = e.splits.associate { it.memberId to convertMinor(it.shareMinor, e.currency, base, e.fxRateToBase) }
        val target = if (e.paidByMemberId in converted) e.paidByMemberId else converted.maxBy { it.value }.key
        val fixed = converted + (target to converted.getValue(target) + (amount - converted.values.sum()))
        return BalanceExpense(e.paidByMemberId, amount, fixed, paid)
    }
}
