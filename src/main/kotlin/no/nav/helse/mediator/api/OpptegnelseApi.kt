package no.nav.helse.mediator.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import no.nav.helse.mediator.OpptegnelseMediator
import java.util.*

internal fun Route.opptegnelseApi(opptegnelseMediator: OpptegnelseMediator) {
    post("/api/opptegnelse/abonner/{aktørId}") {
        val saksbehandlerreferanse = getSaksbehandlerOid()
        val aktørId = requireNotNull(call.parameters["aktørId"]?.toLong()) { "Ugyldig aktørId i path parameter" }

        opptegnelseMediator.opprettAbonnement(saksbehandlerreferanse, aktørId)
        call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
    }

    get("/api/opptegnelse/hent/{sisteSekvensId}") {
        val saksbehandlerreferanse = getSaksbehandlerOid()
        val sisteSekvensId =
            requireNotNull(call.parameters["sisteSekvensId"]?.toInt()) { "Ugyldig siste seksvensid i path parameter" }
        val opptegnelser = opptegnelseMediator.hentAbonnerteOpptegnelser(saksbehandlerreferanse, sisteSekvensId)

        call.respond(HttpStatusCode.OK, opptegnelser)
    }

    get("/api/opptegnelse/hent") {
        val saksbehandlerreferanse = getSaksbehandlerOid()
        val opptegnelser = opptegnelseMediator.hentAbonnerteOpptegnelser(saksbehandlerreferanse)

        call.respond(HttpStatusCode.OK, opptegnelser)
    }
}

private fun PipelineContext<Unit, ApplicationCall>.getSaksbehandlerOid(): UUID {
    val accessToken = requireNotNull(call.principal<JWTPrincipal>()) { "mangler access token" }
    return UUID.fromString(accessToken.payload.getClaim("oid").asString())
}
