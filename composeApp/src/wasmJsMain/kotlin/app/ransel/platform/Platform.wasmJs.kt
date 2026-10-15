package app.ransel.platform

import app.ransel.data.local.LocalStoragePreferencesRepository
import app.ransel.data.remote.ConnectionMonitor
import app.ransel.data.remote.DataInvalidation
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.sync.SyncStatus
import app.ransel.data.auth.NoSessionHooks
import app.ransel.data.auth.SessionHooks
import app.ransel.data.auth.TokenStore
import app.ransel.data.auth.WebTokenStore
import app.ransel.data.remote.RemoteExpenseRepository
import app.ransel.data.remote.RemoteGateway
import app.ransel.data.remote.RemoteItineraryRepository
import app.ransel.data.remote.RemotePackingRepository
import app.ransel.data.remote.RemoteTripRepository
import app.ransel.data.remote.WebConnectionMonitor
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** Web is online-only: repositories call the API directly (docs/TRD.md "Architecture"); no database. */
actual val platformModule: Module = module {
    single { DataInvalidation() }
    single { WebConnectionMonitor(get()) }
    single<ConnectionMonitor> { get<WebConnectionMonitor>() }
    single<SyncStatus> { NoSyncStatus }
    single<SessionHooks> { NoSessionHooks }
    single<TokenStore> { WebTokenStore() }
    single { RemoteGateway(get(), get(), get()) }
    single<TripRepository> { RemoteTripRepository(get()) }
    single<ItineraryRepository> { RemoteItineraryRepository(get()) }
    single<PackingRepository> { RemotePackingRepository(get()) }
    single<ExpenseRepository> { RemoteExpenseRepository(get()) }
    single<PreferencesRepository> { LocalStoragePreferencesRepository() }
}

// Web has no debug/release split yet; the gallery stays reachable until the Phase 2 deploy.
actual val isDebugBuild: Boolean = true

actual val supportsInAppLanguage: Boolean = false

actual val requiresAccount: Boolean = true

@JsFun("() => window.location.pathname")
private external fun browserPath(): String

@JsFun("() => new URLSearchParams(window.location.search).get('token') || ''")
private external fun queryToken(): String

@JsFun("() => window.history.replaceState(null, '', '/')")
private external fun resetUrl()

actual fun initialResetToken(): String? =
    queryToken().takeIf { it.isNotBlank() && browserPath().trimEnd('/').endsWith("reset-password") }

actual fun clearResetTokenFromUrl() = resetUrl()

@JsFun("() => { const m = window.location.pathname.match(/^\\/j\\/([^/]+)\\/?$/); return m ? decodeURIComponent(m[1]) : ''; }")
private external fun invitePathCode(): String

@JsFun("(t) => { if (navigator.share) { navigator.share({ text: t }).catch(() => {}); } else if (navigator.clipboard) { navigator.clipboard.writeText(t); } }")
private external fun shareOrCopy(text: String)

actual fun initialInviteCode(): String? = invitePathCode().takeIf { it.isNotBlank() }

@androidx.compose.runtime.Composable
actual fun rememberShareText(): (String) -> Unit = { shareOrCopy(it) }
