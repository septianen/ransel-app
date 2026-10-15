package app.ransel.server.db

import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PaymentMethod
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import app.ransel.shared.dto.TemplateItem
import app.ransel.server.ai.CachedPlace
import app.ransel.shared.model.FeedbackAction
import kotlinx.serialization.json.Json

/*
 * Exposed mappings of the Flyway schema (V1..V3). Flyway owns the DDL; these objects only describe it.
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
    val tripId = javaUUID("trip_id")
    val userId = javaUUID("user_id")
    val role = enumerationByName<Role>("role", 16)
    val joinedAt = timestamp("joined_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(tripId, userId)
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
    val paidByUserId = javaUUID("paid_by_user_id")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ExpenseSplits : Table("expense_splits") {
    val expenseId = javaUUID("expense_id")
    val userId = javaUUID("user_id")
    val shareMinor = long("share_minor")
    override val primaryKey = PrimaryKey(expenseId, userId)
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
    val fromUserId = javaUUID("from_user_id")
    val toUserId = javaUUID("to_user_id")
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

object AiUsage : Table("ai_usage") {
    val userId = javaUUID("user_id")
    val usageDate = date("usage_date")
    val count = integer("count")
    override val primaryKey = PrimaryKey(userId, usageDate)
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
