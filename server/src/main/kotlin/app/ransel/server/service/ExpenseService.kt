package app.ransel.server.service

import app.ransel.server.db.ExpensePayers
import app.ransel.server.db.ExpenseSplits
import app.ransel.server.db.Expenses
import app.ransel.server.db.Trips
import app.ransel.server.db.TripMembers
import app.ransel.server.db.toExpenseDto
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.isValidCurrency
import app.ransel.shared.domain.summarizeExpenses
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ExpenseSummaryDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaInstant

object ExpenseService {
    fun list(userId: UUID, tripId: UUID): List<ExpenseDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction { loadAll(tripId) }
    }

    fun create(userId: UUID, tripId: UUID, expenseId: UUID, body: ExpenseInput): ExpenseDto {
        requireRole(tripId, userId, Role.EDITOR)
        val payer = resolvePayer(userId, body)
        return transaction {
            validate(body, payer, tripId)
            if (Expenses.select(Expenses.id).where { Expenses.id eq expenseId }.any()) throw conflict("Expense id already exists")
            Expenses.insert {
                it[id] = expenseId
                it[Expenses.tripId] = tripId
                fill(it, body, payer)
            }
            replaceSplits(expenseId, body.splits)
            replacePayers(expenseId, body.payers)
            load(tripId, expenseId)
        }
    }

    /** Replaces the expense, its payers and its splits as a whole. */
    fun replace(userId: UUID, tripId: UUID, expenseId: UUID, body: ExpenseInput): ExpenseDto {
        requireRole(tripId, userId, Role.EDITOR)
        val payer = resolvePayer(userId, body)
        return transaction {
            val before = load(tripId, expenseId)
            // Someone who has since left may still be on this expense; editing the note must not fail.
            val keep = (before.splits.map { it.userId } + before.payers.map { it.userId } + before.paidByUserId).map(UUID::fromString).toSet()
            validate(body, payer, tripId, keep)
            Expenses.update({ Expenses.id eq expenseId }) { fill(it, body, payer) }
            replaceSplits(expenseId, body.splits)
            replacePayers(expenseId, body.payers)
            load(tripId, expenseId)
        }
    }

    fun delete(userId: UUID, tripId: UUID, expenseId: UUID) {
        requireRole(tripId, userId, Role.EDITOR)
        transaction {
            load(tripId, expenseId)
            Expenses.update({ Expenses.id eq expenseId }) { it[deletedAt] = Instant.now() }
        }
    }

    /** Same numbers as the app: the shared `summarizeExpenses`, grouped by day in the server time zone. */
    fun summary(userId: UUID, tripId: UUID): ExpenseSummaryDto {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val trip = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull() ?: throw tripNotFound()
            summarizeExpenses(
                baseCurrency = trip[Trips.baseCurrency],
                budgetMinor = trip[Trips.budgetMinor],
                expenses = loadAll(tripId),
                timeZone = TripService.SERVER_TIME_ZONE,
            )
        }
    }

    /** With several payers the first one is "who paid" (older clients show only that name). */
    private fun resolvePayer(callerId: UUID, body: ExpenseInput): UUID {
        body.payers.firstOrNull()?.let { first ->
            return runCatching { UUID.fromString(first.userId) }.getOrNull() ?: throw validation("payers" to "INVALID_UUID")
        }
        return body.paidByUserId?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
                ?: throw validation("paidByUserId" to "INVALID_UUID")
        } ?: callerId
    }

    private fun loadAll(tripId: UUID): List<ExpenseDto> {
        val rows = Expenses.selectAll()
            .where { (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() }
            .orderBy(Expenses.spentAt to SortOrder.DESC, Expenses.createdAt to SortOrder.DESC)
            .toList()
        if (rows.isEmpty()) return emptyList()
        val splits = ExpenseSplits.selectAll()
            .where { ExpenseSplits.expenseId inList rows.map { it[Expenses.id] } }
            .groupBy({ it[ExpenseSplits.expenseId] }, { ExpenseSplitDto(it[ExpenseSplits.userId].toString(), it[ExpenseSplits.shareMinor]) })
        val payers = loadPayers(rows.map { it[Expenses.id] })
        return rows.map { it.toExpenseDto(splits[it[Expenses.id]].orEmpty().sortedBy { s -> s.userId }, payers[it[Expenses.id]].orEmpty()) }
    }

    /** Payer rows of the given expenses; an expense with one payer has none. */
    internal fun loadPayers(expenseIds: Collection<UUID>): Map<UUID, List<ExpensePayerDto>> =
        if (expenseIds.isEmpty()) emptyMap() else ExpensePayers.selectAll()
            .where { ExpensePayers.expenseId inList expenseIds }
            .groupBy({ it[ExpensePayers.expenseId] }, { ExpensePayerDto(it[ExpensePayers.userId].toString(), it[ExpensePayers.amountMinor]) })

    private fun load(tripId: UUID, expenseId: UUID): ExpenseDto {
        val row = Expenses.selectAll()
            .where { (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() and (Expenses.id eq expenseId) }
            .singleOrNull() ?: throw notFound("Expense")
        val splits = ExpenseSplits.selectAll().where { ExpenseSplits.expenseId eq expenseId }
            .map { ExpenseSplitDto(it[ExpenseSplits.userId].toString(), it[ExpenseSplits.shareMinor]) }
            .sortedBy { it.userId }
        return row.toExpenseDto(splits, loadPayers(listOf(expenseId))[expenseId].orEmpty())
    }

    internal fun replaceSplits(expenseId: UUID, splits: List<ExpenseSplitDto>) {
        ExpenseSplits.deleteWhere { ExpenseSplits.expenseId eq expenseId }
        if (splits.isNotEmpty()) {
            ExpenseSplits.batchInsert(splits) { split ->
                this[ExpenseSplits.expenseId] = expenseId
                this[ExpenseSplits.userId] = UUID.fromString(split.userId)
                this[ExpenseSplits.shareMinor] = split.shareMinor
            }
        }
    }

    internal fun replacePayers(expenseId: UUID, payers: List<ExpensePayerDto>) {
        ExpensePayers.deleteWhere { ExpensePayers.expenseId eq expenseId }
        if (payers.isNotEmpty()) {
            ExpensePayers.batchInsert(payers) { p ->
                this[ExpensePayers.expenseId] = expenseId
                this[ExpensePayers.userId] = UUID.fromString(p.userId)
                this[ExpensePayers.amountMinor] = p.amountMinor
            }
        }
    }

    internal fun fill(it: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>, body: ExpenseInput, payer: UUID) {
        it[Expenses.amountMinor] = body.amountMinor
        it[Expenses.currency] = body.currency
        it[Expenses.fxRateToBase] = body.fxRateToBase?.let { rate -> BigDecimal(rate) }
        it[Expenses.category] = body.category
        it[Expenses.paymentMethod] = body.paymentMethod
        it[Expenses.note] = body.note
        it[Expenses.spentAt] = body.spentAt.toJavaInstant()
        it[Expenses.paidByUserId] = payer
    }

    private fun validation(vararg pairs: Pair<String, String>) =
        ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.VALIDATION_FAILED, "Validation failed", mapOf(*pairs))

    /**
     * Shape and totals, then membership: the payer and everyone in `splits` must be members of the trip
     * (any role: a VIEWER can still owe or pay). [alreadyOnExpense] are people the stored expense already has.
     */
    internal fun validate(body: ExpenseInput, payer: UUID, tripId: UUID, alreadyOnExpense: Set<UUID> = emptySet()) {
        val problems = buildMap {
            if (body.amountMinor <= 0) put("amountMinor", "MUST_BE_POSITIVE")
            if (!isValidCurrency(body.currency)) put("currency", "INVALID_CURRENCY")
            body.fxRateToBase?.let { raw ->
                val rate = runCatching { BigDecimal(raw) }.getOrNull()
                if (rate == null || rate.signum() <= 0 || rate >= BigDecimal("1E10")) put("fxRateToBase", "INVALID_RATE")
            }
            if (body.splits.any { it.shareMinor < 0 }) put("splits", "MUST_NOT_BE_NEGATIVE")
            if (body.splits.map { it.userId }.toSet().size != body.splits.size) put("splits", "DUPLICATE_USER")
            if (body.splits.any { runCatching { UUID.fromString(it.userId) }.isFailure }) put("splits", "INVALID_UUID")
            if (body.payers.isNotEmpty()) {
                if (body.payers.size < 2) put("payers", "NEEDS_AT_LEAST_TWO")
                else if (body.payers.any { it.amountMinor <= 0 }) put("payers", "MUST_BE_POSITIVE")
                else if (body.payers.map { it.userId }.toSet().size != body.payers.size) put("payers", "DUPLICATE_USER")
                else if (body.payers.any { runCatching { UUID.fromString(it.userId) }.isFailure }) put("payers", "INVALID_UUID")
            }
        }
        requireValid(problems)

        val payerIds = body.payers.map { UUID.fromString(it.userId) }
        // paidByUserId is the first payer: the REST endpoint derives it, sync sends it, both must agree
        if (payerIds.isNotEmpty() && payerIds.first() != payer) throw validation("paidByUserId" to "MUST_BE_FIRST_PAYER")
        val participants = (body.splits.map { UUID.fromString(it.userId) } + payerIds + payer).toSet()
        val allowed = TripMembers.select(TripMembers.userId)
            .where { (TripMembers.tripId eq tripId) and (TripMembers.userId inList participants) }
            .map { it[TripMembers.userId] }.toSet() + alreadyOnExpense
        if (!allowed.containsAll(participants)) {
            val key = when {
                payer !in allowed -> "paidByUserId"
                payerIds.any { it !in allowed } -> "payers"
                else -> "splits"
            }
            throw validation(key to "NOT_A_MEMBER")
        }

        if (body.payers.isNotEmpty()) {
            val paid = body.payers.sumOf { it.amountMinor }
            if (paid != body.amountMinor) {
                throw ApiException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCode.PAYER_TOTAL_MISMATCH,
                    "Payer amounts must add up to the amount",
                    mapOf("payers" to "SUM_${paid}_AMOUNT_${body.amountMinor}"),
                )
            }
        }

        if (body.splits.isNotEmpty()) {
            val sum = body.splits.sumOf { it.shareMinor }
            if (sum != body.amountMinor) {
                throw ApiException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCode.SPLIT_TOTAL_MISMATCH,
                    "Split shares must add up to the amount",
                    mapOf("splits" to "SUM_${sum}_AMOUNT_${body.amountMinor}"),
                )
            }
        }
    }
}
