package app.ransel.shared.dto

import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollPriceUnit
import app.ransel.shared.model.PollStatus
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/*
 * Polls (PRD "Group tab, polling and comments", TRD "Polls, comments and link previews"). Online only, not in /sync.
 * Which option fields a kind uses, and what happens to the winner, is in domain/Polls.kt.
 */

/** One option of a poll. Fields a kind does not use stay null. */
@Serializable
data class PollOptionDto(
    val id: String,
    val label: String,
    /** DATE: the range. */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** PLACE, STAY, FOOD: a Google place id, and/or a link (STAY). */
    val placeId: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    /** STAY per night, FOOD per person: in the trip currency, minor units. */
    val priceMinor: Long? = null,
    val priceUnit: PollPriceUnit? = null,
    /** BUDGET: the range per person, in the trip currency. */
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
    /** Goes up when label, dates, place, link or price change; a vote made on an older version no longer counts. */
    val version: Int = 1,
    /** Votes that count: from current members, on the current version. */
    val voteCount: Int = 0,
    /** Member ids who chose it. Empty for an anonymous poll, for everyone including the creator. */
    val voterIds: List<String> = emptyList(),
)

@Serializable
data class PollDto(
    val id: String,
    val tripId: String,
    val kind: PollKind,
    val question: String,
    val createdByMemberId: String,
    val createdByName: String,
    val createdAt: Timestamp,
    val closesAt: Timestamp,
    /** One choice by default (DATE: several). */
    val multiChoice: Boolean = false,
    /** Always true for BUDGET. Nobody can see who chose what; counts and who has voted stay visible. */
    val anonymous: Boolean = false,
    /** What the winner does (dates, destination, a place, an activity); false = it only records the result. */
    val applyWinner: Boolean = true,
    /** FOOD: the day and time of the activity that is added. */
    val dayDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val status: PollStatus,
    val decidedOptionId: String? = null,
    /** TIED: the options that share the most votes. */
    val tiedOptionIds: List<String> = emptyList(),
    val options: List<PollOptionDto> = emptyList(),
    /** Option ids the caller voted for (current versions only). */
    val myVotes: List<String> = emptyList(),
    /** Members who voted, also for an anonymous poll (so reminders and "belum memilih" work). */
    val votedMemberIds: List<String> = emptyList(),
    /** Current members with an account, the people who can vote. */
    val memberCount: Int = 0,
) {
    val isOpen: Boolean get() = status == PollStatus.OPEN
}

/** An option to add or change (POST/PATCH .../options, or inside [CreatePollRequest]). */
@Serializable
data class PollOptionInput(
    val id: String,
    val label: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val placeId: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val priceMinor: Long? = null,
    val priceUnit: PollPriceUnit? = null,
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
)

/**
 * POST /trips/{id}/polls (OWNER or EDITOR) → 201 PollDto. `closesAt` must be in the future. `anonymous` is forced on for
 * BUDGET. `options` may be empty: they can be added later (also by Jelajah, a pasted link or by hand).
 */
@Serializable
data class CreatePollRequest(
    val id: String,
    val kind: PollKind,
    val question: String,
    val closesAt: Timestamp,
    val multiChoice: Boolean = false,
    val anonymous: Boolean = false,
    val applyWinner: Boolean = true,
    val dayDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val options: List<PollOptionInput> = emptyList(),
)

/**
 * PATCH /polls/{id}: only the fields that are sent change. `anonymous` can change only while nobody has voted
 * (else 409 POLL_LOCKED), and BUDGET cannot be turned public (422).
 */
@Serializable
data class UpdatePollRequest(
    val question: String? = null,
    val closesAt: Timestamp? = null,
    val multiChoice: Boolean? = null,
    val anonymous: Boolean? = null,
    val applyWinner: Boolean? = null,
)

/** One chosen option and the version of it the voter saw (a stale version answers 409 VOTE_STALE). */
@Serializable
data class PollVoteInput(val optionId: String, val version: Int)

/** PUT /polls/{id}/votes: replaces all votes of the caller in this poll; empty = take the vote back. */
@Serializable
data class VotePollRequest(val votes: List<PollVoteInput> = emptyList())

/** POST /polls/{id}/decide: confirms [optionId] and runs its action; null = close the poll without choosing (nothing happens). */
@Serializable
data class DecidePollRequest(val optionId: String? = null)

/** POST /polls/{id}/reopen: a closed or tied poll takes votes again until [closesAt] (future). */
@Serializable
data class ReopenPollRequest(val closesAt: Timestamp)
