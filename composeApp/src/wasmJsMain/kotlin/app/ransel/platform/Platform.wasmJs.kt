package app.ransel.platform

import app.ransel.data.fake.InMemoryExpenseRepository
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPackingRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.repository.AppPreferences
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import org.koin.core.module.Module
import org.koin.dsl.module

// TEMPORARY: the web target has no local database (it is online-only). Until the remote repositories
// of F2.2 exist, it runs on in-memory data so the shared screens do not crash. Nothing is persisted.
actual val platformModule: Module = module {
    single { InMemoryStore() }
    single<TripRepository> { InMemoryTripRepository(get()) }
    single<ItineraryRepository> { InMemoryItineraryRepository(get()) }
    single<PackingRepository> { InMemoryPackingRepository(get()) }
    single<ExpenseRepository> { InMemoryExpenseRepository(get()) }
    single<PreferencesRepository> { InMemoryPreferencesRepository(AppPreferences(onboardingCompleted = true)) }
}

// Web has no debug/release split yet; the gallery stays reachable until the Phase 2 deploy.
actual val isDebugBuild: Boolean = true
