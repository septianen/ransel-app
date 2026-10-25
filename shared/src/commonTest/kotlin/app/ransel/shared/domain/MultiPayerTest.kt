package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MultiPayerTest {
    @Test
    fun severalPayersAreCreditedWithWhatEachPaid() {
        // 300.000 paid by A (200.000) and B (100.000), split equally between A, B and C
        val balances = computeBalances(
            memberIds = listOf("a", "b", "c"),
            expenses = listOf(
                BalanceExpense(
                    paidByMemberId = "a",
                    amountBaseMinor = 300_000,
                    payersBaseMinor = mapOf("a" to 200_000L, "b" to 100_000L),
                    sharesBaseMinor = mapOf("a" to 100_000L, "b" to 100_000L, "c" to 100_000L),
                ),
            ),
            settlements = emptyList(),
        ).associateBy { it.memberId }

        assertEquals(100_000L, balances.getValue("a").balanceMinor)
        assertEquals(0L, balances.getValue("b").balanceMinor)
        assertEquals(-100_000L, balances.getValue("c").balanceMinor)
        assertEquals(0L, balances.values.sumOf { it.balanceMinor })
    }

    @Test
    fun noPayersMeansThePayerPaidAll() {
        val balances = computeBalances(
            memberIds = listOf("a", "b"),
            expenses = listOf(BalanceExpense("a", 100_000, sharesBaseMinor = mapOf("a" to 50_000L, "b" to 50_000L))),
            settlements = emptyList(),
        ).associateBy { it.memberId }
        assertEquals(50_000L, balances.getValue("a").balanceMinor)
        assertEquals(-50_000L, balances.getValue("b").balanceMinor)
    }

    @Test
    fun convertedPayersAlwaysAddUpToTheBaseAmount() {
        // 10 USD paid 3.33 + 3.33 + 3.34, converted at 16.250 IDR: parts are rounded, the sum must stay exact
        val parts = convertPayers(listOf("a" to 333L, "b" to 333L, "c" to 334L), amountMinor = 1000, amountBaseMinor = 162_500)
        assertEquals(162_500L, parts.values.sum())
        assertEquals(listOf("a", "b", "c"), parts.keys.toList())
    }

    @Test
    fun nothingToConvertGivesNothing() {
        assertEquals(emptyMap(), convertPayers(emptyList(), 1000, 5000))
    }
}
