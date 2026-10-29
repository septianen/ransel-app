package app.ransel.shared.domain

import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/*
 * Poll rules that must give the same answer in the form and on the server (PRD "Polling with templates",
 * "Poll edge cases"). The server applies them; the app uses them to enable buttons and to explain results.
 */

const val MAX_POLL_QUESTION = 140
const val MAX_POLL_OPTION_LABEL = 100
const val MAX_POLL_OPTIONS = 20
private const val MAX_URL = 2000

/** PRD "Date poll": a date poll asks about 2–5 ranges, none before today. */
const val DATE_POLL_MIN_OPTIONS = 2
const val DATE_POLL_MAX_OPTIONS = 5

/** The most options a poll of [kind] may hold (all of them together, also after adding later). */
fun maxPollOptions(kind: PollKind): Int = if (kind == PollKind.DATE) DATE_POLL_MAX_OPTIONS else MAX_POLL_OPTIONS

/**
 * The options a new DATE poll starts with: [DATE_POLL_MIN_OPTIONS]..[DATE_POLL_MAX_OPTIONS] ranges, none starting
 * before [today]. Field → code like `validatePollOption` (`options` = TOO_FEW / TOO_MANY, `options[i].startDate` =
 * BEFORE_TODAY). Each range itself is checked by `validatePollOption`. The app passes the device's today; the server
 * passes the earliest date anywhere on earth (UTC−12), so no user's today is refused, whatever their time zone.
 */
fun validateDatePollOptions(options: List<PollOptionInput>, today: LocalDate): Map<String, String> = buildMap {
    if (options.size < DATE_POLL_MIN_OPTIONS) put("options", "TOO_FEW")
    if (options.size > DATE_POLL_MAX_OPTIONS) put("options", "TOO_MANY")
    options.forEachIndexed { i, o -> o.startDate?.let { if (it < today) put("options[$i].startDate", "BEFORE_TODAY") } }
}

/** A tied poll reopens by itself if the creator has not picked a winner after this long, and then stays open this long. */
val POLL_TIE_REOPEN_AFTER: Duration = 24.hours
val POLL_TIE_REOPEN_FOR: Duration = 24.hours

/** Members who have not voted get a reminder this long before the poll closes. */
val POLL_REMINDER_BEFORE: Duration = 24.hours

/** "Tick every range you can make": a date poll takes several choices by default, the others one. */
fun defaultMultiChoice(kind: PollKind): Boolean = kind == PollKind.DATE

/** A budget poll is always anonymous (and cannot be made public): nobody shows what they can spend. */
fun isAnonymousForced(kind: PollKind): Boolean = kind == PollKind.BUDGET

fun effectiveAnonymous(kind: PollKind, requested: Boolean): Boolean = isAnonymousForced(kind) || requested

/** The switch can change until the first vote, then it is locked, so a poll never goes from anonymous to public. */
fun canChangeAnonymous(votesCast: Int): Boolean = votesCast == 0

/** What the winner does when the poll is decided with "apply winner" on. */
enum class PollAction {
    /** DATE: the winning range becomes the trip dates. */
    SET_TRIP_DATES,

    /** DESTINATION: the winning label becomes the trip destination. */
    SET_DESTINATION,

    /** PLACE: the place goes to "Belum dijadwalkan". */
    ADD_UNSCHEDULED_PLACE,

    /** FOOD: an activity on the poll's day and time. */
    ADD_ACTIVITY,

    /** STAY: an estimated expense of the price for the stay (stayEstimateMinor), left out of balances until paid. */
    ADD_ESTIMATE,

    /** BUDGET, TRANSPORT, CUSTOM: only the result is recorded. */
    NONE,
}

fun actionFor(kind: PollKind): PollAction = when (kind) {
    PollKind.DATE -> PollAction.SET_TRIP_DATES
    PollKind.DESTINATION -> PollAction.SET_DESTINATION
    PollKind.PLACE -> PollAction.ADD_UNSCHEDULED_PLACE
    PollKind.FOOD -> PollAction.ADD_ACTIVITY
    PollKind.STAY -> PollAction.ADD_ESTIMATE
    PollKind.BUDGET, PollKind.TRANSPORT, PollKind.CUSTOM -> PollAction.NONE
}

/** Field name → reason code, for ApiError.details. Empty = valid. */
fun validatePollQuestion(question: String): Map<String, String> = buildMap {
    val trimmed = question.trim()
    if (trimmed.isEmpty()) put("question", "REQUIRED")
    else if (trimmed.length > MAX_POLL_QUESTION) put("question", "TOO_LONG")
}

/** What each kind needs from an option. */
fun validatePollOption(kind: PollKind, option: PollOptionInput): Map<String, String> = buildMap {
    val label = option.label.trim()
    // A date range and a budget range name themselves.
    val selfNamed = kind == PollKind.DATE || kind == PollKind.BUDGET
    if (label.isEmpty() && !selfNamed) put("label", "REQUIRED")
    if (label.length > MAX_POLL_OPTION_LABEL) put("label", "TOO_LONG")
    when (kind) {
        PollKind.DATE -> {
            val start = option.startDate
            val end = option.endDate
            if (start == null) put("startDate", "REQUIRED")
            if (end == null) put("endDate", "REQUIRED")
            if (start != null && end != null && end < start) put("endDate", "MUST_BE_AFTER_START")
        }
        PollKind.BUDGET -> {
            val min = option.minMinor
            val max = option.maxMinor
            if (min == null) put("minMinor", "REQUIRED") else if (min < 0) put("minMinor", "MUST_NOT_BE_NEGATIVE")
            if (max == null) put("maxMinor", "REQUIRED")
            if (min != null && max != null && max < min) put("maxMinor", "MUST_BE_AT_LEAST_MIN")
        }
        PollKind.STAY, PollKind.FOOD -> {
            if (option.priceMinor != null && option.priceMinor!! < 0) put("priceMinor", "MUST_NOT_BE_NEGATIVE")
        }
        PollKind.PLACE, PollKind.DESTINATION, PollKind.TRANSPORT, PollKind.CUSTOM -> Unit
    }
    option.url?.takeIf { it.isNotBlank() }?.let { url ->
        val ok = (url.startsWith("http://") || url.startsWith("https://")) && url.length <= MAX_URL && url.none { it.isWhitespace() }
        if (!ok) put("url", "INVALID_URL")
    }
}

/** The label shown for an option: the typed one, or for a date or budget range the range itself. */
fun pollOptionLabel(kind: PollKind, option: PollOptionInput): String {
    val typed = option.label.trim()
    if (typed.isNotEmpty()) return typed
    return when (kind) {
        PollKind.DATE -> "${option.startDate} – ${option.endDate}"
        PollKind.BUDGET -> "${option.minMinor} – ${option.maxMinor}"
        else -> typed
    }
}

/**
 * Whether an edit changes what voters agreed to. Then the version goes up and the votes on that option are cleared;
 * a new picture alone changes nothing they voted on.
 */
fun optionChangesVotes(old: PollOptionInput, new: PollOptionInput): Boolean =
    old.label.trim() != new.label.trim() ||
        old.startDate != new.startDate || old.endDate != new.endDate ||
        old.placeId != new.placeId || old.url != new.url ||
        old.priceMinor != new.priceMinor || old.priceUnit != new.priceUnit ||
        old.minMinor != new.minMinor || old.maxMinor != new.maxMinor

sealed interface PollOutcome {
    /** Nobody voted: there is nothing to confirm. */
    data object NoVotes : PollOutcome

    data class Winner(val optionId: String, val votes: Int) : PollOutcome

    /** Two or more options share the most votes: the creator picks one, or the poll reopens. */
    data class Tie(val optionIds: List<String>, val votes: Int) : PollOutcome
}

/** [counts] are the votes that count per option id (options without votes may be missing or 0). */
fun pollOutcome(counts: Map<String, Int>): PollOutcome {
    val top = counts.values.maxOrNull() ?: return PollOutcome.NoVotes
    if (top <= 0) return PollOutcome.NoVotes
    val leaders = counts.filterValues { it == top }.keys.sorted()
    return if (leaders.size == 1) PollOutcome.Winner(leaders.single(), top) else PollOutcome.Tie(leaders, top)
}

/** A tied poll nobody decided reopens [POLL_TIE_REOPEN_AFTER] after it tied. */
fun shouldReopenTie(tiedAt: Instant, now: Instant): Boolean = now >= tiedAt + POLL_TIE_REOPEN_AFTER

/** True once voting is over for the clock, whatever the stored status says (the server settles the status on its next look). */
fun isPastClosing(closesAt: Instant, now: Instant): Boolean = now >= closesAt

/** Hours until a poll closes, rounded up; 0 once closed. For "sisa 5 jam". */
fun pollHoursLeft(now: Instant, closesAt: Instant): Int {
    val left = closesAt - now
    if (left <= Duration.ZERO) return 0
    val hours = left.inWholeHours
    return (if (left > hours.hours) hours + 1 else hours).toInt()
}
