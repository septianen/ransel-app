package app.ransel.feature.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class SyncTileStateTest {
    private val noon = Instant.parse("2026-11-19T12:11:00Z")
    private val utc = TimeZone.UTC

    @Test
    fun idleShowsClockAndSyncNow() {
        val snap = syncTileSnapshot(syncing = false, offline = false, lastSyncAt = noon, timeZone = utc)
        assertEquals(SyncTileKind.IDLE, snap.kind)
        assertEquals("12:11", snap.clock)
        assertTrue(snap.actionVisible)
        assertFalse(snap.actionIsError)
    }

    @Test
    fun idleWithoutASyncLeavesTheClockEmpty() {
        val snap = syncTileSnapshot(syncing = false, offline = false, lastSyncAt = null, timeZone = utc)
        assertEquals(SyncTileKind.IDLE, snap.kind)
        assertNull(snap.clock)
        assertTrue(snap.actionVisible)
        assertFalse(snap.actionIsError)
    }

    @Test
    fun offlineShowsRetryInErrorColour() {
        val snap = syncTileSnapshot(syncing = false, offline = true, lastSyncAt = noon, timeZone = utc)
        assertEquals(SyncTileKind.OFFLINE, snap.kind)
        assertEquals("12:11", snap.clock)
        assertTrue(snap.actionVisible)
        assertTrue(snap.actionIsError)
    }

    @Test
    fun syncingHidesTheActionAndWinsOverOffline() {
        val snap = syncTileSnapshot(syncing = true, offline = true, lastSyncAt = noon, timeZone = utc)
        assertEquals(SyncTileKind.SYNCING, snap.kind)
        assertFalse(snap.actionVisible)
        assertFalse(snap.actionIsError)
    }

    @Test
    fun clockPadsHoursAndMinutes() {
        val early = Instant.parse("2026-11-19T04:05:00Z")
        assertEquals("04:05", formatAccountClock(early, utc))
    }
}
