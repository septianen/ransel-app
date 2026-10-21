package app.ransel.shared.api

/**
 * Admin API for the admin web app "Karung" (docs/specs/admin-spec.md). Relative to [ApiRoutes.BASE], like [ApiRoutes].
 *
 * Every path needs `Authorization: Bearer <accessToken>` of an account whose email is in the server's `ADMIN_EMAILS`;
 * any other account gets 403 `FORBIDDEN`. Lists are paged with `?page=` (1-based, 25 per page) and carry `meta`.
 * The traveller apps never call these.
 */
object AdminRoutes {
    // Stage A ─ users and credits
    const val ME = "/admin/me"                                     // GET → AdminMeDto
    const val USERS = "/admin/users"                               // GET ?query=&status=&platform=&joinedFrom=&joinedTo=&noCredits=&page= → List<AdminUserDto> + meta
    const val USERS_EXPORT = "/admin/users/export.csv"             // GET, same filters, no paging → text/csv (audited)
    fun user(userId: String) = "/admin/users/$userId"              // GET → AdminUserDetailDto
    fun userLedger(userId: String) = "/admin/users/$userId/credits/ledger" // GET ?page= → List<CreditLedgerEntryDto> (note + balanceAfter filled) + meta
    fun userCredits(userId: String) = "/admin/users/$userId/credits"       // POST AdjustCreditsRequest → CreditsDto
    fun userSignOutAll(userId: String) = "/admin/users/$userId/sign-out-all" // POST → AdminActionResult
    fun userDisable(userId: String) = "/admin/users/$userId/disable"       // POST → AdminActionResult
    fun userEnable(userId: String) = "/admin/users/$userId/enable"         // POST → AdminActionResult
    fun userPasswordReset(userId: String) = "/admin/users/$userId/password-reset" // POST → 204; 409 CONFLICT while email is not set up
    const val AUDIT = "/admin/audit"                               // GET ?targetId=&action=&page= → List<AdminAuditEntryDto> + meta
}
