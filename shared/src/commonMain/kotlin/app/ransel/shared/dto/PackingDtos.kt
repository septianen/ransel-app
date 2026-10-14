package app.ransel.shared.dto

import app.ransel.shared.model.PackingCategory
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

@Serializable
data class PackingItemDto(
    val id: String,
    val tripId: String,
    val category: PackingCategory = PackingCategory.OTHER,
    val name: String,
    val quantity: Int = 1,
    val isPacked: Boolean = false,
    val sortOrder: Double = 0.0,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** POST /trips/{id}/packing. Validation: name not blank, quantity > 0. */
@Serializable
data class CreatePackingItemRequest(
    val id: String,
    val category: PackingCategory = PackingCategory.OTHER,
    val name: String,
    val quantity: Int = 1,
    val sortOrder: Double = 0.0,
)

/** PATCH /trips/{id}/packing/{itemId}. null = leave unchanged. Ticking = {"isPacked": true}. */
@Serializable
data class PatchPackingItemRequest(
    val category: PackingCategory? = null,
    val name: String? = null,
    val quantity: Int? = null,
    val isPacked: Boolean? = null,
    val sortOrder: Double? = null,
)

// ── Templates (Phase 4) ─────────────────────────────────────────────────────

@Serializable
data class PackingTemplateDto(
    val id: String,
    /** null = built-in template (beach, city trip, mountain hike), seeded by the server. */
    val ownerId: String? = null,
    /** Set when the owner chose "Bagikan ke anggota trip": every member of that trip can use it. */
    val sharedTripId: String? = null,
    val name: String,
    val items: List<TemplateItem>,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

/** One line of a template. quantity null = "Simpan jumlah" was off (use 1 when applying). Checked state is never stored. */
@Serializable
data class TemplateItem(
    val category: PackingCategory,
    val name: String,
    val quantity: Int? = null,
)

/** POST /packing-templates ("Simpan sebagai template"). The client sends the items it shows. */
@Serializable
data class CreatePackingTemplateRequest(
    val id: String,
    val name: String,
    val items: List<TemplateItem>,
    val sharedTripId: String? = null,
)

/** POST /trips/{id}/packing/apply-template → 201 List<PackingItemDto> (only the created items). */
@Serializable
data class ApplyTemplateRequest(
    val templateId: String,
    /** Skip items whose name (case-insensitive) + category already exist in the trip. */
    val skipExisting: Boolean = true,
)
