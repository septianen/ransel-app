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

/** Explore form: budget per person per day. Hemat < Rp300rb, Sedang Rp300–800rb, Bebas > Rp800rb. */
@Serializable
enum class BudgetLevel { HEMAT, SEDANG, BEBAS }

/** Explore form interests (multi-select). UI: Alam, Kuliner, Pantai, Budaya, Belanja, Petualangan, Hiburan malam, Foto. */
@Serializable
enum class Interest { NATURE, FOOD, BEACH, CULTURE, SHOPPING, ADVENTURE, NIGHTLIFE, PHOTO }

/** Explore form travel style. SANTAI = 2–3 places a day, PADAT = 4–6. */
@Serializable
enum class TravelStyle { SANTAI, PADAT }
