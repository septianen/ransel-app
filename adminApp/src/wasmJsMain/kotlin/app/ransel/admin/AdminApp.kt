package app.ransel.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.AdminSession
import app.ransel.admin.data.AdminState
import app.ransel.admin.data.ApiEnvironment
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.LocalEnvironments
import app.ransel.admin.feature.auth.BrandMark
import app.ransel.admin.feature.auth.SignInScreen
import app.ransel.admin.feature.shell.AdminSection
import app.ransel.admin.feature.shell.AdminSidebar
import app.ransel.admin.feature.shell.EnvironmentMenuBadge
import app.ransel.admin.feature.shell.EnvironmentStrip
import app.ransel.admin.feature.monitoring.ExploreAiScreen
import app.ransel.admin.feature.monitoring.OverviewScreen
import app.ransel.admin.feature.monitoring.SystemScreen
import app.ransel.admin.feature.users.UserDetailScreen
import app.ransel.admin.feature.users.UsersScreen
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.not_admin_body
import app.ransel.admin.generated.resources.not_admin_switch
import app.ransel.admin.generated.resources.not_admin_title
import app.ransel.admin.generated.resources.open_menu
import app.ransel.admin.platform.locationHash
import app.ransel.admin.platform.onHashChange
import app.ransel.admin.platform.setLocationHash
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.rememberAdminFormat
import app.ransel.shared.dto.AdminMeDto
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Pages, kept in the URL hash so a reload or a shared link opens the same page: `#/overview`, `#/users/<id>`, ... */
sealed interface AdminRoute {
    val hash: String

    data object Overview : AdminRoute {
        override val hash = "#/overview"
    }

    data object ExploreAi : AdminRoute {
        override val hash = "#/explore"
    }

    data object System : AdminRoute {
        override val hash = "#/system"
    }

    data object Users : AdminRoute {
        override val hash = "#/users"
    }

    data class User(val id: String) : AdminRoute {
        override val hash = "#/users/$id"
    }

    companion object {
        fun parse(hash: String): AdminRoute {
            val parts = hash.removePrefix("#").trim('/').split('/').filter { it.isNotEmpty() }
            return when {
                parts.size == 2 && parts[0] == "users" -> User(parts[1])
                parts.firstOrNull() == "users" -> Users
                parts.firstOrNull() == "explore" -> ExploreAi
                parts.firstOrNull() == "system" -> System
                else -> Overview
            }
        }
    }
}

/** The current [AdminRoute], following the browser's back/forward buttons (one `hashchange` listener for the page). */
object HashRoute {
    private val route = MutableStateFlow(AdminRoute.parse(locationHash()))
    val current: StateFlow<AdminRoute> = route

    init {
        onHashChange { route.value = AdminRoute.parse(locationHash()) }
    }

    fun go(next: AdminRoute) {
        route.value = next
        setLocationHash(next.hash)
    }
}

/** Below this width the sidebar becomes a drawer (design: the sidebar stacks on a phone). */
private val WideLayout = 900.dp

@Composable
fun AdminApp(api: AdminApi, session: AdminSession) {
    // Karung follows the light IRIS look of the design; the browser's dark mode is honoured like the apps.
    IrisTheme(mode = ThemeMode.SYSTEM) {
        CompositionLocalProvider(LocalAdminFormat provides rememberAdminFormat()) {
            val state by session.state.collectAsState()
            LaunchedEffect(session) { session.restore() }
            // Surface sets the content colour (onSurface) for every Text below, in light and dark.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxSize()) {
                    // Which server this tab is changing, above every screen (sign-in included) unless it is production.
                    EnvironmentStrip(LocalEnvironments.current.current, (state as? AdminState.Ready)?.me?.environment)
                    Box(Modifier.weight(1f)) {
                        when (val current = state) {
                            AdminState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            AdminState.SignedOut -> SignInScreen(session)
                            is AdminState.NotAdmin -> NotAdminScreen(current.email, session)
                            is AdminState.Unavailable -> UnavailableScreen(current.error, session)
                            is AdminState.Ready -> AdminShell(api, session, current.me)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminShell(api: AdminApi, session: AdminSession, me: AdminMeDto) {
    val route by HashRoute.current.collectAsState()
    val navigate: (AdminRoute) -> Unit = HashRoute::go
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onAccessLost: suspend (ApiFailure) -> Unit = { session.onAccessLost(it) }
    val signOut: () -> Unit = { scope.launch { session.signOut() } }
    val environments = LocalEnvironments.current
    // Switching server signs out here first (revokes this server's refresh token), then reloads on the other one.
    val switchEnvironment: (ApiEnvironment) -> Unit = { next ->
        scope.launch {
            session.signOut()
            environments.switchTo(next)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayout
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val sidebar: @Composable () -> Unit = {
            AdminSidebar(
                me = me,
                selected = when (route) {
                    AdminRoute.Overview -> AdminSection.OVERVIEW
                    AdminRoute.ExploreAi -> AdminSection.EXPLORE_AI
                    AdminRoute.System -> AdminSection.SYSTEM
                    AdminRoute.Users, is AdminRoute.User -> AdminSection.USERS
                },
                onSelect = { section ->
                    when (section) {
                        AdminSection.OVERVIEW -> navigate(AdminRoute.Overview)
                        AdminSection.USERS -> navigate(AdminRoute.Users)
                        AdminSection.EXPLORE_AI -> navigate(AdminRoute.ExploreAi)
                        AdminSection.SYSTEM -> navigate(AdminRoute.System)
                        else -> Unit
                    }
                    scope.launch { drawer.close() }
                },
                onSignOut = signOut,
                onSwitchEnvironment = switchEnvironment,
            )
        }
        val page: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (wide) 32.dp else 16.dp, vertical = if (wide) 28.dp else 16.dp),
                ) {
                    if (!wide) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Symbol(Symbols.moreVert, size = 24.dp, contentDescription = stringResource(Res.string.open_menu))
                            }
                            BrandMark(Modifier.weight(1f))
                            EnvironmentMenuBadge(me.environment, environments, switchEnvironment)
                        }
                    }
                    when (val r = route) {
                        AdminRoute.Overview -> OverviewScreen(
                            api, onAccessLost,
                            onOpenSystem = { navigate(AdminRoute.System) },
                            onOpenExplore = { navigate(AdminRoute.ExploreAi) },
                        )
                        AdminRoute.ExploreAi -> ExploreAiScreen(
                            api, onAccessLost,
                            onOpenUser = { navigate(AdminRoute.User(it)) },
                            onOpenSettings = { navigate(AdminRoute.System) },
                        )
                        AdminRoute.System -> SystemScreen(api, onAccessLost)
                        AdminRoute.Users -> UsersScreen(api, snackbar, onAccessLost, onOpenUser = { navigate(AdminRoute.User(it)) }, canAddUsers = me.passwordResetEmailEnabled)
                        is AdminRoute.User -> UserDetailScreen(api, me, r.id, snackbar, onAccessLost, onBack = { navigate(AdminRoute.Users) })
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                sidebar()
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight()) { page() }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = { ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) { sidebar() } },
            ) { page() }
        }
    }
}

/** 403 on `GET /admin/me`: a real RANSEL account, but not in `ADMIN_EMAILS`. */
@Composable
private fun NotAdminScreen(email: String, session: AdminSession) {
    val scope = rememberCoroutineScope()
    CenteredCard {
        Symbol(Symbols.lockPerson, size = 48.dp, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(Res.string.not_admin_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            stringResource(Res.string.not_admin_body, email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { scope.launch { session.signOut() } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.not_admin_switch))
        }
    }
}

/** `GET /admin/me` failed for another reason: offline or a server error. */
@Composable
private fun UnavailableScreen(error: ApiFailure, session: AdminSession) {
    val scope = rememberCoroutineScope()
    CenteredCard { ErrorPanel(error, onRetry = { scope.launch { session.checkAdmin() } }) }
}

@Composable
private fun CenteredCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), contentAlignment = Alignment.TopCenter) {
        AdminCard(Modifier.padding(top = 72.dp).widthIn(max = 480.dp).fillMaxWidth(), padding = 28.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandMark()
                content()
            }
        }
    }
}
