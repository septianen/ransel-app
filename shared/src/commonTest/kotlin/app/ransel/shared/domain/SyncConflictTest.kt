package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncConflictTest {

    @Test
    fun nextStampIsAtLeastNow() {
        assertEquals(1_000L, nextUpdatedAtMillis(null, 1_000L))
        assertEquals(5_000L, nextUpdatedAtMillis(1_000L, 5_000L))
    }

    @Test
    fun nextStampStepsPastTheStoredServerTimeWhenTheDeviceClockIsBehind() {
        // last pull wrote server updatedAt = 10_000; device now() is still 9_000
        assertEquals(10_001L, nextUpdatedAtMillis(10_000L, 9_000L))
        assertEquals(10_001L, nextUpdatedAtMillis(10_000L, 10_000L))
    }

    @Test
    fun missingLocalRowAlwaysTakesThePull() {
        assertTrue(shouldApplyPulledRow(null, null, 1L))
        assertTrue(shouldApplyPulledRow(null, SYNC_SYNCED, 1L))
    }

    @Test
    fun pendingLocalWriteIsNotReplacedEvenByANewerServerRow() {
        // another device may have written; we still push, and CONFLICT + REJECTED lets the pull in
        assertFalse(shouldApplyPulledRow(localUpdatedAtMillis = 5, localSyncState = SYNC_PENDING, remoteUpdatedAtMillis = 9))
        assertFalse(shouldApplyPulledRow(localUpdatedAtMillis = 9, localSyncState = SYNC_PENDING, remoteUpdatedAtMillis = 5))
    }

    @Test
    fun rejectedRowTakesTheServerCopy() {
        assertTrue(shouldApplyPulledRow(50, SYNC_REJECTED, 10))
        assertTrue(shouldApplyPulledRow(50, SYNC_REJECTED, 80))
    }

    @Test
    fun syncedRowKeepsANewerOrEqualLocalCopy() {
        // the bug: after push the row is SYNCED and a stale GET /sync must not roll it back
        assertFalse(shouldApplyPulledRow(20, SYNC_SYNCED, 10))
        assertFalse(shouldApplyPulledRow(20, SYNC_SYNCED, 20))
    }

    @Test
    fun syncedRowTakesAStrictlyNewerRemoteCopy() {
        // two devices: the other phone wrote later and we have nothing pending
        assertTrue(shouldApplyPulledRow(20, SYNC_SYNCED, 21))
    }

    @Test
    fun unknownSyncStateIsTreatedLikeSynced() {
        assertFalse(shouldApplyPulledRow(20, "WEIRD", 10))
        assertTrue(shouldApplyPulledRow(20, "WEIRD", 21))
    }
}
