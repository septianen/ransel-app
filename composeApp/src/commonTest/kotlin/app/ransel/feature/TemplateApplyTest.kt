package app.ransel.feature

import app.ransel.feature.packing.templateItemsToAdd
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.model.PackingCategory
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateApplyTest {
    private val at = Instant.parse("2026-10-15T00:00:00Z")
    private val template = PackingTemplateDto(
        id = "t", name = "Pantai", createdAt = at, updatedAt = at,
        items = listOf(
            TemplateItem(PackingCategory.CLOTHING, "Kaos", 4),
            TemplateItem(PackingCategory.CLOTHING, "Celana pendek"),
            TemplateItem(PackingCategory.DOCUMENTS, "KTP"),
        ),
    )

    private fun item(category: PackingCategory, name: String) = PackingItemDto(
        id = name, tripId = "x", category = category, name = name, createdAt = at, updatedAt = at,
    )

    @Test
    fun skipsItemsAlreadyOnTheListIgnoringCaseAndSpaces() {
        val existing = listOf(item(PackingCategory.CLOTHING, " kaos "), item(PackingCategory.DOCUMENTS, "ktp"))
        assertEquals(listOf("Celana pendek"), templateItemsToAdd(template, existing, skipExisting = true).map { it.name })
    }

    @Test
    fun sameNameInAnotherCategoryIsNotADuplicate() {
        val existing = listOf(item(PackingCategory.OTHER, "KTP"))
        assertEquals(3, templateItemsToAdd(template, existing, skipExisting = true).size)
    }

    @Test
    fun addsEverythingWhenSkippingIsOff() {
        val existing = listOf(item(PackingCategory.CLOTHING, "Kaos"))
        assertEquals(3, templateItemsToAdd(template, existing, skipExisting = false).size)
    }
}
