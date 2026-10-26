package app.ransel.server.db

import app.ransel.shared.model.Category
import app.ransel.shared.model.CommentTarget
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollPriceUnit
import app.ransel.shared.model.PollStatus
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PaymentMethod
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import app.ransel.shared.dto.TemplateItem
import app.ransel.server.ai.CachedDetail
import app.ransel.server.ai.CachedPlace
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.CreditLedgerKind
import app.ransel.shared.model.CreditSource
import app.ransel.shared.model.DevicePlatform
import app.ransel.shared.model.FeedbackAction
import app.ransel.shared.model.NotificationType
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.model.Vote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import app.ransel.shared.model.AdminAction
import app.ransel.shared.dto.PlaceDto
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.shared.dto.ViewportDto
import app.ransel.shared.model.FxSource
import app.ransel.shared.model.PaymentMethodKind
import app.ransel.shared.model.SettlementStatus

/*
 * Exposed mappings of the Flyway schema (V1..V21). Flyway owns the DDL; these objects only describe it.
 * `updated_at` is set by the DB trigger, so writers never set it.
 */

object Users : Table("users") {
    val id = javaUUID("id")
    val displayName = text("display_name")
    /** Null only for the seeded Phase 2 dummy user (guest data); real accounts always have both. */
    val email = text("email").nullable()
    val passwordHash = text("password_hash").nullable()
    val defaultCurrency = char("default_currency", 3)
    val createdAt = timestamp("created_at")
    val deletedAt = timestamp("deleted_at").nullable()
    /** Set by an admin: sign-in and refresh answer 403 ACCOUNT_DISABLED. */
    val disabledAt = timestamp("disabled_at").nullable()
    /** Null until the sign-up email link is opened (V13); Explore and vouchers wait for it. */
    val emailVerifiedAt = timestamp("email_verified_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Trips : Table("trips") {
    val id = javaUUID("id")
    val ownerId = javaUUID("owner_id")
    val title = text("title")
    val destination = text("destination")
    val startDate = date("start_date")
    val endDate = date("end_date")
    val baseCurrency = char("base_currency", 3)
    val budgetMinor = long("budget_minor").nullable()
    val coverUrl = text("cover_url").nullable()
    /** The picked destination's place (V19); all null for a typed name. */
    val destPlaceId = text("dest_place_id").nullable()
    val destLat = double("dest_lat").nullable()
    val destLng = double("dest_lng").nullable()
    val destViewport = jsonb<ViewportDto>("dest_viewport", Json).nullable()
    /** V20: destination country, its currency and zone; null = home (Indonesia, base currency, WIB). */
    val countryCode = char("country_code", 2).nullable()
    val localCurrency = char("local_currency", 3).nullable()
    val timeZone = text("time_zone").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object TripMembers : Table("trip_members") {
    /** What expenses, splits, payers and settlements point at. Accounts: shared memberIdFor(tripId, userId). */
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    /** Null = a member added by name, without an account (V15). */
    val userId = javaUUID("user_id").nullable()
    val role = enumerationByName<Role>("role", 16)
    val displayName = text("display_name").nullable()
    /** Digits with country code; only the OWNER may see it. */
    val phone = text("phone").nullable()
    val claimedAt = timestamp("claimed_at").nullable()
    val claimedByMemberId = javaUUID("claimed_by_member_id").nullable()
    /** Set when removed, left or claimed: the row stays so expenses keep a name, but the person is no longer a member. */
    val removedAt = timestamp("removed_at").nullable()
    val joinedAt = timestamp("joined_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)

    /** Current members only: not removed, not left, not claimed. */
    fun active(): Op<Boolean> = removedAt.isNull()
}

/** Which rows moved when a member added by name was claimed (V15); lets the OWNER undo it. */
object MemberClaims : Table("member_claims") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val guestMemberId = javaUUID("guest_member_id")
    val claimedByMemberId = javaUUID("claimed_by_member_id")
    val moved = jsonb<Map<String, List<String>>>("moved", Json)
    val claimedAt = timestamp("claimed_at")
    val undoneAt = timestamp("undone_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Polls : Table("polls") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val kind = enumerationByName<PollKind>("kind", 16)
    val question = text("question")
    val createdByMemberId = javaUUID("created_by_member_id")
    val closesAt = timestamp("closes_at")
    val multiChoice = bool("multi_choice")
    val isAnonymous = bool("is_anonymous")
    val anonymityLocked = bool("anonymity_locked")
    val applyWinner = bool("apply_winner")
    val dayDate = date("day_date").nullable()
    val startTime = time("start_time").nullable()
    val status = enumerationByName<PollStatus>("status", 16)
    val decidedOptionId = javaUUID("decided_option_id").nullable()
    val decidedAt = timestamp("decided_at").nullable()
    val tiedAt = timestamp("tied_at").nullable()
    /** The guest link (V22); null = not shared or turned off. */
    val shareCode = text("share_code").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PollOptions : Table("poll_options") {
    val id = javaUUID("id")
    val pollId = javaUUID("poll_id")
    val label = text("label")
    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()
    val placeId = text("place_id").nullable()
    val url = text("url").nullable()
    val imageUrl = text("image_url").nullable()
    val priceMinor = long("price_minor").nullable()
    val priceUnit = enumerationByName<PollPriceUnit>("price_unit", 8).nullable()
    val minMinor = long("min_minor").nullable()
    val maxMinor = long("max_minor").nullable()
    /** Goes up when what people voted on changes; a vote on an older version no longer counts. */
    val version = integer("version")
    val sortOrder = integer("sort_order")
    val createdAt = timestamp("created_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PollVotes : Table("poll_votes") {
    val optionId = javaUUID("option_id")
    val pollId = javaUUID("poll_id")
    val memberId = javaUUID("member_id")
    val optionVersion = integer("option_version")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(optionId, memberId)
}

object LinkPreviews : Table("link_previews") {
    val urlHash = text("url_hash")
    val url = text("url")
    val finalUrl = text("final_url")
    val title = text("title").nullable()
    val imageUrl = text("image_url").nullable()
    val siteName = text("site_name").nullable()
    val address = text("address").nullable()
    val ratingValue = double("rating_value").nullable()
    val ratingCount = integer("rating_count").nullable()
    val fetchedAt = timestamp("fetched_at")
    override val primaryKey = PrimaryKey(urlHash)
}

object Comments : Table("comments") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val targetKind = enumerationByName<CommentTarget>("target_kind", 16)
    val targetId = javaUUID("target_id")
    val authorMemberId = javaUUID("author_member_id")
    val body = text("body")
    val mentions = jsonb<List<String>>("mentions", Json)
    val createdAt = timestamp("created_at")
    val editedAt = timestamp("edited_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ItineraryItems : Table("itinerary_items") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val dayDate = date("day_date").nullable()
    val startTime = time("start_time").nullable()
    val endTime = time("end_time").nullable()
    val title = text("title")
    val category = enumerationByName<Category>("category", 16)
    val placeName = text("place_name").nullable()
    val placeId = text("place_id").nullable()
    val lat = double("lat").nullable()
    val lng = double("lng").nullable()
    val durationMin = integer("duration_min").nullable()
    val notes = text("notes").nullable()
    val bookingUrl = text("booking_url").nullable()
    val itemSource = enumerationByName<ItemSource>("source", 16)
    val sortOrder = double("sort_order")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PackingItems : Table("packing_items") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val category = enumerationByName<PackingCategory>("category", 16)
    val name = text("name")
    val quantity = integer("quantity")
    val isPacked = bool("is_packed")
    val sortOrder = double("sort_order")
    /** Bersama: who brings it (V21). */
    val assigneeMemberId = javaUUID("assignee_member_id").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Each member's own packing lines (V21). */
object PersonalItems : Table("personal_items") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val ownerMemberId = javaUUID("owner_member_id")
    val category = enumerationByName<PackingCategory>("category", 16)
    val name = text("name")
    val quantity = integer("quantity")
    val isPacked = bool("is_packed")
    val sortOrder = double("sort_order")
    val sourceItemId = javaUUID("source_item_id").nullable()
    val required = bool("required")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** "Daftar bawaan anggota", one per trip (V21). */
object MemberLists : Table("member_lists") {
    val tripId = javaUUID("trip_id")
    val name = text("name")
    val version = integer("version")
    val sentToAll = bool("sent_to_all")
    val sentAt = timestamp("sent_at").nullable()
    val createdByMemberId = javaUUID("created_by_member_id")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(tripId)
}

object MemberListItems : Table("member_list_items") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val category = enumerationByName<PackingCategory>("category", 16)
    val name = text("name")
    val quantity = integer("quantity")
    val required = bool("required")
    val sortOrder = double("sort_order")
    override val primaryKey = PrimaryKey(id)
}

object MemberListRecipients : Table("member_list_recipients") {
    val tripId = javaUUID("trip_id")
    val memberId = javaUUID("member_id")
    override val primaryKey = PrimaryKey(tripId, memberId)
}

object Expenses : Table("expenses") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val amountMinor = long("amount_minor")
    val currency = char("currency", 3)
    val fxRateToBase = decimal("fx_rate_to_base", 20, 10).nullable()
    val category = enumerationByName<Category>("category", 16)
    val paymentMethod = enumerationByName<PaymentMethod>("payment_method", 16)
    val note = text("note").nullable()
    val spentAt = timestamp("spent_at")
    val paidByMemberId = javaUUID("paid_by_member_id")
    val paidFromKitty = bool("paid_from_kitty")
    /** A planned cost (V18), e.g. the stay chosen in a poll; left out of balances. */
    val isEstimate = bool("is_estimate")
    /** V20: where fx_rate_to_base came from and the day of the reference rate. */
    val fxSource = enumerationByName<FxSource>("fx_source", 8).nullable()
    val fxDate = date("fx_date").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Receipt lines of an expense, for the "per item" split (V18). The splits stay the source of truth. */
object ExpenseItems : Table("expense_items") {
    val expenseId = javaUUID("expense_id")
    val sortOrder = integer("sort_order")
    val name = text("name")
    val amountMinor = long("amount_minor")
    val memberIds = jsonb<List<String>>("member_ids", Json)
    override val primaryKey = PrimaryKey(expenseId, sortOrder)
}

object ExpenseSplits : Table("expense_splits") {
    val expenseId = javaUUID("expense_id")
    val memberId = javaUUID("member_id")
    val shareMinor = long("share_minor")
    override val primaryKey = PrimaryKey(expenseId, memberId)
}

object ExpensePayers : Table("expense_payers") {
    val expenseId = javaUUID("expense_id")
    val memberId = javaUUID("member_id")
    val amountMinor = long("amount_minor")
    override val primaryKey = PrimaryKey(expenseId, memberId)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    /** SHA-256 hex of the token; the token itself is never stored. */
    val tokenHash = text("token_hash")
    val deviceName = text("device_name").nullable()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PasswordResetTokens : Table("password_reset_tokens") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object EmailVerificationTokens : Table("email_verification_tokens") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** A browser on the guest page that picked a member without an account (V22). */
object GuestSessions : Table("guest_sessions") {
    val tokenHash = text("token_hash")
    val tripId = javaUUID("trip_id")
    val memberId = javaUUID("member_id")
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
    override val primaryKey = PrimaryKey(tokenHash)
}

object TripInvites : Table("trip_invites") {
    val code = text("code")
    val tripId = javaUUID("trip_id")
    val role = enumerationByName<Role>("role", 16)
    val createdBy = javaUUID("created_by")
    val maxUses = integer("max_uses").nullable()
    val usedCount = integer("used_count")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(code)
}

object Settlements : Table("settlements") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val fromMemberId = javaUUID("from_member_id")
    val toMemberId = javaUUID("to_member_id")
    val amountMinor = long("amount_minor")
    val settledAt = timestamp("settled_at")
    val status = enumerationByName<SettlementStatus>("status", 16)
    val claimedAt = timestamp("claimed_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** One kitty per trip (V18): contribution per person and the treasurer who holds the money. */
object Kitties : Table("kitty") {
    val tripId = javaUUID("trip_id")
    val amountPerMemberMinor = long("amount_per_member_minor")
    val treasurerMemberId = javaUUID("treasurer_member_id")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(tripId)
}

object KittyDeposits : Table("kitty_deposits") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val memberId = javaUUID("member_id")
    val amountMinor = long("amount_minor")
    val receivedByMemberId = javaUUID("received_by_member_id")
    val receivedAt = timestamp("received_at")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** ECB reference rates: units of [currency] per 1 EUR, per day (V20). */
object FxRates : Table("fx_rates") {
    val rateDate = date("rate_date")
    val currency = char("currency", 3)
    val perEuro = decimal("per_euro", 20, 8)
    override val primaryKey = PrimaryKey(rateDate, currency)
}

/** Suggestions per normalised query, 24 hours (V19). */
object PlaceAutocompleteCache : Table("place_autocomplete_cache") {
    val queryKey = text("query_key")
    val suggestions = jsonb<List<PlaceSuggestionDto>>("suggestions", Json)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(queryKey)
}

/** Where a Google place is, 30 days (V19). */
object PlaceDetailsCache : Table("place_details_cache") {
    val placeId = text("place_id")
    val place = jsonb<PlaceDto>("place", Json)
    val fetchedAt = timestamp("fetched_at")
    override val primaryKey = PrimaryKey(placeId)
}

/** Account → Info pembayaran (V18). */
object PaymentMethods : Table("payment_methods") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val kind = enumerationByName<PaymentMethodKind>("kind", 16)
    val provider = text("provider")
    val accountNumber = text("account_number")
    val holderName = text("holder_name")
    val isPrimary = bool("is_primary")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PackingTemplates : Table("packing_templates") {
    val id = javaUUID("id")
    /** Null = built-in. */
    val ownerId = javaUUID("owner_id").nullable()
    val sharedTripId = javaUUID("shared_trip_id").nullable()
    val name = text("name")
    val items = jsonb<List<TemplateItem>>("items", Json)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object CreditWallets : Table("credit_wallets") {
    val userId = javaUUID("user_id")
    val balance = integer("balance")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object CreditDaily : Table("credit_daily") {
    val userId = javaUUID("user_id")
    val usageDate = date("usage_date")
    val freeUsed = integer("free_used")
    override val primaryKey = PrimaryKey(userId, usageDate)
}

object CreditLedger : Table("credit_ledger") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val kind = enumerationByName<CreditLedgerKind>("kind", 20)
    val delta = integer("delta")
    val spendSource = enumerationByName<CreditSource>("source", 20).nullable()
    val action = enumerationByName<CreditAction>("action", 20).nullable()
    val ref = text("ref").nullable()
    val idempotencyKey = text("idempotency_key").nullable()
    val balanceAfter = integer("balance_after")
    /** The admin's reason for an ADJUSTMENT. */
    val note = text("note").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Append-only; no foreign keys so it outlives the accounts it mentions (V12). */
object AdminAuditLog : Table("admin_audit_log") {
    val id = javaUUID("id")
    val adminId = javaUUID("admin_id")
    val adminEmail = text("admin_email")
    val action = enumerationByName<AdminAction>("action", 32)
    val targetType = text("target_type")
    val targetId = text("target_id").nullable()
    val before = jsonb<JsonElement>("before", Json).nullable()
    val after = jsonb<JsonElement>("after", Json).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CreditVouchers : Table("credit_vouchers") {
    val code = text("code")
    val credits = integer("credits")
    val maxUses = integer("max_uses")
    val usedCount = integer("used_count")
    val expiresAt = timestamp("expires_at").nullable()
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(code)
}

object CreditVoucherRedemptions : Table("credit_voucher_redemptions") {
    val code = text("code")
    val userId = javaUUID("user_id")
    val redeemedAt = timestamp("redeemed_at")
    override val primaryKey = PrimaryKey(code, userId)
}

object Devices : Table("devices") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val platform = enumerationByName<DevicePlatform>("platform", 10)
    val pushToken = text("push_token")
    val locale = text("locale")
    val appVersion = text("app_version").nullable()
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
    val disabledAt = timestamp("disabled_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Notifications : Table("notifications") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val type = enumerationByName<NotificationType>("type", 24)
    val tripId = javaUUID("trip_id").nullable()
    val params = jsonb<Map<String, String>>("params", Json)
    val dedupeKey = text("dedupe_key")
    val createdAt = timestamp("created_at")
    val readAt = timestamp("read_at").nullable()
    val pushState = text("push_state")
    val pushAttempts = short("push_attempts")
    override val primaryKey = PrimaryKey(id)
}

object NotificationSettings : Table("notification_settings") {
    val userId = javaUUID("user_id")
    val tripActivity = bool("trip_activity")
    val expenses = bool("expenses")
    val reminders = bool("reminders")
    override val primaryKey = PrimaryKey(userId)
}

object RecommendationCache : Table("recommendation_cache") {
    val queryHash = text("query_hash")
    val destination = text("destination")
    val response = jsonb<List<CachedPlace>>("response", Json)
    val model = text("model")
    val hitCount = integer("hit_count")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(queryHash)
}

object PlaceDetailCache : Table("place_detail_cache") {
    val placeId = text("place_id")
    val lang = text("lang")
    val response = jsonb<CachedDetail>("response", Json)
    val model = text("model")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(placeId, lang)
}

object ExploreSuggestionsCache : Table("explore_suggestions_cache") {
    val destination = text("destination")
    val month = text("month")
    val language = text("language")
    val interests = jsonb<List<String>>("interests", Json)
    val priceLowMaxUsd = decimal("price_low_max_usd", 10, 2)
    val priceMidMaxUsd = decimal("price_mid_max_usd", 10, 2)
    val model = text("model")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(destination, month, language)
}

object RecommendationFeedback : Table("recommendation_feedback") {
    val tripId = javaUUID("trip_id")
    val userId = javaUUID("user_id")
    val placeId = text("place_id")
    val placeName = text("place_name")
    val category = enumerationByName<Category>("category", 16)
    val action = enumerationByName<FeedbackAction>("action", 8)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(tripId, userId, placeId)
}

/** V11: itinerary voting (docs/specs/voting-spec.md). */
object SuggestionRounds : Table("suggestion_rounds") {
    val id = javaUUID("id")
    val tripId = javaUUID("trip_id")
    val createdBy = javaUUID("created_by")
    val createdAt = timestamp("created_at")
    val closesAt = timestamp("closes_at")
    val expiresAt = timestamp("expires_at")
    val decidedAt = timestamp("decided_at").nullable()
    val decidedBy = javaUUID("decided_by").nullable()
    override val primaryKey = PrimaryKey(id)
}

object SuggestionRoundPlaces : Table("suggestion_round_places") {
    val roundId = javaUUID("round_id")
    val placeId = text("place_id")
    val category = enumerationByName<Category>("category", 16)
    val sortOrder = integer("sort_order")
    val snapshot = jsonb<RecommendationItem>("snapshot", Json { ignoreUnknownKeys = true })
    val chosen = bool("chosen")
    override val primaryKey = PrimaryKey(roundId, placeId)
}

object SuggestionVotes : Table("suggestion_votes") {
    val roundId = javaUUID("round_id")
    val placeId = text("place_id")
    val userId = javaUUID("user_id")
    val vote = enumerationByName<Vote>("vote", 8)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(roundId, placeId, userId)
}
