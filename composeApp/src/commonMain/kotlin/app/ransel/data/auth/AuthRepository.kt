package app.ransel.data.auth

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.deviceName
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.RegisterRequest
import app.ransel.shared.dto.VerifyEmailResponse

/**
 * Local work that has to follow a sign-in or sign-out (mobile: re-own the guest data, wipe it on
 * sign-out). The web has nothing local, so it uses [NoSessionHooks].
 */
interface SessionHooks {
    /** [userId] just signed in; runs before the first sync. */
    suspend fun onSignedIn(userId: String)

    /** The user chose to sign out on this device: remove the account's data from it. */
    suspend fun onSignedOut()
}

object NoSessionHooks : SessionHooks {
    override suspend fun onSignedIn(userId: String) = Unit
    override suspend fun onSignedOut() = Unit
}

/** Sign up / in / out and password reset. ViewModels use this, never [RanselApi] directly. */
class AuthRepository(
    private val api: RanselApi,
    private val session: SessionManager,
    private val hooks: SessionHooks,
    private val verification: EmailVerification,
) {
    suspend fun signIn(email: String, password: String): ApiResult<Unit> =
        started(api.login(LoginRequest(email.trim(), password, deviceName())))

    /** A new account starts unverified: the navigation then shows "Satu langkah lagi" ([EmailVerification.prompt]). */
    suspend fun signUp(displayName: String, email: String, password: String): ApiResult<Unit> {
        val result = api.register(RegisterRequest(email.trim(), password, displayName.trim(), deviceName()))
        if (result is ApiResult.Success && !result.value.user.emailVerified) verification.prompt(result.value.user.email)
        return started(result)
    }

    /** Opens the link's token. Signed in, the stored user is refreshed so Explore unlocks at once. */
    suspend fun verifyEmail(token: String): ApiResult<VerifyEmailResponse> =
        api.verifyEmail(token).also { if (it is ApiResult.Success) refreshUser() }

    suspend fun resendVerificationEmail(): ApiResult<Unit> = api.resendVerificationEmail()

    /** GET /me into the session (e.g. "Sudah verifikasi" after opening the link on another device). */
    suspend fun refreshUser(): ApiResult<Unit> {
        if (session.user == null) return ApiResult.Success(Unit)
        return when (val result = api.me()) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                session.updateUser(result.value)
                ApiResult.Success(Unit)
            }
        }
    }

    suspend fun forgotPassword(email: String): ApiResult<Unit> = api.forgotPassword(email.trim())

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        api.resetPassword(token, newPassword)

    /** True once after [SessionManager] ended the session because the account was deactivated (403 ACCOUNT_DISABLED). */
    fun takeAccountDisabled(): Boolean = session.takeAccountDisabled()

    suspend fun signOut() {
        // Hook first: it still needs the token to send what is pending before the data is wiped.
        hooks.onSignedOut()
        session.signOut()
    }

    private suspend fun started(result: ApiResult<AuthSessionDto>): ApiResult<Unit> = when (result) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> {
            hooks.onSignedIn(result.value.user.id)
            session.start(result.value)
            ApiResult.Success(Unit)
        }
    }
}
