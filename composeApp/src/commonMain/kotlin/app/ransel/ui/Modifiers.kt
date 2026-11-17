package app.ransel.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp

/** A button-like tap target with an accessibility label. */
fun Modifier.clickableSemantics(label: String?, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = label, role = Role.Button, onClick = onClick)

/**
 * Pulls an element [amount] closer to what sits above it: what a negative top padding would do. Compose refuses a negative
 * padding (`IllegalArgumentException: Padding must be non-negative`) and the app crashes, so never write one: use this.
 * The element keeps its width and loses [amount] of height; it cannot be pulled above its own height.
 */
fun Modifier.pulledUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val up = amount.roundToPx().coerceIn(0, placeable.height)
    layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
}
