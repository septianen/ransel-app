package app.ransel.shared.domain

/*
 * Split bill and balances (PRD "Business rules", TRD "Domain logic").
 */

/**
 * Split equally between [participantIds] (the ticked members). The rounding remainder
 * goes to the payer when the payer takes part, otherwise to the first participant,
 * so the shares always add up to [amountMinor].
 */
fun splitEqually(amountMinor: Long, participantIds: List<String>, payerId: String): Map<String, Long> {
    require(amountMinor > 0) { "amount must be > 0" }
    val ids = participantIds.distinct()
    require(ids.isNotEmpty()) { "at least one participant" }
    val base = amountMinor / ids.size
    val remainder = amountMinor - base * ids.size
    val receiver = if (payerId in ids) payerId else ids.first()
    return ids.associateWith { if (it == receiver) base + remainder else base }
}

/**
 * What each payer paid when [amountMinor] is turned into [amountBaseMinor] (the trip currency): proportional, the
 * rounding difference goes to the first payer so the parts always add up to [amountBaseMinor].
 */
fun convertPayers(payers: List<Pair<String, Long>>, amountMinor: Long, amountBaseMinor: Long): Map<String, Long> {
    if (payers.isEmpty() || amountMinor <= 0) return emptyMap()
    val converted = payers.map { (id, amount) -> id to (amount.toDouble() / amountMinor * amountBaseMinor).toLong() }
    val remainder = amountBaseMinor - converted.sumOf { it.second }
    return converted.mapIndexed { i, (id, amount) -> id to if (i == 0) amount + remainder else amount }.toMap()
}

/** "Atur nominal": positive = still short ("kurang"), negative = over ("lebih"), 0 = Save enabled. */
fun splitDifference(amountMinor: Long, shares: Collection<Long>): Long = amountMinor - shares.sum()

data class BalanceExpense(
    val paidByMemberId: String,
    /** Already converted to the trip base currency. */
    val amountBaseMinor: Long,
    /** memberId → share in base currency. Empty = carried fully by the payer. */
    val sharesBaseMinor: Map<String, Long>,
    /** memberId → what they paid, in base currency, when several people paid. Empty = [paidByMemberId] paid all. */
    val payersBaseMinor: Map<String, Long> = emptyMap(),
)

data class BalanceSettlement(val fromMemberId: String, val toMemberId: String, val amountMinor: Long)

data class MemberBalance(val memberId: String, val paidMinor: Long, val shareMinor: Long, val balanceMinor: Long)

data class Transfer(val fromMemberId: String, val toMemberId: String, val amountMinor: Long)

/** balance = Σ paid − Σ share + Σ settlements paid − Σ settlements received. Sums to zero. */
fun computeBalances(
    memberIds: List<String>,
    expenses: List<BalanceExpense>,
    settlements: List<BalanceSettlement>,
): List<MemberBalance> {
    val paid = HashMap<String, Long>()
    val share = HashMap<String, Long>()
    val settled = HashMap<String, Long>()
    for (e in expenses) {
        val payers = e.payersBaseMinor.ifEmpty { mapOf(e.paidByMemberId to e.amountBaseMinor) }
        for ((user, p) in payers) paid[user] = (paid[user] ?: 0) + p
        val shares = e.sharesBaseMinor.ifEmpty { mapOf(e.paidByMemberId to e.amountBaseMinor) }
        for ((user, s) in shares) share[user] = (share[user] ?: 0) + s
    }
    for (s in settlements) {
        settled[s.fromMemberId] = (settled[s.fromMemberId] ?: 0) + s.amountMinor
        settled[s.toMemberId] = (settled[s.toMemberId] ?: 0) - s.amountMinor
    }
    val ids = (memberIds + paid.keys + share.keys + settled.keys).distinct()
    return ids.map { id ->
        val p = paid[id] ?: 0
        val sh = share[id] ?: 0
        MemberBalance(id, p, sh, p - sh + (settled[id] ?: 0))
    }
}

/** "Supaya lunas": greedy — largest debtor pays largest creditor until everything is zero. */
fun fewestTransfers(balances: List<MemberBalance>): List<Transfer> {
    val creditors = balances.filter { it.balanceMinor > 0 }.map { it.memberId to it.balanceMinor }.toMutableList()
    val debtors = balances.filter { it.balanceMinor < 0 }.map { it.memberId to -it.balanceMinor }.toMutableList()
    val result = mutableListOf<Transfer>()
    while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
        creditors.sortByDescending { it.second }
        debtors.sortByDescending { it.second }
        val (c, cAmt) = creditors[0]
        val (d, dAmt) = debtors[0]
        val amount = minOf(cAmt, dAmt)
        result += Transfer(fromMemberId = d, toMemberId = c, amountMinor = amount)
        if (cAmt == amount) creditors.removeAt(0) else creditors[0] = c to cAmt - amount
        if (dAmt == amount) debtors.removeAt(0) else debtors[0] = d to dAmt - amount
    }
    return result
}
