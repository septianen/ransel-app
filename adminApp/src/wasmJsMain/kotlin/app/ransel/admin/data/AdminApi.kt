package app.ransel.admin.data

import app.ransel.shared.api.AdminRoutes
import app.ransel.shared.api.ApiErrorResponse
import app.ransel.shared.api.ApiResponse
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AdjustCreditsRequest
import app.ransel.shared.dto.AdminActionResult
import app.ransel.shared.dto.AdminAuditEntryDto
import app.ransel.shared.dto.AdminMeDto
import app.ransel.shared.dto.AdminUserDetailDto
import app.ransel.shared.dto.AdminUserDto
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.CreateUserRequest
import app.ransel.shared.dto.CreditLedgerEntryDto
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.LoginRequest
import app.ransel.shared.dto.RefreshRequest
import app.ransel.shared.model.UserStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** Same settings as the server's `ApiJson` and the traveller app. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun createHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false // the status decides success, mapped below
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) { requestTimeoutMillis = 15_000L }
    }
    return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
}

/** Filters of GET /admin/users and its CSV export (admin-spec "API"). */
data class UserFilter(
    val query: String = "",
    val status: UserStatus? = null,
    /** `ANDROID`, `IOS` or `WEB` (no phone registered for push). */
    val platform: String? = null,
    val joinedFrom: LocalDate? = null,
    val joinedTo: LocalDate? = null,
    val noCredits: Boolean = false,
)

/** The bearer token and how to renew it; [AdminSession] implements it. */
interface TokenSource {
    fun accessToken(): String?

    /** Exchanges the refresh token; false when that failed (the session is over). */
    suspend fun refresh(): Boolean
}

/**
 * Typed access to the auth endpoints and [AdminRoutes]. Every call returns an [ApiResult]; nothing throws except
 * cancellation. [origin] is scheme + host (+ port), without `/api/v1`.
 */
class AdminApi(
    private val client: HttpClient,
    private val origin: String,
) {
    var tokens: TokenSource? = null

    private fun url(path: String) = origin.trimEnd('/') + ApiRoutes.BASE + path

    // ── auth (the normal RANSEL account) ────────────────────────────────────
    suspend fun login(email: String, password: String): ApiResult<AuthSessionDto> =
        send(HttpMethod.Post, ApiRoutes.AUTH_LOGIN, LoginRequest(email.trim(), password, DEVICE_NAME))
    suspend fun refresh(refreshToken: String): ApiResult<AuthSessionDto> =
        send(HttpMethod.Post, ApiRoutes.AUTH_REFRESH, RefreshRequest(refreshToken))
    suspend fun logout(refreshToken: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.AUTH_LOGOUT, { contentType(ContentType.Application.Json); setBody(RefreshRequest(refreshToken)) }) { }

    // ── admin ───────────────────────────────────────────────────────────────
    suspend fun me(): ApiResult<AdminMeDto> = get(AdminRoutes.ME)

    suspend fun users(filter: UserFilter, page: Int): ApiResult<Page<AdminUserDto>> =
        call(HttpMethod.Get, AdminRoutes.USERS, { filter(filter); parameter("page", page) }) { it.page() }

    /** CSV text of every user matching [filter] (audited on the server) and the file name it suggests. */
    suspend fun exportUsers(filter: UserFilter): ApiResult<Pair<String, String>> =
        call(HttpMethod.Get, AdminRoutes.USERS_EXPORT, { filter(filter) }) { response ->
            val name = response.headers[HttpHeaders.ContentDisposition]
                ?.substringAfter("filename=", "")?.trim('"', ' ')?.takeIf { it.isNotEmpty() }
                ?: "ransel-users.csv"
            response.bodyAsText() to name
        }

    /** Creates an account and sends its invite email (201). */
    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUserDto> =
        send(HttpMethod.Post, AdminRoutes.USERS, request)

    suspend fun user(userId: String): ApiResult<AdminUserDetailDto> = get(AdminRoutes.user(userId))

    suspend fun userLedger(userId: String, page: Int): ApiResult<Page<CreditLedgerEntryDto>> =
        call(HttpMethod.Get, AdminRoutes.userLedger(userId), { parameter("page", page) }) { it.page() }

    suspend fun adjustCredits(userId: String, request: AdjustCreditsRequest): ApiResult<CreditsDto> =
        send(HttpMethod.Post, AdminRoutes.userCredits(userId), request)

    suspend fun signOutAll(userId: String): ApiResult<AdminActionResult> = post(AdminRoutes.userSignOutAll(userId))
    suspend fun disable(userId: String): ApiResult<AdminActionResult> = post(AdminRoutes.userDisable(userId))
    suspend fun enable(userId: String): ApiResult<AdminActionResult> = post(AdminRoutes.userEnable(userId))
    suspend fun sendPasswordReset(userId: String): ApiResult<Unit> =
        call(HttpMethod.Post, AdminRoutes.userPasswordReset(userId)) { }

    suspend fun audit(targetId: String?, page: Int = 1): ApiResult<Page<AdminAuditEntryDto>> =
        call(HttpMethod.Get, AdminRoutes.AUDIT, { targetId?.let { parameter("targetId", it) }; parameter("page", page) }) { it.page() }

    // ── plumbing ────────────────────────────────────────────────────────────
    private fun HttpRequestBuilder.filter(filter: UserFilter) {
        filter.query.trim().takeIf { it.isNotEmpty() }?.let { parameter("query", it) }
        filter.status?.let { parameter("status", it.name) }
        filter.platform?.let { parameter("platform", it) }
        filter.joinedFrom?.let { parameter("joinedFrom", it.toString()) }
        filter.joinedTo?.let { parameter("joinedTo", it.toString()) }
        if (filter.noCredits) parameter("noCredits", true)
    }

    private suspend inline fun <reified T> HttpResponse.page(): Page<T> {
        val envelope = body<ApiResponse<List<T>>>()
        val meta = envelope.meta ?: app.ransel.shared.api.PageMeta(1, envelope.data.size, envelope.data.size)
        return Page(envelope.data, meta)
    }

    private suspend inline fun <reified T> get(path: String): ApiResult<T> =
        call(HttpMethod.Get, path) { it.body<ApiResponse<T>>().data }

    private suspend inline fun <reified T> post(path: String): ApiResult<T> =
        call(HttpMethod.Post, path) { it.body<ApiResponse<T>>().data }

    private suspend inline fun <reified B : Any, reified T> send(method: HttpMethod, path: String, body: B): ApiResult<T> =
        call(method, path, { contentType(ContentType.Application.Json); setBody(body) }) { it.body<ApiResponse<T>>().data }

    /** One call: bearer token on everything but `/auth/`, one token renewal on 401, failures mapped to [ApiFailure]. */
    private suspend inline fun <T> call(
        method: HttpMethod,
        path: String,
        noinline setup: HttpRequestBuilder.() -> Unit = {},
        crossinline parse: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val source = tokens.takeIf { !path.startsWith("/auth/") }
        var renewed = false
        while (true) {
            return try {
                val response = client.request(url(path)) {
                    this.method = method
                    setup() // not `build`: that would resolve to HttpRequestBuilder.build()
                    source?.accessToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
                when {
                    response.status.isSuccess() -> ApiResult.Success(parse(response))
                    response.status.value == 401 && source != null && !renewed -> {
                        renewed = true
                        if (source.refresh()) continue
                        ApiResult.Failure(response.toFailure())
                    }
                    else -> ApiResult.Failure(response.toFailure())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                ApiResult.Failure(ApiFailure(ErrorCode.NETWORK_TIMEOUT, "The request timed out"))
            } catch (e: kotlinx.serialization.SerializationException) {
                ApiResult.Failure(ApiFailure(ErrorCode.INTERNAL_ERROR, "Unexpected response from the server"))
            } catch (e: io.ktor.serialization.ContentConvertException) {
                ApiResult.Failure(ApiFailure(ErrorCode.INTERNAL_ERROR, "Unexpected response from the server"))
            } catch (e: Throwable) {
                ApiResult.Failure(ApiFailure(ErrorCode.NETWORK_UNAVAILABLE, e.message ?: "No connection"))
            }
        }
    }

    private suspend fun HttpResponse.toFailure(): ApiFailure {
        val parsed = runCatching { body<ApiErrorResponse>().error }.getOrNull()
        val statusCode = status.value
        if (parsed != null) return ApiFailure(parsed.code, parsed.message, statusCode, parsed.details)
        return when {
            statusCode == 404 -> ApiFailure(ErrorCode.NOT_FOUND, "Not found", statusCode)
            statusCode == 401 -> ApiFailure(ErrorCode.UNAUTHORIZED, "Not signed in", statusCode)
            statusCode == 403 -> ApiFailure(ErrorCode.FORBIDDEN, "Not allowed", statusCode)
            statusCode >= 500 -> ApiFailure(ErrorCode.INTERNAL_ERROR, "Server error", statusCode)
            else -> ApiFailure(ErrorCode.VALIDATION_FAILED, "Request rejected", statusCode)
        }
    }

    companion object {
        /** Stored with the refresh token; shows up in the admin's own device list. */
        const val DEVICE_NAME = "Karung (web)"
    }
}
