package app.ransel.shared.domain

import app.ransel.shared.dto.MemberListItemDto
import app.ransel.shared.dto.PersonalItemDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MemberListsTest {
    private fun item(id: String, name: String, required: Boolean = false, qty: Int = 1) = MemberListItemDto(id, name = name, quantity = qty, required = required)

    @Test
    fun aSavedListIsComparedLineByLine() {
        val before = listOf(item("1", "Jaket"), item("2", "Senter", required = true), item("3", "Sandal"))
        val after = listOf(item("1", "Jaket gunung"), item("2", "Senter", required = true), item("4", "Matras", qty = 2))
        val diff = diffMemberList(before, after)
        assertEquals(listOf("4"), diff.added.map { it.id })
        assertEquals(listOf("3"), diff.removedIds)
        assertEquals(listOf("Jaket gunung"), diff.changed.map { it.name })
        assertTrue(diffMemberList(after, after).isEmpty)
    }

    @Test
    fun aListNeedsANameAndSaneLines() {
        assertEquals(emptyMap(), validateMemberList("Bawaan Rinjani", listOf(item("1", "Jaket"))))
        assertEquals(
            mapOf("name" to "REQUIRED", "items" to "DUPLICATE_ID"),
            validateMemberList(" ", listOf(item("1", "Jaket"), item("1", "Topi"))),
        )
        assertEquals(mapOf("items" to "INVALID_QUANTITY"), validateMemberList("x", listOf(item("1", "Jaket", qty = 0))))
    }

    @Test
    fun onlyRequiredLinesFromTheListCountForReadiness() {
        val now = Instant.parse("2026-10-26T00:00:00Z")
        fun p(id: String, source: String?, required: Boolean, packed: Boolean, deleted: Boolean = false) =
            PersonalItemDto(id, "t", "m", name = id, sourceItemId = source, required = required, isPacked = packed, createdAt = now, updatedAt = now, deletedAt = if (deleted) now else null)
        val progress = requiredProgress(
            listOf(
                p("a", "1", required = true, packed = true),
                p("b", "2", required = true, packed = false),
                p("c", "3", required = false, packed = false),
                p("d", null, required = true, packed = false), // own line: never "Wajib" for the owner's card
                p("e", "4", required = true, packed = false, deleted = true),
            ),
        )
        assertEquals(RequiredProgress(1, 2), progress)
        assertTrue(RequiredProgress(2, 2).ready)
    }
}
