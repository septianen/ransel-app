package app.ransel.server.ai

import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.server.plugins.ApiException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * One search that can be cancelled (`RecommendationRequest.searchId`). It has one point of no return, [commit]: just before the
 * credits are charged and the voting round is made. The two moves race on one atomic phase, so exactly one wins:
 *  - [cancel] wins while the search is [Phase.RUNNING]: it never commits, so nothing is charged, no round is made, nobody is notified;
 *  - [commit] wins first: the charge and the round happen, and a later [cancel] answers false.
 * Credits are therefore charged at most once, and never for a search that was cancelled.
 */
class SearchHandle internal constructor(private val createdAt: Long, initial: Phase) {
    enum class Phase { RUNNING, COMMITTED, CANCELLED, DONE }

    private val phase = AtomicReference(initial)
    @Volatile private var job: Job? = null

    val cancelled: Boolean get() = phase.get() == Phase.CANCELLED
    internal val running: Boolean get() = phase.get() == Phase.RUNNING
    internal val ageMillis: Long get() = System.currentTimeMillis() - createdAt

    /** The work to stop when a cancel arrives; a cancel that came before it was attached stops it at once. */
    fun attach(work: Job) {
        job = work
        if (cancelled) work.cancel(CancellationException("search cancelled"))
    }

    /** True = the search may charge and make its round now; false = it was cancelled first. */
    fun commit(): Boolean = phase.compareAndSet(Phase.RUNNING, Phase.COMMITTED)

    /** True = the search is (now) cancelled; false = it is past the point of no return. */
    fun cancel(): Boolean {
        if (phase.compareAndSet(Phase.RUNNING, Phase.CANCELLED)) {
            job?.cancel(CancellationException("search cancelled"))
            return true
        }
        return cancelled
    }

    internal fun finish() {
        phase.compareAndSet(Phase.COMMITTED, Phase.DONE)
        phase.compareAndSet(Phase.RUNNING, Phase.DONE)
    }
}

/**
 * The searches the server knows by (user, searchId), in memory: the server runs as one instance and a search lasts seconds.
 * A cancel that arrives before its search (the app cancelled at once) leaves a cancelled handle behind, so the search that
 * comes later is refused. Finished and cancelled handles are forgotten after [KEEP_MILLIS].
 */
class SearchRegistry {
    private data class Key(val userId: UUID, val searchId: UUID)

    private val handles = ConcurrentHashMap<Key, SearchHandle>()

    /** A handle for a new search; 409 `SEARCH_CANCELLED` when this id was cancelled before it started. */
    fun start(userId: UUID, searchId: String): SearchHandle {
        sweep()
        val id = runCatching { UUID.fromString(searchId) }.getOrNull() ?: throw validationFailed(mapOf("searchId" to "INVALID_UUID"))
        val handle = SearchHandle(System.currentTimeMillis(), SearchHandle.Phase.RUNNING)
        val existing = handles.putIfAbsent(Key(userId, id), handle) ?: return handle
        if (existing.cancelled) throw cancelled()
        throw validationFailed(mapOf("searchId" to "IN_USE"))
    }

    /** True = stopped, or will be refused when it arrives; false = it was already past the point of no return. */
    fun cancel(userId: UUID, searchId: String): Boolean {
        sweep()
        val id = runCatching { UUID.fromString(searchId) }.getOrNull() ?: throw validationFailed(mapOf("searchId" to "INVALID_UUID"))
        val tombstone = SearchHandle(System.currentTimeMillis(), SearchHandle.Phase.CANCELLED)
        val existing = handles.putIfAbsent(Key(userId, id), tombstone) ?: return true
        return existing.cancel()
    }

    private fun sweep() {
        handles.entries.removeIf { (_, h) -> h.ageMillis > KEEP_MILLIS && !h.running }
    }

    companion object {
        const val KEEP_MILLIS = 10 * 60_000L

        fun cancelled() = ApiException(HttpStatusCode.Conflict, ErrorCode.SEARCH_CANCELLED, "The search was cancelled")
    }
}
