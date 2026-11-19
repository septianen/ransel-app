package app.ransel.feature.account

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.remote.apiImageUrl
import app.ransel.ui.motion.IrisMotion
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import coil3.compose.AsyncImage
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_credits
import ransel.composeapp.generated.resources.account_credits_count
import ransel.composeapp.generated.resources.account_sign_out
import ransel.composeapp.generated.resources.account_sync_never
import ransel.composeapp.generated.resources.account_sync_now
import ransel.composeapp.generated.resources.account_sync_retry
import ransel.composeapp.generated.resources.account_sync_running
import ransel.composeapp.generated.resources.account_sync_tile_synced
import ransel.composeapp.generated.resources.account_top_up
import ransel.composeapp.generated.resources.action_back

private const val PRESS_SCALE = 0.97f
private val RowHeight = 56.dp
private val DividerInset = 56.dp

/** Row / card press: scale 0.97 over Short3 (Figma). Kept when Reduce motion is on. */
internal fun Modifier.accountPress(onClick: (() -> Unit)?, enabled: Boolean = true): Modifier = composed {
    if (onClick == null || !enabled) return@composed this
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = IrisMotion.short3(),
        label = "account-press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
internal fun AccountGroupLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun AccountGroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}

internal enum class AccountRowTrailing { CHEVRON, OPEN_IN_NEW, NONE }

@Composable
internal fun AccountSettingRow(
    symbol: String,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    trailing: AccountRowTrailing = if (onClick != null) AccountRowTrailing.CHEVRON else AccountRowTrailing.NONE,
    showDivider: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val titleColor = if (destructive) scheme.error else scheme.onSurface
    val iconTint = if (destructive) scheme.error else scheme.onSurface
    Column(modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = DividerInset), color = scheme.outlineVariant)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowHeight)
                .accountPress(onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Symbol(symbol, size = 24.dp, tint = iconTint)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurfaceVariant,
                )
            }
            when (trailing) {
                AccountRowTrailing.CHEVRON -> Symbol(Symbols.chevronRight, size = 18.dp, tint = scheme.onSurfaceVariant)
                AccountRowTrailing.OPEN_IN_NEW -> Symbol(Symbols.openInNew, size = 18.dp, tint = scheme.onSurfaceVariant)
                AccountRowTrailing.NONE -> Unit
            }
        }
    }
}

@Composable
internal fun AccountSwitchRow(
    symbol: String,
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = DividerInset), color = scheme.outlineVariant)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowHeight)
                .accountPress({ onCheckedChange(!checked) })
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Symbol(symbol, size = 24.dp, tint = scheme.onSurface)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = scheme.primary,
                    checkedThumbColor = scheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
internal fun ProfileCard(name: String, email: String, avatarUrl: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.surfaceContainerLow)
            .accountPress(onClick)
            .padding(16.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onPrimaryContainer,
            )
            apiImageUrl(avatarUrl)?.let { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Symbol(Symbols.chevronRight, size = 18.dp, tint = scheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatusTilesRow(
    credits: Int?,
    sync: SyncTileSnapshot?,
    onCredits: () -> Unit,
    onSync: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        credits?.let {
            CreditsTile(it, onCredits, Modifier.weight(1f))
        }
        sync?.let {
            SyncTile(it, onSync, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CreditsTile(available: Int, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    StatusTile(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(Symbols.autoAwesome, size = 24.dp, tint = scheme.onSurface)
            Text(
                stringResource(Res.string.account_credits_count, available),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(Res.string.account_top_up),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
    }
}

@Composable
private fun SyncTile(snapshot: SyncTileSnapshot, onSync: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = rememberReduceMotion()
    val spinning = snapshot.kind == SyncTileKind.SYNCING && !reduceMotion
    val angle by rememberInfiniteTransition(label = "sync-cloud").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "sync-cloud-angle",
    )
    val title = when (snapshot.kind) {
        SyncTileKind.SYNCING -> stringResource(Res.string.account_sync_running)
        SyncTileKind.OFFLINE -> stringResource(Res.string.account_sync_never)
        SyncTileKind.IDLE -> snapshot.clock?.let { stringResource(Res.string.account_sync_tile_synced, it) }
            ?: stringResource(Res.string.account_sync_never)
    }
    val action = when {
        !snapshot.actionVisible -> null
        snapshot.actionIsError -> stringResource(Res.string.account_sync_retry)
        else -> stringResource(Res.string.account_sync_now)
    }
    val icon = when (snapshot.kind) {
        SyncTileKind.OFFLINE -> Symbols.cloudOff
        SyncTileKind.SYNCING -> Symbols.cloudSync
        SyncTileKind.IDLE -> Symbols.cloudDone
    }
    StatusTile(onClick = onSync.takeIf { snapshot.actionVisible }, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(24.dp).graphicsLayer { if (spinning) rotationZ = angle },
                contentAlignment = Alignment.Center,
            ) { Symbol(icon, size = 24.dp, tint = scheme.onSurface) }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = if (snapshot.actionIsError) scheme.error else scheme.primary,
            )
        }
    }
}

@Composable
private fun StatusTile(onClick: (() -> Unit)?, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clip(IrisShape.md)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .accountPress(onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun SignOutButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, scheme.error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
    ) {
        Text(stringResource(Res.string.account_sign_out), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun AccountSubScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val back = stringResource(Res.string.action_back)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.arrowBack, size = 22.dp, contentDescription = back) }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CreditsBalanceCard(available: Int, onTopUp: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(scheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.account_credits),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                available.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onTopUp,
                modifier = Modifier.height(36.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(stringResource(Res.string.account_top_up), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
