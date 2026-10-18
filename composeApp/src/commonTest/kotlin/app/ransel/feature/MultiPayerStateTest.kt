package app.ransel.feature

import app.ransel.feature.expenses.SplitState
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MultiPayerStateTest {
    private val joined = Instant.parse("2026-10-01T00:00:00Z")
    private val members = listOf(
        TripMemberDto("t", "me", "Septian", null, Role.OWNER, joined),
        TripMemberDto("t", "d", "Dinda", null, Role.EDITOR, joined),
        TripMemberDto("t", "r", "Raka", null, Role.EDITOR, joined),
    )

    private fun state(existing: ExpenseDto? = null) = SplitState(members, myUserId = "me", existing = existing, currency = "IDR")

    private fun expense(payers: List<ExpensePayerDto>, splits: List<ExpenseSplitDto> = emptyList(), amount: Long = 300_000) = ExpenseDto(
        id = "e", tripId = "t", amountMinor = amount, currency = "IDR", spentAt = joined,
        paidByUserId = payers.first().userId, payers = payers, splits = splits, createdAt = joined, updatedAt = joined,
    )

    @Test
    fun oneTickedPayerSendsNoPayerRows() {
        val s = state()
        assertEquals(listOf("me"), s.payers.toList())
        assertEquals(emptyList(), s.toPayers(300_000, "IDR"))
        assertEquals(mapOf("me" to 300_000L), s.payerAmounts(300_000, "IDR"))
    }

    @Test
    fun atLeastOnePayerStaysTicked() {
        val s = state()
        s.togglePayer("me")
        assertEquals(listOf("me"), s.payers.toList(), "the only payer cannot be unticked")
        s.togglePayer("d")
        s.togglePayer("me")
        assertEquals(listOf("d"), s.payers.toList())
        assertEquals("d", s.paidBy)
    }

    @Test
    fun severalPayersPayEquallyAndTheRemainderGoesToTheFirst() {
        val s = state()
        s.togglePayer("d")
        s.togglePayer("r")
        val payers = s.toPayers(100_000, "IDR")
        assertEquals(listOf("me", "d", "r"), payers.map { it.userId })
        assertEquals(100_000L, payers.sumOf { it.amountMinor })
        assertEquals(33_334L, payers.first().amountMinor)
    }

    @Test
    fun customPayerAmountsMustAddUpBeforeSaving() {
        val s = state()
        s.togglePayer("d")
        s.usePayersCustom(300_000, "IDR")
        s.payerTexts["me"] = "200000"
        s.payerTexts["d"] = "50000"
        assertEquals(50_000L, s.payerDifference(300_000, "IDR"))
        assertFalse(s.isValid(300_000, "IDR"))
        s.payerTexts["d"] = "100000"
        assertEquals(0L, s.payerDifference(300_000, "IDR"))
        assertTrue(s.isValid(300_000, "IDR"))
        assertEquals(mapOf("me" to 200_000L, "d" to 100_000L), s.toPayers(300_000, "IDR").associate { it.userId to it.amountMinor })
    }

    @Test
    fun aPayerWithNothingTypedIsNotValid() {
        val s = state()
        s.togglePayer("d")
        s.usePayersCustom(300_000, "IDR")
        s.payerTexts["me"] = "300000"
        s.payerTexts["d"] = "0"
        assertFalse(s.isValid(300_000, "IDR"), "a ticked payer must have paid something")
    }

    @Test
    fun editingAnExpenseWithSeveralPayersShowsThemAndKeepsCustomAmounts() {
        val equal = state(expense(listOf(ExpensePayerDto("me", 150_000), ExpensePayerDto("d", 150_000))))
        assertEquals(listOf("me", "d"), equal.payers.toList())
        assertFalse(equal.payersCustom)

        val custom = state(expense(listOf(ExpensePayerDto("d", 200_000), ExpensePayerDto("r", 100_000))))
        assertEquals("d", custom.paidBy)
        assertTrue(custom.payersCustom)
        assertEquals("200000", custom.payerTexts["d"]?.replace(".", ""))
    }

    @Test
    fun theRoundingRemainderOfTheSplitGoesToTheFirstPayer() {
        val s = state()
        s.togglePayer("d")
        s.toggle("r") // r no longer takes part: shares go to me and d
        val shares = s.toSplits(100_001, "IDR").associate { it.userId to it.shareMinor }
        assertEquals(100_001L, shares.values.sum())
        assertEquals(50_001L, shares["me"])
    }
}
