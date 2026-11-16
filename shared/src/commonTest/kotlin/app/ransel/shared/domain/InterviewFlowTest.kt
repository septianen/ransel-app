package app.ransel.shared.domain

import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import app.ransel.shared.model.TravelStyle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterviewFlowTest {
    private val none = InterviewCityFacts()
    private val bandung = InterviewDestination("Bandung", "p-bdg", -6.9175, 107.6191, "ID")
    private val bali = InterviewDestination("Bali", "p-bali", -8.4095, 115.1889, "ID")
    private val tokyo = InterviewDestination("Tokyo", "p-tyo", 35.6762, 139.6503, "JP")
    private val subang = TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID")
    private val jakarta = TripOriginDto("Jakarta", -6.2088, 106.8456, countryCode = "ID")
    private val dates = LocalDate(2026, 11, 20) to LocalDate(2026, 11, 22)
    private val rentalBali = InterviewCityFacts(motorcycleRentalCity = true)

    private fun start(destination: InterviewDestination = bandung) =
        InterviewAnswers().withDestination(destination, none).withDates(dates.first, dates.second)

    // ── Layar 1 ─────────────────────────────────────────────────────────────

    @Test
    fun layar1NeedsADestinationAndDatesOrTheSwitch() {
        val door = InterviewDoor.MY_TRIPS
        val step = InterviewStep.DESTINATION
        assertFalse(canContinue(door, step, InterviewAnswers()))
        assertFalse(canContinue(door, step, InterviewAnswers().withDestination(bandung, none)), "1e: destination, no dates, switch off")
        assertTrue(canContinue(door, step, start()))
        assertTrue(canContinue(door, step, InterviewAnswers().withDestination(bandung, none).withDatesUndecided(true)), "1d")
        assertFalse(canContinue(door, step, InterviewAnswers().withDatesUndecided(true)), "dates alone are not enough")
    }

    @Test
    fun theDateRangeAndTheSwitchExcludeEachOther() {
        val withDates = start()
        val undecided = withDates.withDatesUndecided(true)
        assertTrue(undecided.datesUndecided)
        assertNull(undecided.startDate)
        assertNull(undecided.endDate)
        val back = undecided.withDates(dates.first, dates.second)
        assertFalse(back.datesUndecided)
        assertTrue(back.hasDates)
        assertFalse(undecided.withDatesUndecided(false).datesUndecided)
    }

    @Test
    fun layar1HasNoSkipAndNoSkipInterviewLink() {
        InterviewDoor.entries.forEach { assertFalse(canSkip(it, InterviewStep.DESTINATION)) }
        assertTrue(canSkipInterview(InterviewStep.ORIGIN))
        listOf(InterviewStep.DESTINATION, InterviewStep.DEPARTURE, InterviewStep.LOCAL_TRANSPORT, InterviewStep.TASTE)
            .forEach { assertFalse(canSkipInterview(it), "$it") }
    }

    // ── steps and the indicator follow the answers ─────────────────────────

    @Test
    fun subangToBandungByMotorcycleDropsW3AndTheIndicator() {
        var a = start().withOrigin(subang, none)
        assertEquals(DepartureBand.SHORT, a.departureBand())
        assertEquals(DepartureMode.OWN_MOTORCYCLE, a.departureOptions(none).first())
        assertEquals(InterviewProgress(3, 5), a.progress(InterviewStep.DEPARTURE))
        a = a.withDeparture(DepartureMode.OWN_MOTORCYCLE, none)
        assertEquals(InterviewProgress(3, 4), a.progress(InterviewStep.DEPARTURE))
        assertEquals(InterviewProgress(4, 4), a.progress(InterviewStep.TASTE))
        assertEquals(InterviewStep.TASTE, nextInterviewStep(a.steps(), InterviewStep.DEPARTURE), "W3 is skipped")
        assertEquals(InterviewStep.DEPARTURE, previousInterviewStep(a.steps(), InterviewStep.TASTE))
    }

    @Test
    fun anOverseasDestinationIsFourFromLayar1AndSkipsW2() {
        val a = start(tokyo)
        assertTrue(a.isOverseas())
        assertEquals(InterviewProgress(1, 4), a.progress(InterviewStep.DESTINATION))
        assertEquals(InterviewStep.LOCAL_TRANSPORT, nextInterviewStep(a.steps(), InterviewStep.ORIGIN))
        assertEquals(InterviewStep.ORIGIN, previousInterviewStep(a.steps(), InterviewStep.LOCAL_TRANSPORT))
    }

    @Test
    fun anOriginAbroadAlsoSkipsW2AndClearsAnAnswer() {
        val a = start().withOrigin(subang, none).withDeparture(DepartureMode.OWN_CAR, none)
        val abroad = a.withOrigin(TripOriginDto("Singapore", 1.35, 103.82, countryCode = "SG"), none)
        assertTrue(abroad.isOverseas())
        assertNull(abroad.departure)
    }

    @Test
    fun changingW2BackToAFlightBringsW3AndTheTotalBackUp() {
        val own = start().withOrigin(subang, none).withDeparture(DepartureMode.OWN_CAR, none)
        assertEquals(4, own.steps().size)
        val plane = own.withDeparture(DepartureMode.BUS, none)
        assertEquals(5, plane.steps().size, "Y goes up again")
        assertEquals(InterviewStep.LOCAL_TRANSPORT, nextInterviewStep(plane.steps(), InterviewStep.DEPARTURE))
    }

    @Test
    fun pickingAnOwnVehicleDropsAnEarlierW3Answer() {
        val a = start(bali).withOrigin(jakarta, rentalBali)
            .withDeparture(DepartureMode.PLANE, rentalBali)
            .withLocalTransport(LocalTransport.RENT_MOTORCYCLE, rentalBali)
        assertEquals(LocalTransport.RENT_MOTORCYCLE, a.localTransport)
        val own = a.withDeparture(DepartureMode.OWN_CAR, rentalBali)
        assertNull(own.localTransport)
    }

    @Test
    fun anAnswerThatIsNoLongerOfferedIsDropped() {
        val a = start().withOrigin(subang, none).withDeparture(DepartureMode.OWN_MOTORCYCLE, none)
        // Jakarta to Bandung is medium: the own motorcycle is not on that list
        val moved = a.withOrigin(jakarta, none)
        assertEquals(DepartureBand.MEDIUM, moved.departureBand())
        assertNull(moved.departure)
        // an answer that is still offered stays
        val stays = start().withOrigin(subang, none).withDeparture(DepartureMode.BUS, none).withOrigin(TripOriginDto("Lembang", -6.81, 107.62), none)
        assertEquals(DepartureMode.BUS, stays.departure)
    }

    @Test
    fun aNewDestinationClearsTheOldKhasChipsAndW3IfItIsNoLongerOffered() {
        val a = start(bali).withOrigin(jakarta, rentalBali)
            .withDeparture(DepartureMode.PLANE, rentalBali)
            .withLocalTransport(LocalTransport.RENT_MOTORCYCLE, rentalBali)
            .toggleExtraInterest("Pantai Kuta")
            .toggleInterest(Interest.FOOD)
        val tokyoTrip = a.withDestination(tokyo, none)
        assertEquals(emptySet(), tokyoTrip.taste.extraInterests)
        assertEquals(setOf(Interest.FOOD), tokyoTrip.taste.interests, "core interests are not about a place")
        assertNull(tokyoTrip.departure, "abroad: no W2")
        assertNull(tokyoTrip.localTransport, "no motorbike rental offered in Tokyo")
        assertEquals(a.startDate, tokyoTrip.startDate)
        // the same destination again changes nothing
        assertEquals(a.taste, a.withDestination(bali, rentalBali).taste)
    }

    @Test
    fun withoutAPointW2ShowsTheFullListAndYStaysFive() {
        val text = InterviewAnswers().withDestination(InterviewDestination("Desa Sukamaju"), none).withDates(dates.first, dates.second)
            .withOrigin(subang, none)
        assertEquals(DepartureBand.UNKNOWN, text.departureBand())
        assertEquals(
            listOf(
                DepartureMode.OWN_MOTORCYCLE, DepartureMode.OWN_CAR, DepartureMode.TRAVEL_SHUTTLE, DepartureMode.BUS,
                DepartureMode.TRAIN, DepartureMode.PLANE, DepartureMode.FERRY,
            ),
            text.departureOptions(none),
        )
        assertEquals(5, text.steps().size)
        assertNull(text.roadDistanceKm())
    }

    @Test
    fun theW2SubtitleHasTheRoadDistance() {
        assertEquals(54, start().withOrigin(subang, none).roadDistanceKm(), "Subang to Bandung, about 42 km straight times 1.3")
        assertNull(start(tokyo).withOrigin(jakarta, none).roadDistanceKm(), "abroad: no W2, no distance")
        assertNull(start().roadDistanceKm(), "no origin")
    }

    // ── Lanjut and Lewati ───────────────────────────────────────────────────

    @Test
    fun eachQuestionNeedsAnAnswerBeforeLanjut() {
        val door = InterviewDoor.MY_TRIPS
        val a = start().withOrigin(subang, none)
        assertFalse(canContinue(door, InterviewStep.DEPARTURE, a))
        assertTrue(canContinue(door, InterviewStep.DEPARTURE, a.withDeparture(DepartureMode.BUS, none)))
        assertFalse(canContinue(door, InterviewStep.LOCAL_TRANSPORT, a))
        assertTrue(canContinue(door, InterviewStep.LOCAL_TRANSPORT, a.withLocalTransport(LocalTransport.RIDE_HAILING, none)))
        assertFalse(canContinue(door, InterviewStep.ORIGIN, start()))
        assertTrue(canContinue(door, InterviewStep.ORIGIN, a))
    }

    @Test
    fun w4FromMyTripsTakesAnyPickAndFromJelajahNeedsAnInterest() {
        val styleOnly = start().withStyle(TravelStyle.PADAT)
        assertTrue(canContinue(InterviewDoor.MY_TRIPS, InterviewStep.TASTE, styleOnly))
        assertFalse(canContinue(InterviewDoor.EXPLORE, InterviewStep.TASTE, styleOnly), "the style alone is not enough")
        val khas = start().toggleExtraInterest("Kuliner Sunda")
        assertTrue(canContinue(InterviewDoor.EXPLORE, InterviewStep.TASTE, khas), "a khas chip counts as an interest")
        assertFalse(canContinue(InterviewDoor.MY_TRIPS, InterviewStep.TASTE, start()))
        val off = start().toggleInterest(Interest.NATURE).toggleInterest(Interest.NATURE)
        assertFalse(canContinue(InterviewDoor.EXPLORE, InterviewStep.TASTE, off), "all chips off: Lanjut off again")
    }

    @Test
    fun w4HasNoSkipFromJelajah() {
        assertTrue(canSkip(InterviewDoor.MY_TRIPS, InterviewStep.TASTE))
        assertFalse(canSkip(InterviewDoor.EXPLORE, InterviewStep.TASTE))
        assertTrue(canSkip(InterviewDoor.EXPLORE, InterviewStep.ORIGIN))
    }

    @Test
    fun lewatiStoresNothingAndClearsTheScreen() {
        val a = start(bali).withOrigin(jakarta, rentalBali)
            .withDeparture(DepartureMode.PLANE, rentalBali)
            .withLocalTransport(LocalTransport.RIDE_HAILING, rentalBali)
            .toggleInterest(Interest.FOOD).withStyle(TravelStyle.SANTAI)
        assertNull(a.skipped(InterviewStep.ORIGIN, rentalBali).origin)
        assertNull(a.skipped(InterviewStep.DEPARTURE, rentalBali).departure)
        assertNull(a.skipped(InterviewStep.LOCAL_TRANSPORT, rentalBali).localTransport)
        assertTrue(a.skipped(InterviewStep.TASTE, rentalBali).taste.isEmpty)
        assertEquals(a, a.skipped(InterviewStep.DESTINATION, rentalBali))
    }

    @Test
    fun skipTheInterviewGoesToTheSummaryOrToW4FromJelajah() {
        assertNull(skipInterviewTarget(InterviewDoor.MY_TRIPS), "the summary")
        assertEquals(InterviewStep.TASTE, skipInterviewTarget(InterviewDoor.EXPLORE))
    }

    @Test
    fun backFromLayar1LeavesTheFlow() {
        assertNull(previousInterviewStep(InterviewAnswers().steps(), InterviewStep.DESTINATION))
    }

    @Test
    fun theLeaveDialogIsOnlyForAnAnsweredFlow() {
        assertFalse(InterviewAnswers().hasAnything())
        assertTrue(InterviewAnswers().withDestination(bandung, none).hasAnything())
        assertTrue(InterviewAnswers().withDatesUndecided(true).hasAnything())
        assertTrue(InterviewAnswers().toggleInterest(Interest.FOOD).hasAnything())
    }

    // ── summary and what is stored ──────────────────────────────────────────

    @Test
    fun aSkippedRowShowsAsNotFilledAndW3IsLeftOutForAnOwnVehicle() {
        val skipped = start()
        val rows = skipped.summaryRows().toMap()
        assertEquals(false, rows[SummaryRow.DEPARTURE])
        assertEquals(false, rows[SummaryRow.LOCAL_TRANSPORT], "W3 applies and was skipped: Belum diisi")
        assertEquals(false, rows[SummaryRow.TASTE])
        assertEquals(false, rows[SummaryRow.BUDGET], "an empty budget is not an error")
        assertEquals(true, rows[SummaryRow.NAME])
        assertEquals(true, rows[SummaryRow.DATES])

        val own = skipped.withOrigin(subang, none).withDeparture(DepartureMode.OWN_MOTORCYCLE, none)
        assertFalse(SummaryRow.LOCAL_TRANSPORT in own.summaryRows().map { it.first })
        assertEquals(true, own.summaryRows().toMap()[SummaryRow.DEPARTURE])
        assertEquals(
            listOf(SummaryRow.NAME, SummaryRow.DESTINATION, SummaryRow.DATES, SummaryRow.DEPARTURE, SummaryRow.TASTE, SummaryRow.BUDGET),
            own.summaryRows().map { it.first },
        )
    }

    @Test
    fun undatedTripsCountTheDatesRowAsFilled() {
        val a = InterviewAnswers().withDestination(bandung, none).withDatesUndecided(true)
        assertEquals(true, a.summaryRows().toMap()[SummaryRow.DATES])
    }

    @Test
    fun theMainTravelModeIsOnlyStoredWhenAnAnswerGaveIt() {
        assertNull(start().interviewMainTravelMode())
        assertEquals(MainTravelMode.MOTORCYCLE, start().withOrigin(subang, none).withDeparture(DepartureMode.OWN_MOTORCYCLE, none).interviewMainTravelMode())
        val tokyoTaxi = start(tokyo).withLocalTransport(LocalTransport.TAXI, none)
        assertEquals(MainTravelMode.CAR, tokyoTaxi.interviewMainTravelMode())
        assertEquals(MainTravelMode.PUBLIC_TRANSPORT, start(tokyo).withLocalTransport(LocalTransport.PUBLIC_TRANSPORT, none).interviewMainTravelMode())
    }
}
