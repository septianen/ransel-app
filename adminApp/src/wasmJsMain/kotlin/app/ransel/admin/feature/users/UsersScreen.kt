package app.ransel.admin.feature.users

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.Page
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.page_next
import app.ransel.admin.generated.resources.page_previous
import app.ransel.admin.generated.resources.platform_android
import app.ransel.admin.generated.resources.platform_ios
import app.ransel.admin.generated.resources.platform_web
import app.ransel.admin.generated.resources.status_active
import app.ransel.admin.generated.resources.status_deleted
import app.ransel.admin.generated.resources.status_disabled
import app.ransel.admin.generated.resources.users_col_credits
import app.ransel.admin.generated.resources.users_col_free_today
import app.ransel.admin.generated.resources.users_col_joined
import app.ransel.admin.generated.resources.users_col_last_active
import app.ransel.admin.generated.resources.users_col_platform
import app.ransel.admin.generated.resources.users_col_status
import app.ransel.admin.generated.resources.users_col_trips
import app.ransel.admin.generated.resources.users_col_user
import app.ransel.admin.generated.resources.users_count
import app.ransel.admin.generated.resources.users_empty
import app.ransel.admin.generated.resources.users_export
import app.ransel.admin.generated.resources.users_exported
import app.ransel.admin.generated.resources.users_filter_joined
import app.ransel.admin.generated.resources.users_filter_joined_30
import app.ransel.admin.generated.resources.users_filter_joined_7
import app.ransel.admin.generated.resources.users_filter_joined_90
import app.ransel.admin.generated.resources.users_filter_joined_all
import app.ransel.admin.generated.resources.users_filter_no_credits
import app.ransel.admin.generated.resources.users_filter_platform
import app.ransel.admin.generated.resources.users_filter_platform_all
import app.ransel.admin.generated.resources.users_filter_status_all
import app.ransel.admin.generated.resources.users_range
import app.ransel.admin.generated.resources.users_search
import app.ransel.admin.generated.resources.users_title
import app.ransel.admin.generated.resources.time_never
import app.ransel.admin.platform.downloadText
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.Avatar
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.Pill
import app.ransel.admin.ui.failureMessage
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.admin.ui.todayWib
import app.ransel.shared.dto.AdminUserDto
import app.ransel.shared.model.UserStatus
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The table is at least this wide; narrower windows scroll it sideways (design: overflow-x auto). */
private val TableMinWidth = 1040.dp

@Composable
fun UsersScreen(
    api: AdminApi,
    snackbar: SnackbarHostState,
    onAccessLost: suspend (ApiFailure) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val presenter = rememberPresenter {
        UsersPresenter(api, it, ::todayWib, { name, csv -> downloadText(name, csv, "text/csv;charset=utf-8") }, onAccessLost)
    }
    val state by presenter.state.collectAsState()
    LaunchedEffect(presenter) {
        presenter.events.collect { event ->
            val text = when (event) {
                UsersEvent.Exported -> getString(Res.string.users_exported)
                is UsersEvent.Failed -> failureMessage(event.error)
            }
            snackbar.showSnackbar(text)
        }
    }
    UsersContent(
        state = state,
        onQuery = presenter::setQuery,
        onStatus = presenter::setStatus,
        onPlatform = presenter::setPlatform,
        onJoined = presenter::setJoined,
        onNoCredits = presenter::toggleNoCredits,
        onPrevious = presenter::previousPage,
        onNext = presenter::nextPage,
        onRetry = presenter::retry,
        onExport = presenter::export,
        onOpenUser = onOpenUser,
    )
}

@Composable
fun UsersContent(
    state: UsersUiState,
    onQuery: (String) -> Unit,
    onStatus: (UserStatus?) -> Unit,
    onPlatform: (String?) -> Unit,
    onJoined: (JoinedRange) -> Unit,
    onNoCredits: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = LocalAdminFormat.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).widthIn(min = 240.dp)) {
                Text(stringResource(Res.string.users_title), style = MaterialTheme.typography.displaySmall)
                state.result?.let {
                    Text(
                        stringResource(Res.string.users_count, format.number(it.meta.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onExport,
                enabled = !state.exporting,
                modifier = Modifier.heightIn(min = 44.dp).align(Alignment.CenterVertically),
            ) {
                if (state.exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Symbol(Symbols.receiptLong, size = 20.dp)
                Box(Modifier.width(8.dp))
                Text(stringResource(Res.string.users_export))
            }
        }
        if (state.error != null && state.exporting.not() && state.result != null) {
            // A refresh failed while rows are on screen: keep them and say so above.
            Text(failureText(state.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Filters(state, onQuery, onStatus, onPlatform, onJoined, onNoCredits)
        AdminCard(padding = 0.dp) {
            Column {
                when {
                    state.result == null && state.error != null -> ErrorPanel(state.error, onRetry)
                    state.result == null -> LoadingPanel()
                    else -> UsersTable(state.result, state.loading, onOpenUser)
                }
                state.result?.let { Pager(it, state.loading, onPrevious, onNext) }
            }
        }
    }
}

@Composable
private fun Filters(
    state: UsersUiState,
    onQuery: (String) -> Unit,
    onStatus: (UserStatus?) -> Unit,
    onPlatform: (String?) -> Unit,
    onJoined: (JoinedRange) -> Unit,
    onNoCredits: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.queryText,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(Res.string.users_search)) },
            leadingIcon = { Symbol(Symbols.search, size = 20.dp, tint = scheme.onSurfaceVariant) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = scheme.surface,
                focusedContainerColor = scheme.surface,
                unfocusedBorderColor = scheme.outlineVariant,
            ),
            modifier = Modifier.widthIn(min = 260.dp, max = 440.dp),
        )
        MenuChip(
            label = statusLabel(state.filter.status) ?: stringResource(Res.string.users_filter_status_all),
            selected = state.filter.status != null,
            options = listOf<Pair<UserStatus?, String>>(null to stringResource(Res.string.users_filter_status_all)) +
                UserStatus.entries.map { it to (statusLabel(it) ?: it.name) },
            onPick = onStatus,
        )
        MenuChip(
            label = platformLabel(state.filter.platform) ?: stringResource(Res.string.users_filter_platform),
            selected = state.filter.platform != null,
            options = listOf<Pair<String?, String>>(null to stringResource(Res.string.users_filter_platform_all)) +
                listOf("ANDROID", "IOS", "WEB").map { it to (platformLabel(it) ?: it) },
            onPick = onPlatform,
        )
        val joinedLabels = mapOf(
            JoinedRange.ANY to stringResource(Res.string.users_filter_joined_all),
            JoinedRange.DAYS_7 to stringResource(Res.string.users_filter_joined_7),
            JoinedRange.DAYS_30 to stringResource(Res.string.users_filter_joined_30),
            JoinedRange.DAYS_90 to stringResource(Res.string.users_filter_joined_90),
        )
        MenuChip(
            label = if (state.joined == JoinedRange.ANY) stringResource(Res.string.users_filter_joined) else joinedLabels.getValue(state.joined),
            selected = state.joined != JoinedRange.ANY,
            options = JoinedRange.entries.map { it to joinedLabels.getValue(it) },
            onPick = onJoined,
        )
        FilterChip(
            selected = state.filter.noCredits,
            onClick = onNoCredits,
            label = { Text(stringResource(Res.string.users_filter_no_credits)) },
            leadingIcon = if (state.filter.noCredits) ({ Symbol(Symbols.check, size = 18.dp) }) else null,
            modifier = Modifier.heightIn(min = 40.dp),
        )
    }
}

@Composable
private fun <T> MenuChip(label: String, selected: Boolean, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = if (selected) ({ Symbol(Symbols.check, size = 18.dp) }) else null,
            trailingIcon = { Symbol(Symbols.arrowDropDown, size = 18.dp) },
            modifier = Modifier.heightIn(min = 40.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

@Composable
fun statusLabel(status: UserStatus?): String? = when (status) {
    null -> null
    UserStatus.ACTIVE -> stringResource(Res.string.status_active)
    UserStatus.DISABLED -> stringResource(Res.string.status_disabled)
    UserStatus.DELETED -> stringResource(Res.string.status_deleted)
}

@Composable
fun platformLabel(platform: String?): String? = when (platform) {
    "ANDROID" -> stringResource(Res.string.platform_android)
    "IOS" -> stringResource(Res.string.platform_ios)
    "WEB" -> stringResource(Res.string.platform_web)
    else -> null
}

@Composable
fun StatusPill(status: UserStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        UserStatus.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
        UserStatus.DISABLED -> scheme.errorContainer to scheme.onErrorContainer
        UserStatus.DELETED -> scheme.surfaceContainer to scheme.onSurfaceVariant
    }
    Pill(statusLabel(status).orEmpty(), bg, fg, modifier)
}

private val Weights = listOf(3f, 1.3f, 1.3f, 1f, 0.7f, 0.8f, 1f, 1.1f)

@Composable
private fun RowScope.Cell(index: Int, end: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.weight(Weights[index]).padding(horizontal = 12.dp),
        contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun UsersTable(page: Page<AdminUserDto>, loading: Boolean, onOpenUser: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val headers = listOf(
        Res.string.users_col_user, Res.string.users_col_joined, Res.string.users_col_last_active, Res.string.users_col_platform,
        Res.string.users_col_trips, Res.string.users_col_credits, Res.string.users_col_free_today, Res.string.users_col_status,
    )
    val numeric = setOf(4, 5, 6)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (maxWidth > TableMinWidth) maxWidth else TableMinWidth
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width).padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp)) {
                    headers.forEachIndexed { i, res ->
                        Cell(i, end = i in numeric) {
                            Text(stringResource(res), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                if (page.items.isEmpty()) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    Text(
                        stringResource(Res.string.users_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                page.items.forEach { user ->
                    HorizontalDivider(color = scheme.outlineVariant)
                    UserRow(user, dimmed = loading, onClick = { onOpenUser(user.id) })
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: AdminUserDto, dimmed: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val muted = if (dimmed) scheme.outline else scheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyMedium
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(user.displayName, user.id)
                Column {
                    Text(user.displayName, style = body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Cell(1) { Text(format.date(user.joinedAt), style = body, color = muted) }
        Cell(2) { Text(user.lastActiveAt?.let { format.relative(it) } ?: stringResource(Res.string.time_never), style = body, color = muted) }
        Cell(3) { Text(platformLabel(user.platform?.name ?: "WEB").orEmpty(), style = body) }
        Cell(4, end = true) { Text(format.number(user.tripCount), style = body, fontWeight = FontWeight.SemiBold) }
        Cell(5, end = true) {
            val empty = user.balance + user.dailyFreeLeft == 0
            Text(
                format.number(user.balance),
                style = body,
                fontWeight = if (empty) FontWeight.Bold else FontWeight.SemiBold,
                color = if (empty) scheme.error else scheme.onSurface,
            )
        }
        Cell(6, end = true) { Text(format.number(user.dailyFreeLeft), style = body, color = muted) }
        Cell(7) { StatusPill(user.status) }
    }
}

@Composable
private fun Pager(page: Page<AdminUserDto>, loading: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    HorizontalDivider(color = scheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
        PagerButton(Symbols.chevronLeft, stringResource(Res.string.page_previous), enabled = page.meta.page > 1 && !loading, onClick = onPrevious)
        PagerButton(Symbols.chevronRight, stringResource(Res.string.page_next), enabled = page.hasNext && !loading, onClick = onNext)
    }
}

@Composable
private fun PagerButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).background(scheme.surface, IrisShape.md),
    ) {
        Symbol(symbol, size = 20.dp, tint = if (enabled) scheme.onSurface else scheme.outline, contentDescription = label)
    }
}
