package app.ransel.shared.domain

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.PaymentMethodInput
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.model.PollPriceUnit
import app.ransel.shared.model.SettlementStatus

/* Group money (PRD "Getting paid", TRD "Group trip features"): balances, receipt splits, stay estimates. */

/**
 * One expense in the trip's base currency. Each share is converted on its own and the rounding difference goes to the
 * payer (or the largest share), so shares always add up to the converted amount and balances always add up to zero.
 */
fun ExpenseDto.toBalanceExpense(baseCurrency: String): BalanceExpense {
    val amount = convertMinor(amountMinor, currency, baseCurrency, fxRateToBase)
    // what each payer paid, in base currency (empty = the one payer paid all)
    val paid = if (payers.isEmpty()) emptyMap() else convertPayers(payers.map { it.memberId to it.amountMinor }, amountMinor, amount)
    if (splits.isEmpty()) return BalanceExpense(paidByMemberId, amount, emptyMap(), paid)
    val converted = splits.associate { it.memberId to convertMinor(it.shareMinor, currency, baseCurrency, fxRateToBase) }
    val target = if (paidByMemberId in converted) paidByMemberId else converted.maxBy { it.value }.key
    val fixed = converted + (target to converted.getValue(target) + (amount - converted.values.sum()))
    return BalanceExpense(paidByMemberId, amount, fixed, paid)
}

/**
 * Balances of a trip as the Saldo tab shows them. Deleted rows and estimates are ignored, and settlements count only
 * once CONFIRMED.
 */
fun tripBalances(
    memberIds: List<String>,
    expenses: List<ExpenseDto>,
    settlements: List<SettlementDto>,
    baseCurrency: String,
): List<MemberBalance> {
    val balanceExpenses = expenses
        .filter { it.deletedAt == null && !it.isEstimate }
        .map { it.toBalanceExpense(baseCurrency) }
    val paidBack = settlements
        .filter { it.deletedAt == null && it.status == SettlementStatus.CONFIRMED }
        .map { BalanceSettlement(it.fromMemberId, it.toMemberId, it.amountMinor) }
    return computeBalances(memberIds, balanceExpenses, paidBack)
}

/**
 * "Per item" split of a receipt. Each item is split equally between its members (the remainder to the payer when they
 * share it, else the first member). Items without members (tax, service) are spread over everyone in proportion to
 * their item totals; that rounding remainder goes to the payer when they have items, else to the largest share.
 * When every item with members is free there is no proportion, so the extras are split equally between those members.
 * The shares add up to the sum of [items]. Members of no item get nothing.
 */
fun splitByItems(items: List<ExpenseItemDto>, payerId: String): Map<String, Long> {
    require(items.isNotEmpty()) { "at least one item" }
    require(items.all { it.amountMinor >= 0 }) { "negative item" }
    val shared = items.filter { it.memberIds.isNotEmpty() }
    require(shared.isNotEmpty()) { "no item has members" }
    val base = LinkedHashMap<String, Long>()
    for (item in shared) {
        if (item.amountMinor == 0L) continue
        for ((id, share) in splitEqually(item.amountMinor, item.memberIds, payerId)) base[id] = (base[id] ?: 0) + share
    }
    val extra = items.filter { it.memberIds.isEmpty() }.sumOf { it.amountMinor }
    val itemsTotal = base.values.sum()
    if (extra == 0L) return base
    if (itemsTotal == 0L) return splitEqually(extra, shared.flatMap { it.memberIds }.distinct(), payerId)
    val result = base.mapValues { (_, v) -> v + extra * v / itemsTotal }.toMutableMap()
    val remainder = extra - (result.values.sum() - itemsTotal)
    val receiver = if (payerId in result) payerId else result.maxBy { it.value }.key
    result[receiver] = result.getValue(receiver) + remainder
    return result
}

/** Total of the receipt lines, for the "items must match the total" check (SPLIT_TOTAL_MISMATCH). */
fun itemsTotal(items: List<ExpenseItemDto>): Long = items.sumOf { it.amountMinor }

/**
 * The estimate a STAY poll winner turns into: NIGHT = price × nights, PERSON = price × people (for the whole stay),
 * TOTAL = price. Nights and people are at least 1.
 */
fun stayEstimateMinor(priceMinor: Long, unit: PollPriceUnit?, nights: Int, people: Int): Long = when (unit ?: PollPriceUnit.NIGHT) {
    PollPriceUnit.NIGHT -> priceMinor * nights.coerceAtLeast(1)
    PollPriceUnit.PERSON -> priceMinor * people.coerceAtLeast(1)
    PollPriceUnit.TOTAL -> priceMinor
}

const val MAX_PAYMENT_METHODS = 10
private val ACCOUNT_NUMBER = Regex("^[0-9][0-9 \\-]{2,38}[0-9]$")

/** Field name → reason code, for ApiError.details. Empty = valid. */
fun validatePaymentMethod(input: PaymentMethodInput): Map<String, String> = buildMap {
    val provider = input.provider.trim()
    if (provider.isEmpty()) put("provider", "REQUIRED") else if (provider.length > 40) put("provider", "TOO_LONG")
    val number = input.accountNumber.trim()
    if (number.isEmpty()) put("accountNumber", "REQUIRED")
    else if (!ACCOUNT_NUMBER.matches(number) || number.count { it.isDigit() } < 4) put("accountNumber", "INVALID")
    val holder = input.holderName.trim()
    if (holder.isEmpty()) put("holderName", "REQUIRED") else if (holder.length > 80) put("holderName", "TOO_LONG")
}
