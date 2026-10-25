package app.ransel.data

import app.ransel.shared.domain.memberIdFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `[{"userId":"u","shareMinor":1}]` -> `[{"memberId":"m","shareMinor":1}]` with `m = memberIdFor(tripId, u)`.
 * Used by the Room migration 4 -> 5 for the splits and payers JSON of an expense. Anything that cannot be read is
 * returned as it was, so a corrupt row never blocks the migration.
 */
fun userIdsToMemberIds(json: String?, tripId: String): String? {
    if (json == null) return null
    val array = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return json
    val converted = array.map { element ->
        val item = runCatching { element.jsonObject }.getOrNull() ?: return json
        val user = item["userId"]?.jsonPrimitive?.content ?: return@map element
        JsonObject(
            buildMap {
                put("memberId", JsonPrimitive(memberIdFor(tripId, user)))
                item.forEach { (key, value) -> if (key != "userId") put(key, value) }
            },
        )
    }
    return JsonArray(converted).toString()
}
