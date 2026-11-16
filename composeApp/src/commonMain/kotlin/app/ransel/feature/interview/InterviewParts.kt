package app.ransel.feature.interview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.components.SelectedChoice
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.interview_exit
import ransel.composeapp.generated.resources.interview_offline
import ransel.composeapp.generated.resources.interview_progress
import ransel.composeapp.generated.resources.interview_progress_a11y
import ransel.composeapp.generated.resources.interview_progress_done
import ransel.composeapp.generated.resources.interview_skip_all

/*
 * The pieces of the interview screens (spec "Wawancara Buat trip", components): the step header with its segments, the
 * big choice rows, the interest chips, the style cards and the bottom bar. Selected is purple ([SelectedChoice], owner,
 * 16 Nov 2026: a `tertiary-container` fill, a 2 dp `tertiary` border, a `tertiary` tile and check); green is only for the
 * main button and the segments.
 */

/** Summary cards (Destination, Dates, Leaving, …): press scale of the siblings, skipped when Reduce motion is on. */
internal const val SUMMARY_PRESS_SCALE = 0.97f

@Composable
internal fun Modifier.summaryCardClickable(onClick: () -> Unit, enabled: Boolean = true): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val scale = if (enabled && pressed && !reduce) SUMMARY_PRESS_SCALE else 1f
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        )
}

/** The segments and the "X dari Y" of the top of a question; [position] of [total] are filled. [leading] is the X or the back arrow. */
@Composable
internal fun InterviewHeader(
    position: Int,
    total: Int,
    leadingSymbol: String,
    onLeading: () -> Unit,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    onSkipAll: (() -> Unit)? = null,
    /** False in "Mode ubah": only the back arrow, no segments and no "X dari Y". */
    showProgress: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val label = if (done) stringResource(Res.string.interview_progress_done, total) else stringResource(Res.string.interview_progress, position, total)
    val spoken = stringResource(Res.string.interview_progress_a11y, position, total)
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onLeading),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    leadingSymbol,
                    contentDescription = stringResource(if (leadingSymbol == Symbols.close) Res.string.interview_exit else Res.string.action_back),
                )
            }
            if (showProgress) Row(
                Modifier.weight(1f).padding(end = 12.dp).semantics { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(total) { index ->
                    val filled = done || index < position
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(if (filled) scheme.primary else scheme.surfaceContainerHigh),
                    )
                }
            }
        }
        if (showProgress) Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            if (onSkipAll != null) {
                TextButton(onClick = onSkipAll, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        stringResource(Res.string.interview_skip_all),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                }
            }
        }
    }
}

/** "Kamu sedang offline." under the header. */
@Composable
internal fun OfflineBanner(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 40.dp).clip(IrisShape.md)
            .background(scheme.inverseSurface).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.wifiOff, size = 16.dp, tint = scheme.inverseOnSurface)
        Text(stringResource(Res.string.interview_offline), style = MaterialTheme.typography.bodyMedium, color = scheme.inverseOnSurface)
    }
}

@Composable
internal fun QuestionTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One answer with its icon, its name, one line on what it changes and a radio on the right. */
@Composable
internal fun ChoiceRow(
    symbol: String,
    label: String,
    effect: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** false = greyed to 38 % and not tappable ("Gunakan lokasi saat ini" without a permission or offline). */
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val border = if (selected) BorderStroke(SelectedChoice.BorderWidth, SelectedChoice.border) else BorderStroke(1.dp, scheme.outlineVariant)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(IrisShape.lg)
            .background(if (selected) SelectedChoice.fill else scheme.surface)
            .border(border, IrisShape.lg)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; this.role = Role.RadioButton; contentDescription = if (effect != null) "$label, $effect" else label }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(if (selected) SelectedChoice.accent else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(symbol, size = 22.dp, tint = if (selected) SelectedChoice.onAccent else scheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            if (effect != null) Text(effect, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (selected) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(SelectedChoice.accent), contentAlignment = Alignment.Center) {
                Symbol(Symbols.check, size = 16.dp, tint = SelectedChoice.onAccent)
            }
        } else {
            Box(Modifier.size(21.dp).clip(CircleShape).border(1.5.dp, scheme.outline, CircleShape))
        }
    }
}

/** A W4 interest: it switches on and off. */
@Composable
internal fun InterestChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, special: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) SelectedChoice.fill else scheme.surface)
            .border(if (selected) BorderStroke(SelectedChoice.BorderWidth, SelectedChoice.border) else BorderStroke(1.dp, scheme.outline), CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Symbol(Symbols.check, size = 16.dp, tint = SelectedChoice.accent)
        else if (special) Symbol(Symbols.autoAwesome, size = 16.dp, tint = scheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold))
    }
}

/** A grey bar where a "Khas" chip is still loading. */
@Composable
internal fun SkeletonChip(modifier: Modifier = Modifier) {
    Box(modifier.height(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

/** The Santai / Padat card. */
@Composable
internal fun StyleCard(label: String, hint: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(IrisShape.lg)
            .background(if (selected) SelectedChoice.fill else scheme.surface)
            .border(if (selected) BorderStroke(SelectedChoice.BorderWidth, SelectedChoice.border) else BorderStroke(1.dp, scheme.outlineVariant), IrisShape.lg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(SelectedChoice.accent), contentAlignment = Alignment.Center) {
                Symbol(Symbols.check, size = 14.dp, tint = SelectedChoice.onAccent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * The sticky bottom bar: the main button (disabled until the screen has an answer, it says so aloud), a small note, and
 * "Lewati" under it when the screen can be skipped. Lewati is `on-surface`, not green.
 */
@Composable
internal fun InterviewBottomBar(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryLoading: Boolean = false,
    note: String? = null,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = scheme.surface, shadowElevation = 8.dp) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            if (primaryLoading) {
                LoadingButton(onClick = onPrimary, loading = true, modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape) {
                    Text(primaryLabel)
                }
            } else {
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().height(48.dp).alpha(if (primaryEnabled) 1f else 0.38f),
                    shape = CircleShape,
                ) { Text(primaryLabel, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
            }
            if (skipLabel != null && onSkip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(skipLabel, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
                }
            }
        }
    }
}
