package app.ransel.server.routes

import app.ransel.server.AppConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Domain verification for invite links (`https://<link host>/j/<code>`). These files are only read from the
 * host of the link, so that host's proxy must forward `/.well-known/` to this server. Values come from the
 * environment because they belong to the signing keys, not to the code.
 */
fun Route.wellKnownRoutes(config: AppConfig) {
    get("/.well-known/assetlinks.json") {
        if (config.androidCertSha256.isEmpty()) return@get call.respond(HttpStatusCode.NotFound)
        val body: JsonArray = buildJsonArray {
            add(
                buildJsonObject {
                    putJsonArray("relation") { add(JsonPrimitive("delegate_permission/common.handle_all_urls")) }
                    putJsonObject("target") {
                        put("namespace", "android_app")
                        put("package_name", config.androidPackage)
                        putJsonArray("sha256_cert_fingerprints") { config.androidCertSha256.forEach { add(JsonPrimitive(it)) } }
                    }
                },
            )
        }
        call.respondText(body.toString(), ContentType.Application.Json)
    }

    // Must be served as application/json, without an extension and without a redirect.
    get("/.well-known/apple-app-site-association") {
        if (config.iosAppIds.isEmpty()) return@get call.respond(HttpStatusCode.NotFound)
        val body: JsonObject = buildJsonObject {
            putJsonObject("applinks") {
                putJsonArray("details") {
                    add(
                        buildJsonObject {
                            putJsonArray("appIDs") { config.iosAppIds.forEach { add(JsonPrimitive(it)) } }
                            putJsonArray("components") { add(buildJsonObject { put("/", "/j/*") }) }
                        },
                    )
                }
            }
        }
        call.respondText(body.toString(), ContentType.Application.Json)
    }
}
