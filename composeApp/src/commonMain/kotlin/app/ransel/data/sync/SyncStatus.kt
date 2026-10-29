package app.ransel.data.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable
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

    /** Changes the server refused and that were deleted from this device; null when nothing to announce. */
    val dropped: StateFlow<DroppedChanges?> get() = NoDropped
    fun acknowledgeDropped() = Unit

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

private val NoDropped: StateFlow<DroppedChanges?> = MutableStateFlow(null)

/** What a dropped row was, as the snackbar names it. */
enum class DroppedKind { EXPENSE, ACTIVITY, PACKING, TRIP }

/**
 * Rows the server refused that are gone from this device, for one snackbar (owner, 29 Oct 2026). [tripTitle] is set
 * when they all belong to one trip whose title is known. [noAccess] = every refusal was FORBIDDEN or TRIP_NOT_FOUND
 * ("kamu tidak punya akses edit lagi"); otherwise the text only says the server refused.
 */
@Serializable
data class DroppedChanges(
    val counts: Map<DroppedKind, Int>,
    val tripIds: Set<String>,
    val tripTitle: String? = null,
    val noAccess: Boolean = true,
) {
    /** One notice for both: a second batch before the first was seen. */
    operator fun plus(other: DroppedChanges): DroppedChanges {
        val trips = tripIds + other.tripIds
        return DroppedChanges(
            counts = (counts.keys + other.counts.keys).associateWith { (counts[it] ?: 0) + (other.counts[it] ?: 0) },
            tripIds = trips,
            tripTitle = if (trips.size == 1) tripTitle ?: other.tripTitle else null,
            noAccess = noAccess && other.noAccess,
        )
    }
}
