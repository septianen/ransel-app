package app.ransel.feature.polls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.shared.dto.PollSearchResult
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollPriceUnit
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.format.formatMoney
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ai_paused_body
import ransel.composeapp.generated.resources.near_pick
import ransel.composeapp.generated.resources.poll_closed_notice
import ransel.composeapp.generated.resources.poll_option_error_invalid
import ransel.composeapp.generated.resources.poll_price_night
import ransel.composeapp.generated.resources.poll_price_person
import ransel.composeapp.generated.resources.poll_search_add
import ransel.composeapp.generated.resources.poll_search_area
import ransel.composeapp.generated.resources.poll_search_budget_stay
import ransel.composeapp.generated.resources.poll_search_budget_food
import ransel.composeapp.generated.resources.poll_search_cost
import ransel.composeapp.generated.resources.poll_search_empty
import ransel.composeapp.generated.resources.poll_search_error_credits
import ransel.composeapp.generated.resources.poll_search_error_failed
import ransel.composeapp.generated.resources.poll_search_error_unavailable
import ransel.composeapp.generated.resources.poll_search_error_verify
import ransel.composeapp.generated.resources.poll_search_estimate
import ransel.composeapp.generated.resources.poll_search_go
import ransel.composeapp.generated.resources.poll_search_max
import ransel.composeapp.generated.resources.poll_search_min
import ransel.composeapp.generated.resources.poll_search_needs
import ransel.composeapp.generated.resources.poll_search_needs_hint
import ransel.composeapp.generated.resources.poll_search_title

/**
 * Jelajah for a stay or food poll: where, how much, what matters; the ticked results become options. Searching costs
 * credits like a new Explore search, so nothing runs until the button is pressed.
 */
@Composable
fun PollSearchSheet(
    kind: PollKind,
    state: PollSearchState,
    currency: String,
    destination: String,
    onArea: (String) -> Unit,
    /** Opens "Cari dekat mana?" (current location, last activity, another place). */
    onPickArea: () -> Unit,
    onMin: (String) -> Unit,
    onMax: (String) -> Unit,
    onNeeds: (String) -> Unit,
    onSearch: () -> Unit,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.poll_search_title), style = MaterialTheme.typography.titleLarge)
            val results = state.results
            if (results == null || state.loading) {
                IrisTextField(
                    label = stringResource(Res.string.poll_search_area),
                    value = state.area,
                    onValueChange = onArea,
                    placeholder = destination,
                    trailing = {
                        androidx.compose.material3.TextButton(onClick = onPickArea) {
                            app.ransel.ui.symbols.Symbol(app.ransel.ui.symbols.Symbols.locationOn, size = 18.dp)
                            Text(stringResource(Res.string.near_pick), Modifier.padding(start = 4.dp))
                        }
                    },
                )
                Text(
                    stringResource(if (kind == PollKind.STAY) Res.string.poll_search_budget_stay else Res.string.poll_search_budget_food),
                    style = MaterialTheme.typography.labelLarge,
                )
                MoneyField(stringResource(Res.string.poll_search_min), state.min, currency, if (state.badInput) stringResource(Res.string.poll_option_error_invalid) else null, optional = true, onChange = onMin)
                MoneyField(stringResource(Res.string.poll_search_max), state.max, currency, null, optional = true, onChange = onMax)
                IrisTextField(
                    label = stringResource(Res.string.poll_search_needs),
                    value = state.needs,
                    onValueChange = onNeeds,
                    placeholder = stringResource(Res.string.poll_search_needs_hint),
                )
                state.error?.let {
                    Text(stringResource(it.message()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(Res.string.poll_search_cost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LoadingButton(onClick = onSearch, loading = state.loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.poll_search_go), style = MaterialTheme.typography.titleMedium)
                }
            } else if (results.isEmpty()) {
                Text(stringResource(Res.string.poll_search_empty), style = MaterialTheme.typography.bodyMedium)
                LoadingButton(onClick = onRetry, loading = false, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(Res.string.poll_search_go))
                }
            } else {
                results.forEach { result ->
                    ResultRow(result, currency, checked = result.placeId in state.picked, onToggle = { onToggle(result.placeId) })
                }
                LoadingButton(
                    onClick = onAdd,
                    loading = state.adding,
                    enabled = state.picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(Res.string.poll_search_add, state.picked.size), style = MaterialTheme.typography.titleMedium)
                }
                state.error?.let {
                    Text(stringResource(it.message()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: PollSearchResult, currency: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(result.name, style = MaterialTheme.typography.titleSmall)
            Text(result.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            result.priceMinor?.let { price ->
                val perUnit = stringResource(
                    if (result.priceUnit == PollPriceUnit.NIGHT) Res.string.poll_price_night else Res.string.poll_price_person,
                    formatMoney(price, currency),
                )
                Text(stringResource(Res.string.poll_search_estimate, perUnit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun PollSearchError.message() = when (this) {
    PollSearchError.NO_CREDITS -> Res.string.poll_search_error_credits
    PollSearchError.NOT_VERIFIED -> Res.string.poll_search_error_verify
    PollSearchError.UNAVAILABLE -> Res.string.poll_search_error_unavailable
    PollSearchError.PAUSED -> Res.string.ai_paused_body
    PollSearchError.CLOSED -> Res.string.poll_closed_notice
    PollSearchError.FAILED -> Res.string.poll_search_error_failed
}
