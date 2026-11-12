package app.ransel.platform

import androidx.compose.runtime.Composable
import app.ransel.shared.model.DevicePlatform
import org.koin.core.module.Module

/** Per-platform Koin bindings (database builder, token storage, Ktor engine, ...). Filled in from Phase 1. */
expect val platformModule: Module

/** True in debug builds; gates the Component Gallery entry. */
expect val isDebugBuild: Boolean

/** The web app has no guest mode: every screen needs a signed-in user. Mobile works offline without an account. */
expect val requiresAccount: Boolean

/** Which store build this is (Android, iOS); null on the web, which is always the latest build and is never asked to update. */
expect val devicePlatform: DevicePlatform?

/**
 * The installed version, "0.1.0" (Android: `versionName`, a debug build adds "-debug"; iOS: `CFBundleShortVersionString`);
 * null on the web. The About row and the update check both read it, so there is one place to bump.
 */
expect val installedAppVersion: String?

/** The password-reset token in the page URL (`/reset-password?token=…`), web only; null elsewhere. */
expect fun initialResetToken(): String?

/** The email-verification token in the page URL (`/verify-email?token=…`), web only; null elsewhere. */
expect fun initialVerifyToken(): String?

/** Removes the reset or verification token from the address bar once it has been used (web only). */
expect fun clearResetTokenFromUrl()

/** The invite code in the page URL (`/j/<code>`), web only; null elsewhere (apps get it from the link intent). */
expect fun initialInviteCode(): String?

/** Web: the code of an opened poll link (`/p/<code>`), shown as the guest page; null elsewhere. */
expect fun initialGuestPollCode(): String?

/** Opens the system share sheet with [text] (web: the Web Share API, else copies it). */
@Composable
expect fun rememberShareText(): (String) -> Unit

/**
 * Opens the Play listing for this app. Tries `market://details?id=app.ransel`, then [httpsUrl] when it is an https
 * link, then the built-in Play https listing. Returns false when nothing could be opened. Android only; other
 * platforms return false (they are not asked to update).
 */
@Composable
expect fun rememberPlayStoreOpener(): (httpsUrl: String?) -> Boolean
