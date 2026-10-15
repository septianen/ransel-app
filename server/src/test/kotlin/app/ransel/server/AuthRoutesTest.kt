package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthRoutesTest {
    private fun register(email: String, password: String = "Passw0rdOk", name: String = "Budi") =
        """{"email":"$email","password":"$password","displayName":"$name","deviceName":"Pixel 8"}"""

    @Test
    fun registerReturnsSessionAndMeWorks() = apiTest {
        val response = signUp("budi@example.com", displayName = "Budi")
        assertEquals(HttpStatusCode.Created, response.status)
        val me = get("/me")
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("budi@example.com", me.data().jsonObject.getValue("email").jsonPrimitive.content)
        assertEquals("IDR", me.data().jsonObject.getValue("defaultCurrency").jsonPrimitive.content)
    }

    @Test
    fun duplicateEmailIsCaseInsensitive409() = apiTest {
        signUp("Budi@Example.com")
        val again = post("/auth/register", register("budi@example.COM"))
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("EMAIL_ALREADY_REGISTERED", again.errorCode())
    }

    @Test
    fun weakPasswordAndBadEmailAre422() = apiTest {
        val response = post("/auth/register", register("not-an-email", password = "short"))
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val details = response.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals("INVALID_EMAIL", details.getValue("email").jsonPrimitive.content)
        assertEquals("WEAK_PASSWORD", details.getValue("password").jsonPrimitive.content)

        val tooLong = post("/auth/register", register("long@example.com", password = "a1" + "x".repeat(80)))
        assertEquals(HttpStatusCode.UnprocessableEntity, tooLong.status)
    }

    @Test
    fun loginChecksPasswordAndHidesWhichPartIsWrong() = apiTest {
        signUp("login@example.com", password = "Passw0rdOk")
        val ok = post("/auth/login", """{"email":"LOGIN@example.com","password":"Passw0rdOk"}""")
        assertEquals(HttpStatusCode.OK, ok.status)

        val wrongPassword = post("/auth/login", """{"email":"login@example.com","password":"Wrong1234"}""")
        val unknownEmail = post("/auth/login", """{"email":"nobody@example.com","password":"Passw0rdOk"}""")
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals("INVALID_CREDENTIALS", wrongPassword.errorCode())
        assertEquals("INVALID_CREDENTIALS", unknownEmail.errorCode())
    }

    @Test
    fun refreshRotatesAndOldTokenIsDead() = apiTest {
        val old = refreshToken!!
        val rotated = post("/auth/refresh", """{"refreshToken":"$old"}""")
        assertEquals(HttpStatusCode.OK, rotated.status)
        val fresh = rotated.data().jsonObject.getValue("refreshToken").jsonPrimitive.content
        assertNotEquals(old, fresh)

        val replay = post("/auth/refresh", """{"refreshToken":"$old"}""")
        assertEquals(HttpStatusCode.Unauthorized, replay.status)
        assertEquals("TOKEN_EXPIRED", replay.errorCode())
        assertEquals(HttpStatusCode.OK, post("/auth/refresh", """{"refreshToken":"$fresh"}""").status)
    }

    @Test
    fun logoutRevokesTokenAndIsIdempotent() = apiTest {
        val refresh = refreshToken!!
        assertEquals(HttpStatusCode.NoContent, post("/auth/logout", """{"refreshToken":"$refresh"}""").status)
        assertEquals(HttpStatusCode.NoContent, post("/auth/logout", """{"refreshToken":"$refresh"}""").status)
        assertEquals("TOKEN_EXPIRED", post("/auth/refresh", """{"refreshToken":"$refresh"}""").errorCode())
        assertEquals("TOKEN_EXPIRED", post("/auth/refresh", """{"refreshToken":"made-up"}""").errorCode())
    }

    @Test
    fun protectedRoutesNeedAValidToken() = apiTest {
        token = null
        val anonymous = get("/trips")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals("UNAUTHORIZED", anonymous.errorCode())

        token = "not.a.jwt"
        assertEquals("UNAUTHORIZED", get("/trips").errorCode())
        assertEquals(HttpStatusCode.OK, get("/health").status)
    }

    @Test
    fun usersCannotSeeEachOthersTrips() = apiTest {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId))
        signUp()
        assertEquals(0, get("/trips").data().let { (it as kotlinx.serialization.json.JsonArray).size })
        val other = get("/trips/$tripId")
        assertEquals(HttpStatusCode.NotFound, other.status)
        assertEquals("TRIP_NOT_FOUND", other.errorCode())
    }

    @Test
    fun patchMeUpdatesProfile() = apiTest {
        val updated = patch("/me", """{"displayName":"Budi S","defaultCurrency":"USD"}""")
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("USD", updated.data().jsonObject.getValue("defaultCurrency").jsonPrimitive.content)
        assertEquals("VALIDATION_FAILED", patch("/me", """{"defaultCurrency":"usd"}""").errorCode())
    }

    @Test
    fun loginAttemptsAreRateLimited() = apiTest(TestDb.config().copy(authRateLimitPerMinute = 3)) {
        // signUp in apiTest already used one attempt of the budget
        var last = HttpStatusCode.OK
        repeat(4) { last = post("/auth/login", """{"email":"x@example.com","password":"Wrong1234"}""").status }
        assertEquals(HttpStatusCode.TooManyRequests, last)
        val blocked = post("/auth/login", """{"email":"x@example.com","password":"Wrong1234"}""")
        assertEquals("RATE_LIMITED", blocked.errorCode())
    }
}

class PasswordResetTest {
    private fun tokenOf(link: String) = link.substringAfter("token=")

    @Test
    fun forgotAlways204AndOnlySendsForKnownEmails() = apiTest {
        signUp("known@example.com")
        assertEquals(HttpStatusCode.NoContent, post("/auth/password/forgot", """{"email":"Known@Example.com"}""").status)
        assertEquals(HttpStatusCode.NoContent, post("/auth/password/forgot", """{"email":"ghost@example.com"}""").status)

        val link = TestEmails.awaitLink("known@example.com")
        assertTrue(link.startsWith("https://ransel.app/reset-password?token="))
        kotlinx.coroutines.delay(300)
        assertEquals(1, TestEmails.sent.size)
    }

    @Test
    fun resetChangesPasswordSignsOutEveryDeviceAndTokenIsSingleUse() = apiTest {
        signUp("reset@example.com", password = "OldPassw0rd")
        val oldRefresh = refreshToken!!
        post("/auth/password/forgot", """{"email":"reset@example.com"}""")
        val token = tokenOf(TestEmails.awaitLink("reset@example.com"))

        val weak = post("/auth/password/reset", """{"token":"$token","newPassword":"short"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, weak.status)

        assertEquals(HttpStatusCode.NoContent, post("/auth/password/reset", """{"token":"$token","newPassword":"NewPassw0rd"}""").status)

        assertEquals("INVALID_CREDENTIALS", post("/auth/login", """{"email":"reset@example.com","password":"OldPassw0rd"}""").errorCode())
        assertEquals(HttpStatusCode.OK, post("/auth/login", """{"email":"reset@example.com","password":"NewPassw0rd"}""").status)
        assertEquals("TOKEN_EXPIRED", post("/auth/refresh", """{"refreshToken":"$oldRefresh"}""").errorCode())

        val reuse = post("/auth/password/reset", """{"token":"$token","newPassword":"Another1Pass"}""")
        assertEquals(HttpStatusCode.BadRequest, reuse.status)
        assertEquals("RESET_TOKEN_INVALID", reuse.errorCode())
    }

    @Test
    fun unknownAndExpiredTokensAreRejected() = apiTest {
        assertEquals("RESET_TOKEN_INVALID", post("/auth/password/reset", """{"token":"nope","newPassword":"NewPassw0rd"}""").errorCode())

        signUp("late@example.com")
        post("/auth/password/forgot", """{"email":"late@example.com"}""")
        val token = tokenOf(TestEmails.awaitLink("late@example.com"))
        TestDb.expireResetTokens()
        assertEquals("RESET_TOKEN_INVALID", post("/auth/password/reset", """{"token":"$token","newPassword":"NewPassw0rd"}""").errorCode())
    }
}
