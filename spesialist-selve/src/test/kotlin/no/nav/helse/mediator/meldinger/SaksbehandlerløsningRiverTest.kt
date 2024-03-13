package no.nav.helse.mediator.meldinger

import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.meldinger.løsninger.SaksbehandlerløsningRiver
import no.nav.helse.modell.vedtaksperiode.vedtak.Saksbehandlerløsning
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SaksbehandlerløsningRiverTest {
    private companion object {
        private val ID = UUID.randomUUID()
        private val GODKJENNINGSBEHOV_ID = UUID.randomUUID()
        private val CONTEXT = UUID.randomUUID()
        private const val FNR = "12345678911"
    }

    private val mediator = mockk<MeldingMediator>(relaxed = true)
    private val testRapid = TestRapid().apply {
        SaksbehandlerløsningRiver(this, mediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `leser saksbehandlerløsning`() {
        testRapid.sendTestMessage(
            Testmeldingfabrikk.lagSaksbehandlerløsning(
                FNR,
                GODKJENNINGSBEHOV_ID,
                CONTEXT,
                id = ID
            )
        )
        verify(exactly = 1) { mediator.håndter(FNR, any<Saksbehandlerløsning>(), any()) }
    }
}
