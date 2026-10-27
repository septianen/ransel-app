package app.ransel.feature

import androidx.lifecycle.viewModelScope
import app.ransel.data.fake.InMemoryPackingRepository
import app.ransel.data.fake.InMemoryPersonalItemRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.packing.PackingTemplatesViewModel
import app.ransel.feature.packing.ready
import app.ransel.shared.dto.MemberListProgressDto
import app.ransel.shared.model.PackingCategory
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Templates on the Pribadi list, and what "siap" means for a member (owner, 27 Oct 2026). */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalTemplatesTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine {
                respond(
                    """{"data":[{"id":"tpl","name":"Gunung","items":[{"category":"CLOTHING","name":"Jaket","quantity":1},{"category":"OTHER","name":"Senter","quantity":2}],"createdAt":"2026-10-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"}]}""",
                    HttpStatusCode.OK, json,
                )
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun aTemplateFillsMyOwnListNotTheSharedOne() = runTest {
        val store = InMemoryStore()
        val personal = InMemoryPersonalItemRepository(store) { "m" }
        val shared = InMemoryPackingRepository(store)
        personal.add("t1", app.ransel.shared.dto.CreatePersonalItemRequest("p0", PackingCategory.CLOTHING, "Jaket", 1, 1.0))
        val vm = PackingTemplatesViewModel("t1", api(), shared, personal)
        vm.load()
        eventually { vm.state.value.templates.isNotEmpty() }
        vm.select("tpl")
        vm.setSkipExisting(true)
        vm.apply()
        eventually { personal.observe("t1").first().size == 2 }
        assertEquals(setOf("Jaket", "Senter"), personal.observe("t1").first().map { it.name }.toSet(), "the jacket I have is not added twice")
        assertTrue(shared.observe("t1").first().isEmpty(), "Bersama stays as it was")
        vm.viewModelScope.cancel()
    }

    @Test
    fun readyMeansEveryRequiredLineOrEveryLine() {
        assertTrue(MemberListProgressDto("m", requiredDone = 2, requiredTotal = 2, done = 3, total = 9).ready)
        assertFalse(MemberListProgressDto("m", requiredDone = 1, requiredTotal = 2, done = 9, total = 9).ready)
        assertTrue(MemberListProgressDto("m", requiredDone = 0, requiredTotal = 0, done = 4, total = 4).ready, "no required lines: all ticked")
        assertFalse(MemberListProgressDto("m", requiredDone = 0, requiredTotal = 0, done = 3, total = 4).ready)
    }
}
