package no.nav.helse.mediator

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.modell.Oppgave
import no.nav.helse.modell.OppgaveDao
import no.nav.helse.modell.Oppgavestatus
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.kommando.TestHendelse
import no.nav.helse.modell.tildeling.ReservasjonDao
import no.nav.helse.modell.tildeling.TildelingDao
import no.nav.helse.modell.vedtak.VedtakDto
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.random.Random.Default.nextLong

internal class OppgaveMediatorTest {
    private companion object {
        private const val FNR = "12345678911"
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private val VEDTAKSPERIODE_ID_2 = UUID.randomUUID()
        private val HENDELSE_ID = UUID.randomUUID()
        private val COMMAND_CONTEXT_ID = UUID.randomUUID()
        private val TESTHENDELSE = TestHendelse(HENDELSE_ID, VEDTAKSPERIODE_ID, FNR)
        private val OPPGAVE_ID = nextLong()
        private val VEDTAKREF = nextLong()
        private val VEDTAKREF2 = nextLong()
        private const val SAKSBEHANDLERIDENT = "Z999999"
        private val SAKSBEHANDLEROID = UUID.randomUUID()
        private const val OPPGAVETYPE_SØKNAD = "SØKNAD"
        private const val OPPGAVETYPE_STIKKPRØVE = "STIKKPRØVE"
    }

    private val oppgaveDao = mockk<OppgaveDao>(relaxed = true)
    private val vedtakDao = mockk<VedtakDao>(relaxed = true)
    private val tildelingDao = mockk<TildelingDao>(relaxed = true)
    private val reservasjonDao = mockk<ReservasjonDao>(relaxed = true)
    private val mediator = OppgaveMediator(oppgaveDao, vedtakDao, tildelingDao, reservasjonDao)
    private val søknadsoppgave: Oppgave = Oppgave.søknad(VEDTAKSPERIODE_ID)
    private val stikkprøveoppgave: Oppgave = Oppgave.stikkprøve(VEDTAKSPERIODE_ID_2)

    private val testRapid = TestRapid()

    @BeforeEach
    fun setup() {
        clearMocks(oppgaveDao, vedtakDao, tildelingDao)
        testRapid.reset()
    }

    @Test
    fun `lagrer oppgaver`() {
        every { vedtakDao.findVedtak(VEDTAKSPERIODE_ID) } returns VedtakDto(VEDTAKREF, 2L)
        every { vedtakDao.findVedtak(VEDTAKSPERIODE_ID_2) } returns VedtakDto(VEDTAKREF2, 2L)
        every { reservasjonDao.hentReservasjonFor(TESTHENDELSE.fødselsnummer()) } returns null
        every { oppgaveDao.finn(0L) } returns søknadsoppgave
        every { oppgaveDao.finn(1L) } returns stikkprøveoppgave
        every { oppgaveDao.opprettOppgave(any(), OPPGAVETYPE_SØKNAD, any()) } returns 0L
        every { oppgaveDao.opprettOppgave(any(), OPPGAVETYPE_STIKKPRØVE, any()) } returns 1L
        every { oppgaveDao.finnHendelseId(any()) } returns HENDELSE_ID
        every { oppgaveDao.finnContextId(any()) } returns COMMAND_CONTEXT_ID
        every { oppgaveDao.finnMakstid(any()) } returns null
        mediator.opprett(søknadsoppgave)
        mediator.opprett(stikkprøveoppgave)
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        verify(exactly = 1) { oppgaveDao.opprettOppgave(COMMAND_CONTEXT_ID, OPPGAVETYPE_SØKNAD, VEDTAKREF) }
        verify(exactly = 1) { oppgaveDao.opprettOppgave(COMMAND_CONTEXT_ID, OPPGAVETYPE_STIKKPRØVE, VEDTAKREF2) }
        assertEquals(2, testRapid.inspektør.size)
        assertOppgaveevent(0, "oppgave_opprettet")
        assertOppgaveevent(1, "oppgave_opprettet")
        verify(exactly = 2) { oppgaveDao.opprettMakstid(any()) }
    }

    @Test
    fun `lagrer tildeling`() {
        val (oid, gyldigTil) = Pair(UUID.randomUUID(), LocalDateTime.now())
        every { reservasjonDao.hentReservasjonFor(TESTHENDELSE.fødselsnummer()) } returns (oid to gyldigTil)
        every { oppgaveDao.finn(0L) } returns søknadsoppgave
        mediator.opprett(søknadsoppgave)
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        verify(exactly = 1) { tildelingDao.opprettTildeling(any(), oid, gyldigTil) }
        verify(exactly = 1) { oppgaveDao.oppdaterMakstidVedTildeling(any()) }
    }

    @Test
    fun `oppdaterer oppgave`() {
        every { reservasjonDao.hentReservasjonFor(TESTHENDELSE.fødselsnummer()) } returns null
        val oppgave = Oppgave(OPPGAVE_ID, OPPGAVETYPE_SØKNAD, Oppgavestatus.AvventerSaksbehandler, VEDTAKSPERIODE_ID)
        every { oppgaveDao.finn(any<Long>()) } returns oppgave
        every { oppgaveDao.finnHendelseId(any()) } returns HENDELSE_ID
        every { oppgaveDao.finnContextId(any()) } returns COMMAND_CONTEXT_ID
        mediator.ferdigstill(oppgave, SAKSBEHANDLERIDENT, SAKSBEHANDLEROID)
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        assertEquals(1, testRapid.inspektør.size)
        assertOppgaveevent(0, "oppgave_oppdatert", Oppgavestatus.Ferdigstilt) {
            assertEquals(OPPGAVE_ID, it.path("oppgaveId").longValue())
            assertEquals(SAKSBEHANDLERIDENT, it.path("ferdigstiltAvIdent").asText())
            assertEquals(SAKSBEHANDLEROID, UUID.fromString(it.path("ferdigstiltAvOid").asText()))
        }
    }

    @Test
    fun `oppretter ikke flere oppgaver på samme vedtaksperiodeId`() {
        every { oppgaveDao.harAktivOppgave(VEDTAKSPERIODE_ID) } returnsMany listOf(false, true)
        every { reservasjonDao.hentReservasjonFor(TESTHENDELSE.fødselsnummer()) } returns null
        every { oppgaveDao.finn(0L) } returns søknadsoppgave
        mediator.opprett(søknadsoppgave)
        mediator.opprett(søknadsoppgave)
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        verify(exactly = 1) { oppgaveDao.opprettOppgave(COMMAND_CONTEXT_ID, OPPGAVETYPE_SØKNAD, any()) }
    }

    @Test
    fun `lagrer ikke dobbelt`() {
        every { reservasjonDao.hentReservasjonFor(TESTHENDELSE.fødselsnummer()) } returns null
        every { oppgaveDao.finn(0L) } returns søknadsoppgave
        every { oppgaveDao.finn(1L) } returns stikkprøveoppgave
        every { oppgaveDao.opprettOppgave(any(), OPPGAVETYPE_SØKNAD, any()) } returns 0L
        every { oppgaveDao.opprettOppgave(any(), OPPGAVETYPE_STIKKPRØVE, any()) } returns 1L
        mediator.opprett(søknadsoppgave)
        mediator.opprett(stikkprøveoppgave)
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        assertEquals(2, testRapid.inspektør.size)
        testRapid.reset()
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        assertEquals(0, testRapid.inspektør.size)
    }

    @Test
    fun `avbryter oppgaver`() {
        val oppgave1 = Oppgave(1L, OPPGAVETYPE_SØKNAD, Oppgavestatus.AvventerSaksbehandler, VEDTAKSPERIODE_ID)
        val oppgave2 = Oppgave(2L, OPPGAVETYPE_STIKKPRØVE, Oppgavestatus.AvventerSaksbehandler, VEDTAKSPERIODE_ID)
        every { oppgaveDao.finnAktive(VEDTAKSPERIODE_ID) } returns listOf(oppgave1, oppgave2)
        every { reservasjonDao.hentReservasjonFor(TESTHENDELSE.fødselsnummer()) } returns null
        every { oppgaveDao.finn(1L) } returns oppgave1
        every { oppgaveDao.finn(2L) } returns oppgave2
        mediator.avbrytOppgaver(VEDTAKSPERIODE_ID)
        mediator.lagreOgTildelOppgaver(TESTHENDELSE, testRapid, COMMAND_CONTEXT_ID)
        verify(exactly = 1) { oppgaveDao.finnAktive(VEDTAKSPERIODE_ID) }
        verify(exactly = 2) { oppgaveDao.updateOppgave(any(), Oppgavestatus.Invalidert, null, null) }
    }

    @Test
    fun `timer ut oppgaver som har oppnådd makstid`() {
        val oppgave1 = Oppgave(1L, OPPGAVETYPE_SØKNAD, Oppgavestatus.AvventerSaksbehandler, VEDTAKSPERIODE_ID)
        every { oppgaveDao.finn(1L) } returns oppgave1
        mediator.makstidOppnådd(oppgave1)
        mediator.lagreOppgaver(testRapid, UUID.randomUUID(), COMMAND_CONTEXT_ID)
        verify(exactly = 1) { oppgaveDao.updateOppgave(any(), Oppgavestatus.MakstidOppnådd, null, null) }
        verify(exactly = 1) { oppgaveDao.finnMakstid(any()) }
        verify(exactly = 1) { oppgaveDao.finnFødselsnummer(any()) }
    }

    private fun assertOppgaveevent(indeks: Int, navn: String, status: Oppgavestatus = Oppgavestatus.AvventerSaksbehandler, assertBlock: (JsonNode) -> Unit = {}) {
        testRapid.inspektør.message(indeks).also {
            assertEquals(navn, it.path("@event_name").asText())
            assertEquals(HENDELSE_ID, UUID.fromString(it.path("hendelseId").asText()))
            assertEquals(COMMAND_CONTEXT_ID, UUID.fromString(it.path("contextId").asText()))
            assertEquals(status, enumValueOf<Oppgavestatus>(it.path("status").asText()))
            assertTrue(it.hasNonNull("oppgaveId"))
            assertBlock(it)
        }
    }
}
