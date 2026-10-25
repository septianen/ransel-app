package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.Devices
import app.ransel.server.db.ExpensePayers
import app.ransel.server.db.ExpenseSplits
import app.ransel.server.db.Expenses
import app.ransel.server.db.NotificationSettings
import app.ransel.server.db.Notifications
import app.ransel.server.db.Settlements
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.push.PushMessage
import app.ransel.server.push.PushResult
import app.ransel.server.push.PushSender
import app.ransel.server.push.PushText
import app.ransel.shared.dto.NotificationDto
import app.ransel.shared.dto.NotificationSettingsDto
import app.ransel.shared.dto.NotificationsDto
import app.ransel.shared.dto.RegisterDeviceRequest
import app.ransel.shared.dto.UpdateNotificationSettingsRequest
import app.ransel.shared.model.NotificationType
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Notifications. An event writes inbox rows inside the transaction that caused it (so a rolled-back change leaves no
 * notification behind); a background loop later sends the pushes of the rows still `PENDING` (an outbox, so a push
 * never goes out for something that did not commit). The unique (user, dedupe key) makes a sync retry harmless.
 */
class NotificationService(private val config: AppConfig, private val push: PushSender) {
    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    // ── writing ─────────────────────────────────────────────────────────────

    /** Adds the inbox row for each recipient who wants this kind of notification. Call it inside the causing transaction. */
    fun notify(
        recipients: Collection<UUID>,
        type: NotificationType,
        tripId: UUID?,
        dedupeKey: String,
        params: (UUID) -> Map<String, String>,
    ) {
        if (recipients.isEmpty()) return
        transaction {
            val settings = NotificationSettings.selectAll().where { NotificationSettings.userId inList recipients.toList() }
                .associate { it[NotificationSettings.userId] to Triple(it[NotificationSettings.tripActivity], it[NotificationSettings.expenses], it[NotificationSettings.reminders]) }
            recipients.distinct().forEach { user ->
                val (tripActivity, expenses, reminders) = settings[user] ?: Triple(true, true, true)
                val wanted = when (type) {
                    NotificationType.MEMBER_JOINED, NotificationType.MEMBER_REMOVED, NotificationType.ROLE_CHANGED,
                    NotificationType.VOTE_REQUESTED, NotificationType.VOTE_RESULT,
                    NotificationType.MEMBER_CLAIMED, NotificationType.COMMENT_MENTION -> tripActivity
                    NotificationType.EXPENSE_ADDED, NotificationType.SETTLEMENT_MARKED -> expenses
                    NotificationType.PACKING_REMINDER, NotificationType.ACTIVITY_REMINDER -> reminders
                }
                if (!wanted) return@forEach
                Notifications.insertIgnore {
                    it[id] = UUID.randomUUID()
                    it[userId] = user
                    it[Notifications.type] = type
                    it[Notifications.tripId] = tripId
                    it[Notifications.params] = params(user)
                    it[Notifications.dedupeKey] = dedupeKey
                    it[pushState] = "PENDING"
                    it[pushAttempts] = 0
                }
            }
        }
    }

    // Event helpers. They read what they need from the rows just written, in the same transaction.

    fun memberJoined(tripId: UUID, joiner: UUID) {
        val title = tripTitle(tripId)
        val name = displayName(joiner)
        val audience = TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.role inList listOf(Role.OWNER, Role.EDITOR)) and (TripMembers.userId neq joiner) }
            .mapNotNull { it[TripMembers.userId] }
        notify(audience, NotificationType.MEMBER_JOINED, tripId, "joined:$tripId:$joiner:${UUID.randomUUID()}") {
            mapOf("tripTitle" to title, "actorName" to name)
        }
    }

    fun memberRemoved(tripId: UUID, target: UUID) {
        val title = tripTitle(tripId)
        notify(listOf(target), NotificationType.MEMBER_REMOVED, tripId, "removed:$tripId:$target:${UUID.randomUUID()}") { mapOf("tripTitle" to title) }
    }

    fun roleChanged(tripId: UUID, target: UUID, role: Role) {
        val title = tripTitle(tripId)
        notify(listOf(target), NotificationType.ROLE_CHANGED, tripId, "role:$tripId:$target:${UUID.randomUUID()}") {
            mapOf("tripTitle" to title, "role" to role.name)
        }
    }

    /** Someone took over a member added by name: tell the OWNER (unless the OWNER did it themselves). */
    fun memberClaimed(tripId: UUID, claimer: UUID, guestMemberId: UUID, guestName: String) {
        val title = tripTitle(tripId)
        val name = displayName(claimer)
        val owners = TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.role eq Role.OWNER) }
            .mapNotNull { it[TripMembers.userId] }.filter { it != claimer }
        notify(owners, NotificationType.MEMBER_CLAIMED, tripId, "claimed:$tripId:$claimer:${UUID.randomUUID()}") {
            mapOf("tripTitle" to title, "actorName" to name, "guestName" to guestName, "guestMemberId" to guestMemberId.toString())
        }
    }

    /** A comment that names people with @: each of them (not the author), once per comment. */
    fun commentMention(tripId: UUID, commentId: UUID, author: UUID, mentioned: Collection<UUID>, params: Map<String, String>) {
        val title = tripTitle(tripId)
        val name = displayName(author)
        notify(mentioned.filter { it != author }, NotificationType.COMMENT_MENTION, tripId, "mention:$commentId") {
            params + mapOf("tripTitle" to title, "actorName" to name, "commentId" to commentId.toString())
        }
    }

    /** A new expense: everyone on it (payers, people with a share) except the one who recorded it. */
    fun expenseAdded(tripId: UUID, expenseId: UUID, actor: UUID) {
        val expense = Expenses.selectAll().where { Expenses.id eq expenseId }.singleOrNull() ?: return
        if (expense[Expenses.deletedAt] != null) return
        val shareByMember = ExpenseSplits.selectAll().where { ExpenseSplits.expenseId eq expenseId }
            .associate { it[ExpenseSplits.memberId] to it[ExpenseSplits.shareMinor] }
        val payers = ExpensePayers.selectAll().where { ExpensePayers.expenseId eq expenseId }.map { it[ExpensePayers.memberId] }
        val memberIds = (shareByMember.keys + payers + expense[Expenses.paidByMemberId]).distinct()
        // Only current members with an account: a person who left can still be on an old expense, and a member added by name has no inbox.
        val accounts = TripMembers.selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.id inList memberIds) }
            .mapNotNull { row -> row[TripMembers.userId]?.let { row[TripMembers.id] to it } }
            .toMap()
        val shares = shareByMember.mapNotNull { (member, share) -> accounts[member]?.let { it to share } }.toMap()
        val members = accounts.values.filter { it != actor }
        val title = tripTitle(tripId)
        val name = displayName(actor)
        notify(members, NotificationType.EXPENSE_ADDED, tripId, "expense:$expenseId") { user ->
            buildMap {
                put("tripTitle", title)
                put("actorName", name)
                put("amountMinor", expense[Expenses.amountMinor].toString())
                put("currency", expense[Expenses.currency].trim())
                shares[user]?.let { put("shareMinor", it.toString()) }
            }
        }
    }

    /** A recorded payment: the other people on it (normally the one who is paid back). */
    fun settlementMarked(tripId: UUID, settlementId: UUID, actor: UUID) {
        val settlement = Settlements.selectAll().where { Settlements.id eq settlementId }.singleOrNull() ?: return
        if (settlement[Settlements.deletedAt] != null) return
        val audience = userIdsOfMembers(listOf(settlement[Settlements.fromMemberId], settlement[Settlements.toMemberId])).values.filter { it != actor }
        val trip = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull() ?: return
        val name = displayName(actor)
        notify(audience, NotificationType.SETTLEMENT_MARKED, tripId, "settlement:$settlementId") {
            mapOf(
                "tripTitle" to trip[Trips.title], "actorName" to name,
                "amountMinor" to settlement[Settlements.amountMinor].toString(), "currency" to trip[Trips.baseCurrency].trim(),
            )
        }
    }

    // ── reading ─────────────────────────────────────────────────────────────

    fun list(userId: UUID, limit: Int, before: String?): NotificationsDto = transaction {
        val cursor = before?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { id ->
            Notifications.selectAll().where { (Notifications.id eq id) and (Notifications.userId eq userId) }.singleOrNull()
                ?.let { it[Notifications.createdAt] to it[Notifications.id] }
        }
        val items = Notifications.selectAll().where {
            val mine = Notifications.userId eq userId
            if (cursor == null) mine
            else mine and ((Notifications.createdAt less cursor.first) or
                ((Notifications.createdAt eq cursor.first) and (Notifications.id less cursor.second)))
        }
            .orderBy(Notifications.createdAt to SortOrder.DESC, Notifications.id to SortOrder.DESC)
            .limit(limit.coerceIn(1, 100))
            .map {
                NotificationDto(
                    id = it[Notifications.id].toString(),
                    type = it[Notifications.type],
                    tripId = it[Notifications.tripId]?.toString(),
                    params = it[Notifications.params],
                    createdAt = it[Notifications.createdAt].toTimestamp(),
                    readAt = it[Notifications.readAt]?.toTimestamp(),
                )
            }
        val unread = Notifications.selectAll().where { (Notifications.userId eq userId) and Notifications.readAt.isNull() }.count().toInt()
        NotificationsDto(items, unread)
    }

    fun markRead(userId: UUID, ids: List<String>?) {
        val parsed = ids?.map { runCatching { UUID.fromString(it) }.getOrNull() }
        requireValid(if (parsed != null && parsed.any { it == null }) mapOf("ids" to "INVALID_UUID") else emptyMap())
        transaction {
            Notifications.update({
                val open = (Notifications.userId eq userId) and Notifications.readAt.isNull()
                if (parsed == null) open else open and (Notifications.id inList parsed.filterNotNull())
            }) { it[readAt] = Instant.now() }
        }
    }

    fun settings(userId: UUID): NotificationSettingsDto = transaction {
        NotificationSettings.selectAll().where { NotificationSettings.userId eq userId }.singleOrNull()?.let {
            NotificationSettingsDto(it[NotificationSettings.tripActivity], it[NotificationSettings.expenses], it[NotificationSettings.reminders])
        } ?: NotificationSettingsDto()
    }

    fun updateSettings(userId: UUID, body: UpdateNotificationSettingsRequest): NotificationSettingsDto = transaction {
        NotificationSettings.insertIgnore { it[NotificationSettings.userId] = userId; it[tripActivity] = true; it[expenses] = true; it[reminders] = true }
        NotificationSettings.update({ NotificationSettings.userId eq userId }) {
            body.tripActivity?.let { v -> it[tripActivity] = v }
            body.expenses?.let { v -> it[expenses] = v }
            body.reminders?.let { v -> it[reminders] = v }
        }
        settings(userId)
    }

    // ── devices ─────────────────────────────────────────────────────────────

    /** A token belongs to the user who signed in last on that phone. */
    fun registerDevice(userId: UUID, body: RegisterDeviceRequest) {
        val token = body.pushToken.trim()
        requireValid(
            buildMap {
                if (token.isEmpty()) put("pushToken", "REQUIRED")
                if (token.length > 4096) put("pushToken", "TOO_LONG")
            },
        )
        val language = if (body.locale.lowercase().startsWith("en")) "en" else "id"
        transaction {
            val existing = Devices.selectAll().where { Devices.pushToken eq token }.singleOrNull()
            if (existing == null) {
                Devices.insert {
                    it[id] = UUID.randomUUID()
                    it[Devices.userId] = userId
                    it[platform] = body.platform
                    it[pushToken] = token
                    it[locale] = language
                    it[appVersion] = body.appVersion?.take(40)
                }
            } else {
                Devices.update({ Devices.pushToken eq token }) {
                    it[Devices.userId] = userId
                    it[platform] = body.platform
                    it[locale] = language
                    it[appVersion] = body.appVersion?.take(40)
                    it[lastSeenAt] = Instant.now()
                    it[disabledAt] = null
                }
            }
        }
    }

    fun unregisterDevice(userId: UUID, token: String) {
        transaction { Devices.deleteWhere { (Devices.pushToken eq token.trim()) and (Devices.userId eq userId) } }
    }

    // ── sending (outbox) ────────────────────────────────────────────────────

    private data class Pending(val id: UUID, val userId: UUID, val type: NotificationType, val tripId: UUID?, val params: Map<String, String>, val attempts: Int)
    private data class DeviceRow(val id: UUID, val token: String, val locale: String)

    /** Sends the pushes of pending inbox rows. Returns how many rows were settled (sent or given up). */
    suspend fun dispatchPending(): Int {
        val batch = transaction {
            Notifications.selectAll().where { Notifications.pushState eq "PENDING" }
                .orderBy(Notifications.createdAt to SortOrder.ASC).limit(BATCH)
                .map { Pending(it[Notifications.id], it[Notifications.userId], it[Notifications.type], it[Notifications.tripId], it[Notifications.params], it[Notifications.pushAttempts].toInt()) }
        }
        var settled = 0
        for (n in batch) {
            val devices = transaction {
                Devices.selectAll().where { (Devices.userId eq n.userId) and Devices.disabledAt.isNull() }
                    .map { DeviceRow(it[Devices.id], it[Devices.pushToken], it[Devices.locale]) }
            }
            var delivered = false
            var retry = false
            for (device in devices) {
                val (title, body) = PushText.render(n.type, n.params, device.locale)
                val data = buildMap {
                    put("type", n.type.name)
                    put("notificationId", n.id.toString())
                    n.tripId?.let { put("tripId", it.toString()) }
                    // Voting: the app opens the round itself, and the owner's "ready" opens the result.
                    n.params["roundId"]?.let { put("roundId", it) }
                    n.params["stage"]?.let { put("stage", it) }
                }
                when (push.send(device.token, PushMessage(title, body, data))) {
                    PushResult.OK -> delivered = true
                    PushResult.INVALID_TOKEN -> transaction { Devices.update({ Devices.id eq device.id }) { it[disabledAt] = Instant.now() } }
                    PushResult.RETRY -> retry = true
                }
            }
            val giveUp = !delivered && (!retry || n.attempts + 1 >= MAX_ATTEMPTS)
            transaction {
                when {
                    delivered -> Notifications.update({ Notifications.id eq n.id }) { it[pushState] = "SENT" }
                    giveUp -> Notifications.update({ Notifications.id eq n.id }) { it[pushState] = "SKIPPED"; it[pushAttempts] = (n.attempts + 1).toShort() }
                    else -> Notifications.update({ Notifications.id eq n.id }) { it[pushAttempts] = (n.attempts + 1).toShort() }
                }
            }
            if (delivered || giveUp) settled++
            if (delivered) log.info("push.sent type={}", n.type) else if (giveUp && devices.isNotEmpty()) log.info("push.failed type={}", n.type)
        }
        return settled
    }

    /** Hourly: read notifications older than 90 days and devices that stayed disabled for 30 days. */
    fun purge() = transaction {
        Notifications.deleteWhere { readAt.isNotNull() and (createdAt less Instant.now().minus(90, ChronoUnit.DAYS)) }
        Devices.deleteWhere { disabledAt.isNotNull() and (disabledAt less Instant.now().minus(30, ChronoUnit.DAYS)) }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun tripTitle(tripId: UUID) = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull()?.get(Trips.title) ?: throw notFound("Trip")
    private fun displayName(userId: UUID) = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.displayName).orEmpty()

    private companion object {
        const val BATCH = 50
        const val MAX_ATTEMPTS = 3
    }
}

/** Reached from services that are plain objects (MemberService, SyncService); set once in Application, null in tools. */
object Notify {
    @Volatile
    var service: NotificationService? = null

    inline fun emit(block: NotificationService.() -> Unit) {
        service?.block()
    }
}
