package app.ransel.server

import app.ransel.server.service.PollService
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
private fun JsonElement.obj() = jsonObject
private fun JsonObject.strOrNull(key: String) = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

/** Owner plus two EDITORs (a, b) and a VIEWER (v), and a stranger. Ends signed in as the owner. */
private class PollTrip(val id: String, val owner: Session, val a: Session, val b: Session, val v: Session, val stranger: Session)

private suspend fun Api.pollTrip(): PollTrip {
    val owner = session()
    val id = UUID.randomUUID().toString()
    post("/trips", tripJson(id))
    val joined = mutableListOf<Session>()
    for (role in listOf("EDITOR", "EDITOR", "VIEWER")) {
        use(owner)
        val code = post("/trips/$id/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        joined += session()
    }
    signUp()
    val stranger = session()
    use(owner)
    return PollTrip(id, owner, joined[0], joined[1], joined[2], stranger)
}

private fun option(label: String, id: String = UUID.randomUUID().toString(), extra: String = "") = """{"id":"$id","label":"$label"$extra}"""

private fun pollJson(
    id: String = UUID.randomUUID().toString(),
    kind: String = "CUSTOM",
    question: String = "Makan di mana?",
    closesAt: String = "2099-01-01T00:00:00Z",
    extra: String = "",
    options: List<String> = listOf(option("Sate"), option("Bakso")),
) = """{"id":"$id","kind":"$kind","question":"$question","closesAt":"$closesAt"$extra,"options":[${options.joinToString(",")}]}"""

private suspend fun Api.createPoll(trip: PollTrip, json: String = pollJson()): JsonObject {
    val response = post("/trips/${trip.id}/polls", json)
    assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText() + " for " + json)
    return response.data().obj()
}

private fun JsonObject.options() = getValue("options").jsonArray.map { it.obj() }
private fun JsonObject.vote(index: Int) = options()[index].let { """{"optionId":"${it.str("id")}","version":${it.str("version")}}""" }
private suspend fun Api.voteFor(pollId: String, vararg votes: String) =
    put("/polls/$pollId/votes", """{"votes":[${votes.joinToString(",")}]}""")

private fun expire(pollId: String) = TestDb.exec("UPDATE polls SET closes_at = now() - interval '1 minute' WHERE id = '$pollId'")

private suspend fun Api.inboxOf(session: Session): List<JsonObject> {
    val back = session()
    use(session)
    val items = get("/notifications").data().obj().getValue("items").jsonArray.map { it.obj() }
    use(back)
    return items
}

class PollRoutesTest {
    @Test
    fun editorsCreateAndEveryoneVotesOncePerPoll() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t)
        val pollId = poll.str("id")
        assertEquals("OPEN", poll.str("status"))
        assertEquals(2, poll.options().size)
        assertEquals("4", poll.str("memberCount"))

        // members hear about it, the creator does not
        assertEquals(true, inboxOf(t.a).any { it.str("type") == "POLL_REQUESTED" && it.getValue("params").obj().str("stage") == "OPEN" })
        assertEquals(false, inboxOf(t.owner).any { it.str("type") == "POLL_REQUESTED" })

        use(t.a)
        val voted = voteFor(pollId, poll.vote(0))
        assertEquals(HttpStatusCode.OK, voted.status)
        assertEquals(listOf(poll.options()[0].str("id")), voted.data().obj().getValue("myVotes").jsonArray.map { it.jsonPrimitive.content })

        use(t.v) // a viewer votes too
        assertEquals(HttpStatusCode.OK, voteFor(pollId, poll.vote(0)).status)

        use(t.b)
        val seen = get("/polls/$pollId").data().obj()
        assertEquals("2", seen.options()[0].str("voteCount"))
        assertEquals(2, seen.options()[0].getValue("voterIds").jsonArray.size) // not anonymous: names are visible
        assertEquals(0, seen.getValue("myVotes").jsonArray.size)
        assertEquals(1, get("/trips/${t.id}/polls").data().jsonArray.size)

        // changing the vote replaces it; an empty list takes it back
        use(t.a)
        assertEquals("1", voteFor(pollId, poll.vote(1)).data().obj().options()[0].str("voteCount")) // only the viewer is left on it
        // a took the vote back; the viewer's stays
        assertEquals(1, voteFor(pollId).data().obj().getValue("votedMemberIds").jsonArray.size)
    }

    @Test
    fun viewersAndStrangersCannotCreate() = apiTest {
        val t = pollTrip()
        use(t.v)
        assertEquals(HttpStatusCode.Forbidden, post("/trips/${t.id}/polls", pollJson()).status)
        use(t.stranger)
        assertEquals("TRIP_NOT_FOUND", post("/trips/${t.id}/polls", pollJson()).errorCode())
        assertEquals("TRIP_NOT_FOUND", get("/trips/${t.id}/polls").errorCode())
    }

    @Test
    fun theFormIsChecked() = apiTest {
        val t = pollTrip()
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${t.id}/polls", pollJson(question = "  ")).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${t.id}/polls", pollJson(closesAt = "2020-01-01T00:00:00Z")).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${t.id}/polls", pollJson(kind = "DATE", options = listOf(option("")))).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${t.id}/polls", pollJson(options = listOf(option("")))).status)
    }

    @Test
    fun aDatePollHasTwoToFiveRangesNoneInThePast() = apiTest {
        val t = pollTrip()
        fun range(day: String) = option("", extra = ""","startDate":"$day","endDate":"$day"""")
        fun date(vararg days: String) = pollJson(kind = "DATE", question = "Kapan berangkat?", options = days.map { range(it) })
        val few = post("/trips/${t.id}/polls", date("2099-12-01"))
        assertEquals(HttpStatusCode.UnprocessableEntity, few.status)
        assertTrue("TOO_FEW" in few.bodyAsText(), few.bodyAsText())
        val many = post("/trips/${t.id}/polls", date("2099-12-01", "2099-12-02", "2099-12-03", "2099-12-04", "2099-12-05", "2099-12-06"))
        assertEquals(HttpStatusCode.UnprocessableEntity, many.status)
        val past = post("/trips/${t.id}/polls", date("2020-01-01", "2099-12-01"))
        assertEquals(HttpStatusCode.UnprocessableEntity, past.status)
        assertTrue("BEFORE_TODAY" in past.bodyAsText(), past.bodyAsText())

        // five is the most, also when ranges are added later
        val poll = createPoll(t, date("2099-12-01", "2099-12-02", "2099-12-03", "2099-12-04"))
        assertEquals(HttpStatusCode.Created, post("/polls/${poll.str("id")}/options", range("2099-12-05")).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/polls/${poll.str("id")}/options", range("2099-12-06")).status)
    }

    @Test
    fun oneChoiceUnlessTheCreatorAllowsSeveral() = apiTest {
        val t = pollTrip()
        val single = createPoll(t)
        val two = voteFor(single.str("id"), single.vote(0), single.vote(1))
        assertEquals(HttpStatusCode.UnprocessableEntity, two.status)

        val multi = createPoll(t, pollJson(extra = ""","multiChoice":true"""))
        val both = voteFor(multi.str("id"), multi.vote(0), multi.vote(1))
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals(2, both.data().obj().getValue("myVotes").jsonArray.size)
    }

    @Test
    fun anAnonymousPollNeverShowsWhoChoseWhatAndTheSwitchLocks() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t, pollJson(extra = ""","anonymous":true"""))
        val pollId = poll.str("id")
        use(t.a)
        assertEquals(HttpStatusCode.OK, voteFor(pollId, poll.vote(0)).status)

        use(t.owner)
        val asCreator = get("/polls/$pollId").data().obj()
        assertEquals("1", asCreator.options()[0].str("voteCount"))
        assertEquals(true, asCreator.options().all { it.getValue("voterIds").jsonArray.isEmpty() })
        assertEquals(1, asCreator.getValue("votedMemberIds").jsonArray.size) // who has voted stays visible

        val locked = patch("/polls/$pollId", """{"anonymous":false}""")
        assertEquals(HttpStatusCode.Conflict, locked.status)
        assertEquals("POLL_LOCKED", locked.errorCode())
    }

    @Test
    fun theSwitchCanChangeUntilTheFirstVoteButBudgetStaysAnonymous() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t, pollJson(extra = ""","anonymous":true"""))
        assertEquals("false", patch("/polls/${poll.str("id")}", """{"anonymous":false}""").data().obj().str("anonymous"))

        val budget = createPoll(t, pollJson(kind = "BUDGET", question = "Budget per orang?", options = listOf(option("", extra = ""","minMinor":500000,"maxMinor":1000000"""))))
        assertEquals("true", budget.str("anonymous"))
        assertEquals(HttpStatusCode.UnprocessableEntity, patch("/polls/${budget.str("id")}", """{"anonymous":false}""").status)
    }

    @Test
    fun editingAnOptionClearsItsVotesAndAnOldVoteIsRefused() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t)
        val pollId = poll.str("id")
        val first = poll.options()[0]
        use(t.a)
        voteFor(pollId, poll.vote(0))

        use(t.owner)
        val edited = patch("/polls/$pollId/options/${first.str("id")}", option("Sate Padang", first.str("id")))
        assertEquals(HttpStatusCode.OK, edited.status)
        val after = edited.data().obj().options()[0]
        assertEquals("2", after.str("version"))
        assertEquals("0", after.str("voteCount"))
        assertEquals(true, inboxOf(t.a).any { it.str("type") == "POLL_REQUESTED" && it.getValue("params").obj()["stage"]?.jsonPrimitive?.content == "RESET" })

        use(t.b)
        val stale = voteFor(pollId, poll.vote(0)) // still the old version
        assertEquals(HttpStatusCode.Conflict, stale.status)
        assertEquals("VOTE_STALE", stale.errorCode())
        assertEquals(HttpStatusCode.OK, voteFor(pollId, """{"optionId":"${first.str("id")}","version":2}""").status)

        // a new picture is not what people voted on
        use(t.owner)
        val picture = patch("/polls/$pollId/options/${first.str("id")}", option("Sate Padang", first.str("id"), ""","imageUrl":"https://x.example/a.jpg""""))
        assertEquals("2", picture.data().obj().options()[0].str("version"))
        assertEquals("1", picture.data().obj().options()[0].str("voteCount"))
    }

    @Test
    fun anOptionCanBeAddedAfterVotesAndRemovedWithItsVotes() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t)
        val pollId = poll.str("id")
        use(t.a)
        voteFor(pollId, poll.vote(0))
        use(t.owner)
        val added = post("/polls/$pollId/options", option("Nasi Goreng"))
        assertEquals(HttpStatusCode.Created, added.status)
        assertEquals(3, added.data().obj().options().size)
        assertEquals("1", added.data().obj().options()[0].str("voteCount")) // existing votes stay

        val removed = delete("/polls/$pollId/options/${poll.options()[0].str("id")}")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertEquals(2, removed.data().obj().options().size)
        assertEquals(0, removed.data().obj().getValue("votedMemberIds").jsonArray.size)
    }

    @Test
    fun votingAfterTheClosingTimeIsRefusedAndTheStatusFollows() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t)
        val pollId = poll.str("id")
        use(t.a)
        voteFor(pollId, poll.vote(0))
        expire(pollId)

        use(t.b)
        val late = voteFor(pollId, poll.vote(0))
        assertEquals(HttpStatusCode.Conflict, late.status)
        assertEquals("POLL_CLOSED", late.errorCode())
        assertEquals("CLOSED", get("/polls/$pollId").data().obj().str("status"))
        assertEquals("POLL_CLOSED", post("/polls/$pollId/options", option("Terlambat")).errorCode())
    }

    @Test
    fun aTieIsToldToTheCreatorWhoPicksOrLetsItReopen() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t)
        val pollId = poll.str("id")
        use(t.a); voteFor(pollId, poll.vote(0))
        use(t.b); voteFor(pollId, poll.vote(1))
        expire(pollId)

        use(t.owner)
        val tied = get("/polls/$pollId").data().obj()
        assertEquals("TIED", tied.str("status"))
        assertEquals(2, tied.getValue("tiedOptionIds").jsonArray.size)
        assertEquals(true, inboxOf(t.owner).any { it.str("type") == "POLL_RESULT" && it.getValue("params").obj().str("stage") == "TIED" })

        // nobody decided for a day: the minute job opens it again for another day
        TestDb.exec("UPDATE polls SET tied_at = now() - interval '25 hours' WHERE id = '$pollId'")
        PollService.tick()
        val reopened = get("/polls/$pollId").data().obj()
        assertEquals("OPEN", reopened.str("status"))
        assertTrue(Instant.parse(reopened.str("closesAt")).isAfter(Instant.now()))
        use(t.a)
        assertEquals(HttpStatusCode.OK, voteFor(pollId, reopened.vote(0)).status)
    }

    @Test
    fun theCreatorPicksAmongTheTiedOptions() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t, pollJson(options = listOf(option("A"), option("B"), option("C"))))
        val pollId = poll.str("id")
        use(t.a); voteFor(pollId, poll.vote(0))
        use(t.b); voteFor(pollId, poll.vote(1))
        expire(pollId)

        use(t.owner)
        val notTied = post("/polls/$pollId/decide", """{"optionId":"${poll.options()[2].str("id")}"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, notTied.status)
        val decided = post("/polls/$pollId/decide", """{"optionId":"${poll.options()[1].str("id")}"}""")
        assertEquals(HttpStatusCode.OK, decided.status)
        assertEquals("DECIDED", decided.data().obj().str("status"))
        assertEquals(poll.options()[1].str("id"), decided.data().obj().str("decidedOptionId"))
        assertEquals(HttpStatusCode.Conflict, post("/polls/$pollId/decide", """{}""").status)

        val note = inboxOf(t.a).first { it.str("type") == "POLL_RESULT" && it.getValue("params").obj().str("stage") == "DECIDED" }
        assertEquals("B", note.getValue("params").obj().str("winner"))
    }

    @Test
    fun onlyTheCreatorOrTheOwnerManagesAPoll() = apiTest {
        val t = pollTrip()
        use(t.a)
        val poll = createPoll(t, pollJson())
        val pollId = poll.str("id")

        use(t.b)
        assertEquals(HttpStatusCode.Forbidden, post("/polls/$pollId/close").status)
        assertEquals(HttpStatusCode.Forbidden, patch("/polls/$pollId", """{"question":"Lain"}""").status)
        assertEquals(HttpStatusCode.Forbidden, post("/polls/$pollId/decide", """{}""").status)

        use(t.owner)
        assertEquals("CLOSED", post("/polls/$pollId/close").data().obj().str("status"))
        val reopen = post("/polls/$pollId/reopen", """{"closesAt":"2099-02-01T00:00:00Z"}""")
        assertEquals("OPEN", reopen.data().obj().str("status"))
        assertEquals(HttpStatusCode.NoContent, delete("/polls/$pollId").status)
        assertEquals(HttpStatusCode.NotFound, get("/polls/$pollId").status)
    }

    @Test
    fun aCreatorWhoLeavesHandsThePollToTheOwner() = apiTest {
        val t = pollTrip()
        use(t.a)
        val poll = createPoll(t)
        assertEquals(HttpStatusCode.NoContent, post("/trips/${t.id}/leave").status)
        use(t.owner)
        assertEquals(t.owner.memberId(t.id), get("/polls/${poll.str("id")}").data().obj().str("createdByMemberId"))
    }

    @Test
    fun theWinnerOfADatePollBecomesTheTripDates() = apiTest {
        val t = pollTrip()
        val poll = createPoll(
            t,
            pollJson(
                kind = "DATE", question = "Kapan berangkat?", extra = ""","multiChoice":true""",
                options = listOf(
                    option("", extra = ""","startDate":"2099-12-01","endDate":"2099-12-04""""),
                    option("", extra = ""","startDate":"2099-12-10","endDate":"2099-12-13""""),
                ),
            ),
        )
        val pollId = poll.str("id")
        assertEquals("2099-12-01 – 2099-12-04", poll.options()[0].str("label"))
        use(t.a); voteFor(pollId, poll.vote(0), poll.vote(1))
        use(t.b); voteFor(pollId, poll.vote(1))
        use(t.owner)
        post("/polls/$pollId/close")
        assertEquals("DECIDED", post("/polls/$pollId/decide", """{"optionId":"${poll.options()[1].str("id")}"}""").data().obj().str("status"))
        val trip = get("/trips/${t.id}").data().obj()
        assertEquals("2099-12-10", trip.getValue("trip").obj().str("startDate"))
        assertEquals("2099-12-13", trip.getValue("trip").obj().str("endDate"))
    }

    @Test
    fun placeFoodAndDestinationPollsPutTheWinnerInThePlan() = apiTest {
        val t = pollTrip()
        val place = createPoll(t, pollJson(kind = "PLACE", question = "Ke mana?", options = listOf(option("Tanah Lot", extra = ""","placeId":"gp1""""))))
        post("/polls/${place.str("id")}/decide", """{"optionId":"${place.options()[0].str("id")}"}""")

        val food = createPoll(t, pollJson(kind = "FOOD", question = "Makan malam?", extra = ""","dayDate":"2026-11-21","startTime":"19:00"""", options = listOf(option("Warung Babi Guling"))))
        post("/polls/${food.str("id")}/decide", """{"optionId":"${food.options()[0].str("id")}"}""")

        val dest = createPoll(t, pollJson(kind = "DESTINATION", question = "Tujuan?", options = listOf(option("Lombok"))))
        post("/polls/${dest.str("id")}/decide", """{"optionId":"${dest.options()[0].str("id")}"}""")

        val items = get("/trips/${t.id}/itinerary").data().jsonArray.map { it.obj() }
        val tanahLot = items.single { it.str("title") == "Tanah Lot" }
        assertNull(tanahLot.strOrNull("dayDate"))
        assertEquals("gp1", tanahLot.str("placeId"))
        val dinner = items.single { it.str("title") == "Warung Babi Guling" }
        assertEquals("FOOD", dinner.str("category"))
        assertEquals("2026-11-21", dinner.str("dayDate"))
        assertEquals("19:00", dinner.str("startTime"))
        assertEquals("Lombok", get("/trips/${t.id}").data().obj().getValue("trip").obj().str("destination"))
    }

    @Test
    fun turningTheActionOffOnlyRecordsTheResult() = apiTest {
        val t = pollTrip()
        val dest = createPoll(t, pollJson(kind = "DESTINATION", question = "Tujuan?", extra = ""","applyWinner":false""", options = listOf(option("Lombok"))))
        post("/polls/${dest.str("id")}/decide", """{"optionId":"${dest.options()[0].str("id")}"}""")
        assertEquals("Bali", get("/trips/${t.id}").data().obj().getValue("trip").obj().str("destination"))
    }

    @Test
    fun dismissingAPollChangesNothing() = apiTest {
        val t = pollTrip()
        val dest = createPoll(t, pollJson(kind = "DESTINATION", question = "Tujuan?", options = listOf(option("Lombok"))))
        val decided = post("/polls/${dest.str("id")}/decide", """{}""")
        assertEquals("DECIDED", decided.data().obj().str("status"))
        assertNull(decided.data().obj().strOrNull("decidedOptionId"))
        assertEquals("Bali", get("/trips/${t.id}").data().obj().getValue("trip").obj().str("destination"))
    }

    @Test
    fun membersWhoHaveNotVotedGetAReminderADayBefore() = apiTest {
        val t = pollTrip()
        val soon = Instant.now().plusSeconds(2 * 3600).toString()
        val poll = createPoll(t, pollJson(closesAt = soon))
        use(t.a); voteFor(poll.str("id"), poll.vote(0))

        PollService.tick()
        PollService.tick() // a repeat sends nothing twice

        assertEquals(1, inboxOf(t.b).count { it.getValue("params").obj().strOrNull("stage") == "CLOSING" })
        assertEquals(0, inboxOf(t.a).count { it.getValue("params").obj().strOrNull("stage") == "CLOSING" })
    }

    @Test
    fun optionsCanBeCommentedOn() = apiTest {
        val t = pollTrip()
        val poll = createPoll(t)
        val optionId = poll.options()[0].str("id")
        use(t.b)
        val comment = """{"id":"${UUID.randomUUID()}","targetKind":"POLL_OPTION","targetId":"$optionId","body":"Aku alergi seafood"}"""
        assertEquals(HttpStatusCode.Created, post("/trips/${t.id}/comments", comment).status)
        assertEquals("1", get("/trips/${t.id}/comments/counts?targetKind=POLL_OPTION").data().jsonArray.single().obj().str("count"))
        val other = """{"id":"${UUID.randomUUID()}","targetKind":"POLL_OPTION","targetId":"${UUID.randomUUID()}","body":"?"}"""
        assertEquals(HttpStatusCode.NotFound, post("/trips/${t.id}/comments", other).status)
    }
}

