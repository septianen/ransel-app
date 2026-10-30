package app.ransel.admin

import app.ransel.admin.feature.monitoring.SettingsForm
import app.ransel.shared.dto.AdminSettingDto
import app.ransel.shared.dto.AdminSettingsDto
import app.ransel.shared.model.SettingKey
import app.ransel.shared.model.SettingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Karung stage C (F9.5): what "Simpan" sends, "Kembalikan bawaan", and the shared value rule. */
class SettingsTest {
    private val saved = AdminSettingsDto(
        settings = SettingKey.entries.map { key ->
            when (key) {
                SettingKey.COST_RECEIPT -> AdminSettingDto(key, "2", "1", SettingSource.OVERRIDE, "admin@example.com")
                SettingKey.LLM_MODEL -> AdminSettingDto(key, "gemini-3.1-flash-lite", "gemini-3.1-flash-lite", SettingSource.ENV)
                SettingKey.AI_BUDGET_MODE -> AdminSettingDto(key, "KEEP_GOING", "KEEP_GOING", SettingSource.ENV)
                SettingKey.AI_BUDGET_ALERT_PERCENT -> AdminSettingDto(key, "80", "80", SettingSource.ENV)
                else -> AdminSettingDto(key, "3", "3", SettingSource.ENV)
            }
        },
        llmModels = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash"),
    )

    @Test
    fun onlyChangedValuesAreSentAndAResetClearsTheOverride() {
        val form = SettingsForm(saved)
        assertTrue(form.changes.isEmpty())
        assertFalse(form.canSave)

        val edited = form.edit(SettingKey.COST_SEARCH, "4").reset(SettingKey.COST_RECEIPT)
        assertEquals(mapOf(SettingKey.COST_SEARCH to "4", SettingKey.COST_RECEIPT to null), edited.changes)
        assertEquals("1", edited.text(SettingKey.COST_RECEIPT), "the field shows the env value")
        assertFalse(edited.isOverride(SettingKey.COST_RECEIPT))
        assertTrue(edited.isOverride(SettingKey.COST_SEARCH))
        assertTrue(edited.canSave)

        // typing the value that is already saved is no change
        assertTrue(form.edit(SettingKey.COST_SEARCH, "3").changes.isEmpty())
    }

    @Test
    fun valuesOutsideTheSharedRuleBlockSaving() {
        val form = SettingsForm(saved).edit(SettingKey.DAILY_FREE, "500").edit(SettingKey.LLM_MODEL, "gpt-9")
        assertEquals(mapOf(SettingKey.DAILY_FREE to "OUT_OF_RANGE", SettingKey.LLM_MODEL to "NOT_ALLOWED"), form.problems)
        assertFalse(form.canSave)
    }
}
