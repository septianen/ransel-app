package app.ransel.data.remote

import app.ransel.data.analytics.CrashReporter
import app.ransel.data.analytics.NoCrashReporter
import app.ransel.shared.api.ApiErrorResponse
import app.ransel.shared.api.ApiResponse
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.api.ErrorCode
import app.ransel.data.auth.ApiAuth
import app.ransel.shared.dto.AddMemberRequest
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.CreatePollRequest
import app.ransel.shared.dto.DecidePollRequest
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.BulkPollOptionsRequest
import app.ransel.shared.dto.LinkPreviewDto
import app.ransel.shared.dto.LinkPreviewRequest
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.dto.PollSearchRequest
import app.ransel.shared.dto.PollSearchResponse
import app.ransel.shared.dto.ReopenPollRequest
import app.ransel.shared.dto.UpdatePollRequest
import app.ransel.shared.dto.VotePollRequest
import app.ransel.shared.dto.CommentCountDto
import app.ransel.shared.dto.CommentDto
import app.ransel.shared.dto.CreateCommentRequest
import app.ransel.shared.dto.UpdateCommentRequest
import app.ransel.shared.model.CommentTarget
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.ForgotPasswordRequest
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.VerifyEmailRequest
import app.ransel.shared.dto.VerifyEmailResponse
import app.ransel.shared.dto.RefreshRequest
import app.ransel.shared.dto.RegisterRequest
import app.ransel.shared.dto.ResetPasswordRequest
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushRequest
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.dto.BalancesDto
import app.ransel.shared.dto.ExploreSuggestionsResponse
import app.ransel.shared.dto.FeedbackRequest
import app.ransel.shared.dto.PlaceDetailDto
import app.ransel.shared.dto.RecommendationRequest
import app.ransel.shared.dto.RecommendationResponse
import app.ransel.shared.dto.CreatePackingTemplateRequest
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.dto.CreateInviteRequest
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.dto.CreditPackagesResponse
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.MarkReadRequest
import app.ransel.shared.dto.NotificationSettingsDto
import app.ransel.shared.dto.NotificationsDto
import app.ransel.shared.dto.RegisterDeviceRequest
import app.ransel.shared.dto.UpdateNotificationSettingsRequest
import app.ransel.shared.dto.RedeemVoucherRequest
import app.ransel.shared.dto.RedeemVoucherResponse
import app.ransel.shared.dto.CreateSettlementRequest
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.FxRateDto
import app.ransel.shared.dto.PlaceDto
import app.ransel.shared.dto.PlaceSuggestionsDto
import app.ransel.shared.dto.PopularDestinationDto
import app.ransel.shared.dto.ReverseGeocodeDto
import app.ransel.shared.dto.ClaimSettlementRequest
import app.ransel.shared.dto.KittyDepositDto
import app.ransel.shared.dto.KittyDepositInput
import app.ransel.shared.dto.KittyInput
import app.ransel.shared.dto.KittyOverviewDto
import app.ransel.shared.dto.PayInfoDto
import app.ransel.shared.dto.PaymentMethodDto
import app.ransel.shared.dto.PaymentMethodInput
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.dto.InviteDto
import app.ransel.shared.dto.InvitePreviewDto
import app.ransel.shared.dto.TransferOwnershipRequest
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.UpdateMeRequest
import app.ransel.shared.dto.UpdateMemberRoleRequest
import app.ransel.shared.model.Role
import app.ransel.shared.model.Vote
import app.ransel.shared.dto.DecideRequest
import app.ransel.shared.dto.RoundDto
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.shared.dto.VoteRequest
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlin.time.Instant
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.DayOrderRequest
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.dto.PollShareDto
import app.ransel.shared.dto.GuestPageDto
import app.ransel.shared.dto.GuestIdentityRequest
import app.ransel.shared.dto.GuestIdentityDto
import app.ransel.shared.dto.GuestTickRequest
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.MemberListDto
import app.ransel.shared.dto.SaveMemberListRequest
import app.ransel.shared.dto.SendMemberListRequest
import app.ransel.shared.dto.MemberListProgressDto
import app.ransel.shared.dto.RemindMemberListRequest
import app.ransel.shared.dto.TripDetailDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.UpdateTripRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** Same settings as the server's `ApiJson`, so both sides read and write identical JSON. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private const val REQUEST_TIMEOUT_MILLIS = 15_000L

/**
 * A search runs an LLM call (up to 50 s on the server, retried once) plus Places lookups, so it needs far more
 * than the 15 s of a normal request. The background batch (`newSearch=false`) takes the same path.
 * Keep it in step with the server's LLM timeouts and nginx's `proxy_read_timeout` (120 s), and below Android's OkHttp read timeout (150 s).
 */
private const val RECOMMENDATION_TIMEOUT_MILLIS = 120_000L

/**
 * HTTP client for the API. [engine] is only passed by tests (MockEngine); otherwise the platform's
 * engine is used (OkHttp, Darwin, or the browser `fetch`).
 */
fun createApiHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false // the status decides success, we map it ourselves
        install(ContentNegotiation) { json(ApiJson) }
        // Only the request timeout: the browser engine supports no connect/socket timeouts.
        install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
    }
    return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
}

/**
 * Typed access to the REST API (`ApiRoutes`). Every call returns an [ApiResult]; nothing throws except
 * coroutine cancellation. [origin] is scheme + host (+ port), without `/api/v1`.
 */
class RanselApi(
    private val client: HttpClient,
    private val origin: String,
    /** Adds the bearer token and renews it once on 401. Null before Phase 3 / in tests. */
    private val auth: ApiAuth? = null,
    /** Reports responses the app cannot read (a contract mismatch, never a network problem). */
    private val crashes: CrashReporter = NoCrashReporter,
) {
    private fun url(path: String) = origin.trimEnd('/') + ApiRoutes.BASE + path

    // ── auth & account (Phase 3) ────────────────────────────────────────────
    suspend fun register(request: RegisterRequest): ApiResult<AuthSessionDto> =
        send(HttpMethod.Post, ApiRoutes.AUTH_REGISTER, request)
    suspend fun login(request: LoginRequest): ApiResult<AuthSessionDto> =
        send(HttpMethod.Post, ApiRoutes.AUTH_LOGIN, request)
    suspend fun refresh(refreshToken: String): ApiResult<AuthSessionDto> =
        send(HttpMethod.Post, ApiRoutes.AUTH_REFRESH, RefreshRequest(refreshToken))
    suspend fun logout(refreshToken: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.AUTH_LOGOUT, bodyOf = { contentType(ContentType.Application.Json); setBody(RefreshRequest(refreshToken)) }) { }
    suspend fun forgotPassword(email: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.AUTH_PASSWORD_FORGOT, bodyOf = { contentType(ContentType.Application.Json); setBody(ForgotPasswordRequest(email)) }) { }
    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.AUTH_PASSWORD_RESET, bodyOf = { contentType(ContentType.Application.Json); setBody(ResetPasswordRequest(token, newPassword)) }) { }
    suspend fun verifyEmail(token: String): ApiResult<VerifyEmailResponse> =
        send(HttpMethod.Post, ApiRoutes.AUTH_EMAIL_VERIFY, VerifyEmailRequest(token))
    suspend fun resendVerificationEmail(): ApiResult<Unit> = call(HttpMethod.Post, ApiRoutes.AUTH_EMAIL_RESEND) { }
    suspend fun me(): ApiResult<MeDto> = get(ApiRoutes.ME)
    suspend fun updateMe(request: UpdateMeRequest): ApiResult<MeDto> = send(HttpMethod.Patch, ApiRoutes.ME, request)

    // ── sync (mobile, Phase 3) ──────────────────────────────────────────────
    suspend fun syncPull(since: Instant?): ApiResult<SyncPullResponse> =
        call(HttpMethod.Get, ApiRoutes.SYNC, bodyOf = { if (since != null) parameter("since", since.toString()) }) {
            it.body<ApiResponse<SyncPullResponse>>().data
        }
    suspend fun syncPush(request: SyncPushRequest): ApiResult<SyncPushResponse> =
        send(HttpMethod.Post, ApiRoutes.SYNC, request)

    // ── trips ───────────────────────────────────────────────────────────────
    suspend fun trips(): ApiResult<List<TripDto>> = get(ApiRoutes.TRIPS)
    suspend fun tripDetail(tripId: String): ApiResult<TripDetailDto> = get(ApiRoutes.trip(tripId))
    suspend fun createTrip(request: CreateTripRequest): ApiResult<TripDto> = send(HttpMethod.Post, ApiRoutes.TRIPS, request)
    suspend fun updateTrip(tripId: String, request: UpdateTripRequest): ApiResult<TripDto> =
        send(HttpMethod.Put, ApiRoutes.trip(tripId), request)
    suspend fun deleteTrip(tripId: String): ApiResult<Unit> = delete(ApiRoutes.trip(tripId))

    // ── itinerary ───────────────────────────────────────────────────────────
    suspend fun itinerary(tripId: String): ApiResult<List<ItineraryItemDto>> = get(ApiRoutes.itinerary(tripId))
    suspend fun createItineraryItem(tripId: String, input: ItineraryItemInput): ApiResult<ItineraryItemDto> =
        send(HttpMethod.Post, ApiRoutes.itinerary(tripId), input)
    suspend fun replaceItineraryItem(tripId: String, input: ItineraryItemInput): ApiResult<ItineraryItemDto> =
        send(HttpMethod.Put, ApiRoutes.itineraryItem(tripId, input.id), input)
    suspend fun deleteItineraryItem(tripId: String, itemId: String): ApiResult<Unit> =
        delete(ApiRoutes.itineraryItem(tripId, itemId))
    suspend fun reorderDay(tripId: String, date: LocalDate, request: DayOrderRequest): ApiResult<List<ItineraryItemDto>> =
        send(HttpMethod.Put, ApiRoutes.dayOrder(tripId, date.toString()), request)

    // ── packing ─────────────────────────────────────────────────────────────
    suspend fun packing(tripId: String): ApiResult<List<PackingItemDto>> = get(ApiRoutes.packing(tripId))
    suspend fun createPackingItem(tripId: String, request: CreatePackingItemRequest): ApiResult<PackingItemDto> =
        send(HttpMethod.Post, ApiRoutes.packing(tripId), request)
    suspend fun patchPackingItem(tripId: String, itemId: String, patch: PatchPackingItemRequest): ApiResult<PackingItemDto> =
        send(HttpMethod.Patch, ApiRoutes.packingItem(tripId, itemId), patch)
    suspend fun deletePackingItem(tripId: String, itemId: String): ApiResult<Unit> =
        delete(ApiRoutes.packingItem(tripId, itemId))
    suspend fun uncheckAllPacking(tripId: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.packingUncheckAll(tripId)) { }
    suspend fun bringPackingItem(tripId: String, itemId: String): ApiResult<PackingItemDto> =
        call(HttpMethod.Post, ApiRoutes.packingBring(tripId, itemId)) { it.body<ApiResponse<PackingItemDto>>().data }
    suspend fun giveBackPackingItem(tripId: String, itemId: String): ApiResult<PackingItemDto> =
        call(HttpMethod.Delete, ApiRoutes.packingBring(tripId, itemId)) { it.body<ApiResponse<PackingItemDto>>().data }

    // ── sharing and the guest page (stage 8) ────────────────────────────────
    suspend fun sharePoll(pollId: String): ApiResult<PollShareDto> =
        call(HttpMethod.Post, ApiRoutes.pollShare(pollId)) { it.body<ApiResponse<PollShareDto>>().data }
    suspend fun unsharePoll(pollId: String): ApiResult<Unit> = delete(ApiRoutes.pollShare(pollId))
    suspend fun releaseGuestName(tripId: String, memberId: String): ApiResult<Unit> = delete(ApiRoutes.memberGuestSession(tripId, memberId))

    /** Public: no bearer; the browser's guest token for the trip, if it has one. */
    suspend fun guestPage(code: String, token: String?): ApiResult<GuestPageDto> =
        call(HttpMethod.Get, ApiRoutes.guestPage(code), bodyOf = { guestToken(token) }) { it.body<ApiResponse<GuestPageDto>>().data }
    suspend fun guestIdentity(code: String, token: String?, request: GuestIdentityRequest): ApiResult<GuestIdentityDto> =
        call(HttpMethod.Post, ApiRoutes.guestIdentity(code), bodyOf = { guestToken(token); contentType(ContentType.Application.Json); setBody(request) }) {
            it.body<ApiResponse<GuestIdentityDto>>().data
        }
    suspend fun guestVote(code: String, token: String?, pollId: String, request: VotePollRequest): ApiResult<PollDto> =
        call(HttpMethod.Post, ApiRoutes.guestVotes(code, pollId), bodyOf = { guestToken(token); contentType(ContentType.Application.Json); setBody(request) }) {
            it.body<ApiResponse<PollDto>>().data
        }
    suspend fun guestTick(code: String, token: String?, itemId: String, isPacked: Boolean): ApiResult<PersonalItemDto> =
        call(HttpMethod.Patch, ApiRoutes.guestPersonalItem(code, itemId), bodyOf = { guestToken(token); contentType(ContentType.Application.Json); setBody(GuestTickRequest(isPacked)) }) {
            it.body<ApiResponse<PersonalItemDto>>().data
        }

    private fun io.ktor.client.request.HttpRequestBuilder.guestToken(token: String?) {
        token?.let { header(ApiRoutes.GUEST_TOKEN_HEADER, it) }
    }

    // ── group packing (stage 7) ─────────────────────────────────────────────
    suspend fun personalItems(tripId: String): ApiResult<List<PersonalItemDto>> = get(ApiRoutes.personalItems(tripId))
    suspend fun createPersonalItem(tripId: String, request: CreatePersonalItemRequest): ApiResult<PersonalItemDto> =
        send(HttpMethod.Post, ApiRoutes.personalItems(tripId), request)
    suspend fun patchPersonalItem(tripId: String, itemId: String, patch: PatchPersonalItemRequest): ApiResult<PersonalItemDto> =
        send(HttpMethod.Patch, ApiRoutes.personalItem(tripId, itemId), patch)
    suspend fun deletePersonalItem(tripId: String, itemId: String): ApiResult<Unit> = delete(ApiRoutes.personalItem(tripId, itemId))
    suspend fun memberList(tripId: String): ApiResult<MemberListDto> = get(ApiRoutes.memberList(tripId))
    suspend fun saveMemberList(tripId: String, request: SaveMemberListRequest): ApiResult<MemberListDto> =
        send(HttpMethod.Put, ApiRoutes.memberList(tripId), request)
    suspend fun sendMemberList(tripId: String, request: SendMemberListRequest): ApiResult<MemberListDto> =
        send(HttpMethod.Post, ApiRoutes.memberListSend(tripId), request)
    suspend fun memberListProgress(tripId: String): ApiResult<List<MemberListProgressDto>> = get(ApiRoutes.memberListProgress(tripId))
    suspend fun remindMemberList(tripId: String, memberIds: List<String>): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.memberListRemind(tripId), bodyOf = { contentType(ContentType.Application.Json); setBody(RemindMemberListRequest(memberIds)) }) { }

    // ── expenses ────────────────────────────────────────────────────────────
    suspend fun expenses(tripId: String): ApiResult<List<ExpenseDto>> = get(ApiRoutes.expenses(tripId))
    suspend fun createExpense(tripId: String, input: ExpenseInput): ApiResult<ExpenseDto> =
        send(HttpMethod.Post, ApiRoutes.expenses(tripId), input)
    suspend fun replaceExpense(tripId: String, input: ExpenseInput): ApiResult<ExpenseDto> =
        send(HttpMethod.Put, ApiRoutes.expense(tripId, input.id), input)
    suspend fun deleteExpense(tripId: String, expenseId: String): ApiResult<Unit> =
        delete(ApiRoutes.expense(tripId, expenseId))

    // ── members (Phase 4) ───────────────────────────────────────────────────
    suspend fun members(tripId: String): ApiResult<List<TripMemberDto>> = get(ApiRoutes.members(tripId))
    suspend fun updateMemberRole(tripId: String, memberId: String, role: Role): ApiResult<Unit> =
        call(HttpMethod.Patch, ApiRoutes.member(tripId, memberId), bodyOf = { contentType(ContentType.Application.Json); setBody(UpdateMemberRoleRequest(role)) }) { }
    suspend fun removeMember(tripId: String, memberId: String): ApiResult<Unit> = delete(ApiRoutes.member(tripId, memberId))
    /** OWNER: adds a person by name, without an account. */
    suspend fun addMember(tripId: String, request: AddMemberRequest): ApiResult<TripMemberDto> =
        send(HttpMethod.Post, ApiRoutes.members(tripId), request)
    /** "Kamu yang mana?": the signed-in caller takes over a member added by name. */
    suspend fun claimMember(tripId: String, memberId: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.memberClaim(tripId, memberId)) { }
    /** OWNER: undoes a wrong claim. */
    suspend fun undoClaim(tripId: String, memberId: String): ApiResult<Unit> = delete(ApiRoutes.memberClaim(tripId, memberId))

    // ── polls (online only) ─────────────────────────────────────────────────
    suspend fun polls(tripId: String): ApiResult<List<PollDto>> = get(ApiRoutes.polls(tripId))
    suspend fun poll(pollId: String): ApiResult<PollDto> = get(ApiRoutes.poll(pollId))
    suspend fun createPoll(tripId: String, request: CreatePollRequest): ApiResult<PollDto> =
        send(HttpMethod.Post, ApiRoutes.polls(tripId), request)
    suspend fun updatePoll(pollId: String, request: UpdatePollRequest): ApiResult<PollDto> =
        send(HttpMethod.Patch, ApiRoutes.poll(pollId), request)
    suspend fun deletePoll(pollId: String): ApiResult<Unit> = delete(ApiRoutes.poll(pollId))
    suspend fun addPollOption(pollId: String, option: PollOptionInput): ApiResult<PollDto> =
        send(HttpMethod.Post, ApiRoutes.pollOptions(pollId), option)
    suspend fun addPollOptionsBulk(pollId: String, options: List<PollOptionInput>): ApiResult<PollDto> =
        send(HttpMethod.Post, ApiRoutes.pollOptionsBulk(pollId), BulkPollOptionsRequest(options))
    suspend fun searchPoll(pollId: String, request: PollSearchRequest): ApiResult<PollSearchResponse> =
        send(HttpMethod.Post, ApiRoutes.pollSearch(pollId), request)
    suspend fun linkPreview(url: String): ApiResult<LinkPreviewDto> =
        send(HttpMethod.Post, ApiRoutes.LINK_PREVIEW, LinkPreviewRequest(url))
    suspend fun updatePollOption(pollId: String, option: PollOptionInput): ApiResult<PollDto> =
        send(HttpMethod.Patch, ApiRoutes.pollOption(pollId, option.id), option)
    suspend fun deletePollOption(pollId: String, optionId: String): ApiResult<PollDto> =
        call(HttpMethod.Delete, ApiRoutes.pollOption(pollId, optionId)) { it.body<ApiResponse<PollDto>>().data }
    suspend fun votePoll(pollId: String, request: VotePollRequest): ApiResult<PollDto> =
        send(HttpMethod.Put, ApiRoutes.pollVotes(pollId), request)
    suspend fun closePoll(pollId: String): ApiResult<PollDto> =
        call(HttpMethod.Post, ApiRoutes.pollClose(pollId)) { it.body<ApiResponse<PollDto>>().data }
    suspend fun decidePoll(pollId: String, request: DecidePollRequest): ApiResult<PollDto> =
        send(HttpMethod.Post, ApiRoutes.pollDecide(pollId), request)
    suspend fun reopenPoll(pollId: String, request: ReopenPollRequest): ApiResult<PollDto> =
        send(HttpMethod.Post, ApiRoutes.pollReopen(pollId), request)

    // ── comments (online only) ──────────────────────────────────────────────
    suspend fun comments(tripId: String, kind: CommentTarget, targetId: String): ApiResult<List<CommentDto>> = call(
        HttpMethod.Get,
        ApiRoutes.comments(tripId),
        bodyOf = { parameter("targetKind", kind.name); parameter("targetId", targetId) },
    ) { it.body<ApiResponse<List<CommentDto>>>().data }
    suspend fun commentCounts(tripId: String, kind: CommentTarget): ApiResult<List<CommentCountDto>> = call(
        HttpMethod.Get,
        ApiRoutes.commentCounts(tripId),
        bodyOf = { parameter("targetKind", kind.name) },
    ) { it.body<ApiResponse<List<CommentCountDto>>>().data }
    suspend fun createComment(tripId: String, request: CreateCommentRequest): ApiResult<CommentDto> =
        send(HttpMethod.Post, ApiRoutes.comments(tripId), request)
    suspend fun updateComment(commentId: String, request: UpdateCommentRequest): ApiResult<CommentDto> =
        send(HttpMethod.Patch, ApiRoutes.comment(commentId), request)
    suspend fun deleteComment(commentId: String): ApiResult<Unit> = delete(ApiRoutes.comment(commentId))
    suspend fun leaveTrip(tripId: String): ApiResult<Unit> = call(HttpMethod.Post, ApiRoutes.leave(tripId)) { }
    suspend fun transferOwnership(tripId: String, newOwnerUserId: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.transferOwnership(tripId), bodyOf = { contentType(ContentType.Application.Json); setBody(TransferOwnershipRequest(newOwnerUserId)) }) { }

    // ── balances & settlements (Phase 4) ────────────────────────────────────
    suspend fun balances(tripId: String): ApiResult<BalancesDto> = get(ApiRoutes.balances(tripId))
    suspend fun settlements(tripId: String): ApiResult<List<SettlementDto>> = get(ApiRoutes.settlements(tripId))
    suspend fun createSettlement(tripId: String, request: CreateSettlementRequest): ApiResult<SettlementDto> =
        send(HttpMethod.Post, ApiRoutes.settlements(tripId), request)
    /** "Saya sudah bayar": the caller's own member paid; the receiver confirms later. */
    suspend fun claimSettlement(tripId: String, request: ClaimSettlementRequest): ApiResult<SettlementDto> =
        send(HttpMethod.Post, ApiRoutes.settlementClaim(tripId), request)
    suspend fun confirmSettlement(tripId: String, settlementId: String): ApiResult<SettlementDto> =
        call(HttpMethod.Post, ApiRoutes.settlementConfirm(tripId, settlementId)) { it.body<ApiResponse<SettlementDto>>().data }
    suspend fun deleteSettlement(tripId: String, settlementId: String): ApiResult<Unit> =
        delete(ApiRoutes.settlement(tripId, settlementId))
    suspend fun payInfo(tripId: String, fromMemberId: String, toMemberId: String): ApiResult<PayInfoDto> =
        get(ApiRoutes.payInfo(tripId, fromMemberId, toMemberId))

    // ── destination search ──────────────────────────────────────────────────
    /** Cancelled with the caller's coroutine, so a newer key press drops the older request. */
    suspend fun placeAutocomplete(query: String, session: String?, near: Pair<Double, Double>? = null): ApiResult<PlaceSuggestionsDto> =
        call(HttpMethod.Get, ApiRoutes.PLACES_AUTOCOMPLETE, bodyOf = {
            parameter("q", query)
            session?.let { parameter("session", it) }
            near?.let { parameter("near", "${it.first},${it.second}") }
        }) { it.body<ApiResponse<PlaceSuggestionsDto>>().data }
    suspend fun place(placeId: String, session: String?): ApiResult<PlaceDto> =
        call(HttpMethod.Get, ApiRoutes.place(placeId), bodyOf = { session?.let { parameter("session", it) } }) {
            it.body<ApiResponse<PlaceDto>>().data
        }
    /** The coordinates go to the server once and are not kept anywhere. */
    suspend fun reverseGeocode(lat: Double, lng: Double): ApiResult<ReverseGeocodeDto> =
        call(HttpMethod.Get, ApiRoutes.PLACES_REVERSE, bodyOf = {
            parameter("lat", lat)
            parameter("lng", lng)
        }) { it.body<ApiResponse<ReverseGeocodeDto>>().data }
    suspend fun popularDestinations(): ApiResult<List<PopularDestinationDto>> = get(ApiRoutes.PLACES_POPULAR)
    /** 1 [from] in [to] on [date] (the latest earlier day when that one has no rate). */
    suspend fun fxRate(from: String, to: String, date: LocalDate): ApiResult<FxRateDto> =
        call(HttpMethod.Get, ApiRoutes.FX_RATE, bodyOf = {
            parameter("from", from)
            parameter("to", to)
            parameter("date", date.toString())
        }) { it.body<ApiResponse<FxRateDto>>().data }

    // ── group money: kitty, payment info, receipts ──────────────────────────
    suspend fun kitty(tripId: String): ApiResult<KittyOverviewDto> = get(ApiRoutes.kitty(tripId))
    suspend fun setKitty(tripId: String, request: KittyInput): ApiResult<KittyOverviewDto> =
        send(HttpMethod.Put, ApiRoutes.kitty(tripId), request)
    suspend fun addDeposit(tripId: String, request: KittyDepositInput): ApiResult<KittyDepositDto> =
        send(HttpMethod.Post, ApiRoutes.kittyDeposits(tripId), request)
    suspend fun deleteDeposit(tripId: String, depositId: String): ApiResult<Unit> = delete(ApiRoutes.kittyDeposit(tripId, depositId))
    suspend fun paymentMethods(): ApiResult<List<PaymentMethodDto>> = get(ApiRoutes.ME_PAYMENT_METHODS)
    suspend fun addPaymentMethod(request: PaymentMethodInput): ApiResult<PaymentMethodDto> =
        send(HttpMethod.Post, ApiRoutes.ME_PAYMENT_METHODS, request)
    suspend fun updatePaymentMethod(id: String, request: PaymentMethodInput): ApiResult<PaymentMethodDto> =
        send(HttpMethod.Patch, ApiRoutes.mePaymentMethod(id), request)
    suspend fun deletePaymentMethod(id: String): ApiResult<Unit> = delete(ApiRoutes.mePaymentMethod(id))

    /** A receipt photo ([mimeType] image/jpeg, png or webp) read by the server's AI; costs credits. */
    suspend fun parseReceipt(tripId: String, image: ByteArray, mimeType: String): ApiResult<ReceiptDraftDto> =
        call(HttpMethod.Post, ApiRoutes.RECEIPTS_PARSE, bodyOf = {
            parameter("tripId", tripId)
            contentType(ContentType.parse(mimeType))
            setBody(image)
            timeout { requestTimeoutMillis = RECOMMENDATION_TIMEOUT_MILLIS }
        }) { it.body<ApiResponse<ReceiptDraftDto>>().data }

    // ── packing templates (Phase 4) ─────────────────────────────────────────
    suspend fun packingTemplates(): ApiResult<List<PackingTemplateDto>> = get(ApiRoutes.PACKING_TEMPLATES)
    suspend fun createPackingTemplate(request: CreatePackingTemplateRequest): ApiResult<PackingTemplateDto> =
        send(HttpMethod.Post, ApiRoutes.PACKING_TEMPLATES, request)

    // ── Explore AI (Phase 5) ────────────────────────────────────────────────
    /**
     * Destination interests for the form: pass [tripId] (existing trip) or [destination] + [month] as `YYYY-MM`
     * (new trip). Never counts against the daily quota. [language] is "id" or "en"; null = let the engine decide.
     */
    suspend fun exploreSuggestions(
        tripId: String? = null,
        destination: String? = null,
        month: String? = null,
        language: String? = null,
    ): ApiResult<ExploreSuggestionsResponse> = call(
        HttpMethod.Get,
        ApiRoutes.EXPLORE_SUGGESTIONS,
        bodyOf = {
            tripId?.let { parameter("tripId", it) }
            destination?.let { parameter("destination", it) }
            month?.let { parameter("month", it) }
            language?.let { header(HttpHeaders.AcceptLanguage, it) }
        },
    ) { it.body<ApiResponse<ExploreSuggestionsResponse>>().data }

    suspend fun recommend(request: RecommendationRequest): ApiResult<RecommendationResponse> =
        send(HttpMethod.Post, ApiRoutes.RECOMMENDATIONS, request, timeoutMillis = RECOMMENDATION_TIMEOUT_MILLIS)
    suspend fun sendFeedback(request: FeedbackRequest): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.RECOMMENDATION_FEEDBACK, bodyOf = { contentType(ContentType.Application.Json); setBody(request) }) { }
    suspend fun undoFeedback(tripId: String, placeId: String): ApiResult<Unit> =
        delete(ApiRoutes.recommendationFeedback(tripId, placeId))

    /** Detail text of a place for the itinerary entry; [name] only matters when the server must rebuild an expired text. */
    suspend fun placeDetail(placeId: String, name: String?, currency: String?): ApiResult<PlaceDetailDto> = call(
        HttpMethod.Get,
        ApiRoutes.placeDetail(placeId),
        bodyOf = {
            name?.let { parameter("name", it) }
            currency?.let { parameter("currency", it) }
        },
    ) { it.body<ApiResponse<PlaceDetailDto>>().data }

    // ── itinerary voting ────────────────────────────────────────────────────
    suspend fun rounds(tripId: String): ApiResult<List<RoundSummaryDto>> = get(ApiRoutes.rounds(tripId))
    suspend fun round(tripId: String, roundId: String): ApiResult<RoundDto> = get(ApiRoutes.round(tripId, roundId))
    suspend fun vote(tripId: String, roundId: String, placeId: String, vote: Vote): ApiResult<Unit> =
        call(HttpMethod.Put, ApiRoutes.roundVote(tripId, roundId, placeId), bodyOf = { contentType(ContentType.Application.Json); setBody(VoteRequest(vote)) }) { }
    suspend fun undoVote(tripId: String, roundId: String, placeId: String): ApiResult<Unit> =
        delete(ApiRoutes.roundVote(tripId, roundId, placeId))
    suspend fun closeRound(tripId: String, roundId: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.roundClose(tripId, roundId)) { }
    suspend fun decideRound(tripId: String, roundId: String, placeIds: List<String>): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.roundDecide(tripId, roundId), bodyOf = { contentType(ContentType.Application.Json); setBody(DecideRequest(placeIds)) }) { }

    // ── Explore credits ─────────────────────────────────────────────────────
    suspend fun credits(): ApiResult<CreditsDto> = get(ApiRoutes.CREDITS)
    suspend fun creditPackages(): ApiResult<CreditPackagesResponse> = get(ApiRoutes.CREDIT_PACKAGES)
    suspend fun creditLedger(limit: Int = 50): ApiResult<List<CreditLedgerEntryDto>> = call(
        HttpMethod.Get,
        ApiRoutes.CREDITS_LEDGER,
        bodyOf = { parameter("limit", limit) },
    ) { it.body<ApiResponse<List<CreditLedgerEntryDto>>>().data }
    suspend fun redeemVoucher(code: String): ApiResult<RedeemVoucherResponse> =
        send(HttpMethod.Post, ApiRoutes.CREDITS_REDEEM, RedeemVoucherRequest(code))

    // ── notifications ───────────────────────────────────────────────────────
    suspend fun registerDevice(request: RegisterDeviceRequest): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.DEVICES, bodyOf = { contentType(ContentType.Application.Json); setBody(request) }) { }
    suspend fun unregisterDevice(token: String): ApiResult<Unit> =
        call(HttpMethod.Delete, ApiRoutes.DEVICES, bodyOf = { parameter("token", token) }) { }
    suspend fun notifications(limit: Int = 30, before: String? = null): ApiResult<NotificationsDto> = call(
        HttpMethod.Get,
        ApiRoutes.NOTIFICATIONS,
        bodyOf = { parameter("limit", limit); before?.let { parameter("before", it) } },
    ) { it.body<ApiResponse<NotificationsDto>>().data }
    /** Null [ids] marks everything read. */
    suspend fun markNotificationsRead(ids: List<String>?): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.NOTIFICATIONS_READ, bodyOf = { contentType(ContentType.Application.Json); setBody(MarkReadRequest(ids)) }) { }
    suspend fun notificationSettings(): ApiResult<NotificationSettingsDto> = get(ApiRoutes.NOTIFICATION_SETTINGS)
    suspend fun updateNotificationSettings(request: UpdateNotificationSettingsRequest): ApiResult<NotificationSettingsDto> =
        send(HttpMethod.Patch, ApiRoutes.NOTIFICATION_SETTINGS, request)

    // ── invites (Phase 4) ───────────────────────────────────────────────────
    suspend fun invites(tripId: String): ApiResult<List<InviteDto>> = get(ApiRoutes.invites(tripId))
    suspend fun createInvite(tripId: String, request: CreateInviteRequest): ApiResult<InviteDto> =
        send(HttpMethod.Post, ApiRoutes.invites(tripId), request)
    suspend fun revokeInvite(tripId: String, code: String): ApiResult<Unit> = delete(ApiRoutes.invite(tripId, code))
    suspend fun invitePreview(code: String): ApiResult<InvitePreviewDto> = get(ApiRoutes.invitePreview(code))
    suspend fun acceptInvite(code: String): ApiResult<TripDto> =
        call(HttpMethod.Post, ApiRoutes.inviteAccept(code)) { it.body<ApiResponse<TripDto>>().data }

    // ── plumbing ────────────────────────────────────────────────────────────
    private suspend inline fun <reified T> get(path: String): ApiResult<T> =
        call(HttpMethod.Get, path) { it.body<ApiResponse<T>>().data }

    private suspend inline fun <reified B : Any, reified T> send(
        method: HttpMethod,
        path: String,
        body: B,
        timeoutMillis: Long? = null,
    ): ApiResult<T> =
        call(method, path, bodyOf = {
            contentType(ContentType.Application.Json)
            setBody(body)
            if (timeoutMillis != null) timeout { requestTimeoutMillis = timeoutMillis }
        }) {
            it.body<ApiResponse<T>>().data
        }

    private suspend fun delete(path: String): ApiResult<Unit> = call(HttpMethod.Delete, path) { }

    /**
     * One API call. Attaches the bearer token (not on the auth endpoints); on a 401 it renews the token once and
     * repeats the request. Anything else is mapped to an [ApiFailure]; nothing throws except cancellation.
     */
    private suspend inline fun <T> call(
        method: HttpMethod,
        path: String,
        noinline bodyOf: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
        crossinline parse: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val needsToken = auth != null && !path.startsWith("/auth/")
        var renewed = false
        while (true) {
            val outcome: ApiResult<T> = try {
                val response = client.request(url(path)) {
                    this.method = method
                    bodyOf()
                    if (needsToken) auth?.accessToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
                when {
                    response.status.isSuccess() -> ApiResult.Success(parse(response))
                    response.status.value == 401 && needsToken && !renewed -> {
                        renewed = true
                        if (auth!!.refreshAccessToken()) continue
                        ApiResult.Failure(response.toFailure())
                    }
                    else -> ApiResult.Failure(response.toFailure())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                ApiResult.Failure(ApiFailure(ErrorCode.NETWORK_TIMEOUT, "The request timed out"))
            } catch (e: kotlinx.serialization.SerializationException) {
                crashes.record(UnreadableResponseException(method, path, e))
                ApiResult.Failure(ApiFailure(ErrorCode.INTERNAL_ERROR, "Unexpected response from the server"))
            } catch (e: io.ktor.serialization.ContentConvertException) {
                crashes.record(UnreadableResponseException(method, path, e))
                ApiResult.Failure(ApiFailure(ErrorCode.INTERNAL_ERROR, "Unexpected response from the server"))
            } catch (e: Throwable) {
                ApiResult.Failure(ApiFailure(ErrorCode.NETWORK_UNAVAILABLE, e.message ?: "No connection"))
            }
            return outcome
        }
    }

    private suspend fun HttpResponse.toFailure(): ApiFailure {
        val parsed = runCatching { body<ApiErrorResponse>().error }.getOrNull()
        val statusCode = status.value
        if (parsed != null) return ApiFailure(parsed.code, parsed.message, statusCode, parsed.details)
        return when {
            statusCode == 404 -> ApiFailure(ErrorCode.NOT_FOUND, "Not found", statusCode)
            statusCode == 401 -> ApiFailure(ErrorCode.UNAUTHORIZED, "Not signed in", statusCode)
            statusCode == 403 -> ApiFailure(ErrorCode.FORBIDDEN, "Not allowed", statusCode)
            statusCode >= 500 -> ApiFailure(ErrorCode.INTERNAL_ERROR, "Server error", statusCode)
            else -> ApiFailure(ErrorCode.VALIDATION_FAILED, "Request rejected", statusCode)
        }
    }
}

/**
 * A response the client could not parse. The path keeps only its fixed words: ids, invite codes and tokens become
 * `{}` ("/trips/{}/expenses"), and the query is dropped, so nothing secret ends up in a crash report.
 */
class UnreadableResponseException(method: HttpMethod, path: String, cause: Throwable) :
    Exception("Unreadable response: ${method.value} ${routePattern(path)}", cause)

internal fun routePattern(path: String): String =
    path.substringBefore('?').split('/').joinToString("/") { if (it.all { c -> c in 'a'..'z' || c == '-' }) it else "{}" }
