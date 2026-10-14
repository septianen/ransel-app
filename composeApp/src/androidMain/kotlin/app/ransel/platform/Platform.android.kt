package app.ransel.platform

import app.ransel.BuildConfig
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module { }

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
