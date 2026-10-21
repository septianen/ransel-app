package app.ransel.shared.domain

import app.ransel.shared.dto.RoundDto
import app.ransel.shared.dto.TallyDto
import kotlin.time.Instant

/*
 * Itinerary voting rules both sides need (docs/specs/voting-spec.md). The server owns the clock decisions
 * (it rejects late votes); the app uses the same functions to show the right screen without another request.
 */

/** Which screen a round belongs to at a moment. */
enum class RoundState {
    /** Voting is open. */
    OPEN,
    /** Past `closesAt`, the OWNER has not decided yet. */
    CLOSED,
    /** The OWNER decided; final. */
    DECIDED,
}

fun roundState(now: Instant, closesAt: Instant, decidedAt: Instant?): RoundState = when {
    decidedAt != null -> RoundState.DECIDED
    now < closesAt -> RoundState.OPEN
    else -> RoundState.CLOSED
}

fun RoundDto.state(now: Instant): RoundState = roundState(now, closesAt, decidedAt)

/** Whole hours left until [closesAt], rounded up ("sisa 23 jam" at 22 h 10 min); 0 when closed. */
fun hoursLeft(now: Instant, closesAt: Instant): Int {
    val minutes = (closesAt - now).inWholeMinutes
    return if (minutes <= 0) 0 else ((minutes + 59) / 60).toInt()
}

/** Index of the first card the caller has not voted on, or null when every card has a vote. */
fun RoundDto.firstUnvotedIndex(): Int? =
    places.indexOfFirst { it.item.placeId !in myVotes }.takeIf { it >= 0 }

/**
 * Result order: most likes first, then fewest skips, then the original card order. [order] is the round's card order
 * (placeIds); places without a tally row count as 0 likes.
 */
fun sortByLikes(order: List<String>, tally: List<TallyDto>): List<String> {
    val byPlace = tally.associateBy { it.placeId }
    return order.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<String>> { byPlace[it.value]?.likes ?: 0 }
                .thenBy { byPlace[it.value]?.skips ?: 0 }
                .thenBy { it.index },
        )
        .map { it.value }
}

/** Values of the `stage` notification param of VOTE_REQUESTED (OPEN, CLOSING) and VOTE_RESULT (READY, DECIDED). */
object VoteStage {
    const val OPEN = "OPEN"
    const val CLOSING = "CLOSING"
    const val READY = "READY"
    const val DECIDED = "DECIDED"
}
