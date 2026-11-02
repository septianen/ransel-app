package app.ransel.feature.recap

import app.ransel.content.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import app.ransel.ui.components.AdaptiveSheet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import app.ransel.ui.adaptive.rememberIsExpanded
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
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
 * The recap as a sheet, opened from "Bagikan" on the Home card, the trip header and the notification: the ticket
 * (boards RekapTrip, WebRekapTrip) and the action that shares a separate picture, [renderRecapStory] (web: downloads it).
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
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.bodyMedium.fontFamily
    val story = state.storyText()
    var shareFailed by remember { mutableStateOf(false) }
    val share: () -> Unit = {
        shareFailed = false
        scope.launch {
            runCatching {
                val mascot = Res.readBytes("drawable/splash_peeking.webp").decodeToImageBitmap()
                val png = renderRecapStory(requireNotNull(story), mascot, measurer, family).toPngBytes()
                check(png.isNotEmpty())
                files.share("rekap-${state.trip?.title.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "trip" }}.png", png)
            }.onFailure { shareFailed = true }
        }
    }
    AdaptiveSheet(onDismiss = onDismiss, maxWidth = 760.dp) {
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
                RecapCard(trip.title, start, end, trip.baseCurrency, recap, state.members, state.openTransfers)
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

/** What the picture says; null until the trip and its recap are there. */
@Composable
private fun RecapUiState.storyText(): RecapStoryText? {
    val trip = trip ?: return null
    val recap = recap ?: return null
    val dates = rememberDateFormatter()
    val range = dates.range(trip.startDate ?: return null, trip.endDate ?: return null)
    return RecapStoryText(
        finished = stringResource(Res.string.recap_finished),
        title = trip.title,
        dates = range,
        places = recap.placesVisited.toString(),
        placesLabel = stringResource(Res.string.home_recap_places),
        days = recap.days.toString(),
        daysLabel = stringResource(Res.string.recap_days),
        perPerson = if (recap.totalMinor > 0) compactMoney(recap.perPersonMinor, trip.baseCurrency) else null,
        perPersonLabel = stringResource(Res.string.home_recap_per_person),
        // with no expense nobody shared a cost, so the members say how many went
        people = (if (recap.totalMinor > 0) recap.people else members.size.takeIf { it > 0 } ?: recap.people).toString(),
        peopleLabel = stringResource(Res.string.recap_people),
        favourites = recap.favourites.joinToString(" · "),
        tagline = stringResource(Res.string.splash_tagline),
    )
}

@Composable
private fun compactMoney(minor: Long, currency: String): String = formatMoneyCompact(
    minor,
    currency,
    stringResource(Res.string.money_unit_thousand),
    stringResource(Res.string.money_unit_million),
    stringResource(Res.string.money_unit_billion),
)

/** Boards RekapTrip and WebRekapTrip: the mascot peeks over a ticket on the green panel. Only shown in the app. */
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
    val wide = rememberIsExpanded()
    Box(modifier.fillMaxWidth().clip(IrisShape.xl).background(scheme.primary)) {
        Image(
            painterResource(Res.drawable.splash_peeking),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(102.dp, 133.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 112.dp, bottom = 16.dp, start = 14.dp, end = 14.dp)) {
            Ticket(title, start, end, currency, recap, members, openTransfers, wide)
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = scheme.onPrimary
                val pocket = scheme.primary
                Canvas(Modifier.size(18.dp, 24.dp)) { drawRanselMark(tint, pocket, size.width) }
                Text("RANSEL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = tint)
            }
        }
    }
}

@Composable
private fun Ticket(
    title: String,
    start: LocalDate,
    end: LocalDate,
    currency: String,
    recap: TripRecap,
    members: List<TripMemberDto>,
    openTransfers: Int?,
    wide: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    val avatars = @Composable {
        Row {
            members.take(8).forEachIndexed { i, member ->
                MemberAvatar(member.displayName, member.memberId, isGuest = member.userId == null, size = 32.dp, modifier = Modifier.offset(x = (-8 * i).dp))
            }
        }
    }
    // a background, not a clip: the notches on the tear line reach past the edges
    Column(Modifier.fillMaxWidth().background(scheme.surface, IrisShape.lg).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(stringResource(Res.string.recap_finished), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (wide && members.isNotEmpty()) avatars()
        }
        Text(dates.range(start, end), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (!wide && members.isNotEmpty()) Box(Modifier.padding(top = 10.dp)) { avatars() }

        // the tear line; the notches sit on the ticket's edges, in the panel's colour
        val line = scheme.outlineVariant
        val notch = scheme.primary
        Canvas(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(2.dp)) {
            val y = size.height / 2
            val r = 12.dp.toPx()
            val edge = 20.dp.toPx()
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
            drawCircle(notch, r, Offset(-edge, y))
            drawCircle(notch, r, Offset(size.width + edge, y))
        }

        val stats = buildList {
            add(recap.placesVisited.toString() to stringResource(Res.string.home_recap_places))
            add(recap.days.toString() to stringResource(Res.string.recap_days))
            // a trip without expenses has no money to show
            if (recap.totalMinor > 0) {
                add(compactMoney(recap.perPersonMinor, currency) to stringResource(Res.string.home_recap_per_person))
                add(compactMoney(recap.totalMinor, currency) to stringResource(Res.string.recap_total))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            stats.chunked(if (wide) 4 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (value, label) -> Stat(value, label, Modifier.weight(1f)) }
                    repeat((if (wide) 4 else 2) - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (recap.favourites.isNotEmpty()) {
            Text(recap.favourites.joinToString(" · "), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            openTransfers == 0 -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Symbols.checkCircle, size = 22.dp, filled = true, tint = scheme.primary)
                Text(stringResource(Res.string.recap_settled), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            }
            openTransfers != null -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Symbols.schedule, size = 22.dp, tint = scheme.primary)
                Text(stringResource(Res.string.recap_unsettled, openTransfers), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = scheme.primary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
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
