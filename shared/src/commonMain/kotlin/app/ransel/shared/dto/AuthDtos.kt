package app.ransel.shared.dto

import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/* Phase 3. Password rule lives in domain/Validation.kt (same check on Sign up screen and server). */

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val deviceName: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    /** e.g. "Pixel 8", "Chrome di MacBook"; stored with the refresh token. */
    val deviceName: String? = null,
)

/** POST /auth/refresh and POST /auth/logout. */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/** Returned by register, login and refresh. The refresh token rotates on every refresh. */
@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: Timestamp,
    val refreshToken: String,
    val user: MeDto,
)

/** POST /auth/password/forgot — always 204, never reveals whether the email exists. */
@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

/** POST /auth/password/reset — token from the email link (valid 30 min, single use). */
@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

@Serializable
data class MeDto(
    val id: String,
    val email: String,
    val displayName: String,
    /** Only the default for NEW trips; existing trips keep their own currency. */
    val defaultCurrency: String = "IDR",
)

/** PATCH /me. null = unchanged. */
@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val defaultCurrency: String? = null,
)
