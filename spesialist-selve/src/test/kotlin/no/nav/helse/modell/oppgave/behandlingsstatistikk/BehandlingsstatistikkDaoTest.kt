package no.nav.helse.modell.oppgave.behandlingsstatistikk

import DatabaseIntegrationTest
import no.nav.helse.modell.vedtaksperiode.Inntektskilde
import no.nav.helse.modell.vedtaksperiode.Periodetype
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.spesialist.api.behandlingsstatistikk.BehandlingsstatistikkType as BehandlingsstatistikkTypeForApi

internal class BehandlingsstatistikkDaoTest : DatabaseIntegrationTest() {

    private val NOW = LocalDate.now()

    @Test
    fun `en periode til godkjenning`() {
        nyPerson()
        val dto = behandlingsstatistikkDao.oppgavestatistikk(NOW)
        assertEquals(0, dto.fullførteBehandlinger.totalt)
        assertEquals(0, dto.fullførteBehandlinger.automatisk)
        assertEquals(0, dto.fullførteBehandlinger.manuelt.totalt)
        assertEquals(0, dto.fullførteBehandlinger.annullert)
        assertEquals(0, dto.tildelteOppgaver.totalt)
        assertEquals(0, dto.tildelteOppgaver.perPeriodetype.size)
        assertEquals(1, dto.oppgaverTilGodkjenning.totalt)
        assertEquals(1, dto.oppgaverTilGodkjenning.perPeriodetype.size)
        assertEquals(1, dto.oppgaverTilGodkjenning.perPeriodetype[BehandlingsstatistikkTypeForApi.FØRSTEGANGSBEHANDLING])
    }

    @Test
    fun `antall tildelte oppgaver`() {
        nyPerson()
        opprettSaksbehandler()
        tildelingDao.opprettTildeling(oppgaveId, SAKSBEHANDLER_OID)
        val dto = behandlingsstatistikkDao.oppgavestatistikk(NOW)
        assertEquals(1, dto.tildelteOppgaver.totalt)
        assertEquals(1, dto.tildelteOppgaver.perPeriodetype[BehandlingsstatistikkTypeForApi.FØRSTEGANGSBEHANDLING])
    }

    @Test
    fun antallManuelleGodkjenninger() {
        nyPerson()
        oppgaveDao.updateOppgave(oppgaveId, Oppgavestatus.Ferdigstilt)
        val dto = behandlingsstatistikkDao.oppgavestatistikk(NOW)
        assertEquals(1, dto.fullførteBehandlinger.totalt)
        assertEquals(1, dto.fullførteBehandlinger.manuelt.totalt)
        assertEquals(0, dto.fullførteBehandlinger.automatisk)
        assertEquals(0, dto.fullførteBehandlinger.annullert)
    }

    @Test
    fun antallAutomatiskeGodkjenninger() {
        nyPersonMedAutomatiskVedtak()
        val dto = behandlingsstatistikkDao.oppgavestatistikk(NOW)
        assertEquals(1, dto.fullførteBehandlinger.totalt)
        assertEquals(0, dto.fullførteBehandlinger.manuelt.totalt)
        assertEquals(1, dto.fullførteBehandlinger.automatisk)
        assertEquals(0, dto.fullførteBehandlinger.annullert)
    }

    @Test
    fun antallAnnulleringer() {
        opprettSaksbehandler()
        utbetalingDao.nyAnnullering(LocalDateTime.now(), SAKSBEHANDLER_OID)
        val dto = behandlingsstatistikkDao.oppgavestatistikk(NOW)
        assertEquals(1, dto.fullførteBehandlinger.totalt)
        assertEquals(0, dto.fullførteBehandlinger.manuelt.totalt)
        assertEquals(1, dto.fullførteBehandlinger.annullert)
        assertEquals(0, dto.fullførteBehandlinger.automatisk)
    }

    @Test
    fun `flere periodetyper`() {
        nyPerson()
        nyVedtaksperiode(Periodetype.FORLENGELSE)
        val dto = behandlingsstatistikkDao.oppgavestatistikk(NOW)
        assertEquals(2, dto.oppgaverTilGodkjenning.totalt)
        assertEquals(1, dto.oppgaverTilGodkjenning.perPeriodetype[BehandlingsstatistikkTypeForApi.FØRSTEGANGSBEHANDLING])
        assertEquals(1, dto.oppgaverTilGodkjenning.perPeriodetype[BehandlingsstatistikkTypeForApi.FORLENGELSE])
    }

    @Test
    fun `tar ikke med innslag som er eldre enn dato som sendes inn for fullførte behandlinger`() {
        nyPersonMedAutomatiskVedtak()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)
        val fremtidigDato = NOW.plusDays(1)
        val dto = behandlingsstatistikkDao.oppgavestatistikk(fremtidigDato)
        assertEquals(0, dto.fullførteBehandlinger.totalt)
        assertEquals(0, dto.fullførteBehandlinger.annullert)
        assertEquals(0, dto.fullførteBehandlinger.manuelt.totalt)
        assertEquals(0, dto.fullførteBehandlinger.automatisk)
        assertEquals(1, dto.oppgaverTilGodkjenning.totalt)
        assertEquals(1, dto.oppgaverTilGodkjenning.perPeriodetype.size)
    }

    private operator fun List<Pair<BehandlingsstatistikkTypeForApi, Int>>.get(type: BehandlingsstatistikkTypeForApi) = this.first { it.first == type }.second

    private fun nyPersonMedAutomatiskVedtak(periodetype: Periodetype = Periodetype.FØRSTEGANGSBEHANDLING, inntektskilde: Inntektskilde = Inntektskilde.EN_ARBEIDSGIVER) {
        godkjenningsbehov()
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode(periodetype = periodetype, inntektskilde = inntektskilde)
        nyttAutomatiseringsinnslag(true)
    }
}
