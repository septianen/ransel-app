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
    /**
     * Bersama: who brings it ("Saya bawa"); null = "Belum ada yang bawa". Once someone brings it, only they tick it.
     * These trip-wide items are the "Bersama" list; each member's own list is [PersonalItemDto].
     */
    val assigneeMemberId: String? = null,
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
    /** "Wajib" on a member list saved as a template. */
    val required: Boolean = false,
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

// ── Pribadi: each member's own list (stage 7) ───────────────────────────────

/**
 * One line of a member's own packing list. Only its owner sees it (sync pulls only the caller's rows). Lines that came
 * from the trip owner's list carry [sourceItemId] and [required] ("Wajib"); their name and quantity follow the list,
 * the check mark is the member's own and survives edits of the list.
 */
@Serializable
data class PersonalItemDto(
    val id: String,
    val tripId: String,
    val ownerMemberId: String,
    val category: PackingCategory = PackingCategory.OTHER,
    val name: String,
    val quantity: Int = 1,
    val isPacked: Boolean = false,
    val sortOrder: Double = 0.0,
    /** The member list line this came from; null = added by the member. */
    val sourceItemId: String? = null,
    val required: Boolean = false,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
)

/** POST /trips/{id}/personal-items: a member's own line. Validation as for packing items. */
@Serializable
data class CreatePersonalItemRequest(
    val id: String,
    val category: PackingCategory = PackingCategory.OTHER,
    val name: String,
    val quantity: Int = 1,
    val sortOrder: Double = 0.0,
)

/** PATCH /trips/{id}/personal-items/{itemId}. On a line from the owner's list only [isPacked] can change. */
@Serializable
data class PatchPersonalItemRequest(
    val category: PackingCategory? = null,
    val name: String? = null,
    val quantity: Int? = null,
    val isPacked: Boolean? = null,
    val sortOrder: Double? = null,
)

// ── Daftar bawaan anggota: the owner's list sent to members (stage 7) ───────

@Serializable
data class MemberListItemDto(
    val id: String,
    val category: PackingCategory = PackingCategory.OTHER,
    val name: String,
    val quantity: Int = 1,
    /** "Wajib". */
    val required: Boolean = false,
    val sortOrder: Double = 0.0,
)

/** GET /trips/{id}/member-list (any member). One list per trip. */
@Serializable
data class MemberListDto(
    val tripId: String,
    val name: String,
    /** Goes up on every save; a save must name the version it edited. */
    val version: Int,
    val items: List<MemberListItemDto>,
    /** Sent to everyone, members who join later included. */
    val sentToAll: Boolean = false,
    /** Who received it (when not [sentToAll]). */
    val recipientMemberIds: List<String> = emptyList(),
    val sentAt: Timestamp? = null,
    val createdByMemberId: String,
    val updatedAt: Timestamp,
)

/**
 * PUT /trips/{id}/member-list (OWNER or EDITOR). [baseVersion] = the version that was edited (null for a new list);
 * another one answers 409 LIST_STALE. Once sent, the server applies the difference to every recipient: new lines are
 * added, removed lines disappear (checked or not), changed lines are updated; check marks and own lines stay.
 */
@Serializable
data class SaveMemberListRequest(
    val name: String,
    val baseVersion: Int? = null,
    val items: List<MemberListItemDto>,
)

/** POST /trips/{id}/member-list/send. [memberIds] empty = everyone, members who join later included. */
@Serializable
data class SendMemberListRequest(
    val memberIds: List<String> = emptyList(),
    /** "Simpan sebagai template saya": the lines (with "Wajib", never check marks) become the caller's template. */
    val saveAsTemplate: Boolean = false,
    val templateName: String? = null,
)

/** GET /trips/{id}/member-list/progress (OWNER or EDITOR): one row per recipient. */
@Serializable
data class MemberListProgressDto(
    val memberId: String,
    val requiredDone: Int,
    val requiredTotal: Int,
    val done: Int,
    val total: Int,
)

/** POST /trips/{id}/member-list/remind ("Ingatkan"): a push to members with an account, at most once per 12 hours each. */
@Serializable
data class RemindMemberListRequest(val memberIds: List<String>)
