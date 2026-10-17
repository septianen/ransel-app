package app.ransel.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.navigation.PlaceDetailRoute
import app.ransel.shared.domain.googleMapsPlaceUrl
import app.ransel.shared.dto.OpeningHoursLine
import app.ransel.shared.model.Category
import app.ransel.shared.model.PriceLevel
import app.ransel.ui.category.itineraryLabel
import app.ransel.ui.category.symbol
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.format.formatDurationApprox
import app.ransel.ui.format.formatMoney
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/** Width from which the detail uses two columns, like the web design (`WebDetailTempat`). */
private val WIDE = 900.dp

/**
 * Full-screen place detail (designs `DetailTempat`, `DetailTempatItinerary`, `WebDetailTempat`). [onSkip] and [onSave]
 * are set when it was opened from a swipe card (the bar with the same two decisions as the swipe screen) and null when
 * it was opened from the itinerary.
 */
@Composable
fun PlaceDetailScreen(
    route: PlaceDetailRoute,
    onBack: () -> Unit,
    onSkip: (() -> Unit)?,
    onSave: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: PlaceDetailViewModel = koinViewModel(key = "place-detail-${route.placeId}-${route.fromItinerary}") { parametersOf(route) },
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    PlaceDetailContent(
        state = state,
        fallbackName = route.name,
        onBack = onBack,
        onSkip = onSkip,
        onSave = onSave,
        onRetry = viewModel::retry,
        onOpenMaps = { info -> uriHandler.openUri(googleMapsPlaceUrl(info.name, info.placeId)) },
        modifier = modifier,
    )
}

@Composable
fun PlaceDetailContent(
    state: PlaceDetailUiState,
    fallbackName: String?,
    onBack: () -> Unit,
    onSkip: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onRetry: () -> Unit,
    onOpenMaps: (PlaceDetailInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize().background(scheme.surface)) {
        val wide = maxWidth >= WIDE
        if (wide) {
            WideLayout(state, fallbackName, onBack, onSkip, onSave, onRetry, onOpenMaps)
        } else {
            NarrowLayout(state, fallbackName, onBack, onSkip, onSave, onRetry, onOpenMaps)
        }
    }
}

@Composable
private fun NarrowLayout(
    state: PlaceDetailUiState,
    fallbackName: String?,
    onBack: () -> Unit,
    onSkip: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onRetry: () -> Unit,
    onOpenMaps: (PlaceDetailInfo) -> Unit,
) {
    val info = state.info
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Hero(info, onBack = onBack, height = 300.dp, rounded = false)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TitleBlock(info, fallbackName)
                Sections(state, onRetry, onOpenMaps, includeSide = true)
            }
        }
        if (onSkip != null && onSave != null) ActionBar(onSkip, onSave)
    }
}

@Composable
private fun WideLayout(
    state: PlaceDetailUiState,
    fallbackName: String?,
    onBack: () -> Unit,
    onSkip: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onRetry: () -> Unit,
    onOpenMaps: (PlaceDetailInfo) -> Unit,
) {
    val info = state.info
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Symbols.arrowBack, stringResource(Res.string.action_back), onBack, onSurface = true)
            Text(
                stringResource(Res.string.place_detail_title),
                Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Row(Modifier.weight(1f).padding(horizontal = 32.dp).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Hero(info, onBack = null, height = 300.dp, rounded = true)
                Column(
                    Modifier.fillMaxWidth().clip(IrisShape.xl).background(scheme.surfaceContainerLow).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    TitleBlock(info, fallbackName)
                    Sections(state, onRetry, onOpenMaps, includeSide = false)
                }
            }
            Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (onSkip != null && onSave != null) {
                    SideCard {
                        SaveButton(onSave, Modifier.fillMaxWidth())
                        SkipButton(onSkip, Modifier.fillMaxWidth())
                    }
                }
                if (info != null) {
                    if (info.openingHours.isNotEmpty()) SideCard { HoursBlock(info) }
                    SideCard { LocationBlock(info, onOpenMaps) }
                }
            }
        }
    }
}

@Composable
private fun SideCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.xl).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** The photo with the back button over it (narrow) and the category chip and Google credit on it. */
@Composable
private fun Hero(info: PlaceDetailInfo?, onBack: (() -> Unit)?, height: androidx.compose.ui.unit.Dp, rounded: Boolean) {
    val category = info?.category
    val shape = if (rounded) IrisShape.xl else androidx.compose.ui.graphics.RectangleShape
    Box(
        Modifier.fillMaxWidth().height(height).clip(shape).background(category?.tileColor() ?: MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (category != null) {
            Symbol(category.symbol, size = 96.dp, filled = true, tint = IrisTheme.accents.onAccent.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.Center))
        }
        if (info != null) {
            val request = rememberPlacePhotoRequest(info.photoUrl, info.placeId)
            if (request != null) {
                AsyncImage(model = request, contentDescription = info.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Text(
                    info.photoAttribution?.let { stringResource(Res.string.swipe_photo_credit, it) } ?: stringResource(Res.string.swipe_photo_credit_google),
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(IrisShape.xs)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onBack != null) {
            Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
            RoundIcon(
                Symbols.arrowBack, stringResource(Res.string.action_back), onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            )
        }
        if (category != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Symbol(category.symbol, size = 16.dp, filled = true, tint = category.tileColor())
                Text(stringResource(category.itineraryLabel), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RoundIcon(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSurface: Boolean = false,
) {
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .background(if (onSurface) Color.Transparent else Color.Black.copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, contentDescription = description, tint = if (onSurface) MaterialTheme.colorScheme.onSurface else Color.White)
    }
}

@Composable
private fun TitleBlock(info: PlaceDetailInfo?, fallbackName: String?) {
    val scheme = MaterialTheme.colorScheme
    val name = info?.name ?: fallbackName
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (name != null) {
            Text(name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
        } else {
            Skeleton(Modifier.width(220.dp).height(28.dp))
        }
        if (info != null) {
            val meta = buildList {
                info.priceLevel?.let { level ->
                    val budget = when { level <= 1 -> PriceLevel.LOW; level == 2 -> PriceLevel.MEDIUM; else -> PriceLevel.HIGH }
                    add(Symbols.payments to costLabel(stringResource(budget.label), info))
                } ?: info.estimatedCostMinor?.let { add(Symbols.payments to costLabel(null, info)) }
                info.durationMin?.let { add(Symbols.schedule to formatDurationApprox(it)) }
                info.bestTime?.let { add(Symbols.schedule to stringResource(Res.string.place_detail_best_time, it)) }
            }
            // wraps on narrow windows: two short rows are better than a truncated one
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                meta.forEach { (symbol, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Symbol(symbol, size = 18.dp, tint = scheme.onSurfaceVariant)
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            info.reason?.let { reason ->
                Row(
                    Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.tertiaryContainer).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Symbols.autoAwesome, size = 18.dp, filled = true, tint = scheme.onTertiaryContainer)
                    Text(reason, style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
                }
            }
        }
    }
}

/** "Hemat · ± Rp25.000 per orang", or just the amount when there is no price level (itinerary entry). */
@Composable
private fun costLabel(level: String?, info: PlaceDetailInfo): String {
    val cost = info.estimatedCostMinor?.let { minor ->
        info.costCurrency?.let { currency -> stringResource(Res.string.place_detail_cost, formatMoney(minor, currency)) }
    }
    return listOfNotNull(level, cost).joinToString(" · ")
}

/**
 * The text sections. [includeSide] puts hours and location in the same column (narrow); on wide windows they sit in
 * the right column instead.
 */
@Composable
private fun Sections(
    state: PlaceDetailUiState,
    onRetry: () -> Unit,
    onOpenMaps: (PlaceDetailInfo) -> Unit,
    includeSide: Boolean,
) {
    val info = state.info
    when {
        info != null -> {
            info.about?.let { about ->
                Section(stringResource(Res.string.place_detail_about)) {
                    Text(about, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (info.highlights.isNotEmpty()) {
                Section(stringResource(Res.string.place_detail_highlights)) {
                    info.highlights.forEach { tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Symbol(Symbols.info, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tip, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (includeSide) {
                HoursBlock(info)
                LocationBlock(info, onOpenMaps)
            }
        }
        state.failure != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.place_detail_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LoadingButton(onClick = onRetry, loading = false) { Text(stringResource(Res.string.state_retry)) }
        }
        else -> LoadingSkeleton()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

/** Opening hours are an AI estimate, not Google data: labelled so, with the Maps button next to the location to check. */
@Composable
private fun HoursBlock(info: PlaceDetailInfo) {
    if (info.openingHours.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.place_detail_hours), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Symbol(Symbols.autoAwesome, size = 14.dp, filled = true, tint = scheme.onTertiaryContainer)
            Text(
                stringResource(Res.string.place_detail_hours_ai),
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onTertiaryContainer,
            )
        }
        Column(Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            info.openingHours.forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(line.label, style = MaterialTheme.typography.bodyMedium)
                    Text(line.hours, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(stringResource(Res.string.place_detail_hours_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun LocationBlock(info: PlaceDetailInfo, onOpenMaps: (PlaceDetailInfo) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.place_detail_location), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        info.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .border(1.dp, scheme.outline, CircleShape)
                .clickable(role = Role.Button) { onOpenMaps(info) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.map, size = 18.dp, tint = scheme.primary)
            Text(stringResource(Res.string.place_detail_open_maps), style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Skeleton(modifier: Modifier) {
    Box(modifier.clip(IrisShape.xs).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/** Only shown when the itinerary reopens a place whose saved text expired and is being rebuilt. */
@Composable
private fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Symbol(Symbols.autoAwesome, size = 14.dp, filled = true, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(stringResource(Res.string.place_detail_loading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Skeleton(Modifier.width(140.dp).height(16.dp))
        Skeleton(Modifier.fillMaxWidth().height(14.dp))
        Skeleton(Modifier.fillMaxWidth().height(14.dp))
        Skeleton(Modifier.width(220.dp).height(14.dp))
        HorizontalDivider()
        Skeleton(Modifier.fillMaxWidth().height(96.dp))
    }
}

@Composable
private fun SaveButton(onSave: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 56.dp).clip(CircleShape).background(IrisTheme.accents.magenta).clickable(role = Role.Button, onClick = onSave).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Symbol(Symbols.favorite, size = 24.dp, filled = true, tint = IrisTheme.accents.onAccent)
        Text(stringResource(Res.string.place_detail_save), style = MaterialTheme.typography.titleMedium, color = IrisTheme.accents.onAccent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 56.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Symbol(Symbols.close, size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.place_detail_skip), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** The same two decisions as the swipe screen, so the user can decide without going back. */
@Composable
private fun ActionBar(onSkip: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(onSkip, Modifier.width(112.dp))
            SaveButton(onSave, Modifier.weight(1f))
        }
    }
}

// ── previews ────────────────────────────────────────────────────────────────

private val PreviewInfo = PlaceDetailInfo(
    placeId = "p1",
    name = "Air Terjun Tegenungan",
    category = Category.ATTRACTION,
    reason = "Cocok dengan minat alam dan gaya santaimu; bisa digabung dengan kunjungan ke Ubud.",
    priceLevel = 1,
    durationMin = 90,
    address = "Jl. Air Terjun Tegenungan, Kemenuh, Sukawati, Gianyar, Bali",
    about = "Air terjun setinggi sekitar 15 meter di dekat Ubud dengan kolam alami untuk berenang.",
    highlights = listOf("Paling sepi di pagi hari sebelum jam 9.", "Bawa baju ganti dan sandal anti selip."),
    openingHours = listOf(OpeningHoursLine("Setiap hari", "08.00 – 18.00")),
    estimatedCostMinor = 25_000,
    costCurrency = "IDR",
)

@Preview
@Composable
private fun PlaceDetailFromSwipePreview() = IrisPreview {
    PlaceDetailContent(PlaceDetailUiState(info = PreviewInfo), null, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun PlaceDetailFromItineraryPreview() = IrisPreview {
    PlaceDetailContent(PlaceDetailUiState(info = PreviewInfo.copy(category = null, reason = null, priceLevel = null)), null, {}, null, null, {}, {})
}

@Preview
@Composable
private fun PlaceDetailLoadingPreview() = IrisPreview {
    PlaceDetailContent(PlaceDetailUiState(loading = true), "Air Terjun Tegenungan", {}, null, null, {}, {})
}
