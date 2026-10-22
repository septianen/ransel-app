package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EmailVerificationTest {
    private fun tokenOf(link: String) = link.substringAfter("token=")

    private suspend fun Api.verify(token: String) = post("/auth/email/verify", """{"token":"$token"}""")
    private suspend fun Api.emailVerified() = get("/me").data().jsonObject.getValue("emailVerified").jsonPrimitive.content
    private suspend fun Api.balance() = get("/credits").data().jsonObject.getValue("balance").jsonPrimitive.content

    private suspend fun awaitVerifyEmails(count: Int) {
        repeat(100) {
            if (TestEmails.count(TestEmails.Kind.VERIFY) >= count) return
            delay(50)
        }
        error("expected $count verification emails, got ${TestEmails.count(TestEmails.Kind.VERIFY)}")
    }

    @Test
    fun signUpEmailsALinkAndTheAccountStartsUnverified() = apiTest {
        assertEquals(HttpStatusCode.Created, signUp("new@example.com", verified = false).status)
        val link = TestEmails.awaitLink("new@example.com", TestEmails.Kind.VERIFY)
        assertTrue(link.startsWith("https://ransel.mietek.tech/verify-email?token="))
        assertEquals("false", emailVerified())
    }

    @Test
    fun anUnverifiedAccountCannotExploreOrRedeemAndHasNoSignupCredits() = apiTest {
        signUp("wait@example.com", verified = false)
        assertEquals("0", balance())
        assertEquals(emptyList(), get("/credits/ledger").data().jsonArray.toList())

        val redeem = post("/credits/redeem", """{"code":"ANY"}""")
        assertEquals(HttpStatusCode.Forbidden, redeem.status)
        assertEquals("EMAIL_NOT_VERIFIED", redeem.errorCode())
        val suggestions = get("/explore/suggestions?destination=Bali&month=2026-11")
        assertEquals(HttpStatusCode.Forbidden, suggestions.status)
        assertEquals("EMAIL_NOT_VERIFIED", suggestions.errorCode())

        // The rest of the app works.
        assertEquals(HttpStatusCode.OK, get("/me").status)
    }

    @Test
    fun verifyingUnlocksAndGrantsTheSignupCreditsOnce() = apiTest {
        signUp("ok@example.com", verified = false)
        assertEquals("0", balance()) // the wallet exists before verification and still gets the grant
        val token = tokenOf(TestEmails.awaitLink("ok@example.com", TestEmails.Kind.VERIFY))

        val first = verify(token)
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("ok@example.com", first.data().jsonObject.getValue("email").jsonPrimitive.content)
        assertEquals("30", first.data().jsonObject.getValue("creditsGranted").jsonPrimitive.content)
        assertEquals("true", emailVerified())
        assertEquals("30", balance())

        // A second tap on the same link: still fine, nothing more.
        val again = verify(token)
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals("0", again.data().jsonObject.getValue("creditsGranted").jsonPrimitive.content)
        assertEquals("30", balance())
        val kinds = get("/credits/ledger").data().jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content }
        assertEquals(listOf("SIGNUP_GRANT"), kinds)

        assertNotEquals("EMAIL_NOT_VERIFIED", get("/explore/suggestions?destination=Bali&month=2026-11").errorCode())
    }

    @Test
    fun aResendVoidsTheOlderLink() = apiTest {
        signUp("twice@example.com", verified = false)
        awaitVerifyEmails(1)
        val old = tokenOf(TestEmails.awaitLink("twice@example.com", TestEmails.Kind.VERIFY))
        assertEquals(HttpStatusCode.NoContent, post("/auth/email/resend").status)
        awaitVerifyEmails(2)
        val new = tokenOf(TestEmails.awaitLink("twice@example.com", TestEmails.Kind.VERIFY))
        assertNotEquals(old, new)

        val stale = verify(old)
        assertEquals(HttpStatusCode.BadRequest, stale.status)
        assertEquals("VERIFY_TOKEN_INVALID", stale.errorCode())
        assertEquals(HttpStatusCode.OK, verify(new).status)
    }

    @Test
    fun unknownAndExpiredLinksAreRejected() = apiTest {
        assertEquals("VERIFY_TOKEN_INVALID", verify("nope").errorCode())

        signUp("late@example.com", verified = false)
        val token = tokenOf(TestEmails.awaitLink("late@example.com", TestEmails.Kind.VERIFY))
        TestDb.exec("UPDATE email_verification_tokens SET expires_at = now() - interval '1 minute'")
        assertEquals("VERIFY_TOKEN_INVALID", verify(token).errorCode())
        assertEquals("false", emailVerified())
    }

    @Test
    fun aVerifiedAccountGetsNoEmailFromResend() = apiTest {
        // apiTest's own account is verified (signUp's default); let its sign-up email settle first.
        delay(300)
        val before = TestEmails.count(TestEmails.Kind.VERIFY)
        assertEquals(HttpStatusCode.NoContent, post("/auth/email/resend").status)
        delay(300)
        assertEquals(before, TestEmails.count(TestEmails.Kind.VERIFY))
        assertEquals("true", emailVerified())
    }

    @Test
    fun seedAccountsAreVerified() = apiTest(
        TestDb.config().copy(seedUsers = listOf(AppConfig.SeedUser("seed@example.com", "Passw0rdOk", "Seed"))),
    ) {
        val login = post("/auth/login", """{"email":"seed@example.com","password":"Passw0rdOk"}""")
        assertEquals("true", login.data().jsonObject.getValue("user").jsonObject.getValue("emailVerified").jsonPrimitive.content)
    }
}
