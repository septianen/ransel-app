package app.ransel.admin

import app.ransel.admin.feature.shell.AdminSection
import kotlin.test.Test
import kotlin.test.assertEquals

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
    }
}
