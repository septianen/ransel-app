package app.ransel.di

import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.RoomExpenseRepository
import app.ransel.data.local.RoomItineraryRepository
import app.ransel.data.local.RoomPackingRepository
import app.ransel.data.local.RoomPreferencesRepository
import app.ransel.data.local.RoomTripRepository
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import org.koin.dsl.module

/** Local-first repositories for Android and iOS. The platform module provides [RanselDatabase]. */
val mobileDataModule = module {
    single<TripRepository> { RoomTripRepository(get<RanselDatabase>()) }
    single<ItineraryRepository> { RoomItineraryRepository(get<RanselDatabase>()) }
    single<PackingRepository> { RoomPackingRepository(get<RanselDatabase>()) }
    single<ExpenseRepository> { RoomExpenseRepository(get<RanselDatabase>()) }
    single<PreferencesRepository> { RoomPreferencesRepository(get<RanselDatabase>()) }
}
