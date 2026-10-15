package app.ransel.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update

/**
 * "Something changed on the server": every observing screen fetches again. One shared counter for all
 * repositories, because a change in one resource shows up in others (an itinerary edit changes the
 * trip summary on Home).
 */
class DataInvalidation {
    private val tick = MutableStateFlow(0)
    val ticks: StateFlow<Int> = tick
    fun invalidate() = tick.update { it + 1 }
}

class WebConnectionMonitor(private val invalidation: DataInvalidation) : ConnectionMonitor {
    private val current = MutableStateFlow<ApiFailure?>(null)
    override val failure: StateFlow<ApiFailure?> = current

    internal fun report(failure: ApiFailure) {
        current.value = failure
    }

    internal fun clear() {
        current.value = null
    }

    override fun retry() {
        current.value = null
        invalidation.invalidate()
    }

    override fun dismiss() {
        current.value = null
    }
}

/**
 * Turns request/response calls into the Flow-based repository interfaces. The web app is online-only:
 * there is no local copy, so a "stream" is just "fetch again whenever something changed".
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RemoteGateway(
    val api: RanselApi,
    private val monitor: WebConnectionMonitor,
    private val invalidation: DataInvalidation,
) {
    /**
     * Emits the result of [fetch] now and after every invalidation. A failure is reported to the
     * connection monitor and emits nothing. When the thing no longer exists (`*_NOT_FOUND`, e.g. a
     * trip that was just deleted while its screen is still leaving) the stream emits [whenMissing],
     * or stays quiet when that is null, instead of raising the error state.
     */
    fun <T> observe(whenMissing: (() -> T)? = null, fetch: suspend () -> ApiResult<T>): Flow<T> =
        invalidation.ticks
            .mapLatest { fetch() }
            .mapNotNull { result ->
                when (result) {
                    is ApiResult.Success -> {
                        monitor.clear()
                        result.value
                    }
                    is ApiResult.Failure -> {
                        if (result.error.isNotFound) {
                            whenMissing?.invoke()
                        } else {
                            monitor.report(result.error)
                            null
                        }
                    }
                }
            }

    /** Runs a write. On success every observer refreshes; on failure the monitor shows the state. */
    suspend fun <T> mutate(block: suspend () -> ApiResult<T>) {
        when (val result = block()) {
            is ApiResult.Success -> invalidation.invalidate()
            is ApiResult.Failure -> monitor.report(result.error)
        }
    }
}

/** Web has nothing to sync; pull-to-refresh just fetches the visible data again. */
class WebSyncStatus(private val invalidation: DataInvalidation) :
    app.ransel.data.sync.SyncStatus by app.ransel.data.sync.NoSyncStatus {
    override suspend fun refresh() {
        invalidation.invalidate()
        kotlinx.coroutines.delay(600) // the spinner needs a moment; the fetch itself is asynchronous
    }
}
