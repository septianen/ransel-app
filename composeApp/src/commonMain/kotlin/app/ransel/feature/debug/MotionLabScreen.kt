package app.ransel.feature.debug

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.SectionHeader
import app.ransel.ui.components.TripCover
import app.ransel.ui.motion.rememberReduceMotion
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisMotion
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.irisCard
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview as CmpPreview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.debug_motion_lab_back
import ransel.composeapp.generated.resources.debug_motion_lab_card_hint
import ransel.composeapp.generated.resources.debug_motion_lab_check_split
import ransel.composeapp.generated.resources.debug_motion_lab_content_body
import ransel.composeapp.generated.resources.debug_motion_lab_content_title
import ransel.composeapp.generated.resources.debug_motion_lab_crossfade_note
import ransel.composeapp.generated.resources.debug_motion_lab_detail_activity
import ransel.composeapp.generated.resources.debug_motion_lab_detail_back
import ransel.composeapp.generated.resources.debug_motion_lab_load
import ransel.composeapp.generated.resources.debug_motion_lab_press_button
import ransel.composeapp.generated.resources.debug_motion_lab_press_card
import ransel.composeapp.generated.resources.debug_motion_lab_press_hint
import ransel.composeapp.generated.resources.debug_motion_lab_reduce
import ransel.composeapp.generated.resources.debug_motion_lab_reduce_hint
import ransel.composeapp.generated.resources.debug_motion_lab_reload
import ransel.composeapp.generated.resources.debug_motion_lab_reset_split
import ransel.composeapp.generated.resources.debug_motion_lab_section_press
import ransel.composeapp.generated.resources.debug_motion_lab_section_shared
import ransel.composeapp.generated.resources.debug_motion_lab_section_shimmer
import ransel.composeapp.generated.resources.debug_motion_lab_section_success
import ransel.composeapp.generated.resources.debug_motion_lab_split_done
import ransel.composeapp.generated.resources.debug_motion_lab_subtitle
import ransel.composeapp.generated.resources.debug_motion_lab_success_hint
import ransel.composeapp.generated.resources.debug_motion_lab_title
import ransel.composeapp.generated.resources.debug_motion_lab_trip_dates
import ransel.composeapp.generated.resources.debug_motion_lab_trip_title

private const val FakeTripSeed = "motion-lab-bali"
private const val SharedTripKey = "motion-lab-trip"

/**
 * Debug-only playground so motion tokens can be felt on a phone. Fake data only; production screens are unchanged.
 */
@Composable
fun MotionLabScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val systemReduce = rememberReduceMotion()
    var simulateReduce by remember { mutableStateOf(false) }
    MotionLabContent(
        reduceMotion = systemReduce || simulateReduce,
        simulateReduce = simulateReduce,
        onSimulateReduce = { simulateReduce = it },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun MotionLabContent(
    reduceMotion: Boolean,
    simulateReduce: Boolean,
    onSimulateReduce: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(IrisTheme.spacing.s24),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(Res.string.debug_motion_lab_back))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.debug_motion_lab_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(Res.string.debug_motion_lab_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(Res.string.debug_motion_lab_reduce), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(Res.string.debug_motion_lab_reduce_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = simulateReduce, onCheckedChange = onSimulateReduce)
            }
            if (reduceMotion) {
                Text(
                    stringResource(Res.string.debug_motion_lab_crossfade_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        SharedElementDemo(reduceMotion)
        ShimmerDemo(reduceMotion)
        PressDemo(reduceMotion)
        SuccessDemo(reduceMotion)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedElementDemo(reduceMotion: Boolean) {
    var showDetail by remember { mutableStateOf(false) }
    val fade = IrisMotion.crossfadeSpec<Float>(reduceMotion)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(Res.string.debug_motion_lab_section_shared))
        Text(
            stringResource(Res.string.debug_motion_lab_card_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SharedTransitionLayout {
            AnimatedContent(
                targetState = showDetail,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "motion-lab-trip",
            ) { detail ->
                if (detail) {
                    FakeTripDetail(
                        reduceMotion = reduceMotion,
                        sharedModifier = if (IrisMotion.usesCrossfadeOnly(reduceMotion)) {
                            Modifier
                        } else {
                            Modifier.sharedElement(
                                rememberSharedContentState(SharedTripKey),
                                this@AnimatedContent,
                            )
                        },
                        onBack = { showDetail = false },
                    )
                } else {
                    FakeTripCard(
                        sharedModifier = if (IrisMotion.usesCrossfadeOnly(reduceMotion)) {
                            Modifier
                        } else {
                            Modifier.sharedElement(
                                rememberSharedContentState(SharedTripKey),
                                this@AnimatedContent,
                            )
                        },
                        onOpen = { showDetail = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun FakeTripCard(sharedModifier: Modifier, onOpen: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(sharedModifier)
            .irisCard(IrisShape.lg)
            .clip(IrisShape.lg)
            .clickable(role = Role.Button) {
                haptic.press()
                onOpen()
            },
    ) {
        TripCover(seed = FakeTripSeed, modifier = Modifier.fillMaxWidth().height(140.dp), radius = 0.dp)
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.debug_motion_lab_trip_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    stringResource(Res.string.debug_motion_lab_trip_dates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Symbol(Symbols.chevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FakeTripDetail(reduceMotion: Boolean, sharedModifier: Modifier, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(sharedModifier)
            .irisCard(IrisShape.lg)
            .clip(IrisShape.lg),
    ) {
        TripCover(seed = FakeTripSeed, modifier = Modifier.fillMaxWidth().height(if (reduceMotion) 140.dp else 200.dp), radius = 0.dp)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.debug_motion_lab_trip_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(Res.string.debug_motion_lab_trip_dates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.debug_motion_lab_detail_activity),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onBack) { Text(stringResource(Res.string.debug_motion_lab_detail_back)) }
        }
    }
}

@Composable
private fun ShimmerDemo(reduceMotion: Boolean) {
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        delay(1600)
        loaded = true
        loading = false
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(Res.string.debug_motion_lab_section_shimmer))
        val fade = IrisMotion.crossfadeSpec<Float>(reduceMotion)
        AnimatedContent(
            targetState = loaded,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "motion-lab-shimmer",
        ) { ready ->
            if (ready) {
                Column(
                    Modifier.fillMaxWidth().irisCard(IrisShape.lg).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.debug_motion_lab_content_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(Res.string.debug_motion_lab_content_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ShimmerCard(reduceMotion)
            }
        }
        if (loaded) {
            OutlinedButton(onClick = { loaded = false }) { Text(stringResource(Res.string.debug_motion_lab_reload)) }
        } else {
            Button(onClick = { loading = true }, enabled = !loading) {
                Text(stringResource(Res.string.debug_motion_lab_load))
            }
        }
    }
}

@Composable
private fun ShimmerCard(reduceMotion: Boolean) {
    val shape = IrisShape.lg
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val shine = MaterialTheme.colorScheme.surfaceContainerLowest
    val loop = IrisMotion.shimmerDurationMillis(reduceMotion)
    val shimmer = if (loop == 0) {
        Modifier.clip(shape).background(base)
    } else {
        val progress by rememberInfiniteTransition(label = "iris-shimmer").animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(loop, easing = LinearEasing), RepeatMode.Restart),
            label = "iris-shimmer-progress",
        )
        Modifier.clip(shape).background(base).drawWithContent {
            val x = size.width * progress
            drawRect(
                Brush.linearGradient(
                    listOf(base, shine, base),
                    start = Offset(x - size.width / 2, 0f),
                    end = Offset(x + size.width / 2, size.height),
                ),
            )
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(120.dp).then(shimmer))
        Box(Modifier.fillMaxWidth(0.6f).height(16.dp).then(shimmerBlock(IrisShape.sm, reduceMotion)))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).then(shimmerBlock(IrisShape.sm, reduceMotion)))
    }
}

@Composable
private fun shimmerBlock(shape: Shape, reduceMotion: Boolean): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val shine = MaterialTheme.colorScheme.surfaceContainerLowest
    val loop = IrisMotion.shimmerDurationMillis(reduceMotion)
    if (loop == 0) return Modifier.clip(shape).background(base)
    val progress by rememberInfiniteTransition(label = "iris-shimmer-block").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(loop, easing = LinearEasing), RepeatMode.Restart),
        label = "iris-shimmer-block-progress",
    )
    return Modifier.clip(shape).background(base).drawWithContent {
        val x = size.width * progress
        drawRect(
            Brush.linearGradient(
                listOf(base, shine, base),
                start = Offset(x - size.width / 2, 0f),
                end = Offset(x + size.width / 2, size.height),
            ),
        )
    }
}

@Composable
private fun PressDemo(reduceMotion: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(Res.string.debug_motion_lab_section_press))
        Text(
            stringResource(Res.string.debug_motion_lab_press_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PressScale(reduceMotion) { scale, source ->
            Button(
                onClick = {},
                interactionSource = source,
                modifier = Modifier.scale(scale).fillMaxWidth().height(48.dp),
            ) { Text(stringResource(Res.string.debug_motion_lab_press_button)) }
        }
        PressScale(reduceMotion) { scale, source ->
            val haptic = LocalHapticFeedback.current
            Row(
                Modifier
                    .scale(scale)
                    .fillMaxWidth()
                    .irisCard(IrisShape.lg)
                    .clip(IrisShape.lg)
                    .clickable(interactionSource = source, indication = null, role = Role.Button) {
                        haptic.press()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TripCover(seed = "motion-lab-press", modifier = Modifier.size(56.dp))
                Text(
                    stringResource(Res.string.debug_motion_lab_press_card),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PressScale(reduceMotion: Boolean, content: @Composable (scale: Float, source: MutableInteractionSource) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed) haptic.press()
    }
    val target = if (reduceMotion) 1f else if (pressed) 0.96f else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = IrisMotion.springSpec(IrisMotion.Spring.Effects, reduceMotion),
        label = "press-scale",
    )
    content(scale, source)
}

@Composable
private fun SuccessDemo(reduceMotion: Boolean) {
    var done by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val appear by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = IrisMotion.springSpec(IrisMotion.Spring.DefaultSpatial, reduceMotion),
        label = "success-scale",
    )
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionHeader(stringResource(Res.string.debug_motion_lab_section_success))
        Text(
            stringResource(Res.string.debug_motion_lab_success_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (done) {
                Box(
                    Modifier
                        .scale(if (reduceMotion) 1f else appear)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(Symbols.check, size = 36.dp, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        if (done) {
            Text(
                stringResource(Res.string.debug_motion_lab_split_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = { done = false }) { Text(stringResource(Res.string.debug_motion_lab_reset_split)) }
        } else {
            Button(
                onClick = {
                    haptic.confirm()
                    done = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.debug_motion_lab_check_split)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun HapticFeedback.press() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

private fun HapticFeedback.confirm() {
    performHapticFeedback(HapticFeedbackType.Confirm)
}

@CmpPreview
@Composable
private fun MotionLabPreview() = IrisPreview {
    MotionLabContent(
        reduceMotion = false,
        simulateReduce = false,
        onSimulateReduce = {},
        onBack = {},
        modifier = Modifier.height(640.dp),
    )
}
