package app.ransel.admin

import app.ransel.admin.feature.content.StringEdit
import app.ransel.admin.feature.content.shownId
import app.ransel.admin.feature.shell.AdminSection
import app.ransel.shared.dto.AdminContentStringDto
import app.ransel.shared.dto.ContentTextDto
import app.ransel.shared.model.ContentStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** Karung stage D (F9.6): content routes, what a row shows, and the shared placeholder rule in the editor. */
class ContentTest {
    private val row = AdminContentStringDto(
        key = "trip_duration", area = "trip",
        defaults = ContentTextDto("%1\$d hari · %2\$d malam", "%1\$d days · %2\$d nights"),
        published = ContentTextDto(id = "%1\$d hari, %2\$d malam"),
        status = ContentStatus.PUBLISHED,
    )

    @Test
    fun contentHashesOpenTheirEditor() {
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_STRINGS), AdminRoute.parse("#/content/strings"))
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_ERRORS), AdminRoute.parse("#/content/errors"))
        assertEquals(AdminRoute.Content(AdminSection.CONTENT_INTERESTS), AdminRoute.parse("#/content/interests"))
        assertEquals("#/content/categories", AdminRoute.Content(AdminSection.CONTENT_CATEGORIES).hash)
    }

    @Test
    fun aRowShowsTheDraftThenTheLiveTextThenTheBundledOne() {
        assertEquals("%1\$d hari, %2\$d malam", row.shownId())
        assertEquals("Lama", row.copy(draft = ContentTextDto(id = "Lama")).shownId())
        assertEquals("%1\$d hari · %2\$d malam", row.copy(published = null, status = ContentStatus.DEFAULT).shownId())
    }

    @Test
    fun theEditorRefusesALostPlaceholder() {
        assertEquals(mapOf("id" to "PLACEHOLDER_MISMATCH"), StringEdit(row.key, "Lama perjalanan", "").problems(row))
        assertEquals(emptyMap(), StringEdit(row.key, "%1\$d hari / %2\$d malam", "").problems(row))
    }
}
