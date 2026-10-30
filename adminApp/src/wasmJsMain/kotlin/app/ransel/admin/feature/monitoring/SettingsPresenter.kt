package app.ransel.admin.feature.monitoring

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.shared.domain.validateSetting
import app.ransel.shared.dto.AdminSettingDto
import app.ransel.shared.dto.AdminSettingsDto
import app.ransel.shared.dto.UpdateSettingsRequest
import app.ransel.shared.model.SettingKey
import app.ransel.shared.model.SettingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "Pengaturan" form of the System page (F9.5): what is saved, what is typed, and which overrides are to be
 * cleared ("Kembalikan bawaan"). The rule for each value is the shared one the server applies.
 */
data class SettingsForm(
    val saved: AdminSettingsDto,
    val texts: Map<SettingKey, String> = saved.settings.associate { it.key to it.value },
    /** Overrides to clear on "Simpan"; their field shows the env value meanwhile. */
    val clearing: Set<SettingKey> = emptySet(),
) {
    fun setting(key: SettingKey): AdminSettingDto = saved.settings.first { it.key == key }

    fun text(key: SettingKey): String = texts[key].orEmpty()

    fun edit(key: SettingKey, text: String): SettingsForm = copy(texts = texts + (key to text), clearing = clearing - key)

    /** "Kembalikan bawaan": back to the env value; an override that is saved is cleared on "Simpan". */
    fun reset(key: SettingKey): SettingsForm {
        val s = setting(key)
        return copy(texts = texts + (key to s.envValue), clearing = if (s.source == SettingSource.OVERRIDE) clearing + key else clearing)
    }

    /** The source the field will have after saving. */
    fun isOverride(key: SettingKey): Boolean =
        key !in clearing && (setting(key).source == SettingSource.OVERRIDE || text(key).trim() != setting(key).value)

    /** What "Simpan" sends: changed values, and null for an override to clear. */
    val changes: Map<SettingKey, String?>
        get() = SettingKey.entries.mapNotNull { key ->
            when {
                key in clearing -> key to null
                text(key).trim() != setting(key).value -> key to text(key).trim()
                else -> null
            }
        }.toMap()

    /** Problems per field, as `validateSetting` codes. */
    val problems: Map<SettingKey, String>
        get() = changes.mapNotNull { (key, value) -> value?.let { v -> validateSetting(key, v, saved.llmModels)?.let { key to it } } }.toMap()

    val canSave: Boolean get() = changes.isNotEmpty() && problems.isEmpty()
}

data class SettingsUiState(
    val loading: Boolean = true,
    val form: SettingsForm? = null,
    val error: ApiFailure? = null,
    /** The "Simpan" confirmation is open. */
    val confirming: Boolean = false,
    val saving: Boolean = false,
    val saveError: ApiFailure? = null,
    /** Shown once after a save ("Pengaturan disimpan"). */
    val saved: Boolean = false,
)

class SettingsPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            when (val result = api.settings()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, form = SettingsForm(result.value)) }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }

    fun edit(key: SettingKey, text: String) = _state.update { s -> s.copy(form = s.form?.edit(key, text), saved = false) }

    fun reset(key: SettingKey) = _state.update { s -> s.copy(form = s.form?.reset(key), saved = false) }

    /** "Batal": back to what is saved. */
    fun cancel() = _state.update { s -> s.copy(form = s.form?.let { SettingsForm(it.saved) }, saved = false) }

    fun askToSave() {
        if (_state.value.form?.canSave == true) _state.update { it.copy(confirming = true, saveError = null) }
    }

    fun dismiss() = _state.update { if (it.saving) it else it.copy(confirming = false, saveError = null) }

    fun save() {
        val form = _state.value.form ?: return
        if (!form.canSave || _state.value.saving) return
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch {
            when (val result = api.updateSettings(UpdateSettingsRequest(form.changes))) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, confirming = false, form = SettingsForm(result.value), saved = true)
                }
                is ApiResult.Failure -> {
                    onAccessLost(result.error)
                    _state.update { it.copy(saving = false, saveError = result.error) }
                }
            }
        }
    }
}
