package app.ransel.feature.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.model.Interest
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.preview.IrisPreview
import app.ransel.feature.explore.label
import org.jetbrains.compose.resources.stringResource as composeStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.ai_paused_title
import ransel.composeapp.generated.resources.explore_ask_cta
import ransel.composeapp.generated.resources.explore_ask_field_helper
import ransel.composeapp.generated.resources.explore_ask_field_placeholder
import ransel.composeapp.generated.resources.explore_home_headline
import ransel.composeapp.generated.resources.explore_interests_limit
import ransel.composeapp.generated.resources.explore_interests_max_hint
import ransel.composeapp.generated.resources.explore_interests_title
import ransel.composeapp.generated.resources.explore_offline
import ransel.composeapp.generated.resources.explore_question_failed
import ransel.composeapp.generated.resources.explore_rate_limited_body
import ransel.composeapp.generated.resources.explore_rate_limited_body_minutes
import ransel.composeapp.generated.resources.explore_retry
import ransel.composeapp.generated.resources.explore_trip_door
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.state_connection_title

@Composable
fun InspirationHomeScreen(
    viewModel: InspirationHomeViewModel,
    onStartInterview: (String) -> Unit,
    onOpenCredits: () -> Unit,
    onVerifyEmail: () -> Unit,
    onTripSearch: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.sessionId) { state.sessionId?.let(onStartInterview) }
    LaunchedEffect(state.startState) {
        when (state.startState) {
            InspirationStartState.OutOfCredits -> onOpenCredits()
            InspirationStartState.NeedsVerification -> onVerifyEmail()
            else -> Unit
        }
    }
    InspirationHomeContent(state, viewModel::setText, viewModel::toggleInterest, viewModel::start, onOpenCredits, onVerifyEmail, onTripSearch)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InspirationHomeContent(
    state: InspirationHomeUiState,
    onText: (String) -> Unit = {},
    onToggle: (Interest) -> Unit = {},
    onStart: () -> Unit = {},
    onCredits: () -> Unit = {},
    onVerify: () -> Unit = {},
    onTripSearch: () -> Unit = {},
) {
    when (val start = state.startState) {
        InspirationStartState.Offline -> FullScreenState(
            title = stringResource(Res.string.state_connection_title), body = stringResource(Res.string.explore_offline),
            primaryLabel = stringResource(Res.string.explore_retry), onPrimary = onStart,
            secondaryLabel = stringResource(Res.string.action_close), onSecondary = {},
        )
        is InspirationStartState.RateLimited -> FullScreenState(
            title = stringResource(Res.string.ai_paused_title),
            body = if (start.minutes == null) stringResource(Res.string.explore_rate_limited_body)
                else composeStringResource(Res.string.explore_rate_limited_body_minutes, start.minutes),
            secondaryLabel = stringResource(Res.string.action_close), onSecondary = {},
        )
        InspirationStartState.Failed -> FullScreenState(
            title = stringResource(Res.string.explore_question_failed), body = "",
            primaryLabel = stringResource(Res.string.explore_retry), onPrimary = onStart,
        )
        else -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.explore_home_headline), style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicTextField(state.text, onText, modifier = Modifier.fillMaxWidth(), maxLines = 2, decorationBox = { inner ->
                    if (state.text.isEmpty()) Text(stringResource(Res.string.explore_ask_field_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                })
                Text("${state.text.length}/120", style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(Res.string.explore_ask_field_helper), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.explore_interests_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.explore_interests_max_hint), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Interest.entries.forEach { interest ->
                    val selected = interest in state.interests
                    OutlinedButton(onClick = { onToggle(interest) }) { Text(composeStringResource(interest.label) + if (selected) " ✓" else "") }
                }
            }
            if (state.limitReached) Text(stringResource(Res.string.explore_interests_limit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("✨  ${stringResource(Res.string.explore_ask_field_helper)}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStart, enabled = start != InspirationStartState.Loading) {
                Text(composeStringResource(Res.string.explore_ask_cta, state.cost))
            }
            if (start == InspirationStartState.OutOfCredits) TextButton(onClick = onCredits) { Text("Isi kredit") }
            if (start == InspirationStartState.NeedsVerification) TextButton(onClick = onVerify) { Text("Verifikasi email") }
            TextButton(onClick = onTripSearch) { Text(stringResource(Res.string.explore_trip_door)) }
        }
    }
}

@Preview @Composable private fun HomeEmptyPreview() = IrisPreview { InspirationHomeContent(InspirationHomeUiState()) }
@Preview @Composable private fun HomeLimitPreview() = IrisPreview { InspirationHomeContent(InspirationHomeUiState(interests = Interest.entries.take(5), limitReached = true)) }
@Preview @Composable private fun HomeRateLimitedPreview() = IrisPreview { InspirationHomeContent(InspirationHomeUiState(startState = InspirationStartState.RateLimited(5))) }
