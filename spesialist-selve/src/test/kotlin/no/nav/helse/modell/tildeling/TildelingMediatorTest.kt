package no.nav.helse.modell.tildeling

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.feilhåndtering.OppgaveAlleredeTildelt
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.saksbehandler.SaksbehandlerDao
import no.nav.helse.tildeling.TildelingApiDto
import no.nav.helse.tildeling.TildelingDao
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import org.junit.jupiter.api.BeforeEach

internal class TildelingMediatorTest {

    companion object {
        const val navn = "Sara Saksbehandler"
        const val epost = "sara.saksbehandler@nav.no"
        val SAKSBEHANDLER = UUID.randomUUID()
        const val oppgaveref = 1L
    }

    private val saksbehandlerDao = mockk<SaksbehandlerDao>(relaxed = true)
    private val tildelingDao = mockk<TildelingDao>()
    private val hendelseMediator = mockk<HendelseMediator>(relaxed = true)
    private val tildelingMediator = TildelingMediator(saksbehandlerDao, tildelingDao, hendelseMediator)

    @BeforeEach
    fun setup() {
        clearMocks(saksbehandlerDao, tildelingDao, hendelseMediator)
    }

    @Test
    fun `stopper tildeling av allerede tildelt sak`() {
        val eksisterendeTildeling = TildelingApiDto(epost = "epost@nav.no", oid = UUID.randomUUID(), påVent = false, navn = "annen saksbehandler")
        every { tildelingDao.tildelingForOppgave(any()) } returns eksisterendeTildeling

        assertThrows<OppgaveAlleredeTildelt> {
            tildelingMediator.tildelOppgaveTilSaksbehandler(
                oppgaveId = oppgaveref,
                saksbehandlerreferanse = eksisterendeTildeling.oid,
                epostadresse = eksisterendeTildeling.epost,
                navn = "navn",
                ident = "Z999999"
            )
        }
    }

    @Test
    fun `hvis det finnes en tidligere_saksbehandler blir tildeling fjernet og tidligere saksbehandler tildelt`() {
        every { tildelingDao.slettTildeling(oppgaveref) } returns 1
        every { tildelingDao.tildelingForOppgave(any()) } returns null
        every { hendelseMediator.tildelOppgaveTilSaksbehandler(any(), any()) } returns true

        tildelingMediator.fjernTildelingOgTildelNySaksbehandlerHvisFinnes(oppgaveref, SAKSBEHANDLER)

        verify(exactly = 1) { tildelingDao.slettTildeling(oppgaveref) }
        verify(exactly = 1) { hendelseMediator.tildelOppgaveTilSaksbehandler(oppgaveref, SAKSBEHANDLER) }
    }

    @Test
    fun `hvis det ikke finnes en tidligere_saksbehandler blir tildeling fjernet`() {
        every { tildelingDao.slettTildeling(oppgaveref) } returns 1
        every { tildelingDao.tildelingForOppgave(any()) } returns null
        every { hendelseMediator.tildelOppgaveTilSaksbehandler(any(), any()) } returns true

        tildelingMediator.fjernTildelingOgTildelNySaksbehandlerHvisFinnes(oppgaveref, null)

        verify(exactly = 1) { tildelingDao.slettTildeling(oppgaveref) }
        verify(exactly = 0) { hendelseMediator.tildelOppgaveTilSaksbehandler(any(), any()) }
    }
}
