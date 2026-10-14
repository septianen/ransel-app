package app.ransel.platform

import androidx.room3.Room
import app.ransel.BuildConfig
import app.ransel.data.local.DATABASE_FILE
import app.ransel.data.local.RanselDatabase
import app.ransel.data.local.RanselDatabaseConstructor
import app.ransel.data.local.configureAndBuild
import app.ransel.di.mobileDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    includes(mobileDataModule)
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
