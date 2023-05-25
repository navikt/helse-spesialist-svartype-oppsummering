package no.nav.helse.spesialist.api.graphql.mutation

import com.expediagroup.graphql.server.operations.Mutation
import graphql.GraphQLError
import graphql.GraphqlErrorException.newErrorException
import graphql.execution.DataFetcherResult
import graphql.execution.DataFetcherResult.newResult
import graphql.schema.DataFetchingEnvironment
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveAlleredeTildelt
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDER_EPOST
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDLER_IDENT
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDLER_NAVN
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDLER_OID
import no.nav.helse.spesialist.api.graphql.ContextValues.TILGANGER
import no.nav.helse.spesialist.api.graphql.schema.Tildeling
import no.nav.helse.spesialist.api.tildeling.TildelingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TildelingMutation(
    private val tildelingService: TildelingService,
) : Mutation {

    private companion object {
        private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }

    @Suppress("unused")
    suspend fun opprettTildeling(
        oppgaveId: String,
        env: DataFetchingEnvironment,
    ): DataFetcherResult<Tildeling?> = withContext(Dispatchers.IO) {
        val tilganger = env.graphQlContext.get<SaksbehandlerTilganger>(TILGANGER.key)
        val saksbehandlerOid = UUID.fromString(env.graphQlContext.get(SAKSBEHANDLER_OID.key))
        val epostadresse = env.graphQlContext.get<String>(SAKSBEHANDER_EPOST.key)
        val navn = env.graphQlContext.get<String>(SAKSBEHANDLER_NAVN.key)
        val ident = env.graphQlContext.get<String>(SAKSBEHANDLER_IDENT.key)
        val tildeling = try {
            tildelingService.tildelOppgaveTilSaksbehandler(
                oppgaveId = oppgaveId.toLong(),
                saksbehandlerreferanse = saksbehandlerOid,
                epostadresse = epostadresse,
                navn = navn,
                ident = ident,
                saksbehandlerTilganger = tilganger
            )
        } catch (e: OppgaveAlleredeTildelt) {
            return@withContext newResult<Tildeling?>().error(alleredeTildeltError(e)).build()
        } catch (e: RuntimeException) {
            return@withContext newResult<Tildeling?>().error(getUpdateError(oppgaveId)).build()
        }

        newResult<Tildeling?>().data(
            Tildeling(
                navn = tildeling.navn,
                oid = tildeling.oid.toString(),
                epost = tildeling.epost,
                reservert = tildeling.påVent
            )
        ).build()
    }

    @Suppress("unused")
    suspend fun fjernTildeling(oppgaveId: String): DataFetcherResult<Boolean> = withContext(Dispatchers.IO) {
        val result = tildelingService.fjernTildeling(oppgaveId.toLong())
        newResult<Boolean>().data(result).build()
    }

    private fun getUpdateError(oppgaveId: String): GraphQLError {
        val message = "Kunne ikke tildele oppgave med oppgaveId=$oppgaveId"
        sikkerlogg.error(message)
        return newErrorException()
            .message(message)
            .extensions(mapOf("code" to 500))
            .build()
    }

    private fun alleredeTildeltError(error: OppgaveAlleredeTildelt): GraphQLError {
        return newErrorException()
            .message("Oppgave allerede tildelt")
            .extensions(mapOf("code" to error.httpkode, "tildeltNavn" to error.tildeling.navn))
            .build()
    }
}
