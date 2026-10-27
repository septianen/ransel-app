package app.ransel.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun ExitBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun SheetBackHandler(onBack: () -> Unit) = Unit
