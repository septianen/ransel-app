package app.ransel.shared.domain

/** How a charge is paid: the free daily allowance first, then the wallet. */
data class CreditSpend(val fromFree: Int, val fromWallet: Int) {
    val total: Int get() = fromFree + fromWallet
}

/**
 * Splits [cost] over the free daily allowance and the wallet. Null when the two together cannot cover it.
 * A cost of 0 is free and spends nothing. Server and app use the same rule (the app only to enable or disable buttons).
 */
fun planCreditSpend(cost: Int, dailyFreeLeft: Int, balance: Int): CreditSpend? {
    require(cost >= 0 && dailyFreeLeft >= 0 && balance >= 0) { "negative credits" }
    val fromFree = minOf(cost, dailyFreeLeft)
    val fromWallet = cost - fromFree
    return if (fromWallet <= balance) CreditSpend(fromFree, fromWallet) else null
}
