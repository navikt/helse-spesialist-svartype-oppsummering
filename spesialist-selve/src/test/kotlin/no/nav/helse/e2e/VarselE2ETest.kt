package no.nav.helse.e2e

import AbstractE2ETest
import java.time.LocalDate
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.januar
import no.nav.helse.mediator.meldinger.Risikofunn
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk.VergemålJson.Fullmakt
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk.VergemålJson.Område.Syk
import no.nav.helse.modell.varsel.Varsel
import no.nav.helse.modell.varsel.Varsel.Status.AKTIV
import no.nav.helse.modell.varsel.Varsel.Status.INAKTIV
import no.nav.helse.modell.varsel.Varselkode
import no.nav.helse.modell.varsel.Varselkode.SB_EX_1
import no.nav.helse.modell.varsel.Varselkode.SB_EX_3
import no.nav.helse.modell.varsel.Varselkode.SB_IK_1
import no.nav.helse.modell.varsel.Varselkode.SB_RV_1
import no.nav.helse.modell.varsel.Varselkode.SB_RV_2
import no.nav.helse.modell.varsel.Varselkode.SB_RV_3
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VarselE2ETest : AbstractE2ETest() {

    @Test
    fun `ingen varsel`() {
        fremTilSaksbehandleroppgave()
        assertIngenVarsel(SB_IK_1, VEDTAKSPERIODE_ID)
        assertIngenVarsel(SB_RV_1, VEDTAKSPERIODE_ID)
        assertIngenVarsel(SB_RV_3, VEDTAKSPERIODE_ID)
        assertIngenVarsel(SB_EX_1, VEDTAKSPERIODE_ID)
        assertIngenVarsel(SB_EX_3, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `varsel om vergemål`() {
        fremTilSaksbehandleroppgave(fullmakter = listOf(Fullmakt(områder = listOf(Syk), LocalDate.MIN, LocalDate.MAX)))
        assertVarsel(SB_IK_1, VEDTAKSPERIODE_ID, AKTIV)
    }

    @Test
    fun `varsel om faresignaler ved risikovurdering`() {
        fremTilSaksbehandleroppgave(risikofunn = listOf(Risikofunn(listOf("EN_KATEGORI"), "EN_BESKRIVELSE", false)))
        assertVarsel(SB_RV_1, VEDTAKSPERIODE_ID, AKTIV)
    }

    @Test
    fun `ingen varsler dersom ingen åpne oppgaver eller oppslagsfeil`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning()
        assertIngenVarsel(SB_EX_3, VEDTAKSPERIODE_ID)
        assertIngenVarsel(SB_EX_1, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `lager varsel ved åpne gosys-oppgaver`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(antall = 1)
        assertVarsel(SB_EX_1, VEDTAKSPERIODE_ID, AKTIV)
        assertIngenVarsel(SB_EX_3, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `lager ikke duplikatvarsel ved åpne gosys-oppgaver`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(antall = 1)
        håndterRisikovurderingløsning()
        håndterInntektløsning()
        håndterGosysOppgaveEndret()
        håndterÅpneOppgaverløsning(antall = 1)
        assertVarsel(SB_EX_1, VEDTAKSPERIODE_ID, AKTIV)
    }

    @Test
    fun `fjern varsel om gosys-oppgave dersom det ikke finnes gosys-oppgave lenger`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(antall = 1)
        håndterRisikovurderingløsning()
        håndterInntektløsning()
        håndterGosysOppgaveEndret()
        håndterÅpneOppgaverløsning(antall = 0)
        assertVarsel(SB_EX_1, VEDTAKSPERIODE_ID, INAKTIV)
        assertIngenVarsel(SB_EX_3, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `fjern varsel om tilbakedatering dersom tilbakedatert sykmelding er godkjent`() {
        fremTilÅpneOppgaver(regelverksvarsler = listOf("RV_SØ_3"),)
        håndterÅpneOppgaverløsning()
        håndterRisikovurderingløsning()
        håndterInntektløsning()
        assertVarsel("RV_SØ_3", VEDTAKSPERIODE_ID, AKTIV)

        håndterTilbakedateringBehandlet(skjæringstidspunkt = 1.januar)
        assertVarsel("RV_SØ_3", VEDTAKSPERIODE_ID, INAKTIV)
    }

    @Test
    fun `fjern varsel om tilbakedatering på alle overlappende perioder i sykefraværstilfellet for ok-sykmelding`() {
        fremTilÅpneOppgaver(regelverksvarsler = listOf("RV_SØ_3"),)
        håndterÅpneOppgaverløsning()
        håndterRisikovurderingløsning()
        håndterInntektløsning()

        val vedtaksperiodeId2 = UUID.randomUUID()
        håndterSøknad()
        håndterVedtaksperiodeOpprettet(vedtaksperiodeId = vedtaksperiodeId2)
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("RV_SØ_3"), vedtaksperiodeId = vedtaksperiodeId2
        )

        assertVarsel("RV_SØ_3", VEDTAKSPERIODE_ID, AKTIV)
        assertVarsel("RV_SØ_3", vedtaksperiodeId2, AKTIV)

        håndterTilbakedateringBehandlet(skjæringstidspunkt = 1.januar)
        assertVarsel("RV_SØ_3", VEDTAKSPERIODE_ID, INAKTIV)
        assertVarsel("RV_SØ_3", vedtaksperiodeId2, INAKTIV)
    }

    @Test
    fun `varsel dersom kall til gosys feilet`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(antall = 0, oppslagFeilet = true)
        assertVarsel(SB_EX_3, VEDTAKSPERIODE_ID, AKTIV)
    }

    @Test
    fun `fjern varsel dersom kall til gosys ikke feiler lenger`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(antall = 0, oppslagFeilet = true)
        håndterRisikovurderingløsning()
        håndterInntektløsning()
        håndterGosysOppgaveEndret()
        håndterÅpneOppgaverløsning(antall = 0)
        assertVarsel(SB_EX_3, VEDTAKSPERIODE_ID, INAKTIV)
    }

    @Test
    fun `legger til varsel om gosys-oppgave når vi får beskjed om at gosys har fått oppgaver`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(antall = 0)
        håndterRisikovurderingløsning(kanGodkjennesAutomatisk = false)
        håndterInntektløsning()
        håndterGosysOppgaveEndret()
        håndterÅpneOppgaverløsning(antall = 1)
        assertVarsel(SB_EX_1, VEDTAKSPERIODE_ID, AKTIV)
        assertIngenVarsel(SB_EX_3, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `legger til varsel om manglende gosys-info`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(oppslagFeilet = true)
        håndterRisikovurderingløsning()
        assertVarsel(SB_EX_3, VEDTAKSPERIODE_ID, AKTIV)
        assertIngenVarsel(SB_EX_1, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `legger til varsel dersom oppslag feiler når vi har fått beskjed om at gosys har endret seg`() {
        fremTilÅpneOppgaver()
        håndterÅpneOppgaverløsning(oppslagFeilet = false)
        håndterRisikovurderingløsning(kanGodkjennesAutomatisk = false)
        håndterInntektløsning()
        håndterGosysOppgaveEndret()
        håndterÅpneOppgaverløsning(oppslagFeilet = true)
        assertVarsel(SB_EX_3, VEDTAKSPERIODE_ID, AKTIV)
        assertIngenVarsel(SB_EX_1, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `varsel dersom teknisk feil ved sjekk av 8-4-knappetrykk`() {
        fremTilSaksbehandleroppgave(
            risikofunn = listOf(
                Risikofunn(
                    listOf("8-4", "EN_ANNEN_KATEGORI"),
                    "Klarte ikke gjøre automatisk 8-4-vurdering p.g.a. teknisk feil. Kan godkjennes hvis alt ser greit ut.",
                    false
                )
            )
        )
        assertVarsel(SB_RV_3, VEDTAKSPERIODE_ID, AKTIV)
    }

    @Test
    fun `varsel ved manuell stans av automatisk behandling - 8-4`() {
        fremTilSaksbehandleroppgave(
            risikofunn = listOf(
                Risikofunn(listOf("8-4", "EN_ANNEN_KATEGORI"), "EN_BESKRIVELSE", false)
            )
        )
        assertIngenVarsel(SB_RV_3, VEDTAKSPERIODE_ID)
        assertVarsel(SB_RV_2, VEDTAKSPERIODE_ID, AKTIV)
    }

    private fun assertVarsel(varselkode: Varselkode, vedtaksperiodeId: UUID, status: Varsel.Status) {
        val antallVarsler = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT count(*) FROM selve_varsel WHERE kode = ? AND vedtaksperiode_id = ? AND status = ?"
            requireNotNull(
                session.run(
                    queryOf(
                        query,
                        varselkode.name,
                        vedtaksperiodeId,
                        status.name
                    ).map { it.int(1) }.asSingle
                )
            )
        }
        assertEquals(1, antallVarsler)
    }

    private fun assertVarsel(varselkode: String, vedtaksperiodeId: UUID, status: Varsel.Status) {
        val antallVarsler = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT count(*) FROM selve_varsel WHERE kode = ? AND vedtaksperiode_id = ? AND status = ?"
            requireNotNull(
                session.run(
                    queryOf(
                        query,
                        varselkode,
                        vedtaksperiodeId,
                        status.name
                    ).map { it.int(1) }.asSingle
                )
            )
        }
        assertEquals(1, antallVarsler)
    }

    private fun assertIngenVarsel(varselkode: Varselkode, vedtaksperiodeId: UUID) {
        val antallVarsler = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT count(*) FROM selve_varsel WHERE kode = ? AND vedtaksperiode_id = ?"
            requireNotNull(
                session.run(
                    queryOf(
                        query,
                        varselkode.name,
                        vedtaksperiodeId,
                    ).map { it.int(1) }.asSingle
                )
            )
        }
        assertEquals(0, antallVarsler)
    }
}
