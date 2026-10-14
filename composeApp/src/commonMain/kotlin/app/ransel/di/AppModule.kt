package app.ransel.di

import app.ransel.feature.home.HomeViewModel
import app.ransel.feature.itinerary.ActivityFormViewModel
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.feature.trips.TripListViewModel
import app.ransel.platform.platformModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Platform-independent bindings: ViewModels (repositories come from the platform module). */
val commonModule: Module = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::TripListViewModel)
    viewModel { params -> TripFormViewModel(params.getOrNull(), get(), get()) }
    viewModel { params -> ItineraryViewModel(params.get(), get(), get()) }
    viewModel { params -> ActivityFormViewModel(
            tripId = params.get<String>(0),
            itemId = params.get<String?>(1),
            initialDay = params.get<String?>(2),
            trips = get(),
            itinerary = get(),
        ) }
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(commonModule, platformModule)
    }
}
