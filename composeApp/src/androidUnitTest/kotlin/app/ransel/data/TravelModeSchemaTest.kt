package app.ransel.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Room v6 is an AutoMigration from v5: the exported schema must list `travel_modes`. */
class TravelModeSchemaTest {
    @Test
    fun v6AddsTheTravelModesTable() {
        val schema = listOf(
            File("schemas/app.ransel.data.local.RanselDatabase/6.json"),
            File("composeApp/schemas/app.ransel.data.local.RanselDatabase/6.json"),
        ).first { it.exists() }.readText()
        assertTrue("\"version\": 6" in schema || "\"version\":6" in schema.replace(" ", ""))
        assertTrue("travel_modes" in schema)
        assertTrue("fromItemId" in schema)
        assertTrue("toItemId" in schema)
        val v5 = listOf(
            File("schemas/app.ransel.data.local.RanselDatabase/5.json"),
            File("composeApp/schemas/app.ransel.data.local.RanselDatabase/5.json"),
        ).first { it.exists() }.readText()
        assertTrue("travel_modes" !in v5)
    }
}
