package app.ransel.shared.domain

import app.ransel.shared.dto.MemberListItemDto
import app.ransel.shared.dto.PersonalItemDto

/* "Daftar bawaan dari owner" (PRD "Packing list from the trip owner"). */

const val MAX_MEMBER_LIST_ITEMS = 200
const val MAX_MEMBER_LIST_NAME = 60

/** What changes for every recipient when a sent list is saved again. Check marks are never part of it. */
data class MemberListDiff(
    val added: List<MemberListItemDto>,
    val removedIds: List<String>,
    /** Same id, other name, quantity, category, "Wajib" or order. */
    val changed: List<MemberListItemDto>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removedIds.isEmpty() && changed.isEmpty()
}

fun diffMemberList(before: List<MemberListItemDto>, after: List<MemberListItemDto>): MemberListDiff {
    val old = before.associateBy { it.id }
    val new = after.associateBy { it.id }
    return MemberListDiff(
        added = after.filter { it.id !in old },
        removedIds = before.filter { it.id !in new }.map { it.id },
        changed = after.filter { item -> old[item.id]?.let { it != item } == true },
    )
}

/** Field → reason for a list to save. */
fun validateMemberList(name: String, items: List<MemberListItemDto>): Map<String, String> = buildMap {
    if (name.isBlank()) put("name", "REQUIRED") else if (name.trim().length > MAX_MEMBER_LIST_NAME) put("name", "TOO_LONG")
    if (items.size > MAX_MEMBER_LIST_ITEMS) put("items", "TOO_MANY")
    if (items.any { it.name.isBlank() || it.name.length > 100 }) put("items", "INVALID_NAME")
    if (items.any { it.quantity !in 1..999 }) put("items", "INVALID_QUANTITY")
    if (items.map { it.id }.toSet().size != items.size) put("items", "DUPLICATE_ID")
}

/** "3 dari 5 wajib siap": required lines of the owner's list that a member ticked. */
data class RequiredProgress(val done: Int, val total: Int) {
    val ready: Boolean get() = done == total
}

fun requiredProgress(items: List<PersonalItemDto>): RequiredProgress {
    val required = items.filter { it.deletedAt == null && it.sourceItemId != null && it.required }
    return RequiredProgress(required.count { it.isPacked }, required.size)
}
