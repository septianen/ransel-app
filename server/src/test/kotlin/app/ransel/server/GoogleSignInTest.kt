package app.ransel.server

import app.ransel.server.security.GoogleIdentity
import app.ransel.server.security.GoogleVerifier
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Token format of the fake: `sub|email|emailVerified|name`. Anything else is invalid. */
private object FakeGoogle : GoogleVerifier {
    override suspend fun verifyIdToken(idToken: String): GoogleIdentity? {
        val p = idToken.split('|')
        if (p.size != 4) return null
        return GoogleIdentity(p[0], p[1], p[2] == "true", p[3].ifEmpty { null })
    }

    override suspend fun verifyAuthCode(code: String): GoogleIdentity? = verifyIdToken(code)
}

class GoogleSignInTest {
    private val config get() = TestDb.config().copy(googleWebClientId = "web-id", googleIosClientId = "ios-id")

    private fun google(token: String) = """{"idToken":"$token","deviceName":"Pixel 8"}"""
    private suspend fun Api.balance() = get("/credits").data().jsonObject.getValue("balance").jsonPrimitive.content
    private suspend fun Api.useSession(data: kotlinx.serialization.json.JsonObject) {
        token = data.getValue("accessToken").jsonPrimitive.content
    }

    @Test
    fun newGoogleUserGetsVerifiedAccountNameAndCredits() = apiTest(config, googleVerifier = FakeGoogle) {
        val response = post("/auth/google", google("sub-1|new@example.com|true|Sari Dewi"))
        assertEquals(HttpStatusCode.OK, response.status)
        val session = response.data().jsonObject
        assertEquals("true", session.getValue("newAccount").jsonPrimitive.content)
        val user = session.getValue("user").jsonObject
        assertEquals("Sari Dewi", user.getValue("displayName").jsonPrimitive.content)
        assertEquals("true", user.getValue("emailVerified").jsonPrimitive.content)
        useSession(session)
        assertEquals("30", balance())
    }

    @Test
    fun secondSignInIsTheSameAccountAndNotNew() = apiTest(config, googleVerifier = FakeGoogle) {
        val first = post("/auth/google", google("sub-2|a@example.com|true|A")).data().jsonObject
        val second = post("/auth/google", google("sub-2|a@example.com|true|A")).data().jsonObject
        assertEquals("false", second.getValue("newAccount").jsonPrimitive.content)
        assertEquals(
            first.getValue("user").jsonObject.getValue("id").jsonPrimitive.content,
            second.getValue("user").jsonObject.getValue("id").jsonPrimitive.content,
        )
    }

    @Test
    fun verifiedPasswordAccountIsLinkedAndKeepsItsPassword() = apiTest(config, googleVerifier = FakeGoogle) {
        signUp("link@example.com", password = "Passw0rdOk")
        val ownId = userId
        val response = post("/auth/google", google("sub-3|Link@Example.com|true|Link"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ownId, response.data().jsonObject.getValue("user").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("false", response.data().jsonObject.getValue("newAccount").jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, post("/auth/login", """{"email":"link@example.com","password":"Passw0rdOk"}""").status)
    }

    @Test
    fun unverifiedPasswordAccountIsTakenOverAndLosesPasswordAndSessions() = apiTest(config, googleVerifier = FakeGoogle) {
        signUp("victim@example.com", password = "Passw0rdOk", verified = false)
        val oldRefresh = refreshToken
        val response = post("/auth/google", google("sub-4|victim@example.com|true|Real Owner"))
        assertEquals(HttpStatusCode.OK, response.status)
        val user = response.data().jsonObject.getValue("user").jsonObject
        assertEquals("true", user.getValue("emailVerified").jsonPrimitive.content)
        // old password gone; Google-only now
        val login = post("/auth/login", """{"email":"victim@example.com","password":"Passw0rdOk"}""")
        assertEquals(HttpStatusCode.Unauthorized, login.status)
        assertEquals("GOOGLE_ACCOUNT_ONLY", login.errorCode())
        // the pre-registrant's refresh token is dead
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/refresh", """{"refreshToken":"$oldRefresh"}""").status)
        useSession(response.data().jsonObject)
        assertEquals("30", balance())
    }

    @Test
    fun passwordOnGoogleOnlyAccountIsGoogleAccountOnly() = apiTest(config, googleVerifier = FakeGoogle) {
        post("/auth/google", google("sub-5|only@example.com|true|Only"))
        val login = post("/auth/login", """{"email":"only@example.com","password":"Passw0rdOk"}""")
        assertEquals(HttpStatusCode.Unauthorized, login.status)
        assertEquals("GOOGLE_ACCOUNT_ONLY", login.errorCode())
        val unknown = post("/auth/login", """{"email":"nobody@example.com","password":"Passw0rdOk"}""")
        assertEquals("INVALID_CREDENTIALS", unknown.errorCode())
    }

    @Test
    fun emailGoogleDoesNotVouchForAndBadTokensAreRefused() = apiTest(config, googleVerifier = FakeGoogle) {
        val unverified = post("/auth/google", google("sub-6|x@example.com|false|X"))
        assertEquals(HttpStatusCode.Unauthorized, unverified.status)
        assertEquals("GOOGLE_TOKEN_INVALID", unverified.errorCode())
        assertEquals("GOOGLE_TOKEN_INVALID", post("/auth/google", google("garbage")).errorCode())
        assertEquals("GOOGLE_TOKEN_INVALID", post("/auth/google", "{}").errorCode())
    }

    @Test
    fun closedRegistrationRefusesNewGoogleUsers() = apiTest(config.copy(registrationEnabled = false), googleVerifier = FakeGoogle) {
        val response = post("/auth/google", google("sub-7|c@example.com|true|C"))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun disabledAccountCannotSignInWithGoogle() = apiTest(config, googleVerifier = FakeGoogle) {
        signUp("off@example.com")
        TestDb.exec("UPDATE users SET disabled_at = now() WHERE id = '$userId'")
        val response = post("/auth/google", google("sub-8|off@example.com|true|Off"))
        assertEquals("ACCOUNT_DISABLED", response.errorCode())
    }

    @Test
    fun configExposesIdsOnlyWhenSetUp() = apiTest(config, googleVerifier = FakeGoogle) {
        val on = get("/auth/google/config").data().jsonObject
        assertEquals("web-id", on.getValue("webClientId").jsonPrimitive.content)
        assertEquals("ios-id", on.getValue("iosClientId").jsonPrimitive.content)
    }

    @Test
    fun googleSignInIsOffWithoutClientId() = apiTest {
        assertEquals(null, get("/auth/google/config").data().jsonObject["webClientId"]?.jsonPrimitive?.content?.takeIf { it != "null" })
        assertNotEquals(HttpStatusCode.OK, post("/auth/google", google("sub-9|z@example.com|true|Z")).status)
    }
}
