package app.ransel.content

import app.ransel.shared.api.ApiErrorText
import app.ransel.shared.dto.ContentCategoryDto
import app.ransel.shared.dto.ContentDto
import app.ransel.shared.dto.ContentErrorDto
import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.PackingCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteContentTest {
    private val content = RemoteContent(
        ContentDto(
            version = 4,
            strings = mapOf("id" to mapOf("tab_home" to "Rumah", "expense_category_food" to "Makan")),
            errors = listOf(
                ContentErrorDto("INSUFFICIENT_CREDITS", ErrorTextDto(messageId = "Butuh {cost} kredit, sisa {available}.")),
                ContentErrorDto("NOT_FOUND", ErrorTextDto()),
            ),
            categories = listOf(
                ContentCategoryDto(CategoryKind.EXPENSE, "FOOD", labelId = "Kuliner", sortOrder = 1),
                ContentCategoryDto(CategoryKind.PACKING, "HEALTH", sortOrder = 0),
                ContentCategoryDto(CategoryKind.PACKING, "CLOTHING", sortOrder = 1, hidden = true),
                ContentCategoryDto(CategoryKind.PACKING, "DOCUMENTS", sortOrder = 2),
                ContentCategoryDto(CategoryKind.PACKING, "ELECTRONICS", sortOrder = 3),
                ContentCategoryDto(CategoryKind.PACKING, "TOILETRIES", sortOrder = 4),
                ContentCategoryDto(CategoryKind.PACKING, "OTHER", sortOrder = 5),
            ),
        ),
    )

    @Test
    fun onlyCategoryLabelsAreRemoteEvenFromAnOlderSavedCopy() {
        // an older build saved strings and errors too; they no longer apply (owner, 3 Nov 2026)
        assertNull(content.text("id", "tab_home"))
        assertEquals("Kuliner", content.text("id", "expense_category_food"))
        assertNull(content.text("en", "expense_category_food"))
    }

    @Test
    fun pickersFollowTheOrderAndLeaveHiddenOut() {
        val all = PackingCategory.entries
        assertEquals(
            listOf(PackingCategory.HEALTH, PackingCategory.DOCUMENTS, PackingCategory.ELECTRONICS, PackingCategory.TOILETRIES, PackingCategory.OTHER),
            content.ordered(CategoryKind.PACKING, all),
        )
        // the current choice and existing lists keep a hidden category
        assertEquals(PackingCategory.CLOTHING, content.ordered(CategoryKind.PACKING, all, keep = PackingCategory.CLOTHING)[1])
        assertEquals(6, content.ordered(CategoryKind.PACKING, all, hideHidden = false).size)
        // a list Karung never saved keeps the app's order
        assertEquals(Category.entries, content.ordered(CategoryKind.ITINERARY, Category.entries))
    }

    @Test
    fun errorCopyIsWhatTheLastErrorResponseSent() {
        ServerErrorCopy.clear()
        assertNull(ServerErrorCopy.forCode("INSUFFICIENT_CREDITS"))
        ServerErrorCopy.record("INSUFFICIENT_CREDITS", ApiErrorText(message = "Butuh 3 kredit, sisa 1.", illustration = "ic_nope"))
        val copy = ServerErrorCopy.forCode("INSUFFICIENT_CREDITS")!!.toCopy()
        assertEquals("Butuh 3 kredit, sisa 1.", copy.message)
        assertNull(copy.title)
        assertNull(copy.illustration, "an unknown drawable keeps the screen's own")
        // Karung went back to the app's copy
        ServerErrorCopy.record("INSUFFICIENT_CREDITS", null)
        assertNull(ServerErrorCopy.forCode("INSUFFICIENT_CREDITS"))
    }

    @Test
    fun formatArgumentsLikeComposeResources() {
        assertEquals("Bali, 3 hari", formatContent("%1\$s, %2\$d hari", arrayOf<Any>("Bali", 3)))
        assertEquals("%2\$s", formatContent("%2\$s", arrayOf("only one")))
    }
}
