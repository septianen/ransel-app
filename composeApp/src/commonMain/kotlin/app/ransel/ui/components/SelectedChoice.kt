package app.ransel.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look of a selected answer or choice: the interview's rows, chips and cards, and the cards of the Travel sheet (owner,
 * 16 Nov 2026: purple, the IRIS `tertiary` roles, replacing the neutral "Alt A"). One place, so every screen that has a
 * selection matches. Light: `tertiary-container` #FFE6FF with a 2 dp `tertiary` #7C2D92 border and a `tertiary` tile with a
 * white icon (text #1B1B1B on the fill 14.8:1). Dark: #5A226B with #EE99FF. Green stays for the main button and the segments.
 */
object SelectedChoice {
    val BorderWidth: Dp = 2.dp

    /** Background of the selected row, chip or card. */
    val fill: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer

    /** Its 2 dp border. */
    val border: Color @Composable get() = MaterialTheme.colorScheme.tertiary

    /** The icon tile and the round check, and what is drawn on them. */
    val accent: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val onAccent: Color @Composable get() = MaterialTheme.colorScheme.onTertiary
}
