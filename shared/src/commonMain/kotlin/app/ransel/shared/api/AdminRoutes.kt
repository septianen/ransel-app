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
    const val USERS = "/admin/users"                               // GET ?query=&status=&platform=&joinedFrom=&joinedTo=&noCredits=&page= → List<AdminUserDto> + meta;
                                                                   // POST CreateUserRequest → 201 AdminUserDto (sends an invite email)
    const val USERS_EXPORT = "/admin/users/export.csv"             // GET, same filters, no paging → text/csv (audited)
    fun user(userId: String) = "/admin/users/$userId"              // GET → AdminUserDetailDto
    fun userLedger(userId: String) = "/admin/users/$userId/credits/ledger" // GET ?page= → List<CreditLedgerEntryDto> (note + balanceAfter filled) + meta
    fun userCredits(userId: String) = "/admin/users/$userId/credits"       // POST AdjustCreditsRequest → CreditsDto
    fun userSignOutAll(userId: String) = "/admin/users/$userId/sign-out-all" // POST → AdminActionResult
    fun userDisable(userId: String) = "/admin/users/$userId/disable"       // POST → AdminActionResult
    fun userEnable(userId: String) = "/admin/users/$userId/enable"         // POST → AdminActionResult
    fun userPasswordReset(userId: String) = "/admin/users/$userId/password-reset" // POST → 204; 409 CONFLICT while email is not set up
    const val AUDIT = "/admin/audit"                               // GET ?targetId=&action=&page= → List<AdminAuditEntryDto> + meta

    // Stage B ─ monitoring (read only; days are WIB days, provider money in micro-USD, never trip content)
    const val OVERVIEW = "/admin/overview"                         // GET ?range=TODAY|LAST_7_DAYS|LAST_30_DAYS (default LAST_7_DAYS) → AdminOverviewDto
    const val EXPLORE = "/admin/explore"                           // GET ?month=YYYY-MM (default this month) → AdminExploreDto
    const val EXPLORE_SEARCHES = "/admin/explore/searches"         // GET ?userId=&page= → List<AdminSearchDto> + meta, newest first
    const val SYSTEM = "/admin/system"                             // GET ?range=LAST_24_HOURS|LAST_7_DAYS (default LAST_24_HOURS) → AdminSystemDto

    // Stage C ─ settings without a deploy (history: AUDIT ?action=SETTINGS_UPDATE)
    const val SETTINGS = "/admin/settings"                         // GET → AdminSettingsDto; PUT UpdateSettingsRequest → AdminSettingsDto (audited)

    // Stage D ─ remote content (the apps read it later, stage E; curated interests are used by the server at once)
    const val CONTENT_STRINGS = "/admin/content/strings"           // GET → AdminContentStringsDto; PUT UpdateContentStringRequest → AdminContentStringDto (a draft)
    const val CONTENT_ERRORS = "/admin/content/errors"             // GET → AdminContentErrorsDto; PUT UpdateContentErrorRequest → AdminContentErrorDto (a draft)
    const val CONTENT_CATEGORIES = "/admin/content/categories"     // GET → AdminContentCategoriesDto; PUT UpdateContentCategoriesRequest → same (published at once, audited)
    const val CONTENT_INTERESTS = "/admin/content/interests"       // GET ?query= → List<AdminInterestSummaryDto>
    const val CONTENT_INTEREST = "/admin/content/interests/item"   // GET ?destination=&month=&language= → AdminInterestCurationDto;
                                                                   // PUT AdminInterestCurationDto → same (live at once, audited);
                                                                   // POST ?destination=&month=&language= → same, AI chips asked again (503 AI_UNAVAILABLE / AI_PAUSED)
    const val CONTENT_PUBLISH = "/admin/content/publish"           // POST → ContentPublishResultDto: every string and error draft goes live, version + 1 (audited)
}
