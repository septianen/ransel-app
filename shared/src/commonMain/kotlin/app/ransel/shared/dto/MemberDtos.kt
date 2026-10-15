package app.ransel.shared.dto

import app.ransel.shared.model.Role
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/* Phase 4. Only the OWNER invites, changes roles and removes members. */

@Serializable
data class TripMemberDto(
    val tripId: String,
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val role: Role,
    val joinedAt: Timestamp,
)

/** PATCH /trips/{id}/members/{userId}. OWNER cannot be set here; use transfer-ownership. */
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

/** GET /invites/{code} — for the "Gabung trip" screen. Failure codes: INVITE_EXPIRED, INVITE_REVOKED, INVITE_FULL. */
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
    val userId: String,
    val displayName: String,
    val paidMinor: Long,
    val shareMinor: Long,
    /** paid − share + settlements paid − settlements received. > 0 = others owe this member. */
    val balanceMinor: Long,
)

@Serializable
data class TransferDto(
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
)

/** POST /trips/{id}/settlements ("Tandai lunas"). Amount in baseCurrency. */
@Serializable
data class CreateSettlementRequest(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
    val settledAt: Timestamp? = null,
)

@Serializable
data class SettlementDto(
    val id: String,
    val tripId: String,
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
    val settledAt: Timestamp,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)
