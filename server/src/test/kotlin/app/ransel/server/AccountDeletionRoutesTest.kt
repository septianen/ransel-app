package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Account deletion: sessions end, sign-in stops, personal data goes, the group's numbers stay. */
class AccountDeletionRoutesTest {
    private val password = "Passw0rdOk"

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private suspend fun Api.newTrip(title: String = "Bali"): String {
        val id = UUID.randomUUID().toString()
        post("/trips", tripJson(id, title))
        return id
    }

    /** The current user invites a new account, which joins. Ends signed in as the inviter. */
    private suspend fun Api.addFriend(tripId: String): Session {
        val me = session()
        val code = post("/trips/$tripId/invites", """{"role":"EDITOR","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        val friend = session()
        use(me)
        return friend
    }

    private fun expense(amount: Long, paidBy: String, shares: Map<String, Long>): String {
        val splits = shares.entries.joinToString(",", "[", "]") { """{"memberId":"${it.key}","shareMinor":${it.value}}""" }
        return """{"id":"${UUID.randomUUID()}","amountMinor":$amount,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z","paidByMemberId":"$paidBy","category":"FOOD","description":"Makan","splits":$splits}"""
    }

    private fun count(sql: String) = TestDb.column(sql).single().toInt()

    @Test
    fun theWrongPasswordChangesNothing() = apiTest {
        signUp(password = password)
        val wrong = post("/me/deletion", """{"password":"nope"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, wrong.status)
        assertEquals("WRONG", wrong.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("password"))
        assertEquals(HttpStatusCode.OK, get("/me").status)
    }

    @Test
    fun sessionsEndAndTheEmailCannotSignInButCanRegisterAgain() = apiTest {
        val email = "gone-${UUID.randomUUID()}@example.com"
        signUp(email = email, password = password)
        val refresh = refreshToken
        val me = userId
        assertEquals(HttpStatusCode.NoContent, post("/me/deletion", """{"password":"$password"}""").status)

        // the access token still in hand stops working at once, also on routes that do not read the user
        assertEquals(HttpStatusCode.Unauthorized, get("/trips").status)
        assertEquals(HttpStatusCode.Unauthorized, get("/me").status)
        token = null
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/refresh", """{"refreshToken":"$refresh"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/login", """{"email":"$email","password":"$password"}""").status)

        assertEquals(1, count("SELECT count(*) FROM users WHERE id = '$me' AND email IS NULL AND password_hash IS NULL AND avatar_url IS NULL AND deleted_at IS NOT NULL AND display_name = 'Anggota terhapus'"))
        assertEquals(0, count("SELECT count(*) FROM users WHERE lower(email) = lower('$email')"))
        assertEquals(0, count("SELECT count(*) FROM refresh_tokens WHERE user_id = '$me'"))

        // the address is free again, as a new person
        assertEquals(HttpStatusCode.Created, signUp(email = email).status)
        assertTrue(userId != me)
    }

    @Test
    fun personalRowsAreGone() = apiTest {
        signUp(password = password)
        val me = userId
        assertEquals(HttpStatusCode.Created, post("/me/payment-methods", """{"kind":"BANK","provider":"BCA","accountNumber":"123456","holderName":"Ayu"}""").status)
        assertEquals(HttpStatusCode.NoContent, post("/devices", """{"platform":"ANDROID","pushToken":"push-token-1"}""").status)
        get("/credits")
        assertEquals(30, get("/me/deletion").data().jsonObject.str("credits").toInt())
        assertEquals(1, count("SELECT count(*) FROM payment_methods WHERE user_id = '$me'"))
        assertEquals(1, count("SELECT count(*) FROM devices WHERE user_id = '$me'"))
        assertEquals(1, count("SELECT count(*) FROM credit_wallets WHERE user_id = '$me'"))

        assertEquals(HttpStatusCode.NoContent, post("/me/deletion", """{"password":"$password"}""").status)
        for (table in listOf("payment_methods", "credit_wallets", "credit_ledger", "credit_daily", "devices", "notifications", "notification_settings", "ai_search_log")) {
            assertEquals(0, count("SELECT count(*) FROM $table WHERE user_id = '$me'"), table)
        }
        assertEquals(0, count("SELECT count(*) FROM trip_templates WHERE owner_id = '$me'"))
    }

    @Test
    fun anOwnedTripGoesToTheFirstMemberAndBalancesHold() = apiTest {
        signUp(password = password)
        val owner = session()
        val tripId = newTrip("Bali")
        val first = addFriend(tripId)
        addFriend(tripId)
        val mOwner = owner.memberId(tripId)
        val mFirst = first.memberId(tripId)
        assertEquals(HttpStatusCode.Created, post("/trips/$tripId/expenses", expense(30000, mOwner, mapOf(mOwner to 15000, mFirst to 15000))).status)
        TestDb.exec("UPDATE trip_members SET phone = '628123' WHERE id = '$mOwner'")

        use(first)
        val before = get("/trips/$tripId/balances").data().jsonObject
        use(owner)
        val preview = get("/me/deletion").data().jsonObject
        assertEquals(listOf("Bali"), preview.getValue("tripsHandedOver").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(HttpStatusCode.NoContent, post("/me/deletion", """{"password":"$password"}""").status)

        use(first)
        val after = get("/trips/$tripId/balances").data().jsonObject
        fun amounts(o: JsonObject) = o.getValue("members").jsonArray.map { it.jsonObject }.associate { it.str("memberId") to it.str("balanceMinor") }
        assertEquals(amounts(before), amounts(after), "the others' numbers do not change")
        assertEquals(before.getValue("transfers"), after.getValue("transfers"))

        // the friend who joined first owns the trip, and can run it
        assertEquals(1, count("SELECT count(*) FROM trip_members WHERE id = '$mFirst' AND role = 'OWNER'"))
        assertEquals(1, count("SELECT count(*) FROM trips WHERE id = '$tripId' AND owner_id = '${first.userId}' AND deleted_at IS NULL"))
        assertEquals(1, count("SELECT count(*) FROM trip_members WHERE id = '$mOwner' AND user_id IS NULL AND phone IS NULL AND display_name = 'Anggota terhapus' AND removed_at IS NOT NULL"))
    }

    @Test
    fun aTripWithNobodyElseIsDeleted() = apiTest {
        signUp(password = password)
        val solo = newTrip("Sendiri")
        val preview = get("/me/deletion").data().jsonObject
        assertEquals(listOf("Sendiri"), preview.getValue("tripsDeleted").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(HttpStatusCode.NoContent, post("/me/deletion", """{"password":"$password"}""").status)
        assertEquals(1, count("SELECT count(*) FROM trips WHERE id = '$solo' AND deleted_at IS NOT NULL"))
    }

    @Test
    fun aMemberWhoIsNotOwnerLeavesTheTripWithTheOwner() = apiTest {
        signUp()
        val owner = session()
        val tripId = newTrip()
        val friend = addFriend(tripId)
        use(friend)
        assertEquals(HttpStatusCode.NoContent, post("/me/deletion", """{"password":"$password"}""").status)
        use(owner)
        assertEquals(1, count("SELECT count(*) FROM trip_members WHERE id = '${friend.memberId(tripId)}' AND user_id IS NULL AND removed_at IS NOT NULL"))
        assertEquals(HttpStatusCode.OK, get("/trips/$tripId").status)
    }
}
