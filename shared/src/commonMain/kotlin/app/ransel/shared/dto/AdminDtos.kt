package app.ransel.shared.dto

import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.AdminRange
import app.ransel.shared.model.AiSearchKind
import app.ransel.shared.model.AiSearchSource
import app.ransel.shared.model.PriceLevel
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
    /**
     * False until an email provider is connected; Karung hides "Kirim email reset kata sandi" and "Tambah pengguna"
     * while false, because both only work by email.
     */
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
    /** Latest authenticated request (written at most hourly) or sign-in, whichever is newer; null = never. */
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
    /** Null for a trip whose dates are not set yet. */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
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

/**
 * POST /admin/users: an admin adds an account. The server creates it with the email already verified and sends an
 * invite email with a link to set the password (valid 7 days); the admin never sees a password. Works while public
 * sign-up is closed. 422 VALIDATION_FAILED (`email: INVALID_EMAIL`, `displayName: REQUIRED`),
 * 409 EMAIL_ALREADY_REGISTERED, 409 CONFLICT while email is not set up ([AdminMeDto.passwordResetEmailEnabled] false).
 */
@Serializable
data class CreateUserRequest(
    val email: String,
    val displayName: String,
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

// ── Stage B: monitoring ────────────────────────────────────────────────────
// Money is micro-USD (1_000_000 = US$1) because providers bill in USD; these are list-price estimates, not invoices.

/** GET /admin/overview ("Ringkasan"). Tiles named "today" ignore [range]; [newTrips] follows it. */
@Serializable
data class AdminOverviewDto(
    val range: AdminRange,
    val generatedAt: Timestamp,
    /** Users with an authenticated request today. */
    val activeToday: Int,
    /** The same weekday a week ago, for "+8% dari minggu lalu". */
    val activeWeekAgo: Int,
    /** Users active on any of the last 7 days, today included. */
    val activeLast7Days: Int,
    /** Accounts that are not deleted. */
    val registeredUsers: Int,
    /** Trips created in [range], and in the period of the same length just before it. */
    val newTrips: Int,
    val newTripsPrevious: Int,
    val searchesToday: Int,
    val searchesTodayFromCache: Int,
    /** Users refused a search today for lack of credits. */
    val usersOutOfCreditsToday: Int,
    /** Credits spent today on anything, and the part that came from the free daily allowance. */
    val creditsUsedToday: Int,
    val creditsUsedTodayFree: Int,
    val vouchersRedeemedToday: Int,
    val aiCost: AdminAiCostDto,
    /** The last 30 days, oldest first. */
    val activeByDay: List<AdminActiveDayDto>,
    /** Trips created in the last 30 days per destination (case and spaces ignored), most first, at most 6. */
    val topDestinations: List<AdminCountDto>,
    /** Errors of the last 24 hours per code, most first, at most 5 ([AdminErrorDto.trend] empty). */
    val recentErrors: List<AdminErrorDto>,
    /** This month so far. */
    val costByService: AdminCostByServiceDto,
)

/** LLM + Places this month against the budget ("Biaya AI bulan ini"). */
@Serializable
data class AdminAiCostDto(
    /** YYYY-MM */
    val month: String,
    val spentMicroUsd: Long,
    /** spent ÷ days so far × days in the month; equals [spentMicroUsd] for a past month. */
    val projectedMicroUsd: Long,
    /** Null = no budget set (`AI_MONTHLY_BUDGET_USD`; a Karung setting from stage C). */
    val budgetMicroUsd: Long? = null,
    /** Karung warns from this share of the budget (80, 90 or 100). */
    val alertPercent: Int = 80,
)

@Serializable
data class AdminActiveDayDto(
    val date: LocalDate,
    val active: Int,
    /** Accounts created that day. */
    val signups: Int,
)

@Serializable
data class AdminCountDto(val name: String, val count: Int)

@Serializable
data class AdminCostByServiceDto(
    val llmMicroUsd: Long,
    /** Places search, details, photos, autocomplete and geocoding. */
    val placesMicroUsd: Long,
    /** "Server dan database": the configured monthly amount, in full (`SERVER_MONTHLY_COST_USD`). */
    val serverMicroUsd: Long,
)

/** Error responses of one code in a range. */
@Serializable
data class AdminErrorDto(
    /** An [app.ransel.shared.api.ErrorCode] value. */
    val code: String,
    val httpStatus: Int,
    /** The route with the most of them, as a template ("GET /trips/{id}/expenses"); "-" = no route matched. */
    val topRoute: String,
    val count: Long,
    /** Distinct signed-in users who got it. */
    val users: Int,
    val lastAt: Timestamp,
    /** System page: one count per day of the last 7 days, oldest first, whatever the range. Empty elsewhere. */
    val trend: List<Long> = emptyList(),
)

/** GET /admin/explore ("Jelajah AI"): Explore searches and poll Jelajah searches of one month. */
@Serializable
data class AdminExploreDto(
    /** YYYY-MM */
    val month: String,
    /** Days of the month so far (every day for a past month), for "rata-rata per hari". */
    val days: Int,
    val searches: Int,
    val searchesFromCache: Int,
    /** The cache searches times the average estimated cost of a NEW search this month ("hemat ± US$61"). */
    val cacheSavingsMicroUsd: Long,
    /** Cards of successful Explore searches (poll results are options, not swipes), and how many were liked in the trip. */
    val cardsShown: Int,
    val cardsSaved: Int,
    /** Waiting time of successful searches; null without any. */
    val latencyMedianMs: Int? = null,
    val latencyP95Ms: Int? = null,
    /** Users refused at least one search this month for lack of credits. */
    val usersOutOfCredits: Int,
    /** Credits spent on Explore (new search and next batch) from the free allowance and from wallets. */
    val creditsFromFree: Int,
    val creditsFromWallet: Int,
    val aiCost: AdminAiCostDto,
    /** Every LLM and Places call per day (not only searches), each day of the month so far. */
    val costByDay: List<AdminCostDayDto>,
    /** Explore searches only (polls have none): per [PriceLevel] name, every level listed. */
    val priceLevels: List<AdminCountDto>,
    /** Explore searches of the month; the base of the interest shares. */
    val exploreSearches: Int,
    /** Most picked interests, at most 8: built-in ones by their `Interest` name, destination interests as written. */
    val topInterests: List<AdminInterestDto>,
)

@Serializable
data class AdminCostDayDto(val date: LocalDate, val llmMicroUsd: Long, val placesMicroUsd: Long)

@Serializable
data class AdminInterestDto(
    val name: String,
    val count: Int,
    /** A destination interest ("Pantai"): the destination it was picked for most ("khas Bali"); null for built-in ones. */
    val destination: String? = null,
)

/** One row of GET /admin/explore/searches ("Pencarian terbaru"): parameters only, never prompt text. */
@Serializable
data class AdminSearchDto(
    val id: String,
    val createdAt: Timestamp,
    val userId: String,
    val userName: String,
    val kind: AiSearchKind,
    val destination: String,
    /** Null for a poll search. */
    val priceLevel: PriceLevel? = null,
    val interests: List<String> = emptyList(),
    /** False = "next batch" of the same search. */
    val newSearch: Boolean,
    val source: AiSearchSource,
    val cards: Int,
    /** Cards of this search liked in the trip. */
    val saved: Int,
    val latencyMs: Int,
    val creditsCharged: Int,
    /** Estimated LLM + Places cost of this search. */
    val costMicroUsd: Long,
)

/** GET /admin/system ("Sistem & error"): health of this server and its errors. */
@Serializable
data class AdminSystemDto(
    val range: AdminRange,
    val generatedAt: Timestamp,
    /** The deployed commit (`APP_VERSION`); null when not set. */
    val version: String? = null,
    val startedAt: Timestamp,
    /** Requests of the last 24 hours (since start when that is shorter), 5xx among them, and the p95 latency. */
    val requests24h: Long,
    val serverErrors24h: Long,
    val latencyP95Ms: Int? = null,
    val databaseBytes: Long,
    /** From the backup script's marker file; null = no backup seen. */
    val lastBackupAt: Timestamp? = null,
    /** Latest applied Flyway migration, e.g. "27". */
    val flywayVersion: String,
    val llmConfigured: Boolean,
    val placesConfigured: Boolean,
    val emailConfigured: Boolean,
    val pushConfigured: Boolean,
    /** Per code in [range], most first. */
    val errors: List<AdminErrorDto>,
)
