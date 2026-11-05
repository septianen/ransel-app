package app.ransel.ui.tour

import androidx.compose.ui.geometry.Rect
import app.ransel.data.credit.CreditsStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.CreditsDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The new-user tour's rules (design "TourAturan", 5 Nov 2026). */
class TourControllerTest {
    private val prefs = InMemoryPreferencesRepository()
    private val credits = CreditsStore()
    private var user: String? = "u1"
    private val tour = TourController(prefs, { user }, credits, startDelayMillis = 1)

    private fun show(vararg targets: TourTarget) = targets.forEach { tour.targets[it] = TargetInfo(Rect(0f, 0f, 10f, 10f), 16f, null) }

    @Test
    fun aTourIsShownOncePerUserAndRecordedHoweverItCloses() = runTest {
        show(TourTarget.TRIP_TABS, TourTarget.TRIP_ADD)
        tour.start(TourId.TRIP, tripTour)
        assertEquals(2, tour.run?.steps?.size)
        tour.next()
        assertEquals(1, tour.run?.index)
        tour.finish()
        tour.persistFlags()
        assertNull(tour.run)
        assertTrue(tour.isDone(TourId.TRIP))
        tour.start(TourId.TRIP, tripTour)
        assertNull(tour.run, "seen already")
        // another account on the same device sees it
        user = "u2"
        tour.start(TourId.TRIP, tripTour)
        assertNotNull(tour.run)
    }

    @Test
    fun aStepWithoutItsTargetIsLeftOutAndNoStepMeansNoTour() = runTest {
        show(TourTarget.TAB_TRIPS, TourTarget.TAB_EXPLORE, TourTarget.TAB_ACCOUNT) // no "Buat trip pertama": has trips
        tour.start(TourId.HOME, homeTour(null))
        assertEquals(listOf(TourTarget.TAB_TRIPS, TourTarget.TAB_EXPLORE, TourTarget.TAB_ACCOUNT), tour.run?.steps?.map { it.target })
        tour.finish()

        tour.start(TourId.PACKING, packingTip) // a solo trip has no Pribadi/Bersama
        assertNull(tour.run)
        tour.persistFlags()
        assertFalse(tour.isDone(TourId.PACKING), "not shown = not recorded")
    }

    @Test
    fun atMostFiveStepsPerLaunch() = runTest {
        show(TourTarget.HOME_CREATE, TourTarget.TAB_TRIPS, TourTarget.TAB_EXPLORE, TourTarget.TAB_ACCOUNT, TourTarget.TRIP_TABS, TourTarget.TRIP_ADD, TourTarget.ITINERARY_DAYS)
        tour.start(TourId.HOME, homeTour(null))
        tour.finish()
        tour.start(TourId.TRIP, tripTour) // 4 + 2 > 5: waits for the next launch
        assertNull(tour.run)
        tour.start(TourId.ITINERARY, itineraryTip) // 4 + 1 fits
        assertNotNull(tour.run)
    }

    @Test
    fun replayForgetsThisUsersToursAndAskHomeToStart() = runTest {
        show(TourTarget.TRIP_TABS)
        tour.start(TourId.TRIP, tripTour)
        tour.finish()
        tour.persistFlags()
        tour.replay()
        assertTrue(tour.replayHome)
        assertFalse(tour.isDone(TourId.TRIP))
    }

    @Test
    fun guestsNeverSeeATour() = runTest {
        user = null
        show(TourTarget.TRIP_TABS)
        tour.start(TourId.TRIP, tripTour)
        assertNull(tour.run)
    }

    @Test
    fun creditNumbersComeFromTheServerAndAreKeptForOffline() = runTest {
        assertNull(tour.creditNumbers(), "never seen: the sentence without numbers")
        credits.update(CreditsDto(10, 3, 5, Instant.fromEpochSeconds(0), CreditCostsDto(search = 4, nextBatch = 1, placeDetail = 0)))
        assertEquals(5 to 4, tour.creditNumbers())
        credits.update(null) // offline later: the last ones seen
        assertEquals(5 to 4, tour.creditNumbers())
        assertEquals(listOf(5, 4), homeTour(5 to 4)[2].bodyArgs)
        assertTrue(homeTour(null)[2].bodyArgs.isEmpty())
    }
}
