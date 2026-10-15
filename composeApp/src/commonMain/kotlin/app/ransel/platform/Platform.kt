package app.ransel.platform

import org.koin.core.module.Module

/** Per-platform Koin bindings (database builder, token storage, Ktor engine, ...). Filled in from Phase 1. */
expect val platformModule: Module

/** True in debug builds; gates the Component Gallery entry. */
expect val isDebugBuild: Boolean

/**
 * False on the web: the browser's language decides, there is no per-app override, so the Account
 * screen hides the language row there.
 */
expect val supportsInAppLanguage: Boolean

/** The web app has no guest mode: every screen needs a signed-in user. Mobile works offline without an account. */
expect val requiresAccount: Boolean

/** The password-reset token in the page URL (`/reset-password?token=…`), web only; null elsewhere. */
expect fun initialResetToken(): String?

/** Removes the reset token from the address bar once it has been used (web only). */
expect fun clearResetTokenFromUrl()
