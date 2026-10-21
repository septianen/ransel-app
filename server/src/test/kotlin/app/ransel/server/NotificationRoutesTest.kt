package app.ransel.server

import app.ransel.server.push.PushMessage
import app.ransel.server.push.PushResult
import app.ransel.server.push.PushSender
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Remembers every push; [answer] decides the result per token (default OK). */
class RecordingPush : PushSender {
    val sent = CopyOnWriteArrayList<Pair<String, PushMessage>>()
    @Volatile var answer: (String) -> PushResult = { PushResult.OK }

    override suspend fun send(token: String, message: PushMessage): PushResult {
        val result = answer(token)
        if (result == PushResult.OK) sent += token to message
        return result
    }
}

private val fastPush = TestDb.config().copy(pushDispatchIntervalMillis = 30)

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(20) }
}

private suspend fun Api.register(token: String, locale: String = "id") =
    post("/devices", """{"platform":"ANDROID","pushToken":"$token","locale":"$locale"}""")

private suspend fun Api.inbox() = get("/notifications").data().jsonObject

/** Owner (signed in as the owner at the end) and a guest who has an account but is not on the trip. */
private class Pair2(val tripId: String, val owner: Session, val guest: Session)

private suspend fun Api.ownerAndGuest(): Pair2 {
    val owner = session()
    val tripId = UUID.randomUUID().toString()
    post("/trips", tripJson(tripId, title = "Bali 4 hari"))
    signUp()
    val guest = session()
    use(owner)
    return Pair2(tripId, owner, guest)
}

private suspend fun Api.join(tripId: String, who: Session, role: String = "EDITOR") {
    val code = post("/trips/$tripId/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
    use(who)
    assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
}

class NotificationRoutesTest {
    @Test
    fun aJoinNotifiesTheOwnerInboxAndPhoneButNotTheJoiner() {
        val push = RecordingPush()
        apiTest(config = fastPush, push = push) {
            val t = ownerAndGuest()
            assertEquals(HttpStatusCode.NoContent, register("owner-token").status)
            use(t.guest)
            register("guest-token")
            use(t.owner)
            join(t.tripId, t.guest)

            use(t.owner)
            val inbox = inbox()
            assertEquals("1", inbox.str("unreadCount"))
            val item = inbox.getValue("items").jsonArray.single().jsonObject
            assertEquals("MEMBER_JOINED", item.str("type"))
            assertEquals(t.tripId, item.str("tripId"))
            assertEquals("Bali 4 hari", item.getValue("params").jsonObject.str("tripTitle"))

            eventually { push.sent.isNotEmpty() }
            assertEquals(listOf("owner-token"), push.sent.map { it.first })
            assertEquals("MEMBER_JOINED", push.sent.single().second.data["type"])
            assertEquals(t.tripId, push.sent.single().second.data["tripId"])
            assertTrue(push.sent.single().second.title.contains("Bali 4 hari"))

            use(t.guest)
            assertEquals("0", inbox().str("unreadCount"), "the one who joined is not told about it")
        }
    }

    @Test
    fun theDevicesLanguageDecidesThePushTextButNotTheInbox() {
        val push = RecordingPush()
        apiTest(config = fastPush, push = push) {
            val t = ownerAndGuest()
            register("owner-en", locale = "en-US")
            join(t.tripId, t.guest)
            eventually { push.sent.isNotEmpty() }
            assertTrue(push.sent.single().second.body.contains("joined the trip"))
        }
    }

    @Test
    fun removalAndRoleChangeNotifyTheAffectedMember() {
        apiTest(config = fastPush, push = RecordingPush()) {
            val t = ownerAndGuest()
            join(t.tripId, t.guest)
            use(t.owner)
            assertEquals(HttpStatusCode.OK, patch("/trips/${t.tripId}/members/${t.guest.userId}", """{"role":"VIEWER"}""").status)
            assertEquals(HttpStatusCode.NoContent, delete("/trips/${t.tripId}/members/${t.guest.userId}").status)

            use(t.guest)
            val types = inbox().getValue("items").jsonArray.map { it.jsonObject.str("type") }
            assertEquals(listOf("MEMBER_REMOVED", "ROLE_CHANGED"), types)
        }
    }

    @Test
    fun anExpenseNotifiesThePeopleOnItExceptTheOneWhoRecordedIt() {
        apiTest(config = fastPush, push = RecordingPush()) {
            val t = ownerAndGuest()
            join(t.tripId, t.guest)
            use(t.owner)
            val id = UUID.randomUUID().toString()
            val body = """{"id":"$id","amountMinor":100000,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z",
                "splits":[{"userId":"${t.owner.userId}","shareMinor":50000},{"userId":"${t.guest.userId}","shareMinor":50000}]}"""
            assertEquals(HttpStatusCode.Created, post("/trips/${t.tripId}/expenses", body).status)

            use(t.owner)
            assertTrue(inbox().getValue("items").jsonArray.none { it.jsonObject.str("type") == "EXPENSE_ADDED" }, "the one who recorded it is not told")
            use(t.guest)
            val item = inbox().getValue("items").jsonArray.map { it.jsonObject }.single { it.str("type") == "EXPENSE_ADDED" }
            val params = item.getValue("params").jsonObject
            assertEquals("100000", params.str("amountMinor"))
            assertEquals("50000", params.str("shareMinor"), "each person sees their own share")
        }
    }

    @Test
    fun aSettlementNotifiesTheOtherPerson() {
        apiTest(config = fastPush, push = RecordingPush()) {
            val t = ownerAndGuest()
            join(t.tripId, t.guest)
            use(t.guest)
            val body = """{"id":"${UUID.randomUUID()}","fromUserId":"${t.guest.userId}","toUserId":"${t.owner.userId}","amountMinor":25000}"""
            assertEquals(HttpStatusCode.Created, post("/trips/${t.tripId}/settlements", body).status)
            use(t.owner)
            val item = inbox().getValue("items").jsonArray.map { it.jsonObject }.single { it.str("type") == "SETTLEMENT_MARKED" }
            assertEquals("25000", item.getValue("params").jsonObject.str("amountMinor"))
            assertEquals("IDR", item.getValue("params").jsonObject.str("currency"))
        }
    }

    @Test
    fun settingsTurnAKindOffWithoutAnInboxRowAndWithoutAPush() {
        val push = RecordingPush()
        apiTest(config = fastPush, push = push) {
            val t = ownerAndGuest()
            register("owner-token")
            assertEquals("true", get("/notification-settings").data().jsonObject.str("tripActivity"))
            val updated = patch("/notification-settings", """{"tripActivity":false}""").data().jsonObject
            assertEquals("false", updated.str("tripActivity"))
            assertEquals("true", updated.str("expenses"), "only the sent field changes")

            join(t.tripId, t.guest)
            use(t.owner)
            assertEquals("0", inbox().str("unreadCount"))
            delay(300)
            assertTrue(push.sent.isEmpty())
        }
    }

    @Test
    fun markingReadAndPagingTheInbox() {
        apiTest(config = fastPush, push = RecordingPush()) {
            val t = ownerAndGuest()
            join(t.tripId, t.guest)
            use(t.owner)
            patch("/trips/${t.tripId}/members/${t.guest.userId}", """{"role":"VIEWER"}""")
            use(t.guest)
            assertEquals("1", inbox().str("unreadCount"))
            val first = get("/notifications?limit=1").data().jsonObject
            assertEquals(1, first.getValue("items").jsonArray.size)

            assertEquals(HttpStatusCode.NoContent, post("/notifications/read", """{"ids":["${first.getValue("items").jsonArray.single().jsonObject.str("id")}"]}""").status)
            assertEquals("0", inbox().str("unreadCount"))
            assertEquals(HttpStatusCode.NoContent, post("/notifications/read", "{}").status)
            assertEquals(422, post("/notifications/read", """{"ids":["nope"]}""").status.value)
        }
    }

    @Test
    fun aTokenBelongsToTheLastUserAndUnregisterRemovesIt() {
        apiTest(config = fastPush, push = RecordingPush()) {
            val t = ownerAndGuest()
            register("shared-phone")
            use(t.guest)
            register("shared-phone")
            assertEquals(listOf("1"), TestDb.column("SELECT count(*) FROM devices WHERE push_token = 'shared-phone'"))
            assertEquals(listOf(t.guest.userId), TestDb.column("SELECT user_id::text FROM devices WHERE push_token = 'shared-phone'"))

            use(t.owner)
            assertEquals(HttpStatusCode.NoContent, delete("/devices?token=shared-phone").status)
            assertEquals(listOf("1"), TestDb.column("SELECT count(*) FROM devices"), "a user cannot remove somebody else's phone")
            use(t.guest)
            delete("/devices?token=shared-phone")
            assertEquals(listOf("0"), TestDb.column("SELECT count(*) FROM devices"))
            assertEquals(422, post("/devices", """{"platform":"ANDROID","pushToken":"  "}""").status.value)
        }
    }

    @Test
    fun aDeadTokenIsDisabledAndTheInboxRowStays() {
        val push = RecordingPush().also { it.answer = { PushResult.INVALID_TOKEN } }
        apiTest(config = fastPush, push = push) {
            val t = ownerAndGuest()
            register("dead-token")
            join(t.tripId, t.guest)
            eventually { TestDb.column("SELECT count(*) FROM devices WHERE disabled_at IS NOT NULL") == listOf("1") }
            use(t.owner)
            assertEquals("1", inbox().str("unreadCount"))
        }
    }

    @Test
    fun aTemporaryFailureIsRetriedThenGivenUp() {
        val push = RecordingPush().also { it.answer = { PushResult.RETRY } }
        apiTest(config = fastPush, push = push) {
            val t = ownerAndGuest()
            register("flaky")
            join(t.tripId, t.guest)
            eventually { TestDb.column("SELECT push_state FROM notifications") == listOf("SKIPPED") }
            assertEquals(listOf("3"), TestDb.column("SELECT push_attempts::text FROM notifications"))
        }
    }

    @Test
    fun notificationsNeedASignedInUser() {
        apiTest(config = fastPush) {
            token = null
            assertEquals(HttpStatusCode.Unauthorized, get("/notifications").status)
            assertEquals(HttpStatusCode.Unauthorized, post("/devices", "{}").status)
        }
    }
}
