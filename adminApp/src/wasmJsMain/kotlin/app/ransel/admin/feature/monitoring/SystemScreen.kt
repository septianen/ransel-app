package app.ransel.admin.feature.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.overview_range_7
import app.ransel.admin.generated.resources.seconds_short
import app.ransel.admin.generated.resources.system_backup
import app.ransel.admin.generated.resources.system_backup_none
import app.ransel.admin.generated.resources.system_backup_old
import app.ransel.admin.generated.resources.system_database
import app.ransel.admin.generated.resources.system_error_rate
import app.ransel.admin.generated.resources.system_errors
import app.ransel.admin.generated.resources.system_flyway
import app.ransel.admin.generated.resources.system_health
import app.ransel.admin.generated.resources.system_latency
import app.ransel.admin.generated.resources.system_range_24h
import app.ransel.admin.generated.resources.system_range_label
import app.ransel.admin.generated.resources.system_requests
import app.ransel.admin.generated.resources.system_running
import app.ransel.admin.generated.resources.system_service_email
import app.ransel.admin.generated.resources.system_service_llm
import app.ransel.admin.generated.resources.system_service_places
import app.ransel.admin.generated.resources.system_service_push
import app.ransel.admin.generated.resources.system_services
import app.ransel.admin.generated.resources.system_services_missing
import app.ransel.admin.generated.resources.system_since_start
import app.ransel.admin.generated.resources.system_subtitle
import app.ransel.admin.generated.resources.system_subtitle_no_version
import app.ransel.admin.generated.resources.system_title
import app.ransel.admin.generated.resources.system_trend_description
import app.ransel.admin.generated.resources.explore_none
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.CardTitle
import app.ransel.admin.ui.ErrorColumn
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.ErrorsTable
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.RangeToggle
import app.ransel.admin.ui.TileGrid
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.dto.AdminSystemDto
import app.ransel.shared.model.AdminRange
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Design `Sistem` ("Sistem & error"), stage B: health and errors. The settings section comes with stage C. */
@Composable
fun SystemScreen(api: AdminApi, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { MonitorPresenter(it, AdminRange.LAST_24_HOURS, onAccessLost, api::system) }
    val state by presenter.state.collectAsState()
    val format = LocalAdminFormat.current
    val data = state.data
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(
            title = stringResource(Res.string.system_title),
            subtitle = data?.let {
                val since = format.dateTime(it.startedAt)
                it.version?.let { v -> stringResource(Res.string.system_subtitle, v.take(7), since) }
                    ?: stringResource(Res.string.system_subtitle_no_version, since)
            },
        ) {
            if (data != null) ServicesPill(data)
            RefreshButton(state.loading, presenter::reload)
        }
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> {
                state.error?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                HealthCard(data)
                ErrorsCard(data, presenter::select)
            }
        }
    }
}

/** Green while every outside service has its keys; amber names the ones that do not (their features answer 503). */
@Composable
private fun ServicesPill(data: AdminSystemDto) {
    val accents = IrisTheme.accents
    val scheme = MaterialTheme.colorScheme
    val missing = listOfNotNull(
        stringResource(Res.string.system_service_llm).takeIf { !data.llmConfigured },
        stringResource(Res.string.system_service_places).takeIf { !data.placesConfigured },
        stringResource(Res.string.system_service_email).takeIf { !data.emailConfigured },
        stringResource(Res.string.system_service_push).takeIf { !data.pushConfigured },
    )
    val ok = missing.isEmpty()
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .background(if (ok) accents.green.copy(alpha = 0.16f) else accents.yellow.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (ok) accents.green else accents.yellow, CircleShape))
        Text(
            if (ok) stringResource(Res.string.system_services) else stringResource(Res.string.system_services_missing, missing.joinToString(", ")),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun HealthCard(data: AdminSystemDto) {
    val format = LocalAdminFormat.current
    val seconds = stringResource(Res.string.seconds_short)
    val sinceStart = stringResource(Res.string.system_since_start)
    val recentStart = Clock.System.now() - data.startedAt < 24.hours
    val backupOld = data.lastBackupAt?.let { Clock.System.now() - it > 36.hours } ?: true
    AdminCard {
        Column {
            CardTitle(stringResource(Res.string.system_health), Modifier.padding(bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(IrisTheme.accents.green, CircleShape))
                    Text(stringResource(Res.string.system_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TileGrid(
                minWidth = 150.dp,
                gap = 12.dp,
                tiles = listOf(
                    { m -> HealthItem(stringResource(Res.string.system_requests), format.number(data.requests24h), if (recentStart) sinceStart else null, m) },
                    { m -> HealthItem(stringResource(Res.string.system_latency), data.latencyP95Ms?.let { format.duration(it, seconds) } ?: stringResource(Res.string.explore_none), null, m) },
                    { m ->
                        val rate = if (data.requests24h == 0L) 0.0 else data.serverErrors24h * 100.0 / data.requests24h
                        HealthItem(stringResource(Res.string.system_error_rate), format.decimal(rate, 2) + "%", null, m)
                    },
                    { m -> HealthItem(stringResource(Res.string.system_database), format.bytes(data.databaseBytes), null, m) },
                    { m ->
                        HealthItem(
                            stringResource(Res.string.system_backup),
                            data.lastBackupAt?.let { format.dateTime(it) } ?: stringResource(Res.string.system_backup_none),
                            if (backupOld) stringResource(Res.string.system_backup_old) else null,
                            m,
                            warn = backupOld,
                        )
                    },
                    { m -> HealthItem(stringResource(Res.string.system_flyway), "V${data.flywayVersion}", null, m) },
                ),
            )
        }
    }
}

@Composable
private fun HealthItem(label: String, value: String, note: String?, modifier: Modifier, warn: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxHeight().background(scheme.surfaceContainerLow, IrisShape.md).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = if (warn) scheme.error else Color.Unspecified)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (warn) scheme.error else scheme.onSurfaceVariant) }
    }
}

@Composable
private fun ErrorsCard(data: AdminSystemDto, onRange: (AdminRange) -> Unit) {
    val format = LocalAdminFormat.current
    val trendText = stringResource(Res.string.system_trend_description)
    AdminCard(padding = 0.dp) {
        Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
            CardTitle(stringResource(Res.string.system_errors), Modifier.padding(horizontal = 20.dp)) {
                RangeToggle(
                    options = listOf(
                        AdminRange.LAST_24_HOURS to stringResource(Res.string.system_range_24h),
                        AdminRange.LAST_7_DAYS to stringResource(Res.string.overview_range_7),
                    ),
                    selected = data.range,
                    onSelect = onRange,
                    label = stringResource(Res.string.system_range_label),
                    onCard = true,
                )
            }
            ErrorsTable(
                errors = data.errors,
                columns = ErrorColumn.entries,
                trendDescription = { e -> trendText.replace("%1\$s", e.trend.joinToString(", ") { format.number(it) }) },
                minWidth = 900.dp,
            )
        }
    }
}
