package no.nav.helse.modell.kommando

import no.nav.helse.mediator.oppgave.OppgaveDao
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.kommando.CommandContext.Companion.ferdigstill
import no.nav.helse.modell.utbetaling.UtbetalingDao
import org.slf4j.LoggerFactory
import java.util.UUID


internal class VurderVidereBehandlingAvGodkjenningsbehov(
    private val meldingId: UUID,
    private val utbetalingId: UUID,
    private val vedtaksperiodeId: UUID,
    private val utbetalingDao: UtbetalingDao,
    private val oppgaveDao: OppgaveDao,
    private val vedtakDao: VedtakDao,
): Command {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
    override fun execute(context: CommandContext): Boolean {
        if (utbetalingDao.erUtbetalingForkastet(utbetalingId)) {
            sikkerlogg.info("Ignorerer godkjenningsbehov med id=$meldingId for utbetalingId=$utbetalingId fordi utbetalingen er forkastet")
            return ferdigstill(context)
        }
        if (oppgaveDao.harGyldigOppgave(utbetalingId) || vedtakDao.erAutomatiskGodkjent(utbetalingId)) {
            sikkerlogg.info(
                "vedtaksperiodeId=$vedtaksperiodeId med utbetalingId=$utbetalingId har gyldig oppgave eller er automatisk godkjent. Ignorerer godkjenningsbehov med id=$meldingId",
            )
            return ferdigstill(context)
        }
        return true
    }
}
