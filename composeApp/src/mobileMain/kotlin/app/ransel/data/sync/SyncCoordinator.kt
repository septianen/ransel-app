package app.ransel.data.sync

import androidx.room3.withWriteTransaction
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.local.PreferenceEntity
import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.SyncState
import app.ransel.data.local.toDto
import app.ransel.data.local.toEntity
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushRequest
import app.ransel.shared.dto.SyncRejection
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val LAST_SYNC_KEY = "last_sync_at"
private const val MAX_PUSH_ROWS = 500
private const val PERIOD_SECONDS = 60L
private const val DEBOUNCE_MILLIS = 1_500L

/**
 * Offline-first sync (docs/TRD.md "Offline-first sync"): push every PENDING row, then pull what changed
 * on the server since `lastSyncAt`. Runs while a user is signed in: on sign-in/start, every minute,
 * shortly after a local write, and with backoff while the network is down. Last write wins per row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinator(
    private val db: RanselDatabase,
    private val api: RanselApi,
    private val session: SessionManager,
    scope: CoroutineScope,
) : SyncStatus {
    private val _pendingCount = MutableStateFlow(0)
    override val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    private val _pendingIds = MutableStateFlow<Set<String>>(emptySet())
    override val pendingIds: StateFlow<Set<String>> = _pendingIds.asStateFlow()
    private val _lastSyncAt = MutableStateFlow<Instant?>(null)
    override val lastSyncAt: StateFlow<Instant?> = _lastSyncAt.asStateFlow()
    private val _offline = MutableStateFlow(false)
    override val offline: StateFlow<Boolean> = _offline.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    override val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _justSynced = MutableStateFlow<Int?>(null)
    override val justSynced: StateFlow<Int?> = _justSynced.asStateFlow()

    private val kick = Channel<Unit>(Channel.CONFLATED)
    private val runLock = Mutex()
    private var changesWhileOffline = 0

    init {
        scope.launch {
            db.syncDao().observePendingIds().collect { ids ->
                _pendingIds.value = ids.toSet()
                _pendingCount.value = ids.size
                if (ids.isNotEmpty()) kick.trySend(Unit)
            }
        }
        scope.launch {
            _lastSyncAt.value = db.preferenceDao().get(LAST_SYNC_KEY)?.toLongOrNull()
                ?.let(Instant::fromEpochMilliseconds)
        }
        scope.launch {
            session.state.collectLatest { state -> if (state is SessionState.SignedIn) loop() }
        }
    }

    override fun acknowledgeJustSynced() {
        _justSynced.value = null
    }

    override fun syncNow() {
        kick.trySend(Unit)
    }

    override suspend fun refresh() {
        if (session.state.value is SessionState.SignedIn) syncOnce()
    }

    private suspend fun loop() {
        var failures = 0
        var first = true
        while (true) {
            if (!first) {
                val wait = if (failures > 0) min(15L shl (failures - 1).coerceAtMost(3), 120L) else PERIOD_SECONDS
                val kicked = withTimeoutOrNull(wait.seconds) { kick.receive() } != null
                if (kicked) delay(DEBOUNCE_MILLIS) // let a burst of edits finish
            }
            first = false
            failures = if (syncOnce() == Outcome.OFFLINE) failures + 1 else 0
        }
    }

    private enum class Outcome { OK, OFFLINE, FAILED }

    private suspend fun syncOnce(): Outcome = runLock.withLock {
        _syncing.value = true
        try {
            val pushed = push()
            if (pushed == null) return Outcome.OFFLINE
            if (!pull(full = pushed.needsFullPull)) return Outcome.OFFLINE
            if (_offline.value) {
                // The connection came back: tell the user what went out.
                _offline.value = false
                if (changesWhileOffline > 0) _justSynced.value = changesWhileOffline
                changesWhileOffline = 0
            }
            Outcome.OK
        } finally {
            _syncing.value = false
        }
    }

    private class PushResult(val needsFullPull: Boolean)

    /** Sends pending rows in batches. Null = offline (nothing more is attempted). */
    private suspend fun push(): PushResult? {
        val dao = db.syncDao()
        var needsFullPull = false
        while (true) {
            val trips = dao.pendingTrips()
            val items = dao.pendingItems()
            val packing = dao.pendingPacking()
            val expenses = dao.pendingExpenses()
            if (trips.isEmpty() && items.isEmpty() && packing.isEmpty() && expenses.isEmpty()) {
                return PushResult(needsFullPull)
            }
            // Trips first so a batch never has an item whose trip comes later; cap each batch.
            var budget = MAX_PUSH_ROWS
            fun <T> List<T>.take(): List<T> = take(budget).also { budget -= it.size }
            val sendTrips = trips.take()
            val sendItems = items.take()
            val sendPacking = packing.take()
            val sendExpenses = expenses.take()
            val request = SyncPushRequest(
                SyncChanges(
                    trips = sendTrips.map { it.toDto() },
                    itineraryItems = sendItems.map { it.toDto() },
                    packingItems = sendPacking.map { it.toDto() },
                    expenses = sendExpenses.map { it.toDto() },
                ),
            )
            val sent = sendTrips.size + sendItems.size + sendPacking.size + sendExpenses.size
            when (val result = api.syncPush(request)) {
                is ApiResult.Failure -> {
                    if (result.error.isConnection) {
                        _offline.value = true
                        changesWhileOffline = maxOf(changesWhileOffline, _pendingCount.value)
                        return null
                    }
                    // Not offline, but the server did not take the batch (5xx, expired session): try later.
                    return PushResult(needsFullPull)
                }
                is ApiResult.Success -> {
                    val response = result.value
                    val accepted = response.accepted.map { it.entity to it.id }.toSet()
                    val rejected = response.rejected.associateBy { it.entity to it.id }
                    db.withWriteTransaction {
                        sendTrips.forEach { settle(accepted, rejected, "trip", it.id) { s -> dao.setTripState(it.id, it.updatedAt, s) } }
                        sendItems.forEach { settle(accepted, rejected, "itineraryItem", it.id) { s -> dao.setItemState(it.id, it.updatedAt, s) } }
                        sendPacking.forEach { settle(accepted, rejected, "packingItem", it.id) { s -> dao.setPackingState(it.id, it.updatedAt, s) } }
                        sendExpenses.forEach { settle(accepted, rejected, "expense", it.id) { s -> dao.setExpenseState(it.id, it.updatedAt, s) } }
                    }
                    if (response.rejected.any { it.code == ErrorCode.CONFLICT }) needsFullPull = true
                    // A batch that settled nothing would loop forever: stop and retry on the next tick.
                    if (accepted.isEmpty() && rejected.isEmpty()) return PushResult(needsFullPull)
                    if (sent < MAX_PUSH_ROWS) {
                        // Possibly more pending rows arrived meanwhile; the loop re-reads and ends when none.
                    }
                }
            }
        }
    }

    private suspend fun settle(
        accepted: Set<Pair<String, String>>,
        rejected: Map<Pair<String, String>, SyncRejection>,
        entity: String,
        id: String,
        set: suspend (String) -> Unit,
    ) {
        val key = entity to id
        when {
            key in accepted -> set(SyncState.SYNCED)
            // Server copy is newer: take the server's version on the next (full) pull.
            rejected[key] != null -> set(SyncState.REJECTED)
            else -> Unit
        }
    }

    /** Pulls and applies changes. False = offline. */
    private suspend fun pull(full: Boolean): Boolean {
        val since = if (full) null else _lastSyncAt.value
        return when (val result = api.syncPull(since)) {
            is ApiResult.Failure -> {
                if (result.error.isConnection) {
                    _offline.value = true
                    false
                } else true
            }
            is ApiResult.Success -> {
                apply(result.value)
                true
            }
        }
    }

    /** Server rows win unless a newer local edit is still waiting to be sent (last write wins). */
    private suspend fun apply(response: SyncPullResponse) {
        val dao = db.syncDao()
        val c = response.changes
        db.withWriteTransaction {
            c.trips.forEach { dto ->
                val local = dao.trip(dto.id)
                if (local == null || !(local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt.toEpochMilliseconds())) {
                    dao.putTrip(dto.toEntity())
                }
            }
            c.itineraryItems.forEach { dto ->
                val local = dao.item(dto.id)
                if (local == null || !(local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt.toEpochMilliseconds())) {
                    dao.putItem(dto.toEntity())
                }
            }
            c.packingItems.forEach { dto ->
                val local = dao.packing(dto.id)
                if (local == null || !(local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt.toEpochMilliseconds())) {
                    dao.putPacking(dto.toEntity())
                }
            }
            c.expenses.forEach { dto ->
                val local = dao.expense(dto.id)
                if (local == null || !(local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt.toEpochMilliseconds())) {
                    dao.putExpense(dto.toEntity())
                }
            }
            db.preferenceDao().set(PreferenceEntity(LAST_SYNC_KEY, response.serverTime.toEpochMilliseconds().toString()))
        }
        _lastSyncAt.value = response.serverTime
    }

    /** Sign-out on this device: drop everything that belongs to the account. */
    internal suspend fun wipe() {
        val dao = db.syncDao()
        db.withWriteTransaction {
            dao.clearItems()
            dao.clearPacking()
            dao.clearExpenses()
            dao.clearTrips()
            db.preferenceDao().remove(LAST_SYNC_KEY)
        }
        _lastSyncAt.value = null
        _offline.value = false
        changesWhileOffline = 0
    }

    /** Best-effort last push before sign-out. */
    internal suspend fun flush() {
        runLock.withLock { push() }
    }
}
