package app.ransel.data

import app.ransel.data.auth.ApiAuth
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.remote.getOrNull
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.StartInspirationRequest
import app.ransel.shared.dto.InspirationAnswerRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RanselApiTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        RanselApi(createApiHttpClient(MockEngine(handler)), origin = "http://api.test")

    private val tripJson = """
        {"id":"t1","ownerId":"u","title":"Bali","destination":"Bali","startDate":"2026-11-20","endDate":"2026-11-23",
         "baseCurrency":"IDR","budgetMinor":5000000,"myRole":"OWNER",
         "createdAt":"2026-10-13T05:30:00Z","updatedAt":"2026-10-13T05:30:00Z"}
    """.trimIndent()

    @Test
    fun listUnwrapsTheDataEnvelope() = runTest {
        val api = api { request ->
            assertEquals("http://api.test/api/v1/trips", request.url.toString())
            respond("""{"data":[$tripJson]}""", HttpStatusCode.OK, json)
        }
        val trips = api.trips().getOrNull()
        assertEquals("Bali", trips?.single()?.title)
    }

    @Test
    fun noContentIsSuccessForDelete() = runTest {
        val api = api { request ->
            assertEquals(HttpMethod.Delete, request.method)
            respond("", HttpStatusCode.NoContent)
        }
        assertEquals(ApiResult.Success(Unit), api.deleteTrip("t1"))
    }

    @Test
    fun errorBodyBecomesTheFailureCode() = runTest {
        val api = api {
            respond(
                """{"error":{"code":"TRIP_NOT_FOUND","message":"Trip not found"}}""",
                HttpStatusCode.NotFound, json,
            )
        }
        val failure = (api.tripDetail("missing") as ApiResult.Failure).error
        assertEquals(ErrorCode.TRIP_NOT_FOUND, failure.code)
        assertEquals(404, failure.status)
        assertTrue(failure.isNotFound)
    }

    @Test
    fun validationDetailsAreKept() = runTest {
        val api = api {
            respond(
                """{"error":{"code":"VALIDATION_FAILED","message":"x","details":{"title":"REQUIRED"}}}""",
                HttpStatusCode.UnprocessableEntity, json,
            )
        }
        val request = CreateTripRequest(
            "id", "", "Bali", LocalDate(2026, 11, 20), LocalDate(2026, 11, 23), "IDR",
        )
        val failure = (api.createTrip(request) as ApiResult.Failure).error
        assertEquals(mapOf("title" to "REQUIRED"), failure.details)
    }

    @Test
    fun serverErrorWithoutBodyIsInternalError() = runTest {
        val api = api { respondError(HttpStatusCode.BadGateway) }
        val failure = (api.trips() as ApiResult.Failure).error
        assertEquals(ErrorCode.INTERNAL_ERROR, failure.code)
        assertTrue(!failure.isConnection)
    }

    @Test
    fun unreachableServerIsAConnectionFailure() = runTest {
        val api = api { throw RuntimeException("boom") }
        val failure = (api.trips() as ApiResult.Failure).error
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, failure.code)
        assertTrue(failure.isConnection)
    }

    @Test
    fun requestBodyIsSentAsJson() = runTest {
        var sent = ""
        val api = api { request ->
            sent = request.body.toByteArray().decodeToString()
            respond("""{"data":$tripJson}""", HttpStatusCode.Created, json)
        }
        api.createTrip(
            CreateTripRequest("t1", "Bali", "Bali", LocalDate(2026, 11, 20), LocalDate(2026, 11, 23), "IDR", 5_000_000),
        )
        assertTrue("\"startDate\":\"2026-11-20\"" in sent, sent)
        assertTrue("\"budgetMinor\":5000000" in sent, sent)
    }

    @Test
    fun membersListAndRoleChangeUseTheMemberRoutes() = runTest {
        val seen = mutableListOf<String>()
        var patchBody = ""
        val api = api { request ->
            seen += "${request.method.value} ${request.url.encodedPath}"
            if (request.method == HttpMethod.Patch) patchBody = request.body.toByteArray().decodeToString()
            when (request.method) {
                HttpMethod.Get -> respond(
                    """{"data":[{"tripId":"t1","memberId":"m1","userId":"u1","displayName":"Septian","email":"s@x.id","role":"OWNER","joinedAt":"2026-10-13T05:30:00Z"}]}""",
                    HttpStatusCode.OK, json,
                )
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        assertEquals("Septian", api.members("t1").getOrNull()?.single()?.displayName)
        assertEquals(ApiResult.Success(Unit), api.updateMemberRole("t1", "u2", app.ransel.shared.model.Role.VIEWER))
        assertEquals(ApiResult.Success(Unit), api.removeMember("t1", "u2"))
        assertEquals(ApiResult.Success(Unit), api.leaveTrip("t1"))
        assertEquals(
            listOf(
                "GET /api/v1/trips/t1/members",
                "PATCH /api/v1/trips/t1/members/u2",
                "DELETE /api/v1/trips/t1/members/u2",
                "POST /api/v1/trips/t1/leave",
            ),
            seen,
        )
        assertTrue("\"role\":\"VIEWER\"" in patchBody, patchBody)
    }

    @Test
    fun ownerCannotLeaveIsAFailureCode() = runTest {
        val api = api {
            respond(
                """{"error":{"code":"OWNER_CANNOT_LEAVE","message":"Transfer ownership first"}}""",
                HttpStatusCode.Conflict, json,
            )
        }
        assertEquals(ErrorCode.OWNER_CANNOT_LEAVE, (api.leaveTrip("t1") as ApiResult.Failure).error.code)
    }

    @Test
    fun changePasswordSendsTheBearerAndOnlyTheTwoFields() = runTest {
        var authorization: String? = null
        var body = ""
        val api = RanselApi(
            createApiHttpClient(MockEngine { request ->
                authorization = request.headers[HttpHeaders.Authorization]
                assertEquals("/api/v1/auth/password/change", request.url.encodedPath)
                assertEquals(HttpMethod.Post, request.method)
                body = request.body.toByteArray().decodeToString()
                respond("", HttpStatusCode.NoContent)
            }),
            origin = "http://api.test",
            auth = fixedAuth("access-1"),
        )
        assertEquals(ApiResult.Success(Unit), api.changePassword("OldPassw0rd", "NewPassw0rd"))
        assertEquals("Bearer access-1", authorization)
        assertTrue("\"currentPassword\":\"OldPassw0rd\"" in body, body)
        assertTrue("\"newPassword\":\"NewPassw0rd\"" in body, body)
        assertTrue("confirm" !in body, body)
    }

    @Test
    fun wrongCurrentPasswordIsTheMachineCodeAndDoesNotRefresh() = runTest {
        var refreshes = 0
        val api = RanselApi(
            createApiHttpClient(MockEngine {
                respond(
                    """{"error":{"code":"CURRENT_PASSWORD_WRONG","message":"Current password is wrong","details":{"currentPassword":"WRONG"}}}""",
                    HttpStatusCode.BadRequest,
                    json,
                )
            }),
            origin = "http://api.test",
            auth = object : ApiAuth {
                override fun accessToken() = "access-1"
                override suspend fun refreshAccessToken(): Boolean {
                    refreshes += 1
                    return false
                }
            },
        )
        val failure = (api.changePassword("nope", "NewPassw0rd") as ApiResult.Failure).error
        assertEquals(ErrorCode.CURRENT_PASSWORD_WRONG, failure.code)
        assertEquals(400, failure.status)
        assertEquals("WRONG", failure.details?.get("currentPassword"))
        assertEquals(0, refreshes)
    }

    private val inspirationSessionJson =
        """{"id":"s1","status":"ACTIVE","question":{"slot":"COMBINATION","text":"Question?","options":[{"id":"a","title":"A"},{"id":"b","title":"B"},{"id":"c","title":"C"}]},"expiresAt":"2026-11-22T00:00:00Z"}"""

    @Test
    fun startInspirationPostsRequestAndBody() = runTest {
        val api = api { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/explore/inspiration/sessions", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue("\"id\":\"s1\"" in body, body)
            respond("""{"data":$inspirationSessionJson}""", HttpStatusCode.Created, json)
        }
        val result = api.startInspiration(StartInspirationRequest("s1"))
        assertEquals("s1", assertIs<ApiResult.Success<*>>(result).value.let { it as app.ransel.shared.dto.InspirationSessionDto }.id)
    }

    @Test
    fun answerAndFinishUseExpectedRoutes() = runTest {
        val seen = mutableListOf<String>()
        val api = api { request ->
            seen += "${request.method.value} ${request.url.encodedPath}"
            respond("""{"data":$inspirationSessionJson}""", HttpStatusCode.OK, json)
        }
        api.answerInspiration("s1", InspirationAnswerRequest(optionId = "a"))
        api.finishInspiration("s1")
        assertEquals(
            listOf("POST /api/v1/explore/inspiration/sessions/s1/answers", "POST /api/v1/explore/inspiration/sessions/s1/finish"),
            seen,
        )
    }

    @Test
    fun inspirationCreditAndRateLimitErrorsKeepCodesAndDetails() = runTest {
        val creditApi = api {
            respond("""{"error":{"code":"INSUFFICIENT_CREDITS","message":"No credits"}}""", HttpStatusCode.PaymentRequired, json)
        }
        val noCredits = assertIs<ApiResult.Failure>(creditApi.startInspiration(StartInspirationRequest("s1"))).error
        assertEquals(ErrorCode.INSUFFICIENT_CREDITS, noCredits.code)
        val limitedApi = api {
            respond("""{"error":{"code":"RATE_LIMITED","message":"Wait","details":{"retryAfterSeconds":"75"}}}""", HttpStatusCode.TooManyRequests, json)
        }
        val limited = assertIs<ApiResult.Failure>(limitedApi.startInspiration(StartInspirationRequest("s1"))).error
        assertEquals(ErrorCode.RATE_LIMITED, limited.code)
        assertEquals("75", limited.details?.get("retryAfterSeconds"))
    }
}

private fun fixedAuth(token: String) = object : ApiAuth {
    override fun accessToken() = token
    override suspend fun refreshAccessToken() = false
}
