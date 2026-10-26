package app.ransel.shared.domain

import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.model.PollKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PollsTest {
    private fun option(label: String = "x", block: PollOptionInput.() -> PollOptionInput = { this }) = PollOptionInput("o", label).block()

    @Test fun onlyDateTakesSeveralChoicesAndOnlyBudgetIsAlwaysAnonymous() {
        assertTrue(defaultMultiChoice(PollKind.DATE))
        assertFalse(PollKind.entries.filter { it != PollKind.DATE }.any(::defaultMultiChoice))
        assertTrue(PollKind.entries.filter(::isAnonymousForced) == listOf(PollKind.BUDGET))
        assertTrue(effectiveAnonymous(PollKind.BUDGET, false))
        assertFalse(effectiveAnonymous(PollKind.FOOD, false))
        assertTrue(effectiveAnonymous(PollKind.FOOD, true))
    }

    @Test fun theSwitchIsLockedAfterTheFirstVote() {
        assertTrue(canChangeAnonymous(0))
        assertFalse(canChangeAnonymous(1))
    }

    @Test fun everyKindHasItsAction() {
        assertEquals(PollAction.SET_TRIP_DATES, actionFor(PollKind.DATE))
        assertEquals(PollAction.SET_DESTINATION, actionFor(PollKind.DESTINATION))
        assertEquals(PollAction.ADD_UNSCHEDULED_PLACE, actionFor(PollKind.PLACE))
        assertEquals(PollAction.ADD_ACTIVITY, actionFor(PollKind.FOOD))
        assertEquals(PollAction.ADD_ESTIMATE, actionFor(PollKind.STAY))
        listOf(PollKind.BUDGET, PollKind.TRANSPORT, PollKind.CUSTOM).forEach { assertEquals(PollAction.NONE, actionFor(it)) }
    }

    @Test fun questionNeedsTextOfSensibleLength() {
        assertEquals(emptyMap(), validatePollQuestion("Kapan berangkat?"))
        assertEquals(mapOf("question" to "REQUIRED"), validatePollQuestion("  "))
        assertEquals(mapOf("question" to "TOO_LONG"), validatePollQuestion("a".repeat(MAX_POLL_QUESTION + 1)))
    }

    @Test fun dateOptionsNeedAnOrderedRange() {
        val a = LocalDate(2026, 11, 20)
        val b = LocalDate(2026, 11, 23)
        assertEquals(emptyMap(), validatePollOption(PollKind.DATE, option("") { copy(startDate = a, endDate = b) }))
        assertEquals(mapOf("endDate" to "MUST_BE_AFTER_START"), validatePollOption(PollKind.DATE, option("") { copy(startDate = b, endDate = a) }))
        assertEquals(mapOf("startDate" to "REQUIRED", "endDate" to "REQUIRED"), validatePollOption(PollKind.DATE, option("")))
    }

    @Test fun budgetOptionsNeedAMinAndAMax() {
        assertEquals(emptyMap(), validatePollOption(PollKind.BUDGET, option("") { copy(minMinor = 500_000, maxMinor = 1_000_000) }))
        assertEquals(mapOf("maxMinor" to "MUST_BE_AT_LEAST_MIN"), validatePollOption(PollKind.BUDGET, option("") { copy(minMinor = 5, maxMinor = 1) }))
        assertEquals(mapOf("minMinor" to "REQUIRED", "maxMinor" to "REQUIRED"), validatePollOption(PollKind.BUDGET, option("")))
    }

    @Test fun otherKindsNeedALabelAndSaneLinksAndPrices() {
        assertEquals(mapOf("label" to "REQUIRED"), validatePollOption(PollKind.PLACE, option(" ")))
        assertEquals(emptyMap(), validatePollOption(PollKind.STAY, option("Villa Ubud") { copy(priceMinor = 750_000, url = "https://booking.example/villa") }))
        assertEquals(mapOf("priceMinor" to "MUST_NOT_BE_NEGATIVE"), validatePollOption(PollKind.STAY, option("Villa") { copy(priceMinor = -1) }))
        assertEquals(mapOf("url" to "INVALID_URL"), validatePollOption(PollKind.STAY, option("Villa") { copy(url = "javascript:alert(1)") }))
        assertEquals(mapOf("url" to "INVALID_URL"), validatePollOption(PollKind.STAY, option("Villa") { copy(url = "https://a b") }))
        assertEquals(mapOf("label" to "TOO_LONG"), validatePollOption(PollKind.CUSTOM, option("a".repeat(MAX_POLL_OPTION_LABEL + 1))))
    }

    @Test fun rangesNameThemselves() {
        val a = LocalDate(2026, 11, 20)
        val b = LocalDate(2026, 11, 23)
        assertEquals("2026-11-20 – 2026-11-23", pollOptionLabel(PollKind.DATE, option("") { copy(startDate = a, endDate = b) }))
        assertEquals("Sabtu", pollOptionLabel(PollKind.DATE, option("Sabtu") { copy(startDate = a, endDate = b) }))
        assertEquals("500000 – 1000000", pollOptionLabel(PollKind.BUDGET, option("") { copy(minMinor = 500_000, maxMinor = 1_000_000) }))
    }

    @Test fun editingWhatPeopleVotedOnClearsTheirVotes() {
        val before = option("Villa Ubud") { copy(priceMinor = 750_000, imageUrl = "a.jpg") }
        assertFalse(optionChangesVotes(before, before.copy(imageUrl = "b.jpg")))
        assertFalse(optionChangesVotes(before, before.copy(label = " Villa Ubud ")))
        assertTrue(optionChangesVotes(before, before.copy(priceMinor = 800_000)))
        assertTrue(optionChangesVotes(before, before.copy(label = "Villa Seminyak")))
        assertTrue(optionChangesVotes(before, before.copy(url = "https://x.example")))
    }

    @Test fun outcomeIsAWinnerATieOrNothing() {
        assertEquals(PollOutcome.NoVotes, pollOutcome(emptyMap()))
        assertEquals(PollOutcome.NoVotes, pollOutcome(mapOf("a" to 0, "b" to 0)))
        assertEquals(PollOutcome.Winner("b", 3), pollOutcome(mapOf("a" to 1, "b" to 3, "c" to 2)))
        assertEquals(PollOutcome.Tie(listOf("a", "c"), 2), pollOutcome(mapOf("c" to 2, "a" to 2, "b" to 1)))
    }

    @Test fun aTiedPollReopensAfter24Hours() {
        val tied = Instant.parse("2026-10-25T10:00:00Z")
        assertFalse(shouldReopenTie(tied, tied + 23.hours))
        assertTrue(shouldReopenTie(tied, tied + 24.hours))
    }

    @Test fun hoursLeftRoundsUp() {
        val closes = Instant.parse("2026-10-26T10:00:00Z")
        assertEquals(24, pollHoursLeft(closes - 24.hours, closes))
        assertEquals(2, pollHoursLeft(closes - 61.minutes, closes))
        assertEquals(1, pollHoursLeft(closes - 1.minutes, closes))
        assertEquals(0, pollHoursLeft(closes, closes))
        assertEquals(0, pollHoursLeft(closes + 1.hours, closes))
        assertTrue(isPastClosing(closes, closes))
        assertFalse(isPastClosing(closes, closes - 1.minutes))
    }
}
