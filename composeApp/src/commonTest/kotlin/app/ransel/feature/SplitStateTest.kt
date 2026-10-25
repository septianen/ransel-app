package app.ransel.feature

import app.ransel.feature.expenses.SplitState
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitStateTest {
    private val joined = Instant.parse("2026-10-01T00:00:00Z")
    private val members = listOf(
        TripMemberDto("t", "me", "u-me", "Septian", null, Role.OWNER, joined),
        TripMemberDto("t", "d", "u-d", "Dinda", null, Role.EDITOR, joined),
        TripMemberDto("t", "r", "u-r", "Raka", null, Role.EDITOR, joined),
        TripMemberDto("t", "m", "u-m", "Maya", null, Role.VIEWER, joined),
    )

    private fun state() = SplitState(members, myMemberId = "me", existing = null, currency = "IDR")

    @Test
    fun newExpenseIsSplitEquallyBetweenEveryone() {
        val s = state()
        assertEquals("me", s.paidBy)
        assertEquals(4, s.participants.size)
        val shares = s.toSplits(600_000, "IDR").associate { it.memberId to it.shareMinor }
        assertEquals(mapOf("me" to 150_000L, "d" to 150_000L, "r" to 150_000L, "m" to 150_000L), shares)
    }

    @Test
    fun untickedMemberIsLeftOutAndTheRemainderGoesToThePayer() {
        val s = state()
        s.toggle("m")
        val shares = s.toSplits(100_000, "IDR").associate { it.memberId to it.shareMinor }
        assertEquals(100_000L, shares.values.sum())
        assertEquals(33_334L, shares["me"]) // payer takes the rounding remainder
        assertFalse("m" in shares)
    }

    @Test
    fun customAmountsMustMatchTheTotalBeforeSaving() {
        val s = state()
        s.toggle("m")
        s.useCustom(600_000, "IDR")
        s.shareTexts["me"] = "250000"
        s.shareTexts["d"] = "200000"
        s.shareTexts["r"] = "100000"
        assertEquals(50_000L, s.difference(600_000, "IDR")) // 550.000 / 600.000: still short
        assertFalse(s.isValid(600_000, "IDR"))
        s.shareTexts["r"] = "150000"
        assertEquals(0L, s.difference(600_000, "IDR"))
        assertTrue(s.isValid(600_000, "IDR"))
    }

    @Test
    fun onlyThePayerTakingPartIsPersonal() {
        val s = state()
        listOf("d", "r", "m").forEach(s::toggle)
        assertTrue(s.toSplits(85_000, "IDR").isEmpty())
    }
}
