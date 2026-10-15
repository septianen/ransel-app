package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val FAR_FUTURE = "2099-01-01T00:00:00Z"

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

/** Two or three real accounts around one trip. Starts signed in as the owner. */
private class Trip(val id: String, val owner: Session, val guest: Session, val third: Session)

private suspend fun Api.setup(): Trip {
    val owner = session()
    val id = UUID.randomUUID().toString()
    post("/trips", tripJson(id, title = "Bali 4 hari"))
    signUp()
    val guest = session()
    signUp()
    val third = session()
    use(owner)
    return Trip(id, owner, guest, third)
}

private suspend fun Api.invite(trip: Trip, role: String = "EDITOR", maxUses: Int? = null): String {
    val response = post(
        "/trips/${trip.id}/invites",
        """{"role":"$role","expiresAt":"$FAR_FUTURE"${if (maxUses != null) ""","maxUses":$maxUses""" else ""}}""",
    )
    assertEquals(HttpStatusCode.Created, response.status)
    return response.data().jsonObject.str("code")
}

class InviteRoutesTest {
    @Test
    fun ownerInvitesGuestPreviewsAndJoins() = apiTest {
        val trip = setup()
        val created = post("/trips/${trip.id}/invites", """{"role":"VIEWER","maxUses":3,"expiresAt":"$FAR_FUTURE"}""")
        val dto = created.data().jsonObject
        val code = dto.str("code")
        assertEquals(true, code.startsWith("BALI-"))
        assertEquals(true, dto.str("url").endsWith("/j/$code"))

        use(trip.guest)
        val preview = get("/invites/${code.lowercase()}").data().jsonObject
        assertEquals("Bali 4 hari", preview.str("tripTitle"))
        assertEquals("VIEWER", preview.str("role"))
        assertEquals("1", preview.str("memberCount"))

        val joined = post("/invites/$code/accept")
        assertEquals(HttpStatusCode.OK, joined.status)
        assertEquals("VIEWER", joined.data().jsonObject.str("myRole"))
        assertEquals(1, get("/trips").data().jsonArray.size)

        use(trip.owner)
        val listed = get("/trips/${trip.id}/invites").data().jsonArray.single().jsonObject
        assertEquals("1", listed.str("usedCount"))
        assertEquals(2, get("/trips/${trip.id}").data().jsonObject.getValue("summary").jsonObject.str("memberCount").toInt())
    }

    @Test
    fun checksRunInOrderRevokedExpiredFullAlreadyMember() = apiTest {
        val trip = setup()

        val revoked = invite(trip)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/invites/$revoked").status)
        use(trip.guest)
        assertEquals("INVITE_REVOKED", post("/invites/$revoked/accept").errorCode())
        assertEquals(HttpStatusCode.Gone, get("/invites/$revoked").status)

        use(trip.owner)
        val expired = invite(trip)
        TestDb.exec("UPDATE trip_invites SET expires_at = now() - interval '1 hour' WHERE code = '$expired'")
        use(trip.guest)
        assertEquals("INVITE_EXPIRED", post("/invites/$expired/accept").errorCode())

        use(trip.owner)
        val single = invite(trip, maxUses = 1)
        use(trip.guest)
        assertEquals(HttpStatusCode.OK, post("/invites/$single/accept").status)
        use(trip.third)
        val full = post("/invites/$single/accept")
        assertEquals(HttpStatusCode.Conflict, full.status)
        assertEquals("INVITE_FULL", full.errorCode())

        use(trip.owner)
        val open = invite(trip)
        use(trip.guest)
        val again = post("/invites/$open/accept")
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("ALREADY_MEMBER", again.errorCode())
    }

    @Test
    fun onlyOwnerManagesInvitesAndOwnerRoleCannotBeInvited() = apiTest {
        val trip = setup()
        val code = invite(trip, role = "EDITOR")
        use(trip.guest)
        post("/invites/$code/accept")

        assertEquals(HttpStatusCode.Forbidden, post("/trips/${trip.id}/invites", """{"role":"EDITOR","expiresAt":"$FAR_FUTURE"}""").status)
        assertEquals(HttpStatusCode.Forbidden, get("/trips/${trip.id}/invites").status)

        use(trip.owner)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${trip.id}/invites", """{"role":"OWNER","expiresAt":"$FAR_FUTURE"}""").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${trip.id}/invites", """{"role":"EDITOR","expiresAt":"2020-01-01T00:00:00Z"}""").status)
        assertEquals(HttpStatusCode.NotFound, get("/invites/NOPE-0000").status)
    }
}

class MemberRoutesTest {
    private suspend fun Api.join(trip: Trip, who: Session, role: String = "EDITOR") {
        use(trip.owner)
        val code = invite(trip, role)
        use(who)
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
    }

    @Test
    fun emailsAreVisibleToTheOwnerOnly() = apiTest {
        val trip = setup()
        join(trip, trip.guest)

        use(trip.owner)
        val asOwner = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject }
        assertEquals(2, asOwner.size)
        assertEquals(true, asOwner.all { it["email"] != null })

        use(trip.guest)
        val asGuest = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject }
        assertEquals(true, asGuest.all { it["email"] == null })
        assertEquals(setOf("OWNER", "EDITOR"), asGuest.map { it.str("role") }.toSet())
    }

    @Test
    fun viewerCannotChangeContentUntilPromoted() = apiTest {
        val trip = setup()
        join(trip, trip.guest, role = "VIEWER")
        val item = itemJson()

        use(trip.guest)
        assertEquals(HttpStatusCode.OK, get("/trips/${trip.id}/itinerary").status)
        val denied = post("/trips/${trip.id}/itinerary", item)
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("FORBIDDEN", denied.errorCode())

        use(trip.owner)
        val patched = patch("/trips/${trip.id}/members/${trip.guest.userId}", """{"role":"EDITOR"}""")
        assertEquals("EDITOR", patched.data().jsonObject.str("role"))
        assertEquals(HttpStatusCode.UnprocessableEntity, patch("/trips/${trip.id}/members/${trip.guest.userId}", """{"role":"OWNER"}""").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, patch("/trips/${trip.id}/members/${trip.owner.userId}", """{"role":"EDITOR"}""").status)

        use(trip.guest)
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/itinerary", item).status)
        assertEquals(HttpStatusCode.Forbidden, delete("/trips/${trip.id}").status) // only the owner deletes
    }

    @Test
    fun removedMemberLosesAccessButKeepsTheirExpenses() = apiTest {
        val trip = setup()
        join(trip, trip.guest)

        use(trip.guest)
        val expenseId = UUID.randomUUID().toString()
        val body = """{"id":"$expenseId","amountMinor":50000,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z"}"""
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/expenses", body).status)

        use(trip.owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/members/${trip.guest.userId}").status)
        val expenses = get("/trips/${trip.id}/expenses").data().jsonArray
        assertEquals(1, expenses.size)
        assertEquals(trip.guest.userId, expenses.single().jsonObject.str("paidByUserId"))

        use(trip.guest)
        assertEquals("TRIP_NOT_FOUND", get("/trips/${trip.id}").errorCode())
        assertEquals(0, get("/trips").data().jsonArray.size)
    }

    @Test
    fun leaveAndOwnerRules() = apiTest {
        val trip = setup()
        join(trip, trip.guest)

        use(trip.owner)
        val ownerLeave = post("/trips/${trip.id}/leave")
        assertEquals(HttpStatusCode.Conflict, ownerLeave.status)
        assertEquals("OWNER_CANNOT_LEAVE", ownerLeave.errorCode())
        assertEquals("OWNER_CANNOT_LEAVE", delete("/trips/${trip.id}/members/${trip.owner.userId}").errorCode())

        use(trip.guest)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/leave").status)
        assertEquals("TRIP_NOT_FOUND", get("/trips/${trip.id}").errorCode())
    }

    @Test
    fun transferOwnershipSwapsRolesAtomically() = apiTest {
        val trip = setup()
        join(trip, trip.guest)

        use(trip.owner)
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            post("/trips/${trip.id}/transfer-ownership", """{"newOwnerUserId":"${trip.third.userId}"}""").status,
        ) // not a member
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/transfer-ownership", """{"newOwnerUserId":"${trip.guest.userId}"}""").status)

        val roles = get("/trips/${trip.id}/members").data().jsonArray.associate { it.jsonObject.str("userId") to it.jsonObject.str("role") }
        assertEquals("EDITOR", roles[trip.owner.userId])
        assertEquals("OWNER", roles[trip.guest.userId])
        assertEquals(HttpStatusCode.Forbidden, delete("/trips/${trip.id}").status) // old owner lost the right

        use(trip.guest)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}").status)
    }
}
