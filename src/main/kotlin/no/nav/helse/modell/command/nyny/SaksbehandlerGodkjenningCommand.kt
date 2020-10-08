package no.nav.helse.modell.command.nyny

import no.nav.helse.api.OppgaveMediator
import no.nav.helse.modell.Oppgave
import no.nav.helse.modell.automatisering.Automatisering
import no.nav.helse.tildeling.ReservasjonDao
import org.slf4j.LoggerFactory
import java.util.*

internal class SaksbehandlerGodkjenningCommand(
    private val fødselsnummer: String,
    private val vedtaksperiodeId: UUID,
    private val godkjenningsbehovJson: String,
    private val reservasjonDao: ReservasjonDao,
    private val oppgaveMediator: OppgaveMediator,
    private val automatisering: Automatisering,
    private val hendelseId: UUID
) : Command {

    private companion object {
        private val logg = LoggerFactory.getLogger(SaksbehandlerGodkjenningCommand::class.java)
    }

    private val oppgave = Oppgave.avventerSaksbehandler(this::class.java.simpleName, vedtaksperiodeId)

    override fun execute(context: CommandContext): Boolean {
        if (automatisering.harBlittAutomatiskBehandlet(vedtaksperiodeId, hendelseId)) return true

        logg.info("Oppretter saksbehandleroppgave")
        reservasjonDao.hentReservasjonFor(fødselsnummer)?.let { reservasjon ->
            oppgaveMediator.tildel(oppgave, reservasjon.first, reservasjon.second)
        } ?: oppgaveMediator.nyOppgave(oppgave)

        return true
    }
}
