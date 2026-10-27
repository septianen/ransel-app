package app.ransel.feature

import app.ransel.feature.group.VotingEntry
import app.ransel.feature.group.finishedVotings
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** "Riwayat voting" holds only what is over: confirmed polls and decided rounds, the latest ending first. */
class VotingListsTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    private fun poll(id: String, status: PollStatus, closes: Instant) = PollDto(
        id = id, tripId = "t", kind = PollKind.CUSTOM, question = id, createdByMemberId = "m", createdByName = "Ayu",
        createdAt = closes - 24.hours, closesAt = closes, status = status, memberCount = 2,
    )

    private fun round(id: String, closes: Instant, decided: Instant?) = RoundSummaryDto(
        id = id, tripId = "t", createdByUserId = "u", createdByName = "Ayu", createdAt = closes - 24.hours,
        closesAt = closes, decidedAt = decided, placeCount = 5, myVoteCount = 5,
    )

    @Test
    fun onlyFinishedVotingsAreHistory() {
        val polls = listOf(
            poll("open", PollStatus.OPEN, now + 2.hours),
            poll("waiting for the creator", PollStatus.CLOSED, now - 1.hours),
            poll("tied", PollStatus.TIED, now - 1.hours),
            poll("decided", PollStatus.DECIDED, now - 5.hours),
        )
        val rounds = listOf(
            round("voting", now + 3.hours, null),
            round("waiting for the owner", now - 2.hours, null),
            round("chosen", now - 10.hours, now - 3.hours),
        )
        val ids = finishedVotings(polls, rounds, now).map {
            when (it) {
                is VotingEntry.Poll -> it.poll.id
                is VotingEntry.Round -> it.round.id
            }
        }
        assertEquals(listOf("chosen", "decided"), ids, "decided 3 h ago comes before a poll that ended 5 h ago")
    }
}
