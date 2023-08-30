package no.nav.helse.spesialist.api.tildeling

import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveAlleredeTildelt
import no.nav.helse.spesialist.api.oppgave.Oppgavemelder
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao.TotrinnsvurderingDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class TildelingServiceTest {

    private val saksbehandlerDao = mockk<SaksbehandlerDao>(relaxed = true)
    private val tildelingDao = mockk<TildelingDao>()
    private val totrinnsvurderingApiDao = mockk<TotrinnsvurderingApiDao>(relaxed = true)
    private val oppgavemelder = { mockk<Oppgavemelder>(relaxed = true) }
    private val tildelingService = TildelingService(
        tildelingDao, saksbehandlerDao, totrinnsvurderingApiDao, oppgavemelder
    )

    @Test
    fun `stopper tildeling av allerede tildelt sak`() {
        val eksisterendeTildeling = TildelingApiDto(
            epost = "epost@nav.no", oid = UUID.randomUUID(), påVent = false, navn = "annen saksbehandler"
        )
        every { tildelingDao.tildelingForOppgave(any()) } returns eksisterendeTildeling
        every { tildelingDao.opprettTildeling(any(), any()) } returns null

        assertThrows<OppgaveAlleredeTildelt> {
            tildelingService.tildelOppgaveTilSaksbehandler(
                oppgaveId = 1L,
                saksbehandlerreferanse = UUID.randomUUID(),
                epostadresse = "saksbehandler.epost",
                navn = "navn",
                ident = "Z999999",
                saksbehandlerTilganger = saksbehandlerTilganger(harBesluttertilgang = true)
            )
        }
    }

    @Test
    fun `får ikke lov å ta sak man har sendt til beslutter, eller vice versa`() {
        val saksbehandler = UUID.randomUUID()
        every { totrinnsvurderingApiDao.hentAktiv(any<Long>()) } returns totrinnsvurdering(saksbehandler)
        assertThrows<IllegalStateException> {
            tildelingService.tildelOppgaveTilSaksbehandler(
                oppgaveId = 1L,
                saksbehandlerreferanse = saksbehandler,
                epostadresse = "saksbehandler.epost",
                navn = "navn",
                ident = "Z999999",
                saksbehandlerTilganger = saksbehandlerTilganger(harBesluttertilgang = false)
            )
        }
    }

    @Test
    fun `får lov å tildele seg beslutteroppgave hvis man er i besluttergruppe`() {
        every { totrinnsvurderingApiDao.hentAktiv(any<Long>()) } returns totrinnsvurdering()
        every { tildelingDao.opprettTildeling(any(), any()) } returns mockk()

        assertDoesNotThrow {
            tildelingService.tildelOppgaveTilSaksbehandler(
                oppgaveId = 1L,
                saksbehandlerreferanse = UUID.randomUUID(),
                epostadresse = "saksbehandler.epost",
                navn = "navn",
                ident = "Z999999",
                saksbehandlerTilganger = saksbehandlerTilganger(harBesluttertilgang = true)
            )
        }
    }

    @Test
    fun `får ikke lov å tildele seg beslutteroppgave hvis man ikke er i besluttergruppe`() {
        every { totrinnsvurderingApiDao.hentAktiv(any<Long>()) } returns totrinnsvurdering(saksbehandlerOid = UUID.randomUUID())

        assertThrows<IllegalStateException> {
            tildelingService.tildelOppgaveTilSaksbehandler(
                oppgaveId = 1L,
                saksbehandlerreferanse = UUID.randomUUID(),
                epostadresse = "saksbehandler.epost",
                navn = "navn",
                ident = "Z999999",
                saksbehandlerTilganger = saksbehandlerTilganger(harBesluttertilgang = false)
            )
        }
    }

    private fun totrinnsvurdering(saksbehandlerOid: UUID? = null) = TotrinnsvurderingDto(
        vedtaksperiodeId = UUID.randomUUID(),
        erRetur = false,
        saksbehandler = saksbehandlerOid,
        beslutter = null,
        utbetalingIdRef = null,
        opprettet = LocalDateTime.now(),
        oppdatert = null
    )

    private fun saksbehandlerTilganger(harBesluttertilgang: Boolean) = mockk<SaksbehandlerTilganger> {
        every { harTilgangTilBeslutterOppgaver() } returns harBesluttertilgang
    }

}
