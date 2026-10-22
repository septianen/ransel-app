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
import com.chuckerteam.chucker.api.ChuckerInterceptor
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    includes(mobileDataModule)
    single<ConnectionMonitor> { NoConnectionMonitor }
    // Same OkHttp engine Ktor would pick, plus Chucker (a notification + request list in debug builds; no-op in release).
    single<HttpClientEngine> {
        OkHttp.create {
            // OkHttp has its own 10 s read timeout, separate from Ktor's request timeout; without this a slow
            // recommendation search is cut off long before the 120 s the API client allows for it.
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(150, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            }
            addInterceptor(ChuckerInterceptor.Builder(androidContext()).redactHeaders("Authorization").build())
        }
    }
    single<TokenStore> { KeystoreTokenStore(androidContext()) }
    single<app.ransel.data.notification.PushRegistrar> { app.ransel.push.AndroidPushRegistrar(androidContext()) }
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

actual fun initialVerifyToken(): String? = null

actual fun clearResetTokenFromUrl() = Unit

actual fun initialInviteCode(): String? = null

@androidx.compose.runtime.Composable
actual fun rememberShareText(): (String) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    return { text ->
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(send, null))
    }
}
