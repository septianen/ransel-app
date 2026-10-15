package app.ransel.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

/** One real Postgres 16 per test JVM (docker required); tables are truncated before every test. */
object TestDb {
    private val container: PostgreSQLContainer by lazy { PostgreSQLContainer("postgres:16").also { it.start() } }

    fun config() = AppConfig(
        port = 0,
        databaseUrl = container.jdbcUrl,
        databaseUser = container.username,
        databasePassword = container.password,
        corsOrigins = listOf("http://localhost:8081"),
        bcryptCost = 4,
        authRateLimitPerMinute = 1000,
    )

    /** Removes every trip and what hangs off it. The seeded dummy user stays. */
    fun reset() {
        val c = config()
        DriverManager.getConnection(c.databaseUrl, c.databaseUser, c.databasePassword).use { conn ->
            val exists = conn.createStatement().executeQuery("select to_regclass('public.trips')").use { rs -> rs.next() && rs.getString(1) != null }
            if (exists) {
                conn.createStatement().use { it.execute("TRUNCATE trips CASCADE") }
                // Real accounts go (their refresh tokens cascade); the seeded dummy user has no email and stays.
                conn.createStatement().use { it.execute("DELETE FROM users WHERE email IS NOT NULL") }
            }
        }
    }
}

val TestJson = Json { ignoreUnknownKeys = true }

class Api(val client: HttpClient) {
    /** Access token sent as Bearer; null = anonymous request. */
    var token: String? = null
    var refreshToken: String? = null
    var userId: String = ""

    suspend fun send(method: HttpMethod, path: String, body: String? = null): HttpResponse =
        client.request("/api/v1$path") {
            this.method = method
            token?.let { bearerAuth(it) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    suspend fun get(path: String) = send(HttpMethod.Get, path)
    suspend fun post(path: String, body: String? = null) = send(HttpMethod.Post, path, body)
    suspend fun put(path: String, body: String) = send(HttpMethod.Put, path, body)
    suspend fun patch(path: String, body: String) = send(HttpMethod.Patch, path, body)
    suspend fun delete(path: String) = send(HttpMethod.Delete, path)
}

/** Registers a new account and makes it the one this [Api] acts as. */
suspend fun Api.signUp(
    email: String = "user-${UUID.randomUUID()}@example.com",
    password: String = "Passw0rdOk",
    displayName: String = "Tester",
): HttpResponse {
    token = null
    val response = post("/auth/register", """{"email":"$email","password":"$password","displayName":"$displayName"}""")
    if (response.status.value == 201) {
        val session = response.data().jsonObject
        token = session.getValue("accessToken").jsonPrimitive.content
        refreshToken = session.getValue("refreshToken").jsonPrimitive.content
        userId = session.getValue("user").jsonObject.getValue("id").jsonPrimitive.content
    }
    return response
}

suspend fun HttpResponse.jsonBody(): JsonObject = TestJson.parseToJsonElement(bodyAsText()).jsonObject
suspend fun HttpResponse.data(): JsonElement = jsonBody().getValue("data")
suspend fun HttpResponse.errorCode(): String = jsonBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content

/** Starts the app on a clean database and runs [block] as a freshly registered user. */
fun apiTest(config: AppConfig = TestDb.config(), block: suspend Api.() -> Unit) {
    TestDb.reset()
    testApplication {
        application { module(config) }
        val client = createClient { install(ContentNegotiation) { json(TestJson) } }
        runBlocking {
            val api = Api(client)
            api.signUp()
            api.block()
        }
    }
}
