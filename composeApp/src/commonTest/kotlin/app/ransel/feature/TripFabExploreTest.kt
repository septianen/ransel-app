package app.ransel.feature

import app.ransel.feature.group.ExplorePrice
import app.ransel.feature.group.canCreatePollFromSheet
import app.ransel.feature.group.canInviteFromSheet
import app.ransel.feature.group.canSearchPlacesFromSheet
import app.ransel.feature.group.explorePrice
import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.CreditsDto
import app.ransel.feature.trips.TripPhase
import app.ransel.shared.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** "Cari tempat dengan AI" in the trip + sheet (plan fab-cari-tempat-voting, 22 Nov 2026): who sees the row. */
class TripFabExploreTest {
    @Test
    fun ownersAndEditorsSeeItBeforeTheTripEnds() {
        for (phase in listOf(TripPhase.UPCOMING, TripPhase.ONGOING)) {
            assertTrue(canSearchPlacesFromSheet(Role.OWNER, phase), "owner, $phase")
            assertTrue(canSearchPlacesFromSheet(Role.EDITOR, phase), "editor, $phase")
        }
    }

    @Test
    fun pollAndInviteStayOnUpcomingAndOngoingTrips() {
        for (phase in listOf(TripPhase.UPCOMING, TripPhase.ONGOING)) {
            assertTrue(canCreatePollFromSheet(solo = false, phase), "poll, $phase")
            assertTrue(canInviteFromSheet(isOwner = true, phase), "invite owner, $phase")
        }
        assertFalse(canCreatePollFromSheet(solo = true, TripPhase.UPCOMING))
        assertFalse(canInviteFromSheet(isOwner = false, TripPhase.ONGOING))
    }

    @Test
    fun endedTripHidesPollAndInvite() {
        assertFalse(canCreatePollFromSheet(solo = false, TripPhase.FINISHED))
        assertFalse(canCreatePollFromSheet(solo = true, TripPhase.FINISHED))
        assertFalse(canInviteFromSheet(isOwner = true, TripPhase.FINISHED))
        assertFalse(canInviteFromSheet(isOwner = false, TripPhase.FINISHED))
    }

    @Test
    fun viewersAndEndedTripsDoNot() {
        for (phase in TripPhase.entries) assertFalse(canSearchPlacesFromSheet(Role.VIEWER, phase), "viewer, $phase")
        assertFalse(canSearchPlacesFromSheet(Role.OWNER, TripPhase.FINISHED))
        assertFalse(canSearchPlacesFromSheet(Role.EDITOR, TripPhase.FINISHED))
        assertFalse(canSearchPlacesFromSheet(null, TripPhase.UPCOMING))
    }

    private fun credits(balance: Int, freeLeft: Int, search: Int = 3) =
        CreditsDto(balance, freeLeft, 3, Instant.parse("2026-11-22T17:00:00Z"), CreditCostsDto(search = search, nextBatch = 1, placeDetail = 0))

    @Test
    fun thePriceChipComesFromTheServerNumbers() {
        assertEquals(ExplorePrice.Credits(3), explorePrice(credits(balance = 20, freeLeft = 0), resumes = false))
        assertEquals(ExplorePrice.Credits(4), explorePrice(credits(balance = 20, freeLeft = 1, search = 4), resumes = false))
        assertEquals(ExplorePrice.FreeToday, explorePrice(credits(balance = 0, freeLeft = 3), resumes = false))
        // unknown credits, a free search, or a resumed session: no chip
        assertNull(explorePrice(null, resumes = false))
        assertNull(explorePrice(credits(balance = 20, freeLeft = 0, search = 0), resumes = false))
        assertNull(explorePrice(credits(balance = 20, freeLeft = 0), resumes = true))
    }
}
