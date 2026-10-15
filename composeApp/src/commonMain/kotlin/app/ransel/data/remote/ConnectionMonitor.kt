package app.ransel.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Surfaces API failures to the app shell so it can show the "connection" or "server" state (design
 * "State: error koneksi / error server") instead of every screen handling errors itself.
 * Android and iOS are offline-first and never fail this way, so they use [NoConnectionMonitor].
 */
interface ConnectionMonitor {
    /** The last failure that has not been dismissed, or null when everything works. */
    val failure: StateFlow<ApiFailure?>

    /** "Coba lagi": clears the failure and re-fetches what the screens show. */
    fun retry()

    /** "Kembali ke Beranda": clears the failure without re-fetching. */
    fun dismiss()
}

object NoConnectionMonitor : ConnectionMonitor {
    override val failure: StateFlow<ApiFailure?> = MutableStateFlow(null)
    override fun retry() = Unit
    override fun dismiss() = Unit
}
