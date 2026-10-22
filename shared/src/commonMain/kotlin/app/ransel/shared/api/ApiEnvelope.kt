package app.ransel.shared.api

import kotlinx.serialization.Serializable

/*
 * Response format (TRD "API"):
 *  - success/failure is read from the HTTP status code, never from a body field;
 *  - success body:  {"data": ..., "meta": {...}?}
 *  - failure body:  {"error": {"code": "...", "message": "...", "details": {...}?}}
 *  - 201 create, 204 delete/no content (empty body), 400/422 validation,
 *    401 token, 403 permission, 404, 409 conflict, 429 quota, 500 (no stack trace).
 *
 * Server: wrap with call.respondData(...) and build every error in StatusPages.
 * Client: map to ApiResult<T> (composeApp) so ViewModels never see status codes.
 */

@Serializable
data class ApiResponse<T>(
    val data: T,
    val meta: PageMeta? = null,
)

@Serializable
data class PageMeta(
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    /** One of [ErrorCode]. Stable; app logic switches on it. */
    val code: String,
    /** Human-readable, for display/logs only. May change at any time. */
    val message: String,
    /** Field-level problems for VALIDATION_FAILED, e.g. {"endDate": "MUST_BE_AFTER_START"}. */
    val details: Map<String, String>? = null,
)

/**
 * Error codes. Shown small on state screens (e.g. "Kode: AI_UNAVAILABLE").
 * Adding a code is a contract change: add it here first, then use it.
 */
object ErrorCode {
    // client-only (never sent by the server)
    const val NETWORK_TIMEOUT = "NETWORK_TIMEOUT"
    const val NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"

    // generic
    const val INTERNAL_ERROR = "INTERNAL_ERROR"            // 500
    const val VALIDATION_FAILED = "VALIDATION_FAILED"      // 400/422, details per field
    const val NOT_FOUND = "NOT_FOUND"                      // 404, non-trip resources
    const val FORBIDDEN = "FORBIDDEN"                      // 403, role too low for this action
    const val CONFLICT = "CONFLICT"                        // 409, generic
    const val RATE_LIMITED = "RATE_LIMITED"                // 429, too many requests (e.g. login attempts); retry after the Retry-After header

    // auth (Phase 3)
    const val UNAUTHORIZED = "UNAUTHORIZED"                // 401, no/invalid access token
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"              // 401, refresh token expired/revoked → Session expired screen
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"  // 401 on /auth/login
    const val EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED" // 409 on /auth/register
    const val RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID"  // 400 on /auth/password/reset (expired, used, unknown)
    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"        // 403 on /auth/login (right password) and /auth/refresh: an admin deactivated the account
    const val VERIFY_TOKEN_INVALID = "VERIFY_TOKEN_INVALID" // 400 on /auth/email/verify (expired, replaced by a newer link, unknown)
    const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"    // 403 on Explore and voucher routes until the account's email is verified

    // trips & members
    const val TRIP_NOT_FOUND = "TRIP_NOT_FOUND"            // 404 (also when the user is not a member, to avoid leaking existence)
    const val INVITE_EXPIRED = "INVITE_EXPIRED"            // 410
    const val INVITE_REVOKED = "INVITE_REVOKED"            // 410
    const val INVITE_FULL = "INVITE_FULL"                  // 409
    const val ALREADY_MEMBER = "ALREADY_MEMBER"            // 409 on accept
    const val OWNER_CANNOT_LEAVE = "OWNER_CANNOT_LEAVE"    // 409, transfer ownership first

    // expenses
    const val SPLIT_TOTAL_MISMATCH = "SPLIT_TOTAL_MISMATCH" // 422, sum(shares) != amount
    const val PAYER_TOTAL_MISMATCH = "PAYER_TOTAL_MISMATCH" // 422, sum(payers) != amount

    // Explore AI (Phase 5)
    const val AI_UNAVAILABLE = "AI_UNAVAILABLE"            // 503, LLM failed twice / Places down

    // Itinerary voting
    const val ROUND_NOT_FOUND = "ROUND_NOT_FOUND"          // 404, unknown or expired round, or a place that is not in it
    const val VOTING_CLOSED = "VOTING_CLOSED"              // 409, vote/undo/next batch after closesAt or after the decision
    const val ROUND_ALREADY_DECIDED = "ROUND_ALREADY_DECIDED" // 409 on decide
    const val ROUND_LIMIT = "ROUND_LIMIT"                  // 409 on a new search: the trip already has 3 open rounds

    // Explore credits
    const val INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS" // 402, details: cost, available, resetsAt
    const val VOUCHER_INVALID = "VOUCHER_INVALID"          // 404, unknown or revoked code
    const val VOUCHER_EXPIRED = "VOUCHER_EXPIRED"          // 422, past expiresAt
    const val VOUCHER_USED = "VOUCHER_USED"                // 409, redeemed by this user already, or no uses left
}
