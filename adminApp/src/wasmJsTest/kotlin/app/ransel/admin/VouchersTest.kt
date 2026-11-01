package app.ransel.admin

import app.ransel.admin.feature.vouchers.VoucherForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VouchersTest {
    @Test
    fun theVoucherRoutesParse() {
        assertEquals(AdminRoute.Vouchers, AdminRoute.parse("#/vouchers"))
        assertEquals("#/vouchers", AdminRoute.Vouchers.hash)
    }

    @Test
    fun aFormNeedsEveryNumberInRange() {
        assertNull(VoucherForm().request)
        assertNull(VoucherForm(credits = "0").request)
        assertNull(VoucherForm(credits = "15", maxUses = "100001").request)
        assertNull(VoucherForm(credits = "15", validDays = "3651").request)
        val request = assertNotNull(VoucherForm(credits = "15", note = "  ").request)
        assertEquals(15, request.credits)
        assertEquals(100, request.maxUses)
        assertEquals(30, request.validDays)
        assertNull(request.note)
    }
}
