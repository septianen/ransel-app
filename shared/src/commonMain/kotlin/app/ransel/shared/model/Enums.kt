package app.ransel.shared.model

import kotlinx.serialization.Serializable

/*
 * Every enum here is part of the API contract and matches a CHECK constraint
 * in server/src/main/resources/db/migration. The serialized value is the
 * constant name (e.g. "ATTRACTION"). Labels, icons and colors live in the UI
 * (composeApp), never here.
 */

/**
 * Shared by itinerary_items.category, expenses.category and
 * recommendation_feedback.category.
 *
 * UI labels differ per screen (see docs/specs/itinerary-spec.md):
 * itinerary = Wisata/Explore, Kuliner/Eat, Perjalanan/Travel, Menginap/Stay, Belanja/Shop, Lainnya/Other;
 * expenses  = Aktivitas/Activities, Makan/Food, Transport, Penginapan/Lodging, Belanja/Shopping, Lainnya/Other.
 * Tile color is the same per value in both places.
 */
@Serializable
enum class Category { ATTRACTION, FOOD, TRANSPORT, LODGING, SHOPPING, OTHER }

/** packing_items.category — see docs/specs/packing-spec.md. */
@Serializable
enum class PackingCategory { CLOTHING, DOCUMENTS, ELECTRONICS, TOILETRIES, HEALTH, OTHER }

/** expenses.payment_method — the "Bayar pakai" field. Default CASH. */
@Serializable
enum class PaymentMethod { CASH, DEBIT_CARD, CREDIT_CARD, TRANSFER, E_WALLET, OTHER }

/** trip_members.role. Invites can only grant EDITOR or VIEWER. */
@Serializable
enum class Role {
    VIEWER, EDITOR, OWNER;

    /** True when this role is at least [min] (VIEWER < EDITOR < OWNER). */
    fun atLeast(min: Role): Boolean = ordinal >= min.ordinal
}

/** itinerary_items.source. AI = saved from an Explore swipe. */
@Serializable
enum class ItemSource { MANUAL, AI }

/** recommendation_feedback.action. */
@Serializable
enum class FeedbackAction { LIKE, SKIP }

/** suggestion_votes.vote: one member's vote on one card of a suggestion round (docs/specs/voting-spec.md). */
@Serializable
enum class Vote { LIKE, SKIP }

/**
 * comments.target_kind: what a comment hangs on. There is no group chat, only comments on things.
 * POLL_OPTION: a comment on one option of a poll (the place comments show in the app).
 */
@Serializable
enum class CommentTarget { ITINERARY_ITEM, EXPENSE, POLL_OPTION }

/** polls.kind: which question a poll asks. Each kind has its own option fields and what happens to the winner (domain/Polls.kt). */
@Serializable
enum class PollKind { DATE, PLACE, STAY, FOOD, BUDGET, DESTINATION, TRANSPORT, CUSTOM }

/**
 * polls.status. OPEN until `closesAt`; then CLOSED (a winner can be confirmed) or TIED (the creator picks among the
 * tied options, or the poll reopens by itself after 24 hours); DECIDED once the creator confirmed or dismissed it.
 */
@Serializable
enum class PollStatus { OPEN, TIED, CLOSED, DECIDED }

/** What a price on a poll option is per. */
@Serializable
enum class PollPriceUnit { NIGHT, PERSON, TOTAL }

/**
 * Explore form: price of the places, relative to local prices (a cheap ramen stall in Tokyo is LOW in Tokyo).
 * UI: `$` Murah, `$$` Sedang, `$$$` Mewah. Maps to Google Places priceLevel: LOW = 0–1, MEDIUM = 2, HIGH = 3–4.
 */
@Serializable
enum class PriceLevel { LOW, MEDIUM, HIGH }

/**
 * Explore core interests (multi-select), valid in every destination. UI: Alam, Kuliner, Budaya & sejarah, Belanja,
 * Petualangan, Hiburan malam, Spot foto, Santai. Beach is not core: it comes as a destination interest where it fits.
 */
@Serializable
enum class Interest { NATURE, FOOD, CULTURE, SHOPPING, ADVENTURE, NIGHTLIFE, PHOTO, RELAX }

/** Explore form travel style. SANTAI = 2–3 places a day, PADAT = 4–6. */
@Serializable
enum class TravelStyle { SANTAI, PADAT }

/** What an Explore credit was spent on (`CreditLedgerEntryDto.action`). */
@Serializable
enum class CreditAction { SEARCH, NEXT_BATCH, PLACE_DETAIL, RECEIPT }

/** Why a ledger row exists. SPEND is negative; ADJUSTMENT (an admin's correction) may be either sign; every other kind adds credits. */
@Serializable
enum class CreditLedgerKind { SIGNUP_GRANT, SPEND, VOUCHER, TOPUP, REFUND, ADJUSTMENT }

/** Admin credit adjustment: add to or subtract from the wallet. */
@Serializable
enum class CreditDirection { ADD, SUBTRACT }

/** Account state as the admin app sees it. DELETED = the user deleted their account (soft delete). */
@Serializable
enum class UserStatus { ACTIVE, DISABLED, DELETED }

/** What an admin did; one audit row each. Stage D adds CONTENT_PUBLISH. */
@Serializable
enum class AdminAction { CREDITS_ADJUST, USER_SIGN_OUT_ALL, USER_DISABLE, USER_ENABLE, PASSWORD_RESET_SEND, USERS_EXPORT, USER_CREATE, SETTINGS_UPDATE }

/**
 * A setting Karung can change without a deploy (admin-spec "Credit settings", stage C). Each one has an env var as
 * its default; a value set in Karung wins until it is cleared. Allowed values: `domain/AdminSettings.kt`.
 */
@Serializable
enum class SettingKey {
    /** Credits a new verified account gets (`CREDIT_SIGNUP_GRANT`). */
    SIGNUP_GRANT,
    /** Free credits per WIB day (`CREDIT_DAILY_FREE`). */
    DAILY_FREE,
    /** A new Explore or poll search (`CREDIT_COST_SEARCH`). */
    COST_SEARCH,
    /** The next batch of the same search (`CREDIT_COST_NEXT_BATCH`). */
    COST_NEXT_BATCH,
    /** Opening a place's detail (`CREDIT_COST_PLACE_DETAIL`). */
    COST_PLACE_DETAIL,
    /** One receipt photo read by AI (`CREDIT_COST_RECEIPT`). */
    COST_RECEIPT,
    /** LLM + Places budget per month in USD, "0" = none (`AI_MONTHLY_BUDGET_USD`). */
    AI_MONTHLY_BUDGET_USD,
    /** Karung warns from this share of the budget: 80, 90 or 100 (`AI_BUDGET_ALERT_PERCENT`). */
    AI_BUDGET_ALERT_PERCENT,
    /** What happens once the month's spend reaches the budget, an [AiBudgetMode] name (`AI_BUDGET_MODE`). */
    AI_BUDGET_MODE,
    /** Interest suggestions per user per hour (`SUGGESTIONS_RATE_LIMIT_PER_HOUR`). */
    SUGGESTIONS_PER_HOUR,
    /** The LLM model, one of the server's allow-list (`LLM_MODEL`, `LLM_MODELS`). */
    LLM_MODEL,
}

/** ENV = the env var or the built-in default; OVERRIDE = set in Karung. */
@Serializable
enum class SettingSource { ENV, OVERRIDE }

/**
 * KEEP_GOING = the budget only warns. CACHE_ONLY = once the month's spend reaches it, nothing new asks the LLM: cached
 * searches still answer, new searches and receipt photos get 503 AI_PAUSED (no credits are charged).
 */
@Serializable
enum class AiBudgetMode { KEEP_GOING, CACHE_ONLY }

/** Time range of a Karung monitoring page; each endpoint lists the ones it takes. Days are WIB days. */
@Serializable
enum class AdminRange { TODAY, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS }

/** How an Explore or poll search ended, as Karung lists it ("Baru", "Cache", "Gagal", "Kredit kurang"). */
@Serializable
enum class AiSearchSource { NEW, CACHE, FAILED, NO_CREDITS }

/** EXPLORE = the swipe search of a trip; POLL = Jelajah from a stay or food poll. */
@Serializable
enum class AiSearchKind { EXPLORE, POLL }

/** Where spent credits came from: the free daily allowance first, then the wallet. */
@Serializable
enum class CreditSource { FREE_DAILY, WALLET }

/**
 * What a notification is about. The inbox stores the type plus `params`, never rendered text, so the app shows it in
 * its own language. VOTE_REQUESTED and VOTE_RESULT each cover two events, told apart by the `stage` param
 * (`VoteStage` in domain/Voting.kt), so voting needed no new enum value.
 */
@Serializable
enum class NotificationType {
    MEMBER_JOINED, MEMBER_REMOVED, ROLE_CHANGED,
    EXPENSE_ADDED, SETTLEMENT_MARKED,
    VOTE_REQUESTED, VOTE_RESULT,
    PACKING_REMINDER, ACTIVITY_REMINDER,
    MEMBER_CLAIMED, COMMENT_MENTION,
    POLL_REQUESTED, POLL_RESULT,
    PACKING_LIST,
    DOCUMENT_ADDED, RECAP_READY,
}

/** trip_documents.category: the groups of "Dokumen trip". */
@Serializable
enum class DocumentCategory { TRAVEL, STAY, ACTIVITY, OTHER }

/** trip_documents.kind: an uploaded file, or a link (a booking page). */
@Serializable
enum class DocumentKind { FILE, LINK }

@Serializable
enum class DevicePlatform { ANDROID, IOS }

/**
 * A settlement between two members. CLAIMED_PAID = the payer tapped "Saya sudah bayar" and waits for the receiver;
 * CONFIRMED = the money arrived (confirmed claim, or "Tandai lunas"). Balances count CONFIRMED only.
 */
@Serializable
enum class SettlementStatus { CLAIMED_PAID, CONFIRMED }

/** Where an expense's exchange rate came from: the day's reference rate (ECB, through the server) or typed by the member. */
@Serializable
enum class FxSource { DAILY, MANUAL }

/** Account → Info pembayaran: a bank account or an e-wallet. */
@Serializable
enum class PaymentMethodKind { BANK, EWALLET }
