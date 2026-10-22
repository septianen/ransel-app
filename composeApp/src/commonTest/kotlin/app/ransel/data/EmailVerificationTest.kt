package app.ransel.data

import app.ransel.data.auth.EmailVerification
import app.ransel.data.auth.parseVerifyToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailVerificationTest {
    @Test
    fun parsesTheVerificationUrlForms() {
        assertEquals("aB3_-x", parseVerifyToken("https://ransel.mietek.tech/verify-email?token=aB3_-x"))
        assertEquals("aB3_-x", parseVerifyToken("https://ransel.mietek.tech/verify-email/?token=aB3_-x"))
        assertEquals("aB3_-x", parseVerifyToken("ransel.mietek.tech/verify-email?utm=mail&token=aB3_-x&x=1"))
        assertEquals("aB3_-x", parseVerifyToken("  https://ransel.mietek.tech/verify-email?token=aB3_-x  "))
    }

    @Test
    fun ignoresOtherUrls() {
        assertNull(parseVerifyToken("https://ransel.mietek.tech/reset-password?token=abc"))
        assertNull(parseVerifyToken("https://ransel.mietek.tech/verify-email"))
        assertNull(parseVerifyToken("https://ransel.mietek.tech/j/BALI-7K2Q"))
        assertNull(parseVerifyToken("https://evil.example/verify-email?token=abc"))
        assertNull(parseVerifyToken("https://ransel.mietek.tech.evil.example/verify-email?token=abc"))
    }

    @Test
    fun tokenAndPromptStayUntilCleared() {
        val verification = EmailVerification()
        verification.offerUrl("https://ransel.mietek.tech/j/BALI-7K2Q")
        assertNull(verification.token.value)
        verification.offerUrl("https://ransel.mietek.tech/verify-email?token=abc")
        assertEquals("abc", verification.token.value)
        verification.clearToken()
        assertNull(verification.token.value)

        verification.prompt("budi@example.com")
        assertEquals("budi@example.com", verification.prompt.value)
        verification.clearPrompt()
        assertNull(verification.prompt.value)
    }
}
