package app.ransel.platform

import org.koin.core.module.Module

/** Per-platform Koin bindings (database builder, token storage, Ktor engine, ...). Filled in from Phase 1. */
expect val platformModule: Module

/** True in debug builds; gates the Component Gallery entry. */
expect val isDebugBuild: Boolean
