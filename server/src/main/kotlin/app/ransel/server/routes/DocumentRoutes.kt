package app.ransel.server.routes

import app.ransel.server.plugins.ApiJson
import app.ransel.server.plugins.PhotoRateLimit
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.DocumentService
import app.ransel.server.service.TripTemplateService
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.CreateDocumentRequest
import app.ransel.shared.dto.CreateTripTemplateRequest
import app.ransel.shared.dto.MAX_DOCUMENT_BYTES
import app.ransel.shared.dto.PatchDocumentRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** Trip documents (behind authenticate). A link is JSON; a file is multipart `meta` + `file`. */
fun Route.documentRoutes(documents: DocumentService) {
    route("${ApiRoutes.TRIPS}/{id}/documents") {
        get { call.respondData(HttpStatusCode.OK, documents.list(call.userId(), call.tripId())) }
        post {
            if (call.request.contentType().match(ContentType.MultiPart.FormData)) {
                var meta: CreateDocumentRequest? = null
                var fileName = ""
                var mime: String? = null
                var bytes: ByteArray? = null
                // one byte over the limit is enough to know the file is too large
                call.receiveMultipart(formFieldLimit = MAX_DOCUMENT_BYTES + 64 * 1024).forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "meta") meta = runCatching { ApiJson.decodeFromString<CreateDocumentRequest>(part.value) }.getOrNull()
                        is PartData.FileItem -> if (part.name == "file") {
                            fileName = part.originalFileName.orEmpty()
                            mime = part.contentType?.toString()
                            bytes = part.provider().readRemaining(MAX_DOCUMENT_BYTES + 1).readByteArray()
                        }
                        else -> Unit
                    }
                    part.dispose()
                }
                val request = meta ?: throw validationFailed(mapOf("meta" to "REQUIRED"))
                val content = bytes ?: throw validationFailed(mapOf("file" to "REQUIRED"))
                call.respondData(HttpStatusCode.Created, documents.createFile(call.userId(), call.tripId(), request, fileName, mime, content))
            } else {
                val body = call.receive<CreateDocumentRequest>()
                call.respondData(HttpStatusCode.Created, documents.createLink(call.userId(), call.tripId(), body))
            }
        }
        patch("/{docId}") {
            val body = call.receive<PatchDocumentRequest>()
            call.respondData(HttpStatusCode.OK, documents.patch(call.userId(), call.tripId(), call.pathUuid("docId", "Document"), body))
        }
        delete("/{docId}") {
            documents.delete(call.userId(), call.tripId(), call.pathUuid("docId", "Document"))
            call.respondNoContent()
        }
        get("/{docId}/download") {
            call.respondData(HttpStatusCode.OK, documents.download(call.userId(), call.tripId(), call.pathUuid("docId", "Document")))
        }
    }
    route(ApiRoutes.TRIP_TEMPLATES) {
        get { call.respondData(HttpStatusCode.OK, TripTemplateService.list(call.userId())) }
        post {
            val body = call.receive<CreateTripTemplateRequest>()
            call.respondData(HttpStatusCode.Created, TripTemplateService.create(call.userId(), body))
        }
        delete("/{templateId}") {
            TripTemplateService.delete(call.userId(), call.pathUuid("templateId", "Template"))
            call.respondNoContent()
        }
    }
}

/** Public: the signed link from `/download` (15 minutes); no bearer, so a browser or the system viewer can open it. */
fun Route.documentFileRoutes(documents: DocumentService) {
    rateLimit(PhotoRateLimit) {
        get(ApiRoutes.documentFile("{docId}")) {
            val id = runCatching { java.util.UUID.fromString(call.parameters["docId"]) }.getOrNull()
            val file = id?.let { documents.file(it, call.request.queryParameters["exp"]?.toLongOrNull(), call.request.queryParameters["sig"]) }
            if (file == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val (path, mime, name) = file
            call.response.header(HttpHeaders.ContentType, mime)
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, name).toString())
            call.response.header(HttpHeaders.CacheControl, "private, max-age=900")
            call.respondFile(path.toFile())
        }
    }
}
