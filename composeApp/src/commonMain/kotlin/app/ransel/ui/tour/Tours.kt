package app.ransel.ui.tour

import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.tour_expenses_body
import ransel.composeapp.generated.resources.tour_expenses_title
import ransel.composeapp.generated.resources.tour_home_account_body
import ransel.composeapp.generated.resources.tour_home_account_title
import ransel.composeapp.generated.resources.tour_home_create_body
import ransel.composeapp.generated.resources.tour_home_create_title
import ransel.composeapp.generated.resources.tour_home_explore_body
import ransel.composeapp.generated.resources.tour_home_explore_body_plain
import ransel.composeapp.generated.resources.tour_home_explore_title
import ransel.composeapp.generated.resources.tour_home_trips_body
import ransel.composeapp.generated.resources.tour_home_trips_title
import ransel.composeapp.generated.resources.tour_itinerary_body
import ransel.composeapp.generated.resources.tour_itinerary_title
import ransel.composeapp.generated.resources.tour_packing_body
import ransel.composeapp.generated.resources.tour_packing_title
import ransel.composeapp.generated.resources.tour_swipe_body
import ransel.composeapp.generated.resources.tour_swipe_title
import ransel.composeapp.generated.resources.tour_trip_add_body
import ransel.composeapp.generated.resources.tour_trip_add_title
import ransel.composeapp.generated.resources.tour_trip_tabs_body
import ransel.composeapp.generated.resources.tour_trip_tabs_title

/*
 * The tours of the design's "TourAturan" board. A step whose target is not on screen is left out and the steps are
 * numbered again (a user with trips has no "Buat trip pertama" button, so Home's replay has 3 steps).
 */

/** Beranda: Buat trip pertama → Trip → Jelajah (credits from the server, [credits] = daily free, new search) → Akun. */
fun homeTour(credits: Pair<Int, Int>?): List<TourStep> = listOf(
    TourStep(TourTarget.HOME_CREATE, Res.string.tour_home_create_title, Res.string.tour_home_create_body),
    TourStep(TourTarget.TAB_TRIPS, Res.string.tour_home_trips_title, Res.string.tour_home_trips_body),
    if (credits != null) {
        TourStep(TourTarget.TAB_EXPLORE, Res.string.tour_home_explore_title, Res.string.tour_home_explore_body, listOf(credits.first, credits.second))
    } else {
        TourStep(TourTarget.TAB_EXPLORE, Res.string.tour_home_explore_title, Res.string.tour_home_explore_body_plain)
    },
    TourStep(TourTarget.TAB_ACCOUNT, Res.string.tour_home_account_title, Res.string.tour_home_account_body),
)

/** The first time any trip opens: the tab row, then the + button. */
val tripTour = listOf(
    TourStep(TourTarget.TRIP_TABS, Res.string.tour_trip_tabs_title, Res.string.tour_trip_tabs_body),
    TourStep(TourTarget.TRIP_ADD, Res.string.tour_trip_add_title, Res.string.tour_trip_add_body),
)

val itineraryTip = listOf(TourStep(TourTarget.ITINERARY_DAYS, Res.string.tour_itinerary_title, Res.string.tour_itinerary_body))
val packingTip = listOf(TourStep(TourTarget.PACKING_SWITCH, Res.string.tour_packing_title, Res.string.tour_packing_body))
val expensesTip = listOf(TourStep(TourTarget.EXPENSES_SWITCH, Res.string.tour_expenses_title, Res.string.tour_expenses_body))
val swipeTip = listOf(TourStep(TourTarget.SWIPE_CARDS, Res.string.tour_swipe_title, Res.string.tour_swipe_body))
