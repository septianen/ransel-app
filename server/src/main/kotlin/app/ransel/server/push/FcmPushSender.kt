package app.ransel.server.push

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Firebase Cloud Messaging, HTTP v1. No Firebase SDK: the service-account JSON (from the environment, never from git)
 * signs a short JWT that is exchanged for an access token, which is cached until a minute before it expires.
 */
class FcmPushSender(
    private val http: HttpClient,
    private val projectId: String,
    serviceAccountJson: String,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : PushSender {
    private val log = LoggerFactory.getLogger(FcmPushSender::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val account = json.parseToJsonElement(serviceAccountJson).jsonObject
    private val clientEmail = account.getValue("client_email").jsonPrimitive.content
    private val tokenUri = account["token_uri"]?.jsonPrimitive?.contentOrNull ?: "https://oauth2.googleapis.com/token"
    private val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.getMimeDecoder().decode(
                account.getValue("private_key").jsonPrimitive.content
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\s".toRegex(), ""),
            ),
        ),
    )

    private val lock = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L

    override suspend fun send(token: String, message: PushMessage): PushResult {
        val bearer = runCatching { accessToken() }.getOrElse {
            log.warn("push.failed reason=auth {}", it.javaClass.simpleName)
            return PushResult.RETRY
        }
        val body = buildJsonObject {
            put("message", buildJsonObject {
                put("token", token)
                put("notification", buildJsonObject { put("title", message.title); put("body", message.body) })
                put("data", buildJsonObject { message.data.forEach { (k, v) -> put(k, v) } })
                put("android", buildJsonObject { put("priority", "HIGH") })
            })
        }
        val response = try {
            http.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                bearerAuth(bearer)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: Exception) {
            log.warn("push.failed reason=network {}", e.javaClass.simpleName)
            return PushResult.RETRY
        }
        val status = response.status.value
        if (status in 200..299) return PushResult.OK
        val text = response.bodyAsText()
        val errorCode = runCatching {
            json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("details")?.jsonArray
                ?.firstNotNullOfOrNull { it.jsonObject["errorCode"]?.jsonPrimitive?.contentOrNull }
        }.getOrNull()
        return when {
            status == 404 || errorCode == "UNREGISTERED" -> PushResult.INVALID_TOKEN
            status == 400 && errorCode == "INVALID_ARGUMENT" -> PushResult.INVALID_TOKEN
            status == 401 || status == 403 -> {
                lock.withLock { accessToken = null }
                log.warn("push.failed reason=auth status={}", status)
                PushResult.RETRY
            }
            else -> {
                log.warn("push.failed status={} code={}", status, errorCode)
                PushResult.RETRY
            }
        }
    }

    private suspend fun accessToken(): String = lock.withLock {
        val cached = accessToken
        if (cached != null && now() < accessTokenExpiresAt - 60) return cached
        val issued = now()
        val assertion = signedJwt(issued)
        val response = http.submitForm(
            url = tokenUri,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )
        check(response.status.value in 200..299) { "token exchange failed: ${response.status.value}" }
        val parsed = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = parsed.getValue("access_token").jsonPrimitive.content
        accessToken = token
        accessTokenExpiresAt = issued + (parsed["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600)
        token
    }

    private fun signedJwt(issuedAt: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun part(o: JsonObject) = encoder.encodeToString(o.toString().toByteArray())
        val header = part(buildJsonObject { put("alg", "RS256"); put("typ", "JWT") })
        val claims = part(
            buildJsonObject {
                put("iss", clientEmail)
                put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                put("aud", tokenUri)
                put("iat", issuedAt)
                put("exp", issuedAt + 3600)
            },
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update("$header.$claims".toByteArray())
            sign()
        }
        return "$header.$claims.${encoder.encodeToString(signature)}"
    }
}
