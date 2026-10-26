package app.ransel.server

import app.ransel.server.AppConfig
import app.ransel.server.security.EmailSender
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

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

    /** Runs raw SQL against the test database (to age invites, etc.). */
    fun exec(sql: String) {
        val c = config()
        DriverManager.getConnection(c.databaseUrl, c.databaseUser, c.databasePassword).use { conn ->
            conn.createStatement().use { it.execute(sql) }
        }
    }

    /** First column of every row, as strings (to look at what a test left in the database). */
    fun column(sql: String): List<String> {
        val c = config()
        return DriverManager.getConnection(c.databaseUrl, c.databaseUser, c.databasePassword).use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } }
        }
    }

    fun expireResetTokens() {
        val c = config()
        DriverManager.getConnection(c.databaseUrl, c.databaseUser, c.databasePassword).use { conn ->
            conn.createStatement().use { it.execute("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'") }
        }
    }

    /** Removes every trip and what hangs off it. The seeded dummy user stays. */
    fun reset() {
        val c = config()
        DriverManager.getConnection(c.databaseUrl, c.databaseUser, c.databasePassword).use { conn ->
            val exists = conn.createStatement().executeQuery("select to_regclass('public.trips')").use { rs -> rs.next() && rs.getString(1) != null }
            if (exists) {
                conn.createStatement().use { it.execute("TRUNCATE trips CASCADE") }
                // not tied to a trip, but it would leak results from one test into the next
                conn.createStatement().use { it.execute("TRUNCATE recommendation_cache, explore_suggestions_cache, place_detail_cache, place_autocomplete_cache, place_details_cache") }
                conn.createStatement().use { it.execute("TRUNCATE credit_vouchers CASCADE") }
                conn.createStatement().use { it.execute("TRUNCATE admin_audit_log") }
                // Real accounts go (their refresh tokens cascade); the seeded dummy user has no email and stays.
                conn.createStatement().use { it.execute("DELETE FROM users WHERE email IS NOT NULL") }
            }
        }
    }
}

val TestJson = Json { ignoreUnknownKeys = true }

/** Collects "sent" emails so tests can read the reset and verification links. */
object TestEmails : EmailSender {
    enum class Kind { RESET, VERIFY, INVITE }

    data class Sent(val to: String, val link: String, val kind: Kind)

    val sent = CopyOnWriteArrayList<Sent>()

    override suspend fun sendPasswordReset(toEmail: String, displayName: String, link: String) {
        sent += Sent(toEmail, link, Kind.RESET)
    }

    override suspend fun sendEmailVerification(toEmail: String, displayName: String, link: String) {
        sent += Sent(toEmail, link, Kind.VERIFY)
    }

    override suspend fun sendAccountInvite(toEmail: String, displayName: String, link: String) {
        sent += Sent(toEmail, link, Kind.INVITE)
    }

    fun count(kind: Kind) = sent.count { it.kind == kind }

    /** Both emails go out in the background; wait for the newest one of [kind]. */
    suspend fun awaitLink(to: String, kind: Kind = Kind.RESET): String {
        repeat(100) {
            sent.lastOrNull { it.to.equals(to, ignoreCase = true) && it.kind == kind }?.let { return it.link }
            delay(50)
        }
        error("no $kind email for $to")
    }
}

data class Session(val token: String?, val refreshToken: String?, val userId: String)

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

/**
 * Registers a new account and makes it the one this [Api] acts as. [verified] marks the email verified straight in the
 * database (most tests are about something else); pass false to test the unverified state.
 */
suspend fun Api.signUp(
    email: String = "user-${UUID.randomUUID()}@example.com",
    password: String = "Passw0rdOk",
    displayName: String = "Tester",
    verified: Boolean = true,
): HttpResponse {
    token = null
    val response = post("/auth/register", """{"email":"$email","password":"$password","displayName":"$displayName"}""")
    if (response.status.value == 201) {
        val session = response.data().jsonObject
        token = session.getValue("accessToken").jsonPrimitive.content
        refreshToken = session.getValue("refreshToken").jsonPrimitive.content
        userId = session.getValue("user").jsonObject.getValue("id").jsonPrimitive.content
        if (verified) TestDb.exec("UPDATE users SET email_verified_at = now() WHERE id = '$userId'")
    }
    return response
}

suspend fun HttpResponse.jsonBody(): JsonObject = TestJson.parseToJsonElement(bodyAsText()).jsonObject
suspend fun HttpResponse.data(): JsonElement = jsonBody().getValue("data")
suspend fun HttpResponse.errorCode(): String = jsonBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content

/** Starts the app on a clean database and runs [block] as a freshly registered user. */
fun apiTest(
    config: AppConfig = TestDb.config(),
    ai: app.ransel.server.ai.AiServices? = null,
    fx: app.ransel.server.ai.FxRates? = null,
    push: app.ransel.server.push.PushSender? = null,
    linkFetcher: app.ransel.server.net.LinkFetcher? = null,
    block: suspend Api.() -> Unit,
) {
    TestDb.reset()
    TestEmails.sent.clear()
    testApplication {
        application { module(config, TestEmails, ai, fx, push, linkFetcher) }
        val client = createClient { install(ContentNegotiation) { json(TestJson) } }
        runBlocking {
            val api = Api(client)
            api.signUp()
            api.block()
        }
    }
}

/** The member id of this account in a trip: what expenses, splits and settlements point at. */
fun Session.memberId(tripId: String): String = app.ransel.shared.domain.memberIdFor(tripId, userId)

/** Remember who is signed in, so a test can switch between accounts. */
fun Api.session() = Session(token, refreshToken, userId)

fun Api.use(session: Session) {
    token = session.token
    refreshToken = session.refreshToken
    userId = session.userId
}
