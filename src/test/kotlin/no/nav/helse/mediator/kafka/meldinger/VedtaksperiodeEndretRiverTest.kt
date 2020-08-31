package no.nav.helse.mediator.kafka.meldinger

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.mediator.kafka.HendelseMediator
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VedtaksperiodeEndretRiverTest {

    private val rapid = TestRapid()
    private val mediator = mockk<HendelseMediator>(relaxed = true)
    private val meldingsfabrikk = Testmeldingfabrikk("fnr")

    init {
        NyVedtaksperiodeEndretMessage.VedtaksperiodeEndretRiver(rapid, mediator)
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
        clearMocks(mediator)
    }

    @Test
    fun `tolker vedtaksperiode_endret`() {
        rapid.sendTestMessage(meldingsfabrikk.lagVedtaksperiodeEndret())
        verify { mediator.vedtaksperiodeEndret(any(), any()) }

    }
}
