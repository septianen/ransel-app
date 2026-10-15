package app.ransel.data.remote

import app.ransel.shared.api.ApiErrorResponse
import app.ransel.shared.api.ApiResponse
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.DayOrderRequest
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.dto.TripDetailDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.UpdateTripRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** Same settings as the server's `ApiJson`, so both sides read and write identical JSON. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private const val REQUEST_TIMEOUT_MILLIS = 15_000L

/**
 * HTTP client for the API. [engine] is only passed by tests (MockEngine); otherwise the platform's
 * engine is used (OkHttp, Darwin, or the browser `fetch`).
 */
fun createApiHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false // the status decides success, we map it ourselves
        install(ContentNegotiation) { json(ApiJson) }
        // Only the request timeout: the browser engine supports no connect/socket timeouts.
        install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
    }
    return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
}

/**
 * Typed access to the REST API (`ApiRoutes`). Every call returns an [ApiResult]; nothing throws except
 * coroutine cancellation. [origin] is scheme + host (+ port), without `/api/v1`.
 */
class RanselApi(
    private val client: HttpClient,
    private val origin: String,
) {
    private fun url(path: String) = origin.trimEnd('/') + ApiRoutes.BASE + path

    // ── trips ───────────────────────────────────────────────────────────────
    suspend fun trips(): ApiResult<List<TripDto>> = get(ApiRoutes.TRIPS)
    suspend fun tripDetail(tripId: String): ApiResult<TripDetailDto> = get(ApiRoutes.trip(tripId))
    suspend fun createTrip(request: CreateTripRequest): ApiResult<TripDto> = send(HttpMethod.Post, ApiRoutes.TRIPS, request)
    suspend fun updateTrip(tripId: String, request: UpdateTripRequest): ApiResult<TripDto> =
        send(HttpMethod.Put, ApiRoutes.trip(tripId), request)
    suspend fun deleteTrip(tripId: String): ApiResult<Unit> = delete(ApiRoutes.trip(tripId))

    // ── itinerary ───────────────────────────────────────────────────────────
    suspend fun itinerary(tripId: String): ApiResult<List<ItineraryItemDto>> = get(ApiRoutes.itinerary(tripId))
    suspend fun createItineraryItem(tripId: String, input: ItineraryItemInput): ApiResult<ItineraryItemDto> =
        send(HttpMethod.Post, ApiRoutes.itinerary(tripId), input)
    suspend fun replaceItineraryItem(tripId: String, input: ItineraryItemInput): ApiResult<ItineraryItemDto> =
        send(HttpMethod.Put, ApiRoutes.itineraryItem(tripId, input.id), input)
    suspend fun deleteItineraryItem(tripId: String, itemId: String): ApiResult<Unit> =
        delete(ApiRoutes.itineraryItem(tripId, itemId))
    suspend fun reorderDay(tripId: String, date: LocalDate, request: DayOrderRequest): ApiResult<List<ItineraryItemDto>> =
        send(HttpMethod.Put, ApiRoutes.dayOrder(tripId, date.toString()), request)

    // ── packing ─────────────────────────────────────────────────────────────
    suspend fun packing(tripId: String): ApiResult<List<PackingItemDto>> = get(ApiRoutes.packing(tripId))
    suspend fun createPackingItem(tripId: String, request: CreatePackingItemRequest): ApiResult<PackingItemDto> =
        send(HttpMethod.Post, ApiRoutes.packing(tripId), request)
    suspend fun patchPackingItem(tripId: String, itemId: String, patch: PatchPackingItemRequest): ApiResult<PackingItemDto> =
        send(HttpMethod.Patch, ApiRoutes.packingItem(tripId, itemId), patch)
    suspend fun deletePackingItem(tripId: String, itemId: String): ApiResult<Unit> =
        delete(ApiRoutes.packingItem(tripId, itemId))
    suspend fun uncheckAllPacking(tripId: String): ApiResult<Unit> =
        call(HttpMethod.Post, ApiRoutes.packingUncheckAll(tripId)) { }

    // ── expenses ────────────────────────────────────────────────────────────
    suspend fun expenses(tripId: String): ApiResult<List<ExpenseDto>> = get(ApiRoutes.expenses(tripId))
    suspend fun createExpense(tripId: String, input: ExpenseInput): ApiResult<ExpenseDto> =
        send(HttpMethod.Post, ApiRoutes.expenses(tripId), input)
    suspend fun replaceExpense(tripId: String, input: ExpenseInput): ApiResult<ExpenseDto> =
        send(HttpMethod.Put, ApiRoutes.expense(tripId, input.id), input)
    suspend fun deleteExpense(tripId: String, expenseId: String): ApiResult<Unit> =
        delete(ApiRoutes.expense(tripId, expenseId))

    // ── plumbing ────────────────────────────────────────────────────────────
    private suspend inline fun <reified T> get(path: String): ApiResult<T> =
        call(HttpMethod.Get, path) { it.body<ApiResponse<T>>().data }

    private suspend inline fun <reified B : Any, reified T> send(method: HttpMethod, path: String, body: B): ApiResult<T> =
        call(method, path, bodyOf = { contentType(ContentType.Application.Json); setBody(body) }) {
            it.body<ApiResponse<T>>().data
        }

    private suspend fun delete(path: String): ApiResult<Unit> = call(HttpMethod.Delete, path) { }

    private suspend inline fun <T> call(
        method: HttpMethod,
        path: String,
        noinline bodyOf: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
        crossinline parse: suspend (HttpResponse) -> T,
    ): ApiResult<T> = try {
        val response = client.request(url(path)) {
            this.method = method
            bodyOf()
        }
        if (response.status.isSuccess()) ApiResult.Success(parse(response)) else ApiResult.Failure(response.toFailure())
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
}
