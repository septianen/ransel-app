package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.AppSettings
import app.ransel.server.db.Users
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.domain.microToUsdText
import app.ransel.shared.domain.usdToMicro
import app.ransel.shared.domain.validateSetting
import app.ransel.shared.dto.AdminSettingDto
import app.ransel.shared.dto.AdminSettingsDto
import app.ransel.shared.dto.UpdateSettingsRequest
import app.ransel.shared.model.AdminAction
import app.ransel.shared.model.AiBudgetMode
import app.ransel.shared.model.SettingKey
import app.ransel.shared.model.SettingSource
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.util.UUID
import kotlin.time.toKotlinInstant

/**
 * Settings Karung changes without a deploy (admin-spec "Credit settings", B9.8). Effective value = override in
 * `app_settings` ?: env var ?: built-in default; [current] is the [base] config with the overrides applied, cached in
 * memory and rebuilt on every write (one server instance). Services read their numbers from [current], so a change
 * applies to the next charge, grant or search.
 */
class SettingsService(private val base: AppConfig) {
    private data class Override(val value: String, val by: UUID, val at: Instant)

    @Volatile private var overrides: Map<SettingKey, Override> = emptyMap()

    @Volatile var current: AppConfig = base
        private set

    /** Reads the overrides; call once the database is migrated. */
    fun load() {
        overrides = transaction {
            AppSettings.selectAll().associate { it[AppSettings.key] to Override(it[AppSettings.value], it[AppSettings.updatedBy], it[AppSettings.updatedAt]) }
        }
        current = apply(base, overrides.mapValues { it.value.value })
    }

    fun settings(): AdminSettingsDto {
        val now = overrides
        val emails = if (now.isEmpty()) emptyMap() else transaction {
            Users.select(Users.id, Users.email).where { Users.id inList now.values.map { it.by }.distinct() }
                .associate { it[Users.id] to it[Users.email] }
        }
        return AdminSettingsDto(
            settings = SettingKey.entries.map { key ->
                val o = now[key]
                AdminSettingDto(
                    key = key,
                    value = o?.value ?: valueOf(base, key),
                    envValue = valueOf(base, key),
                    source = if (o != null) SettingSource.OVERRIDE else SettingSource.ENV,
                    updatedByEmail = o?.let { emails[it.by] },
                    updatedAt = o?.at?.toKotlinInstant(),
                )
            },
            llmModels = base.llmModels,
        )
    }

    /** Sets or clears overrides, all or nothing; one audit row per key that changes. */
    fun update(admin: AdminPrincipal, body: UpdateSettingsRequest): AdminSettingsDto {
        val problems = body.values.mapNotNull { (key, value) ->
            value?.let { v -> validateSetting(key, v, base.llmModels)?.let { key.name to it } }
        }.toMap()
        if (problems.isNotEmpty()) throw validationFailed(problems)
        transaction {
            for ((key, raw) in body.values) {
                val before = overrides[key]?.value ?: valueOf(base, key)
                val value = raw?.let { normalise(key, it) }
                val after = value ?: valueOf(base, key)
                // clearing an override that is not there, or setting the value it already has, changes nothing
                if (value == null && key !in overrides) continue
                if (value != null && value == overrides[key]?.value) continue
                if (value == null) {
                    AppSettings.deleteWhere { AppSettings.key eq key }
                } else {
                    AppSettings.upsert {
                        it[AppSettings.key] = key
                        it[AppSettings.value] = value
                        it[updatedBy] = admin.id
                        it[updatedAt] = Instant.now()
                    }
                }
                writeAudit(admin, AdminAction.SETTINGS_UPDATE, "SETTING", key.name, JsonPrimitive(before), JsonPrimitive(after))
            }
        }
        load()
        return settings()
    }

    companion object {
        private fun normalise(key: SettingKey, value: String): String {
            val text = value.trim()
            return when (key) {
                SettingKey.AI_MONTHLY_BUDGET_USD -> microToUsdText(usdToMicro(text)!!)
                else -> text.toIntOrNull()?.toString() ?: text
            }
        }

        /** The setting's value in [config], as text. */
        fun valueOf(config: AppConfig, key: SettingKey): String = when (key) {
            SettingKey.SIGNUP_GRANT -> config.creditSignupGrant.toString()
            SettingKey.DAILY_FREE -> config.creditDailyFree.toString()
            SettingKey.COST_SEARCH -> config.creditCostSearch.toString()
            SettingKey.COST_NEXT_BATCH -> config.creditCostNextBatch.toString()
            SettingKey.COST_PLACE_DETAIL -> config.creditCostPlaceDetail.toString()
            SettingKey.COST_RECEIPT -> config.creditCostReceipt.toString()
            SettingKey.AI_MONTHLY_BUDGET_USD -> microToUsdText(config.aiMonthlyBudgetMicroUsd)
            SettingKey.AI_BUDGET_ALERT_PERCENT -> config.aiBudgetAlertPercent.toString()
            SettingKey.AI_BUDGET_MODE -> config.aiBudgetMode.name
            SettingKey.SUGGESTIONS_PER_HOUR -> config.suggestionsRateLimitPerHour.toString()
            SettingKey.LLM_MODEL -> config.llmModel
        }

        /** [config] with [values] applied; a stored value that no longer passes the rule (a model taken off the list) is skipped. */
        fun apply(config: AppConfig, values: Map<SettingKey, String>): AppConfig =
            values.entries.fold(config) { c, (key, value) ->
                if (validateSetting(key, value, config.llmModels) != null) return@fold c
                when (key) {
                    SettingKey.SIGNUP_GRANT -> c.copy(creditSignupGrant = value.toInt())
                    SettingKey.DAILY_FREE -> c.copy(creditDailyFree = value.toInt())
                    SettingKey.COST_SEARCH -> c.copy(creditCostSearch = value.toInt())
                    SettingKey.COST_NEXT_BATCH -> c.copy(creditCostNextBatch = value.toInt())
                    SettingKey.COST_PLACE_DETAIL -> c.copy(creditCostPlaceDetail = value.toInt())
                    SettingKey.COST_RECEIPT -> c.copy(creditCostReceipt = value.toInt())
                    SettingKey.AI_MONTHLY_BUDGET_USD -> c.copy(aiMonthlyBudgetMicroUsd = usdToMicro(value)!!)
                    SettingKey.AI_BUDGET_ALERT_PERCENT -> c.copy(aiBudgetAlertPercent = value.toInt())
                    SettingKey.AI_BUDGET_MODE -> c.copy(aiBudgetMode = AiBudgetMode.valueOf(value))
                    SettingKey.SUGGESTIONS_PER_HOUR -> c.copy(suggestionsRateLimitPerHour = value.toInt())
                    SettingKey.LLM_MODEL -> c.copy(llmModel = value)
                }
            }
    }
}
