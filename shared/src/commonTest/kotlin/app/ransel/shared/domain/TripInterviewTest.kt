package app.ransel.shared.domain

import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripInterviewTest {
    private val subang = TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID")
    private val jakarta = TripOriginDto("Jakarta", -6.2088, 106.8456, countryCode = "ID")
    private val bandung = -6.9175 to 107.6191
    private val yogyakarta = -7.7956 to 110.3695
    private val bali = -8.6705 to 115.2126
    private val tokyo = 35.6762 to 139.6503

    private fun band(origin: TripOriginDto?, dest: Pair<Double, Double>?, country: String? = "ID") =
        departureBand(origin, dest?.first, dest?.second, country)

    // ── islands ─────────────────────────────────────────────────────────────

    @Test
    fun knownCitiesLandOnTheirIsland() {
        val expected = mapOf(
            "Jakarta" to (-6.2088 to 106.8456) to Island.JAWA,
            "Bandung" to bandung to Island.JAWA,
            "Yogyakarta" to yogyakarta to Island.JAWA,
            "Surabaya" to (-7.2575 to 112.7521) to Island.JAWA,
            "Sumenep (Madura)" to (-7.0167 to 113.8667) to Island.JAWA,
            "Banyuwangi" to (-8.2192 to 114.3691) to Island.JAWA,
            "Merak" to (-5.93 to 106.0) to Island.JAWA,
            "Denpasar" to bali to Island.BALI,
            "Ubud" to (-8.5069 to 115.2625) to Island.BALI,
            "Gilimanuk" to (-8.1667 to 114.4417) to Island.BALI,
            "Mataram" to (-8.5833 to 116.1167) to Island.LOMBOK,
            "Gili Trawangan" to (-8.35 to 116.03) to Island.LOMBOK,
            "Bima" to (-8.46 to 118.73) to Island.SUMBAWA,
            "Labuan Bajo" to (-8.4967 to 119.8878) to Island.FLORES,
            "Maumere" to (-8.62 to 122.21) to Island.FLORES,
            "Waingapu" to (-9.66 to 120.26) to Island.SUMBA,
            "Kupang" to (-10.1772 to 123.607) to Island.TIMOR,
            "Medan" to (3.5952 to 98.6722) to Island.SUMATERA,
            "Banda Aceh" to (5.55 to 95.32) to Island.SUMATERA,
            "Palembang" to (-2.9761 to 104.7754) to Island.SUMATERA,
            "Bandar Lampung" to (-5.45 to 105.2667) to Island.SUMATERA,
            "Bakauheni" to (-5.87 to 105.75) to Island.SUMATERA,
            "Pontianak" to (-0.0263 to 109.3425) to Island.KALIMANTAN,
            "Balikpapan" to (-1.2379 to 116.8529) to Island.KALIMANTAN,
            "Makassar" to (-5.1477 to 119.4327) to Island.SULAWESI,
            "Palu" to (-0.9 to 119.87) to Island.SULAWESI,
            "Kendari" to (-3.97 to 122.51) to Island.SULAWESI,
            "Manado" to (1.4748 to 124.8421) to Island.SULAWESI,
            "Gorontalo" to (0.5412 to 123.0595) to Island.SULAWESI,
            "Jayapura" to (-2.5337 to 140.7181) to Island.PAPUA,
            "Sorong" to (-0.88 to 131.26) to Island.PAPUA,
            "Ambon" to (-3.6954 to 128.1814) to Island.SERAM,
        )
        expected.forEach { (place, island) ->
            val (name, point) = place
            assertEquals(island, islandOf(point.first, point.second), name)
        }
    }

    @Test
    fun smallIslandsAndSeaAreUnknownNotWrong() {
        assertNull(islandOf(1.0456, 104.0305), "Batam")
        assertNull(islandOf(-2.74, 107.63), "Belitung")
        assertNull(islandOf(0.79, 127.38), "Ternate")
        assertNull(islandOf(35.6762, 139.6503), "Tokyo")
        assertNull(islandOf(-5.0, 110.0), "Java Sea")
    }

    // ── W2 bands and options ────────────────────────────────────────────────

    @Test
    fun subangToBandungIsShortAndOwnMotorcycleIsOnTop() {
        assertEquals(DepartureBand.SHORT, band(subang, bandung))
        val options = departureOptions(DepartureBand.SHORT, trainBetweenCities = true)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, options.first())
        assertEquals(listOf(DepartureMode.OWN_MOTORCYCLE, DepartureMode.OWN_CAR, DepartureMode.TRAVEL_SHUTTLE, DepartureMode.BUS, DepartureMode.TRAIN), options)
    }

    @Test
    fun theTrainOnlyComesWhenBothCitiesHaveAStation() {
        listOf(DepartureBand.SHORT, DepartureBand.MEDIUM, DepartureBand.LONG).forEach {
            assertTrue(DepartureMode.TRAIN !in departureOptions(it, trainBetweenCities = false), "$it")
            assertTrue(DepartureMode.TRAIN in departureOptions(it, trainBetweenCities = true), "$it")
        }
        assertTrue(DepartureMode.TRAIN !in departureOptions(DepartureBand.OTHER_ISLAND, trainBetweenCities = true))
    }

    @Test
    fun jakartaToYogyakartaIsMediumTrainFirstAfterTheCar() {
        assertEquals(DepartureBand.MEDIUM, band(jakarta, yogyakarta))
        assertEquals(
            listOf(DepartureMode.OWN_CAR, DepartureMode.TRAIN, DepartureMode.BUS, DepartureMode.PLANE, DepartureMode.TRAVEL_SHUTTLE),
            departureOptions(DepartureBand.MEDIUM, trainBetweenCities = true),
        )
    }

    @Test
    fun anotherIslandAsksForThePlaneFirstAtAnyDistance() {
        assertEquals(DepartureBand.OTHER_ISLAND, band(jakarta, bali))
        assertEquals(
            listOf(DepartureMode.PLANE, DepartureMode.FERRY, DepartureMode.OWN_CAR),
            departureOptions(DepartureBand.OTHER_ISLAND, trainBetweenCities = true),
        )
        // Surabaya to Denpasar is about 330 km on the ground: still another island
        assertEquals(DepartureBand.OTHER_ISLAND, band(TripOriginDto("Surabaya", -7.2575, 112.7521), bali))
        // Bali to Lombok is under 100 km
        assertEquals(DepartureBand.OTHER_ISLAND, band(TripOriginDto("Denpasar", bali.first, bali.second), -8.5833 to 116.1167))
    }

    @Test
    fun overseasEitherWayAsksNoQuestion() {
        assertEquals(DepartureBand.OVERSEAS, band(jakarta, tokyo, country = "JP"))
        assertEquals(DepartureBand.OVERSEAS, band(TripOriginDto("Singapore", 1.35, 103.82, countryCode = "SG"), bali))
        assertEquals(DepartureBand.OVERSEAS, band(null, tokyo, country = "JP"))
        assertEquals(emptyList(), departureOptions(DepartureBand.OVERSEAS, trainBetweenCities = true))
    }

    @Test
    fun oneIslandBeyondSevenHundredKilometresIsLongAndStartsWithThePlane() {
        // Medan to Bandar Lampung: well over 700 km of road on Sumatra
        val medan = TripOriginDto("Medan", 3.5952, 98.6722, countryCode = "ID")
        assertEquals(DepartureBand.LONG, band(medan, -5.45 to 105.2667))
        assertEquals(DepartureMode.PLANE, departureOptions(DepartureBand.LONG, false).first())
    }

    @Test
    fun withoutAnOriginOrADestinationPointTheDistanceIsUnknownAndEverythingIsOffered() {
        assertEquals(DepartureBand.UNKNOWN, band(null, bandung))
        assertEquals(DepartureBand.UNKNOWN, band(subang, null))
        val expected = listOf(
            DepartureMode.OWN_MOTORCYCLE, DepartureMode.OWN_CAR, DepartureMode.TRAVEL_SHUTTLE, DepartureMode.BUS,
            DepartureMode.TRAIN, DepartureMode.PLANE, DepartureMode.FERRY,
        )
        assertEquals(expected, departureOptions(DepartureBand.UNKNOWN, trainBetweenCities = false), "the whole list in the default order")
        assertEquals(expected, departureOptions(DepartureBand.UNKNOWN, trainBetweenCities = true))
    }

    @Test
    fun anOriginTypedAsTextHasNoDistance() {
        assertEquals(DepartureBand.UNKNOWN, band(TripOriginDto("Desa Sukamaju"), bandung))
    }

    @Test
    fun withAnUnknownIslandTheDistanceDecides() {
        val batam = TripOriginDto("Batam", 1.0456, 104.0305, countryCode = "ID")
        // Batam to Pekanbaru, same island or not, it is under 700 km: distance says MEDIUM or SHORT
        assertTrue(band(batam, 0.5071 to 101.4478) in setOf(DepartureBand.SHORT, DepartureBand.MEDIUM))
    }

    @Test
    fun roadDistanceIsTheStraightLineTimesOnePointThree() {
        val straight = greatCircleKm(jakarta.lat!!, jakarta.lng!!, yogyakarta.first, yogyakarta.second)
        assertEquals(straight * 1.3, roadKm(jakarta.lat!!, jakarta.lng!!, yogyakarta.first, yogyakarta.second), 1e-9)
        assertEquals(0.0, greatCircleKm(1.0, 2.0, 1.0, 2.0), 1e-9)
        assertEquals(111.19, greatCircleKm(0.0, 0.0, 1.0, 0.0), 0.1)
    }

    // ── W3 ──────────────────────────────────────────────────────────────────

    @Test
    fun w3IsSkippedOnlyForAnOwnVehicle() {
        assertEquals(false, asksLocalTransport(DepartureMode.OWN_MOTORCYCLE))
        assertEquals(false, asksLocalTransport(DepartureMode.OWN_CAR))
        assertEquals(true, asksLocalTransport(DepartureMode.PLANE))
        assertEquals(true, asksLocalTransport(null), "W2 skipped or never asked (overseas)")
    }

    @Test
    fun w3ChoicesFollowTheDestination() {
        assertEquals(
            listOf(LocalTransport.RENT_MOTORCYCLE, LocalTransport.RIDE_HAILING, LocalTransport.RENT_CAR, LocalTransport.UNKNOWN),
            localTransportOptions(overseas = false, motorcycleRentalCity = true, publicTransportCity = false),
        )
        assertEquals(
            listOf(LocalTransport.PUBLIC_TRANSPORT, LocalTransport.RIDE_HAILING, LocalTransport.UNKNOWN),
            localTransportOptions(overseas = false, motorcycleRentalCity = false, publicTransportCity = true),
        )
        assertEquals(
            listOf(LocalTransport.PUBLIC_TRANSPORT, LocalTransport.TAXI, LocalTransport.RENT_CAR, LocalTransport.UNKNOWN),
            localTransportOptions(overseas = true, motorcycleRentalCity = true, publicTransportCity = true),
            "overseas wins over the Indonesian city lists",
        )
        assertEquals(
            listOf(LocalTransport.RIDE_HAILING, LocalTransport.RENT_CAR, LocalTransport.RENT_MOTORCYCLE, LocalTransport.UNKNOWN),
            localTransportOptions(overseas = false, motorcycleRentalCity = false, publicTransportCity = false),
        )
        val both = localTransportOptions(overseas = false, motorcycleRentalCity = true, publicTransportCity = true)
        assertEquals(LocalTransport.RENT_MOTORCYCLE, both[0])
        assertEquals(LocalTransport.PUBLIC_TRANSPORT, both[1])
    }

    @Test
    fun everyW3ListEndsWithNotKnownYet() {
        listOf(true, false).forEach { overseas ->
            listOf(true, false).forEach { rental ->
                listOf(true, false).forEach { transit ->
                    assertEquals(LocalTransport.UNKNOWN, localTransportOptions(overseas, rental, transit).last())
                }
            }
        }
    }

    @Test
    fun anOwnVehicleBlocksTheRentalSuggestion() {
        assertEquals(false, allowsRentalSuggestion(DepartureMode.OWN_MOTORCYCLE))
        assertEquals(false, allowsRentalSuggestion(DepartureMode.OWN_CAR))
        assertEquals(true, allowsRentalSuggestion(DepartureMode.PLANE))
        assertEquals(true, allowsRentalSuggestion(null))
    }

    // ── main travel mode ────────────────────────────────────────────────────

    @Test
    fun mainTravelModeFollowsTheAnswers() {
        assertEquals(MainTravelMode.MOTORCYCLE, mainTravelModeOf(DepartureMode.OWN_MOTORCYCLE, null, overseas = false))
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(DepartureMode.OWN_CAR, null, overseas = false))
        // an own vehicle decides even if a later answer says otherwise
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(DepartureMode.OWN_CAR, LocalTransport.RENT_MOTORCYCLE, overseas = false))
        assertEquals(MainTravelMode.MOTORCYCLE, mainTravelModeOf(DepartureMode.PLANE, LocalTransport.RENT_MOTORCYCLE, false))
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(DepartureMode.PLANE, LocalTransport.RENT_CAR, false))
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(null, LocalTransport.TAXI, true))
        assertEquals(MainTravelMode.PUBLIC_TRANSPORT, mainTravelModeOf(null, LocalTransport.PUBLIC_TRANSPORT, true))
    }

    @Test
    fun withoutAnAnswerTheDefaultIsMotorInIndonesiaAndCarAbroad() {
        assertEquals(MainTravelMode.MOTORCYCLE, mainTravelModeOf(null, null, overseas = false))
        assertEquals(MainTravelMode.MOTORCYCLE, mainTravelModeOf(DepartureMode.PLANE, LocalTransport.UNKNOWN, overseas = false))
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(null, null, overseas = true))
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(null, LocalTransport.UNKNOWN, overseas = true))
    }

    @Test
    fun rideHailingIsAMotorbikeInIndonesiaAndACarAbroad() {
        assertEquals(MainTravelMode.MOTORCYCLE, mainTravelModeOf(DepartureMode.PLANE, LocalTransport.RIDE_HAILING, overseas = false))
        assertEquals(MainTravelMode.CAR, mainTravelModeOf(null, LocalTransport.RIDE_HAILING, overseas = true))
    }

    // ── the "X dari Y" indicator ────────────────────────────────────────────

    /** The indicator on each screen the user sees, given the answers known at that moment. */
    private fun walk(overseas: Boolean, departure: DepartureMode?): List<String> =
        InterviewStep.entries
            .filter { !(it == InterviewStep.DEPARTURE && overseas) && !(it == InterviewStep.LOCAL_TRANSPORT && isOwnVehicle(departure)) }
            .map { step ->
                // W2 is answered on its own screen: before that the answer is not known yet
                val known = departure.takeIf { step.ordinal > InterviewStep.DEPARTURE.ordinal }
                val progress = interviewProgress(interviewSteps(overseas, known), step)
                "${progress.position} dari ${progress.total}"
            }

    @Test
    fun subangToBandungByOwnMotorcycleReads1of5Then3of5Then4of4() {
        assertEquals(listOf("1 dari 5", "2 dari 5", "3 dari 5", "4 dari 4"), walk(overseas = false, DepartureMode.OWN_MOTORCYCLE))
        // the moment W2 is picked its own indicator drops to 3 dari 4
        assertEquals(InterviewProgress(3, 4), interviewProgress(interviewSteps(false, DepartureMode.OWN_MOTORCYCLE), InterviewStep.DEPARTURE))
    }

    @Test
    fun aFlightReadsFiveOfFive() {
        assertEquals(listOf("1 dari 5", "2 dari 5", "3 dari 5", "4 dari 5", "5 dari 5"), walk(overseas = false, DepartureMode.PLANE))
    }

    @Test
    fun overseasIsFourFromLayar1() {
        assertEquals(listOf("1 dari 4", "2 dari 4", "3 dari 4", "4 dari 4"), walk(overseas = true, null))
    }

    @Test
    fun theTotalComesBackUpWhenTheUserChangesTheAnswer() {
        val own = interviewSteps(overseas = false, DepartureMode.OWN_CAR)
        assertEquals(InterviewProgress(4, 4), interviewProgress(own, InterviewStep.TASTE))
        // back to W2, picks the plane: W3 is a step again and W4 is 5 dari 5
        val plane = interviewSteps(overseas = false, DepartureMode.PLANE)
        assertEquals(InterviewProgress(4, 5), interviewProgress(plane, InterviewStep.LOCAL_TRANSPORT))
        assertEquals(InterviewProgress(5, 5), interviewProgress(plane, InterviewStep.TASTE))
    }

    @Test
    fun aStepThatIsGoneIsShownAsTheLastOne() {
        val own = interviewSteps(overseas = false, DepartureMode.OWN_CAR)
        assertEquals(InterviewProgress(4, 4), interviewProgress(own, InterviewStep.LOCAL_TRANSPORT))
    }

    // ── validation ──────────────────────────────────────────────────────────

    @Test
    fun anOriginIsCheckedLikeADestinationPlace() {
        assertEquals(emptyMap(), validateTripOrigin(null))
        assertEquals(emptyMap(), validateTripOrigin(subang))
        assertEquals(mapOf("origin.name" to "REQUIRED"), validateTripOrigin(subang.copy(name = " ")))
        assertEquals(mapOf("origin.name" to "TOO_LONG"), validateTripOrigin(subang.copy(name = "x".repeat(TRIP_ORIGIN_NAME_MAX + 1))))
        assertEquals(mapOf("origin.coordinates" to "INVALID_COORDINATES"), validateTripOrigin(subang.copy(lat = 91.0)))
        assertEquals(mapOf("origin.coordinates" to "INVALID_COORDINATES"), validateTripOrigin(subang.copy(lng = -181.0)))
        assertEquals(mapOf("origin.countryCode" to "INVALID_COUNTRY"), validateTripOrigin(subang.copy(countryCode = "IDN")))
        assertEquals(mapOf("origin.placeId" to "INVALID_PLACE_ID"), validateTripOrigin(subang.copy(placeId = " ")))
        // a city typed as text has no point; a point that is half there is wrong
        assertEquals(emptyMap(), validateTripOrigin(TripOriginDto("Desa Sukamaju")))
        assertEquals(mapOf("origin.coordinates" to "INVALID_COORDINATES"), validateTripOrigin(subang.copy(lng = null)))
        assertEquals(mapOf("origin.coordinates" to "INVALID_COORDINATES"), validateTripOrigin(subang.copy(lat = null)))
    }
}
