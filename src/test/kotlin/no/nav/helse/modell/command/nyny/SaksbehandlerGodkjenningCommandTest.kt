package no.nav.helse.modell.command.nyny

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.api.OppgaveMediator
import no.nav.helse.modell.Oppgave
import no.nav.helse.modell.automatisering.Automatisering
import no.nav.helse.tildeling.ReservasjonDao
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class SaksbehandlerGodkjenningCommandTest {
    private companion object {
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private const val FNR = "12345678910"
        private const val JSON = "{}"
        private val hendelseId = UUID.randomUUID()
    }

    private val oppgaveMediator = mockk<OppgaveMediator>(relaxed = true)
    private val automatisering = mockk<Automatisering>(relaxed = true)
    private val reservasjonDao = mockk<ReservasjonDao>(relaxed = true)
    private lateinit var context: CommandContext
    private val command = SaksbehandlerGodkjenningCommand(
        FNR,
        VEDTAKSPERIODE_ID,
        JSON,
        reservasjonDao,
        oppgaveMediator,
        automatisering,
        hendelseId
    )
    private lateinit var forventetOppgave: Oppgave

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
        forventetOppgave =
            Oppgave.avventerSaksbehandler(SaksbehandlerGodkjenningCommand::class.java.simpleName, VEDTAKSPERIODE_ID)
        clearMocks(oppgaveMediator)
    }

    @Test
    fun `oppretter oppgave`() {
        every { reservasjonDao.hentReservasjonFor(FNR) } returns null
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(forventetOppgave) }
    }

    @Test
    fun `oppretter oppgave med reservasjon`() {
        val reservasjon = Pair(UUID.randomUUID(), LocalDateTime.now())
        every { reservasjonDao.hentReservasjonFor(FNR) } returns reservasjon
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.tildel(forventetOppgave, reservasjon.first, reservasjon.second) }
    }
}
