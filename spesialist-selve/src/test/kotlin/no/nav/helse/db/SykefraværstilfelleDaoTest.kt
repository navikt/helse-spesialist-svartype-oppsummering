package no.nav.helse.db

import DatabaseIntegrationTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.db.SkjønnsfastsettingstypeForDatabase.OMREGNET_ÅRSINNTEKT
import no.nav.helse.db.SkjønnsfastsettingstypeForDatabase.RAPPORTERT_ÅRSINNTEKT
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.modell.sykefraværstilfelle.SkjønnsfastattSykepengegrunnlag
import no.nav.helse.modell.sykefraværstilfelle.Skjønnsfastsettingstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SykefraværstilfelleDaoTest: DatabaseIntegrationTest() {
    private val sykefraværstilfelleDao = SykefraværstilfelleDao(dataSource)

    @Test
    fun `Finn skjønnsfastsatt sykepengegrunnlag`() {
        nyPerson()
        opprettSaksbehandler()
        val hendelseId = UUID.randomUUID()
        testhendelse(hendelseId)
        val tidspunkt = LocalDateTime.now()
        overstyringDao.persisterSkjønnsfastsettingSykepengegrunnlag(
            skjønnsfastsattSykepengegrunnlag = skjønnsfastsattSykepengegrunnlag(
                opprettet = tidspunkt,
                fødselsnummer = FNR,
            ),
            saksbehandlerOid = SAKSBEHANDLER_OID,
        )

        val funnet = sykefraværstilfelleDao.finnSkjønnsfastsatteSykepengegrunnlag(FNR, 1.januar)
        assertEquals(1, funnet.size)
        assertEquals(SkjønnsfastattSykepengegrunnlag(Skjønnsfastsettingstype.OMREGNET_ÅRSINNTEKT, 1.januar, "mal", "fritekst","konklusjon",  tidspunkt), funnet.single())
    }

    @Test
    fun `Finn kun skjønnsfastsatt sykepengegrunnlag for bestemt fnr`() {
        nyPerson()
        val person2 = "111111111111"
        val arbeidsgiver2 = "999999999"
        nyPerson(fødselsnummer = person2, organisasjonsnummer = arbeidsgiver2, vedtaksperiodeId = UUID.randomUUID())
        opprettSaksbehandler()
        val hendelseId1 = UUID.randomUUID()
        val hendelseId2 = UUID.randomUUID()
        testhendelse(hendelseId1)
        testhendelse(hendelseId2)
        val tidspunkt = LocalDateTime.now()
        overstyringDao.persisterSkjønnsfastsettingSykepengegrunnlag(
            skjønnsfastsattSykepengegrunnlag(
                opprettet = tidspunkt,
                fødselsnummer = FNR,
            ),
            saksbehandlerOid = SAKSBEHANDLER_OID
        )
        overstyringDao.persisterSkjønnsfastsettingSykepengegrunnlag(
            skjønnsfastsattSykepengegrunnlag(
                fødselsnummer = person2,
                orgnummer = arbeidsgiver2,
                type = RAPPORTERT_ÅRSINNTEKT,
            ),
            saksbehandlerOid = SAKSBEHANDLER_OID
        )

        val funnet = sykefraværstilfelleDao.finnSkjønnsfastsatteSykepengegrunnlag(FNR, 1.januar)
        assertEquals(1, funnet.size)
        assertEquals(SkjønnsfastattSykepengegrunnlag(Skjønnsfastsettingstype.OMREGNET_ÅRSINNTEKT , 1.januar, "mal", "fritekst", "konklusjon", tidspunkt), funnet.single())
    }

    @Test
    fun `Finner skjønnsfastsatt sykepengegrunnlag kun for aktuelt skjæringstidspunkt`() {
        nyPerson()
        opprettSaksbehandler()
        val hendelseId1 = UUID.randomUUID()
        val hendelseId2 = UUID.randomUUID()
        testhendelse(hendelseId1)
        testhendelse(hendelseId2)
        val tidspunkt = LocalDateTime.now()
        overstyringDao.persisterSkjønnsfastsettingSykepengegrunnlag(
            skjønnsfastsattSykepengegrunnlag(opprettet = tidspunkt),
            saksbehandlerOid = SAKSBEHANDLER_OID,
        )
        overstyringDao.persisterSkjønnsfastsettingSykepengegrunnlag(
            skjønnsfastsattSykepengegrunnlag(skjæringstidspunkt = 1.februar),
            saksbehandlerOid = SAKSBEHANDLER_OID,
        )

        val funnet = sykefraværstilfelleDao.finnSkjønnsfastsatteSykepengegrunnlag(FNR, 1.januar)
        assertEquals(1, funnet.size)
        assertEquals(SkjønnsfastattSykepengegrunnlag(Skjønnsfastsettingstype.OMREGNET_ÅRSINNTEKT, 1.januar, "mal", "fritekst", "konklusjon", tidspunkt), funnet.single())
    }

    private fun skjønnsfastsattSykepengegrunnlag(
        opprettet: LocalDateTime = LocalDateTime.now(),
        fødselsnummer: String = FNR,
        orgnummer: String = ORGNUMMER,
        type: SkjønnsfastsettingstypeForDatabase = OMREGNET_ÅRSINNTEKT,
        skjæringstidspunkt: LocalDate = 1.januar,
    ): SkjønnsfastsattSykepengegrunnlagForDatabase =
        SkjønnsfastsattSykepengegrunnlagForDatabase(
            id = UUID.randomUUID(),
            aktørId = AKTØR,
            fødselsnummer = fødselsnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgivere = listOf(
                SkjønnsfastsattArbeidsgiverForDatabase(
                    organisasjonsnummer = orgnummer,
                    årlig = 1.0,
                    fraÅrlig = 1.0,
                    årsak = "årsak",
                    type = type,
                    begrunnelseMal = "mal",
                    begrunnelseKonklusjon = "konklusjon",
                    begrunnelseFritekst = "fritekst",
                    lovhjemmel = LovhjemmelForDatabase(paragraf = "paragraf", ledd = "ledd", bokstav = "bokstav"),
                    initierendeVedtaksperiodeId = UUID.randomUUID().toString(),
                )
            ),
            opprettet = opprettet
        )
}