package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.CreditDaily
import app.ransel.server.db.CreditLedger
import app.ransel.server.db.CreditVoucherRedemptions
import app.ransel.server.db.CreditVouchers
import app.ransel.server.db.CreditWallets
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.planCreditSpend
import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.dto.CreditPackageDto
import app.ransel.shared.dto.CreditPackagesResponse
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.QuotaDto
import app.ransel.shared.dto.RedeemVoucherResponse
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.CreditLedgerKind
import app.ransel.shared.model.CreditSource
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant

/**
 * Explore credits. A charge takes the free daily allowance first, then the wallet (`planCreditSpend`, shared).
 * Every wallet operation first locks the user's wallet row (`SELECT ... FOR UPDATE`), so two parallel searches of one user
 * run one after the other and the balance can never go below zero.
 */
class CreditService(private val config: AppConfig) {
    private val wib = TimeZone.of("Asia/Jakarta")

    private fun todayWib(): LocalDate = Clock.System.now().toLocalDateTime(wib).date
    private fun resetsAt() = todayWib().plus(1, DateTimeUnit.DAY).atStartOfDayIn(wib)

    fun cost(action: CreditAction): Int = when (action) {
        CreditAction.SEARCH -> config.creditCostSearch
        CreditAction.NEXT_BATCH -> config.creditCostNextBatch
        CreditAction.PLACE_DETAIL -> config.creditCostPlaceDetail
    }

    fun credits(userId: UUID): CreditsDto = transaction { snapshot(userId) }

    fun packages() = CreditPackagesResponse(
        packages = config.creditPackages.map { CreditPackageDto(it.id, it.credits, it.priceMinor, it.currency) },
        purchasable = false,
    )

    /** Newest first. */
    fun ledger(userId: UUID, limit: Int): List<CreditLedgerEntryDto> = transaction {
        CreditLedger.selectAll().where { CreditLedger.userId eq userId }
            .orderBy(CreditLedger.createdAt to SortOrder.DESC, CreditLedger.id to SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map {
                CreditLedgerEntryDto(
                    id = it[CreditLedger.id].toString(),
                    kind = it[CreditLedger.kind],
                    delta = it[CreditLedger.delta],
                    source = it[CreditLedger.spendSource],
                    action = it[CreditLedger.action],
                    ref = it[CreditLedger.ref],
                    createdAt = it[CreditLedger.createdAt].toTimestamp(),
                )
            }
    }

    /** Throws 402 when the user cannot pay [action] now. Used before the expensive work; costs nothing itself. */
    fun requireAffordable(userId: UUID, action: CreditAction) {
        val cost = cost(action)
        if (cost == 0) return
        val now = credits(userId)
        if (planCreditSpend(cost, now.dailyFreeLeft, now.balance) == null) throw insufficient(cost, now)
    }

    /** Pays [action]. Call after the work succeeded, so a failed LLM costs nothing. Returns the state after paying. */
    fun charge(userId: UUID, action: CreditAction, ref: String? = null): CreditsDto = transaction {
        val cost = cost(action)
        val day = todayWib().toJavaLocalDate()
        val balance = lockedBalance(userId)
        val freeUsed = CreditDaily.selectAll().where { (CreditDaily.userId eq userId) and (CreditDaily.usageDate eq day) }
            .singleOrNull()?.get(CreditDaily.freeUsed) ?: 0
        val freeLeft = (config.creditDailyFree - freeUsed).coerceAtLeast(0)
        val spend = if (cost == 0) null else planCreditSpend(cost, freeLeft, balance)
        if (cost > 0 && spend == null) throw insufficient(cost, snapshot(userId))
        if (spend != null) {
            if (spend.fromFree > 0) {
                CreditDaily.insertIgnore { it[CreditDaily.userId] = userId; it[usageDate] = day; it[CreditDaily.freeUsed] = 0 }
                CreditDaily.update({ (CreditDaily.userId eq userId) and (CreditDaily.usageDate eq day) }) {
                    it[CreditDaily.freeUsed] = CreditDaily.freeUsed + spend.fromFree
                }
                ledger(userId, CreditLedgerKind.SPEND, -spend.fromFree, balance, CreditSource.FREE_DAILY, action, ref, null)
            }
            if (spend.fromWallet > 0) {
                CreditWallets.update({ CreditWallets.userId eq userId }) { it[CreditWallets.balance] = CreditWallets.balance - spend.fromWallet }
                ledger(userId, CreditLedgerKind.SPEND, -spend.fromWallet, balance - spend.fromWallet, CreditSource.WALLET, action, ref, null)
            }
        }
        snapshot(userId)
    }

    /**
     * Adds credits to the wallet. [idempotencyKey] makes a retry (a payment webhook, a double tap) add them once:
     * a key that already exists adds nothing and the current state comes back.
     */
    fun addCredits(userId: UUID, amount: Int, kind: CreditLedgerKind, idempotencyKey: String?, ref: String? = null): CreditsDto {
        require(amount > 0 && kind != CreditLedgerKind.SPEND) { "addCredits adds credits" }
        return transaction {
            val balance = lockedBalance(userId)
            val written = ledger(userId, kind, amount, balance + amount, null, null, ref, idempotencyKey)
            if (written) {
                CreditWallets.update({ CreditWallets.userId eq userId }) { it[CreditWallets.balance] = CreditWallets.balance + amount }
            }
            snapshot(userId)
        }
    }

    /** Case-insensitive, trimmed. One redemption per user per code. */
    fun redeem(userId: UUID, rawCode: String): RedeemVoucherResponse {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) throw ApiException(HttpStatusCode.NotFound, ErrorCode.VOUCHER_INVALID, "Voucher not found")
        return transaction {
            lockedBalance(userId)
            val voucher = CreditVouchers.selectAll().where { CreditVouchers.code eq code }.forUpdate().singleOrNull()
            if (voucher == null || voucher[CreditVouchers.revokedAt] != null) {
                throw ApiException(HttpStatusCode.NotFound, ErrorCode.VOUCHER_INVALID, "Voucher not found")
            }
            val expires = voucher[CreditVouchers.expiresAt]
            if (expires != null && expires.isBefore(Instant.now())) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.VOUCHER_EXPIRED, "Voucher expired")
            }
            if (voucher[CreditVouchers.usedCount] >= voucher[CreditVouchers.maxUses]) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.VOUCHER_USED, "Voucher has no uses left")
            }
            val first = CreditVoucherRedemptions.insertIgnore { it[CreditVoucherRedemptions.code] = code; it[CreditVoucherRedemptions.userId] = userId }
            if (first.insertedCount == 0) throw ApiException(HttpStatusCode.Conflict, ErrorCode.VOUCHER_USED, "Voucher already redeemed")
            CreditVouchers.update({ CreditVouchers.code eq code }) { it[usedCount] = usedCount + 1 }
            val granted = voucher[CreditVouchers.credits]
            RedeemVoucherResponse(granted, addCredits(userId, granted, CreditLedgerKind.VOUCHER, "voucher:$code:$userId", code))
        }
    }

    /** Legacy `quota` field for app versions from before credits: used = credits spent today, limit = used + available. */
    fun legacyQuota(userId: UUID): QuotaDto = transaction {
        val now = snapshot(userId)
        val midnight = todayWib().atStartOfDayIn(wib).toJavaInstant()
        val spent = CreditLedger.selectAll()
            .where { (CreditLedger.userId eq userId) and (CreditLedger.kind eq CreditLedgerKind.SPEND) and (CreditLedger.createdAt greaterEq midnight) }
            .sumOf { -it[CreditLedger.delta] }
        QuotaDto(used = spent, limit = spent + now.available, resetsAt = resetsAt())
    }

    // ── internals ───────────────────────────────────────────────────────────

    /**
     * Creates the wallet on first use with the signup grant (lazy, so existing and seeded users are covered), then locks
     * the row until the transaction ends. The signup ledger row has a unique idempotency key, so a race grants it once.
     */
    private fun lockedBalance(userId: UUID): Int {
        val created = CreditWallets.insertIgnore { it[CreditWallets.userId] = userId; it[balance] = config.creditSignupGrant }.insertedCount > 0
        if (created && config.creditSignupGrant > 0) {
            ledger(userId, CreditLedgerKind.SIGNUP_GRANT, config.creditSignupGrant, config.creditSignupGrant, null, null, null, "signup:$userId")
        }
        return CreditWallets.selectAll().where { CreditWallets.userId eq userId }.forUpdate().single()[CreditWallets.balance]
    }

    private fun snapshot(userId: UUID): CreditsDto {
        val balance = lockedBalance(userId)
        val day = todayWib().toJavaLocalDate()
        val used = CreditDaily.selectAll().where { (CreditDaily.userId eq userId) and (CreditDaily.usageDate eq day) }
            .singleOrNull()?.get(CreditDaily.freeUsed) ?: 0
        return CreditsDto(
            balance = balance,
            dailyFreeLeft = (config.creditDailyFree - used).coerceAtLeast(0),
            dailyFreeLimit = config.creditDailyFree,
            resetsAt = resetsAt(),
            costs = CreditCostsDto(config.creditCostSearch, config.creditCostNextBatch, config.creditCostPlaceDetail),
        )
    }

    /** False when [idempotencyKey] already exists (nothing written). */
    private fun ledger(
        userId: UUID, kind: CreditLedgerKind, delta: Int, balanceAfter: Int,
        source: CreditSource?, action: CreditAction?, ref: String?, idempotencyKey: String?,
    ): Boolean = CreditLedger.insertIgnore {
        it[id] = UUID.randomUUID()
        it[CreditLedger.userId] = userId
        it[CreditLedger.kind] = kind
        it[CreditLedger.delta] = delta
        it[CreditLedger.spendSource] = source
        it[CreditLedger.action] = action
        it[CreditLedger.ref] = ref
        it[CreditLedger.idempotencyKey] = idempotencyKey
        it[CreditLedger.balanceAfter] = balanceAfter
    }.insertedCount > 0

    private fun insufficient(cost: Int, now: CreditsDto) = ApiException(
        HttpStatusCode.PaymentRequired, ErrorCode.INSUFFICIENT_CREDITS, "Not enough credits",
        mapOf("cost" to cost.toString(), "available" to now.available.toString(), "resetsAt" to now.resetsAt.toString()),
    )
}
