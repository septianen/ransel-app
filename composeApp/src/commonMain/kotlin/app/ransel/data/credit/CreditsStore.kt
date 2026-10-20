package app.ransel.data.credit

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.CreditsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last known Explore credits of the signed-in user, shared by the Explore screens and the Credits screen.
 * Credits are server data (online only): this only remembers the last answer so screens do not flash empty.
 * A search response carries the new balance, so [update] is called with it and no extra request is needed.
 */
class CreditsStore {
    private val _state = MutableStateFlow<CreditsDto?>(null)
    val state: StateFlow<CreditsDto?> = _state.asStateFlow()

    fun update(credits: CreditsDto?) {
        _state.value = credits
    }

    suspend fun refresh(api: RanselApi): ApiResult<CreditsDto> =
        api.credits().also { if (it is ApiResult.Success) update(it.value) }
}
