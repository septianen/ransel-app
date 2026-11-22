package app.ransel.feature.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.explore.label
import app.ransel.shared.model.Interest
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.stringResource as composeStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.ai_paused_title
import ransel.composeapp.generated.resources.credits_error_generic
import ransel.composeapp.generated.resources.credits_free_today
import ransel.composeapp.generated.resources.explore_add_credits
import ransel.composeapp.generated.resources.explore_ask_charge_note
import ransel.composeapp.generated.resources.explore_ask_cta
import ransel.composeapp.generated.resources.explore_ask_cta_free
import ransel.composeapp.generated.resources.explore_ask_field_helper
import ransel.composeapp.generated.resources.explore_ask_field_placeholder
import ransel.composeapp.generated.resources.explore_explainer
import ransel.composeapp.generated.resources.explore_home_headline
import ransel.composeapp.generated.resources.explore_home_title
import ransel.composeapp.generated.resources.saved_ideas_title
import ransel.composeapp.generated.resources.explore_interests_limit
import ransel.composeapp.generated.resources.explore_interests_max_hint
import ransel.composeapp.generated.resources.explore_interests_title
import ransel.composeapp.generated.resources.explore_offline
import ransel.composeapp.generated.resources.explore_rate_limited_body
import ransel.composeapp.generated.resources.explore_rate_limited_body_minutes
import ransel.composeapp.generated.resources.explore_retry
import ransel.composeapp.generated.resources.explore_trip_door
import ransel.composeapp.generated.resources.explore_verify_email
import ransel.composeapp.generated.resources.state_connection_title
import ransel.composeapp.generated.resources.state_server_title

@Composable
fun InspirationHomeScreen(
    viewModel: InspirationHomeViewModel,
    onStartInterview: (String) -> Unit,
    onOpenCredits: () -> Unit,
    onVerifyEmail: () -> Unit,
    onTripSearch: () -> Unit,
    onOpenSavedIdeas: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    // fills CreditsStore from the server on entering the screen; the ViewModel reads the store
    app.ransel.feature.credits.rememberCredits()
    LaunchedEffect(state.sessionId) {
        val sessionId = state.sessionId ?: return@LaunchedEffect
        viewModel.consumeStarted()
        onStartInterview(sessionId)
    }
    LaunchedEffect(state.startState) {
        when (state.startState) {
            InspirationStartState.OutOfCredits -> onOpenCredits()
            InspirationStartState.NeedsVerification -> onVerifyEmail()
            else -> Unit
        }
    }
    InspirationHomeContent(
        state = state,
        onText = viewModel::setText,
        onToggle = viewModel::toggleInterest,
        onStart = viewModel::start,
        onDismissError = viewModel::dismissError,
        onCredits = onOpenCredits,
        onVerify = onVerifyEmail,
        onTripSearch = onTripSearch,
        onOpenSavedIdeas = onOpenSavedIdeas,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InspirationHomeContent(
    state: InspirationHomeUiState,
    onText: (String) -> Unit = {},
    onToggle: (Interest) -> Unit = {},
    onStart: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onCredits: () -> Unit = {},
    onVerify: () -> Unit = {},
    onTripSearch: () -> Unit = {},
    onOpenSavedIdeas: () -> Unit = {},
) {
    when (val start = state.startState) {
        InspirationStartState.Offline -> FullScreenState(
            title = stringResource(Res.string.state_connection_title),
            body = stringResource(Res.string.explore_offline),
            primaryLabel = stringResource(Res.string.explore_retry),
            onPrimary = onStart,
            secondaryLabel = stringResource(Res.string.action_close),
            onSecondary = onDismissError,
        )
        is InspirationStartState.RateLimited -> FullScreenState(
            title = stringResource(Res.string.ai_paused_title),
            body = if (start.minutes == null) stringResource(Res.string.explore_rate_limited_body)
            else composeStringResource(Res.string.explore_rate_limited_body_minutes, start.minutes),
            secondaryLabel = stringResource(Res.string.action_close),
            onSecondary = onDismissError,
        )
        InspirationStartState.Failed -> FullScreenState(
            title = stringResource(Res.string.state_server_title),
            body = stringResource(Res.string.credits_error_generic),
            primaryLabel = stringResource(Res.string.explore_retry),
            onPrimary = onStart,
            secondaryLabel = stringResource(Res.string.action_close),
            onSecondary = onDismissError,
        )
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.explore_home_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onOpenSavedIdeas) {
                    Symbol(Symbols.bookmark, contentDescription = stringResource(Res.string.saved_ideas_title))
                }
            }
            Text(stringResource(Res.string.explore_home_headline), style = MaterialTheme.typography.headlineMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextField(
                    value = state.text,
                    onValueChange = onText,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.explore_ask_field_placeholder)) },
                    trailingIcon = if (state.text.isNotEmpty()) {
                        { IconButton(onClick = { onText("") }) { Symbol(Symbols.close, size = 20.dp) } }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.explore_ask_field_helper), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.text.length}/120", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.explore_interests_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.explore_interests_max_hint), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Interest.entries.forEach { interest ->
                    val selected = interest in state.interests
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(interest) },
                        modifier = Modifier.height(36.dp),
                        label = { Text(composeStringResource(interest.label), style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Symbol(interestSymbol(interest), size = 18.dp, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) },
                        trailingIcon = if (selected) ({ Symbol(Symbols.check, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer) }) else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            if (state.limitReached) Text(
                stringResource(Res.string.explore_interests_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(Symbols.autoAwesome, size = 20.dp, tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(Res.string.explore_explainer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                composeStringResource(Res.string.credits_free_today, state.dailyFreeLeft, state.dailyFreeLimit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStart,
                enabled = start != InspirationStartState.Loading && !state.cannotAfford,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Symbols.autoAwesome, size = 20.dp)
                    Text(if (state.coveredByFreeCredits) stringResource(Res.string.explore_ask_cta_free) else composeStringResource(Res.string.explore_ask_cta, state.cost))
                }
            }
            if (start == InspirationStartState.OutOfCredits || state.cannotAfford) {
                TextButton(onClick = onCredits, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(Res.string.explore_add_credits))
                }
            }
            if (start == InspirationStartState.NeedsVerification) {
                TextButton(onClick = onVerify, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(Res.string.explore_verify_email))
                }
            }
            TextButton(onClick = onTripSearch, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(Res.string.explore_trip_door))
            }
        }
    }
}

/** Known credits below the session price: the CTA is disabled and "Isi kredit" shows. Unknown credits never block. */
private val InspirationHomeUiState.cannotAfford: Boolean get() = creditsKnown && availableCredits < cost

/** "Tanya AI · gratis hari ini" only when today's free credits pay the whole session. */
private val InspirationHomeUiState.coveredByFreeCredits: Boolean get() = creditsKnown && cost > 0 && dailyFreeLeft >= cost

private fun interestSymbol(interest: Interest): String = when (interest) {
    Interest.PHOTO -> Symbols.photoCamera
    Interest.SHOPPING -> Symbols.shoppingBag
    Interest.FOOD -> Symbols.restaurant
    Interest.ADVENTURE -> Symbols.hiking
    Interest.NIGHTLIFE -> Symbols.musicNote
    Interest.CULTURE -> Symbols.museum
    Interest.NATURE -> Symbols.forest
    Interest.RELAX -> Symbols.spa
}

@Preview @Composable private fun HomeEmptyPreview() = IrisPreview { InspirationHomeContent(InspirationHomeUiState()) }
@Preview @Composable private fun HomeLimitPreview() = IrisPreview { InspirationHomeContent(InspirationHomeUiState(interests = Interest.entries.take(5), limitReached = true)) }
@Preview @Composable private fun HomeRateLimitedPreview() = IrisPreview { InspirationHomeContent(InspirationHomeUiState(startState = InspirationStartState.RateLimited(5))) }
