package app.ransel.data.auth

import app.ransel.shared.dto.GoogleSignInConfigDto

/** What Google handed back: an ID token (Android, iOS) or a one-time auth code (web). Sent to `POST /auth/google`. */
sealed interface GoogleProof {
    data class IdToken(val value: String) : GoogleProof
    data class AuthCode(val value: String) : GoogleProof
}

sealed interface GoogleResult {
    data class Success(val proof: GoogleProof) : GoogleResult

    /** The user closed Google's sheet or popup: show nothing. */
    data object Cancelled : GoogleResult

    /** Google's step failed (no account on the device, no connection, popup blocked, not available here). */
    data object Failed : GoogleResult
}

/**
 * Google's own native step (Android Credential Manager sheet, iOS Google Sign-In, web popup): system UI, never drawn
 * by the app. [request] takes the server's client ids from `GET /auth/google/config`.
 */
class GoogleSignInLauncher(val request: suspend (GoogleSignInConfigDto) -> GoogleResult)
