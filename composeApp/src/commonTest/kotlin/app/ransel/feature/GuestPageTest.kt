package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.guest.GuestNotice
import app.ransel.feature.guest.GuestPageViewModel
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

private suspend fun session(signedIn: Boolean): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    return SessionManager(store) { error("not used") }.also {
        if (signedIn) it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu")))
    }
}

private fun pageJson(code: String, me: Boolean) = """{"data":{"code":"$code","tripId":"t1","tripTitle":"Bali 4 hari","destination":"Bali",
    "startDate":"2026-11-20","endDate":"2026-11-23","ownerName":"Septian",
    "members":[{"memberId":"s","name":"Septian","hasAccount":true},{"memberId":"b","name":"Bima","hasAccount":false}],
    ${if (me) "\"me\":{\"memberId\":\"b\",\"name\":\"Bima\",\"hasAccount\":false}," else ""}
    "focusPollId":${if (code == "POLL01") "\"p1\"" else "null"},
    "polls":[{"id":"p1","tripId":"t1","kind":"CUSTOM","question":"Makan di mana?","createdByMemberId":"s","createdByName":"Septian",
      "createdAt":"2026-10-26T00:00:00Z","closesAt":"2099-01-01T00:00:00Z","status":"OPEN","memberCount":2,
      "options":[{"id":"o1","label":"Sate","version":3,"voteCount":0},{"id":"o2","label":"Bakso","version":1,"voteCount":0}]}]}}"""

@OptIn(ExperimentalCoroutinesApi::class)
class GuestPageTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** method, path, guest token header, body */
    private val requests = MutableStateFlow<List<List<String>>>(emptyList())
    private var nameExists = false

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val token = request.headers["X-Guest-Token"].orEmpty()
                requests.update { it + listOf(listOf(request.method.value, path, token, request.body.toByteArray().decodeToString())) }
                val code = path.substringAfter("/guest/").substringBefore('/')
                when {
                    path.endsWith("/identity") -> if (nameExists) {
                        respond("""{"error":{"code":"GUEST_NAME_EXISTS","message":"x","details":{"memberId":"b"}}}""", HttpStatusCode.Conflict, json)
                    } else {
                        respond("""{"data":{"token":"tok-1","me":{"memberId":"b","name":"Bima","hasAccount":false}}}""", HttpStatusCode.OK, json)
                    }
                    path.endsWith("/votes") -> respond(
                        """{"data":{"id":"p1","tripId":"t1","kind":"CUSTOM","question":"Makan di mana?","createdByMemberId":"s","createdByName":"Septian","createdAt":"2026-10-26T00:00:00Z","closesAt":"2099-01-01T00:00:00Z","status":"OPEN","myVotes":["o1"],"options":[]}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.startsWith("/api/v1/guest/") -> respond(pageJson(code, me = token == "tok-1"), HttpStatusCode.OK, json)
                    path == "/api/v1/polls/p1" -> respond("""{"error":{"code":"NOT_FOUND","message":"x"}}""", HttpStatusCode.NotFound, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun pickingANameKeepsTheTokenForTheTripAndVotesWithIt() = runTest {
        val prefs = InMemoryPreferencesRepository()
        val vm = GuestPageViewModel("POLL01", api(), prefs, session(false))
        eventually { vm.state.value.page != null }
        assertTrue(vm.state.value.choosingName)
        assertEquals(listOf("Bima"), vm.state.value.pickable.map { it.name })

        vm.pick("b")
        eventually { vm.state.value.me != null }
        assertEquals("tok-1", prefs.getString(GuestPageViewModel.tokenKey("t1")))

        vm.toggle(vm.state.value.page!!.polls.single(), "o1")
        vm.send(vm.state.value.page!!.polls.single())
        eventually { vm.state.value.notice == GuestNotice.SENT }
        val vote = requests.value.last { it[1].endsWith("/votes") }
        assertEquals("tok-1", vote[2])
        assertTrue("\"optionId\":\"o1\"" in vote[3] && "\"version\":3" in vote[3], vote[3])

        // the invite link of the same trip is the same person
        val other = GuestPageViewModel("BALI-7K2Q", api(), prefs, session(false))
        eventually { other.state.value.me != null }
        assertEquals("Bima", other.state.value.me?.name)
    }

    @Test
    fun aWrittenNameThatExistsIsOffered() = runTest {
        nameExists = true
        val vm = GuestPageViewModel("POLL01", api(), InMemoryPreferencesRepository(), session(false))
        eventually { vm.state.value.page != null }
        vm.startWriting()
        vm.submitName()
        assertEquals("REQUIRED", vm.state.value.nameError)
        vm.onName(" bima ")
        vm.submitName()
        eventually { vm.state.value.sameName != null }
        assertEquals("b", vm.state.value.sameName?.memberId)
        assertNull(vm.state.value.me)
    }
}
