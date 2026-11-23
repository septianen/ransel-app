package app.ransel.shared.domain

import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode

/*
 * Changing the travel answers of a trip that already exists ("Edit trip, Perjalanan", spec 1638556): the same questions and
 * rules as the interview (W1 to W3), applied to the trip's own answers. Pure and offline. An answer that is no longer valid is
 * reset (and said so), an own vehicle brings its own way of getting around, and the trip's main mode follows the answers.
 */

/** The trip's travel answers as the interview's answers, so the rules of W1 to W3 apply to them. */
fun TripDto.travelAnswers(): InterviewAnswers = InterviewAnswers(
    destination = InterviewDestination(destination, destinationPlace?.placeId, destinationPlace?.lat, destinationPlace?.lng, countryCode),
    startDate = startDate,
    endDate = endDate,
    datesUndecided = startDate == null || endDate == null,
    origin = origin,
    departure = departureMode,
    localTransport = localTransport,
)

/** The city facts (train, rentals, public transport) of the trip's own cities. */
fun TripDto.cityFacts(): InterviewCityFacts = interviewCityFacts(origin, destination)

/**
 * What a change of an answer did.
 *
 * @property answers the answers after the change (the rules of W1 to W3 applied).
 * @property mainTravelMode what the trip stores as its main mode (null = none: the default applies by itself).
 * @property departureReset the "Cara berangkat" that stopped being possible because the cities changed (a dialog says so);
 * null when it was kept, or dropped without a word (a trip abroad has no W2).
 * @property askLocalTransportNext the own vehicle was replaced by another way: "Cara keliling" opens right after saving.
 * @property ownVehicleNow the answer is now an own vehicle: getting around follows it and the rental suggestions are gone.
 */
data class TravelEdit(
    val answers: InterviewAnswers,
    val mainTravelMode: MainTravelMode?,
    val departureReset: DepartureMode?,
    val askLocalTransportNext: Boolean,
    val ownVehicleNow: Boolean,
)

private fun TripDto.edited(after: InterviewAnswers): TravelEdit {
    val before = travelAnswers()
    val overseas = after.isOverseas()
    val wasOwn = isOwnVehicle(before.departure)
    val isOwn = isOwnVehicle(after.departure)
    val main = when {
        // an own vehicle is the way of getting around
        isOwn -> mainTravelModeOf(after.departure, null, overseas)
        // the vehicle is gone and nothing was said about getting around yet: the default of the plan
        wasOwn -> mainTravelModeOf(null, after.localTransport, overseas)
        // getting around changed (or was dropped because the destination does not offer it)
        after.localTransport != before.localTransport -> mainTravelModeOf(after.departure, after.localTransport, overseas)
        // Bus to Kereta and the like: nothing about getting around changes
        else -> mainTravelMode
    }
    val dropped = before.departure != null && after.departure == null
    return TravelEdit(
        answers = after,
        mainTravelMode = main,
        departureReset = before.departure.takeIf { dropped && !overseas },
        askLocalTransportNext = wasOwn && !isOwn && after.departure != null,
        ownVehicleNow = isOwn && !wasOwn,
    )
}

/** "Berangkat dari": a new starting city (or none); the W2 choices are worked out again and an impossible answer is reset. */
fun TripDto.changeOrigin(origin: TripOriginDto?): TravelEdit {
    val facts = interviewCityFacts(origin, destination)
    return edited(travelAnswers().withOrigin(origin, facts))
}

/** "Cara berangkat": W2. An own vehicle drops the answer about getting around; another way asks for it next. */
fun TripDto.changeDeparture(mode: DepartureMode?): TravelEdit = edited(travelAnswers().withDeparture(mode, cityFacts()))

/** "Keliling di {destinasi}": W3. The main mode follows the answer. */
fun TripDto.changeLocalTransport(choice: LocalTransport?): TravelEdit = edited(travelAnswers().withLocalTransport(choice, cityFacts()))

/**
 * Another destination (the Destinasi field of Edit trip): the W2 choices are worked out again from the new distance and what the
 * new destination does not offer any more is reset, like for a new starting city.
 */
fun TripDto.changeDestination(destination: InterviewDestination): TravelEdit {
    val facts = interviewCityFacts(origin, destination.name)
    return edited(travelAnswers().withDestination(destination, facts))
}

/**
 * The request that stores [edit] on this trip, whole: every other field as it is, the four answers as the truth
 * (`replaceTravelAnswers`), so a reset really clears.
 */
fun TripDto.updateRequestFor(edit: TravelEdit): UpdateTripRequest = UpdateTripRequest(
    title = title, destination = destination, startDate = startDate, endDate = endDate, baseCurrency = baseCurrency,
    budgetMinor = budgetMinor, destinationPlace = destinationPlace, countryCode = countryCode, localCurrency = localCurrency,
    timeZone = timeZone,
    origin = edit.answers.origin,
    departureMode = edit.answers.departure,
    localTransport = edit.answers.localTransport,
    mainTravelMode = edit.mainTravelMode,
    replaceTravelAnswers = true,
)

/** The request that puts this trip's own answers back, whole: "Urungkan" after a change. */
fun TripDto.restoreRequest(): UpdateTripRequest = UpdateTripRequest(
    title = title, destination = destination, startDate = startDate, endDate = endDate, baseCurrency = baseCurrency,
    budgetMinor = budgetMinor, destinationPlace = destinationPlace, countryCode = countryCode, localCurrency = localCurrency,
    timeZone = timeZone,
    origin = origin, departureMode = departureMode, localTransport = localTransport, mainTravelMode = mainTravelMode,
    replaceTravelAnswers = true,
)

/** The rows of the section "Perjalanan" and what they show. */
enum class TravelRow { ORIGIN, DEPARTURE, LOCAL_TRANSPORT, TASTE }

/** The rows that apply to this trip: "Cara berangkat" is left out for a trip abroad (W2 is not asked). */
fun TripDto.travelRows(): List<TravelRow> = buildList {
    add(TravelRow.ORIGIN)
    if (!travelAnswers().isOverseas()) add(TravelRow.DEPARTURE)
    add(TravelRow.LOCAL_TRANSPORT)
    add(TravelRow.TASTE)
}

/** "Keliling" follows the own vehicle ("Motor · ikut kendaraanmu"): not a choice of its own, so not tappable. */
fun TripDto.localTransportFollowsVehicle(): Boolean = isOwnVehicle(departureMode) && !travelAnswers().isOverseas()
