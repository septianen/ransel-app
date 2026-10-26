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
    const val AUTH_EMAIL_VERIFY = "/auth/email/verify"             // POST VerifyEmailRequest → VerifyEmailResponse (no token needed; the link is the proof)
    const val AUTH_EMAIL_RESEND = "/auth/email/resend"             // POST (bearer) → 204, sends a new link and voids older ones; 204 too when already verified
    const val ME = "/me"                                           // GET MeDto, PATCH UpdateMeRequest
    const val SYNC = "/sync"                                       // GET ?since=<ISO instant> → SyncPullResponse, POST SyncPushRequest → SyncPushResponse

    // Phase 4 ─ members, invites, balances, templates
    fun invites(tripId: String) = "/trips/$tripId/invites"         // GET list InviteDto, POST CreateInviteRequest (OWNER)
    fun invite(tripId: String, code: String) = "/trips/$tripId/invites/$code" // DELETE = revoke (OWNER)
    fun invitePreview(code: String) = "/invites/$code"             // GET InvitePreviewDto (public, no bearer; rate limited per IP)
    fun inviteAccept(code: String) = "/invites/$code/accept"       // POST → TripDto
    fun members(tripId: String) = "/trips/$tripId/members"         // GET list TripMemberDto, POST AddMemberRequest (OWNER) → 201 TripMemberDto
    fun member(tripId: String, memberId: String) =
        "/trips/$tripId/members/$memberId"                         // PATCH UpdateMemberRoleRequest, DELETE = remove (OWNER)
    fun memberClaim(tripId: String, memberId: String) =
        "/trips/$tripId/members/$memberId/claim"                   // POST = claim a member without an account → 204; DELETE = undo (OWNER) → 204

    // Group: comments on itinerary items and expenses (no chat). Any member with an account may read and write.
    fun comments(tripId: String) = "/trips/$tripId/comments"       // GET ?targetKind=&targetId= → List<CommentDto>, POST CreateCommentRequest → 201 CommentDto
    fun commentCounts(tripId: String) = "/trips/$tripId/comments/counts" // GET ?targetKind= → List<CommentCountDto> (targets that have comments)
    fun comment(commentId: String) = "/comments/$commentId"        // PATCH UpdateCommentRequest, DELETE (author only) → 204
    fun leave(tripId: String) = "/trips/$tripId/leave"             // POST → 204 (OWNER → OWNER_CANNOT_LEAVE)
    fun transferOwnership(tripId: String) = "/trips/$tripId/transfer-ownership" // POST TransferOwnershipRequest
    fun balances(tripId: String) = "/trips/$tripId/balances"       // GET BalancesDto
    fun settlements(tripId: String) = "/trips/$tripId/settlements" // GET list, POST CreateSettlementRequest ("Tandai lunas", CONFIRMED at once)
    fun settlement(tripId: String, settlementId: String) =
        "/trips/$tripId/settlements/$settlementId"                 // DELETE → 204 (the payer, the receiver, or the OWNER): a claim the receiver did not get, or a mistake
    fun settlementClaim(tripId: String) = "/trips/$tripId/settlements/claim" // POST ClaimSettlementRequest → 201 SettlementDto (CLAIMED_PAID), from = the caller's member
    fun settlementConfirm(tripId: String, settlementId: String) =
        "/trips/$tripId/settlements/$settlementId/confirm"         // POST → SettlementDto (CONFIRMED): the receiver, or the OWNER for a receiver without an account; 409 SETTLEMENT_NOT_CLAIMED
    fun payInfo(tripId: String, fromMemberId: String, toMemberId: String) =
        "/trips/$tripId/settlements/$fromMemberId/$toMemberId/pay-info" // GET → PayInfoDto, only for the caller's own member as [fromMemberId] while they owe [toMemberId]; else 403

    // Group money: kitty (iuran), payment info, receipts. Amounts of the kitty are in the trip's base currency.
    fun kitty(tripId: String) = "/trips/$tripId/kitty"             // GET → KittyOverviewDto, PUT KittyInput (OWNER) → KittyOverviewDto, DELETE (OWNER) → 204
    fun kittyDeposits(tripId: String) = "/trips/$tripId/kitty/deposits" // POST KittyDepositInput (EDITOR+ or the treasurer) → 201 KittyDepositDto
    fun kittyDeposit(tripId: String, depositId: String) =
        "/trips/$tripId/kitty/deposits/$depositId"                 // DELETE (EDITOR+ or the treasurer) → 204
    const val ME_PAYMENT_METHODS = "/me/payment-methods"           // GET → List<PaymentMethodDto>, POST PaymentMethodInput → 201 PaymentMethodDto
    fun mePaymentMethod(id: String) = "/me/payment-methods/$id"    // PATCH PaymentMethodInput → PaymentMethodDto, DELETE → 204
    const val RECEIPTS_PARSE = "/receipts/parse"                   // POST ?tripId= with the photo as the body (image/jpeg|png|webp, ≤ 5 MB) → ReceiptDraftDto; costs credits
    const val PACKING_TEMPLATES = "/packing-templates"             // GET list (built-in + own + shared), POST CreatePackingTemplateRequest
    fun applyTemplate(tripId: String) = "/trips/$tripId/packing/apply-template" // POST ApplyTemplateRequest → List<PackingItemDto>

    // Phase 5 ─ Explore AI
    const val EXPLORE_SUGGESTIONS = "/explore/suggestions"         // GET ?tripId=[&destination=] | ?destination=&month=YYYY-MM → ExploreSuggestionsResponse
    const val RECOMMENDATIONS = "/recommendations"                 // POST RecommendationRequest → RecommendationResponse
    const val RECOMMENDATION_FEEDBACK = "/recommendations/feedback" // POST FeedbackRequest → 204
    fun recommendationFeedback(tripId: String, placeId: String) =
        "/recommendations/feedback/$tripId/$placeId"               // DELETE = undo the last swipe → 204
    /** Signed image proxy, no bearer: `RecommendationItem.photoUrl` is this path plus `?exp=&sig=`, relative to [BASE]. */
    fun placePhoto(placeId: String) = "/places/photo/$placeId"     // GET → image bytes, 404 if no photo
    /** Bearer. Query: `name` (the item title, needed only when the server has no saved text to rebuild from) and `currency` (the trip currency for the cost estimate). For opening a place again from the itinerary. */
    fun placeDetail(placeId: String) = "/places/$placeId/detail"   // GET → PlaceDetailDto, 404 NOT_FOUND if the place was never recommended

    // Destination search (bearer; 60 autocomplete requests per user per minute). The static paths win over {placeId}.
    const val PLACES_AUTOCOMPLETE = "/places/autocomplete"         // GET ?q= (2+ chars) &session= (UUID per search field) [&near=lat,lng] → PlaceSuggestionsDto
    const val PLACES_REVERSE = "/places/reverse"                   // GET ?lat=&lng= → ReverseGeocodeDto (coordinates are not logged or stored)
    const val PLACES_POPULAR = "/places/popular"                   // GET → List<PopularDestinationDto>
    fun place(placeId: String) = "/places/$placeId"                // GET ?session= → PlaceDto, 404 NOT_FOUND

    // Itinerary voting (docs/specs/voting-spec.md). Any member reads and votes; only the OWNER decides.
    fun rounds(tripId: String) = "/trips/$tripId/rounds"           // GET → List<RoundSummaryDto> (open and recent, newest first)
    fun round(tripId: String, roundId: String) =
        "/trips/$tripId/rounds/$roundId"                           // GET → RoundDto, 404 ROUND_NOT_FOUND
    fun roundVote(tripId: String, roundId: String, placeId: String) =
        "/trips/$tripId/rounds/$roundId/votes/$placeId"            // PUT VoteRequest → 204, DELETE = undo → 204; 409 VOTING_CLOSED
    fun roundDecide(tripId: String, roundId: String) =
        "/trips/$tripId/rounds/$roundId/decide"                    // POST DecideRequest → 204 (OWNER); 409 ROUND_ALREADY_DECIDED
    fun roundClose(tripId: String, roundId: String) =
        "/trips/$tripId/rounds/$roundId/close"                     // POST → 204 (OWNER): ends voting now (closesAt = now); 409 VOTING_CLOSED if already closed, 409 ROUND_ALREADY_DECIDED

    // Polls (PRD "Group tab, polling and comments"). Any member with an account reads and votes; the OWNER and EDITORs create.
    fun polls(tripId: String) = "/trips/$tripId/polls"             // GET → List<PollDto> (newest first), POST CreatePollRequest → 201 PollDto
    fun poll(pollId: String) = "/polls/$pollId"                    // GET → PollDto, PATCH UpdatePollRequest (creator or OWNER) → PollDto, DELETE (creator or OWNER) → 204
    fun pollOptions(pollId: String) = "/polls/$pollId/options"     // POST PollOptionInput → 201 PollDto
    fun pollOption(pollId: String, optionId: String) =
        "/polls/$pollId/options/$optionId"                         // PATCH PollOptionInput → PollDto (bumps the version, clears its votes), DELETE → PollDto (clears its votes)
    fun pollOptionsBulk(pollId: String) = "/polls/$pollId/options/bulk" // POST BulkPollOptionsRequest → 201 PollDto
    fun pollSearch(pollId: String) = "/polls/$pollId/search"       // POST PollSearchRequest → PollSearchResponse (Jelajah for STAY and FOOD polls; costs credits)
    const val LINK_PREVIEW = "/link-preview"                       // POST LinkPreviewRequest → LinkPreviewDto
    fun pollVotes(pollId: String) = "/polls/$pollId/votes"         // PUT VotePollRequest → PollDto; 409 POLL_CLOSED / VOTE_STALE
    fun pollClose(pollId: String) = "/polls/$pollId/close"         // POST (creator or OWNER) → PollDto: ends voting now
    fun pollDecide(pollId: String) = "/polls/$pollId/decide"       // POST DecidePollRequest (creator or OWNER) → PollDto: confirms the winner and runs its action
    fun pollReopen(pollId: String) = "/polls/$pollId/reopen"       // POST ReopenPollRequest (creator or OWNER) → PollDto

    // Explore credits (replaces the fixed daily quota)
    const val CREDITS = "/credits"                                 // GET → CreditsDto (creates the wallet with the signup grant on first call)
    const val CREDITS_LEDGER = "/credits/ledger"                   // GET ?limit= (default 50, max 200), newest first → List<CreditLedgerEntryDto>
    const val CREDITS_REDEEM = "/credits/redeem"                   // POST RedeemVoucherRequest → RedeemVoucherResponse
    const val CREDIT_PACKAGES = "/credits/packages"                // GET → CreditPackagesResponse

    // Notifications
    const val DEVICES = "/devices"                                 // POST RegisterDeviceRequest → 204 (upsert by token); DELETE ?token= → 204 (sign out)
    const val NOTIFICATIONS = "/notifications"                     // GET ?limit= (default 30, max 100) &before=<id> → NotificationsDto
    const val NOTIFICATIONS_READ = "/notifications/read"           // POST MarkReadRequest → 204
    const val NOTIFICATION_SETTINGS = "/notification-settings"     // GET → NotificationSettingsDto, PATCH UpdateNotificationSettingsRequest → NotificationSettingsDto
}
