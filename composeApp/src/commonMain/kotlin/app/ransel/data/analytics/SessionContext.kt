package app.ransel.data.analytics

import app.ransel.data.auth.SessionState
import app.ransel.data.repository.AppLanguage

/** User properties (Firebase: name ≤ 24 characters, value ≤ 36), so the PRD metrics can be split per group. */
object UserProperty {
    /** guest (mobile without an account), signed_out (web), signed_in, expired. */
    const val ACCOUNT_STATUS = "account_status"
    /** true / false while signed in; unset otherwise. Explore needs a verified email. */
    const val EMAIL_VERIFIED = "email_verified"
    /** system / id / en: the in-app language choice (web: always system). */
    const val APP_LANGUAGE = "app_language"
}

/** Custom keys on crash reports. */
object CrashKey {
    const val ACCOUNT_STATUS = "account_status"
    const val APP_LANGUAGE = "app_language"
    /** Which server the build talks to (staging or production). */
    const val API_ORIGIN = "api_origin"
}

/**
 * Tells analytics and crash reports who is using the app (account id only) and in which state. Called by the app
 * whenever the session or the language changes; [requiresAccount] = web, where "not signed in" is not a guest.
 */
fun applySessionContext(
    analytics: Analytics,
    crashes: CrashReporter,
    state: SessionState,
    language: AppLanguage,
    requiresAccount: Boolean,
) {
    val user = when (state) {
        is SessionState.SignedIn -> state.user
        is SessionState.Expired -> state.user
        else -> null
    }
    val status = when (state) {
        is SessionState.SignedIn -> "signed_in"
        is SessionState.Expired -> "expired"
        else -> if (requiresAccount) "signed_out" else "guest"
    }
    val languageValue = when (language) {
        AppLanguage.SYSTEM -> "system"
        AppLanguage.INDONESIAN -> "id"
        AppLanguage.ENGLISH -> "en"
    }
    analytics.setUser(user?.id)
    analytics.setUserProperty(UserProperty.ACCOUNT_STATUS, status)
    analytics.setUserProperty(UserProperty.EMAIL_VERIFIED, user?.emailVerified?.toString())
    analytics.setUserProperty(UserProperty.APP_LANGUAGE, languageValue)
    crashes.setUser(user?.id)
    crashes.setKey(CrashKey.ACCOUNT_STATUS, status)
    crashes.setKey(CrashKey.APP_LANGUAGE, languageValue)
}
