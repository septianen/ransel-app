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

/*
 * Exposed mappings of the Flyway schema (V1..V15). Flyway owns the DDL; these objects only describe it.
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
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
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
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
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
