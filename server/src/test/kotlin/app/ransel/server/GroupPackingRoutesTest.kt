package app.ransel.server

import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushRequest
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.model.PackingCategory
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val wire = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Owner, an EDITOR (e) and a VIEWER (v). Ends signed in as the owner. */
private class PackTrip(val id: String, val owner: Session, val e: Session, val v: Session)

private suspend fun Api.packTrip(): PackTrip {
    val owner = session()
    val id = UUID.randomUUID().toString()
    post("/trips", tripJson(id))
    val joined = mutableListOf<Session>()
    for (role in listOf("EDITOR", "VIEWER")) {
        use(owner)
        val code = post("/trips/$id/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        joined += session()
    }
    use(owner)
    return PackTrip(id, owner, joined[0], joined[1])
}

private fun line(id: String, name: String, required: Boolean = false, quantity: Int = 1, order: Double = 0.0) =
    """{"id":"$id","category":"CLOTHING","name":"$name","quantity":$quantity,"required":$required,"sortOrder":$order}"""

private fun listJson(baseVersion: Int?, vararg lines: String) =
    """{"name":"Daftar gunung","baseVersion":${baseVersion ?: "null"},"items":[${lines.joinToString(",")}]}"""

private suspend fun Api.mine(trip: String) = get("/trips/$trip/personal-items").data().jsonArray.map { it.jsonObject }

class GroupPackingRoutesTest {
    @Test
    fun sentListLandsInEveryBagAndMembersOwnTheirCopies() = apiTest {
        val trip = packTrip()
        val jacket = UUID.randomUUID().toString()
        val socks = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.NotFound, get("/trips/${trip.id}/member-list").status)

        val saved = put("/trips/${trip.id}/member-list", listJson(null, line(jacket, "Jaket", required = true), line(socks, "Kaos kaki", quantity = 3, order = 1.0)))
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        assertEquals("1", saved.data().jsonObject.str("version"))
        // a second save on the old version is stale
        assertEquals("LIST_STALE", put("/trips/${trip.id}/member-list", listJson(null, line(jacket, "Jaket"))).errorCode())

        // the list is the OWNER's alone: an editor and a viewer may not read, write, send or follow it
        for (other in listOf(trip.e, trip.v)) {
            use(other)
            assertEquals(HttpStatusCode.Forbidden, get("/trips/${trip.id}/member-list").status)
            assertEquals(HttpStatusCode.Forbidden, put("/trips/${trip.id}/member-list", listJson(1, line(jacket, "Jaket"))).status)
            assertEquals(HttpStatusCode.Forbidden, post("/trips/${trip.id}/member-list/send", """{"memberIds":[]}""").status)
            assertEquals(HttpStatusCode.Forbidden, get("/trips/${trip.id}/member-list/progress").status)
        }
        assertEquals(0, mine(trip.id).size, "nothing before it is sent")

        use(trip.owner)
        // the owner already keeps a jacket: the list links it instead of adding a second one
        val ownJacket = UUID.randomUUID().toString()
        post("/trips/${trip.id}/personal-items", """{"id":"$ownJacket","category":"CLOTHING","name":"jaket"}""")
        assertEquals(HttpStatusCode.OK, post("/trips/${trip.id}/member-list/send", """{"memberIds":[]}""").status)
        // the list is everyone's own to start with, the owner's too
        val ownerLines = mine(trip.id)
        assertEquals(listOf("jaket", "Kaos kaki"), ownerLines.map { it.str("name") })
        assertEquals(ownJacket, ownerLines.first().str("id"))
        assertEquals(jacket, ownerLines.first().str("sourceItemId"))
        use(trip.v)
        val copies = mine(trip.id).associateBy { it.str("name") }
        assertEquals(setOf("Jaket", "Kaos kaki"), copies.keys)
        val jacketCopy = copies.getValue("Jaket").str("id")
        // every line is the member's: tick, rename, delete
        assertEquals(HttpStatusCode.OK, patch("/trips/${trip.id}/personal-items/$jacketCopy", """{"isPacked":true}""").status)
        assertEquals(HttpStatusCode.OK, patch("/trips/${trip.id}/personal-items/$jacketCopy", """{"name":"Jas"}""").status)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/personal-items/${copies.getValue("Kaos kaki").str("id")}").status)
        val own = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/personal-items", """{"id":"$own","category":"OTHER","name":"Buku"}""").status)

        // the owner renames the jacket, drops the socks and adds a headlamp
        use(trip.owner)
        val lamp = UUID.randomUUID().toString()
        val edited = put("/trips/${trip.id}/member-list", listJson(1, line(jacket, "Jaket tebal", required = true), line(lamp, "Headlamp", required = true, order = 2.0)))
        assertEquals("2", edited.data().jsonObject.str("version"))
        // the viewer's edited jacket stays theirs; the new line arrives; their own line stays
        use(trip.v)
        val after = mine(trip.id).associateBy { it.str("name") }
        assertEquals(setOf("Jas", "Headlamp", "Buku"), after.keys)
        assertEquals("true", after.getValue("Jas").str("isPacked"))
        // the editor never touched their copies: they follow the list
        use(trip.e)
        assertEquals(setOf("Jaket tebal", "Headlamp"), mine(trip.id).map { it.str("name") }.toSet())

        // the owner's progress: the viewer ticked 1 of 2 required
        use(trip.owner)
        val progress = get("/trips/${trip.id}/member-list/progress").data().jsonArray.map { it.jsonObject }.associateBy { it.str("memberId") }
        val viewer = progress.getValue(trip.v.memberId(trip.id))
        assertEquals("1", viewer.str("requiredDone"))
        assertEquals("2", viewer.str("requiredTotal"))
        assertTrue(trip.owner.memberId(trip.id) !in progress, "the author is not a recipient")

        // a copy the member deleted does not come back with the next save
        use(trip.v)
        val lampCopy = mine(trip.id).single { it.str("name") == "Headlamp" }.str("id")
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/personal-items/$lampCopy").status)
        use(trip.owner)
        put("/trips/${trip.id}/member-list", listJson(2, line(jacket, "Jaket tebal", required = true), line(lamp, "Headlamp LED", required = true, order = 2.0)))
        use(trip.v)
        assertEquals(setOf("Jas", "Buku"), mine(trip.id).map { it.str("name") }.toSet())
    }

    @Test
    fun chosenRecipientsOnlyAndLateJoinersWhenSentToAll() = apiTest {
        val trip = packTrip()
        put("/trips/${trip.id}/member-list", listJson(null, line(UUID.randomUUID().toString(), "Jaket")))
        post("/trips/${trip.id}/member-list/send", """{"memberIds":["${trip.v.memberId(trip.id)}"],"saveAsTemplate":true,"templateName":"Gunung"}""")
        use(trip.e)
        assertEquals(0, mine(trip.id).size)
        use(trip.v)
        assertEquals(1, mine(trip.id).size)
        use(trip.owner)
        val template = get("/packing-templates").data().jsonArray.map { it.jsonObject }.single { it.str("name") == "Gunung" }
        assertEquals(1, template.getValue("items").jsonArray.size)

        // sent to everyone: someone who joins later gets it as well
        post("/trips/${trip.id}/member-list/send", """{"memberIds":[]}""")
        use(trip.e)
        assertEquals(1, mine(trip.id).size)
        use(trip.owner)
        val code = post("/trips/${trip.id}/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        post("/invites/$code/accept")
        assertEquals(listOf("Jaket"), mine(trip.id).map { it.str("name") })
    }

    @Test
    fun remindIsOncePerTwelveHours() = apiTest {
        val trip = packTrip()
        put("/trips/${trip.id}/member-list", listJson(null, line(UUID.randomUUID().toString(), "Jaket", required = true)))
        post("/trips/${trip.id}/member-list/send", """{"memberIds":[]}""")
        val body = """{"memberIds":["${trip.v.memberId(trip.id)}"]}"""
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/member-list/remind", body).status)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/member-list/remind", body).status)
        use(trip.v)
        val reminders = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }
            .filter { it.str("type") == "PACKING_LIST" && it.getValue("params").jsonObject.str("stage") == "REMIND" }
        assertEquals(1, reminders.size)
        assertEquals("1", reminders.single().getValue("params").jsonObject.str("requiredLeft"))
        assertEquals(HttpStatusCode.Forbidden, post("/trips/${trip.id}/member-list/remind", body).status)
    }

    @Test
    fun onlyTheBringerTicksASharedItem() = apiTest {
        val trip = packTrip()
        val stove = UUID.randomUUID().toString()
        post("/trips/${trip.id}/packing", """{"id":"$stove","category":"OTHER","name":"Kompor"}""")
        use(trip.v)
        val brought = post("/trips/${trip.id}/packing/$stove/bring")
        assertEquals(HttpStatusCode.OK, brought.status, brought.bodyAsText())
        assertEquals(trip.v.memberId(trip.id), brought.data().jsonObject.str("assigneeMemberId"))
        use(trip.e)
        assertEquals(HttpStatusCode.Conflict, post("/trips/${trip.id}/packing/$stove/bring").status)
        assertEquals(HttpStatusCode.Forbidden, patch("/trips/${trip.id}/packing/$stove", """{"isPacked":true}""").status)
        use(trip.v)
        assertEquals(HttpStatusCode.OK, patch("/trips/${trip.id}/packing/$stove", """{"isPacked":true}""").status)
        // an editor may give it back for them; the tick goes with it
        use(trip.e)
        val back = delete("/trips/${trip.id}/packing/$stove/bring").data().jsonObject
        assertEquals("false", back.str("isPacked"))
        assertTrue(back["assigneeMemberId"].toString() == "null")

        // "Siapa yang bawa?" when adding: an editor names a member, not a stranger
        val tent = UUID.randomUUID().toString()
        val added = post("/trips/${trip.id}/packing", """{"id":"$tent","name":"Tenda","assigneeMemberId":"${trip.v.memberId(trip.id)}"}""")
        assertEquals(trip.v.memberId(trip.id), added.data().jsonObject.str("assigneeMemberId"))
        assertEquals("VALIDATION_FAILED", post("/trips/${trip.id}/packing", """{"id":"${UUID.randomUUID()}","name":"Tenda","assigneeMemberId":"${UUID.randomUUID()}"}""").errorCode())
    }

    @Test
    fun syncCarriesOnlyMyOwnLinesAndBringRules() = apiTest {
        val trip = packTrip()
        val stove = UUID.randomUUID().toString()
        post("/trips/${trip.id}/packing", """{"id":"$stove","category":"OTHER","name":"Kompor"}""")
        put("/trips/${trip.id}/member-list", listJson(null, line(UUID.randomUUID().toString(), "Jaket")))
        post("/trips/${trip.id}/member-list/send", """{"memberIds":[]}""")

        use(trip.v)
        val now = Clock.System.now()
        val pulled = pull()
        val copy = pulled.changes.personalItems.single()
        assertEquals(trip.v.memberId(trip.id), copy.ownerMemberId)
        val stoveRow = pulled.changes.packingItems.single { it.id == stove }
        val own = PersonalItemDto(UUID.randomUUID().toString(), trip.id, trip.v.memberId(trip.id), PackingCategory.OTHER, "Buku", createdAt = now, updatedAt = now)
        val pushed = push(
            SyncChanges(
                personalItems = listOf(
                    own,
                    copy.copy(isPacked = true, name = "Jaket hujan", updatedAt = now + 1.minutes),
                    // someone else's line, and a copy pretending to come from the list
                    own.copy(id = UUID.randomUUID().toString(), ownerMemberId = trip.e.memberId(trip.id)),
                    own.copy(id = UUID.randomUUID().toString(), sourceItemId = copy.sourceItemId),
                ),
                packingItems = listOf(stoveRow.copy(assigneeMemberId = trip.v.memberId(trip.id), isPacked = true, updatedAt = now + 1.minutes)),
            ),
        )
        assertEquals(setOf(own.id, copy.id, stove), pushed.accepted.map { it.id }.toSet(), pushed.rejected.toString())
        assertEquals(listOf("FORBIDDEN", "FORBIDDEN"), pushed.rejected.map { it.code })
        assertEquals("Jaket hujan", pull().changes.personalItems.single { it.id == copy.id }.name, "a copy is the member's to rename")

        // a viewer cannot rename the shared item
        val renamed = push(SyncChanges(packingItems = listOf(stoveRow.copy(name = "Kompor gas", assigneeMemberId = trip.v.memberId(trip.id), isPacked = true, updatedAt = now + 2.minutes))))
        assertEquals("FORBIDDEN", renamed.rejected.single().code)

        // the editor sees the item as brought and ticked, and none of the viewer's own lines
        use(trip.e)
        val editorPull = pull()
        val stoveNow = editorPull.changes.packingItems.single { it.id == stove }
        assertEquals(trip.v.memberId(trip.id), stoveNow.assigneeMemberId)
        assertTrue(stoveNow.isPacked)
        assertTrue(editorPull.changes.personalItems.none { it.ownerMemberId == trip.v.memberId(trip.id) })
    }
}

private suspend fun Api.push(changes: SyncChanges): SyncPushResponse {
    val response = post("/sync", wire.encodeToString(SyncPushRequest(changes)))
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPushResponse>>(response.bodyAsText()).data
}

private suspend fun Api.pull(): SyncPullResponse {
    val response = get("/sync")
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPullResponse>>(response.bodyAsText()).data
}
