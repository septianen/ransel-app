package app.ransel.server.service

import app.ransel.server.db.ExpenseItems
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
import app.ransel.shared.domain.itemsTotal
import app.ransel.shared.domain.splitByItems
import app.ransel.shared.domain.summarizeExpenses
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ExpenseSummaryDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.toJavaLocalDate
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

    fun create(userId: UUID, tripId: UUID, expenseId: UUID, raw: ExpenseInput): ExpenseDto {
        requireRole(tripId, userId, Role.EDITOR)
        return transaction {
            val payer = if (raw.paidFromKitty) kittyPayer(tripId, raw, before = null) else resolvePayer(userId, tripId, raw)
            val body = withItemSplits(raw, payer)
            validate(body, payer, tripId)
            if (Expenses.select(Expenses.id).where { Expenses.id eq expenseId }.any()) throw conflict("Expense id already exists")
            Expenses.insert {
                it[id] = expenseId
                it[Expenses.tripId] = tripId
                fill(it, body, payer)
            }
            replaceSplits(expenseId, body.splits)
            replacePayers(expenseId, body.payers)
            replaceItems(expenseId, body.items)
            if (!body.isEstimate) Notify.emit { expenseAdded(tripId, expenseId, userId) }
            load(tripId, expenseId)
        }
    }

    /** Replaces the expense, its payers and its splits as a whole. */
    fun replace(userId: UUID, tripId: UUID, expenseId: UUID, raw: ExpenseInput): ExpenseDto {
        requireRole(tripId, userId, Role.EDITOR)
        return transaction {
            val before = load(tripId, expenseId)
            val payer = if (raw.paidFromKitty) kittyPayer(tripId, raw, before) else resolvePayer(userId, tripId, raw)
            val body = withItemSplits(raw, payer)
            // Someone who has since left may still be on this expense; editing the note must not fail.
            val keep = (before.splits.map { it.memberId } + before.payers.map { it.memberId } + before.paidByMemberId).map(UUID::fromString).toSet()
            validate(body, payer, tripId, keep)
            Expenses.update({ Expenses.id eq expenseId }) { fill(it, body, payer) }
            replaceSplits(expenseId, body.splits)
            replacePayers(expenseId, body.payers)
            replaceItems(expenseId, body.items)
            // an estimate saved as a real payment is news for the group, like a new expense
            if (before.isEstimate && !body.isEstimate) Notify.emit { expenseAdded(tripId, expenseId, userId) }
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
    private fun resolvePayer(callerId: UUID, tripId: UUID, body: ExpenseInput): UUID {
        body.payers.firstOrNull()?.let { first ->
            return runCatching { UUID.fromString(first.memberId) }.getOrNull() ?: throw validation("payers" to "INVALID_UUID")
        }
        return body.paidByMemberId?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
                ?: throw validation("paidByMemberId" to "INVALID_UUID")
        } ?: callerMemberId(tripId, callerId)
    }

    /**
     * A kitty expense is paid by the treasurer: the current one for a new expense or one that was not from the kitty
     * before, the stored payer when it already was (a later change of treasurer does not move old expenses).
     */
    internal fun kittyPayer(tripId: UUID, body: ExpenseInput, before: ExpenseDto?): UUID {
        if (body.payers.isNotEmpty()) throw validation("payers" to "NOT_WITH_KITTY")
        if (before != null && before.paidFromKitty) return UUID.fromString(before.paidByMemberId)
        return KittyService.treasurerOf(tripId) ?: throw validation("paidFromKitty" to "NO_KITTY")
    }

    /** With receipt items the splits come from domain.splitByItems; the items must add up to the amount. */
    internal fun withItemSplits(body: ExpenseInput, payer: UUID): ExpenseInput {
        if (body.items.isEmpty()) return body
        requireValid(
            buildMap {
                if (body.items.size > MAX_ITEMS) put("items", "TOO_MANY")
                if (body.items.any { it.name.isBlank() || it.name.length > 120 }) put("items", "INVALID_NAME")
                if (body.items.any { it.amountMinor < 0 }) put("items", "MUST_NOT_BE_NEGATIVE")
                if (body.items.any { item -> item.memberIds.any { runCatching { UUID.fromString(it) }.isFailure } }) put("items", "INVALID_UUID")
                if (body.items.all { it.memberIds.isEmpty() }) put("items", "NO_MEMBERS")
            },
        )
        val total = itemsTotal(body.items)
        if (total != body.amountMinor) {
            throw ApiException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCode.SPLIT_TOTAL_MISMATCH,
                "Receipt items must add up to the amount",
                mapOf("items" to "SUM_${total}_AMOUNT_${body.amountMinor}"),
            )
        }
        val shares = splitByItems(body.items, payer.toString())
        return body.copy(splits = shares.filterValues { it > 0 }.map { ExpenseSplitDto(it.key, it.value) })
    }

    internal fun loadAll(tripId: UUID): List<ExpenseDto> {
        val rows = Expenses.selectAll()
            .where { (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() }
            .orderBy(Expenses.spentAt to SortOrder.DESC, Expenses.createdAt to SortOrder.DESC)
            .toList()
        if (rows.isEmpty()) return emptyList()
        val splits = ExpenseSplits.selectAll()
            .where { ExpenseSplits.expenseId inList rows.map { it[Expenses.id] } }
            .groupBy({ it[ExpenseSplits.expenseId] }, { ExpenseSplitDto(it[ExpenseSplits.memberId].toString(), it[ExpenseSplits.shareMinor]) })
        val payers = loadPayers(rows.map { it[Expenses.id] })
        val items = loadItems(rows.map { it[Expenses.id] })
        return rows.map {
            it.toExpenseDto(splits[it[Expenses.id]].orEmpty().sortedBy { s -> s.memberId }, payers[it[Expenses.id]].orEmpty(), items[it[Expenses.id]].orEmpty())
        }
    }

    /** Receipt lines of the given expenses, in list order. */
    internal fun loadItems(expenseIds: Collection<UUID>): Map<UUID, List<ExpenseItemDto>> =
        if (expenseIds.isEmpty()) emptyMap() else ExpenseItems.selectAll()
            .where { ExpenseItems.expenseId inList expenseIds }
            .orderBy(ExpenseItems.sortOrder)
            .groupBy({ it[ExpenseItems.expenseId] }, { ExpenseItemDto(it[ExpenseItems.name], it[ExpenseItems.amountMinor], it[ExpenseItems.memberIds]) })

    /** Payer rows of the given expenses; an expense with one payer has none. */
    internal fun loadPayers(expenseIds: Collection<UUID>): Map<UUID, List<ExpensePayerDto>> =
        if (expenseIds.isEmpty()) emptyMap() else ExpensePayers.selectAll()
            .where { ExpensePayers.expenseId inList expenseIds }
            .groupBy({ it[ExpensePayers.expenseId] }, { ExpensePayerDto(it[ExpensePayers.memberId].toString(), it[ExpensePayers.amountMinor]) })

    private fun load(tripId: UUID, expenseId: UUID): ExpenseDto {
        val row = Expenses.selectAll()
            .where { (Expenses.tripId eq tripId) and Expenses.deletedAt.isNull() and (Expenses.id eq expenseId) }
            .singleOrNull() ?: throw notFound("Expense")
        val splits = ExpenseSplits.selectAll().where { ExpenseSplits.expenseId eq expenseId }
            .map { ExpenseSplitDto(it[ExpenseSplits.memberId].toString(), it[ExpenseSplits.shareMinor]) }
            .sortedBy { it.memberId }
        return row.toExpenseDto(splits, loadPayers(listOf(expenseId))[expenseId].orEmpty(), loadItems(listOf(expenseId))[expenseId].orEmpty())
    }

    internal fun replaceItems(expenseId: UUID, items: List<ExpenseItemDto>) {
        ExpenseItems.deleteWhere { ExpenseItems.expenseId eq expenseId }
        if (items.isNotEmpty()) {
            ExpenseItems.batchInsert(items.withIndex().toList()) { (index, item) ->
                this[ExpenseItems.expenseId] = expenseId
                this[ExpenseItems.sortOrder] = index
                this[ExpenseItems.name] = item.name.trim()
                this[ExpenseItems.amountMinor] = item.amountMinor
                this[ExpenseItems.memberIds] = item.memberIds
            }
        }
    }

    internal fun replaceSplits(expenseId: UUID, splits: List<ExpenseSplitDto>) {
        ExpenseSplits.deleteWhere { ExpenseSplits.expenseId eq expenseId }
        if (splits.isNotEmpty()) {
            ExpenseSplits.batchInsert(splits) { split ->
                this[ExpenseSplits.expenseId] = expenseId
                this[ExpenseSplits.memberId] = UUID.fromString(split.memberId)
                this[ExpenseSplits.shareMinor] = split.shareMinor
            }
        }
    }

    internal fun replacePayers(expenseId: UUID, payers: List<ExpensePayerDto>) {
        ExpensePayers.deleteWhere { ExpensePayers.expenseId eq expenseId }
        if (payers.isNotEmpty()) {
            ExpensePayers.batchInsert(payers) { p ->
                this[ExpensePayers.expenseId] = expenseId
                this[ExpensePayers.memberId] = UUID.fromString(p.memberId)
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
        it[Expenses.paidByMemberId] = payer
        it[Expenses.paidFromKitty] = body.paidFromKitty
        it[Expenses.isEstimate] = body.isEstimate
        // a rate source only means something for a foreign amount
        it[Expenses.fxSource] = body.fxSource.takeIf { body.fxRateToBase != null }
        it[Expenses.fxDate] = body.fxDate.takeIf { body.fxRateToBase != null }?.toJavaLocalDate()
    }

    private const val MAX_ITEMS = 100

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
            if (body.splits.map { it.memberId }.toSet().size != body.splits.size) put("splits", "DUPLICATE_MEMBER")
            if (body.splits.any { runCatching { UUID.fromString(it.memberId) }.isFailure }) put("splits", "INVALID_UUID")
            if (body.payers.isNotEmpty()) {
                if (body.payers.size < 2) put("payers", "NEEDS_AT_LEAST_TWO")
                else if (body.payers.any { it.amountMinor <= 0 }) put("payers", "MUST_BE_POSITIVE")
                else if (body.payers.map { it.memberId }.toSet().size != body.payers.size) put("payers", "DUPLICATE_MEMBER")
                else if (body.payers.any { runCatching { UUID.fromString(it.memberId) }.isFailure }) put("payers", "INVALID_UUID")
            }
        }
        requireValid(problems)

        val payerIds = body.payers.map { UUID.fromString(it.memberId) }
        // paidByMemberId is the first payer: the REST endpoint derives it, sync sends it, both must agree
        // The REST endpoint takes the first payer as `payer` whatever paidByMemberId says, so compare the sent value too.
        val sentPaidBy = body.paidByMemberId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (payerIds.isNotEmpty() && (payerIds.first() != payer || (sentPaidBy != null && sentPaidBy != payerIds.first()))) {
            throw validation("paidByMemberId" to "MUST_BE_FIRST_PAYER")
        }
        val itemMembers = body.items.flatMap { item -> item.memberIds.map(UUID::fromString) }
        val participants = (body.splits.map { UUID.fromString(it.memberId) } + payerIds + itemMembers + payer).toSet()
        val allowed = TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.id inList participants) }
            .map { it[TripMembers.id] }.toSet() + alreadyOnExpense
        if (!allowed.containsAll(participants)) {
            val key = when {
                payer !in allowed -> "paidByMemberId"
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
