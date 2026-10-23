package app.ransel.admin.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.ApiEnvironment
import app.ransel.admin.data.LocalEnvironments
import app.ransel.admin.feature.auth.BrandMark
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.coming_soon
import app.ransel.admin.generated.resources.env_development
import app.ransel.admin.generated.resources.env_production
import app.ransel.admin.generated.resources.env_staging
import app.ransel.admin.generated.resources.nav_content_categories
import app.ransel.admin.generated.resources.nav_content_errors
import app.ransel.admin.generated.resources.nav_content_interests
import app.ransel.admin.generated.resources.nav_content_strings
import app.ransel.admin.generated.resources.nav_explore_ai
import app.ransel.admin.generated.resources.nav_overview
import app.ransel.admin.generated.resources.nav_system
import app.ransel.admin.generated.resources.nav_users
import app.ransel.admin.generated.resources.sidebar_label
import app.ransel.admin.generated.resources.sidebar_role
import app.ransel.admin.generated.resources.sidebar_section_content
import app.ransel.admin.generated.resources.sidebar_section_watch
import app.ransel.admin.generated.resources.sign_out
import app.ransel.admin.ui.Avatar
import app.ransel.admin.ui.Pill
import app.ransel.shared.dto.AdminMeDto
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Sidebar sections; only [USERS] is built in stage A, the rest show "Segera" until their stage. */
enum class AdminSection(val label: StringResource, val symbol: String, val available: Boolean) {
    OVERVIEW(Res.string.nav_overview, Symbols.home, false),
    USERS(Res.string.nav_users, Symbols.person, true),
    EXPLORE_AI(Res.string.nav_explore_ai, Symbols.autoAwesome, false),
    SYSTEM(Res.string.nav_system, Symbols.warning, false),
    CONTENT_STRINGS(Res.string.nav_content_strings, Symbols.translate, false),
    CONTENT_ERRORS(Res.string.nav_content_errors, Symbols.error, false),
    CONTENT_CATEGORIES(Res.string.nav_content_categories, Symbols.category, false),
    CONTENT_INTERESTS(Res.string.nav_content_interests, Symbols.star, false),
}

private val WatchSections = listOf(AdminSection.OVERVIEW, AdminSection.USERS, AdminSection.EXPLORE_AI, AdminSection.SYSTEM)
private val ContentSections = listOf(
    AdminSection.CONTENT_STRINGS, AdminSection.CONTENT_ERRORS, AdminSection.CONTENT_CATEGORIES, AdminSection.CONTENT_INTERESTS,
)

/** Design `Sidebar`: brand, navigation, the signed-in admin and the environment badge from `GET /admin/me` (also the server switch). */
@Composable
fun AdminSidebar(
    me: AdminMeDto,
    selected: AdminSection,
    onSelect: (AdminSection) -> Unit,
    onSignOut: () -> Unit,
    onSwitchEnvironment: (ApiEnvironment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(Res.string.sidebar_label)
    Column(
        modifier
            .width(248.dp)
            .fillMaxHeight()
            .background(scheme.surface)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BrandMark(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 20.dp))
            SectionLabel(stringResource(Res.string.sidebar_section_watch))
            WatchSections.forEach { NavItem(it, it == selected, onSelect) }
            Box(Modifier.height(12.dp))
            SectionLabel(stringResource(Res.string.sidebar_section_content))
            ContentSections.forEach { NavItem(it, it == selected, onSelect) }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(me.displayName, me.id, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(me.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.sidebar_role), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            EnvironmentMenuBadge(me.environment, LocalEnvironments.current, onSwitchEnvironment)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val signOut = stringResource(Res.string.sign_out)
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onSignOut)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.logout, size = 20.dp, tint = scheme.onSurfaceVariant)
                Text(signOut, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun NavItem(section: AdminSection, selected: Boolean, onSelect: (AdminSection) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val content = when {
        selected -> scheme.onPrimaryContainer
        section.available -> scheme.onSurfaceVariant
        else -> scheme.outline
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (selected) scheme.primaryContainer else scheme.surface, CircleShape)
            .clickable(enabled = section.available, role = Role.Tab) { onSelect(section) }
            .semantics {
                this.selected = selected
                if (!section.available) disabled()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(section.symbol, size = 20.dp, tint = content)
        Text(
            stringResource(section.label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (!section.available) {
            Text(stringResource(Res.string.coming_soon), style = MaterialTheme.typography.labelSmall, color = scheme.outline)
        }
    }
}

/** "Produksi" in red so an admin always knows they are changing real data; staging amber, local grey. */
@Composable
fun EnvironmentBadge(environment: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    when (environment.lowercase()) {
        "production" -> Pill(stringResource(Res.string.env_production), scheme.errorContainer, scheme.onErrorContainer, modifier)
        "staging" -> Pill(stringResource(Res.string.env_staging), IrisTheme.accents.yellow.copy(alpha = 0.22f), scheme.onSurface, modifier)
        "development" -> Pill(stringResource(Res.string.env_development), scheme.surfaceContainerHigh, scheme.onSurfaceVariant, modifier)
        else -> Pill(environment, scheme.surfaceContainerHigh, scheme.onSurfaceVariant, modifier)
    }
}
