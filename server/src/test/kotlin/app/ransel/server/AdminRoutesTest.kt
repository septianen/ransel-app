package app.ransel.server

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminRoutesTest {
    private val adminEmail = "admin@example.com"
    private val adminConfig = TestDb.config().copy(adminEmails = setOf(adminEmail))

    /** apiTest signs up a normal user first; this keeps that user and adds the admin. Returns (user, admin). */
    private suspend fun Api.userAndAdmin(): Pair<Session, Session> {
        val user = session()
        signUp(email = "Admin@Example.com") // the allow-list is case-insensitive
        return user to session()
    }

    private suspend fun Api.adjust(userId: String, direction: String, amount: Int, reason: String = "Kompensasi pencarian gagal", requestId: String = UUID.randomUUID().toString()) =
        post("/admin/users/$userId/credits", """{"requestId":"$requestId","direction":"$direction","amount":$amount,"reason":"$reason"}""")

    private suspend fun Api.balanceOf(userId: String) =
        get("/admin/users/$userId").data().jsonObject.getValue("credits").jsonObject.getValue("balance").jsonPrimitive.content

    @Test
    fun aNormalAccountGets403OnEveryAdminRoute() = apiTest(adminConfig) {
        val id = userId
        val routes = listOf(
            HttpMethod.Get to "/admin/me",
            HttpMethod.Get to "/admin/users",
            HttpMethod.Post to "/admin/users",
            HttpMethod.Get to "/admin/users/export.csv",
            HttpMethod.Get to "/admin/users/$id",
            HttpMethod.Get to "/admin/users/$id/credits/ledger",
            HttpMethod.Post to "/admin/users/$id/credits",
            HttpMethod.Post to "/admin/users/$id/sign-out-all",
            HttpMethod.Post to "/admin/users/$id/disable",
            HttpMethod.Post to "/admin/users/$id/enable",
            HttpMethod.Post to "/admin/users/$id/password-reset",
            HttpMethod.Get to "/admin/audit",
        )
        routes.forEach { (method, path) ->
            val body = if (method == HttpMethod.Post) """{"requestId":"${UUID.randomUUID()}","direction":"ADD","amount":5,"reason":"abc"}""" else null
            val response = send(method, path, body)
            assertEquals(HttpStatusCode.Forbidden, response.status, "$method $path")
            assertEquals("FORBIDDEN", response.errorCode())
        }
        token = null
        assertEquals(HttpStatusCode.Unauthorized, get("/admin/me").status)
    }

    @Test
    fun meTellsTheEnvironmentAndThatResetEmailIsOff() = apiTest(adminConfig) {
        userAndAdmin()
        val me = get("/admin/me").data().jsonObject
        assertEquals(adminEmail, me.getValue("email").jsonPrimitive.content.lowercase())
        assertEquals("development", me.getValue("environment").jsonPrimitive.content)
        assertEquals("false", me.getValue("passwordResetEmailEnabled").jsonPrimitive.content)
    }

    @Test
    fun usersListFiltersAndPagesAndNeverShowsTripContent() = apiTest(adminConfig) {
        assertEquals(HttpStatusCode.Created, post("/trips", tripJson(title = "Rahasia keluarga")).status)
        val (user, _) = userAndAdmin()

        val all = get("/admin/users")
        val meta = all.jsonBody().getValue("meta").jsonObject
        assertEquals("2", meta.getValue("total").jsonPrimitive.content)
        val found = get("/admin/users?query=admin").data().jsonArray
        assertEquals(1, found.size)
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/admin/users?status=NOPE").status)

        val detail = get("/admin/users/${user.userId}")
        val text = detail.bodyAsText()
        assertFalse("Rahasia keluarga" in text, "trip titles are trip content")
        val trips = detail.data().jsonObject.getValue("trips").jsonArray
        assertEquals("Bali", trips.single().jsonObject.getValue("destination").jsonPrimitive.content)
        assertEquals("OWNER", trips.single().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(HttpStatusCode.NotFound, get("/admin/users/${UUID.randomUUID()}").status)
    }

    @Test
    fun adjustingAddsAndSubtractsAndShowsInBothHistoriesAndTheAudit() = apiTest(adminConfig) {
        val (user, admin) = userAndAdmin()
        assertEquals(HttpStatusCode.OK, adjust(user.userId, "ADD", 10).status)
        val after = adjust(user.userId, "SUBTRACT", 15, reason = "Koreksi kredit dobel")
        assertEquals(HttpStatusCode.OK, after.status)
        assertEquals("25", after.data().jsonObject.getValue("balance").jsonPrimitive.content) // 30 + 10 - 15

        val adminLedger = get("/admin/users/${user.userId}/credits/ledger").data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("-15", "10", "30"), adminLedger.map { it.getValue("delta").jsonPrimitive.content })
        assertEquals("Koreksi kredit dobel", adminLedger.first().getValue("note").jsonPrimitive.content)
        assertEquals("25", adminLedger.first().getValue("balanceAfter").jsonPrimitive.content)
        assertEquals(admin.userId, adminLedger.first().getValue("ref").jsonPrimitive.content)

        val audit = get("/admin/audit?targetId=${user.userId}").data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("CREDITS_ADJUST", "CREDITS_ADJUST"), audit.map { it.getValue("action").jsonPrimitive.content })

        use(user)
        val own = get("/credits/ledger").data().jsonArray.map { it.jsonObject }
        assertEquals("ADJUSTMENT", own.first().getValue("kind").jsonPrimitive.content)
        assertEquals("-15", own.first().getValue("delta").jsonPrimitive.content)
        assertNull(own.first()["note"]?.takeIf { it.toString() != "null" }, "the reason is admin-only")
        assertEquals("25", get("/credits").data().jsonObject.getValue("balance").jsonPrimitive.content)
    }

    @Test
    fun subtractingMoreThanTheBalanceIs422AndChangesNothing() = apiTest(adminConfig) {
        val (user, _) = userAndAdmin()
        val response = adjust(user.userId, "SUBTRACT", 31)
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("EXCEEDS_BALANCE", response.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.getValue("amount").jsonPrimitive.content)
        assertEquals("30", balanceOf(user.userId))
        assertEquals(HttpStatusCode.OK, adjust(user.userId, "SUBTRACT", 30).status)
        assertEquals("0", balanceOf(user.userId))
    }

    @Test
    fun theSameRequestIdChangesTheBalanceOnce() = apiTest(adminConfig) {
        val (user, _) = userAndAdmin()
        val id = UUID.randomUUID().toString()
        adjust(user.userId, "ADD", 7, requestId = id)
        val again = adjust(user.userId, "ADD", 7, requestId = id)
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals("37", again.data().jsonObject.getValue("balance").jsonPrimitive.content)
        assertEquals(1, get("/admin/audit?action=CREDITS_ADJUST").data().jsonArray.size)
    }

    @Test
    fun badAdjustRequestsAreRejectedPerField() = apiTest(adminConfig) {
        val (user, _) = userAndAdmin()
        val response = post("/admin/users/${user.userId}/credits", """{"requestId":"nope","direction":"ADD","amount":0,"reason":" "}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val details = response.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals(setOf("requestId", "amount", "reason"), details.keys)
    }

    @Test
    fun aDisabledAccountCannotSignInOrRefreshUntilEnabled() = apiTest(adminConfig) {
        val email = "traveller@example.com"
        signUp(email = email)
        val user = session()
        signUp(email = adminEmail)

        val disabled = post("/admin/users/${user.userId}/disable")
        assertEquals(HttpStatusCode.OK, disabled.status)
        assertEquals("DISABLED", disabled.data().jsonObject.getValue("user").jsonObject.getValue("status").jsonPrimitive.content)
        assertEquals("1", disabled.data().jsonObject.getValue("revokedSessions").jsonPrimitive.content)
        assertEquals(HttpStatusCode.Conflict, post("/admin/users/$userId/disable").status, "not yourself")

        val login = post("/auth/login", """{"email":"$email","password":"Passw0rdOk"}""")
        assertEquals(HttpStatusCode.Forbidden, login.status)
        assertEquals("ACCOUNT_DISABLED", login.errorCode())
        val wrong = post("/auth/login", """{"email":"$email","password":"Wrong1234"}""")
        assertEquals("INVALID_CREDENTIALS", wrong.errorCode(), "a wrong password never reveals the state")
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/refresh", """{"refreshToken":"${user.refreshToken}"}""").status, "revoked")

        assertEquals(HttpStatusCode.OK, post("/admin/users/${user.userId}/enable").status)
        assertEquals(HttpStatusCode.OK, post("/auth/login", """{"email":"$email","password":"Passw0rdOk"}""").status)
        val actions = get("/admin/audit?targetId=${user.userId}").data().jsonArray.map { it.jsonObject.getValue("action").jsonPrimitive.content }
        assertEquals(listOf("USER_ENABLE", "USER_DISABLE"), actions)
    }

    @Test
    fun signOutAllRevokesEverySession() = apiTest(adminConfig) {
        val (user, _) = userAndAdmin()
        val result = post("/admin/users/${user.userId}/sign-out-all")
        assertEquals("1", result.data().jsonObject.getValue("revokedSessions").jsonPrimitive.content)
        assertEquals("TOKEN_EXPIRED", post("/auth/refresh", """{"refreshToken":"${user.refreshToken}"}""").errorCode())
    }

    @Test
    fun passwordResetEmailIsOffUntilConfigured() = apiTest(adminConfig) {
        val (user, _) = userAndAdmin()
        assertEquals(HttpStatusCode.Conflict, post("/admin/users/${user.userId}/password-reset").status)
    }

    @Test
    fun passwordResetEmailIsSentAndLimited() = apiTest(adminConfig.copy(adminPasswordResetEnabled = true)) {
        signUp(email = "forgetful@example.com")
        val user = session()
        signUp(email = adminEmail)
        assertEquals(HttpStatusCode.NoContent, post("/admin/users/${user.userId}/password-reset").status)
        assertTrue(TestEmails.awaitLink("forgetful@example.com").contains("token="))
        // Tokens are written in the background; wait for all three before the fourth request.
        repeat(2) { post("/admin/users/${user.userId}/password-reset") }
        var waited = 0
        while (TestDb.column("SELECT count(*) FROM password_reset_tokens").single().toInt() < 3 && waited++ < 100) {
            kotlinx.coroutines.delay(50)
        }
        assertEquals(HttpStatusCode.TooManyRequests, post("/admin/users/${user.userId}/password-reset").status)
    }

    @Test
    fun theCsvExportListsAccountsAndIsAudited() = apiTest(adminConfig) {
        userAndAdmin()
        val response = get("/admin/users/export.csv")
        assertEquals(HttpStatusCode.OK, response.status)
        val lines = response.bodyAsText().trim().lines()
        assertTrue(lines.first().startsWith("id,email,display_name,status"))
        assertEquals(3, lines.size, "header + two accounts")
        assertEquals("USERS_EXPORT", get("/admin/audit").data().jsonArray.single().jsonObject.getValue("action").jsonPrimitive.content)
    }

    private val inviteConfig = adminConfig.copy(adminPasswordResetEnabled = true)

    private suspend fun Api.createUser(email: String, name: String = "Rina") =
        post("/admin/users", """{"email":"$email","displayName":"$name"}""")

    @Test
    fun addingAUserNeedsEmailToBeSetUp() = apiTest(adminConfig) {
        userAndAdmin()
        assertEquals(HttpStatusCode.Conflict, createUser("rina@example.com").status)
    }

    @Test
    fun anAddedUserIsInvitedAndSetsTheirOwnPassword() = apiTest(inviteConfig.copy(registrationEnabled = false)) {
        userAndAdmin()
        val created = createUser("Rina@Example.com", " Rina ")
        assertEquals(HttpStatusCode.Created, created.status)
        val user = created.data().jsonObject
        val id = user.getValue("id").jsonPrimitive.content
        assertEquals("Rina", user.getValue("displayName").jsonPrimitive.content)
        assertEquals("ACTIVE", user.getValue("status").jsonPrimitive.content)
        assertEquals("USER_CREATE", get("/admin/audit?targetId=$id").data().jsonArray.single().jsonObject.getValue("action").jsonPrimitive.content)

        // Nobody knows a password yet.
        assertEquals("INVALID_CREDENTIALS", post("/auth/login", """{"email":"rina@example.com","password":"Passw0rdOk"}""").errorCode())

        val link = TestEmails.awaitLink("rina@example.com", TestEmails.Kind.INVITE)
        val token = link.substringAfter("token=")
        assertEquals(HttpStatusCode.NoContent, post("/auth/password/reset", """{"token":"$token","newPassword":"Rina1234ok"}""").status)
        val login = post("/auth/login", """{"email":"rina@example.com","password":"Rina1234ok"}""")
        assertEquals(HttpStatusCode.OK, login.status, "works while public sign-up is closed")
        assertEquals("true", login.data().jsonObject.getValue("user").jsonObject.getValue("emailVerified").jsonPrimitive.content)
    }

    @Test
    fun addingAUserRejectsTakenEmailsAndBadInput() = apiTest(inviteConfig) {
        userAndAdmin()
        assertEquals(HttpStatusCode.Created, createUser("taken@example.com").status)
        val again = createUser("TAKEN@example.com")
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("EMAIL_ALREADY_REGISTERED", again.errorCode())

        val bad = createUser("not-an-email", "  ")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        assertEquals(setOf("email", "displayName"), bad.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.keys)
    }
}
