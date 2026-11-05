package app.ransel.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlFragmentsTest {
    private val trip = "6f1c2a52-4d1e-4b8a-9a77-2f0c6c1e9d31"

    @Test
    fun screensGetShortAddresses() {
        assertEquals("#/home", urlFragmentForRoute(HomeRoute))
        assertEquals("#/sign-in", urlFragmentForRoute(SignInRoute))
        assertEquals("#/trips/$trip", urlFragmentForRoute(TripRoute(trip)))
        assertEquals("#/trips/$trip/expenses", urlFragmentForRoute(TripRoute(trip, "EXPENSES")))
        assertEquals("#/trips/$trip/polls/p1", urlFragmentForRoute(PollRoute(trip, "p1", share = true)))
        assertEquals("#/account/credits", urlFragmentForRoute(CreditsRoute))
        assertEquals("#/account/password", urlFragmentForRoute(ChangePasswordRoute))
    }

    @Test
    fun noPersonalDataInTheAddress() {
        assertEquals("#/check-email", urlFragmentForRoute(CheckEmailRoute("rina@example.com")))
        assertEquals("#/new-password", urlFragmentForRoute(NewPasswordRoute("secret-token")))
        assertEquals("#/email-verified", urlFragmentForRoute(EmailVerifiedRoute("secret-token")))
        assertEquals("#/join", urlFragmentForRoute(JoinTripRoute("BALI-7K2Q")))
        assertEquals("#/trips/$trip/albums/a1", urlFragmentForRoute(AlbumGalleryRoute(trip, "a1", "Bali", "Rina", "https://drive.google.com/x")))
    }

    @Test
    fun aReloadOpensScreensThatNeedOnlyIds() {
        assertEquals(TripRoute(trip), routeForUrlFragment("#/trips/$trip"))
        assertEquals(TripRoute(trip, "ITINERARY"), routeForUrlFragment("#/trips/$trip/itinerary"))
        assertEquals(MembersRoute(trip), routeForUrlFragment("#/trips/$trip/members"))
        assertEquals(PollRoute(trip, "p1"), routeForUrlFragment("#/trips/$trip/polls/p1"))
        assertEquals(ExploreRoute, routeForUrlFragment("#/explore"))
        assertEquals(NotificationSettingsRoute, routeForUrlFragment("#/notifications/settings"))
        // every address it reads back is the one it writes
        listOf(TripRoute(trip, "PACKING"), RecapRoute(trip), AllTransactionsRoute(trip), CreditsRoute, AccountRoute, ChangePasswordRoute).forEach {
            assertEquals(it, routeForUrlFragment(urlFragmentForRoute(it)))
        }
    }

    @Test
    fun formsSignInStepsAndOldAddressesStartOver() {
        assertNull(routeForUrlFragment(""))
        assertNull(routeForUrlFragment("#/trips/new"))
        assertNull(routeForUrlFragment("#/trips/$trip/polls/new"))
        assertNull(routeForUrlFragment("#/trips/$trip/activity"))
        assertNull(routeForUrlFragment("#/sign-in"))
        assertNull(routeForUrlFragment("#/new-password"))
        assertNull(routeForUrlFragment("#app.ransel.navigation.SignInRoute"))
        assertNull(routeForUrlFragment("#/trips/$trip/nope"))
    }
}
