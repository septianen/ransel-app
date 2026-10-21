package app.ransel.shared.dto

import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.CreditLedgerKind
import app.ransel.shared.model.CreditSource
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Explore credits. Every signed-in user has a wallet (created with the signup grant on first use) and a free daily
 * allowance that resets at 00:00 WIB and does not accumulate. A charge takes the free allowance first, then the wallet.
 * Costs and prices come from the server, never hardcoded in the app. Credits are server data: online only.
 */

/** GET /credits. */
@Serializable
data class CreditsDto(
    /** Credits in the wallet (granted, redeemed or bought). Does not include the free daily allowance. */
    val balance: Int,
    /** Free allowance still unused today. */
    val dailyFreeLeft: Int,
    val dailyFreeLimit: Int,
    /** Next 00:00 WIB, when the free allowance refills. */
    val resetsAt: Timestamp,
    val costs: CreditCostsDto,
) {
    /** What can be spent right now. */
    val available: Int get() = balance + dailyFreeLeft
}

/** Credits per action, from the server config. 0 = free. */
@Serializable
data class CreditCostsDto(
    val search: Int,
    val nextBatch: Int,
    val placeDetail: Int,
)

/** GET /credits/ledger, newest first. [delta] is negative for a spend, and for an ADJUSTMENT that took credits away ("Penyesuaian admin"). */
@Serializable
data class CreditLedgerEntryDto(
    val id: String,
    val kind: CreditLedgerKind,
    val delta: Int,
    /** Only for a SPEND. */
    val source: CreditSource? = null,
    /** Only for a SPEND. */
    val action: CreditAction? = null,
    /** Trip id for a spend, voucher code for a VOUCHER, admin user id for an ADJUSTMENT. */
    val ref: String? = null,
    val createdAt: Timestamp,
    /** The admin's reason for an ADJUSTMENT. Admin responses only; null in GET /credits/ledger. */
    val note: String? = null,
    /** Wallet balance after this row. Admin responses only; null in GET /credits/ledger. */
    val balanceAfter: Int? = null,
)

/**
 * POST /credits/redeem. 404 VOUCHER_INVALID, 422 VOUCHER_EXPIRED, 409 VOUCHER_USED.
 * [code] is matched case-insensitively after trimming.
 */
@Serializable
data class RedeemVoucherRequest(val code: String)

@Serializable
data class RedeemVoucherResponse(
    /** Credits added by this voucher. */
    val granted: Int,
    val credits: CreditsDto,
)

/** GET /credits/packages. Prices are in minor units of [currency] (IDR has 0 decimals: Rp10.000 = 10000). */
@Serializable
data class CreditPackageDto(
    val id: String,
    val credits: Int,
    val priceMinor: Long,
    val currency: String,
)

@Serializable
data class CreditPackagesResponse(
    val packages: List<CreditPackageDto>,
    /** False until a payment provider is connected: show the packs but do not offer to buy. */
    val purchasable: Boolean = false,
)
