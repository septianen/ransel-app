package app.ransel.data

import app.ransel.data.invite.InviteLinks
import app.ransel.data.invite.parseInviteCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinksTest {
    @Test
    fun parsesTheInviteUrlForms() {
        assertEquals("BALI-7K2Q", parseInviteCode("https://ransel.mietek.tech/j/BALI-7K2Q"))
        assertEquals("BALI-7K2Q", parseInviteCode("https://ransel.mietek.tech/j/bali-7k2q/"))
        assertEquals("BALI-7K2Q", parseInviteCode("ransel.mietek.tech/j/BALI-7K2Q?utm=x"))
        assertEquals("BALI-7K2Q", parseInviteCode("  https://ransel.mietek.tech/j/BALI-7K2Q  "))
    }

    @Test
    fun ignoresOtherUrls() {
        assertNull(parseInviteCode("https://ransel.mietek.tech/"))
        assertNull(parseInviteCode("https://ransel.mietek.tech/reset-password?token=abc"))
        assertNull(parseInviteCode("https://evil.example/j/BALI-7K2Q"))
        assertNull(parseInviteCode("https://ransel.app/j/BALI-7K2Q")) // the old domain
        assertNull(parseInviteCode("https://ransel.mietek.tech.evil.example/j/BALI-7K2Q"))
    }

    @Test
    fun pendingCodeStaysUntilCleared() {
        val links = InviteLinks()
        links.offerUrl("https://ransel.mietek.tech/j/BALI-7K2Q")
        assertEquals("BALI-7K2Q", links.pending.value)
        links.offerUrl("https://ransel.mietek.tech/other")
        assertEquals("BALI-7K2Q", links.pending.value)
        links.clear()
        assertNull(links.pending.value)
    }
}
