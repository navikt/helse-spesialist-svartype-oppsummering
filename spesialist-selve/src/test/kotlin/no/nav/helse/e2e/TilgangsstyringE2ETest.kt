package no.nav.helse.e2e

import AbstractE2ETest
import graphql.GraphQLError
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import no.nav.helse.Meldingssender.sendArbeidsforholdløsningOld
import no.nav.helse.Meldingssender.sendArbeidsgiverinformasjonløsningOld
import no.nav.helse.Meldingssender.sendDigitalKontaktinformasjonløsningOld
import no.nav.helse.Meldingssender.sendEgenAnsattløsningOld
import no.nav.helse.Meldingssender.sendGodkjenningsbehov
import no.nav.helse.Meldingssender.sendPersoninfoløsningComposite
import no.nav.helse.Meldingssender.sendRisikovurderingløsningOld
import no.nav.helse.Meldingssender.sendVergemålløsningOld
import no.nav.helse.Meldingssender.sendÅpneGosysOppgaverløsningOld
import no.nav.helse.MeldingssenderV2
import no.nav.helse.Testdata.AKTØR
import no.nav.helse.Testdata.FØDSELSNUMMER
import no.nav.helse.Testdata.ORGNR
import no.nav.helse.Testdata.SNAPSHOT_MED_WARNINGS
import no.nav.helse.Testdata.UTBETALING_ID
import no.nav.helse.Testdata.VEDTAKSPERIODE_ID
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TilgangsstyringE2ETest : AbstractE2ETest() {
    private val meldingssenderV2 = MeldingssenderV2(testRapid)

    @Test
    fun `Gir 404 når det ikke er noe å vise ennå, selv om saksbehandler har tilgang`() {
        settOppDefaultDataOgTilganger()

        val godkjenningsmeldingId = sendMeldingerOppTilEgenAnsatt()

        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")

        sendEgenAnsattløsningOld(godkjenningsmeldingId = godkjenningsmeldingId, erEgenAnsatt = true)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")

        sendFramTilOppgave(godkjenningsmeldingId)
        assertIngenOppgave()

        saksbehandlertilgangTilSkjermede(harTilgang = true)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
    }

    @Test
    fun `Kan hente person om den er klar til visning`() {
        settOppDefaultDataOgTilganger()

        val godkjenningsmeldingId = sendMeldingerOppTilEgenAnsatt()

        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        sendEgenAnsattløsningOld(godkjenningsmeldingId = godkjenningsmeldingId, erEgenAnsatt = false)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        sendFramTilOppgave(godkjenningsmeldingId)
        assertOppgaver(1)
        assertKanHentePerson()
    }

    @Test
    fun `Kan ikke hente person hvis tilgang mangler`() {
        settOppDefaultDataOgTilganger()

        val godkjenningsmeldingId = sendMeldingerOppTilEgenAnsatt()

        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        sendEgenAnsattløsningOld(godkjenningsmeldingId = godkjenningsmeldingId, erEgenAnsatt = false)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        sendFramTilOppgave(godkjenningsmeldingId)
        assertOppgaver(1)

        assertKanHentePerson()

        meldingssenderV2.sendEndretSkjermetinfo(true)

        assertKanIkkeHentePerson("Har ikke tilgang til person med fødselsnummer ")

        saksbehandlertilgangTilSkjermede(harTilgang = true)

        assertKanHentePerson()
    }

    private fun sendMeldingerOppTilEgenAnsatt(): UUID {
        val godkjenningsmeldingId = sendGodkjenningsbehov(AKTØR, FØDSELSNUMMER, ORGNR, VEDTAKSPERIODE_ID, UTBETALING_ID)
        sendPersoninfoløsningComposite(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        sendArbeidsgiverinformasjonløsningOld(
            hendelseId = godkjenningsmeldingId,
            organisasjonsnummer = ORGNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID,
            navn = "En Arbeidsgiver",
            bransjer = listOf("En eller flere bransjer")
        )
        sendArbeidsforholdløsningOld(
            hendelseId = godkjenningsmeldingId,
            orgnr = ORGNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID
        )
        return godkjenningsmeldingId
    }

    private fun sendFramTilOppgave(godkjenningsmeldingId: UUID) {
        sendVergemålløsningOld(godkjenningsmeldingId)
        sendDigitalKontaktinformasjonløsningOld(godkjenningsmeldingId)
        sendÅpneGosysOppgaverløsningOld(godkjenningsmeldingId)
        sendRisikovurderingløsningOld(godkjenningsmeldingId, VEDTAKSPERIODE_ID)
    }

    private fun fetchPerson() = personQuery.person(FØDSELSNUMMER, null, dataFetchingEnvironment)

    private fun assertKanIkkeHentePerson(feilmelding: String) {
        fetchPerson().let { response ->
            assertFeilmelding(feilmelding, response.errors)
            assertNull(response.data)
        }
    }

    private fun assertKanHentePerson() {
        fetchPerson().let { response ->
            assertEquals(emptyList<GraphQLError>(), response.errors)
            assertNotNull(response.data)
        }
    }

    private fun settOppDefaultDataOgTilganger() {
        every { snapshotClient.hentSnapshot(FØDSELSNUMMER) } returns SNAPSHOT_MED_WARNINGS
        every { dataFetchingEnvironment.graphQlContext.get<String>("saksbehandlerNavn") } returns "saksbehandler"
        saksbehandlertilgangTilSkjermede(harTilgang = false)
    }

    private fun saksbehandlertilgangTilSkjermede(harTilgang: Boolean) {
        every { dataFetchingEnvironment.graphQlContext.get<SaksbehandlerTilganger>("tilganger") } returns mockk(relaxed = true) {
            every { harTilgangTilSkjermedePersoner() } returns harTilgang
        }
    }

    companion object {
        private fun assertFeilmelding(feilmelding: String, errors: List<GraphQLError>) {
            assertTrue(errors.any { it.message.contains(feilmelding) }) {
                "Forventet at $errors skulle inneholde \"$feilmelding\""
            }
        }
    }
}
