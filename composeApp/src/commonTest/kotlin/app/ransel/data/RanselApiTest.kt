package app.ransel.data

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.remote.getOrNull
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreateTripRequest
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
                    """{"data":[{"tripId":"t1","userId":"u1","displayName":"Septian","email":"s@x.id","role":"OWNER","joinedAt":"2026-10-13T05:30:00Z"}]}""",
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
}
