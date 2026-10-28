package app.ransel.data.sync

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the UI may show about synchronisation (mobile only; the web has no pending changes).
 * [pendingIds] are ids of rows (any table) whose latest local change has not been accepted yet.
 */
interface SyncStatus {
    val pendingCount: StateFlow<Int>
    val pendingIds: StateFlow<Set<String>>
    val lastSyncAt: StateFlow<Instant?>
    val offline: StateFlow<Boolean>
    val syncing: StateFlow<Boolean>

    /** Counts how many changes were sent when the connection came back; null when nothing to announce. */
    val justSynced: StateFlow<Int?>
    fun acknowledgeJustSynced()

    /** Starts a sync now in the background (no-op on the web). */
    fun syncNow()

    /** Pull-to-refresh: mobile syncs and returns when done; web fetches everything again. */
    suspend fun refresh()

    /** The app went to the background (false) or came back (true). Mobile stops the minute-by-minute sync meanwhile. */
    fun setInForeground(foreground: Boolean) = Unit
}

/** Web / tests: nothing is ever pending. */
object NoSyncStatus : SyncStatus {
    override val pendingCount: StateFlow<Int> = MutableStateFlow(0)
    override val pendingIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val lastSyncAt: StateFlow<Instant?> = MutableStateFlow(null)
    override val offline: StateFlow<Boolean> = MutableStateFlow(false)
    override val syncing: StateFlow<Boolean> = MutableStateFlow(false)
    override val justSynced: StateFlow<Int?> = MutableStateFlow(null)
    override fun acknowledgeJustSynced() = Unit
    override fun syncNow() = Unit
    override suspend fun refresh() = Unit
}
