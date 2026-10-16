package app.ransel.ui.motion

import androidx.compose.runtime.Composable

/** True when the user asked the system for less motion; decorative loops should then show a still frame. */
@Composable
expect fun rememberReduceMotion(): Boolean
