package app.ransel.data.auth

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.deviceName
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.RegisterRequest

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
) {
    suspend fun signIn(email: String, password: String): ApiResult<Unit> =
        started(api.login(LoginRequest(email.trim(), password, deviceName())))

    suspend fun signUp(displayName: String, email: String, password: String): ApiResult<Unit> =
        started(api.register(RegisterRequest(email.trim(), password, displayName.trim(), deviceName())))

    suspend fun forgotPassword(email: String): ApiResult<Unit> = api.forgotPassword(email.trim())

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        api.resetPassword(token, newPassword)

    suspend fun signOut() {
        session.signOut()
        hooks.onSignedOut()
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
