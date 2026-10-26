package app.ransel.shared.dto

import app.ransel.shared.model.Role
import app.ransel.shared.model.SettlementStatus
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/* Phase 4. Only the OWNER invites, changes roles and removes members. */

@Serializable
data class TripMemberDto(
    val tripId: String,
    /**
     * trip_members.id: what expenses, splits, payers and settlements point at. For an account it is
     * `memberIdFor(tripId, userId)`; a member without an account has a random UUID.
     */
    val memberId: String,
    /** Null = a member without an account ("Tanpa akun"), added by name by the OWNER. */
    val userId: String? = null,
    val displayName: String,
    val email: String? = null,
    val role: Role,
    val joinedAt: Timestamp,
    /** Digits with country code (domain.normalizePhone). Only the OWNER sees it, and only on members without an account. */
    val phone: String? = null,
    /** Set on a member without an account that someone has claimed (such a member is hidden from lists). */
    val claimedAt: Timestamp? = null,
    /** A member without an account whose name a browser uses on the guest page (OWNER only; the OWNER can release it). */
    val guestBrowser: Boolean = false,
) {
    val isGuest: Boolean get() = userId == null
}

/** POST /trips/{id}/members (OWNER): add a person without an account. 201 TripMemberDto with role VIEWER. */
@Serializable
data class AddMemberRequest(
    val id: String,
    val displayName: String,
    /** Optional WhatsApp number in any common format; the server stores domain.normalizePhone(it). */
    val phone: String? = null,
)

/** PATCH /trips/{id}/members/{memberId}. OWNER cannot be set here; use transfer-ownership. */
@Serializable
data class UpdateMemberRoleRequest(
    val role: Role,
)

/** POST /trips/{id}/transfer-ownership. The old owner becomes EDITOR. */
@Serializable
data class TransferOwnershipRequest(
    val newOwnerUserId: String,
)

// ── Invites ─────────────────────────────────────────────────────────────────

/** POST /trips/{id}/invites. role must be EDITOR or VIEWER. maxUses null = unlimited. */
@Serializable
data class CreateInviteRequest(
    val role: Role = Role.EDITOR,
    val maxUses: Int? = null,
    val expiresAt: Timestamp,
)

@Serializable
data class InviteDto(
    /** e.g. "BALI-7K2Q" (domain.generateInviteCode). */
    val code: String,
    val tripId: String,
    val role: Role,
    val maxUses: Int? = null,
    val usedCount: Int,
    val expiresAt: Timestamp,
    val revokedAt: Timestamp? = null,
    /** https://ransel.mietek.tech/j/<code> (the server builds it from APP_BASE_URL). */
    val url: String,
    val createdAt: Timestamp,
)

/**
 * GET /invites/{code} — public (no bearer, rate limited per IP) so the link opens in any browser; for the "Gabung trip"
 * screen. Failure codes: INVITE_EXPIRED, INVITE_REVOKED, INVITE_FULL. Carries no amount per person, no phone numbers
 * and no payment info.
 */
@Serializable
data class InvitePreviewDto(
    val code: String,
    val tripId: String,
    val tripTitle: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val ownerName: String,
    val memberCount: Int,
    val role: Role,
    /** One entry per member, e.g. "RP", for the avatar row. */
    val memberInitials: List<String> = emptyList(),
    /** Names of the members without an account, for "Kamu yang mana?". */
    val guestNames: List<String> = emptyList(),
    val activityCount: Int = 0,
    /** Currency of [sharedSpendMinor]; null when the trip has no expenses yet. */
    val baseCurrency: String? = null,
    /** Total of the trip's shared expenses (all members together), in [baseCurrency] minor units. */
    val sharedSpendMinor: Long = 0,
)

// ── Balances & settlements ──────────────────────────────────────────────────

/** GET /trips/{id}/balances. Everything in baseCurrency; computed with domain.computeBalances. */
@Serializable
data class BalancesDto(
    val baseCurrency: String,
    val members: List<MemberBalanceDto>,
    /** "Supaya lunas": fewest transfers that bring every balance to zero. */
    val transfers: List<TransferDto>,
)

@Serializable
data class MemberBalanceDto(
    val memberId: String,
    val displayName: String,
    val paidMinor: Long,
    val shareMinor: Long,
    /** paid − share + settlements paid − settlements received. > 0 = others owe this member. */
    val balanceMinor: Long,
)

@Serializable
data class TransferDto(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
)

/** POST /trips/{id}/settlements ("Tandai lunas"): CONFIRMED at once. Amount in baseCurrency. */
@Serializable
data class CreateSettlementRequest(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val settledAt: Timestamp? = null,
)

/** POST /trips/{id}/settlements/claim ("Saya sudah bayar"). The payer is the caller's own member. Amount in baseCurrency. */
@Serializable
data class ClaimSettlementRequest(
    val id: String,
    val toMemberId: String,
    val amountMinor: Long,
)

@Serializable
data class SettlementDto(
    val id: String,
    val tripId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val settledAt: Timestamp,
    /** Rows from before claims existed are CONFIRMED. Only CONFIRMED rows count in balances. */
    val status: SettlementStatus = SettlementStatus.CONFIRMED,
    /** When the payer said they paid; null for a direct "Tandai lunas". */
    val claimedAt: Timestamp? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)
