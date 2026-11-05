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

/**
 * POST /auth/password/change — signed in. [newPassword] uses the same rule as register and reset
 * (`checkPassword`, plus the server's 72-byte bcrypt limit). Repeating it is checked only on the client.
 * Wrong [currentPassword] is 400 [app.ransel.shared.api.ErrorCode.CURRENT_PASSWORD_WRONG]. A weak new password,
 * one that is too long, or one equal to the current password is 422 `newPassword` = WEAK_PASSWORD, TOO_LONG or
 * SAME_AS_CURRENT. Nothing is written on those paths. Success revokes every other refresh token and keeps this one.
 */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

/** POST /auth/email/verify — token from the link sent after sign-up (valid 24 h, the newest link only). */
@Serializable
data class VerifyEmailRequest(
    val token: String,
)

/**
 * [creditsGranted] = the signup credits that arrived with this verification (0 when they were already given, e.g. a
 * second tap on a link of an account that is already verified, which also answers 200).
 */
@Serializable
data class VerifyEmailResponse(
    val email: String,
    val creditsGranted: Int,
)

@Serializable
data class MeDto(
    val id: String,
    val email: String,
    val displayName: String,
    /** Only the default for NEW trips; existing trips keep their own currency. */
    val defaultCurrency: String = "IDR",
    /**
     * false after sign-up until the link in the email is opened. Until then Explore and vouchers answer 403
     * EMAIL_NOT_VERIFIED and the signup credits are held back; everything else works. Accounts from before
     * verification existed count as verified (hence the default).
     */
    val emailVerified: Boolean = true,
    /** Profile photo, `ApiRoutes.imageFile(id)` relative to `ApiRoutes.BASE`; null = initials. Set with `ApiRoutes.ME_AVATAR`. */
    val avatarUrl: String? = null,
)

/** PATCH /me. null = unchanged. */
@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val defaultCurrency: String? = null,
)

/**
 * GET /me/deletion: what deleting the account would do, for the confirmation screen. [credits] are lost. Trips the user
 * owns go to the member with an account who joined first ([tripsHandedOver]); with no such member they are deleted
 * ([tripsDeleted]). Titles, not ids: only shown to the person.
 */
@Serializable
data class AccountDeletionPreviewDto(
    val credits: Int,
    val tripsHandedOver: List<String> = emptyList(),
    val tripsDeleted: List<String> = emptyList(),
)

/**
 * POST /me/deletion: deletes the account at once, it cannot be undone. [password] proves it is the owner. Personal data
 * goes and every session ends; the member rows stay under [DELETED_MEMBER_NAME], so the group's balances do not change.
 */
@Serializable
data class DeleteAccountRequest(val password: String)

/** Name a deleted account's member rows keep, in expenses and balances. */
const val DELETED_MEMBER_NAME = "Anggota terhapus"
