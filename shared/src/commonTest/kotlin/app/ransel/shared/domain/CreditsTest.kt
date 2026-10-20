package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreditsTest {
    @Test fun freeAllowanceIsSpentBeforeTheWallet() {
        assertEquals(CreditSpend(fromFree = 3, fromWallet = 0), planCreditSpend(cost = 3, dailyFreeLeft = 3, balance = 30))
        assertEquals(CreditSpend(fromFree = 1, fromWallet = 2), planCreditSpend(cost = 3, dailyFreeLeft = 1, balance = 30))
        assertEquals(CreditSpend(fromFree = 0, fromWallet = 3), planCreditSpend(cost = 3, dailyFreeLeft = 0, balance = 3))
    }

    @Test fun notEnoughIsNull() {
        assertNull(planCreditSpend(cost = 3, dailyFreeLeft = 1, balance = 1))
        assertNull(planCreditSpend(cost = 1, dailyFreeLeft = 0, balance = 0))
    }

    @Test fun zeroCostIsFree() {
        assertEquals(CreditSpend(0, 0), planCreditSpend(cost = 0, dailyFreeLeft = 0, balance = 0))
    }
}
