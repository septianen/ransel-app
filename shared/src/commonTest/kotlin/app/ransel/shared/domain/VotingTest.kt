package app.ransel.shared.domain

import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.dto.RoundDto
import app.ransel.shared.dto.RoundPlaceDto
import app.ransel.shared.dto.TallyDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.Vote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class VotingTest {
    private val created = Instant.parse("2026-10-21T02:00:00Z")
    private val closes = created + 24.hours

    @Test fun stateFollowsTheClockUntilDecided() {
        assertEquals(RoundState.OPEN, roundState(created, closes, null))
        assertEquals(RoundState.CLOSED, roundState(closes, closes, null))
        assertEquals(RoundState.DECIDED, roundState(created, closes, created + 1.hours))
    }

    @Test fun hoursLeftRoundsUp() {
        assertEquals(24, hoursLeft(created, closes))
        assertEquals(23, hoursLeft(created + 1.hours + 50.minutes, closes))
        assertEquals(1, hoursLeft(closes - 1.minutes, closes))
        assertEquals(0, hoursLeft(closes, closes))
        assertEquals(0, hoursLeft(closes + 3.hours, closes))
    }

    @Test fun firstUnvotedSkipsVotedCards() {
        val round = round(listOf("a", "b", "c"), mapOf("a" to Vote.LIKE, "c" to Vote.SKIP))
        assertEquals(1, round.firstUnvotedIndex())
        assertNull(round(listOf("a"), mapOf("a" to Vote.SKIP)).firstUnvotedIndex())
    }

    @Test fun likesThenFewerSkipsThenCardOrder() {
        val tally = listOf(
            TallyDto("a", likes = 1, skips = 3),
            TallyDto("b", likes = 3, skips = 1),
            TallyDto("c", likes = 1, skips = 1),
        )
        assertEquals(listOf("b", "c", "a", "d"), sortByLikes(listOf("a", "b", "c", "d"), tally))
        assertEquals(listOf("x", "y"), sortByLikes(listOf("x", "y"), emptyList()))
    }

    private fun round(ids: List<String>, votes: Map<String, Vote>) = RoundDto(
        id = "r", tripId = "t", createdByUserId = "u", createdByName = "Rina", createdAt = created, closesAt = closes,
        expiresAt = created + (24 * 7).hours, memberCount = 2, myVotes = votes,
        places = ids.map { RoundPlaceDto(Category.ATTRACTION, RecommendationItem(it, it, "", null, 60, 0.0, 0.0)) },
    )
}
