package app.ransel.platform

import app.ransel.data.local.LocalStoragePreferencesRepository
import app.ransel.data.remote.ConnectionMonitor
import app.ransel.data.remote.DataInvalidation
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.RemoteExpenseRepository
import app.ransel.data.remote.RemoteGateway
import app.ransel.data.remote.RemoteItineraryRepository
import app.ransel.data.remote.RemotePackingRepository
import app.ransel.data.remote.RemoteTripRepository
import app.ransel.data.remote.WebConnectionMonitor
import app.ransel.data.remote.apiOrigin
import app.ransel.data.remote.createApiHttpClient
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
    single { RanselApi(createApiHttpClient(), apiOrigin()) }
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
