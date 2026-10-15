package app.ransel.platform

import androidx.room3.Room
import app.ransel.BuildConfig
import app.ransel.data.local.DATABASE_FILE
import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.RanselDatabaseConstructor
import app.ransel.data.local.configureAndBuild
import app.ransel.data.auth.KeystoreTokenStore
import app.ransel.data.auth.TokenStore
import app.ransel.data.remote.ConnectionMonitor
import app.ransel.data.remote.NoConnectionMonitor
import app.ransel.di.mobileDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    includes(mobileDataModule)
    single<ConnectionMonitor> { NoConnectionMonitor }
    single<TokenStore> { KeystoreTokenStore(androidContext()) }
    single<RanselDatabase> {
        val context = androidContext()
        Room.databaseBuilder<RanselDatabase>(
            context = context,
            name = context.getDatabasePath(DATABASE_FILE).absolutePath,
            factory = { RanselDatabaseConstructor.initialize() },
        ).configureAndBuild()
    }
}

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

actual val supportsInAppLanguage: Boolean = true

actual val requiresAccount: Boolean = false

actual fun initialResetToken(): String? = null

actual fun clearResetTokenFromUrl() = Unit
