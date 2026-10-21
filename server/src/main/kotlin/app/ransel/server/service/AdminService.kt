package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.AdminAuditLog
import app.ransel.server.db.CreditDaily
import app.ransel.server.db.CreditWallets
import app.ransel.server.db.Devices
import app.ransel.server.db.Expenses
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PackingItems
import app.ransel.server.db.PasswordResetTokens
import app.ransel.server.db.RefreshTokens
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AdjustCreditsRequest
import app.ransel.shared.dto.AdminActionResult
import app.ransel.shared.dto.AdminAuditEntryDto
import app.ransel.shared.dto.AdminDeviceDto
import app.ransel.shared.dto.AdminMeDto
import app.ransel.shared.dto.AdminTripDto
import app.ransel.shared.dto.AdminUserDetailDto
import app.ransel.shared.dto.AdminUserDto
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.UserStatus
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.time.Clock

/** The signed-in admin, checked against `ADMIN_EMAILS` on every request. */
data class AdminPrincipal(val id: UUID, val email: String, val displayName: String)

/** Filters of GET /admin/users and its CSV export. [platform] WEB = no phone registered for push. */
data class UserFilter(
    val query: String? = null,
    val status: UserStatus? = null,
    val platform: String? = null,
    val joinedFrom: LocalDate? = null,
    val joinedTo: LocalDate? = null,
    val noCredits: Boolean = false,
)

/**
 * Admin app "Karung", stage A (docs/specs/admin-spec.md): users, credit adjustments, account actions, audit log.
 * Never returns trip content, only counts, dates and the destination. Every change writes an audit row in the same
 * transaction. Users without an email (the Phase 2 dummy that owns guest data) are not accounts and never listed.
 */
class AdminService(
    private val config: AppConfig,
    private val credits: CreditService,
    private val passwordReset: PasswordResetService,
) {
    private val wib = TimeZone.of("Asia/Jakarta")

    /** 403 unless the account is active and its email is in `ADMIN_EMAILS` (checked per request, so removal is instant). */
    fun requireAdmin(userId: UUID): AdminPrincipal = transaction {
        val row = Users.selectAll().where { Users.id eq userId }.singleOrNull()
        val email = row?.get(Users.email)
        if (row == null || email == null || row[Users.deletedAt] != null || row[Users.disabledAt] != null ||
            email.lowercase() !in config.adminEmails
        ) {
            throw ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "Admins only")
        }
        AdminPrincipal(userId, email, row[Users.displayName])
    }

    fun me(admin: AdminPrincipal) = AdminMeDto(
        id = admin.id.toString(),
        email = admin.email,
        displayName = admin.displayName,
        environment = config.appEnv,
        passwordResetEmailEnabled = config.adminPasswordResetEnabled,
    )

    // ── users ───────────────────────────────────────────────────────────────

    /** Newest accounts first. Returns the page and the total after filtering. */
    fun users(filter: UserFilter, page: Int, pageSize: Int): Pair<List<AdminUserDto>, Int> = transaction {
        val all = filteredUsers(filter)
        all.drop((page - 1) * pageSize).take(pageSize) to all.size
    }

    /** Every matching account (CSV export). Audited. */
    fun exportUsers(admin: AdminPrincipal, filter: UserFilter): String = transaction {
        val rows = filteredUsers(filter)
        audit(admin, AdminAction.USERS_EXPORT, "EXPORT", null, null, buildJsonObject {
            put("count", rows.size)
            put("query", filter.query)
            put("status", filter.status?.name)
            put("platform", filter.platform)
            put("joinedFrom", filter.joinedFrom?.toString())
            put("joinedTo", filter.joinedTo?.toString())
            put("noCredits", filter.noCredits)
        })
        buildString {
            appendLine("id,email,display_name,status,joined_at,last_active_at,platform,trips,balance,daily_free_left")
            rows.forEach { u ->
                listOf(
                    u.id, u.email, u.displayName, u.status.name, u.joinedAt.toString(), u.lastActiveAt?.toString().orEmpty(),
                    u.platform?.name ?: "WEB", u.tripCount.toString(), u.balance.toString(), u.dailyFreeLeft.toString(),
                ).joinTo(this, ",") { csvCell(it) }
                appendLine()
            }
        }
    }

    fun user(id: UUID): AdminUserDetailDto {
        val row = transaction { account(id) }
        val wallet = credits.credits(id)
        return transaction {
            val trips = (TripMembers innerJoin Trips).selectAll()
                .where { (TripMembers.userId eq id) and Trips.deletedAt.isNull() }
                .orderBy(Trips.startDate to SortOrder.DESC)
                .map { t ->
                    val tripId = t[Trips.id]
                    AdminTripDto(
                        id = tripId.toString(),
                        destination = t[Trips.destination],
                        startDate = t[Trips.startDate].toKotlinLocalDate(),
                        endDate = t[Trips.endDate].toKotlinLocalDate(),
                        role = t[TripMembers.role],
                        memberCount = TripMembers.selectAll().where { TripMembers.tripId eq tripId }.count().toInt(),
                        itemCount = (
                            ItineraryItems.selectAll().where { (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }.count() +
                                PackingItems.selectAll().where { (PackingItems.tripId eq tripId) and PackingItems.deletedAt.isNull() }.count()
                            ).toInt(),
                    )
                }
            AdminUserDetailDto(
                user = toDtos(listOf(row)).single(),
                credits = wallet,
                expenseCount = Expenses.selectAll().where { (Expenses.paidByUserId eq id) and Expenses.deletedAt.isNull() }.count().toInt(),
                trips = trips,
                devices = devices(id),
            )
        }
    }

    fun ledger(id: UUID, page: Int, pageSize: Int): Pair<List<CreditLedgerEntryDto>, Int> {
        transaction { account(id) }
        return credits.ledgerPage(id, page, pageSize)
    }

    // ── actions ─────────────────────────────────────────────────────────────

    fun adjustCredits(admin: AdminPrincipal, id: UUID, body: AdjustCreditsRequest): CreditsDto {
        val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
        val reason = body.reason.trim()
        requireValid(
            buildMap {
                if (requestId == null) put("requestId", "INVALID_UUID")
                if (body.amount !in 1..MAX_ADJUSTMENT) put("amount", "OUT_OF_RANGE")
                if (reason.length !in 3..200) put("reason", if (reason.isEmpty()) "REQUIRED" else "LENGTH")
            },
        )
        return transaction {
            account(id)
            credits.adjust(id, body.direction, body.amount, reason, requestId!!, admin.id) { before, after ->
                audit(
                    admin, AdminAction.CREDITS_ADJUST, "USER", id.toString(),
                    buildJsonObject { put("balance", before) },
                    buildJsonObject {
                        put("balance", after)
                        put("direction", body.direction.name)
                        put("amount", body.amount)
                        put("reason", reason)
                    },
                )
            }
        }
    }

    fun signOutAll(admin: AdminPrincipal, id: UUID): AdminActionResult = transaction {
        account(id)
        val revoked = revokeSessions(id)
        audit(admin, AdminAction.USER_SIGN_OUT_ALL, "USER", id.toString(), null, buildJsonObject { put("revokedSessions", revoked) })
        AdminActionResult(toDtos(listOf(account(id))).single(), revoked)
    }

    /** Idempotent. An admin cannot disable their own account (they would lock themselves out of Karung). */
    fun disable(admin: AdminPrincipal, id: UUID): AdminActionResult = transaction {
        if (id == admin.id) throw conflict("You cannot deactivate your own account")
        val row = account(id)
        var revoked = 0
        if (row[Users.disabledAt] == null) {
            val now = Instant.now()
            Users.update({ Users.id eq id }) { it[disabledAt] = now }
            revoked = revokeSessions(id)
            audit(
                admin, AdminAction.USER_DISABLE, "USER", id.toString(),
                buildJsonObject { put("disabledAt", JsonNull) },
                buildJsonObject { put("disabledAt", now.toString()); put("revokedSessions", revoked) },
            )
        }
        AdminActionResult(toDtos(listOf(account(id))).single(), revoked)
    }

    /** Idempotent. */
    fun enable(admin: AdminPrincipal, id: UUID): AdminActionResult = transaction {
        val row = account(id)
        val was = row[Users.disabledAt]
        if (was != null) {
            Users.update({ Users.id eq id }) { it[disabledAt] = null }
            audit(
                admin, AdminAction.USER_ENABLE, "USER", id.toString(),
                buildJsonObject { put("disabledAt", was.toString()) },
                buildJsonObject { put("disabledAt", JsonNull) },
            )
        }
        AdminActionResult(toDtos(listOf(account(id))).single(), 0)
    }

    /** Off until an email provider exists (409). At most [RESET_EMAILS_PER_HOUR] links per account per hour (429). */
    fun sendPasswordReset(admin: AdminPrincipal, id: UUID) {
        if (!config.adminPasswordResetEnabled) throw conflict("Password reset email is not set up yet")
        val email = transaction {
            val row = account(id)
            if (row[Users.deletedAt] != null || row[Users.disabledAt] != null) throw conflict("The account is not active")
            val recent = PasswordResetTokens.selectAll()
                .where { (PasswordResetTokens.userId eq id) and (PasswordResetTokens.createdAt greater Instant.now().minusSeconds(3600)) }
                .count()
            if (recent >= RESET_EMAILS_PER_HOUR) {
                throw ApiException(HttpStatusCode.TooManyRequests, ErrorCode.RATE_LIMITED, "Too many reset emails for this account, try again later")
            }
            audit(admin, AdminAction.PASSWORD_RESET_SEND, "USER", id.toString(), null, null)
            row[Users.email]!!
        }
        passwordReset.forgot(email)
    }

    /** Newest first. */
    fun auditLog(targetId: String?, action: AdminAction?, page: Int, pageSize: Int): Pair<List<AdminAuditEntryDto>, Int> = transaction {
        val where: Op<Boolean> = listOfNotNull(
            targetId?.let { AdminAuditLog.targetId eq it },
            action?.let { AdminAuditLog.action eq it },
        ).fold(Op.TRUE as Op<Boolean>) { acc, op -> acc and op }
        val total = AdminAuditLog.selectAll().where { where }.count().toInt()
        val rows = AdminAuditLog.selectAll().where { where }
            .orderBy(AdminAuditLog.createdAt to SortOrder.DESC, AdminAuditLog.id to SortOrder.DESC)
            .limit(pageSize).offset(((page - 1) * pageSize).toLong())
            .map {
                AdminAuditEntryDto(
                    id = it[AdminAuditLog.id].toString(),
                    adminId = it[AdminAuditLog.adminId].toString(),
                    adminEmail = it[AdminAuditLog.adminEmail],
                    action = it[AdminAuditLog.action],
                    targetType = it[AdminAuditLog.targetType],
                    targetId = it[AdminAuditLog.targetId],
                    before = it[AdminAuditLog.before],
                    after = it[AdminAuditLog.after],
                    createdAt = it[AdminAuditLog.createdAt].toTimestamp(),
                )
            }
        rows to total
    }

    // ── internals (inside a transaction) ────────────────────────────────────

    /** An account (has an email), deleted or not; 404 otherwise. */
    private fun account(id: UUID): ResultRow =
        Users.selectAll().where { (Users.id eq id) and Users.email.isNotNull() }.singleOrNull() ?: throw notFound("User")

    private fun revokeSessions(id: UUID): Int =
        RefreshTokens.update({ (RefreshTokens.userId eq id) and RefreshTokens.revokedAt.isNull() }) { it[revokedAt] = Instant.now() }

    private fun audit(admin: AdminPrincipal, action: AdminAction, targetType: String, targetId: String?, before: JsonElement?, after: JsonElement?) {
        AdminAuditLog.insert {
            it[id] = UUID.randomUUID()
            it[adminId] = admin.id
            it[adminEmail] = admin.email
            it[AdminAuditLog.action] = action
            it[AdminAuditLog.targetType] = targetType
            it[AdminAuditLog.targetId] = targetId
            it[AdminAuditLog.before] = before
            it[AdminAuditLog.after] = after
        }
    }

    /**
     * Status, query and join date are filtered in SQL; platform and "no credits" need the derived values, so they are
     * filtered after [toDtos]. Fine for the closed beta's few thousand accounts; move to SQL if that grows.
     */
    private fun filteredUsers(filter: UserFilter): List<AdminUserDto> {
        val conditions = buildList<Op<Boolean>> {
            add(Users.email.isNotNull())
            filter.query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { q ->
                val pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
                add((Users.email.lowerCase() like pattern) or (Users.displayName.lowerCase() like pattern))
            }
            when (filter.status) {
                UserStatus.ACTIVE -> { add(Users.deletedAt.isNull()); add(Users.disabledAt.isNull()) }
                UserStatus.DISABLED -> { add(Users.deletedAt.isNull()); add(Users.disabledAt.isNotNull()) }
                UserStatus.DELETED -> add(Users.deletedAt.isNotNull())
                null -> Unit
            }
            filter.joinedFrom?.let { add(Users.createdAt greaterEq it.atStartOfDayIn(wib).toJavaInstantCompat()) }
            filter.joinedTo?.let { add(Users.createdAt less it.plusDays(1).atStartOfDayIn(wib).toJavaInstantCompat()) }
        }
        val rows = Users.selectAll().where { conditions.reduce { a, b -> a and b } }
            .orderBy(Users.createdAt to SortOrder.DESC, Users.id to SortOrder.DESC)
            .toList()
        return toDtos(rows).filter { u ->
            (filter.platform == null || (u.platform?.name ?: "WEB") == filter.platform) &&
                (!filter.noCredits || u.balance + u.dailyFreeLeft < config.creditCostSearch)
        }
    }

    /** Batch lookups (one query per derived value) instead of one per user. */
    private fun toDtos(rows: List<ResultRow>): List<AdminUserDto> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[Users.id] }
        val today = Clock.System.now().toLocalDateTime(wib).date.toJavaLocalDate()
        val balances = CreditWallets.selectAll().where { CreditWallets.userId inList ids }
            .associate { it[CreditWallets.userId] to it[CreditWallets.balance] }
        val freeUsed = CreditDaily.selectAll().where { (CreditDaily.userId inList ids) and (CreditDaily.usageDate eq today) }
            .associate { it[CreditDaily.userId] to it[CreditDaily.freeUsed] }
        val lastSession = RefreshTokens.createdAt.max()
        val lastActive = RefreshTokens.select(RefreshTokens.userId, lastSession)
            .where { RefreshTokens.userId inList ids }
            .groupBy(RefreshTokens.userId)
            .associate { it[RefreshTokens.userId] to it[lastSession] }
        val platforms = Devices.selectAll().where { (Devices.userId inList ids) and Devices.disabledAt.isNull() }
            .orderBy(Devices.lastSeenAt to SortOrder.ASC)
            .associate { it[Devices.userId] to it[Devices.platform] } // ascending, so the newest device wins
        val tripCount = TripMembers.userId.count()
        val trips = (TripMembers innerJoin Trips).select(TripMembers.userId, tripCount)
            .where { (TripMembers.userId inList ids) and Trips.deletedAt.isNull() }
            .groupBy(TripMembers.userId)
            .associate { it[TripMembers.userId] to it[tripCount].toInt() }
        return rows.map { row ->
            val id = row[Users.id]
            AdminUserDto(
                id = id.toString(),
                email = row[Users.email].orEmpty(),
                displayName = row[Users.displayName],
                status = when {
                    row[Users.deletedAt] != null -> UserStatus.DELETED
                    row[Users.disabledAt] != null -> UserStatus.DISABLED
                    else -> UserStatus.ACTIVE
                },
                joinedAt = row[Users.createdAt].toTimestamp(),
                lastActiveAt = lastActive[id]?.toTimestamp(),
                platform = platforms[id],
                tripCount = trips[id] ?: 0,
                // No wallet yet = it is created with the signup grant on the user's first credits call.
                balance = balances[id] ?: config.creditSignupGrant,
                dailyFreeLeft = (config.creditDailyFree - (freeUsed[id] ?: 0)).coerceAtLeast(0),
            )
        }
    }

    private fun devices(id: UUID): List<AdminDeviceDto> {
        val phones = Devices.selectAll().where { (Devices.userId eq id) and Devices.disabledAt.isNull() }.map {
            AdminDeviceDto(name = null, platform = it[Devices.platform], appVersion = it[Devices.appVersion], lastSeenAt = it[Devices.lastSeenAt].toTimestamp())
        }
        val lastSession = RefreshTokens.createdAt.max()
        val sessions = RefreshTokens.select(RefreshTokens.deviceName, lastSession)
            .where { (RefreshTokens.userId eq id) and RefreshTokens.revokedAt.isNull() and (RefreshTokens.expiresAt greater Instant.now()) }
            .groupBy(RefreshTokens.deviceName)
            .mapNotNull { row -> row[lastSession]?.let { AdminDeviceDto(name = row[RefreshTokens.deviceName], lastSeenAt = it.toTimestamp()) } }
        return (phones + sessions).sortedByDescending { it.lastSeenAt }
    }

    private fun csvCell(value: String): String {
        // A leading = + - @ would run as a formula in a spreadsheet.
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun LocalDate.plusDays(days: Int): LocalDate = toJavaLocalDate().plusDays(days.toLong()).toKotlinLocalDate()

    private fun kotlin.time.Instant.toJavaInstantCompat(): Instant = Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

    companion object {
        const val PAGE_SIZE = 25
        const val MAX_ADJUSTMENT = 10_000
        const val RESET_EMAILS_PER_HOUR = 3
    }
}
