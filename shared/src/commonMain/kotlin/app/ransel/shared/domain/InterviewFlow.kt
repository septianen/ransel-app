package app.ransel.shared.domain

import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import app.ransel.shared.model.TravelStyle
import kotlinx.datetime.LocalDate

/*
 * The state of the "Buat trip" interview as pure functions (spec "Wawancara Buat trip" 16 Nov 2026): what the answers
 * are, which steps apply, when Lanjut is on, what Lewati clears, where Back and "Lewati wawancara" go. No Compose, no
 * ViewModel: the screens only draw this and call it, so every rule is tested on the JVM and the same everywhere.
 */

/** Which entrance the user came through: the + of Trip saya, or "Trip baru" / "Buat trip dan cari" of Jelajah. */
enum class InterviewDoor { MY_TRIPS, EXPLORE }

/**
 * The destination of Layar 1. [lat], [lng] are null for a destination typed as text (offline, "Pakai “kata” sebagai
 * teks"): the distance is then unknown. [countryCode] is null while unknown (treated as Indonesia).
 */
data class InterviewDestination(
    val name: String,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val countryCode: String? = null,
)

/** The W4 answers: the core interests, the destination's own ("Khas …", free strings from the AI) and the style. */
data class InterviewTaste(
    val interests: Set<Interest> = emptySet(),
    val extraInterests: Set<String> = emptySet(),
    val style: TravelStyle? = null,
) {
    val isEmpty: Boolean get() = interests.isEmpty() && extraInterests.isEmpty() && style == null

    /** Jelajah needs at least one interest, the style alone is not enough. */
    val hasInterest: Boolean get() = interests.isNotEmpty() || extraInterests.isNotEmpty()
}

/** Everything the user has answered so far. A null is "not answered or skipped"; Lewati never stores anything. */
data class InterviewAnswers(
    val destination: InterviewDestination? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** "Tanggal belum pasti". */
    val datesUndecided: Boolean = false,
    val origin: TripOriginDto? = null,
    val departure: DepartureMode? = null,
    val localTransport: LocalTransport? = null,
    val taste: InterviewTaste = InterviewTaste(),
) {
    val hasDates: Boolean get() = startDate != null && endDate != null
}

/**
 * What Karung's city lists say about this trip: both cities have a train station, the destination rents motorbikes, the
 * destination has public transport on Google Maps. The caller looks them up; false when unknown.
 */
data class InterviewCityFacts(
    val trainBetweenCities: Boolean = false,
    val motorcycleRentalCity: Boolean = false,
    val publicTransportCity: Boolean = false,
)

/** The trip leaves Indonesia or ends outside it: W2 is not asked. */
fun InterviewAnswers.isOverseas(): Boolean = isOverseas(destination?.countryCode) || isOverseas(origin?.countryCode)

/** The steps that apply to these answers. */
fun InterviewAnswers.steps(): List<InterviewStep> = interviewSteps(isOverseas(), departure)

/** The indicator for [step] (Y is recomputed from the current answers). */
fun InterviewAnswers.progress(step: InterviewStep): InterviewProgress = interviewProgress(steps(), step)

/** How far the origin is from the destination; [DepartureBand.UNKNOWN] when either has no point (the full W2 list). */
fun InterviewAnswers.departureBand(): DepartureBand =
    departureBand(origin, destination?.lat, destination?.lng, destination?.countryCode)

/** The W2 choices for these answers, first on top. */
fun InterviewAnswers.departureOptions(facts: InterviewCityFacts): List<DepartureMode> =
    departureOptions(departureBand(), facts.trainBetweenCities)

/** The W3 choices for these answers, first on top. */
fun InterviewAnswers.localTransportOptions(facts: InterviewCityFacts): List<LocalTransport> =
    localTransportOptions(isOverseas(), facts.motorcycleRentalCity, facts.publicTransportCity)

/** "{asal} → {tujuan} · ±{N} km" of W2: the road distance rounded to whole km, null when it is not known or the trip is abroad. */
fun InterviewAnswers.roadDistanceKm(): Int? {
    val fromLat = origin?.lat ?: return null
    val fromLng = origin.lng ?: return null
    val lat = destination?.lat ?: return null
    val lng = destination.lng ?: return null
    if (isOverseas()) return null
    return roadKm(fromLat, fromLng, lat, lng).let { kotlin.math.round(it).toInt() }
}

// ── Changing an answer ──────────────────────────────────────────────────────

/**
 * Makes the answers agree with each other after any change: a trip abroad has no W2 answer; an answer that is no longer
 * among the choices (the origin changed from Subang to Jakarta and the motorbike is gone) is dropped; an own vehicle has no
 * W3 answer (spec: "Jawaban W3 yang sudah ada dibuang bila W3 hilang"); a W3 answer that the destination no longer offers is
 * dropped. Call it after every change.
 */
fun InterviewAnswers.reconciled(facts: InterviewCityFacts): InterviewAnswers {
    var result = this
    if (result.isOverseas()) {
        result = result.copy(departure = null)
    } else {
        val options = result.departureOptions(facts)
        if (result.departure != null && result.departure !in options) result = result.copy(departure = null)
    }
    if (isOwnVehicle(result.departure)) {
        result = result.copy(localTransport = null)
    } else if (result.localTransport != null && result.localTransport !in result.localTransportOptions(facts)) {
        result = result.copy(localTransport = null)
    }
    return result
}

/** Layar 1: a new destination clears what was chosen for the old one (its "Khas …" chips); the dates are kept. */
fun InterviewAnswers.withDestination(destination: InterviewDestination?, facts: InterviewCityFacts): InterviewAnswers {
    val changed = this.destination?.name != destination?.name || this.destination?.placeId != destination?.placeId
    val next = copy(destination = destination, taste = if (changed) taste.copy(extraInterests = emptySet()) else taste)
    return next.reconciled(facts)
}

/** Layar 1: a range of dates turns "Tanggal belum pasti" off, and the switch clears the range (the two are exclusive). */
fun InterviewAnswers.withDates(start: LocalDate?, end: LocalDate?): InterviewAnswers =
    copy(startDate = start, endDate = end, datesUndecided = if (start != null && end != null) false else datesUndecided)

fun InterviewAnswers.withDatesUndecided(undecided: Boolean): InterviewAnswers =
    if (undecided) copy(datesUndecided = true, startDate = null, endDate = null) else copy(datesUndecided = false)

fun InterviewAnswers.withOrigin(origin: TripOriginDto?, facts: InterviewCityFacts): InterviewAnswers =
    copy(origin = origin).reconciled(facts)

fun InterviewAnswers.withDeparture(mode: DepartureMode?, facts: InterviewCityFacts): InterviewAnswers =
    copy(departure = mode).reconciled(facts)

fun InterviewAnswers.withLocalTransport(choice: LocalTransport?, facts: InterviewCityFacts): InterviewAnswers =
    copy(localTransport = choice).reconciled(facts)

/** A chip of W4 switches on and off; picking the style replaces the one before. */
fun InterviewAnswers.toggleInterest(interest: Interest): InterviewAnswers =
    copy(taste = taste.copy(interests = taste.interests.toggle(interest)))

fun InterviewAnswers.toggleExtraInterest(label: String): InterviewAnswers =
    copy(taste = taste.copy(extraInterests = taste.extraInterests.toggle(label)))

fun InterviewAnswers.withStyle(style: TravelStyle?): InterviewAnswers = copy(taste = taste.copy(style = style))

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

// ── Continue and skip ───────────────────────────────────────────────────────

/**
 * Lanjut is on when the screen has an answer. Layar 1 needs a destination and (dates or "Tanggal belum pasti"). W4 from
 * the My trips door takes any pick; from Jelajah it needs an interest (the search cannot run without one), the style alone
 * is not enough.
 */
fun canContinue(door: InterviewDoor, step: InterviewStep, answers: InterviewAnswers): Boolean = when (step) {
    InterviewStep.DESTINATION -> answers.destination != null && (answers.hasDates || answers.datesUndecided)
    InterviewStep.ORIGIN -> answers.origin != null
    InterviewStep.DEPARTURE -> answers.departure != null
    InterviewStep.LOCAL_TRANSPORT -> answers.localTransport != null
    InterviewStep.TASTE -> if (door == InterviewDoor.EXPLORE) answers.taste.hasInterest else !answers.taste.isEmpty
}

/** Layar 1 has no Lewati (the destination is required); W4 from Jelajah has none either ("Pilih minimal satu"). */
fun canSkip(door: InterviewDoor, step: InterviewStep): Boolean = when (step) {
    InterviewStep.DESTINATION -> false
    InterviewStep.TASTE -> door == InterviewDoor.MY_TRIPS
    else -> true
}

/** "Lewati wawancara" is a link on W1 only. */
fun canSkipInterview(step: InterviewStep): Boolean = step == InterviewStep.ORIGIN

/** Lewati stores nothing: it clears what is on that screen. */
fun InterviewAnswers.skipped(step: InterviewStep, facts: InterviewCityFacts): InterviewAnswers = when (step) {
    InterviewStep.DESTINATION -> this
    InterviewStep.ORIGIN -> withOrigin(null, facts)
    InterviewStep.DEPARTURE -> withDeparture(null, facts)
    InterviewStep.LOCAL_TRANSPORT -> withLocalTransport(null, facts)
    InterviewStep.TASTE -> copy(taste = InterviewTaste())
}

/** The step after [current] among [steps]; null = the summary. */
fun nextInterviewStep(steps: List<InterviewStep>, current: InterviewStep): InterviewStep? =
    steps.getOrNull(steps.indexOf(current) + 1).takeIf { steps.indexOf(current) >= 0 }

/** The step before [current]; null from Layar 1 = leave the flow (with the dialog when something was filled in). */
fun previousInterviewStep(steps: List<InterviewStep>, current: InterviewStep): InterviewStep? =
    steps.indexOf(current).let { if (it <= 0) null else steps[it - 1] }

/**
 * Where "Lewati wawancara" on W1 goes: the summary from My trips, W4 from Jelajah (the search needs an interest). The
 * answers of the questions skipped are not stored. Null = the summary.
 */
fun skipInterviewTarget(door: InterviewDoor): InterviewStep? = if (door == InterviewDoor.EXPLORE) InterviewStep.TASTE else null

/** "Keluar dari Buat trip?" is asked only when something was filled in on Layar 1 or after it. */
fun InterviewAnswers.hasAnything(): Boolean =
    destination != null || hasDates || datesUndecided || origin != null || departure != null || localTransport != null || !taste.isEmpty

// ── Summary ─────────────────────────────────────────────────────────────────

/** The rows of the summary in order. */
enum class SummaryRow { NAME, DESTINATION, DATES, DEPARTURE, LOCAL_TRANSPORT, TASTE, BUDGET }

/**
 * The rows to draw and whether each is filled (a row not filled shows "Belum diisi · Isi"). The name always has "Trip ke
 * {destinasi}", the destination and the dates are required, so they count as filled when the flow got here; "Keliling" is
 * left out when W3 was not asked (an own vehicle); Budget is optional and an empty one is not an error.
 */
fun InterviewAnswers.summaryRows(): List<Pair<SummaryRow, Boolean>> = buildList {
    add(SummaryRow.NAME to true)
    add(SummaryRow.DESTINATION to (destination != null))
    add(SummaryRow.DATES to (hasDates || datesUndecided))
    add(SummaryRow.DEPARTURE to (departure != null || origin != null))
    if (asksLocalTransport(departure) || localTransport != null) add(SummaryRow.LOCAL_TRANSPORT to (localTransport != null))
    add(SummaryRow.TASTE to !taste.isEmpty)
    add(SummaryRow.BUDGET to false)
}

/**
 * The main travel mode to store with the trip, or null when neither W2 nor W3 was answered (the default for the
 * destination then applies by itself, see [mainTravelModeOf]).
 */
fun InterviewAnswers.interviewMainTravelMode(): MainTravelMode? =
    if (departure == null && localTransport == null) null else mainTravelModeOf(departure, localTransport, isOverseas())
