package app.ransel.server

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Karung stage C (B9.8): settings overrides, their audit rows, and the AI budget's CACHE_ONLY mode. */
class AdminSettingsRoutesTest {
    private val config = TestDb.config().copy(adminEmails = setOf("admin@example.com"))

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private suspend fun Api.settings(): Map<String, JsonObject> =
        get("/admin/settings").data().jsonObject.getValue("settings").jsonArray.map { it.jsonObject }.associateBy { it.str("key") }

    @Test
    fun anOverrideWinsOverEnvIsAuditedAndCanBeCleared() = apiTest(config) {
        val traveller = token
        signUp(email = "admin@example.com")
        val admin = token
        val start = settings()
        assertEquals("1", start.getValue("COST_RECEIPT").str("value"))
        assertEquals("ENV", start.getValue("COST_RECEIPT").str("source"))
        assertEquals(SettingKeyCount, start.size)

        val put = put("/admin/settings", """{"values":{"COST_RECEIPT":"2","SIGNUP_GRANT":"50","AI_MONTHLY_BUDGET_USD":"170,5"}}""")
        assertEquals(HttpStatusCode.OK, put.status, put.bodyAsText())
        val after = settings()
        assertEquals("2", after.getValue("COST_RECEIPT").str("value"))
        assertEquals("1", after.getValue("COST_RECEIPT").str("envValue"))
        assertEquals("OVERRIDE", after.getValue("COST_RECEIPT").str("source"))
        assertEquals("admin@example.com", after.getValue("COST_RECEIPT").str("updatedByEmail"))
        assertEquals("170.50", after.getValue("AI_MONTHLY_BUDGET_USD").str("value"))

        // the traveller app reads the new receipt cost without a release
        token = traveller
        assertEquals("2", get("/credits").data().jsonObject.getValue("costs").jsonObject.str("receipt"))
        token = admin

        val audit = get("/admin/audit?action=SETTINGS_UPDATE").data().jsonArray.map { it.jsonObject }
        assertEquals(setOf("COST_RECEIPT", "SIGNUP_GRANT", "AI_MONTHLY_BUDGET_USD"), audit.map { it.str("targetId") }.toSet())
        val receipt = audit.single { it.str("targetId") == "COST_RECEIPT" }
        assertEquals("1", receipt.str("before"))
        assertEquals("2", receipt.str("after"))

        // "Kembalikan bawaan"; the same value again is no change and no audit row
        put("/admin/settings", """{"values":{"COST_RECEIPT":null,"SIGNUP_GRANT":"50"}}""")
        val cleared = settings().getValue("COST_RECEIPT")
        assertEquals("1", cleared.str("value"))
        assertEquals("ENV", cleared.str("source"))
        assertNull(cleared["updatedByEmail"]?.jsonPrimitive?.content?.takeIf { it != "null" })
        assertEquals(4, get("/admin/audit?action=SETTINGS_UPDATE").data().jsonArray.size)
    }

    @Test
    fun valuesOutsideTheRulesAreRefusedAllOrNothing() = apiTest(config) {
        assertEquals(HttpStatusCode.Forbidden, put("/admin/settings", """{"values":{"COST_SEARCH":"2"}}""").status)
        signUp(email = "admin@example.com")
        val bad = put("/admin/settings", """{"values":{"COST_SEARCH":"2","DAILY_FREE":"500","LLM_MODEL":"gpt-9","AI_BUDGET_ALERT_PERCENT":"85"}}""")
        assertEquals("VALIDATION_FAILED", bad.errorCode())
        val details = TestJson.parseToJsonElement(bad.bodyAsText()).jsonObject.getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals(mapOf("DAILY_FREE" to "OUT_OF_RANGE", "LLM_MODEL" to "NOT_ALLOWED", "AI_BUDGET_ALERT_PERCENT" to "NOT_ALLOWED"), details.mapValues { it.value.jsonPrimitive.content })
        assertEquals("3", settings().getValue("COST_SEARCH").str("value"), "nothing was saved")
    }

    @Test
    fun aSpentBudgetInCacheOnlyModeStopsNewSearchesButNotCachedOnes() = apiTest(config, ai = MonitoringAi) {
        val traveller = token
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip, start = "2099-11-20", end = "2099-11-23", destination = "Bali"))
        assertEquals(HttpStatusCode.OK, post("/recommendations", monitoringSearch(trip)).status)
        val balance = get("/credits").data().jsonObject.str("dailyFreeLeft")

        signUp(email = "admin@example.com")
        assertEquals(HttpStatusCode.OK, put("/admin/settings", """{"values":{"AI_MONTHLY_BUDGET_USD":"0.01","AI_BUDGET_MODE":"CACHE_ONLY"}}""").status)
        val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
        TestDb.exec("INSERT INTO provider_usage (day, call, service, calls, cost_micro_usd) VALUES ('$today', 'PLACES_PHOTO', 'PLACES', 2, 14000) ON CONFLICT (day, call) DO UPDATE SET cost_micro_usd = 14000")

        token = traveller
        // the same search comes from the cache and still works
        assertEquals(HttpStatusCode.OK, post("/recommendations", monitoringSearch(trip)).status)
        val afterCache = get("/credits").data().jsonObject.str("dailyFreeLeft")
        // a new search would ask the LLM: paused, and nothing is charged
        val fresh = post("/recommendations", monitoringSearch(trip).replace("\"Diving\"", "\"Surfing\""))
        assertEquals(HttpStatusCode.ServiceUnavailable, fresh.status)
        assertEquals("AI_PAUSED", fresh.errorCode())
        assertEquals(afterCache, get("/credits").data().jsonObject.str("dailyFreeLeft"))
        assertEquals(true, balance.toInt() >= afterCache.toInt())
    }

    private companion object {
        val SettingKeyCount = app.ransel.shared.model.SettingKey.entries.size
    }
}
