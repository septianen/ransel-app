package app.ransel.di

import app.ransel.feature.account.AccountViewModel
import app.ransel.feature.expenses.ExpensesViewModel
import app.ransel.feature.home.HomeViewModel
import app.ransel.feature.itinerary.ActivityFormViewModel
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.data.invite.InviteLinks
import app.ransel.feature.invite.InviteSheetViewModel
import app.ransel.feature.invite.JoinTripViewModel
import app.ransel.feature.members.MembersViewModel
import app.ransel.feature.packing.PackingViewModel
import app.ransel.feature.trip.TripSidePanelViewModel
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.feature.trips.TripListViewModel
import app.ransel.data.auth.AuthRepository
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.apiOrigin
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.auth.CheckEmailViewModel
import app.ransel.feature.auth.ForgotPasswordViewModel
import app.ransel.feature.auth.NewPasswordViewModel
import app.ransel.feature.auth.SignInViewModel
import app.ransel.feature.auth.SignUpViewModel
import app.ransel.platform.platformModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Platform-independent bindings: ViewModels (repositories come from the platform module). */
val commonModule: Module = module {
    // The API client and the session depend on each other: the manager gets the client lazily.
    single { SessionManager(get()) { get<RanselApi>() } }
    single { RanselApi(createApiHttpClient(), apiOrigin(), get<SessionManager>()) }
    single { AuthRepository(get(), get(), get()) }
    viewModelOf(::SignInViewModel)
    viewModelOf(::SignUpViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModel { params -> CheckEmailViewModel(params.get(), get()) }
    viewModel { params -> NewPasswordViewModel(params.get(), get()) }
    viewModelOf(::HomeViewModel)
    viewModelOf(::TripListViewModel)
    viewModelOf(::AccountViewModel)
    viewModel { params -> TripFormViewModel(params.getOrNull(), get(), get()) }
    viewModel { params -> ItineraryViewModel(params.get(), get(), get()) }
    viewModel { params -> PackingViewModel(params.get(), get()) }
    viewModel { params -> TripSidePanelViewModel(params.get(), get(), get()) }
    viewModel { params -> ExpensesViewModel(params.get(), get()) }
    single { InviteLinks() }
    viewModel { params -> InviteSheetViewModel(params.get(), get()) }
    viewModel { params -> JoinTripViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> MembersViewModel(params.get(), get(), get(), get()) }
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
