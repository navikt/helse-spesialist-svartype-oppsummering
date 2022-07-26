package no.nav.helse.modell

import DatabaseIntegrationTest
import java.time.LocalDate
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.kommando.TestHendelse
import no.nav.helse.modell.vedtaksperiode.Inntektskilde
import no.nav.helse.modell.vedtaksperiode.Periodetype
import no.nav.helse.oppgave.Oppgave
import no.nav.helse.oppgave.Oppgavestatus
import no.nav.helse.oppgave.Oppgavestatus.AvventerSaksbehandler
import no.nav.helse.oppgave.Oppgavestatus.Ferdigstilt
import no.nav.helse.oppgave.Oppgavestatus.Invalidert
import no.nav.helse.oppgave.Oppgavetype
import no.nav.helse.person.Adressebeskyttelse
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import no.nav.helse.vedtaksperiode.Inntektskilde as InntektskildeForApi
import no.nav.helse.vedtaksperiode.Periodetype as PeriodetypeForApi

class OppgaveDaoTest : DatabaseIntegrationTest() {
    private companion object {
        private val CONTEXT_ID = UUID.randomUUID()
        private val TESTHENDELSE = TestHendelse(HENDELSE_ID, UUID.randomUUID(), FNR)
    }

    @BeforeEach
    fun setupDaoTest() {
        sessionOf(dataSource).use {
            it.run(queryOf("SELECT truncate_tables()").asExecute)
        }
        godkjenningsbehov(TESTHENDELSE.id)
        CommandContext(CONTEXT_ID).opprett(CommandContextDao(dataSource), TESTHENDELSE)
    }

    @Test
    fun `lagre oppgave`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        assertEquals(1, oppgave().size)
        oppgave().first().assertEquals(
            LocalDate.now(),
            OPPGAVETYPE,
            OPPGAVESTATUS,
            null,
            null,
            vedtakId,
            CONTEXT_ID
        )
    }

    @Test
    fun `lagre oppgave med fortrolig adressebeskyttelse`() {
        opprettPerson(adressebeskyttelse = Adressebeskyttelse.Fortrolig)
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID, oppgavetype = Oppgavetype.FORTROLIG_ADRESSE)
        assertEquals(1, oppgave().size)
        oppgave().first().assertEquals(
            LocalDate.now(),
            Oppgavetype.FORTROLIG_ADRESSE,
            OPPGAVESTATUS,
            null,
            null,
            vedtakId,
            CONTEXT_ID
        )
    }

    @Test
    fun `finner contextId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        assertEquals(CONTEXT_ID, oppgaveDao.finnContextId(oppgaveId))
    }

    @Test
    fun `finner hendelseId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        assertEquals(HENDELSE_ID, oppgaveDao.finnHendelseId(oppgaveId))
    }

    @Test
    fun `finner oppgaveId ved hjelp av fødselsnummer`() {
        nyPerson()
        assertEquals(oppgaveId, oppgaveDao.finnOppgaveId(FNR))
    }

    @Test
    fun `finner oppgaver`() {
        nyPerson()
        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN)
        val oppgave = oppgaver.first()
        assertTrue(oppgaver.isNotEmpty())
        assertEquals(oppgaveId.toString(), oppgave.oppgavereferanse)
    }

    @Test
    fun `inkluder risk qa oppgaver bare for supersaksbehandlere`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE, oppgavetype = Oppgavetype.RISK_QA)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_RISK)
        assertTrue(oppgaver.isNotEmpty())
        val oppgave = oppgaver.first()
        assertEquals("RISK_QA", oppgave.oppgavetype)
        assertEquals(oppgaveId.toString(), oppgave.oppgavereferanse)
        assertTrue(oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN).isEmpty())
    }

    @Test
    fun `ekskluder kode-7 oppgaver for vanlige saksbehandlere`() {
        opprettPerson(adressebeskyttelse = Adressebeskyttelse.Fortrolig)
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN)
        assertTrue(oppgaver.isEmpty())
    }

    @Test
    fun `inkluder kode-7 oppgaver bare for noen utvalgte saksbehandlere`() {
        opprettPerson(adressebeskyttelse = Adressebeskyttelse.Fortrolig)
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_KODE7)
        assertTrue(oppgaver.isNotEmpty())
    }

    @Test
    fun `ekskluder oppgaver med strengt fortrolig som adressebeskyttelse for alle saksbehandlere`() {
        opprettPerson(adressebeskyttelse = Adressebeskyttelse.StrengtFortrolig)
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_KODE7)
        assertTrue(oppgaver.isEmpty())
    }

    @Test
    fun `ekskluder oppgaver med strengt fortrolig utland som adressebeskyttelse for alle saksbehandlere`() {
        opprettPerson(adressebeskyttelse = Adressebeskyttelse.StrengtFortroligUtland)
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_KODE7)
        assertTrue(oppgaver.isEmpty())
    }

    @Test
    fun `ekskluder oppgaver med ukjent som adressebeskyttelse for alle saksbehandlere`() {
        opprettPerson(adressebeskyttelse = Adressebeskyttelse.Ukjent)
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_KODE7)
        assertTrue(oppgaver.isEmpty())
    }

    @Test
    fun `sorterer STIKKPRØVE-oppgaver først, så RISK_QA, så resten, eldste først`() {
        opprettPerson()
        opprettArbeidsgiver()

        fun opprettVedtaksperiodeOgOppgave(periodetype: Periodetype, oppgavetype: Oppgavetype = OPPGAVETYPE) {
            val randomUUID = UUID.randomUUID()
            opprettVedtaksperiode(vedtaksperiodeId = randomUUID, periodetype = periodetype)
            opprettOppgave(vedtaksperiodeId = randomUUID, oppgavetype = oppgavetype)
        }

        opprettVedtaksperiodeOgOppgave(Periodetype.FØRSTEGANGSBEHANDLING)
        opprettVedtaksperiodeOgOppgave(Periodetype.FORLENGELSE, Oppgavetype.RISK_QA)
        opprettVedtaksperiodeOgOppgave(Periodetype.FØRSTEGANGSBEHANDLING, Oppgavetype.RISK_QA)
        opprettVedtaksperiodeOgOppgave(Periodetype.OVERGANG_FRA_IT, Oppgavetype.RISK_QA)
        opprettVedtaksperiodeOgOppgave(Periodetype.INFOTRYGDFORLENGELSE)
        opprettVedtaksperiodeOgOppgave(Periodetype.INFOTRYGDFORLENGELSE, Oppgavetype.RISK_QA)
        opprettVedtaksperiodeOgOppgave(Periodetype.FORLENGELSE, Oppgavetype.STIKKPRØVE)
        opprettVedtaksperiodeOgOppgave(Periodetype.OVERGANG_FRA_IT, Oppgavetype.STIKKPRØVE)

        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_RISK)
        oppgaver.filter { it.oppgavetype == "RISK_QA" }.let { riskoppgaver ->
            assertTrue(riskoppgaver.map { it.opprettet }.zipWithNext { a, b -> a <= b }.all { it }) {
                "Oops, skulle ha vært sortert stigende , men er det ikke: $riskoppgaver"
            }
        }
        oppgaver.filter { it.oppgavetype == "STIKKPRØVER" }.let { stikkprøver ->
            assertTrue(stikkprøver.map { it.opprettet }.zipWithNext { a, b -> a <= b }.all { it }) {
                "Oops, skulle ha vært sortert stigende , men er det ikke: $stikkprøver"
            }
        }
        listOf(
            "STIKKPRØVE" to PeriodetypeForApi.FORLENGELSE,
            "STIKKPRØVE" to PeriodetypeForApi.OVERGANG_FRA_IT,
            "RISK_QA" to PeriodetypeForApi.FORLENGELSE,
            "RISK_QA" to PeriodetypeForApi.FØRSTEGANGSBEHANDLING,
            "RISK_QA" to PeriodetypeForApi.OVERGANG_FRA_IT,
            "RISK_QA" to PeriodetypeForApi.INFOTRYGDFORLENGELSE,
            "SØKNAD" to PeriodetypeForApi.FØRSTEGANGSBEHANDLING,
            "SØKNAD" to PeriodetypeForApi.INFOTRYGDFORLENGELSE,
        ).let { ønsketRekkefølge ->
            assertEquals(ønsketRekkefølge.map { it.first }, oppgaver.map { it.oppgavetype })
            assertEquals(ønsketRekkefølge.map { it.second }, oppgaver.map { it.type })
        }
    }

    @Test
    fun `finner oppgave`() {
        nyPerson()
        val oppgave = oppgaveDao.finn(oppgaveId) ?: fail { "Fant ikke oppgave" }
        assertEquals(
            Oppgave(
                oppgaveId,
                OPPGAVETYPE,
                AvventerSaksbehandler,
                VEDTAKSPERIODE,
                utbetalingId = UTBETALING_ID
            ), oppgave
        )
    }

    @Test
    fun `finner oppgave fra utbetalingId`() {
        val utbetalingId = UUID.randomUUID()
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode(
            periodetype = Periodetype.FØRSTEGANGSBEHANDLING,
            inntektskilde = Inntektskilde.EN_ARBEIDSGIVER
        )
        val oppgaveId = insertOppgave(
            utbetalingId = utbetalingId,
            commandContextId = CONTEXT_ID,
            vedtakRef = vedtakId,
            oppgavetype = OPPGAVETYPE
        )
        val oppgave = oppgaveDao.finn(utbetalingId) ?: fail { "Fant ikke oppgave" }
        assertEquals(
            Oppgave(
                oppgaveId,
                OPPGAVETYPE,
                AvventerSaksbehandler,
                VEDTAKSPERIODE,
                utbetalingId = utbetalingId
            ), oppgave
        )
    }

    @Test
    fun `finner ikke oppgave fra utbetalingId dersom oppgaven er invalidert`() {
        val utbetalingId = UUID.randomUUID()
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode(
            periodetype = Periodetype.FØRSTEGANGSBEHANDLING,
            inntektskilde = Inntektskilde.EN_ARBEIDSGIVER
        )
        insertOppgave(
            utbetalingId = utbetalingId,
            commandContextId = CONTEXT_ID,
            vedtakRef = vedtakId,
            oppgavetype = OPPGAVETYPE,
            status = Invalidert
        )
        val oppgave = oppgaveDao.finn(utbetalingId)
        assertNull(oppgave)
    }

    @Test
    fun `kan hente oppgave selv om utbetalingId mangler`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode(
            periodetype = Periodetype.FØRSTEGANGSBEHANDLING,
            inntektskilde = Inntektskilde.EN_ARBEIDSGIVER
        )
        val oppgaveId = insertOppgave(
            utbetalingId = null,
            commandContextId = CONTEXT_ID,
            vedtakRef = vedtakId,
            oppgavetype = OPPGAVETYPE
        )
        val oppgave = oppgaveDao.finn(oppgaveId) ?: fail { "Fant ikke oppgave" }
        assertEquals(
            Oppgave(oppgaveId, OPPGAVETYPE, AvventerSaksbehandler, VEDTAKSPERIODE, utbetalingId = null),
            oppgave
        )
    }

    @Test
    fun `finner vedtaksperiodeId`() {
        nyPerson()
        val actual = oppgaveDao.finnVedtaksperiodeId(oppgaveId)
        assertEquals(VEDTAKSPERIODE, actual)
    }

    @Test
    fun `finner oppgaver med tildeling`() {
        nyPerson()
        assertEquals(
            null,
            oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN)
                .first().tildeling?.epost
        )
        saksbehandlerDao.opprettSaksbehandler(
            SAKSBEHANDLER_OID,
            "Navn Navnesen",
            SAKSBEHANDLEREPOST,
            SAKSBEHANDLER_IDENT
        )
        tildelingDao.opprettTildeling(oppgaveId, SAKSBEHANDLER_OID)
        assertEquals(
            SAKSBEHANDLEREPOST, oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN).first().tildeling?.epost
        )
        assertEquals(
            SAKSBEHANDLEREPOST, oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN).first().tildeling?.epost
        )
        assertEquals(
            false,
            oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN)
                .first().tildeling?.påVent
        )
        assertEquals(
            SAKSBEHANDLER_OID, oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_INGEN).first().tildeling?.oid
        )
    }

    @Test
    fun `oppdatere oppgave`() {
        val nyStatus = Ferdigstilt
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        oppgaveDao.updateOppgave(oppgaveId, nyStatus, SAKSBEHANDLEREPOST, SAKSBEHANDLER_OID)
        assertEquals(1, oppgave().size)
        oppgave().first().assertEquals(
            LocalDate.now(),
            OPPGAVETYPE,
            nyStatus,
            SAKSBEHANDLEREPOST,
            SAKSBEHANDLER_OID,
            vedtakId,
            CONTEXT_ID
        )
    }

    @Test
    fun `sjekker om det fins aktiv oppgave`() {
        nyPerson()
        oppgaveDao.updateOppgave(oppgaveId, AvventerSaksbehandler, null, null)
        assertTrue(oppgaveDao.venterPåSaksbehandler(oppgaveId))

        oppgaveDao.updateOppgave(oppgaveId, Ferdigstilt, null, null)
        assertFalse(oppgaveDao.venterPåSaksbehandler(oppgaveId))
    }

    @Test
    fun `sjekker om det fins aktiv oppgave med to oppgaver`() {
        nyPerson()
        oppgaveDao.updateOppgave(oppgaveId, AvventerSaksbehandler)

        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)
        oppgaveDao.updateOppgave(oppgaveId, AvventerSaksbehandler)

        assertTrue(oppgaveDao.harGyldigOppgave(UTBETALING_ID))
    }

    @Test
    fun `sjekker at det ikke fins ferdigstilt oppgave`() {
        nyPerson()
        oppgaveDao.updateOppgave(oppgaveId, AvventerSaksbehandler)

        assertFalse(oppgaveDao.harFerdigstiltOppgave(VEDTAKSPERIODE))
    }

    @Test
    fun `sjekker at det fins ferdigstilt oppgave`() {
        nyPerson()
        oppgaveDao.updateOppgave(oppgaveId, Ferdigstilt)

        assertTrue(oppgaveDao.harFerdigstiltOppgave(VEDTAKSPERIODE))
    }


    @Test
    fun `finner alle oppgaver knyttet til vedtaksperiodeId`() {
        nyPerson()
        opprettOppgave(vedtaksperiodeId = VEDTAKSPERIODE)
        val oppgaver = oppgaveDao.finnAktive(VEDTAKSPERIODE)
        assertEquals(2, oppgaver.size)
    }

    @Test
    fun `finner ikke oppgaver knyttet til andre vedtaksperiodeider`() {
        val v2 = UUID.randomUUID()
        nyPerson()
        opprettVedtaksperiode(v2)
        opprettOppgave(vedtaksperiodeId = v2)
        assertEquals(1, oppgaveDao.finnAktive(VEDTAKSPERIODE).size)
    }

    @Test
    fun `henter fødselsnummeret til personen en oppgave gjelder for`() {
        nyPerson()
        val fødselsnummer = oppgaveDao.finnFødselsnummer(oppgaveId)
        assertEquals(fødselsnummer, FNR)
    }

    @Test
    fun `en oppgave har riktig oppgavetype og inntektskilde`() {
        nyPerson(inntektskilde = Inntektskilde.FLERE_ARBEIDSGIVERE)
        val oppgaver = oppgaveDao.finnOppgaver(SAKSBEHANDLERTILGANGER_MED_RISK)
        assertEquals(PeriodetypeForApi.FØRSTEGANGSBEHANDLING, oppgaver.first().type)
        assertEquals(InntektskildeForApi.FLERE_ARBEIDSGIVERE, oppgaver.first().inntektskilde)
    }

    @Test
    fun `oppretter oppgaver med riktig oppgavetype for alle oppgavetype-verdier`() {
        Oppgavetype.values().forEach {
            assertDoesNotThrow({
                insertOppgave(
                    commandContextId = UUID.randomUUID(),
                    oppgavetype = it,
                    utbetalingId = null
                )
            }, "Oppgavetype-enumen mangler verdien $it. Kjør migrering: ALTER TYPE oppgavetype ADD VALUE '$it';")
        }
    }

    @Test
    fun `setter ikke trenger Totrinnsvurdering`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        opprettSaksbehandler()

        assertNotEquals(null, oppgaveDao.setTrengerTotrinnsvurdering(VEDTAKSPERIODE))
    }

    @Test
    fun `setter trenger Totrinnsvurdering`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        opprettSaksbehandler()

        assertFalse(trengerTotrinnsvurdering())

        oppgaveDao.setTrengerTotrinnsvurdering(VEDTAKSPERIODE)

        assertTrue(trengerTotrinnsvurdering())
    }

    @Test
    fun `henter ut trenger Totrinnsvurdering`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        opprettSaksbehandler()

        assertFalse(trengerTotrinnsvurdering())

        oppgaveDao.setTrengerTotrinnsvurdering(VEDTAKSPERIODE)

        assertTrue(oppgaveDao.trengerTotrinnsvurdering(VEDTAKSPERIODE))
    }


    @Test
    fun `henter ut tidligere saksbehandlerOid er null`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        opprettSaksbehandler()

        assertNull(oppgaveDao.hentTidligereSaksbehandlerOid(VEDTAKSPERIODE))
    }

    @Test
    fun `henter ut tidligere saksbehandlerOid`() {
        val tidligereSaksbehandlerOid = UUID.randomUUID()

        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        opprettSaksbehandler()
        oppgaveDao.setBeslutterOppgave(oppgaveId, true, false, false, tidligereSaksbehandlerOid)

        assertEquals(tidligereSaksbehandlerOid, oppgaveDao.hentTidligereSaksbehandlerOid(VEDTAKSPERIODE))
    }

    @Test
    fun `overstyr inntekt - utbetalt 2 perioder, finner vedtaksperiodeId_2`() {
        val vedtaksperiodeId_1 = UUID.randomUUID()
        val vedtaksperiodeId_2 = UUID.randomUUID()

        opprettPerson()
        opprettArbeidsgiver()

        opprettVedtaksperiode(vedtaksperiodeId = vedtaksperiodeId_1)
        opprettOppgave(vedtaksperiodeId = vedtaksperiodeId_1)
        val oppgaveId_1 = oppgaveDao.finnOppgaveId(vedtaksperiodeId_1)!!
        oppgaveDao.updateOppgave(oppgaveId_1, Ferdigstilt)

        opprettVedtaksperiode(vedtaksperiodeId = vedtaksperiodeId_2, fom = TOM.plusDays(1), tom = TOM.plusDays(10))
        opprettOppgave(vedtaksperiodeId = vedtaksperiodeId_2)
        val oppgaveId_2 = oppgaveDao.finnOppgaveId(vedtaksperiodeId_2)!!
        oppgaveDao.updateOppgave(oppgaveId_2, Ferdigstilt)

        val vedtaksperiodeIdFerdigstilt = oppgaveDao.finnNyesteVedtaksperiodeIdMedStatusForSkjæringstidspunkt(FNR, ORGNUMMER, FOM, Ferdigstilt)?.vedtaksperiodeId
        val vedtaksperiodeIdTilGodkjenning = oppgaveDao.finnNyesteVedtaksperiodeIdMedStatusForSkjæringstidspunkt(FNR, ORGNUMMER, FOM, AvventerSaksbehandler)?.vedtaksperiodeId
        assertEquals(vedtaksperiodeIdFerdigstilt, vedtaksperiodeId_2)
        assertNull(vedtaksperiodeIdTilGodkjenning)
    }

    @Test
    fun `overstyr inntekt - ingen utbetalte perioder, finner for aktiv oppgave`() {
        val vedtaksperiodeId_1 = UUID.randomUUID()

        opprettPerson()
        opprettArbeidsgiver()

        opprettVedtaksperiode(vedtaksperiodeId = vedtaksperiodeId_1)
        opprettOppgave(vedtaksperiodeId = vedtaksperiodeId_1)

        val vedtaksperiodeIdFerdigstilt = oppgaveDao.finnNyesteVedtaksperiodeIdMedStatusForSkjæringstidspunkt(FNR, ORGNUMMER, FOM, Ferdigstilt)?.vedtaksperiodeId
        val vedtaksperiodeIdTilGodkjenning = oppgaveDao.finnNyesteVedtaksperiodeIdMedStatusForSkjæringstidspunkt(FNR, ORGNUMMER, FOM, AvventerSaksbehandler)?.vedtaksperiodeId
        assertEquals(vedtaksperiodeIdTilGodkjenning, vedtaksperiodeId_1)
        assertNull(vedtaksperiodeIdFerdigstilt)
    }

    @Test
    fun `overstyr inntekt - utbetalt 1 av 2 perioder, finner vedtaksperiodeId_1`() {
        val vedtaksperiodeId_1 = UUID.randomUUID()
        val vedtaksperiodeId_2 = UUID.randomUUID()

        opprettPerson()
        opprettArbeidsgiver()

        opprettVedtaksperiode(vedtaksperiodeId = vedtaksperiodeId_1)
        opprettOppgave(vedtaksperiodeId = vedtaksperiodeId_1)
        val oppgaveId_1 = oppgaveDao.finnOppgaveId(vedtaksperiodeId_1)!!
        oppgaveDao.updateOppgave(oppgaveId_1, Ferdigstilt)

        opprettVedtaksperiode(vedtaksperiodeId = vedtaksperiodeId_2, fom = TOM.plusDays(1), tom = TOM.plusDays(10))
        opprettOppgave(vedtaksperiodeId = vedtaksperiodeId_2)

        val vedtaksperiodeIdFerdigstilt = oppgaveDao.finnNyesteVedtaksperiodeIdMedStatusForSkjæringstidspunkt(FNR, ORGNUMMER, FOM, Ferdigstilt)?.vedtaksperiodeId
        val vedtaksperiodeIdTilGodkjenning = oppgaveDao.finnNyesteVedtaksperiodeIdMedStatusForSkjæringstidspunkt(FNR, ORGNUMMER, FOM, AvventerSaksbehandler)?.vedtaksperiodeId
        assertEquals(vedtaksperiodeIdFerdigstilt, vedtaksperiodeId_1)
        assertEquals(vedtaksperiodeIdTilGodkjenning, vedtaksperiodeId_2)
    }

    @Test
    fun `overstyr arbeidsforhold - finner neste aktive vedtaksperiode for skjæringstidspunkt`() {
        val vedtaksperiodeId_1 = UUID.randomUUID()
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode(vedtaksperiodeId = vedtaksperiodeId_1)
        opprettOppgave(vedtaksperiodeId = vedtaksperiodeId_1)
        val vedtaksperiodeId = oppgaveDao.finnAktivVedtaksperiodeId(FNR)
        assertEquals(vedtaksperiodeId, vedtaksperiodeId_1)
    }

    @Test
    fun `overstyr tidslinje - finner vedtaksperiodeId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave(contextId = CONTEXT_ID)
        assertEquals(
            VEDTAKSPERIODE,
            oppgaveDao.finnNyesteVedtaksperiodeIdMedStatus(FNR, ORGNUMMER, FOM, AvventerSaksbehandler)?.vedtaksperiodeId
        )
        assertNull(oppgaveDao.finnNyesteVedtaksperiodeIdMedStatus(FNR, ORGNUMMER, FOM, Ferdigstilt)?.vedtaksperiodeId)
    }

    @Test
    fun `overstyr tidslinje - forlengelse`() {
        opprettPerson()
        opprettArbeidsgiver()

        val vedtaksperiodeId = UUID.randomUUID()
        opprettVedtaksperiode(
            vedtaksperiodeId,
            1.januar,
            31.januar,
            Periodetype.FØRSTEGANGSBEHANDLING
        )
        opprettOppgave(contextId = CONTEXT_ID, vedtaksperiodeId = vedtaksperiodeId)
        oppgaveDao.updateOppgave(oppgaveId, Ferdigstilt, null, null)

        val vedtaksperiodeId2 = UUID.randomUUID()
        opprettVedtaksperiode(
            vedtaksperiodeId2,
            1.februar,
            28.februar,
            Periodetype.FORLENGELSE
        )
        opprettOppgave(contextId = CONTEXT_ID, vedtaksperiodeId = vedtaksperiodeId2)

        assertEquals(vedtaksperiodeId2, oppgaveDao.finnNyesteVedtaksperiodeIdMedStatus(FNR, ORGNUMMER, 31.januar, AvventerSaksbehandler)?.vedtaksperiodeId)
        assertEquals(vedtaksperiodeId, oppgaveDao.finnNyesteVedtaksperiodeIdMedStatus(FNR, ORGNUMMER, 31.januar, Ferdigstilt)?.vedtaksperiodeId)
        assertEquals(vedtaksperiodeId2, oppgaveDao.finnNyesteVedtaksperiodeIdMedStatus(FNR, ORGNUMMER, 28.februar, AvventerSaksbehandler)?.vedtaksperiodeId)
        assertNull(oppgaveDao.finnNyesteVedtaksperiodeIdMedStatus(FNR, ORGNUMMER, 28.februar, Ferdigstilt))
    }

    private fun trengerTotrinnsvurdering(): Boolean = sessionOf(dataSource).use {
        it.run(
            queryOf(
                "SELECT totrinnsvurdering FROM oppgave"
            ).map { row -> row.boolean("totrinnsvurdering") }.asSingle
        )
    } ?: false

    private fun oppgave() =
        sessionOf(dataSource).use {
            it.run(queryOf("SELECT * FROM oppgave ORDER BY id DESC").map {
                OppgaveAssertions(
                    oppdatert = it.localDate("oppdatert"),
                    type = enumValueOf(it.string("type")),
                    status = enumValueOf(it.string("status")),
                    ferdigstiltAv = it.stringOrNull("ferdigstilt_av"),
                    ferdigstiltAvOid = it.stringOrNull("ferdigstilt_av_oid")?.let(UUID::fromString),
                    vedtakRef = it.longOrNull("vedtak_ref"),
                    commandContextId = it.stringOrNull("command_context_id")?.let(UUID::fromString)
                )
            }.asList)
        }

    private fun insertOppgave(
        commandContextId: UUID,
        oppgavetype: Oppgavetype,
        vedtakRef: Long? = null,
        utbetalingId: UUID?,
        status: Oppgavestatus = AvventerSaksbehandler
    ) = requireNotNull(sessionOf(dataSource, returnGeneratedKey = true).use {
        it.run(
            queryOf(
                """
                INSERT INTO oppgave(oppdatert, type, status, ferdigstilt_av, ferdigstilt_av_oid, vedtak_ref, command_context_id, utbetaling_id)
                VALUES (now(), CAST(? as oppgavetype), CAST(? as oppgavestatus), ?, ?, ?, ?, ?);
            """,
                oppgavetype.name,
                status.name,
                null,
                null,
                vedtakRef,
                commandContextId,
                utbetalingId
            ).asUpdateAndReturnGeneratedKey
        )
    }) { "Kunne ikke opprette oppgave" }

    private class OppgaveAssertions(
        private val oppdatert: LocalDate,
        private val type: Oppgavetype,
        private val status: Oppgavestatus,
        private val ferdigstiltAv: String?,
        private val ferdigstiltAvOid: UUID?,
        private val vedtakRef: Long?,
        private val commandContextId: UUID?
    ) {
        fun assertEquals(
            forventetOppdatert: LocalDate,
            forventetType: Oppgavetype,
            forventetStatus: Oppgavestatus,
            forventetFerdigstilAv: String?,
            forventetFerdigstilAvOid: UUID?,
            forventetVedtakRef: Long?,
            forventetCommandContextId: UUID
        ) {
            assertEquals(forventetOppdatert, oppdatert)
            assertEquals(forventetType, type)
            assertEquals(forventetStatus, status)
            assertEquals(forventetFerdigstilAv, ferdigstiltAv)
            assertEquals(forventetFerdigstilAvOid, ferdigstiltAvOid)
            assertEquals(forventetVedtakRef, vedtakRef)
            assertEquals(forventetCommandContextId, commandContextId)
        }
    }
}
