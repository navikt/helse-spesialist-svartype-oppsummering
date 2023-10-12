package no.nav.helse.spesialist.api.graphql.query

import com.expediagroup.graphql.server.operations.Query
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDLER
import no.nav.helse.spesialist.api.graphql.schema.BehandletOppgave
import no.nav.helse.spesialist.api.graphql.schema.FerdigstiltOppgave
import no.nav.helse.spesialist.api.graphql.schema.OppgaveTilBehandling
import no.nav.helse.spesialist.api.graphql.schema.Oppgavesortering
import no.nav.helse.spesialist.api.graphql.schema.tilFerdigstilteOppgaver
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi
import no.nav.helse.spesialist.api.tildeling.Oppgavehåndterer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OppgaverQuery(private val oppgaveApiDao: OppgaveApiDao, private val oppgavehåndterer: Oppgavehåndterer) : Query {

    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

    @Suppress("unused")
    fun behandledeOppgaver(
        behandletAvIdent: String? = null,
        behandletAvOid: String,
        fom: String?,
    ): DataFetcherResult<List<FerdigstiltOppgave>> {
        val fraOgMed = try {
            LocalDate.parse(fom)
        } catch (_: Exception) {
            null
        }

        val oppgaver =
            oppgaveApiDao.hentBehandledeOppgaver(UUID.fromString(behandletAvOid), fraOgMed)
                .tilFerdigstilteOppgaver()

        return DataFetcherResult.newResult<List<FerdigstiltOppgave>>().data(oppgaver).build()
    }

    @Suppress("unused")
    suspend fun behandledeOppgaverIDag(
        env: DataFetchingEnvironment,
    ): DataFetcherResult<List<BehandletOppgave>> {
        val saksbehandler = env.graphQlContext.get<Lazy<SaksbehandlerFraApi>>(SAKSBEHANDLER.key).value
        val behandledeOppgaver = withContext(Dispatchers.IO) {
            oppgavehåndterer.behandledeOppgaver(saksbehandler)
        }

        return DataFetcherResult.newResult<List<BehandletOppgave>>().data(behandledeOppgaver).build()
    }

    suspend fun oppgaver(
        startIndex: Int? = 0,
        pageSize: Int? = null,
        sortering: List<Oppgavesortering>? = emptyList(),
        env: DataFetchingEnvironment,
    ): DataFetcherResult<List<OppgaveTilBehandling>> {
        sikkerLogg.info("Henter OppgaverTilBehandling")
        val startTrace = startSporing(env)
        val saksbehandler = env.graphQlContext.get<Lazy<SaksbehandlerFraApi>>(SAKSBEHANDLER.key).value
        val oppgaver = withContext(Dispatchers.IO) {
            oppgavehåndterer.oppgaver(saksbehandler, startIndex ?: 0, pageSize ?: Int.MAX_VALUE, sortering ?: emptyList())
        }
        avsluttSporing(startTrace)

        return DataFetcherResult.newResult<List<OppgaveTilBehandling>>().data(oppgaver).build()
    }

    private fun startSporing(env: DataFetchingEnvironment): Long {
        val hvem = env.graphQlContext.get<String>("saksbehandlerNavn")
        sikkerLogg.trace("Henter oppgaver for $hvem")
        return System.nanoTime()
    }

    private fun avsluttSporing(start: Long) {
        val tidBrukt = Duration.ofNanos(System.nanoTime() - start)
        sikkerLogg.trace("Hentet oppgaver, det tok ${tidBrukt.toMillis()} ms")
    }
}
