package app.ransel.data.remote

import app.ransel.shared.api.ErrorCode

/**
 * What a failed API call looks like to the app. ViewModels and repositories never see HTTP status
 * codes or exceptions (composeApp/CLAUDE.md "Rules"): [code] is an `ErrorCode` and is what they switch on.
 */
data class ApiFailure(
    val code: String,
    val message: String,
    /** HTTP status when the server answered; null for client-side failures (timeout, offline). */
    val status: Int? = null,
    val details: Map<String, String>? = null,
) {
    /** The request never got an answer: show the "connection" state, not the "server" one. */
    val isConnection: Boolean
        get() = code == ErrorCode.NETWORK_TIMEOUT || code == ErrorCode.NETWORK_UNAVAILABLE

    val isNotFound: Boolean
        get() = code == ErrorCode.TRIP_NOT_FOUND || code == ErrorCode.NOT_FOUND
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiFailure) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
