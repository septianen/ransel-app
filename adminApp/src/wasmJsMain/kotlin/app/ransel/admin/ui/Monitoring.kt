package app.ransel.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.errors_col_code
import app.ransel.admin.generated.resources.errors_col_count
import app.ransel.admin.generated.resources.errors_col_endpoint
import app.ransel.admin.generated.resources.errors_col_http
import app.ransel.admin.generated.resources.errors_col_last
import app.ransel.admin.generated.resources.errors_col_trend
import app.ransel.admin.generated.resources.errors_col_users
import app.ransel.admin.generated.resources.errors_empty
import app.ransel.admin.generated.resources.errors_no_route
import app.ransel.shared.dto.AdminErrorDto
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource

/*
 * Pieces shared by Karung's monitoring pages (Ringkasan, Jelajah AI, Sistem). Charts are drawn on a Canvas like the
 * expense donut in the app, each with a spoken description.
 */

/** Page title, a grey line under it, and controls on the right that wrap below on a narrow window. */
@Composable
fun PageHeader(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).widthIn(min = 240.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Design's pill toggle ("Hari ini · 7 hari · 30 hari"): one choice, the chosen one dark. */
@Composable
fun <T> RangeToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, label: String, onCard: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .background(if (onCard) scheme.surfaceContainer else scheme.surface, CircleShape)
            .then(if (onCard) Modifier else Modifier.border(1.dp, scheme.outlineVariant, CircleShape))
            .padding(3.dp)
            .selectableGroup()
            .semantics { contentDescription = label },
    ) {
        options.forEach { (value, text) ->
            val chosen = value == selected
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(if (chosen) scheme.inverseSurface else Color.Transparent)
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (chosen) scheme.inverseOnSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Cards in equal columns at least [minWidth] wide (design: `repeat(auto-fit, minmax(200px, 1fr))`). */
@Composable
fun TileGrid(minWidth: Dp = 200.dp, gap: Dp = 16.dp, tiles: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceIn(1, tiles.size.coerceAtLeast(1))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A number tile: coloured icon and label, the big number, and a line under it. */
@Composable
fun StatTile(
    symbol: String,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    AdminCard(modifier.fillMaxHeight()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(symbol, size = 18.dp, tint = tint)
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            footer()
        }
    }
}

/** Grey line under a tile's number. */
@Composable
fun TileNote(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, bold: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (bold) FontWeight.SemiBold else null)
}

/** Thin bar filled to [fraction] (0..1). */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** Card title on the left, [trailing] on the right. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Small coloured mark and label, for a chart legend. */
@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Dates under a chart: first, a few in between, last. */
@Composable
fun AxisLabels(labels: List<String>, count: Int = 5) {
    if (labels.isEmpty()) return
    val picks = if (labels.size <= count) labels else (0 until count).map { labels[it * (labels.size - 1) / (count - 1)] }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        picks.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Lines over the same days; the first series also gets a light fill (design "Pengguna aktif harian"). */
@Composable
fun LineChart(series: List<Pair<List<Int>, Color>>, description: String, modifier: Modifier = Modifier, height: Dp = 220.dp) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val top = (series.flatMap { it.first }.maxOrNull() ?: 0).coerceAtLeast(1) * 1.1f
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        repeat(5) { i ->
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        series.forEachIndexed { index, (values, color) ->
            if (values.isEmpty()) return@forEachIndexed
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            val points = values.mapIndexed { i, v -> Offset(i * step, size.height - v / top * size.height) }
            val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            if (index == 0 && points.size > 1) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }
                drawPath(area, color.copy(alpha = 0.08f))
            }
            drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** One stacked bar per day: [bars] = (bottom, top) values, drawn in [colors] (design "Biaya harian"). */
@Composable
fun StackedBarChart(bars: List<Pair<Long, Long>>, colors: Pair<Color, Color>, description: String, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    val axis = MaterialTheme.colorScheme.outlineVariant
    val top = (bars.maxOfOrNull { it.first + it.second } ?: 0L).coerceAtLeast(1L) * 1.1f
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        if (bars.isEmpty()) return@Canvas
        val gap = 6.dp.toPx()
        val width = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        bars.forEachIndexed { i, (bottom, upper) ->
            val x = i * (width + gap)
            val hBottom = bottom / top * size.height
            val hTop = upper / top * size.height
            drawRect(colors.first, Offset(x, size.height - hBottom), androidx.compose.ui.geometry.Size(width, hBottom))
            drawRect(colors.second, Offset(x, size.height - hBottom - hTop), androidx.compose.ui.geometry.Size(width, hTop))
        }
    }
}

/** Seven small bars for an error's week (design "Tren 7 hari"). */
@Composable
fun TrendBars(values: List<Long>, description: String) {
    val color = IrisTheme.accents.blue
    val top = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier.height(22.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            Box(Modifier.width(8.dp).height((22f * v / top).coerceAtLeast(2f).dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

/** Error code chip: red for server failures, pink for refusals that need attention, grey for the rest (design). */
@Composable
fun ErrorCodePill(code: String, httpStatus: Int) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        httpStatus >= 500 -> scheme.errorContainer to scheme.onErrorContainer
        httpStatus == 429 || httpStatus == 403 -> IrisTheme.accents.magenta.copy(alpha = 0.12f) to IrisTheme.accents.magenta
        else -> scheme.surfaceContainer to scheme.onSurfaceVariant
    }
    Text(
        code,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = fg,
        maxLines = 1,
        modifier = Modifier.background(bg, IrisShape.sm).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Columns of [ErrorsTable]; the overview shows a short version. */
enum class ErrorColumn { CODE, HTTP, ENDPOINT, COUNT, TREND, USERS, LAST }

/**
 * Errors per code. Wide tables scroll sideways inside the card. [trendDescription] speaks one row's week.
 */
@Composable
fun ErrorsTable(
    errors: List<AdminErrorDto>,
    columns: List<ErrorColumn>,
    trendDescription: (AdminErrorDto) -> String = { "" },
    minWidth: Dp = 720.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val weights = mapOf(
        ErrorColumn.CODE to 2.2f, ErrorColumn.HTTP to 0.6f, ErrorColumn.ENDPOINT to 2.6f, ErrorColumn.COUNT to 0.8f,
        ErrorColumn.TREND to 1.2f, ErrorColumn.USERS to 0.8f, ErrorColumn.LAST to 1.1f,
    )
    val numeric = setOf(ErrorColumn.COUNT, ErrorColumn.USERS)
    val headers = mapOf(
        ErrorColumn.CODE to stringResource(Res.string.errors_col_code),
        ErrorColumn.HTTP to stringResource(Res.string.errors_col_http),
        ErrorColumn.ENDPOINT to stringResource(Res.string.errors_col_endpoint),
        ErrorColumn.COUNT to stringResource(Res.string.errors_col_count),
        ErrorColumn.TREND to stringResource(Res.string.errors_col_trend),
        ErrorColumn.USERS to stringResource(Res.string.errors_col_users),
        ErrorColumn.LAST to stringResource(Res.string.errors_col_last),
    )
    val noRoute = stringResource(Res.string.errors_no_route)
    if (errors.isEmpty()) {
        Text(
            stringResource(Res.string.errors_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (maxWidth > minWidth) maxWidth else minWidth
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width).padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    columns.forEach { c ->
                        Cell(weights.getValue(c), c in numeric) {
                            Text(headers.getValue(c), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                errors.forEach { e ->
                    HorizontalDivider(color = scheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        columns.forEach { c ->
                            Cell(weights.getValue(c), c in numeric) {
                                val body = MaterialTheme.typography.bodyMedium
                                when (c) {
                                    ErrorColumn.CODE -> ErrorCodePill(e.code, e.httpStatus)
                                    ErrorColumn.HTTP -> Text(e.httpStatus.toString(), style = body, color = scheme.onSurfaceVariant)
                                    ErrorColumn.ENDPOINT -> Text(
                                        if (e.topRoute == "-") noRoute else e.topRoute,
                                        style = body,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ErrorColumn.COUNT -> Text(format.number(e.count), style = body, fontWeight = FontWeight.Bold)
                                    ErrorColumn.TREND -> TrendBars(e.trend, trendDescription(e))
                                    ErrorColumn.USERS -> Text(format.number(e.users), style = body)
                                    ErrorColumn.LAST -> Text(format.relative(e.lastAt), style = body, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Cell(weight: Float, end: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight).padding(horizontal = 12.dp), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
        content()
    }
}
