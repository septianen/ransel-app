package app.ransel.di

import app.ransel.platform.platformModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Platform-independent bindings: repositories' interfaces, ViewModels, API client. Grows per phase. */
val commonModule: Module = module { }

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(commonModule, platformModule)
    }
}
