package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Owner, a member who joined as [role], a stranger outside the trip, and one itinerary item. Ends signed in as the owner. */
private class CommentTrip(val id: String, val itemId: String, val owner: Session, val member: Session, val stranger: Session)

private suspend fun Api.commentTrip(role: String = "EDITOR"): CommentTrip {
    val owner = session()
    val id = UUID.randomUUID().toString()
    post("/trips", tripJson(id))
    val itemId = UUID.randomUUID().toString()
    assertEquals(HttpStatusCode.Created, post("/trips/$id/itinerary", itemJson(id = itemId)).status)
    val code = post("/trips/$id/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
    signUp()
    val member = session()
    assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
    signUp()
    val stranger = session()
    use(owner)
    return CommentTrip(id, itemId, owner, member, stranger)
}

private fun commentJson(id: String = UUID.randomUUID().toString(), itemId: String, body: String, mentions: List<String> = emptyList()) =
    """{"id":"$id","targetKind":"ITINERARY_ITEM","targetId":"$itemId","body":"$body","mentions":[${mentions.joinToString(",") { "\"$it\"" }}]}"""

class CommentRoutesTest {
    @Test
    fun membersCommentOnAnItemAndSeeEachOther() = apiTest {
        val t = commentTrip(role = "VIEWER") // a viewer may comment too
        val first = post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "Berangkat jam 8 ya"))
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals("Berangkat jam 8 ya", first.data().jsonObject.str("body"))
        assertEquals(t.owner.memberId(t.id), first.data().jsonObject.str("authorMemberId"))

        use(t.member)
        assertEquals(HttpStatusCode.Created, post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "Setuju")).status)
        val listed = get("/trips/${t.id}/comments?targetKind=ITINERARY_ITEM&targetId=${t.itemId}").data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("Berangkat jam 8 ya", "Setuju"), listed.map { it.str("body") })
        assertEquals("2", get("/trips/${t.id}/comments/counts?targetKind=ITINERARY_ITEM").data().jsonArray.single().jsonObject.str("count"))
        assertEquals(0, get("/trips/${t.id}/comments/counts?targetKind=EXPENSE").data().jsonArray.size)
    }

    @Test
    fun onlyTheAuthorEditsOrDeletes() = apiTest {
        val t = commentTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/${t.id}/comments", commentJson(id = id, itemId = t.itemId, body = "Awal"))

        use(t.member)
        assertEquals(HttpStatusCode.Forbidden, patch("/comments/$id", """{"body":"Diubah"}""").status)
        assertEquals(HttpStatusCode.Forbidden, delete("/comments/$id").status)

        use(t.owner)
        val edited = patch("/comments/$id", """{"body":"Diubah"}""")
        assertEquals(HttpStatusCode.OK, edited.status)
        assertEquals("Diubah", edited.data().jsonObject.str("body"))
        assertEquals(true, edited.data().jsonObject["editedAt"] != null)
        assertEquals(HttpStatusCode.NoContent, delete("/comments/$id").status)
        assertEquals(0, get("/trips/${t.id}/comments?targetKind=ITINERARY_ITEM&targetId=${t.itemId}").data().jsonArray.size)
        assertEquals(HttpStatusCode.NotFound, patch("/comments/$id", """{"body":"Lagi"}""").status)
    }

    @Test
    fun bodyTargetAndMembershipAreChecked() = apiTest {
        val t = commentTrip()
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "  ")).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "a".repeat(1001))).status)
        assertEquals(HttpStatusCode.Created, post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "a".repeat(1000))).status)
        assertEquals(HttpStatusCode.NotFound, post("/trips/${t.id}/comments", commentJson(itemId = UUID.randomUUID().toString(), body = "Hai")).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/trips/${t.id}/comments?targetKind=NOPE&targetId=${t.itemId}").status)

        use(t.stranger)
        assertEquals("TRIP_NOT_FOUND", post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "Hai")).errorCode())
        assertEquals("TRIP_NOT_FOUND", get("/trips/${t.id}/comments?targetKind=ITINERARY_ITEM&targetId=${t.itemId}").errorCode())
    }

    @Test
    fun anExpenseCanBeCommentedOn() = apiTest {
        val t = commentTrip()
        val expenseId = UUID.randomUUID().toString()
        val me = t.owner.memberId(t.id)
        val body = """{"id":"$expenseId","amountMinor":50000,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z","paidByMemberId":"$me"}"""
        assertEquals(HttpStatusCode.Created, post("/trips/${t.id}/expenses", body).status)
        val comment = """{"id":"${UUID.randomUUID()}","targetKind":"EXPENSE","targetId":"$expenseId","body":"Struknya mana?"}"""
        assertEquals(HttpStatusCode.Created, post("/trips/${t.id}/comments", comment).status)
        assertEquals(expenseId, get("/trips/${t.id}/comments/counts?targetKind=EXPENSE").data().jsonArray.single().jsonObject.str("targetId"))
    }

    @Test
    fun aMentionNotifiesThatMemberOnceAndNeverTheAuthor() = apiTest {
        val t = commentTrip()
        val member = t.member.memberId(t.id)
        val me = t.owner.memberId(t.id)
        val id = UUID.randomUUID().toString()
        val stranger = UUID.randomUUID().toString() // not a member: dropped
        val created = post("/trips/${t.id}/comments", commentJson(id = id, itemId = t.itemId, body = "@Tester cek ya", mentions = listOf(member, me, stranger)))
        assertEquals(listOf(member), created.data().jsonObject.getValue("mentions").jsonArray.map { it.jsonPrimitive.content })

        // editing keeps the same dedupe key, so the member still has exactly one notification
        assertEquals(HttpStatusCode.OK, patch("/comments/$id", """{"body":"@Tester cek lagi","mentions":["$member"]}""").status)

        use(t.member)
        val inbox = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }
        val mentions = inbox.filter { it.str("type") == "COMMENT_MENTION" }
        assertEquals(1, mentions.size)
        val params = mentions.single().getValue("params").jsonObject
        assertEquals("ITINERARY_ITEM", params.str("targetKind"))
        assertEquals(t.itemId, params.str("targetId"))
        assertEquals("@Tester cek ya", params.str("snippet"))

        use(t.owner)
        assertEquals(0, get("/notifications").data().jsonObject.getValue("items").jsonArray.count { it.jsonObject.str("type") == "COMMENT_MENTION" })
    }

    @Test
    fun aRemovedMemberCanNoLongerReadOrWrite() = apiTest {
        val t = commentTrip()
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${t.id}/members/${t.member.memberId(t.id)}").status)
        use(t.member)
        assertEquals("TRIP_NOT_FOUND", post("/trips/${t.id}/comments", commentJson(itemId = t.itemId, body = "Hai")).errorCode())
    }
}
