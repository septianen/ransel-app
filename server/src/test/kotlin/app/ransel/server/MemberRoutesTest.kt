package app.ransel.server

import io.ktor.client.statement.bodyAsText
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
        val patched = patch("/trips/${trip.id}/members/${trip.guest.memberId(trip.id)}", """{"role":"EDITOR"}""")
        assertEquals("EDITOR", patched.data().jsonObject.str("role"))
        assertEquals(HttpStatusCode.UnprocessableEntity, patch("/trips/${trip.id}/members/${trip.guest.memberId(trip.id)}", """{"role":"OWNER"}""").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, patch("/trips/${trip.id}/members/${trip.owner.memberId(trip.id)}", """{"role":"EDITOR"}""").status)

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
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/members/${trip.guest.memberId(trip.id)}").status)
        val expenses = get("/trips/${trip.id}/expenses").data().jsonArray
        assertEquals(1, expenses.size)
        assertEquals(trip.guest.memberId(trip.id), expenses.single().jsonObject.str("paidByMemberId"))

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
        assertEquals("OWNER_CANNOT_LEAVE", delete("/trips/${trip.id}/members/${trip.owner.memberId(trip.id)}").errorCode())

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

private fun namedExpense(id: String = UUID.randomUUID().toString(), amount: Long, paidBy: String, splits: Map<String, Long>): String {
    val parts = splits.entries.joinToString(",") { """{"memberId":"${it.key}","shareMinor":${it.value}}""" }
    return """{"id":"$id","amountMinor":$amount,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z","paidByMemberId":"$paidBy","splits":[$parts]}"""
}

/** Adds a person without an account; returns the member id. Signed in as the owner. */
private suspend fun Api.addNamed(trip: Trip, name: String, phone: String? = null): String {
    val id = UUID.randomUUID().toString()
    val phoneField = if (phone != null) ""","phone":"$phone"""" else ""
    val response = post("/trips/${trip.id}/members", """{"id":"$id","displayName":"$name"$phoneField}""")
    assertEquals(HttpStatusCode.Created, response.status)
    return response.data().jsonObject.str("memberId")
}

class NamedMemberRoutesTest {
    private suspend fun Api.join(trip: Trip, who: Session, role: String = "EDITOR") {
        use(trip.owner)
        val code = invite(trip, role)
        use(who)
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
    }

    @Test
    fun ownerAddsAPersonByNameAndOnlyTheOwnerSeesThePhone() = apiTest {
        val trip = setup()
        val rina = post("/trips/${trip.id}/members", """{"id":"${UUID.randomUUID()}","displayName":" Rina ","phone":"0812-3456-7890"}""")
        assertEquals(HttpStatusCode.Created, rina.status)
        val dto = rina.data().jsonObject
        assertEquals("Rina", dto.str("displayName"))
        assertEquals("VIEWER", dto.str("role"))
        assertNull(dto["userId"])
        assertEquals("6281234567890", dto.str("phone"))

        join(trip, trip.guest)
        use(trip.guest)
        val asEditor = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject }
        assertEquals(3, asEditor.size)
        assertEquals(true, asEditor.all { it["phone"] == null })
        assertEquals(true, asEditor.single { it.str("displayName") == "Rina" }["userId"] == null)
        assertEquals(HttpStatusCode.Forbidden, post("/trips/${trip.id}/members", """{"id":"${UUID.randomUUID()}","displayName":"Dewi"}""").status)

        use(trip.owner)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${trip.id}/members", """{"id":"${UUID.randomUUID()}","displayName":"  "}""").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${trip.id}/members", """{"id":"${UUID.randomUUID()}","displayName":"Dewi","phone":"123"}""").status)
    }

    @Test
    fun aPersonWithoutAnAccountCanPayAndShareButNotHaveARole() = apiTest {
        val trip = setup()
        val rina = addNamed(trip, "Rina")
        val me = trip.owner.memberId(trip.id)

        val created = post("/trips/${trip.id}/expenses", namedExpense(amount = 100_000, paidBy = rina, splits = mapOf(rina to 50_000, me to 50_000)))
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(rina, created.data().jsonObject.str("paidByMemberId"))

        val balances = get("/trips/${trip.id}/balances").data().jsonObject.getValue("members").jsonArray.map { it.jsonObject }
        assertEquals(50_000L, balances.single { it.str("memberId") == rina }.str("balanceMinor").toLong())
        assertEquals("Rina", balances.single { it.str("memberId") == rina }.str("displayName"))

        assertEquals(HttpStatusCode.UnprocessableEntity, patch("/trips/${trip.id}/members/$rina", """{"role":"EDITOR"}""").status)
        val stranger = post("/trips/${trip.id}/expenses", namedExpense(amount = 10, paidBy = UUID.randomUUID().toString(), splits = emptyMap()))
        assertEquals("NOT_A_MEMBER", stranger.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("paidByMemberId"))
    }

    @Test
    fun claimingMovesEverythingToTheClaimerAndTheOwnerCanUndo() = apiTest {
        val trip = setup()
        val rina = addNamed(trip, "Rina")
        val me = trip.owner.memberId(trip.id)
        val expenseId = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/expenses", namedExpense(expenseId, 90_000, paidBy = rina, splits = mapOf(rina to 45_000, me to 45_000))).status)
        join(trip, trip.guest)
        val guestMember = trip.guest.memberId(trip.id)

        use(trip.guest)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/members/$rina/claim").status)
        use(trip.owner)
        val moved = get("/trips/${trip.id}/expenses").data().jsonArray.single().jsonObject
        assertEquals(guestMember, moved.str("paidByMemberId"))
        assertEquals(setOf(guestMember, me), moved.getValue("splits").jsonArray.map { it.jsonObject.str("memberId") }.toSet())
        val names = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject.str("displayName") }
        assertEquals(false, names.contains("Rina"))
        val inbox = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(true, inbox.any { it.str("type") == "MEMBER_CLAIMED" && it.getValue("params").jsonObject.str("guestName") == "Rina" })

        // only the owner undoes it
        use(trip.guest)
        assertEquals(HttpStatusCode.Forbidden, delete("/trips/${trip.id}/members/$rina/claim").status)
        use(trip.owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/members/$rina/claim").status)
        val back = get("/trips/${trip.id}/expenses").data().jsonArray.single().jsonObject
        assertEquals(rina, back.str("paidByMemberId"))
        assertEquals(setOf(rina, me), back.getValue("splits").jsonArray.map { it.jsonObject.str("memberId") }.toSet())
        assertEquals(true, get("/trips/${trip.id}/members").data().jsonArray.any { it.jsonObject.str("displayName") == "Rina" })
    }

    @Test
    fun claimingMergesWhenBothAreOnTheSameExpense() = apiTest {
        val trip = setup()
        val rina = addNamed(trip, "Rina")
        join(trip, trip.guest)
        val guestMember = trip.guest.memberId(trip.id)
        use(trip.owner)
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/expenses", namedExpense(amount = 100_000, paidBy = rina, splits = mapOf(rina to 60_000, guestMember to 40_000))).status)

        use(trip.guest)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/members/$rina/claim").status)
        val merged = get("/trips/${trip.id}/expenses").data().jsonArray.single().jsonObject.getValue("splits").jsonArray.map { it.jsonObject }
        assertEquals(1, merged.size)
        assertEquals(100_000L, merged.single().str("shareMinor").toLong())

        use(trip.owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/members/$rina/claim").status)
        val split = get("/trips/${trip.id}/expenses").data().jsonArray.single().jsonObject.getValue("splits").jsonArray
            .associate { it.jsonObject.str("memberId") to it.jsonObject.str("shareMinor").toLong() }
        assertEquals(mapOf(rina to 60_000L, guestMember to 40_000L), split)
    }

    @Test
    fun claimRulesOneClaimPerAccountAndNotAnAccountMember() = apiTest {
        val trip = setup()
        val rina = addNamed(trip, "Rina")
        val dewi = addNamed(trip, "Dewi")
        join(trip, trip.guest)

        use(trip.guest)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/members/$rina/claim").status)
        val again = post("/trips/${trip.id}/members/$dewi/claim")
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("MEMBER_ALREADY_CLAIMED", again.errorCode())
        assertEquals("MEMBER_HAS_ACCOUNT", post("/trips/${trip.id}/members/${trip.owner.memberId(trip.id)}/claim").errorCode())
        assertEquals(HttpStatusCode.NotFound, post("/trips/${trip.id}/members/$rina/claim").status) // already taken

        use(trip.third)
        assertEquals("TRIP_NOT_FOUND", post("/trips/${trip.id}/members/$dewi/claim").errorCode()) // not in the trip
    }

    @Test
    fun rejoiningGivesTheSameMemberBack() = apiTest {
        val trip = setup()
        join(trip, trip.guest)
        val mine = trip.guest.memberId(trip.id)
        use(trip.guest)
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/expenses", namedExpense(amount = 1_000, paidBy = mine, splits = emptyMap())).status)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${trip.id}/leave").status)

        join(trip, trip.guest, role = "VIEWER")
        use(trip.guest)
        val ids = get("/trips/${trip.id}/members").data().jsonArray.map { it.jsonObject.str("memberId") }
        assertEquals(true, mine in ids)
        assertEquals(2, ids.size)
        assertEquals(mine, get("/trips/${trip.id}/expenses").data().jsonArray.single().jsonObject.str("paidByMemberId"))
    }

    @Test
    fun theInvitePreviewIsPublicAndLeaksNothingPrivate() = apiTest {
        val trip = setup()
        addNamed(trip, "Rina", phone = "081234567890")
        val me = trip.owner.memberId(trip.id)
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/itinerary", itemJson()).status)
        assertEquals(HttpStatusCode.Created, post("/trips/${trip.id}/expenses", namedExpense(amount = 80_000, paidBy = me, splits = mapOf(me to 80_000))).status)
        val code = invite(trip)

        use(Session(null, null, ""))
        val response = get("/invites/$code")
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        val preview = TestJson.parseToJsonElement(text).jsonObject.getValue("data").jsonObject
        assertEquals(2, preview.getValue("memberInitials").jsonArray.size)
        assertEquals(listOf("Rina"), preview.getValue("guestNames").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("1", preview.str("activityCount"))
        assertEquals("80000", preview.str("sharedSpendMinor"))
        assertEquals("IDR", preview.str("baseCurrency"))
        assertEquals(false, text.contains("081234567890") || text.contains("6281234567890"))
        assertEquals(false, text.contains("@example.com"))
        assertEquals(false, text.contains("paidBy") || text.contains("shareMinor"))

        use(trip.owner)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${trip.id}/invites/$code").status)
        use(Session(null, null, ""))
        assertEquals(HttpStatusCode.Gone, get("/invites/$code").status)
    }
}
