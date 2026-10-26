package app.ransel.server

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.obj(key: String) = this[key]?.takeIf { it !is JsonNull }?.jsonObject

/** A request from a browser without an account: no bearer, maybe a guest token. */
private suspend fun Api.guest(method: HttpMethod, path: String, token: String? = null, body: String? = null, headers: Map<String, String> = emptyMap()): HttpResponse =
    client.request("/api/v1$path") {
        this.method = method
        token?.let { header("X-Guest-Token", it) }
        headers.forEach { (k, v) -> header(k, v) }
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

private class GuestTrip(val id: String, val owner: Session, val pollId: String, val code: String, val bima: String, val optionA: String, val optionB: String)

/** A trip with the owner, a member without an account (Bima) and an open poll that was shared. Signed in as the owner. */
private suspend fun Api.guestTrip(): GuestTrip {
    val owner = session()
    val id = UUID.randomUUID().toString()
    post("/trips", tripJson(id))
    val bima = UUID.randomUUID().toString()
    assertEquals(HttpStatusCode.Created, post("/trips/$id/members", """{"id":"$bima","displayName":"Bima"}""").status)
    val a = UUID.randomUUID().toString()
    val b = UUID.randomUUID().toString()
    val pollId = UUID.randomUUID().toString()
    val created = post(
        "/trips/$id/polls",
        """{"id":"$pollId","kind":"CUSTOM","question":"Makan di mana?","closesAt":"2099-01-01T00:00:00Z","options":[{"id":"$a","label":"Sate"},{"id":"$b","label":"Bakso"}]}""",
    )
    assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
    val share = post("/polls/$pollId/share").data().jsonObject
    assertTrue(share.str("url").endsWith("/p/${share.str("code")}"))
    assertEquals(share.str("code"), post("/polls/$pollId/share").data().jsonObject.str("code"), "sharing again keeps the link")
    return GuestTrip(id, owner, pollId, share.str("code"), bima, a, b)
}

class GuestRoutesTest {
    @Test
    fun aGuestPicksANameAndVotesWithoutAnAccount() = apiTest {
        val trip = guestTrip()
        val page = guest(HttpMethod.Get, "/guest/${trip.code}").data().jsonObject
        assertNull(page.obj("me"))
        assertEquals(trip.pollId, page.str("focusPollId"))
        val members = page.getValue("members").jsonArray.map { it.jsonObject }
        assertEquals(listOf("true", "false"), members.map { it.str("hasAccount") })
        // no amounts, phones or emails leak to a stranger
        assertTrue("email" !in page.toString() && "phone" !in page.toString())

        assertEquals("GUEST_NAME_REQUIRED", guest(HttpMethod.Post, "/guest/${trip.code}/polls/${trip.pollId}/votes", body = """{"votes":[]}""").errorCode())
        val ownerMember = members.first { it.str("hasAccount") == "true" }.str("memberId")
        assertEquals("MEMBER_HAS_ACCOUNT", guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"memberId":"$ownerMember"}""").errorCode())

        val identity = guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"memberId":"${trip.bima}"}""").data().jsonObject
        val token = identity.str("token")
        assertEquals("Bima", identity.obj("me")!!.str("name"))
        val voted = guest(HttpMethod.Post, "/guest/${trip.code}/polls/${trip.pollId}/votes", token, """{"votes":[{"optionId":"${trip.optionA}","version":1}]}""")
        assertEquals(HttpStatusCode.OK, voted.status, voted.bodyAsText())
        val poll = voted.data().jsonObject
        assertEquals(listOf(trip.optionA), poll.getValue("myVotes").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("2", poll.str("memberCount"), "the guest counts as someone who can vote")

        // the members see Bima's vote
        val asOwner = get("/polls/${trip.pollId}").data().jsonObject
        assertEquals(listOf(trip.bima), asOwner.getValue("votedMemberIds").jsonArray.map { it.jsonPrimitive.content })

        // a second browser cannot take Bima, and the page shows it as taken
        assertEquals("GUEST_NAME_TAKEN", guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"memberId":"${trip.bima}"}""").errorCode())
        val other = guest(HttpMethod.Get, "/guest/${trip.code}").data().jsonObject
        assertEquals("true", other.getValue("members").jsonArray.map { it.jsonObject }.single { it.str("memberId") == trip.bima }.str("taken"))
        val mine = guest(HttpMethod.Get, "/guest/${trip.code}", token).data().jsonObject
        assertEquals(trip.bima, mine.obj("me")!!.str("memberId"))

        // the owner sees a browser on Bima and releases it: the old token is nobody now
        val bima = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject }.single { it.str("memberId") == trip.bima }
        assertEquals("true", bima.str("guestBrowser"))
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/members/${trip.bima}/guest-session").status)
        assertNull(guest(HttpMethod.Get, "/guest/${trip.code}", token).data().jsonObject.obj("me"))
        assertEquals(HttpStatusCode.OK, guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"memberId":"${trip.bima}"}""").status)
    }

    @Test
    fun aWrittenNameBecomesAMemberUnlessItExists() = apiTest {
        val trip = guestTrip()
        val exists = guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"name":"  bima "}""")
        assertEquals("GUEST_NAME_EXISTS", exists.errorCode())
        assertEquals(trip.bima, exists.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("memberId"))
        assertEquals("VALIDATION_FAILED", guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"name":"   "}""").errorCode())

        val rina = guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"name":"Rina  Putri"}""").data().jsonObject
        assertEquals("Rina Putri", rina.obj("me")!!.str("name"))
        val members = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject }
        assertTrue(members.any { it.str("displayName") == "Rina Putri" && it.str("role") == "VIEWER" })
        val joined = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }
            .single { it.str("type") == "MEMBER_JOINED" }.getValue("params").jsonObject
        assertEquals("Rina Putri", joined.str("actorName"))
        assertEquals("LINK", joined.str("via"))
    }

    @Test
    fun linksCanBeTurnedOffAndInviteLinksOpenTheTripPage() = apiTest {
        val trip = guestTrip()
        val token = guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"memberId":"${trip.bima}"}""").data().jsonObject.str("token")
        assertEquals(HttpStatusCode.NoContent, delete("/polls/${trip.pollId}/share").status)
        assertEquals(HttpStatusCode.NotFound, guest(HttpMethod.Get, "/guest/${trip.code}").status)

        val invite = post("/trips/${trip.id}/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        val page = guest(HttpMethod.Get, "/guest/$invite", token).data().jsonObject
        assertNull(page["focusPollId"]?.takeIf { it !is JsonNull })
        assertEquals("Bima", page.obj("me")!!.str("name"), "the guest token holds for the trip, whichever link")
        assertEquals(listOf(trip.pollId), page.getValue("polls").jsonArray.map { it.jsonObject.str("id") })

        delete("/trips/${trip.id}/invites/$invite")
        assertEquals(HttpStatusCode.NotFound, guest(HttpMethod.Get, "/guest/$invite").status)

        // a token from another trip is nobody here
        val second = guestTrip()
        assertNull(guest(HttpMethod.Get, "/guest/${second.code}", token).data().jsonObject.obj("me"))
    }

    @Test
    fun guestsGetTheMembersListAndTickIt() = apiTest {
        val trip = guestTrip()
        val line = UUID.randomUUID().toString()
        put("/trips/${trip.id}/member-list", """{"name":"Gunung","items":[{"id":"$line","category":"CLOTHING","name":"Jaket","required":true}]}""")
        post("/trips/${trip.id}/member-list/send", """{"memberIds":[]}""")
        val progress = get("/trips/${trip.id}/member-list/progress").data().jsonArray.map { it.jsonObject }
        assertEquals(listOf(trip.bima), progress.map { it.str("memberId") })

        val token = guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"memberId":"${trip.bima}"}""").data().jsonObject.str("token")
        val packing = guest(HttpMethod.Get, "/guest/${trip.code}", token).data().jsonObject.getValue("packing").jsonArray.map { it.jsonObject }
        val jacket = packing.single().str("id")
        assertEquals(HttpStatusCode.OK, guest(HttpMethod.Patch, "/guest/${trip.code}/personal-items/$jacket", token, """{"isPacked":true}""").status)
        assertEquals("1", get("/trips/${trip.id}/member-list/progress").data().jsonArray.single().jsonObject.str("requiredDone"))
        // nobody else's lines
        assertEquals(HttpStatusCode.NotFound, guest(HttpMethod.Patch, "/guest/${trip.code}/personal-items/${UUID.randomUUID()}", token, """{"isPacked":true}""").status)

        // a written name joining later gets the list too
        val rina = guest(HttpMethod.Post, "/guest/${trip.code}/identity", body = """{"name":"Rina"}""").data().jsonObject.str("token")
        assertEquals(listOf("Jaket"), guest(HttpMethod.Get, "/guest/${trip.code}", rina).data().jsonObject.getValue("packing").jsonArray.map { it.jsonObject.str("name") })
    }

    @Test
    fun crawlersGetOpenGraphTags() = apiTest {
        val trip = guestTrip()
        val poll = guest(HttpMethod.Get, "/og/p/${trip.code}")
        assertEquals(HttpStatusCode.OK, poll.status)
        val html = poll.bodyAsText()
        assertTrue("""<meta property="og:title" content="Makan di mana? · Bali 4 hari">""" in html, html)
        assertTrue("2 pilihan · tutup " in html, html)
        assertTrue("/og/poll.png" in html && "/p/${trip.code}" in html, html)
        assertTrue("2 options" in guest(HttpMethod.Get, "/og/p/${trip.code}", headers = mapOf("Accept-Language" to "en-US,en")).bodyAsText())

        val invite = post("/trips/${trip.id}/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        val text = guest(HttpMethod.Get, "/og/j/$invite").bodyAsText()
        assertTrue("ngajak kamu ke Bali 4 hari" in text && "20–23 Nov · 2 orang" in text, text)

        val unknown = guest(HttpMethod.Get, "/og/p/NOPE42")
        assertEquals(HttpStatusCode.OK, unknown.status)
        assertTrue("<title>RANSEL</title>" in unknown.bodyAsText())
    }
}
