package app.ransel.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.calllogging.processingTimeMillis
import org.slf4j.event.Level

/** One line per request: method, path, status, duration, error.code. Never bodies, headers or tokens. */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val code = call.attributes.getOrNull(ErrorCodeKey) ?: "-"
            "${call.request.origin.method.value} ${call.request.origin.uri.substringBefore('?')} " +
                "-> ${call.response.status()?.value ?: "-"} in ${call.processingTimeMillis()}ms error.code=$code"
        }
    }
}
