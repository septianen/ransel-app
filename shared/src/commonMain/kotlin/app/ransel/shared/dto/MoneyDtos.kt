package app.ransel.shared.dto

import app.ransel.shared.model.PaymentMethodKind
import app.ransel.shared.serialization.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Group money (PRD "Group kitty", "Getting paid", "Receipt photo"). The kitty works through the treasurer: a deposit
 * counts as the member paying the treasurer, and an expense paid from the kitty counts as paid by the treasurer, so
 * whatever is left at the end shows up in the normal balances as the treasurer owing the members who deposited.
 * Kitty amounts are always in the trip's base currency.
 */

/** The trip's kitty. Its key is the trip id: a trip has one kitty at most. */
@Serializable
data class KittyDto(
    val tripId: String,
    /** Agreed contribution per person ("Iuran per orang"). */
    val amountPerMemberMinor: Long,
    /** The member who holds the money. Deposits go to them and kitty expenses are paid by them. */
    val treasurerMemberId: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** PUT /trips/{id}/kitty (OWNER). Validation: amountPerMemberMinor > 0, treasurerMemberId an active member. */
@Serializable
data class KittyInput(
    val amountPerMemberMinor: Long,
    val treasurerMemberId: String,
)

@Serializable
data class KittyDepositDto(
    val id: String,
    val tripId: String,
    /** Who put the money in. */
    val memberId: String,
    val amountMinor: Long,
    /** The treasurer at the time of the deposit; changing the treasurer later does not move old deposits. */
    val receivedByMemberId: String,
    val receivedAt: Timestamp,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** POST /trips/{id}/kitty/deposits ("Catat setoran"). receivedAt null = now; the receiver is the current treasurer. */
@Serializable
data class KittyDepositInput(
    val id: String,
    val memberId: String,
    val amountMinor: Long,
    val receivedAt: Timestamp? = null,
)

/** GET /trips/{id}/kitty. [kitty] null = the trip has no kitty yet. */
@Serializable
data class KittyOverviewDto(
    val kitty: KittyDto? = null,
    /** Newest first. */
    val deposits: List<KittyDepositDto> = emptyList(),
    val summary: KittySummaryDto,
)

/** Computed with domain.kittySummary, so the app shows the same numbers offline. */
@Serializable
data class KittySummaryDto(
    /** "Terkumpul": all deposits. */
    val collectedMinor: Long,
    /** "Terpakai": expenses paid from the kitty (estimates excluded). */
    val spentMinor: Long,
    /** "Sisa kas": collected − spent. Negative when the kitty paid more than it holds. */
    val leftMinor: Long,
    /** One row per active member, in member order. */
    val members: List<KittyMemberDto>,
)

@Serializable
data class KittyMemberDto(
    val memberId: String,
    val depositedMinor: Long,
    /** Deposited at least the agreed amount per person. */
    val done: Boolean,
)

/** Account → Info pembayaran. Only the owner and members who owe them money ever see it. */
@Serializable
data class PaymentMethodDto(
    val id: String,
    val kind: PaymentMethodKind,
    /** Bank or wallet name as typed, e.g. "BCA", "GoPay". */
    val provider: String,
    val accountNumber: String,
    val holderName: String,
    val isPrimary: Boolean = false,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

/**
 * POST / PATCH /me/payment-methods. Validation (domain.validatePaymentMethod): provider 1–40, accountNumber 4–40 digits,
 * spaces or dashes (an e-wallet uses the phone number), holderName 1–80. At most 10 per account (422 `id = TOO_MANY`).
 * Setting isPrimary clears it on the others.
 */
@Serializable
data class PaymentMethodInput(
    val kind: PaymentMethodKind,
    val provider: String,
    val accountNumber: String,
    val holderName: String,
    val isPrimary: Boolean = false,
)

/** GET pay-info ("Bayar ke <name>"). [amountMinor] = the open transfer between the two members in [currency]. */
@Serializable
data class PayInfoDto(
    val toMemberId: String,
    val displayName: String,
    val amountMinor: Long,
    val currency: String,
    /** Empty for a member without an account or one who saved no payment info. Primary first. */
    val methods: List<PaymentMethodDto> = emptyList(),
)

/**
 * POST /receipts/parse: what Gemini read from the photo. Nothing is stored; the app fills the expense form and the user
 * checks it before saving. Amounts are in [currency] minor units. [items] + [taxMinor] + [serviceMinor] may differ from
 * [totalMinor] when the receipt has discounts or reading errors: the app shows the difference and Save waits for a match.
 */
@Serializable
data class ReceiptDraftDto(
    val placeName: String? = null,
    val spentOn: LocalDate? = null,
    /** ISO code the receipt is in; the trip's base currency when the receipt does not say. */
    val currency: String,
    val items: List<ReceiptLineDto> = emptyList(),
    val taxMinor: Long = 0,
    val serviceMinor: Long = 0,
    val totalMinor: Long,
    /** The wallet after the charge. */
    val credits: CreditsDto,
)

@Serializable
data class ReceiptLineDto(
    val name: String,
    /** As printed ("x2"); null when not printed. [amountMinor] is already the line total. */
    val quantity: Int? = null,
    val amountMinor: Long,
)

/**
 * GET /fx/rate?from=JPY&to=IDR&date=2026-12-06: 1 [from] = [rate] [to], from the ECB reference rates crossed through EUR.
 * [date] is the day the rate is from: the latest on or before the asked day ([stale] = it is earlier, e.g. a weekend).
 */
@Serializable
data class FxRateDto(
    val from: String,
    val to: String,
    /** Decimal as a string, like `ExpenseDto.fxRateToBase`. */
    val rate: String,
    val date: LocalDate,
    val stale: Boolean = false,
    val source: String = "ECB",
)
