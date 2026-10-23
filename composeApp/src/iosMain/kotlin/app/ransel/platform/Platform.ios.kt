package app.ransel.platform

import androidx.room3.Room
import app.ransel.data.local.DATABASE_FILE
import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.RanselDatabaseConstructor
import app.ransel.data.local.configureAndBuild
import app.ransel.data.auth.KeychainTokenStore
import app.ransel.data.auth.TokenStore
import app.ransel.data.remote.ConnectionMonitor
import app.ransel.data.remote.NoConnectionMonitor
import app.ransel.di.mobileDataModule
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun databasePath(): String {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documents?.path) + "/" + DATABASE_FILE
}

actual val platformModule: Module = module {
    // Push on iOS needs the Firebase iOS SDK in the Xcode project (see docs/tasks/frontend.md F7.x).
    single<app.ransel.data.notification.PushRegistrar> { app.ransel.data.notification.NoPushRegistrar }
    // Analytics and Crashlytics on iOS also need the Firebase iOS SDK in the Xcode project.
    single<app.ransel.data.analytics.Analytics> { app.ransel.data.analytics.NoAnalytics }
    single<app.ransel.data.analytics.CrashReporter> { app.ransel.data.analytics.NoCrashReporter }
    includes(mobileDataModule)
    single<ConnectionMonitor> { NoConnectionMonitor }
    single<TokenStore> { KeychainTokenStore() }
    single<RanselDatabase> {
        Room.databaseBuilder<RanselDatabase>(
            name = databasePath(),
            factory = { RanselDatabaseConstructor.initialize() },
        ).configureAndBuild()
    }
}

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

actual val supportsInAppLanguage: Boolean = true

actual val requiresAccount: Boolean = false

actual fun initialResetToken(): String? = null

actual fun initialVerifyToken(): String? = null

actual fun clearResetTokenFromUrl() = Unit

actual fun initialInviteCode(): String? = null

@androidx.compose.runtime.Composable
actual fun rememberShareText(): (String) -> Unit = { text ->
    // Not verified on a device (no macOS host): presents the system share sheet from the top view controller.
    val controller = platform.UIKit.UIActivityViewController(listOf(text), null)
    platform.UIKit.UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(controller, animated = true, completion = null)
}
