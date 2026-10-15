package app.ransel.data.remote

import android.os.Build
import app.ransel.BuildConfig

actual fun apiOrigin(): String = BuildConfig.API_ORIGIN

actual fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }
    .joinToString(" ").ifBlank { "Android" }
