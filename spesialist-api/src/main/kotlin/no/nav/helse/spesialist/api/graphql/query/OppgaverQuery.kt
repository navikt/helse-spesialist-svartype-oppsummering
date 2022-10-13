package no.nav.helse.spesialist.api.graphql.query

import com.expediagroup.graphql.server.operations.Query
import graphql.GraphQLError
import graphql.GraphqlErrorException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.helse.spesialist.api.graphql.schema.FerdigstiltOppgave
import no.nav.helse.spesialist.api.graphql.schema.Oppgaver
import no.nav.helse.spesialist.api.graphql.schema.Paginering
import no.nav.helse.spesialist.api.graphql.schema.tilFerdigstilteOppgaver
import no.nav.helse.spesialist.api.graphql.schema.tilOppgaver
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.graphql.schema.OppgaveForOversiktsvisning
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.oppgave.experimental.OppgaveService

class OppgaverQuery(private val oppgaveApiDao: OppgaveApiDao, private val oppgaveService: OppgaveService) : Query {

    @Suppress("unused")
    fun behandledeOppgaver(
        behandletAvIdent: String,
        behandletAvOid: String,
        fom: String?
    ): DataFetcherResult<List<FerdigstiltOppgave>> {
        val fraOgMed = try {
            LocalDate.parse(fom)
        } catch (_: Exception) {
            null
        }

        val oppgaver =
            oppgaveApiDao.hentBehandledeOppgaver(behandletAvIdent, UUID.fromString(behandletAvOid), fraOgMed)
                .tilFerdigstilteOppgaver()

        return DataFetcherResult.newResult<List<FerdigstiltOppgave>>().data(oppgaver).build()
    }

    @Suppress("unused")
    fun alleOppgaver(env: DataFetchingEnvironment): DataFetcherResult<List<OppgaveForOversiktsvisning>> {
        val tilganger = env.graphQlContext.get<SaksbehandlerTilganger>("tilganger")
        val oppgaver = oppgaveApiDao.finnOppgaver(tilganger).tilOppgaver()

        return DataFetcherResult.newResult<List<OppgaveForOversiktsvisning>>().data(oppgaver).build()
    }

    @Suppress("unused")
    fun oppgaver(first: Int, after: String?, env: DataFetchingEnvironment): DataFetcherResult<Oppgaver> {
        val cursor = try {
            LocalDateTime.parse(after)
        } catch (exception: Exception) {
            return DataFetcherResult.newResult<Oppgaver>().error(getParseCursorError()).build()
        }

        val tilganger = env.graphQlContext.get<SaksbehandlerTilganger>("tilganger")

        val paginering = oppgaveService.hentOppgaver(tilganger = tilganger, fra = cursor, antall = first)

        val oppgaver = Oppgaver(
            oppgaver = paginering.elementer.tilOppgaver(),
            paginering = Paginering(
                peker = paginering.peker.format(DateTimeFormatter.ISO_DATE_TIME),
                side = paginering.nåværendeSide,
                antallSider = paginering.totaltAntallSider,
                elementerPerSide = paginering.sidestørrelse,
            )
        )

        return DataFetcherResult.newResult<Oppgaver>().data(oppgaver).build()
    }

    private fun getParseCursorError(): GraphQLError = GraphqlErrorException.newErrorException()
        .message("Mottok cursor med uventet format")
        .extensions(mapOf("code" to 400))
        .build()

}
