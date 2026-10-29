package app.ransel.di

import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.RoomExpenseRepository
import app.ransel.data.local.RoomItineraryRepository
import app.ransel.data.local.RoomPackingRepository
import app.ransel.data.local.RoomPersonalItemRepository
import app.ransel.data.repository.PersonalItemRepository
import app.ransel.data.local.RoomPreferencesRepository
import app.ransel.data.local.RoomTripRepository
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.auth.SessionHooks
import app.ransel.data.auth.SessionManager
import app.ransel.data.sync.RoomSessionHooks
import app.ransel.data.sync.SyncCoordinator
import app.ransel.data.sync.SyncStatus
import app.ransel.shared.dto.LOCAL_USER_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/** Local-first repositories for Android and iOS. The platform module provides [RanselDatabase]. */
val mobileDataModule = module {
    single<TripRepository> { RoomTripRepository(get<RanselDatabase>(), currentUserId(get())) }
    single<ItineraryRepository> { RoomItineraryRepository(get<RanselDatabase>()) }
    single<PackingRepository> { RoomPackingRepository(get<RanselDatabase>()) }
    single<PersonalItemRepository> { RoomPersonalItemRepository(get<RanselDatabase>(), currentUserId(get())) }
    single<ExpenseRepository> { RoomExpenseRepository(get<RanselDatabase>(), currentUserId(get())) }
    single<PreferencesRepository> { RoomPreferencesRepository(get<RanselDatabase>()) }

    // Sync lives as long as the process; created when the session gate first asks for SyncStatus.
    single { SyncCoordinator(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), get(), get()) }
    single<SyncStatus> { get<SyncCoordinator>() }
    single<SessionHooks> { RoomSessionHooks(get()) { get<SyncCoordinator>() } }
}

/** Signed-in user's id for new rows; the guest placeholder before the first sign-in. */
private fun currentUserId(session: SessionManager): () -> String = { session.user?.id ?: LOCAL_USER_ID }
