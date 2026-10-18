package app.ransel.data

import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.ApiJson
import app.ransel.shared.model.Interest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExploreSessionStoreTest {
    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun session(tripId: String, destination: String = "Bali") = ExploreSession(
        ExploreForm(
            tripId = tripId, tripTitle = "Trip $tripId", destination = destination,
            startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23),
            interests = listOf(Interest.FOOD),
        ),
    )

    @Test
    fun everyTripKeepsItsOwnUnfinishedSession() = runTest {
        val prefs = InMemoryPreferencesRepository()
        val store = ExploreSessionStore(prefs)
        store.save(session("a", "Bali"))
        store.save(session("b", "Tokyo"))

        assertEquals(setOf("a", "b"), store.sessions.value.keys)
        assertEquals("Bali", store.session("a")?.form?.destination)
        assertEquals("Tokyo", store.session("b")?.form?.destination)

        store.clear("a")
        assertNull(store.session("a"))
        assertNotNull(store.session("b"), "clearing one trip must not touch the others")

        // what is left survives a restart
        val restarted = ExploreSessionStore(prefs)
        eventually { restarted.sessions.value.isNotEmpty() }
        assertEquals(setOf("b"), restarted.sessions.value.keys)
    }

    @Test
    fun savingAgainReplacesTheSessionOfThatTripOnly() = runTest {
        val store = ExploreSessionStore(InMemoryPreferencesRepository())
        store.save(session("a", "Bali"))
        store.save(session("b", "Tokyo"))
        store.save(session("a", "Lombok"))

        assertEquals(2, store.sessions.value.size)
        assertEquals("Lombok", store.session("a")?.form?.destination)
    }

    @Test
    fun theSingleSessionFromBeforeIsCarriedOverOnce() = runTest {
        val prefs = InMemoryPreferencesRepository()
        prefs.putString("explore_session", ApiJson.encodeToString(ExploreSession.serializer(), session("old", "Bali")))

        val store = ExploreSessionStore(prefs)
        eventually { store.sessions.value.isNotEmpty() }

        assertEquals(setOf("old"), store.sessions.value.keys)
        assertNull(prefs.getString("explore_session"), "the old key is removed after the move")
    }

    @Test
    fun clearingRemembersTheFormForTheNextFormOnce() = runTest {
        val store = ExploreSessionStore(InMemoryPreferencesRepository())
        store.save(session("a"))
        store.clear("a")

        assertEquals("a", store.takeLastForm("a")?.tripId)
        assertTrue(store.takeLastForm("a") == null, "taken once")
    }
}
