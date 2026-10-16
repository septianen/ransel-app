package app.ransel.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("() => !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)")
private external fun prefersReducedMotion(): Boolean

@Composable
actual fun rememberReduceMotion(): Boolean = remember { prefersReducedMotion() }
