package app.ransel.shared.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * The guest web page (PRD "Sharing to WhatsApp"): people without an account open a poll link (/p/<code>) or an invite
 * link (/j/<code>, "Lihat tanpa akun"), pick their name or write it, then vote and tick the packing list they got.
 * A guest is a trip member without an account; the browser holds a token for that member (header GUEST_TOKEN_HEADER).
 * One name belongs to one browser at a time; the OWNER can release it.
 */

/** A member as the guest page shows it. */
@Serializable
data class GuestMemberDto(
    val memberId: String,
    val name: String,
    val hasAccount: Boolean,
    /** A member without an account that another browser already uses ("sudah dipakai"). */
    val taken: Boolean = false,
)

/** GET /guest/{code}: what a guest sees. [code] is a poll share code or a live invite code. */
@Serializable
data class GuestPageDto(
    val code: String,
    val tripTitle: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val ownerName: String,
    /** Everyone in the trip; only members without an account can be picked. */
    val members: List<GuestMemberDto>,
    /** The member this browser is (null = pick a name first). */
    val me: GuestMemberDto? = null,
    /** The poll of a /p/ link; null for an invite link. */
    val focusPollId: String? = null,
    /** Open polls, plus the linked poll whatever its status. [PollDto.myVotes] are [me]'s. */
    val polls: List<PollDto> = emptyList(),
    /** [me]'s packing lines from the owner's list (empty until a name is picked). */
    val packing: List<PersonalItemDto> = emptyList(),
)

/**
 * POST /guest/{code}/identity: pick a member without an account ([memberId]) or write a new name ([name]).
 * A new name becomes a member without an account (role VIEWER); a name equal to an existing one is refused with
 * GUEST_NAME_EXISTS (details.memberId) so the page can offer that member instead.
 */
@Serializable
data class GuestIdentityRequest(
    val memberId: String? = null,
    val name: String? = null,
)

/** The browser keeps [token] for this trip; it replaces the token it had (if any). */
@Serializable
data class GuestIdentityDto(
    val token: String,
    val me: GuestMemberDto,
)

/** PATCH /guest/{code}/personal-items/{itemId}: a guest only ticks. */
@Serializable
data class GuestTickRequest(val isPacked: Boolean)

/** POST /polls/{id}/share → the poll's link (the same code until it is turned off). */
@Serializable
data class PollShareDto(
    val code: String,
    /** https://ransel.mietek.tech/p/<code> (APP_BASE_URL). */
    val url: String,
)
