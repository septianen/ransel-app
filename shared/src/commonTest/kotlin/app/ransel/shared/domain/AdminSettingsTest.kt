package app.ransel.shared.domain

import app.ransel.shared.model.SettingKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminSettingsTest {
    private val models = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash")

    @Test fun creditNumbersStayInTheirRange() {
        assertNull(validateSetting(SettingKey.COST_RECEIPT, "2", models))
        assertNull(validateSetting(SettingKey.SIGNUP_GRANT, "1000", models))
        assertEquals("OUT_OF_RANGE", validateSetting(SettingKey.SIGNUP_GRANT, "1001", models))
        assertEquals("OUT_OF_RANGE", validateSetting(SettingKey.COST_SEARCH, "-1", models))
        assertEquals("NOT_A_NUMBER", validateSetting(SettingKey.DAILY_FREE, "tiga", models))
        assertEquals("OUT_OF_RANGE", validateSetting(SettingKey.SUGGESTIONS_PER_HOUR, "0", models))
    }

    @Test fun theBudgetAlertModeAndModelComeFromTheirLists() {
        assertNull(validateSetting(SettingKey.AI_MONTHLY_BUDGET_USD, "170,50", models))
        assertEquals("NOT_A_NUMBER", validateSetting(SettingKey.AI_MONTHLY_BUDGET_USD, "170.555", models))
        assertEquals("NOT_ALLOWED", validateSetting(SettingKey.AI_BUDGET_ALERT_PERCENT, "85", models))
        assertNull(validateSetting(SettingKey.AI_BUDGET_MODE, "CACHE_ONLY", models))
        assertEquals("NOT_ALLOWED", validateSetting(SettingKey.AI_BUDGET_MODE, "STOP", models))
        assertEquals("NOT_ALLOWED", validateSetting(SettingKey.LLM_MODEL, "gpt-9", models))
    }

    @Test fun usdTextRoundTrips() {
        assertEquals(170_500_000L, usdToMicro("170.5"))
        assertEquals(170_000_000L, usdToMicro("170"))
        assertEquals("170.50", microToUsdText(170_500_000))
        assertEquals("170", microToUsdText(170_000_000))
    }

    @Test fun androidUpdateSettingsAreCheckedByShape() {
        assertNull(validateSetting(SettingKey.ANDROID_MIN_VERSION, "", models))
        assertNull(validateSetting(SettingKey.ANDROID_MIN_VERSION, "1.2.3", models))
        assertEquals("NOT_A_VERSION", validateSetting(SettingKey.ANDROID_LATEST_VERSION, "1.2", models))
        assertNull(validateSetting(SettingKey.ANDROID_UPDATE_MESSAGE, "", models))
        assertEquals("TOO_LONG", validateSetting(SettingKey.ANDROID_UPDATE_MESSAGE, "x".repeat(MAX_UPDATE_MESSAGE + 1), models))
        assertNull(validateSetting(SettingKey.ANDROID_STORE_URL, "", models))
        assertEquals("NOT_A_URL", validateSetting(SettingKey.ANDROID_STORE_URL, "http://play.google.com/store/apps/details?id=app.ransel", models))
    }

    @Test fun theMinimumCannotPassTheLatestAndAVersionNeedsAStoreLink() {
        val play = "https://play.google.com/store/apps/details?id=app.ransel"
        assertEquals(emptyMap(), androidUpdateProblems("1.1.0", "1.2.0", play))
        assertEquals(emptyMap(), androidUpdateProblems("", "", ""))
        assertEquals(mapOf(SettingKey.ANDROID_MIN_VERSION to "MIN_AFTER_LATEST"), androidUpdateProblems("1.3.0", "1.2.0", play))
        assertEquals(mapOf(SettingKey.ANDROID_STORE_URL to "STORE_URL_REQUIRED"), androidUpdateProblems("1.2.0", "", ""))
        assertEquals(mapOf(SettingKey.ANDROID_STORE_URL to "NOT_A_URL"), androidUpdateProblems("", "1.2.0", "play.google.com/store"))
        assertEquals(mapOf(SettingKey.ANDROID_MIN_VERSION to "NOT_A_VERSION"), androidUpdateProblems("1.2", "1.3.0", play))
    }
}
