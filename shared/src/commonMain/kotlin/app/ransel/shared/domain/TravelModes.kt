package app.ransel.shared.domain

import app.ransel.shared.dto.TravelModeDto

/** Field name → reason code. Empty = valid. */
fun validateTravelMode(fromItemId: String, toItemId: String, mode: LegKind): Map<String, String> = buildMap {
    if (fromItemId.isBlank()) put("fromItemId", "REQUIRED")
    if (toItemId.isBlank()) put("toItemId", "REQUIRED")
    if (fromItemId.isNotBlank() && fromItemId == toItemId) put("toItemId", "MUST_DIFFER")
    if (mode !in SAVED_LEG_KINDS) put("mode", "INVALID")
}

/** Live rows keyed by (from item id, to item id). Soft-deleted rows are left out. */
fun travelModeOverrides(rows: List<TravelModeDto>): Map<Pair<String, String>, LegKind> =
    rows.filter { it.deletedAt == null }.sortedBy { it.updatedAt }.associate { (it.fromItemId to it.toItemId) to it.mode }
