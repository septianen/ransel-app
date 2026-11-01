package app.ransel.feature.recap

import app.ransel.content.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import app.ransel.ui.components.AdaptiveSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.platform.DeviceFiles
import app.ransel.platform.toPngBytes
import app.ransel.shared.domain.TripRecap
import app.ransel.shared.dto.TripMemberDto
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.format.formatMoneyCompact
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/**
 * The recap as a sheet, opened from "Bagikan" on the Home card, the trip header and the notification: the card
 * ("Bali 4 hari, beres!") and the action that shares it as an image (web: downloads it).
 */
@Composable
fun RecapSheet(
    tripId: String,
    onDismiss: () -> Unit,
    viewModel: RecapViewModel = koinViewModel(key = "recap-$tripId") { parametersOf(tripId) },
    files: DeviceFiles = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var shareFailed by remember { mutableStateOf(false) }
    val share: () -> Unit = {
        shareFailed = false
        scope.launch {
            runCatching {
                val png = layer.toImageBitmap().toPngBytes()
                check(png.isNotEmpty())
                files.share("rekap-${state.trip?.title.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "trip" }}.png", png)
            }.onFailure { shareFailed = true }
        }
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.recap_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close))
            }
        }
        val trip = state.trip
        val recap = state.recap
        val start = trip?.startDate
        val end = trip?.endDate
        if (trip != null && recap != null && start != null && end != null) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                // the card is drawn into a layer as well, which becomes the shared picture
                Box(
                    Modifier.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
                ) {
                    RecapCard(trip.title, start, end, trip.baseCurrency, recap, state.members, state.openTransfers)
                }
            }
            if (shareFailed) {
                Text(stringResource(Res.string.recap_share_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(onClick = share, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.share, tint = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(if (files.keepsFiles) Res.string.recap_share else Res.string.recap_download), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** The card itself: brand and dates, headline, avatars, four numbers, favourites, settled. */
@Composable
fun RecapCard(
    title: String,
    start: LocalDate,
    end: LocalDate,
    currency: String,
    recap: TripRecap,
    members: List<TripMemberDto>,
    openTransfers: Int?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    val k = stringResource(Res.string.money_unit_thousand)
    val m = stringResource(Res.string.money_unit_million)
    val b = stringResource(Res.string.money_unit_billion)
    Column(
        modifier.fillMaxWidth().clip(IrisShape.xl).background(scheme.primaryContainer).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.recap_brand, dates.range(start, end)), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
        Text(stringResource(Res.string.recap_headline, title), style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimaryContainer)
        if (members.isNotEmpty()) {
            Row {
                members.take(8).forEachIndexed { i, member ->
                    MemberAvatar(member.displayName, member.memberId, isGuest = member.userId == null, size = 32.dp, modifier = Modifier.offset(x = (-8 * i).dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(recap.placesVisited.toString(), stringResource(Res.string.recap_places), Modifier.weight(1f))
            Stat(recap.days.toString(), stringResource(Res.string.recap_days), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(formatMoneyCompact(recap.totalMinor, currency, k, m, b), stringResource(Res.string.recap_total), Modifier.weight(1f))
            Stat(formatMoneyCompact(recap.perPersonMinor, currency, k, m, b), stringResource(Res.string.recap_per_person), Modifier.weight(1f))
        }
        if (recap.favourites.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.recap_favourites), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
                Text(recap.favourites.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
            }
        }
        when {
            openTransfers == 0 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Symbols.checkCircle, tint = scheme.primary)
                Text(stringResource(Res.string.recap_settled), style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
            }
            openTransfers != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Symbols.schedule, tint = scheme.onPrimaryContainer)
                Text(stringResource(Res.string.recap_unsettled, openTransfers), style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.clip(IrisShape.lg).background(scheme.surface).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/**
 * Design "BerandaRekap": the card on Home after a trip ended. [onOpen] goes to the trip, whose open transfers are the
 * action left to finish; [onShare] opens [RecapSheet].
 */
@Composable
fun RecapHomeCard(title: String, currency: String, recap: TripRecap, openTransfers: Int?, onOpen: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val k = stringResource(Res.string.money_unit_thousand)
    val m = stringResource(Res.string.money_unit_million)
    val b = stringResource(Res.string.money_unit_billion)
    Column(
        modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.primaryContainer).clickable(role = Role.Button, onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.recap_headline, title), style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniStat(recap.placesVisited.toString(), stringResource(Res.string.home_recap_places))
            MiniStat(formatMoneyCompact(recap.totalMinor, currency, k, m, b), stringResource(Res.string.home_recap_total))
            MiniStat(formatMoneyCompact(recap.perPersonMinor, currency, k, m, b), stringResource(Res.string.home_recap_per_person))
        }
        val unsettled = openTransfers != null && openTransfers > 0
        if (unsettled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Symbol(Symbols.schedule, size = 16.dp, tint = scheme.onPrimaryContainer)
                Text(stringResource(Res.string.recap_unsettled, openTransfers ?: 0), style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onShare), contentAlignment = Alignment.Center) {
                Symbol(Symbols.share, tint = scheme.primary, contentDescription = stringResource(Res.string.recap_share))
            }
            Box(Modifier.weight(1f))
            Text(
                stringResource(if (unsettled) Res.string.home_recap_settle else Res.string.home_recap_trip),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
            Symbol(Symbols.chevronRight, tint = scheme.primary)
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private val previewRecap = TripRecap(11, 4, 3_700_000, 740_000, 5, listOf("Air Terjun Tegenungan", "Seafood Jimbaran", "Tari Kecak"))

@Preview
@Composable
private fun RecapCardPreview() = IrisPreview {
    RecapCard("Bali 4 hari", LocalDate(2026, 11, 20), LocalDate(2026, 11, 23), "IDR", previewRecap, emptyList(), openTransfers = 0)
}

@Preview
@Composable
private fun RecapHomeCardPreview() = IrisPreview {
    RecapHomeCard("Bali 4 hari", "IDR", previewRecap, openTransfers = 1, onOpen = {}, onShare = {})
}
