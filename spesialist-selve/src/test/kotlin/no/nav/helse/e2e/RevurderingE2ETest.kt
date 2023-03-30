package no.nav.helse.e2e

import AbstractE2ETest
import io.mockk.every
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Meldingssender.sendArbeidsforholdløsningOld
import no.nav.helse.Meldingssender.sendArbeidsgiverinformasjonløsningOld
import no.nav.helse.Meldingssender.sendEgenAnsattløsningOld
import no.nav.helse.Meldingssender.sendGodkjenningsbehov
import no.nav.helse.Meldingssender.sendOverstyrTidslinje
import no.nav.helse.Meldingssender.sendPersoninfoløsningComposite
import no.nav.helse.Meldingssender.sendRevurderingAvvist
import no.nav.helse.Meldingssender.sendRisikovurderingløsningOld
import no.nav.helse.Meldingssender.sendSøknadSendt
import no.nav.helse.Meldingssender.sendUtbetalingEndret
import no.nav.helse.Meldingssender.sendVedtaksperiodeNyUtbetaling
import no.nav.helse.Meldingssender.sendVedtaksperiodeOpprettet
import no.nav.helse.Meldingssender.sendVergemålløsningOld
import no.nav.helse.Meldingssender.sendÅpneGosysOppgaverløsningOld
import no.nav.helse.TestRapidHelpers.oppgaveId
import no.nav.helse.Testdata.AKTØR
import no.nav.helse.Testdata.FØDSELSNUMMER
import no.nav.helse.Testdata.ORGNR
import no.nav.helse.Testdata.SAKSBEHANDLER_OID
import no.nav.helse.Testdata.SNAPSHOT_MED_WARNINGS
import no.nav.helse.Testdata.SNAPSHOT_UTEN_WARNINGS
import no.nav.helse.Testdata.UTBETALING_ID
import no.nav.helse.Testdata.UTBETALING_ID2
import no.nav.helse.Testdata.VEDTAKSPERIODE_ID
import no.nav.helse.Testdata.snapshotMedRevurderingUtbetaling
import no.nav.helse.modell.utbetaling.Utbetalingsstatus
import no.nav.helse.modell.utbetaling.Utbetalingtype.REVURDERING
import no.nav.helse.spesialist.api.abonnement.OpptegnelseType
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import no.nav.helse.spesialist.api.overstyring.Dagtype
import no.nav.helse.spesialist.api.overstyring.OverstyringDagDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RevurderingE2ETest : AbstractE2ETest() {

    private companion object {
        private const val SAKSBEHANDLERIDENT = "Z999999"
        private const val SAKSBEHANDLEREPOST = "saksbehandler@nav.no"

        private val SAKSBEHANDLEROID = UUID.randomUUID()
    }

    private val OPPGAVEID get() = testRapid.inspektør.oppgaveId()

    @BeforeEach
    fun setup() {
        every { snapshotClient.hentSnapshot(FØDSELSNUMMER) } returns SNAPSHOT_UTEN_WARNINGS
    }

    @Test
    fun `revurdering ved saksbehandlet oppgave`() {
        every { snapshotClient.hentSnapshot(FØDSELSNUMMER) } returns SNAPSHOT_MED_WARNINGS //Legger på warning for at saken ikke skal automatiseres

        sendSøknadSendt(AKTØR, FØDSELSNUMMER, ORGNR)
        sendVedtaksperiodeOpprettet(AKTØR, FØDSELSNUMMER, ORGNR, vedtaksperiodeId = VEDTAKSPERIODE_ID)
        sendVedtaksperiodeNyUtbetaling(VEDTAKSPERIODE_ID, organisasjonsnummer = ORGNR)
        sendUtbetalingEndret(AKTØR, FØDSELSNUMMER, ORGNR, UTBETALING_ID, "UTBETALING")
        val godkjenningsmeldingId1 = sendGodkjenningsbehov(AKTØR, FØDSELSNUMMER, ORGNR, VEDTAKSPERIODE_ID, UTBETALING_ID)
        håndterGodkjenningsbehov(godkjenningsmeldingId1)
        sendSaksbehandlerløsningFraAPI(OPPGAVEID, SAKSBEHANDLERIDENT, SAKSBEHANDLEREPOST, SAKSBEHANDLEROID, true)
        sendUtbetalingEndret(
            aktørId = AKTØR,
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            utbetalingId = UTBETALING_ID,
            type = "UTBETALING",
            status = Utbetalingsstatus.UTBETALT,
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID"
        )
        assertOppgavestatuser(
            0,
            Oppgavestatus.AvventerSaksbehandler,
            Oppgavestatus.AvventerSystem,
            Oppgavestatus.Ferdigstilt
        )
        assertOppgavetype(0, "SØKNAD")
        assertGodkjenningsbehovløsning(true, SAKSBEHANDLERIDENT)

        sendVedtaksperiodeNyUtbetaling(VEDTAKSPERIODE_ID, utbetalingId = UTBETALING_ID2, organisasjonsnummer = ORGNR)
        sendUtbetalingEndret(AKTØR, FØDSELSNUMMER, ORGNR, UTBETALING_ID2, "UTBETALING")
        val godkjenningsmeldingId2 = sendGodkjenningsbehov(
            organisasjonsnummer = ORGNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID2,
            utbetalingtype = REVURDERING
        )
        håndterGodkjenningsbehov(godkjenningsmeldingId2, harOppdatertMetadata = true)
        sendSaksbehandlerløsningFraAPI(OPPGAVEID, SAKSBEHANDLERIDENT, SAKSBEHANDLEREPOST, SAKSBEHANDLEROID, true)
        sendUtbetalingEndret(
            aktørId = AKTØR,
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            utbetalingId = UTBETALING_ID2,
            type = "REVURDERING",
            status = Utbetalingsstatus.UTBETALT,
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID"
        )
        assertOppgavestatuser(
            1,
            Oppgavestatus.AvventerSaksbehandler,
            Oppgavestatus.AvventerSystem,
            Oppgavestatus.Ferdigstilt
        )
        assertOppgavetype(1, "REVURDERING")
        assertGodkjenningsbehovløsning(true, SAKSBEHANDLERIDENT)
    }

    @Test
    fun `revurdering av periode medfører oppgave selv om perioden ikke har warnings`() {
        sendSøknadSendt(AKTØR, FØDSELSNUMMER, ORGNR)
        sendVedtaksperiodeOpprettet(AKTØR, FØDSELSNUMMER, ORGNR, vedtaksperiodeId = VEDTAKSPERIODE_ID)
        sendVedtaksperiodeNyUtbetaling(VEDTAKSPERIODE_ID, utbetalingId = UTBETALING_ID, organisasjonsnummer = ORGNR)
        sendUtbetalingEndret(AKTØR, FØDSELSNUMMER, ORGNR, UTBETALING_ID, "UTBETALING")
        val godkjenningsmeldingId1 = sendGodkjenningsbehov(AKTØR, FØDSELSNUMMER, ORGNR, VEDTAKSPERIODE_ID, UTBETALING_ID)
        håndterGodkjenningsbehov(godkjenningsmeldingId1)
        sendUtbetalingEndret(
            aktørId = AKTØR,
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            utbetalingId = UTBETALING_ID,
            type = "UTBETALING",
            status = Utbetalingsstatus.UTBETALT,
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID"
        )

        sendUtbetalingEndret(
            aktørId = AKTØR,
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            utbetalingId = UTBETALING_ID2,
            type = "REVURDERING",
            status = Utbetalingsstatus.IKKE_GODKJENT,
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID"
        )
        every { snapshotClient.hentSnapshot(FØDSELSNUMMER) } returns snapshotMedRevurderingUtbetaling(utbetalingId = UTBETALING_ID2)
        val godkjenningsmeldingId2 = sendGodkjenningsbehov(
            organisasjonsnummer = ORGNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID2,
            utbetalingtype = REVURDERING
        )
        håndterGodkjenningsbehov(godkjenningsmeldingId2, harOppdatertMetadata = true)
        sendSaksbehandlerløsningFraAPI(OPPGAVEID, SAKSBEHANDLERIDENT, SAKSBEHANDLEREPOST, SAKSBEHANDLEROID, true)
        sendUtbetalingEndret(
            aktørId = AKTØR,
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            utbetalingId = UTBETALING_ID2,
            type = "REVURDERING",
            status = Utbetalingsstatus.UTBETALT,
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID"
        )
        assertOppgavestatuser(
            0,
            Oppgavestatus.AvventerSaksbehandler,
            Oppgavestatus.AvventerSystem,
            Oppgavestatus.Ferdigstilt
        )
        assertOppgavetype(0, "REVURDERING")
        assertGodkjenningsbehovløsning(true, SAKSBEHANDLERIDENT)
    }

    @Test
    fun `fanger opp og informerer saksbehandler om avvist revurdering`() {
        sendSøknadSendt(AKTØR, FØDSELSNUMMER, ORGNR)
        sendVedtaksperiodeOpprettet(AKTØR, FØDSELSNUMMER, ORGNR, vedtaksperiodeId = VEDTAKSPERIODE_ID)
        sendVedtaksperiodeNyUtbetaling(VEDTAKSPERIODE_ID, organisasjonsnummer = ORGNR, utbetalingId = UTBETALING_ID)
        sendUtbetalingEndret(AKTØR, FØDSELSNUMMER, ORGNR, UTBETALING_ID, "UTBETALING")
        val godkjenningsmeldingId1 = sendGodkjenningsbehov(AKTØR, FØDSELSNUMMER, ORGNR, VEDTAKSPERIODE_ID, UTBETALING_ID)

        håndterGodkjenningsbehov(godkjenningsmeldingId1)

        sendOverstyrTidslinje(
            aktørId = AKTØR,
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            dager = listOf(OverstyringDagDto(LocalDate.now(), Dagtype.Feriedag, Dagtype.Sykedag, null, 100))
        )

        // Behind the scenes: Saksbehandler har også hooket opp en opptegnelse
        speilOppretterAbonnement()
        sendRevurderingAvvist(FØDSELSNUMMER, listOf("Revurderingen er åpenbart helt feil"))

        val opptegnelser = opptegnelseApiDao.finnOpptegnelser(SAKSBEHANDLER_OID)
        assertEquals(1, opptegnelser.size)
        assertEquals(OpptegnelseType.REVURDERING_AVVIST, opptegnelser.first().type)
        assertEquals(AKTØR.toLong(), opptegnelser.first().aktørId)
        assertTrue(opptegnelser.first().payload.contains("Revurderingen er åpenbart helt feil"))
    }

    private fun håndterGodkjenningsbehov(godkjenningsmeldingId: UUID, harOppdatertMetadata: Boolean = false) {
        sendPersoninfoløsningComposite(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        if (!harOppdatertMetadata) {
            sendArbeidsgiverinformasjonløsningOld(
                hendelseId = godkjenningsmeldingId,
                organisasjonsnummer = ORGNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID
            )
        }
        sendArbeidsforholdløsningOld(
            hendelseId = godkjenningsmeldingId,
            orgnr = ORGNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID
        )
        sendEgenAnsattløsningOld(
            godkjenningsmeldingId = godkjenningsmeldingId,
            erEgenAnsatt = false
        )
        sendVergemålløsningOld(
            godkjenningsmeldingId = godkjenningsmeldingId
        )
        sendÅpneGosysOppgaverløsningOld(
            godkjenningsmeldingId = godkjenningsmeldingId
        )
        sendRisikovurderingløsningOld(
            godkjenningsmeldingId = godkjenningsmeldingId,
            vedtaksperiodeId = VEDTAKSPERIODE_ID
        )
    }

    private fun speilOppretterAbonnement() {
        abonnementDao.opprettAbonnement(SAKSBEHANDLER_OID, AKTØR.toLong())
    }
}
