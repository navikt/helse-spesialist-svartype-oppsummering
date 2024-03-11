package no.nav.helse.e2e

import AbstractE2ETest
import java.util.UUID
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import org.junit.jupiter.api.Test

internal class RevurderingE2ETest : AbstractE2ETest() {

    @Test
    fun `revurdering ved saksbehandlet oppgave`() {
        håndterSøknad()
        spleisOppretterNyBehandling()
        fremTilSaksbehandleroppgave(regelverksvarsler = listOf("RV_IM_1"))
        håndterSaksbehandlerløsning()
        håndterVedtakFattet()
        assertSaksbehandleroppgave(oppgavestatus = Oppgavestatus.Ferdigstilt)

        val utbetalingId2 = UUID.randomUUID()

        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(utbetalingId = utbetalingId2), harRisikovurdering = true)
        assertSaksbehandleroppgave(oppgavestatus = Oppgavestatus.AvventerSaksbehandler)
    }

    @Test
    fun `revurdering av periode med negativt beløp medfører oppgave`() {
        håndterSøknad()
        spleisOppretterNyBehandling()
        fremTilSaksbehandleroppgave(kanGodkjennesAutomatisk = true)
        håndterVedtakFattet()

        val utbetalingId2 = UUID.randomUUID()

        fremTilSaksbehandleroppgave(
            harOppdatertMetadata = true,
            harRisikovurdering = true,
            kanGodkjennesAutomatisk = true,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(utbetalingId = utbetalingId2),
            arbeidsgiverbeløp = -200,
            personbeløp = 0
        )
        assertSaksbehandleroppgave(oppgavestatus = Oppgavestatus.AvventerSaksbehandler)
    }
    @Test
    fun `revurdering av periode med positivt beløp og ingen varsler medfører ikke oppgave`() {
        håndterSøknad()
        spleisOppretterNyBehandling()
        fremTilSaksbehandleroppgave(kanGodkjennesAutomatisk = true)
        håndterVedtakFattet()

        val utbetalingId2 = UUID.randomUUID()

        fremTilSaksbehandleroppgave(
            harOppdatertMetadata = true,
            harRisikovurdering = true,
            kanGodkjennesAutomatisk = true,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(utbetalingId = utbetalingId2),
            arbeidsgiverbeløp = 200,
            personbeløp = 0
        )
        assertSaksbehandleroppgaveBleIkkeOpprettet()
    }
}
