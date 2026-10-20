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
enum class CreditAction { SEARCH, NEXT_BATCH, PLACE_DETAIL }

/** Why a ledger row exists. SPEND is negative; every other kind adds credits. */
@Serializable
enum class CreditLedgerKind { SIGNUP_GRANT, SPEND, VOUCHER, TOPUP, REFUND, ADJUSTMENT }

/** Where spent credits came from: the free daily allowance first, then the wallet. */
@Serializable
enum class CreditSource { FREE_DAILY, WALLET }
