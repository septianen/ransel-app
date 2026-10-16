package app.ransel.shared.api

/**
 * Every path of the REST API, used by both Ktor routing (server) and Ktor Client (app).
 * All paths are relative to [BASE]. Phase = when the endpoint must exist.
 *
 * From Phase 3 every endpoint except /health and /auth/... needs
 * `Authorization: Bearer <accessToken>`.
 */
object ApiRoutes {
    const val BASE = "/api/v1"

    // Phase 2 ─ health
    const val HEALTH = "/health"                                   // GET

    // Phase 2 ─ trips
    const val TRIPS = "/trips"                                     // GET list (TripDto), POST CreateTripRequest
    fun trip(tripId: String) = "/trips/$tripId"                    // GET TripDetailDto, PUT UpdateTripRequest, DELETE (soft)

    // Phase 2 ─ itinerary
    fun itinerary(tripId: String) = "/trips/$tripId/itinerary"     // GET list, POST ItineraryItemInput
    fun itineraryItem(tripId: String, itemId: String) =
        "/trips/$tripId/itinerary/$itemId"                         // PUT ItineraryItemInput (also used to schedule), DELETE
    fun dayOrder(tripId: String, date: String) =
        "/trips/$tripId/days/$date/order"                          // PUT DayOrderRequest (date = ISO yyyy-MM-dd)

    // Phase 2 ─ packing
    fun packing(tripId: String) = "/trips/$tripId/packing"         // GET list, POST CreatePackingItemRequest
    fun packingItem(tripId: String, itemId: String) =
        "/trips/$tripId/packing/$itemId"                           // PATCH PatchPackingItemRequest, DELETE
    fun packingUncheckAll(tripId: String) = "/trips/$tripId/packing/uncheck-all" // POST, 204

    // Phase 2 ─ expenses
    fun expenses(tripId: String) = "/trips/$tripId/expenses"       // GET list, POST ExpenseInput
    fun expense(tripId: String, expenseId: String) =
        "/trips/$tripId/expenses/$expenseId"                       // PUT ExpenseInput, DELETE
    fun expenseSummary(tripId: String) = "/trips/$tripId/expenses/summary" // GET ExpenseSummaryDto

    // Phase 3 ─ auth & account
    const val AUTH_REGISTER = "/auth/register"                     // POST RegisterRequest → 201 AuthSessionDto
    const val AUTH_LOGIN = "/auth/login"                           // POST LoginRequest → AuthSessionDto
    const val AUTH_REFRESH = "/auth/refresh"                       // POST RefreshRequest → AuthSessionDto (rotates refresh token)
    const val AUTH_LOGOUT = "/auth/logout"                         // POST RefreshRequest → 204 (revokes this device's refresh token)
    const val AUTH_PASSWORD_FORGOT = "/auth/password/forgot"       // POST ForgotPasswordRequest → always 204
    const val AUTH_PASSWORD_RESET = "/auth/password/reset"         // POST ResetPasswordRequest → 204, revokes all refresh tokens
    const val ME = "/me"                                           // GET MeDto, PATCH UpdateMeRequest
    const val SYNC = "/sync"                                       // GET ?since=<ISO instant> → SyncPullResponse, POST SyncPushRequest → SyncPushResponse

    // Phase 4 ─ members, invites, balances, templates
    fun invites(tripId: String) = "/trips/$tripId/invites"         // GET list InviteDto, POST CreateInviteRequest (OWNER)
    fun invite(tripId: String, code: String) = "/trips/$tripId/invites/$code" // DELETE = revoke (OWNER)
    fun invitePreview(code: String) = "/invites/$code"             // GET InvitePreviewDto (signed in)
    fun inviteAccept(code: String) = "/invites/$code/accept"       // POST → TripDto
    fun members(tripId: String) = "/trips/$tripId/members"         // GET list TripMemberDto
    fun member(tripId: String, userId: String) =
        "/trips/$tripId/members/$userId"                           // PATCH UpdateMemberRoleRequest, DELETE = remove (OWNER)
    fun leave(tripId: String) = "/trips/$tripId/leave"             // POST → 204 (OWNER → OWNER_CANNOT_LEAVE)
    fun transferOwnership(tripId: String) = "/trips/$tripId/transfer-ownership" // POST TransferOwnershipRequest
    fun balances(tripId: String) = "/trips/$tripId/balances"       // GET BalancesDto
    fun settlements(tripId: String) = "/trips/$tripId/settlements" // GET list, POST CreateSettlementRequest
    const val PACKING_TEMPLATES = "/packing-templates"             // GET list (built-in + own + shared), POST CreatePackingTemplateRequest
    fun applyTemplate(tripId: String) = "/trips/$tripId/packing/apply-template" // POST ApplyTemplateRequest → List<PackingItemDto>

    // Phase 5 ─ Explore AI
    const val EXPLORE_SUGGESTIONS = "/explore/suggestions"         // GET ?tripId= | ?destination=&month=YYYY-MM → ExploreSuggestionsResponse
    const val RECOMMENDATIONS = "/recommendations"                 // POST RecommendationRequest → RecommendationResponse
    const val RECOMMENDATION_FEEDBACK = "/recommendations/feedback" // POST FeedbackRequest → 204
    fun recommendationFeedback(tripId: String, placeId: String) =
        "/recommendations/feedback/$tripId/$placeId"               // DELETE = undo the last swipe → 204
    /** Signed image proxy, no bearer: `RecommendationItem.photoUrl` is this path plus `?exp=&sig=`, relative to [BASE]. */
    fun placePhoto(placeId: String) = "/places/photo/$placeId"     // GET → image bytes, 404 if no photo
}
