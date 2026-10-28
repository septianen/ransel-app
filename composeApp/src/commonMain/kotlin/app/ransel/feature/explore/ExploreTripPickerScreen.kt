package app.ransel.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.LoadingButton
import app.ransel.data.explore.ExploreSession
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.daysUntilStart
import app.ransel.ui.components.TripCover
import app.ransel.feature.credits.rememberCredits
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import app.ransel.util.today
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*

/**
 * Explore tab, step 1 (design `PilihTripJelajah`, `JelajahLanjut`, `JelajahTamu`): pick the trip to search for,
 * or start a new one. Guests see the sign-in state; a signed-in user with no eligible trip sees the empty state,
 * with the same "Trip baru" floating button.
 */
@Composable
fun ExploreTripPickerScreen(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onPickTrip: (String) -> Unit,
    onNewTrip: () -> Unit,
    onOpenSwipe: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreTripPickerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(Res.string.explore_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            !state.signedIn -> GuestState(onSignIn, onSignUp)
            !state.emailVerified -> VerifyEmailGate(state.email, state.gate, viewModel::resendVerification, viewModel::checkVerified)
            else -> TripPicker(state, onPickTrip, onNewTrip, onOpenSwipe)
        }
    }
}

@Composable
private fun TripPicker(state: ExploreTripPickerState, onPickTrip: (String) -> Unit, onNewTrip: () -> Unit, onOpenSwipe: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    val credits by rememberCredits()
    Box(Modifier.fillMaxSize()) {
    if (state.noEligibleTrip) {
        // Not designed yet: the shared empty state, with room for the floating button below.
        FullScreenState(
            title = stringResource(Res.string.explore_empty_title),
            body = stringResource(Res.string.explore_empty_body),
            illustration = painterResource(Res.drawable.ic_data_not_found),
            modifier = Modifier.padding(bottom = 80.dp),
        )
    } else Column(
        // 96 dp at the bottom: the last trip stays reachable above the floating button
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(IrisShape.xl).background(scheme.tertiaryContainer).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).clip(IrisShape.md).background(IrisTheme.accents.purple), contentAlignment = Alignment.Center) {
                Symbol(Symbols.autoAwesome, filled = true, tint = IrisTheme.accents.onAccent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.explore_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onTertiaryContainer,
                )
                Text(stringResource(Res.string.explore_picker_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
            }
        }
        if (state.trips.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.explore_picker_your_trips), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    credits?.let {
                        Text(
                            stringResource(Res.string.explore_credits_left, it.available),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                state.trips.forEach { row ->
                    val trip = row.trip
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(IrisShape.lg)
                            .background(scheme.surface)
                            .border(1.dp, scheme.outlineVariant, IrisShape.lg),
                    ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPickTrip(trip.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TripCover(trip.id, Modifier.size(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dates.tripRange(trip.startDate, trip.endDate), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val days = trip.daysUntilStart(today())
                                Text(
                                    when {
                                        row.phase == TripPhase.ONGOING -> stringResource(Res.string.trips_ongoing)
                                        days == null -> app.ransel.feature.trips.countdownLabel(null)
                                        else -> pluralStringResource(Res.plurals.explore_picker_in_days, days, days)
                                    },
                                    Modifier.clip(IrisShape.xs).background(scheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                val context = when {
                                    (row.summary?.unscheduledCount ?: 0) > 0 -> stringResource(Res.string.explore_picker_unscheduled, row.summary!!.unscheduledCount)
                                    row.summary != null && row.summary.activityCount == 0 -> stringResource(Res.string.explore_picker_empty_itinerary)
                                    else -> null
                                }
                                context?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                        Symbol(Symbols.chevronRight, tint = scheme.onSurfaceVariant)
                    }
                    row.resume?.let { ResumeStrip(it, onOpen = { onOpenSwipe(trip.id) }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Symbols.info, size = 16.dp, tint = scheme.onSurfaceVariant)
                    Text(stringResource(Res.string.explore_picker_hidden_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
    // "Trip baru" as a floating button, so it is always in reach however long the list is (design `JelajahLanjut`).
    IconFab(
        Symbols.autoAwesome,
        label = stringResource(Res.string.explore_picker_new_trip),
        onClick = onNewTrip,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    )
    }
}

@Composable
private fun IntroBlock(symbol: String, title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, size = 28.dp, filled = true, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuestState(onSignIn: () -> Unit, onSignUp: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IntroBlock(Symbols.lockPerson, stringResource(Res.string.explore_guest_title), stringResource(Res.string.explore_guest_body))
        listOf(
            Triple(Symbols.autoAwesome, Res.string.explore_guest_point1_title, Res.string.explore_guest_point1_body),
            Triple(Symbols.eventNote, Res.string.explore_guest_point2_title, Res.string.explore_guest_point2_body),
            Triple(Symbols.cloudSync, Res.string.explore_guest_point3_title, Res.string.explore_guest_point3_body),
        ).forEach { (symbol, title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Symbol(symbol, filled = true)
                }
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.explore_guest_sign_in), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.explore_guest_sign_up), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(Res.string.explore_guest_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Design `JelajahBelumVerifikasi`: signed up, email not verified yet. Explore and the signup credits wait for it. */
@Composable
private fun VerifyEmailGate(email: String, gate: VerifyGateState, onResend: () -> Unit, onCheck: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(IrisShape.xl).background(scheme.tertiaryContainer).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(44.dp).clip(IrisShape.md).background(IrisTheme.accents.purple), contentAlignment = Alignment.Center) {
                Symbol(Symbols.markEmailRead, filled = true, tint = IrisTheme.accents.onAccent)
            }
            Text(
                stringResource(Res.string.explore_verify_title),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = scheme.onTertiaryContainer,
            )
            Text(stringResource(Res.string.explore_verify_body, email), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
        }
        Column(
            Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceVariant).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(Symbols.inbox to Res.string.check_email_tip_spam, Symbols.schedule to Res.string.explore_verify_tip_expiry).forEach { (symbol, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symbol(symbol, size = 18.dp, tint = scheme.onSurfaceVariant)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
        gate.message?.let { message ->
            val (text, color) = when (message) {
                VerifyGateMessage.SENT -> stringResource(Res.string.explore_verify_sent) to scheme.primary
                VerifyGateMessage.NOT_YET -> stringResource(Res.string.explore_verify_not_yet) to scheme.onSurfaceVariant
                VerifyGateMessage.CONNECTION -> stringResource(Res.string.auth_error_connection) to scheme.error
                VerifyGateMessage.RATE_LIMITED -> stringResource(Res.string.auth_error_rate_limited) to scheme.error
                VerifyGateMessage.GENERIC -> stringResource(Res.string.auth_error_generic) to scheme.error
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadingButton(onClick = onResend, loading = gate.resending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.check_email_resend), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onCheck, enabled = !gate.checking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.refresh, size = 20.dp)
                Text(stringResource(Res.string.explore_verify_check), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Symbol(Symbols.info, size = 16.dp, tint = scheme.onSurfaceVariant)
                Text(stringResource(Res.string.explore_verify_note), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Design `JelajahLanjut`: inside the trip row, "Belum selesai · 3 dari 12 dilihat · 2 disimpan" and "Lanjutkan". */
@Composable
private fun ResumeStrip(session: ExploreSession, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.explore_resume_label), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
            Text(
                stringResource(Res.string.explore_resume_progress, session.position, session.cards.size, session.saved),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer,
            )
        }
        Text(stringResource(Res.string.explore_resume_cta), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
    }
}
