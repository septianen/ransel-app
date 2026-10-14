package app.ransel.shared.domain

import kotlinx.datetime.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* Cases come from PRD "Business rules" and docs/specs/itinerary-spec.md "Simulations". */
class DomainTest {

    private fun t(s: String) = LocalTime(s.substringBefore(':').toInt(), s.substringAfter(':').toInt())
    private fun slot(id: String, a: String, b: String) = Slot(id, t(a), t(b))

    @Test
    fun reorder_noGaps_item1ToPosition3() {
        val before = listOf(
            slot("1", "10:00", "11:25"), slot("2", "11:25", "12:00"),
            slot("3", "12:00", "14:00"), slot("4", "14:00", "17:00"),
        )
        val after = reorder(before, from = 0, to = 2)
        assertEquals(
            listOf(slot("2", "10:00", "10:35"), slot("3", "10:35", "12:35"), slot("1", "12:35", "14:00"), slot("4", "14:00", "17:00")),
            after,
        )
        assertEquals(listOf("2", "3", "1"), changedSlots(before, after).map { it.second.id })
    }

    @Test
    fun reorder_withGap_item2ToPosition1() {
        val before = listOf(slot("1", "10:00", "11:00"), slot("2", "13:30", "14:00"))
        assertEquals(listOf(slot("2", "10:00", "10:30"), slot("1", "10:30", "11:30")), reorder(before, 1, 0))
    }

    @Test
    fun reorder_untimedItemsOnlyMove() {
        val before = listOf(Slot("a", null, null), slot("b", "09:00", "10:00"))
        assertEquals(listOf(slot("b", "09:00", "10:00"), Slot("a", null, null)), reorder(before, 0, 1))
    }

    @Test
    fun schedule_emptyDayStartsAt0900_elseAfterLastEnd() {
        assertEquals(ScheduledTime(t("09:00"), t("10:30")), scheduleAtEndOfDay(emptyList(), 90))
        val day = listOf(slot("x", "09:00", "11:00"), slot("y", "12:30", "13:30"))
        assertEquals(ScheduledTime(t("13:30"), t("14:30")), scheduleAtEndOfDay(day, null))
    }

    @Test
    fun splitEqually_remainderGoesToPayer() {
        val s = splitEqually(100_000, listOf("a", "b", "c"), payerId = "b")
        assertEquals(mapOf("a" to 33_333L, "b" to 33_334L, "c" to 33_333L), s)
        assertEquals(0, splitDifference(100_000, s.values))
    }

    @Test
    fun balances_sumToZero_andTransfersSettleEverything() {
        val expenses = listOf(
            BalanceExpense("a", 300_000, splitEqually(300_000, listOf("a", "b", "c"), "a")),
            BalanceExpense("b", 90_000, mapOf("b" to 45_000, "c" to 45_000)),
        )
        val balances = computeBalances(listOf("a", "b", "c"), expenses, settlements = emptyList())
        assertEquals(0, balances.sumOf { it.balanceMinor })
        assertEquals(200_000, balances.first { it.userId == "a" }.balanceMinor)

        val transfers = fewestTransfers(balances)
        val after = computeBalances(listOf("a", "b", "c"), expenses, transfers.map { BalanceSettlement(it.fromUserId, it.toUserId, it.amountMinor) })
        assertTrue(after.all { it.balanceMinor == 0L })
        assertTrue(transfers.size <= 2)
    }

    @Test
    fun convertMinor_respectsCurrencyDecimals() {
        // USD 12.50 at 16,000 IDR/USD = Rp200,000
        assertEquals(200_000, convertMinor(1250, "USD", "IDR", "16000"))
        assertEquals(50_000, convertMinor(50_000, "IDR", "IDR", null))
    }

    @Test
    fun password_and_inviteCode() {
        assertTrue(checkPassword("rahasia123").isValid)
        assertTrue(!checkPassword("pendek1").isValid)
        val code = generateInviteCode("Bali 4 hari", Random(42))
        assertTrue(code.matches(Regex("^BALI-[2-9A-HJKMNP-Z]{4}$")), code)
        assertEquals("https://ransel.app/j/$code", inviteUrl(code))
    }
}
