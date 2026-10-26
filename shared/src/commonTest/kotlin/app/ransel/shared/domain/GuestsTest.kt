package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuestsTest {
    @Test
    fun namesAreCheckedAndComparedLoosely() {
        assertEquals("REQUIRED", guestNameProblem("   "))
        assertEquals("TOO_LONG", guestNameProblem("x".repeat(MAX_GUEST_NAME + 1)))
        assertNull(guestNameProblem(" Rina "))
        val members = listOf("m1" to "Bima", "m2" to "Rina Putri")
        assertEquals("m2", sameNameMember("  rina   PUTRI ", members))
        assertNull(sameNameMember("Rina", members))
    }

    @Test
    fun theShareLinkLeavesTheChatToTheUser() {
        assertEquals("https://wa.me/?text=Pilih%20ya%20%F0%9F%99%8F", whatsappShareLink("Pilih ya 🙏"))
    }
}
