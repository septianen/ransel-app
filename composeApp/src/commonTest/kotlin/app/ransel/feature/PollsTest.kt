package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.polls.CreatePollEvent
import app.ransel.feature.polls.CreatePollViewModel
import app.ransel.feature.polls.OptionDraft
import app.ransel.feature.polls.PollDialog
import app.ransel.feature.polls.PollEvent
import app.ransel.feature.polls.PollNeed
import app.ransel.feature.polls.PollNotice
import app.ransel.feature.polls.PollSearchError
import app.ransel.feature.polls.PollViewModel
import app.ransel.feature.polls.forDisplay
import app.ransel.feature.polls.leaders
import app.ransel.feature.polls.needFor
import app.ransel.feature.polls.toInput
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.dto.PollOptionInput
import kotlinx.datetime.plus
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollPriceUnit
import app.ransel.shared.model.PollStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TRIP = "t1"
private const val POLL = "p1"
private const val ME = "u1"
private val ME_MEMBER = memberIdFor(TRIP, ME)
private val json = headersOf(HttpHeaders.ContentType, "application/json")
private val stamp = Instant.parse("2026-10-26T10:00:00Z")

private val trip = TripDto(
    id = TRIP, ownerId = ME, title = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
    createdAt = stamp, updatedAt = stamp,
)

private fun option(id: String, label: String, votes: Int = 0, version: Int = 1) =
    PollOptionDto(id = id, label = label, version = version, voteCount = votes)

private fun poll(
    status: PollStatus = PollStatus.OPEN,
    creator: String = "m-other",
    myVotes: List<String> = emptyList(),
    multi: Boolean = false,
    options: List<PollOptionDto> = listOf(option("a", "Sate"), option("b", "Bakso")),
) = PollDto(
    id = POLL, tripId = TRIP, kind = PollKind.CUSTOM, question = "Makan di mana?", createdByMemberId = creator, createdByName = "Dinda",
    createdAt = stamp, closesAt = stamp, multiChoice = multi, status = status, options = options, myVotes = myVotes, memberCount = 3,
)

private fun pollJson(poll: PollDto): String {
    val options = poll.options.joinToString(",") { """{"id":"${it.id}","label":"${it.label}","version":${it.version},"voteCount":${it.voteCount}}""" }
    val mine = poll.myVotes.joinToString(",") { "\"$it\"" }
    return """{"data":{"id":"${poll.id}","tripId":"${poll.tripId}","kind":"${poll.kind}","question":"${poll.question}",
        "createdByMemberId":"${poll.createdByMemberId}","createdByName":"${poll.createdByName}","createdAt":"$stamp","closesAt":"$stamp",
        "multiChoice":${poll.multiChoice},"status":"${poll.status}","options":[$options],"myVotes":[$mine],"memberCount":3}}"""
}

private suspend fun signedIn(): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    val manager = SessionManager(store) { error("not used") }
    manager.start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")))
    return manager
}

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

class PollUiTest {
    @Test
    fun aMemberIsAskedToVoteAndTheManagerToDecide() {
        assertEquals(PollNeed.VOTE, poll().needFor("m-me", isOwner = false))
        assertEquals(null, poll(myVotes = listOf("a")).needFor("m-me", isOwner = false))
        assertEquals(PollNeed.DECIDE, poll(status = PollStatus.CLOSED, creator = "m-me").needFor("m-me", isOwner = false))
        assertEquals(PollNeed.DECIDE, poll(status = PollStatus.TIED).needFor("m-me", isOwner = true)) // the owner can always decide
        assertEquals(null, poll(status = PollStatus.CLOSED).needFor("m-me", isOwner = false))
        assertEquals(null, poll(status = PollStatus.DECIDED, creator = "m-me").needFor("m-me", isOwner = true))
    }

    @Test
    fun openPollsComeFirstThenNewest() {
        val old = poll().copy(id = "old", createdAt = stamp)
        val newer = poll().copy(id = "newer", createdAt = Instant.parse("2026-10-27T10:00:00Z"), status = PollStatus.DECIDED)
        val open = poll().copy(id = "open", createdAt = Instant.parse("2026-10-25T10:00:00Z"))
        assertEquals(listOf("old", "open", "newer"), listOf(newer, open, old).forDisplay().map { it.id })
    }

    @Test
    fun leadersAreTheOptionsWithTheMostVotes() {
        assertEquals(emptyList(), poll().leaders())
        assertEquals(listOf("b"), poll(options = listOf(option("a", "A", 1), option("b", "B", 2))).leaders().map { it.id })
        assertEquals(listOf("a", "b"), poll(options = listOf(option("a", "A", 2), option("b", "B", 2))).leaders().map { it.id })
    }

    @Test
    fun anOptionDraftBecomesTheInputOfItsKind() {
        val (stay, stayProblems) = OptionDraft(label = " Villa Ubud ", price = "750000", url = "https://x.example/v").toInput(PollKind.STAY, "IDR", "o1")
        assertEquals(emptySet(), stayProblems)
        assertEquals(PollOptionInput("o1", "Villa Ubud", url = "https://x.example/v", priceMinor = 750_000, priceUnit = PollPriceUnit.NIGHT), stay)

        val (food, _) = OptionDraft(label = "Warung", price = "").toInput(PollKind.FOOD, "IDR", "o2")
        assertEquals(null, food.priceMinor)
        assertEquals(null, food.priceUnit)

        val (budget, budgetProblems) = OptionDraft(min = "500000", max = "1000000").toInput(PollKind.BUDGET, "IDR", "o3")
        assertEquals(emptySet(), budgetProblems)
        assertEquals(500_000L to 1_000_000L, budget.minMinor to budget.maxMinor)

        val (_, problems) = OptionDraft(min = "9", max = "1").toInput(PollKind.BUDGET, "IDR", "o4")
        assertTrue("maxMinor" in problems)
    }

    @Test
    fun aLinkAndItsPictureTravelWithStayFoodAndPlaceOptionsOnly() {
        val draft = OptionDraft(label = "Warung", url = "https://x.example/w", imageUrl = "https://img.example/w.jpg")
        for (kind in listOf(PollKind.STAY, PollKind.FOOD, PollKind.PLACE)) {
            val (input, problems) = draft.toInput(kind, "IDR", "o")
            assertEquals(emptySet(), problems)
            assertEquals("https://x.example/w" to "https://img.example/w.jpg", input.url to input.imageUrl)
        }
        val (other, _) = draft.toInput(PollKind.CUSTOM, "IDR", "o")
        assertEquals(null, other.url)
    }

    @Test
    fun missingOrBrokenFieldsAreReportedByName() {
        assertTrue("label" in OptionDraft().toInput(PollKind.PLACE, "IDR", "o").second)
        assertEquals(setOf("startDate", "endDate"), OptionDraft().toInput(PollKind.DATE, "IDR", "o").second)
        assertTrue("priceMinor" in OptionDraft(label = "Villa", price = "abc").toInput(PollKind.STAY, "IDR", "o").second)
        assertTrue("url" in OptionDraft(label = "Villa", url = "ftp://x").toInput(PollKind.STAY, "IDR", "o").second)
        val dated = OptionDraft(start = LocalDate(2026, 12, 1), end = LocalDate(2026, 12, 4)).toInput(PollKind.DATE, "IDR", "o")
        assertEquals(emptySet(), dated.second)
        assertEquals(LocalDate(2026, 12, 4), dated.first.endDate)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PollViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private var current = poll()
    private var voteStatus = HttpStatusCode.OK
    private var searchError: String? = null

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val body = request.body.toByteArray().decodeToString()
                log.update { it + (request.method.value + " " + path to body) }
                when {
                    path.endsWith("/members") -> respond(
                        """{"data":[{"tripId":"$TRIP","memberId":"$ME_MEMBER","userId":"$ME","displayName":"Ayu","role":"OWNER","joinedAt":"$stamp"}]}""",
                        HttpStatusCode.OK, json,
                    )
                    request.method == HttpMethod.Put && path.endsWith("/votes") ->
                        if (voteStatus == HttpStatusCode.OK) {
                            val chosen = Regex("\"optionId\":\"([a-z]+)\"").findAll(body).map { it.groupValues[1] }.toList()
                            respond(pollJson(current.copy(myVotes = chosen)), HttpStatusCode.OK, json)
                        } else respond("""{"error":{"code":"VOTE_STALE","message":"x"}}""", voteStatus, json)
                    request.method == HttpMethod.Delete && path.endsWith("/polls/$POLL") -> respond("", HttpStatusCode.NoContent)
                    path.endsWith("/search") ->
                        if (searchError != null) respond("""{"error":{"code":"$searchError","message":"x"}}""", HttpStatusCode.PaymentRequired, json)
                        else respond(
                            """{"data":{"results":[{"placeId":"p-1","name":"Villa Pas","reason":"Dekat pantai","priceMinor":900000,"priceUnit":"NIGHT"},
                                {"placeId":"p-2","name":"Hotel Dua","reason":"Ada kolam"}],
                                "credits":{"balance":30,"dailyFreeLeft":0,"dailyFreeLimit":3,"resetsAt":"$stamp","costs":{"search":3,"nextBatch":1,"placeDetail":0}}}}""",
                            HttpStatusCode.OK, json,
                        )
                    path.endsWith("/options/bulk") ->
                        respond(pollJson(current.copy(options = current.options + option("c", "Villa Pas"))), HttpStatusCode.Created, json)
                    path.endsWith("/link-preview") ->
                        respond("""{"data":{"url":"https://x.example/v","title":"Villa Sunset","imageUrl":"https://img.example/v.jpg"}}""", HttpStatusCode.OK, json)
                    path.endsWith("/decide") -> respond(pollJson(current.copy(status = PollStatus.DECIDED)), HttpStatusCode.OK, json)
                    path.endsWith("/close") -> respond(pollJson(current.copy(status = PollStatus.CLOSED)), HttpStatusCode.OK, json)
                    path.endsWith("/polls/$POLL") -> respond(pollJson(current), HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun vm(): PollViewModel {
        val store = InMemoryStore().also { it.trips.value = listOf(trip) }
        return PollViewModel(TRIP, POLL, api(), signedIn(), InMemoryTripRepository(store))
    }

    private val requests get() = log.value

    @Test
    fun loadsThePollWithMembersAndKnowsTheRole() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null && vm.state.value.members.isNotEmpty() }
        assertEquals("Makan di mana?", vm.state.value.poll!!.question)
        assertTrue(vm.state.value.isOwner)
        assertTrue(vm.state.value.isManager)
        assertEquals("IDR", vm.state.value.currency)
    }

    @Test
    fun oneTapVotesAndAnotherTapMovesTheVote() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.toggle(vm.state.value.poll!!.options[0])
        eventually { vm.state.value.poll!!.myVotes == listOf("a") && !vm.state.value.busy }
        val first = requests.last { it.first.startsWith("PUT") }.second
        assertTrue("\"optionId\":\"a\"" in first && "\"version\":1" in first, first)

        vm.toggle(vm.state.value.poll!!.options[1])
        eventually { vm.state.value.poll!!.myVotes == listOf("b") && !vm.state.value.busy }
        // tapping the chosen option again takes the vote back
        vm.toggle(vm.state.value.poll!!.options[1])
        eventually { requests.count { it.first.startsWith("PUT") } == 3 && !vm.state.value.busy }
        assertTrue("\"votes\":[]" in requests.last { it.first.startsWith("PUT") }.second)
    }

    @Test
    fun severalChoicesAddUp() = runTest {
        current = poll(multi = true)
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.toggle(vm.state.value.poll!!.options[0])
        eventually { !vm.state.value.busy && vm.state.value.poll!!.myVotes == listOf("a") }
        vm.toggle(vm.state.value.poll!!.options[1])
        eventually { requests.count { it.first.startsWith("PUT") } == 2 && !vm.state.value.busy }
        val body = requests.last { it.first.startsWith("PUT") }.second
        assertTrue("\"optionId\":\"a\"" in body && "\"optionId\":\"b\"" in body, body)
    }

    @Test
    fun aStaleVoteTellsTheUserAndLoadsThePollAgain() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null }
        voteStatus = HttpStatusCode.Conflict
        val loads = requests.count { it.first == "GET /api/v1/polls/$POLL" }
        vm.toggle(vm.state.value.poll!!.options[0])
        val event = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } }
        assertEquals(PollEvent.Notice(PollNotice.STALE), event)
        eventually { requests.count { it.first == "GET /api/v1/polls/$POLL" } > loads }
        assertEquals(emptyList(), vm.state.value.poll!!.myVotes) // the optimistic choice is undone
    }

    @Test
    fun decidingNeedsAConfirmationAndSendsTheOption() = runTest {
        current = poll(status = PollStatus.CLOSED)
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.ask(PollDialog.Decide(vm.state.value.poll!!.options[1]))
        assertNotNull(vm.state.value.dialog)
        vm.confirmDialog()
        eventually { vm.state.value.poll!!.status == PollStatus.DECIDED }
        assertTrue("\"optionId\":\"b\"" in requests.last { it.first.endsWith("/decide") }.second)
        assertEquals(null, vm.state.value.dialog)
    }

    @Test
    fun closingWithoutChoosingSendsNoOption() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.ask(PollDialog.Decide(null))
        vm.confirmDialog()
        eventually { requests.any { it.first.endsWith("/decide") } }
        assertTrue("optionId" !in requests.last { it.first.endsWith("/decide") }.second)
    }

    @Test
    fun searchRunsOnTheFormAndTheTickedResultsBecomeOptions() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.openSearch()
        vm.onSearchArea(" Ubud ")
        vm.onSearchMin("500000")
        vm.onSearchMax("1200000")
        vm.onSearchNeeds("kolam renang")
        vm.runSearch()
        eventually { vm.state.value.search?.results != null }
        val sent = requests.last { it.first.endsWith("/search") }.second
        assertTrue("\"area\":\"Ubud\"" in sent && "\"minMinor\":500000" in sent && "\"maxMinor\":1200000" in sent, sent)

        vm.togglePicked("p-1")
        assertEquals(setOf("p-1"), vm.state.value.search!!.picked)
        vm.addPicked()
        eventually { vm.state.value.search == null }
        val bulk = requests.last { it.first.endsWith("/options/bulk") }.second
        assertTrue("\"label\":\"Villa Pas\"" in bulk && "\"placeId\":\"p-1\"" in bulk && "\"priceUnit\":\"NIGHT\"" in bulk, bulk)
        assertEquals(3, vm.state.value.poll!!.options.size)
    }

    @Test
    fun searchNeedsCreditsAndGoodNumbers() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.openSearch()
        vm.onSearchMin("abc")
        vm.runSearch()
        assertTrue(vm.state.value.search!!.badInput)
        assertTrue(requests.none { it.first.endsWith("/search") })

        vm.onSearchMin("")
        searchError = "INSUFFICIENT_CREDITS"
        vm.runSearch()
        eventually { vm.state.value.search?.error != null }
        assertEquals(PollSearchError.NO_CREDITS, vm.state.value.search!!.error)
        assertEquals(null, vm.state.value.search!!.results)
    }

    @Test
    fun aPastedLinkGivesTitleAndPicture() = runTest {
        val vm = vm()
        val page = vm.previewLink("https://x.example/v")
        assertEquals("Villa Sunset", page?.title)
        assertEquals("https://img.example/v.jpg", page?.imageUrl)
    }

    @Test
    fun deletingLeavesTheScreen() = runTest {
        val vm = vm()
        eventually { vm.state.value.poll != null }
        vm.ask(PollDialog.Delete)
        vm.confirmDialog()
        val event = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } }
        assertEquals(PollEvent.Deleted, event)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePollViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private fun vm(kind: PollKind): CreatePollViewModel {
        val api = RanselApi(
            createApiHttpClient(
                MockEngine { request ->
                    log.update { it + (request.method.value + " " + request.url.encodedPath to request.body.toByteArray().decodeToString()) }
                    respond(pollJson(poll()), HttpStatusCode.Created, json)
                },
            ),
            origin = "http://api.test",
        )
        val store = InMemoryStore().also { it.trips.value = listOf(trip) }
        return CreatePollViewModel(TRIP, kind, api, InMemoryTripRepository(store))
    }

    @Test
    fun theTemplateSetsTheDefaults() = runTest {
        val date = vm(PollKind.DATE)
        assertTrue(date.state.value.multiChoice)
        assertEquals(false, date.state.value.anonymous)
        val budget = vm(PollKind.BUDGET)
        assertTrue(budget.state.value.anonymous)
        budget.onAnonymous(false)
        assertTrue(budget.state.value.anonymous) // locked
        assertEquals(false, vm(PollKind.CUSTOM).state.value.multiChoice)
        eventually { date.state.value.tripStart != null }
        assertEquals("IDR", date.state.value.currency)
    }

    @Test
    fun aQuestionIsRequired() = runTest {
        val vm = vm(PollKind.CUSTOM)
        vm.onQuestion("   ")
        vm.create()
        assertTrue(vm.state.value.questionWrong)
        assertTrue(log.value.isEmpty())
        vm.onQuestion("Naik apa?")
        assertEquals(false, vm.state.value.questionWrong)
    }

    @Test
    fun aDatePollNeedsTwoToFiveRanges() = runTest {
        val vm = vm(PollKind.DATE)
        vm.onQuestion("Kapan berangkat?")
        val start = app.ransel.util.today().plus(kotlinx.datetime.DatePeriod(days = 7))
        fun range(i: Int) = PollOptionInput("d$i", "", startDate = start.plus(kotlinx.datetime.DatePeriod(days = i)), endDate = start.plus(kotlinx.datetime.DatePeriod(days = i)))
        vm.saveOption(range(1))
        vm.create()
        assertTrue(vm.state.value.optionsWrong)
        assertTrue(log.value.isEmpty())
        vm.saveOption(range(2))
        assertEquals(false, vm.state.value.optionsWrong)
        (3..6).forEach { vm.saveOption(range(it)) }
        assertEquals(5, vm.state.value.options.size) // the sixth is not taken
        assertEquals(false, vm.state.value.canAddOption)
        vm.create()
        eventually { log.value.isNotEmpty() }
    }

    @Test
    fun theQuestionStartsEmptyAndTheTemplateIsOnlyAHint() = runTest {
        val vm = vm(PollKind.STAY)
        assertEquals("", vm.state.value.question)
        vm.create()
        assertTrue(vm.state.value.questionWrong, "the hint is not sent as the question")
    }

    @Test
    fun createSendsTheFormAndOpensThePoll() = runTest {
        val vm = vm(PollKind.BUDGET)
        vm.onQuestion("Budget per orang?")
        vm.onClosingDays(3)
        vm.saveOption(PollOptionInput("o1", "", minMinor = 500_000, maxMinor = 1_000_000))
        vm.create()
        val event = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.eventFlow.first() } }
        assertEquals(CreatePollEvent.Created(POLL), event)
        val body = log.value.single { it.first == "POST /api/v1/trips/$TRIP/polls" }.second
        assertTrue("\"kind\":\"BUDGET\"" in body, body)
        assertTrue("\"anonymous\":true" in body, body)
        assertTrue("\"minMinor\":500000" in body, body)
    }

    @Test
    fun optionsCanBeAddedChangedAndRemovedBeforeCreating() = runTest {
        val vm = vm(PollKind.CUSTOM)
        vm.saveOption(PollOptionInput("o1", "Sate"))
        vm.saveOption(PollOptionInput("o2", "Bakso"))
        vm.saveOption(PollOptionInput("o1", "Sate Padang"))
        assertEquals(listOf("Sate Padang", "Bakso"), vm.state.value.options.map { it.label })
        vm.removeOption("o2")
        assertEquals(listOf("o1"), vm.state.value.options.map { it.id })
    }
}
