package app.ransel.data

import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.inspiration.InspirationSessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InspirationSessionStoreTest {
    @Test fun setRestoreAndClearSessionId() = runTest {
        val preferences = InMemoryPreferencesRepository()
        val first = InspirationSessionStore(preferences)
        first.set("session-1")
        assertEquals("session-1", first.sessionId.value)
        val restored = InspirationSessionStore(preferences)
        assertEquals("session-1", restored.restore())
        restored.set(null)
        assertNull(InspirationSessionStore(preferences).restore())
    }
}
