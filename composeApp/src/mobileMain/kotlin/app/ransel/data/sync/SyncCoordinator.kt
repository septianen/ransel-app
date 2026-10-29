package app.ransel.data.sync

import androidx.room3.withWriteTransaction
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.CrashReporter
import app.ransel.data.analytics.NoAnalytics
import app.ransel.data.analytics.NoCrashReporter
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.local.PreferenceEntity
import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.SyncState
import app.ransel.data.local.toDto
import app.ransel.data.local.toEntity
import app.ransel.data.remote.ApiResult
import app.ransel.data.local.RejectedRow
import app.ransel.data.remote.RanselApi
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.SyncChanges
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

private const val LAST_SYNC_KEY = "last_sync_at"
/** A [DroppedChanges] notice not yet seen (survives the app being closed). */
private const val DROPPED_KEY = "dropped_changes"
/** Refusals that mean "you may not change this trip any more"; anything else (but CONFLICT) points to a bug. */
private val NO_ACCESS_CODES = setOf(ErrorCode.FORBIDDEN, ErrorCode.TRIP_NOT_FOUND)
private const val MAX_PUSH_ROWS = 500
private const val PULL_TRIPS_PER_PAGE = 20
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
    private val scope: CoroutineScope,
    private val analytics: Analytics = NoAnalytics,
    private val crashes: CrashReporter = NoCrashReporter,
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

    /** The loop only runs while the app is on screen: no pulls every minute in the background (battery, data). */
    private val foreground = MutableStateFlow(true)
    private val storedDropped = MutableStateFlow<DroppedChanges?>(null)
    /** Shown only while the app is on screen, so a notice from a background sync waits for the next visit. */
    override val dropped: StateFlow<DroppedChanges?> =
        combine(storedDropped, foreground) { notice, onScreen -> notice.takeIf { onScreen } }
            .stateIn(scope, SharingStarted.Eagerly, null)
    /** Row id → the server's refusal code, from this process's pushes (older REJECTED rows have none). */
    private val rejectionCodes = mutableMapOf<String, String>()

    private val kick = Channel<Unit>(Channel.CONFLATED)
    private val runLock = Mutex()
    private var changesWhileOffline = 0
    /**
     * Set by [wipe] until the next sign-in. The session is still SignedIn between the wipe and the end of sign-out,
     * so without it a queued sync would pull the whole account back onto a device that was just cleared.
     */
    private var wiped = false

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
            val saved = db.preferenceDao().get(DROPPED_KEY)
                ?.let { runCatching { Json.decodeFromString<DroppedChanges>(it) }.getOrNull() }
            if (saved != null) storedDropped.value = saved
        }
        scope.launch {
            // Coming back to the foreground starts the loop again, which syncs at once.
            combine(session.state, foreground) { state, onScreen -> state.takeIf { onScreen } }.collectLatest { state ->
                if (state is SessionState.SignedIn) {
                    runLock.withLock { wiped = false }
                    loop()
                }
            }
        }
    }

    override fun acknowledgeJustSynced() {
        _justSynced.value = null
    }

    override fun acknowledgeDropped() {
        storedDropped.value = null
        scope.launch { db.preferenceDao().remove(DROPPED_KEY) }
    }

    override fun syncNow() {
        kick.trySend(Unit)
    }

    override fun setInForeground(foreground: Boolean) {
        if (this.foreground.value == foreground) return
        // Leaving with unsent edits: send them once on the way out instead of waiting for the next visit.
        if (!foreground && _pendingCount.value > 0 && session.state.value is SessionState.SignedIn) {
            scope.launch { syncOnce() }
        }
        this.foreground.value = foreground
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
        if (wiped) return Outcome.OK
        _syncing.value = true
        try {
            val pushed = push()
            if (pushed == null) return Outcome.OFFLINE
            // Refused rows are only told apart after a full pull: until one completes they stay (also from older versions).
            val full = pushed.needsFullPull || db.syncDao().rejectedRows().isNotEmpty()
            when (pull(full)) {
                Pulled.OFFLINE -> return Outcome.OFFLINE
                Pulled.INCOMPLETE -> Unit
                Pulled.DONE -> if (full) dropRejected()
            }
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
            val personal = dao.pendingPersonal()
            if (trips.isEmpty() && items.isEmpty() && packing.isEmpty() && expenses.isEmpty() && personal.isEmpty()) {
                return PushResult(needsFullPull)
            }
            // Trips first so a batch never has an item whose trip comes later; cap each batch.
            var budget = MAX_PUSH_ROWS
            fun <T> List<T>.take(): List<T> = take(budget).also { budget -= it.size }
            val sendTrips = trips.take()
            val sendItems = items.take()
            val sendPacking = packing.take()
            val sendExpenses = expenses.take()
            val sendPersonal = personal.take()
            val request = SyncPushRequest(
                SyncChanges(
                    trips = sendTrips.map { it.toDto() },
                    itineraryItems = sendItems.map { it.toDto() },
                    packingItems = sendPacking.map { it.toDto() },
                    expenses = sendExpenses.map { it.toDto() },
                    personalItems = sendPersonal.map { it.toDto() },
                ),
            )
            val sent = sendTrips.size + sendItems.size + sendPacking.size + sendExpenses.size + sendPersonal.size
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
                    response.rejected.filter { it.code != ErrorCode.CONFLICT }.forEach { r ->
                        rejectionCodes[r.id] = r.code
                        analytics.event(AnalyticsEvent.SyncRejected(r.entity, r.code))
                        // the form checks the same shared rules, so any other refusal is a bug worth a report
                        if (r.code !in NO_ACCESS_CODES) crashes.record(IllegalStateException("sync rejected ${r.entity} ${r.code}"))
                    }
                    db.withWriteTransaction {
                        sendTrips.forEach { settle(accepted, rejected, "trip", it.id) { s -> dao.setTripState(it.id, it.updatedAt, s) } }
                        sendItems.forEach { settle(accepted, rejected, "itineraryItem", it.id) { s -> dao.setItemState(it.id, it.updatedAt, s) } }
                        sendPacking.forEach { settle(accepted, rejected, "packingItem", it.id) { s -> dao.setPackingState(it.id, it.updatedAt, s) } }
                        sendExpenses.forEach { settle(accepted, rejected, "expense", it.id) { s -> dao.setExpenseState(it.id, it.updatedAt, s) } }
                        sendPersonal.forEach { settle(accepted, rejected, "personalItem", it.id) { s -> dao.setPersonalState(it.id, it.updatedAt, s) } }
                    }
                    // Any rejection (not only CONFLICT) leaves the local copy different from the server's; a full
                    // pull puts the server's copy back. A row the server never had stays REJECTED.
                    if (response.rejected.isNotEmpty()) needsFullPull = true
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

    private enum class Pulled { DONE, INCOMPLETE, OFFLINE }

    /**
     * A full pull has put the server's copy back over every refused row the server has, so a row still REJECTED is one
     * it does not have (new while the user could no longer edit, or refused as invalid): it is deleted here and the
     * user told once (owner, 29 Oct 2026).
     */
    private suspend fun dropRejected() {
        val dao = db.syncDao()
        val rows = dao.rejectedRows()
        if (rows.isEmpty()) return
        val tripIds = rows.map { it.tripId }.toSet()
        val title = tripIds.singleOrNull()?.let { dao.trip(it)?.title }
        db.withWriteTransaction {
            dao.deleteRejectedItems()
            dao.deleteRejectedPacking()
            dao.deleteRejectedPersonal()
            dao.deleteRejectedExpenses()
            dao.deleteRejectedTrips()
        }
        val notice = DroppedChanges(
            counts = rows.groupingBy { it.kind() }.eachCount(),
            tripIds = tripIds,
            tripTitle = title,
            noAccess = rows.all { rejectionCodes[it.id] in NO_ACCESS_CODES },
        )
        rows.forEach { rejectionCodes.remove(it.id) }
        val merged = storedDropped.value?.plus(notice) ?: notice
        db.preferenceDao().set(PreferenceEntity(DROPPED_KEY, Json.encodeToString(merged)))
        storedDropped.value = merged
    }

    private fun RejectedRow.kind() = when (entity) {
        "expense" -> DroppedKind.EXPENSE
        "itineraryItem" -> DroppedKind.ACTIVITY
        "trip" -> DroppedKind.TRIP
        else -> DroppedKind.PACKING // packingItem, personalItem
    }

    /**
     * Pulls and applies changes, a few trips per page. The first page's server time becomes lastSyncAt only after
     * the last page, so a change made while paging is fetched again next time instead of skipped.
     */
    private suspend fun pull(full: Boolean): Pulled {
        val since = if (full) null else _lastSyncAt.value
        var cursor: Instant? = null
        var after: String? = null
        do {
            when (val result = api.syncPull(since, PULL_TRIPS_PER_PAGE, after)) {
                is ApiResult.Failure -> {
                    if (result.error.isConnection) {
                        _offline.value = true
                        return Pulled.OFFLINE
                    }
                    // A server error mid-way: keep the old cursor, the next tick starts over.
                    return Pulled.INCOMPLETE
                }
                is ApiResult.Success -> {
                    val page = result.value
                    if (cursor == null) cursor = page.serverTime
                    apply(page.changes)
                    after = page.nextTripId
                }
            }
        } while (after != null)
        cursor?.let { saveCursor(it) }
        return Pulled.DONE
    }

    private suspend fun saveCursor(serverTime: Instant) {
        db.preferenceDao().set(PreferenceEntity(LAST_SYNC_KEY, serverTime.toEpochMilliseconds().toString()))
        _lastSyncAt.value = serverTime
    }

    /** Server rows win unless a newer local edit is still waiting to be sent (last write wins). */
    private suspend fun apply(c: SyncChanges) {
        val dao = db.syncDao()
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
            c.personalItems.forEach { dto ->
                val local = dao.personal(dto.id)
                if (local == null || !(local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt.toEpochMilliseconds())) {
                    dao.putPersonal(dto.toEntity())
                }
            }
            c.expenses.forEach { dto ->
                val local = dao.expense(dto.id)
                if (local == null || !(local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt.toEpochMilliseconds())) {
                    dao.putExpense(dto.toEntity())
                }
            }
        }
    }

    /** Sign-out on this device: drop everything that belongs to the account. */
    internal suspend fun wipe() = runLock.withLock {
        // Under the lock, so a sync that is already running finishes first and none starts until the next sign-in.
        wiped = true
        val dao = db.syncDao()
        db.withWriteTransaction {
            dao.clearItems()
            dao.clearPacking()
            dao.clearPersonal()
            dao.clearExpenses()
            dao.clearTrips()
            db.preferenceDao().remove(LAST_SYNC_KEY)
            db.preferenceDao().remove(DROPPED_KEY)
        }
        _lastSyncAt.value = null
        storedDropped.value = null
        rejectionCodes.clear()
        _offline.value = false
        changesWhileOffline = 0
    }

    /** Best-effort last push before sign-out. */
    internal suspend fun flush() {
        runLock.withLock { push() }
    }
}
