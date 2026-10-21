package app.ransel.shared.dto

import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.CreditDirection
import app.ransel.shared.model.DevicePlatform
import app.ransel.shared.model.Role
import app.ransel.shared.model.UserStatus
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Admin app "Karung" (docs/specs/admin-spec.md). Server only, online only. Admin responses never carry trip content
 * (itinerary text, notes, packing items, expense descriptions or amounts): only counts, dates and the destination.
 */

/** GET /admin/me. */
@Serializable
data class AdminMeDto(
    val id: String,
    val email: String,
    val displayName: String,
    /** `production`, `staging` or `development`: Karung shows it as a badge so an admin knows which data they change. */
    val environment: String,
    /** False until an email provider is connected; Karung hides "Kirim email reset kata sandi" while false. */
    val passwordResetEmailEnabled: Boolean,
)

/** One row of GET /admin/users. */
@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val status: UserStatus,
    val joinedAt: Timestamp,
    /** Last token refresh or sign-in; null = never signed in since the account was created. */
    val lastActiveAt: Timestamp? = null,
    /** Phone that last registered for push; null = none (web only, or no push token yet). */
    val platform: DevicePlatform? = null,
    /** Trips the user is a member of (any role), not deleted. */
    val tripCount: Int,
    /** Wallet balance; the free daily allowance is in [dailyFreeLeft]. */
    val balance: Int,
    val dailyFreeLeft: Int,
)

/** GET /admin/users/{id}. */
@Serializable
data class AdminUserDetailDto(
    val user: AdminUserDto,
    val credits: CreditsDto,
    /** Expenses the user paid (not deleted), across all their trips. */
    val expenseCount: Int,
    val trips: List<AdminTripDto>,
    val devices: List<AdminDeviceDto>,
)

/** Trip metadata only. */
@Serializable
data class AdminTripDto(
    val id: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val role: Role,
    val memberCount: Int,
    /** Itinerary items + packing items, not deleted. */
    val itemCount: Int,
)

/**
 * A place the user is signed in: a registered phone (push device) or an active session (refresh token).
 * [platform] is null for a session; [name] is the device name the app sent at sign-in, null when it sent none
 * (and for a push device, which has no name).
 */
@Serializable
data class AdminDeviceDto(
    val name: String? = null,
    val platform: DevicePlatform? = null,
    val appVersion: String? = null,
    val lastSeenAt: Timestamp,
)

/**
 * POST /admin/users/{id}/credits. Changes only the wallet, never the free daily allowance.
 * [amount] 1–10,000; [reason] 3–200 characters, stored on the ledger row and in the audit log.
 * A SUBTRACT larger than the balance → 422 VALIDATION_FAILED `{"amount": "EXCEEDS_BALANCE"}` (nothing changes).
 * [requestId] is a UUID made when the dialog opens: the same id again changes nothing and returns the current state.
 */
@Serializable
data class AdjustCreditsRequest(
    val requestId: String,
    val direction: CreditDirection,
    val amount: Int,
    val reason: String,
)

/** Result of a one-shot account action (sign out everywhere, disable, enable). */
@Serializable
data class AdminActionResult(
    val user: AdminUserDto,
    /** Refresh tokens revoked by this action (0 for enable). */
    val revokedSessions: Int,
)

/** GET /admin/audit, newest first. [before]/[after] are the changed values as JSON (shape per [action]). */
@Serializable
data class AdminAuditEntryDto(
    val id: String,
    val adminId: String,
    val adminEmail: String,
    val action: AdminAction,
    /** `USER`, `SETTING`, `CONTENT` or `EXPORT`. */
    val targetType: String,
    val targetId: String? = null,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
    val createdAt: Timestamp,
)
