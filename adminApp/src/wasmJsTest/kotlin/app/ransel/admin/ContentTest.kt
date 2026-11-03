package app.ransel.admin

import app.ransel.admin.feature.content.TemplateEdit
import app.ransel.admin.feature.shell.AdminSection
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.model.PackingCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Karung stage D (F9.6): content routes. Screen texts are not edited in Karung since 3 Nov 2026. */
class ContentTest {
    @Test
    fun contentHashesOpenTheirEditor() {
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_ERRORS), AdminRoute.parse("#/content/errors"))
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_INTERESTS), AdminRoute.parse("#/content/interests"))
        assertEquals("#/content/categories", AdminRoute.Content(AdminSection.CONTENT_CATEGORIES).hash)
        // an old bookmark of the removed string editor lands on the error messages
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_ERRORS), AdminRoute.parse("#/content/strings"))
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_ERRORS), AdminRoute.parse("#/content"))
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_TEMPLATES), AdminRoute.parse("#/content/templates"))
        assertEquals("#/content/templates", AdminRoute.Content(AdminSection.CONTENT_TEMPLATES).hash)
    }

    @Test
    fun aTemplateCanOnlyBeSavedWithANameAndNamedItems() {
        val item = TemplateItem(PackingCategory.CLOTHING, "Kaos", 3)
        assertTrue(TemplateEdit(null, "Konser", listOf(item)).problems.isEmpty())
        assertEquals("REQUIRED", TemplateEdit(null, "", listOf(item)).problems["name"])
        assertEquals("NAME_REQUIRED", TemplateEdit("t1", "Konser", listOf(item, item.copy(name = " "))).problems["items.1"])
    }
}
