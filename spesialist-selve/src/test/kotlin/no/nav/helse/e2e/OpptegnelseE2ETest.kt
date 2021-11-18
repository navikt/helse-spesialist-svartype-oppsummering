package no.nav.helse.e2e

import AbstractE2ETest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.abonnement.OpptegnelseDto
import no.nav.helse.abonnement.OpptegnelseMediator
import no.nav.helse.abonnement.opptegnelseApi
import no.nav.helse.mediator.api.AbstractApiTest
import no.nav.helse.mediator.api.AbstractApiTest.Companion.authentication
import no.nav.helse.person.Adressebeskyttelse
import no.nav.helse.person.Kjønn
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

private class OpptegnelseE2ETest : AbstractE2ETest() {
    private val SAKSBEHANDLER_ID = UUID.randomUUID()

    @Test
    fun `Ved abonnering får du et nytt abonnement`() {
        val utbetalingId = UUID.randomUUID()
        setupPerson()
        setupArbeidsgiver()
        setupSaksbehandler()
        setupUtbetalingIdKopling(utbetalingId)

        val respons =
            AbstractApiTest.TestServer { opptegnelseApi(OpptegnelseMediator(opptegnelseApiDao, abonnementDao)) }
                .withAuthenticatedServer {
                    it.post<HttpResponse>("/api/opptegnelse/abonner/$AKTØR") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        authentication(SAKSBEHANDLER_ID)
                    }
                }

        assertEquals(HttpStatusCode.OK, respons.status)

        testRapid.sendTestMessage(meldingsfabrikk.lagUtbetalingEndret(
            utbetalingId = utbetalingId,
            type = "ANNULLERING",
            status = "UTBETALING_FEILET"
        ))

        val opptegnelser =
            AbstractApiTest.TestServer { opptegnelseApi(OpptegnelseMediator(opptegnelseApiDao, abonnementDao)) }
                .withAuthenticatedServer {
                    it.get<HttpResponse>("/api/opptegnelse/hent") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        authentication(SAKSBEHANDLER_ID)
                    }.call.receive<List<OpptegnelseDto>>()
                }

        assertEquals(1, opptegnelser.size)

        val oppdateringer =
            AbstractApiTest.TestServer { opptegnelseApi(OpptegnelseMediator(opptegnelseApiDao, abonnementDao)) }
                .withAuthenticatedServer {
                    it.get<HttpResponse>("/api/opptegnelse/hent/${opptegnelser[0].sekvensnummer}") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        authentication(SAKSBEHANDLER_ID)
                    }.call.receive<List<OpptegnelseDto>>()
                }

        assertEquals(0, oppdateringer.size)
    }

    private fun setupUtbetalingIdKopling(utbetalingId : UUID) {
        utbetalingDao.opprettKobling(UUID.randomUUID(), utbetalingId)
    }

    private fun setupPerson() {
        val personinfoId = personDao.insertPersoninfo(
            "Harald",
            "Mellomnavn",
            "Rex",
            LocalDate.now().minusYears(20),
            Kjønn.Ukjent,
            Adressebeskyttelse.Ugradert
        )
        val string = """{ "node": "1234" }"""
        val json = JsonMessage(string, MessageProblems(string))
        json["key"] = "string"
        val enhetId = 1219
        val infoTrygdutbetalingerId = personDao.insertInfotrygdutbetalinger(json["key"])
        personDao.insertPerson(
            FØDSELSNUMMER,
            AKTØR,
            personinfoId,
            enhetId,
            infoTrygdutbetalingerId
        )
    }

    private fun setupArbeidsgiver() {
        arbeidsgiverDao.insertArbeidsgiver(
            "123456789",
            "Bedrift AS",
            listOf("BEDRIFTSGREIER OG STÆSJ")
        )
    }

    private fun setupSaksbehandler() {
        saksbehandlerDao.opprettSaksbehandler(
            SAKSBEHANDLER_ID,
            "Saksbehandler Saksbehandlersen",
            "saksbehandler@nav.no",
            "Z999999"
        )
    }
}
