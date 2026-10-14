package app.ransel.platform

import androidx.room3.Room
import app.ransel.data.local.DATABASE_FILE
import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.RanselDatabaseConstructor
import app.ransel.data.local.configureAndBuild
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
    includes(mobileDataModule)
    single<RanselDatabase> {
        Room.databaseBuilder<RanselDatabase>(
            name = databasePath(),
            factory = { RanselDatabaseConstructor.initialize() },
        ).configureAndBuild()
    }
}

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary
