package app.ransel.admin.feature.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.overview_active_7
import app.ransel.admin.generated.resources.overview_active_7_note
import app.ransel.admin.generated.resources.overview_active_chart
import app.ransel.admin.generated.resources.overview_active_chart_description
import app.ransel.admin.generated.resources.overview_active_today
import app.ransel.admin.generated.resources.overview_ai_cost
import app.ransel.admin.generated.resources.overview_ai_cost_budget
import app.ransel.admin.generated.resources.overview_ai_cost_no_budget
import app.ransel.admin.generated.resources.overview_budget_link
import app.ransel.admin.generated.resources.overview_budget_runs_out
import app.ransel.admin.generated.resources.overview_budget_used
import app.ransel.admin.generated.resources.overview_cost_llm
import app.ransel.admin.generated.resources.overview_cost_places
import app.ransel.admin.generated.resources.overview_cost_server
import app.ransel.admin.generated.resources.overview_cost_title
import app.ransel.admin.generated.resources.overview_credits_note
import app.ransel.admin.generated.resources.overview_credits_today
import app.ransel.admin.generated.resources.overview_destinations
import app.ransel.admin.generated.resources.overview_destinations_empty
import app.ransel.admin.generated.resources.overview_destinations_note
import app.ransel.admin.generated.resources.overview_errors_24h
import app.ransel.admin.generated.resources.overview_explore_link
import app.ransel.admin.generated.resources.overview_legend_active
import app.ransel.admin.generated.resources.overview_legend_signups
import app.ransel.admin.generated.resources.overview_month_so_far
import app.ransel.admin.generated.resources.overview_new_trips
import app.ransel.admin.generated.resources.overview_range_30
import app.ransel.admin.generated.resources.overview_range_7
import app.ransel.admin.generated.resources.overview_range_label
import app.ransel.admin.generated.resources.overview_range_today
import app.ransel.admin.generated.resources.overview_refresh
import app.ransel.admin.generated.resources.overview_searches_note
import app.ransel.admin.generated.resources.overview_searches_today
import app.ransel.admin.generated.resources.overview_since_previous
import app.ransel.admin.generated.resources.overview_since_week_ago
import app.ransel.admin.generated.resources.overview_subtitle
import app.ransel.admin.generated.resources.overview_title
import app.ransel.admin.generated.resources.see_all
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.AxisLabels
import app.ransel.admin.ui.CardTitle
import app.ransel.admin.ui.ErrorColumn
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.ErrorsTable
import app.ransel.admin.ui.LegendItem
import app.ransel.admin.ui.LineChart
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.ProgressBar
import app.ransel.admin.ui.RangeToggle
import app.ransel.admin.ui.StatTile
import app.ransel.admin.ui.TileGrid
import app.ransel.admin.ui.TileNote
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.admin.ui.todayWib
import app.ransel.shared.dto.AdminOverviewDto
import app.ransel.shared.model.AdminRange
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource

/** Design `Main` ("Ringkasan"): the product at a glance. */
@Composable
fun OverviewScreen(
    api: AdminApi,
    onAccessLost: suspend (ApiFailure) -> Unit,
    onOpenSystem: () -> Unit,
    onOpenExplore: () -> Unit,
) {
    val presenter = rememberPresenter { MonitorPresenter(it, AdminRange.LAST_7_DAYS, onAccessLost, api::overview) }
    val state by presenter.state.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val format = LocalAdminFormat.current
        PageHeader(
            title = stringResource(Res.string.overview_title),
            subtitle = state.data?.let { stringResource(Res.string.overview_subtitle, format.date(it.generatedAt), format.time(it.generatedAt)) },
        ) {
            RangeToggle(
                options = listOf(
                    AdminRange.TODAY to stringResource(Res.string.overview_range_today),
                    AdminRange.LAST_7_DAYS to stringResource(Res.string.overview_range_7),
                    AdminRange.LAST_30_DAYS to stringResource(Res.string.overview_range_30),
                ),
                selected = state.query,
                onSelect = presenter::select,
                label = stringResource(Res.string.overview_range_label),
            )
            RefreshButton(state.loading, presenter::reload)
        }
        val data = state.data
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> {
                state.error?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                OverviewContent(data, onOpenSystem, onOpenExplore)
            }
        }
    }
}

@Composable
fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !loading, modifier = Modifier.heightIn(min = 40.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Symbol(Symbols.refresh, size = 20.dp)
        Box(Modifier.width(8.dp))
        Text(stringResource(Res.string.overview_refresh))
    }
}

/** Two cards side by side on a wide window, stacked on a narrow one. */
@Composable
fun SplitRow(
    leftWeight: Float,
    rightWeight: Float,
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
    wideFrom: Dp = 860.dp,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= wideFrom) {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                left(Modifier.weight(leftWeight).fillMaxHeight())
                right(Modifier.weight(rightWeight).fillMaxHeight())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                left(Modifier.fillMaxWidth())
                right(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OverviewContent(data: AdminOverviewDto, onOpenSystem: () -> Unit, onOpenExplore: () -> Unit) {
    val format = LocalAdminFormat.current
    val scheme = MaterialTheme.colorScheme
    val accents = IrisTheme.accents
    val cost = data.aiCost
    val budget = cost.budgetMicroUsd
    val used = if (budget != null && budget > 0) cost.spentMicroUsd.toFloat() / budget else 0f
    if (budget != null && used * 100 >= cost.alertPercent) {
        BudgetBanner(used, budgetRunsOutOn(cost.spentMicroUsd, budget, todayWib()), onOpenSystem)
    }
    TileGrid(
        tiles = listOf(
            { m ->
                StatTile(Symbols.person, accents.teal, stringResource(Res.string.overview_active_today), format.number(data.activeToday), m) {
                    format.change(data.activeToday, data.activeWeekAgo)?.let { change ->
                        val up = data.activeToday >= data.activeWeekAgo
                        TileNote(stringResource(Res.string.overview_since_week_ago, change), if (up) accents.green else scheme.error, bold = true)
                    }
                }
            },
            { m ->
                StatTile(Symbols.calendarMonth, accents.blue, stringResource(Res.string.overview_active_7), format.number(data.activeLast7Days), m) {
                    TileNote(stringResource(Res.string.overview_active_7_note, format.number(data.registeredUsers)))
                }
            },
            { m ->
                StatTile(Symbols.luggage, accents.magenta, stringResource(Res.string.overview_new_trips, rangeLabel(data.range)), format.number(data.newTrips), m) {
                    format.change(data.newTrips, data.newTripsPrevious)?.let { change ->
                        val up = data.newTrips >= data.newTripsPrevious
                        TileNote(stringResource(Res.string.overview_since_previous, change), if (up) accents.green else scheme.error, bold = true)
                    }
                }
            },
            { m ->
                StatTile(Symbols.autoAwesome, accents.purple, stringResource(Res.string.overview_searches_today), format.number(data.searchesToday), m) {
                    TileNote(
                        stringResource(
                            Res.string.overview_searches_note,
                            format.percent(data.searchesTodayFromCache, data.searchesToday),
                            format.number(data.usersOutOfCreditsToday),
                        ),
                    )
                }
            },
            { m ->
                StatTile(Symbols.savings, accents.green, stringResource(Res.string.overview_credits_today), format.number(data.creditsUsedToday), m) {
                    TileNote(
                        stringResource(
                            Res.string.overview_credits_note,
                            format.percent(data.creditsUsedTodayFree, data.creditsUsedToday),
                            format.number(data.vouchersRedeemedToday),
                        ),
                    )
                }
            },
            { m ->
                StatTile(Symbols.payments, accents.orange, stringResource(Res.string.overview_ai_cost), format.usd(cost.spentMicroUsd, 0), m) {
                    if (budget != null) {
                        ProgressBar(used, if (used * 100 >= cost.alertPercent) accents.yellow else accents.green, Modifier.padding(vertical = 6.dp))
                        TileNote(stringResource(Res.string.overview_ai_cost_budget, format.usd(budget, 0)))
                    } else {
                        TileNote(stringResource(Res.string.overview_ai_cost_no_budget))
                    }
                }
            },
        ),
    )
    SplitRow(
        leftWeight = 2f,
        rightWeight = 1f,
        left = { m ->
            AdminCard(m) {
                Column {
                    CardTitle(stringResource(Res.string.overview_active_chart)) {
                        LegendItem(accents.magenta, stringResource(Res.string.overview_legend_active))
                        LegendItem(accents.teal, stringResource(Res.string.overview_legend_signups))
                    }
                    val days = data.activeByDay
                    LineChart(
                        series = listOf(days.map { it.active } to accents.magenta, days.map { it.signups } to accents.teal),
                        description = stringResource(
                            Res.string.overview_active_chart_description,
                            format.number(days.firstOrNull()?.active ?: 0),
                            format.number(days.lastOrNull()?.active ?: 0),
                        ),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    AxisLabels(days.map { format.dayMonth(it.date) })
                }
            }
        },
        right = { m ->
            AdminCard(m) {
                Column {
                    CardTitle(stringResource(Res.string.overview_destinations)) {
                        Text(stringResource(Res.string.overview_destinations_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    val colors = listOf(accents.magenta, accents.teal, accents.yellow, accents.purple, accents.blue, accents.orange)
                    val top = data.topDestinations.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    if (data.topDestinations.isEmpty()) {
                        Text(stringResource(Res.string.overview_destinations_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    }
                    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        data.topDestinations.forEachIndexed { i, d ->
                            Column {
                                Row {
                                    Text(d.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(format.number(d.count), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                ProgressBar(d.count.toFloat() / top, colors[i % colors.size], Modifier.padding(top = 6.dp), height = 8.dp)
                            }
                        }
                    }
                }
            }
        },
    )
    SplitRow(
        leftWeight = 3f,
        rightWeight = 2f,
        left = { m ->
            AdminCard(m, padding = 0.dp) {
                Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                    CardTitle(stringResource(Res.string.overview_errors_24h), Modifier.padding(horizontal = 20.dp)) {
                        LinkText(stringResource(Res.string.see_all), onOpenSystem)
                    }
                    ErrorsTable(data.recentErrors, listOf(ErrorColumn.CODE, ErrorColumn.ENDPOINT, ErrorColumn.COUNT, ErrorColumn.LAST), minWidth = 520.dp)
                }
            }
        },
        right = { m -> CostCard(data, onOpenExplore, m) },
    )
}

@Composable
private fun rangeLabel(range: AdminRange): String = when (range) {
    AdminRange.TODAY -> stringResource(Res.string.overview_range_today)
    AdminRange.LAST_30_DAYS -> stringResource(Res.string.overview_range_30)
    else -> stringResource(Res.string.overview_range_7)
}

@Composable
private fun BudgetBanner(used: Float, runsOut: kotlinx.datetime.LocalDate?, onOpenSystem: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.md)
            .background(scheme.errorContainer)
            .clickable(role = Role.Button, onClick = onOpenSystem)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(Symbols.warning, size = 22.dp, tint = scheme.onErrorContainer)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.overview_budget_used, "${kotlin.math.round(used * 100).toInt()}%"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onErrorContainer,
            )
            runsOut?.let {
                Text(stringResource(Res.string.overview_budget_runs_out, format.dayMonth(it)), style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
            }
        }
        Text(stringResource(Res.string.overview_budget_link), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onErrorContainer)
        Symbol(Symbols.chevronRight, size = 18.dp, tint = scheme.onErrorContainer)
    }
}

@Composable
private fun CostCard(data: AdminOverviewDto, onOpenExplore: () -> Unit, modifier: Modifier) {
    val format = LocalAdminFormat.current
    val accents = IrisTheme.accents
    val c = data.costByService
    val parts = listOf(
        Triple(stringResource(Res.string.overview_cost_llm), c.llmMicroUsd, accents.magenta),
        Triple(stringResource(Res.string.overview_cost_places), c.placesMicroUsd, accents.teal),
        Triple(stringResource(Res.string.overview_cost_server), c.serverMicroUsd, accents.yellow),
    )
    val total = parts.sumOf { it.second }.coerceAtLeast(1L)
    AdminCard(modifier) {
        Column {
            CardTitle(stringResource(Res.string.overview_cost_title))
            Text(
                stringResource(Res.string.overview_month_so_far, format.monthYear(data.aiCost.month)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                parts.filter { it.second > 0 }.forEach { (_, value, color) ->
                    Box(Modifier.weight(value.toFloat() / total).fillMaxHeight().background(color))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parts.forEach { (label, value, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(format.usd(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.padding(top = 16.dp)) { LinkText(stringResource(Res.string.overview_explore_link), onOpenExplore, chevron = true) }
        }
    }
}

/** Green text link like the design's `<a>`, at least 44 dp tall to tap. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, chevron: Boolean = false, color: Color = IrisTheme.accents.green) {
    Row(
        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        if (chevron) Symbol(Symbols.chevronRight, size = 18.dp, tint = color)
    }
}
