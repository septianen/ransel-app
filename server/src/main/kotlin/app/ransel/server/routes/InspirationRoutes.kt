package app.ransel.server.routes

import app.ransel.server.ai.InspirationService
import app.ransel.server.service.requireVerifiedEmail
import app.ransel.server.service.userId
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.dto.BuddyInterestRequest
import app.ransel.shared.dto.InspirationAnswerRequest
import app.ransel.shared.dto.InspirationEditChipRequest
import app.ransel.shared.dto.SaveIdeaRequest
import app.ransel.shared.dto.StartInspirationRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.acceptLanguage
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

/** Explore Inspirasi (PRD v0.8). Every route needs a bearer token and a verified email, like the rest of Explore. */
fun Route.inspirationRoutes(service: InspirationService) {
    post(ApiRoutes.INSPIRATION_SESSIONS) {
        val body = call.receive<StartInspirationRequest>()
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.Created, service.start(call.userId(), body, call.language()))
    }
    get("${ApiRoutes.INSPIRATION_SESSIONS}/{id}") {
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.get(call.userId(), call.parameters["id"].orEmpty()))
    }
    post("${ApiRoutes.INSPIRATION_SESSIONS}/{id}/answers") {
        val body = call.receive<InspirationAnswerRequest>()
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.answer(call.userId(), call.parameters["id"].orEmpty(), body))
    }
    post("${ApiRoutes.INSPIRATION_SESSIONS}/{id}/finish") {
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.finish(call.userId(), call.parameters["id"].orEmpty()))
    }
    post("${ApiRoutes.INSPIRATION_SESSIONS}/{id}/ask-again") {
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.askAgain(call.userId(), call.parameters["id"].orEmpty()))
    }
    patch("${ApiRoutes.INSPIRATION_SESSIONS}/{id}/profile") {
        val body = call.receive<InspirationEditChipRequest>()
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.editChip(call.userId(), call.parameters["id"].orEmpty(), body))
    }
    get(ApiRoutes.INSPIRATION_SAVED) {
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.savedIdeas(call.userId()))
    }
    post(ApiRoutes.INSPIRATION_SAVED) {
        val body = call.receive<SaveIdeaRequest>()
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.Created, service.save(call.userId(), body))
    }
    delete("${ApiRoutes.INSPIRATION_SAVED}/{id}") {
        requireVerifiedEmail(call.userId())
        service.delete(call.userId(), call.parameters["id"].orEmpty())
        call.respondNoContent()
    }
    post("${ApiRoutes.INSPIRATION_SAVED}/{id}/undo") {
        requireVerifiedEmail(call.userId())
        service.undoSave(call.userId(), call.parameters["id"].orEmpty())
        call.respondNoContent()
    }
    post("${ApiRoutes.INSPIRATION_SAVED}/{id}/restore") {
        requireVerifiedEmail(call.userId())
        service.restore(call.userId(), call.parameters["id"].orEmpty())
        call.respondNoContent()
    }
    post("${ApiRoutes.INSPIRATION_SAVED}/{id}/refresh") {
        requireVerifiedEmail(call.userId())
        call.respondData(HttpStatusCode.OK, service.refresh(call.userId(), call.parameters["id"].orEmpty()))
    }
    post(ApiRoutes.INSPIRATION_BUDDY) {
        val body = call.receive<BuddyInterestRequest>()
        requireVerifiedEmail(call.userId())
        service.buddyInterest(call.userId(), body)
        call.respondNoContent()
    }
}

/** "en-US,en;q=0.9" -> en; anything but English is Indonesian, the default UI language (same rule as Explore). */
private fun ApplicationCall.language(): String =
    if (request.acceptLanguage()?.trim()?.lowercase()?.startsWith("en") == true) "en" else "id"
