package app.ransel.platform

import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module { }

// Web has no debug/release split yet; the gallery stays reachable until the Phase 2 deploy.
actual val isDebugBuild: Boolean = true
