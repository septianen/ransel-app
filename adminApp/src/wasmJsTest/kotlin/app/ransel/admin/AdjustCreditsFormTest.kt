package app.ransel.admin

import app.ransel.admin.feature.users.AdjustCreditsForm
import app.ransel.admin.feature.users.AdjustProblem
import app.ransel.shared.model.CreditDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdjustCreditsFormTest {
    private val form = AdjustCreditsForm(requestId = "req-1", balance = 4)

    @Test
    fun addShowsTheBalanceAfter() {
        val f = form.copy(amountText = "10", reason = "Kompensasi")
        assertEquals(14, f.balanceAfter)
        assertTrue(f.canSave)
    }

    @Test
    fun subtractAboveTheBalanceIsBlocked() {
        val f = form.copy(direction = CreditDirection.SUBTRACT, amountText = "5", reason = "Koreksi")
        assertTrue(f.exceedsBalance)
        assertNull(f.balanceAfter)
        assertEquals(AdjustProblem.EXCEEDS_BALANCE, f.problem)
        assertFalse(f.canSave)
    }

    @Test
    fun subtractingTheWholeBalanceIsAllowed() {
        val f = form.copy(direction = CreditDirection.SUBTRACT, amountText = "4", reason = "Koreksi")
        assertEquals(0, f.balanceAfter)
        assertTrue(f.canSave)
    }

    @Test
    fun amountMustBeOneToTenThousand() {
        assertEquals(AdjustProblem.AMOUNT_RANGE, form.copy(amountText = "", reason = "abc").problem)
        assertEquals(AdjustProblem.AMOUNT_RANGE, form.copy(amountText = "0", reason = "abc").problem)
        assertEquals(AdjustProblem.AMOUNT_RANGE, form.copy(amountText = "10001", reason = "abc").problem)
        assertNull(form.copy(amountText = "10000", reason = "abc").problem)
    }

    @Test
    fun reasonIsRequiredThreeToTwoHundredCharactersTrimmed() {
        assertEquals(AdjustProblem.REASON_LENGTH, form.copy(amountText = "1", reason = "  ab  ").problem)
        assertEquals(AdjustProblem.REASON_LENGTH, form.copy(amountText = "1", reason = "x".repeat(201)).problem)
        assertNull(form.copy(amountText = "1", reason = "x".repeat(200)).problem)
    }

    @Test
    fun requestCarriesTheDialogsRequestIdAndATrimmedReason() {
        val request = form.copy(direction = CreditDirection.SUBTRACT, amountText = "3", reason = "  koreksi  ").request()
        assertEquals("req-1", request.requestId)
        assertEquals(CreditDirection.SUBTRACT, request.direction)
        assertEquals(3, request.amount)
        assertEquals("koreksi", request.reason)
    }
}
