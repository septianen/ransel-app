package app.ransel.admin.feature.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.Page
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.explore_budget
import app.ransel.admin.generated.resources.explore_cache
import app.ransel.admin.generated.resources.explore_cache_note
import app.ransel.admin.generated.resources.explore_choices
import app.ransel.admin.generated.resources.explore_choices_interests
import app.ransel.admin.generated.resources.explore_choices_price
import app.ransel.admin.generated.resources.explore_col_cards
import app.ransel.admin.generated.resources.explore_col_cost
import app.ransel.admin.generated.resources.explore_col_credits
import app.ransel.admin.generated.resources.explore_col_destination
import app.ransel.admin.generated.resources.explore_col_interests
import app.ransel.admin.generated.resources.explore_col_latency
import app.ransel.admin.generated.resources.explore_col_price
import app.ransel.admin.generated.resources.explore_col_saved
import app.ransel.admin.generated.resources.explore_col_source
import app.ransel.admin.generated.resources.explore_col_time
import app.ransel.admin.generated.resources.explore_col_user
import app.ransel.admin.generated.resources.explore_cost_chart
import app.ransel.admin.generated.resources.explore_cost_chart_description
import app.ransel.admin.generated.resources.explore_credits
import app.ransel.admin.generated.resources.explore_credits_note
import app.ransel.admin.generated.resources.explore_interest_local
import app.ransel.admin.generated.resources.interest_adventure
import app.ransel.admin.generated.resources.interest_culture
import app.ransel.admin.generated.resources.interest_food
import app.ransel.admin.generated.resources.interest_nature
import app.ransel.admin.generated.resources.interest_nightlife
import app.ransel.admin.generated.resources.interest_photo
import app.ransel.admin.generated.resources.interest_relax
import app.ransel.admin.generated.resources.interest_shopping
import app.ransel.admin.generated.resources.explore_kind_poll
import app.ransel.admin.generated.resources.explore_latency
import app.ransel.admin.generated.resources.explore_latency_note
import app.ransel.admin.generated.resources.explore_month_next
import app.ransel.admin.generated.resources.explore_month_previous
import app.ransel.admin.generated.resources.explore_next_batch
import app.ransel.admin.generated.resources.explore_no_credits
import app.ransel.admin.generated.resources.explore_no_credits_note
import app.ransel.admin.generated.resources.explore_none
import app.ransel.admin.generated.resources.explore_projection
import app.ransel.admin.generated.resources.explore_recent
import app.ransel.admin.generated.resources.explore_recent_empty
import app.ransel.admin.generated.resources.explore_recent_note
import app.ransel.admin.generated.resources.explore_saved
import app.ransel.admin.generated.resources.explore_saved_note
import app.ransel.admin.generated.resources.explore_searches
import app.ransel.admin.generated.resources.explore_searches_note
import app.ransel.admin.generated.resources.explore_settings_link
import app.ransel.admin.generated.resources.explore_source_cache
import app.ransel.admin.generated.resources.explore_source_failed
import app.ransel.admin.generated.resources.explore_source_new
import app.ransel.admin.generated.resources.explore_source_no_credits
import app.ransel.admin.generated.resources.explore_subtitle
import app.ransel.admin.generated.resources.explore_title
import app.ransel.admin.generated.resources.explore_total
import app.ransel.admin.generated.resources.overview_cost_llm_short
import app.ransel.admin.generated.resources.overview_cost_places_short
import app.ransel.admin.generated.resources.page_next
import app.ransel.admin.generated.resources.page_previous
import app.ransel.admin.generated.resources.seconds_short
import app.ransel.admin.generated.resources.users_range
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.AxisLabels
import app.ransel.admin.ui.CardTitle
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LegendItem
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.StackedBarChart
import app.ransel.admin.ui.StatTile
import app.ransel.admin.ui.TileGrid
import app.ransel.admin.ui.TileNote
import app.ransel.admin.ui.Wib
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.admin.ui.todayWib
import app.ransel.shared.dto.AdminExploreDto
import app.ransel.shared.dto.AdminSearchDto
import app.ransel.shared.model.AiSearchKind
import app.ransel.shared.model.AiSearchSource
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Design `JelajahAI`: use, cost and quality of the recommendations, one month at a time. */
@Composable
fun ExploreAiScreen(
    api: AdminApi,
    onAccessLost: suspend (ApiFailure) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val thisMonth = monthOf(todayWib())
    val presenter = rememberPresenter { MonitorPresenter(it, thisMonth, onAccessLost) { month -> api.explore(month) } }
    val searches = rememberPresenter { SearchesPresenter(api, it, onAccessLost) }
    val state by presenter.state.collectAsState()
    val searchState by searches.state.collectAsState()
    val format = LocalAdminFormat.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(stringResource(Res.string.explore_title), stringResource(Res.string.explore_subtitle, format.monthYear(state.query))) {
            MonthStepper(state.query, canGoForward = state.query < thisMonth, onChange = presenter::select)
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 40.dp)) {
                Symbol(Symbols.tune, size = 20.dp)
                Box(Modifier.width(8.dp))
                Text(stringResource(Res.string.explore_settings_link))
            }
        }
        val data = state.data
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> {
                state.error?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                ExploreContent(data)
            }
        }
        RecentSearchesCard(searchState, searches::previousPage, searches::nextPage, searches::reload, onOpenUser)
    }
}

@Composable
private fun MonthStepper(month: String, canGoForward: Boolean, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.background(scheme.surface, CircleShape).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(shiftMonth(month, -1)) }) {
            Symbol(Symbols.chevronLeft, size = 20.dp, contentDescription = stringResource(Res.string.explore_month_previous))
        }
        Text(LocalAdminFormat.current.monthYear(month), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onChange(shiftMonth(month, 1)) }, enabled = canGoForward) {
            Symbol(
                Symbols.chevronRight,
                size = 20.dp,
                tint = if (canGoForward) scheme.onSurface else scheme.outline,
                contentDescription = stringResource(Res.string.explore_month_next),
            )
        }
    }
}

@Composable
private fun ExploreContent(data: AdminExploreDto) {
    val format = LocalAdminFormat.current
    val accents = IrisTheme.accents
    val seconds = stringResource(Res.string.seconds_short)
    val none = stringResource(Res.string.explore_none)
    val credits = data.creditsFromFree + data.creditsFromWallet
    TileGrid(
        tiles = listOf(
            { m ->
                StatTile(Symbols.search, accents.purple, stringResource(Res.string.explore_searches), format.number(data.searches), m) {
                    TileNote(stringResource(Res.string.explore_searches_note, format.number(if (data.days == 0) 0 else data.searches / data.days)))
                }
            },
            { m ->
                StatTile(Symbols.sync, accents.teal, stringResource(Res.string.explore_cache), format.percent(data.searchesFromCache, data.searches), m) {
                    TileNote(stringResource(Res.string.explore_cache_note, format.usd(data.cacheSavingsMicroUsd, 0)))
                }
            },
            { m ->
                StatTile(Symbols.bookmark, accents.magenta, stringResource(Res.string.explore_saved), format.percent(data.cardsSaved, data.cardsShown), m) {
                    TileNote(stringResource(Res.string.explore_saved_note, format.number(data.cardsSaved), format.number(data.cardsShown)))
                }
            },
            { m ->
                StatTile(Symbols.schedule, accents.blue, stringResource(Res.string.explore_latency), data.latencyP95Ms?.let { format.duration(it, seconds) } ?: none, m) {
                    data.latencyMedianMs?.let { TileNote(stringResource(Res.string.explore_latency_note, format.duration(it, seconds))) }
                }
            },
            { m ->
                StatTile(Symbols.cancel, MaterialTheme.colorScheme.error, stringResource(Res.string.explore_no_credits), format.number(data.usersOutOfCredits), m) {
                    TileNote(stringResource(Res.string.explore_no_credits_note))
                }
            },
            { m ->
                StatTile(Symbols.savings, accents.green, stringResource(Res.string.explore_credits), format.number(credits), m) {
                    TileNote(stringResource(Res.string.explore_credits_note, format.percent(data.creditsFromFree, credits), format.percent(data.creditsFromWallet, credits)))
                }
            },
        ),
    )
    SplitRow(
        leftWeight = 2f,
        rightWeight = 1f,
        left = { m -> CostChartCard(data, m) },
        right = { m -> ChoicesCard(data, m) },
    )
}

@Composable
private fun CostChartCard(data: AdminExploreDto, modifier: Modifier) {
    val format = LocalAdminFormat.current
    val accents = IrisTheme.accents
    val cost = data.aiCost
    AdminCard(modifier) {
        Column {
            CardTitle(stringResource(Res.string.explore_cost_chart)) {
                LegendItem(accents.magenta, stringResource(Res.string.overview_cost_llm_short))
                LegendItem(accents.teal, stringResource(Res.string.overview_cost_places_short))
            }
            val bars = data.costByDay.map { it.llmMicroUsd to it.placesMicroUsd }
            StackedBarChart(
                bars = bars,
                colors = accents.magenta to accents.teal,
                description = stringResource(
                    Res.string.explore_cost_chart_description,
                    format.usd(bars.minOfOrNull { it.first + it.second } ?: 0),
                    format.usd(bars.maxOfOrNull { it.first + it.second } ?: 0),
                ),
                modifier = Modifier.padding(top = 16.dp),
            )
            AxisLabels(data.costByDay.map { format.dayMonth(it.date) }, count = 3)
            FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.explore_total, format.usd(cost.spentMicroUsd)), style = MaterialTheme.typography.bodyMedium)
                cost.budgetMicroUsd?.let { Text(stringResource(Res.string.explore_budget, format.usd(it, 0)), style = MaterialTheme.typography.bodyMedium) }
                val over = cost.budgetMicroUsd != null && cost.projectedMicroUsd > cost.budgetMicroUsd!!
                Text(
                    stringResource(Res.string.explore_projection, format.usd(cost.projectedMicroUsd, 0)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (over) FontWeight.SemiBold else null,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ChoicesCard(data: AdminExploreDto, modifier: Modifier) {
    val format = LocalAdminFormat.current
    val scheme = MaterialTheme.colorScheme
    val accents = IrisTheme.accents
    AdminCard(modifier) {
        Column {
            CardTitle(stringResource(Res.string.explore_choices))
            Text(format.monthYear(data.month), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(stringResource(Res.string.explore_choices_price), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            val total = data.priceLevels.sumOf { it.count }
            val colors = mapOf(
                PriceLevel.LOW.name to (accents.green.copy(alpha = 0.18f) to scheme.onSurface),
                PriceLevel.MEDIUM.name to (accents.yellow.copy(alpha = 0.22f) to scheme.onSurface),
                PriceLevel.HIGH.name to (accents.magenta.copy(alpha = 0.14f) to accents.magenta),
            )
            Row(Modifier.fillMaxWidth().height(28.dp).clip(IrisShape.sm).background(scheme.surfaceContainerHigh)) {
                if (total > 0) {
                    data.priceLevels.filter { it.count > 0 }.forEach { level ->
                        val (bg, fg) = colors[level.name] ?: (scheme.surfaceContainerHigh to scheme.onSurface)
                        Box(Modifier.weight(level.count.toFloat() / total).fillMaxHeight().background(bg), contentAlignment = Alignment.Center) {
                            Text(
                                "${priceSymbol(level.name)} ${format.percent(level.count, total)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
            Text(stringResource(Res.string.explore_choices_interests), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            if (data.topInterests.isEmpty()) {
                Text(stringResource(Res.string.explore_none), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.topInterests.forEach { interest ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(interestLabel(interest.name), style = MaterialTheme.typography.bodyMedium)
                        interest.destination?.let { destination ->
                            Text(
                                stringResource(Res.string.explore_interest_local, destination),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.background(scheme.surfaceContainer, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Box(Modifier.weight(1f))
                        Text(format.percent(interest.count, data.exploreSearches), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun priceSymbol(level: String?): String = when (level) {
    PriceLevel.LOW.name -> "$"
    PriceLevel.MEDIUM.name -> "$$"
    PriceLevel.HIGH.name -> "$$$"
    else -> "—"
}

/** Built-in interests by their app label; destination interests ("Diving") as the user wrote them. */
@Composable
fun interestLabel(name: String): String =
    Interest.entries.firstOrNull { it.name == name }?.let { stringResource(interestLabels.getValue(it)) } ?: name

private val interestLabels = mapOf(
    Interest.NATURE to Res.string.interest_nature,
    Interest.FOOD to Res.string.interest_food,
    Interest.CULTURE to Res.string.interest_culture,
    Interest.SHOPPING to Res.string.interest_shopping,
    Interest.ADVENTURE to Res.string.interest_adventure,
    Interest.NIGHTLIFE to Res.string.interest_nightlife,
    Interest.PHOTO to Res.string.interest_photo,
    Interest.RELAX to Res.string.interest_relax,
)

private val SearchWeights = listOf(0.9f, 1.6f, 1.3f, 0.6f, 2f, 0.6f, 0.8f, 1f, 0.7f, 0.9f, 1.2f)

@Composable
private fun RowScope.SearchCell(index: Int, end: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.weight(SearchWeights[index]).padding(horizontal = 10.dp), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
        content()
    }
}

/** "Pencarian terbaru": parameters only, never prompt text. */
@Composable
fun RecentSearchesCard(
    state: SearchesUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: ((String) -> Unit)?,
    title: String = stringResource(Res.string.explore_recent),
) {
    val scheme = MaterialTheme.colorScheme
    AdminCard(padding = 0.dp) {
        Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
            CardTitle(title, Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(Res.string.explore_recent_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            val page = state.result
            when {
                page == null && state.error != null -> ErrorPanel(state.error, onRetry)
                page == null -> LoadingPanel()
                page.items.isEmpty() -> Text(
                    stringResource(Res.string.explore_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                else -> {
                    SearchesTable(page.items, dimmed = state.loading, onOpenUser)
                    SearchesPager(page, state.loading, onPrevious, onNext)
                }
            }
        }
    }
}

@Composable
private fun SearchesTable(rows: List<AdminSearchDto>, dimmed: Boolean, onOpenUser: ((String) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val seconds = stringResource(Res.string.seconds_short)
    val headers = listOf(
        Res.string.explore_col_time, Res.string.explore_col_user, Res.string.explore_col_destination, Res.string.explore_col_price,
        Res.string.explore_col_interests, Res.string.explore_col_cards, Res.string.explore_col_saved, Res.string.explore_col_latency,
        Res.string.explore_col_credits, Res.string.explore_col_cost, Res.string.explore_col_source,
    )
    val numeric = setOf(5, 6, 7, 8, 9)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        val width = if (maxWidth > 1100.dp) maxWidth else 1100.dp
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width).padding(horizontal = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    headers.forEachIndexed { i, res ->
                        SearchCell(i, i in numeric) { Text(stringResource(res), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
                    }
                }
                rows.forEach { r ->
                    val body = MaterialTheme.typography.bodyMedium
                    val muted = if (dimmed) scheme.outline else scheme.onSurfaceVariant
                    val failed = r.source == AiSearchSource.FAILED || r.source == AiSearchSource.NO_CREDITS
                    HorizontalDivider(color = scheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SearchCell(0) {
                            Text(if (r.createdAt.toLocalDate() == todayWib()) format.time(r.createdAt) else format.dateTime(r.createdAt), style = body, color = muted)
                        }
                        SearchCell(1) {
                            Text(
                                r.userName,
                                style = body,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (onOpenUser != null) Modifier.clickable { onOpenUser(r.userId) } else Modifier,
                            )
                        }
                        SearchCell(2) {
                            Column {
                                Text(r.destination, style = body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                when {
                                    r.kind == AiSearchKind.POLL -> Text(stringResource(Res.string.explore_kind_poll), style = MaterialTheme.typography.bodySmall, color = muted)
                                    !r.newSearch -> Text(stringResource(Res.string.explore_next_batch), style = MaterialTheme.typography.bodySmall, color = muted)
                                }
                            }
                        }
                        SearchCell(3) { Text(priceSymbol(r.priceLevel?.name), style = body) }
                        SearchCell(4) {
                            Text(r.interests.map { interestLabel(it) }.joinToString(", "), style = body, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SearchCell(5, true) { Text(if (failed) "—" else format.number(r.cards), style = body) }
                        SearchCell(6, true) { Text(if (failed) "—" else format.number(r.saved), style = body) }
                        SearchCell(7, true) { Text(if (r.source == AiSearchSource.NO_CREDITS) "—" else format.duration(r.latencyMs, seconds), style = body) }
                        SearchCell(8, true) { Text(if (r.creditsCharged == 0) "0" else "−${format.number(r.creditsCharged)}", style = body, fontWeight = FontWeight.SemiBold) }
                        SearchCell(9, true) { Text(format.usd(r.costMicroUsd), style = body) }
                        SearchCell(10) { SourcePill(r.source) }
                    }
                }
            }
        }
    }
}

private fun kotlin.time.Instant.toLocalDate() = toLocalDateTime(Wib).date

@Composable
private fun SourcePill(source: AiSearchSource) {
    val accents = IrisTheme.accents
    val scheme = MaterialTheme.colorScheme
    val (label, colors) = when (source) {
        AiSearchSource.NEW -> stringResource(Res.string.explore_source_new) to (accents.purple.copy(alpha = 0.12f) to accents.purple)
        AiSearchSource.CACHE -> stringResource(Res.string.explore_source_cache) to (accents.green.copy(alpha = 0.16f) to scheme.onSurface)
        AiSearchSource.FAILED -> stringResource(Res.string.explore_source_failed) to (scheme.errorContainer to scheme.onErrorContainer)
        AiSearchSource.NO_CREDITS -> stringResource(Res.string.explore_source_no_credits) to (accents.yellow.copy(alpha = 0.22f) to scheme.onSurface)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = colors.second,
        maxLines = 1,
        modifier = Modifier.background(colors.first, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SearchesPager(page: Page<AdminSearchDto>, loading: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    HorizontalDivider(color = scheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.users_range, format.number(page.first), format.number(page.last), format.number(page.meta.total)),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        IconButton(onClick = onPrevious, enabled = page.meta.page > 1 && !loading) {
            Symbol(Symbols.chevronLeft, size = 20.dp, contentDescription = stringResource(Res.string.page_previous))
        }
        IconButton(onClick = onNext, enabled = page.hasNext && !loading) {
            Symbol(Symbols.chevronRight, size = 20.dp, contentDescription = stringResource(Res.string.page_next))
        }
    }
}
