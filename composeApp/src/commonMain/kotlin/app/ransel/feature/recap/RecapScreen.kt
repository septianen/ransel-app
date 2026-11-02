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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import app.ransel.ui.theme.IrisTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import app.ransel.ui.RefreshOnReturn
import org.jetbrains.compose.resources.StringResource
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
import androidx.compose.material3.ButtonDefaults
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
 * The recap, full screen (from the Home card, the trip header and the notification): the ticket on the green panel.
 * "Bagikan rekap" opens [RecapTemplateSheet].
 */
@Composable
fun RecapScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: RecapViewModel = koinViewModel(key = "recap-$tripId") { parametersOf(tripId) },
    files: DeviceFiles = koinInject(),
) {
    RefreshOnReturn(viewModel::refresh)
    val state by viewModel.state.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    // All one green, like the ticket's picture: no white bars above and below it.
    Column(Modifier.fillMaxSize().background(scheme.primary).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, tint = scheme.onPrimary, contentDescription = stringResource(Res.string.action_back))
            }
            Text(stringResource(Res.string.recap_title), style = MaterialTheme.typography.titleLarge, color = scheme.onPrimary, modifier = Modifier.padding(start = 4.dp))
        }
        val trip = state.trip
        val recap = state.recap
        val start = trip?.startDate
        val end = trip?.endDate
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (trip != null && recap != null && start != null && end != null) {
                RecapCard(trip.title, start, end, trip.baseCurrency, recap, state.members, state.openTransfers, Modifier.verticalScroll(rememberScrollState()))
            }
        }
        if (trip != null && recap != null) {
            Button(
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
            ) {
                Symbol(Symbols.share, tint = scheme.primary)
                Text(stringResource(if (files.keepsFiles) Res.string.recap_share else Res.string.recap_download), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    val story = state.storyText()
    if (picking && story != null) {
        RecapTemplateSheet(story, files, onDismiss = { picking = false })
    }
}

/**
 * Pick one of the three pictures. Then the system's share sheet asks where it goes; the web downloads it. The previews
 * are the real pictures, so what is picked is what is sent.
 */
@Composable
fun RecapTemplateSheet(story: RecapStoryText, files: DeviceFiles, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.bodyMedium.fontFamily
    val scheme = MaterialTheme.colorScheme
    var chosen by rememberSaveable { mutableStateOf(RecapTemplate.TICKET) }
    var pictures by remember { mutableStateOf(emptyMap<RecapTemplate, ImageBitmap>()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(story) {
        runCatching {
            RecapTemplate.entries.forEach { template ->
                val mascot = Res.readBytes(template.resource).decodeToImageBitmap()
                pictures = pictures + (template to renderRecapStory(template, story, mascot, measurer, family))
            }
        }.onFailure { failed = true }
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.recap_template_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecapTemplate.entries.forEach { template ->
                val selected = template == chosen
                Column(
                    Modifier.weight(1f).clip(IrisShape.md).selectable(selected, role = Role.RadioButton) { chosen = template },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val picture = pictures[template]
                    val frame = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(IrisShape.md)
                        // magenta: green would not stand out against the green pictures
                        .border(if (selected) 3.dp else 1.dp, if (selected) IrisTheme.accents.magenta else scheme.outlineVariant, IrisShape.md)
                    if (picture != null) {
                        Image(picture, contentDescription = null, modifier = frame, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
                    } else {
                        Box(frame.background(scheme.surfaceContainerHigh))
                    }
                    Text(
                        stringResource(template.label()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (failed) {
            Text(stringResource(Res.string.recap_share_failed), style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        val picture = pictures[chosen]
        Button(
            onClick = {
                val bitmap = picture ?: return@Button
                failed = false
                scope.launch {
                    runCatching {
                        val png = bitmap.toPngBytes()
                        check(png.isNotEmpty())
                        files.share("rekap-${story.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "trip" }}.png", png)
                    }.onSuccess { onDismiss() }.onFailure { failed = true }
                }
            },
            enabled = picture != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Symbol(Symbols.share, tint = if (picture != null) scheme.onPrimary else scheme.onSurfaceVariant)
            Text(stringResource(if (files.keepsFiles) Res.string.recap_template_share else Res.string.recap_download), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun RecapTemplate.label(): StringResource = when (this) {
    RecapTemplate.TICKET -> Res.string.recap_template_ticket
    RecapTemplate.DONE -> Res.string.recap_template_done
    RecapTemplate.BIG_NUMBERS -> Res.string.recap_template_numbers
}

/** What the picture says; null until the trip and its recap are there. */
@Composable
fun RecapUiState.storyText(): RecapStoryText? {
    val trip = trip ?: return null
    val recap = recap ?: return null
    val dates = rememberDateFormatter()
    val range = dates.range(trip.startDate ?: return null, trip.endDate ?: return null)
    return RecapStoryText(
        finished = stringResource(Res.string.recap_finished),
        done = stringResource(Res.string.recap_done),
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

/** The look of the shared ticket, for the screen: the mascot peeks over the ticket on the green panel. Only shown in the app. */
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
    Box(modifier.widthIn(max = if (wide) 712.dp else 480.dp).fillMaxWidth()) {
        Image(
            painterResource(Res.drawable.ic_binocular),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(126.dp, 164.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 136.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)) {
            Ticket(title, start, end, currency, recap, members, openTransfers, wide)
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = scheme.onPrimary
                val pocket = scheme.primary
                Canvas(Modifier.size(18.dp, 24.dp)) { drawRanselMark(tint, pocket, size.width) }
                Text("RANSEL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = tint)
            }
            Text(
                stringResource(Res.string.splash_tagline),
                Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
                textAlign = TextAlign.Center,
            )
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
 * Board BerandaRekap, "Hero hijau": the card on Home after a trip ended. A tap opens the recap ([onOpen]); while
 * transfers are open the status line goes to the expenses ([onSettle]); [onShare] picks a picture from here. Accent
 * green with white text in both themes. Fixed heights keep it at 148 dp with 48 dp touch targets; a larger font scale
 * grows it. Text beside the mascot keeps clear of it, so white small print never sits on the drawing.
 */
@Composable
fun RecapHomeCard(
    title: String,
    currency: String,
    recap: TripRecap,
    openTransfers: Int?,
    shareDescription: String,
    onOpen: () -> Unit,
    onSettle: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val green = IrisTheme.accents.green
    val white = IrisTheme.accents.onAccent
    Box(modifier.fillMaxWidth().heightIn(min = 148.dp).clip(IrisShape.lg).background(green).clickable(role = Role.Button, onClick = onOpen)) {
        // cut off by the card's bottom edge, above the legs
        Image(
            painterResource(Res.drawable.ic_binocular),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 40.dp).requiredSize(81.dp, 106.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text(
                stringResource(Res.string.recap_headline, title),
                Modifier.padding(end = 48.dp), // 64 from the card's edge, clear of the share button
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = white,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 8.dp, end = MASCOT_CLEARANCE), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeroStat(recap.placesVisited.toString(), stringResource(Res.string.home_recap_places))
                if (recap.totalMinor > 0) {
                    HeroStat(compactMoney(recap.perPersonMinor, currency), stringResource(Res.string.home_recap_per_person), Modifier.weight(1f, fill = false))
                }
            }
            HeroStatus(openTransfers, onSettle)
        }
        // 48 dp to tap, a 40 dp circle to see
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(white), contentAlignment = Alignment.Center) {
                Symbol(Symbols.share, size = 20.dp, tint = green, contentDescription = shareDescription)
            }
        }
    }
}

/** Room the text under the title leaves for the mascot (81 wide + 40 margin − the card's 16 padding, plus a gap). */
private val MASCOT_CLEARANCE = 113.dp

/**
 * Open transfers lead to the expenses, all settled is only said; not known (offline, guest) shows nothing but keeps the
 * room. 48 dp tall to tap, the text sits at the bottom and may take two lines on a narrow phone.
 */
@Composable
private fun HeroStatus(openTransfers: Int?, onSettle: () -> Unit) {
    val white = IrisTheme.accents.onAccent
    val row = Modifier.fillMaxWidth().padding(end = MASCOT_CLEARANCE).heightIn(min = 48.dp)
    when {
        openTransfers == null -> Spacer(row)
        else -> {
            val open = openTransfers > 0
            Row(
                if (open) row.clickable(role = Role.Button, onClick = onSettle) else row,
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Symbol(if (open) Symbols.schedule else Symbols.checkCircle, size = 16.dp, tint = white, filled = !open)
                    Text(
                        if (open) stringResource(Res.string.recap_unsettled, openTransfers) else stringResource(Res.string.recap_settled),
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = white,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (open) Symbol(Symbols.chevronRight, size = 16.dp, tint = white)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    val white = IrisTheme.accents.onAccent
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun RecapHomeCardOpenPreview() = IrisPreview {
    RecapHomeCard("Bali 4 hari", "IDR", previewRecap, openTransfers = 1, shareDescription = "Bagikan rekap", onOpen = {}, onSettle = {}, onShare = {})
}

@Preview
@Composable
private fun RecapHomeCardSettledPreview() = IrisPreview {
    RecapHomeCard("Bali 4 hari", "IDR", previewRecap, openTransfers = 0, shareDescription = "Bagikan rekap", onOpen = {}, onSettle = {}, onShare = {})
}
