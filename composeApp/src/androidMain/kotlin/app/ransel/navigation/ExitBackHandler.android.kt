package app.ransel.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun ExitBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun SheetBackHandler(onBack: () -> Unit) = BackHandler(true, onBack)
