package no.nav.helse.spesialist.api.endepunkter

import io.ktor.http.HttpStatusCode
import java.util.UUID
import no.nav.helse.spesialist.api.AbstractE2ETest
import no.nav.helse.spesialist.api.februar
import no.nav.helse.spesialist.api.januar
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrArbeidsforholdHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrArbeidsforholdHandlingFraApi.ArbeidsforholdDto
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrInntektOgRefusjonHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrInntektOgRefusjonHandlingFraApi.OverstyrArbeidsgiverDto.RefusjonselementDto
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrTidslinjeHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrTidslinjeHandlingFraApi.OverstyrDagDto
import no.nav.helse.spesialist.api.saksbehandler.handlinger.SkjønnsfastsettSykepengegrunnlagHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.SkjønnsfastsettSykepengegrunnlagHandlingFraApi.SkjønnsfastsattArbeidsgiverDto.SkjønnsfastsettingstypeDto
import no.nav.helse.spesialist.api.saksbehandler.handlinger.SubsumsjonDto
import org.junit.jupiter.api.Test

internal class OverstyringApiTest: AbstractE2ETest() {

    @Test
    fun `overstyr tidslinje`() {
        val overstyring = OverstyrTidslinjeHandlingFraApi(
            vedtaksperiodeId = UUID.randomUUID(),
            organisasjonsnummer = ORGANISASJONSNUMMER,
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR_ID,
            begrunnelse = "en begrunnelse",
            dager = listOf(
                OverstyrDagDto(dato = 10.januar, type = "Feriedag", fraType = "Sykedag", grad = null, fraGrad = 100, null)
            )
        )

        overstyrTidslinje(overstyring)

        assertSisteResponskode(HttpStatusCode.OK)
    }

    @Test
    fun `overstyr tidslinje til arbeidsdag`() {
        val overstyring = OverstyrTidslinjeHandlingFraApi(
            vedtaksperiodeId = UUID.randomUUID(),
            organisasjonsnummer = ORGANISASJONSNUMMER,
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR_ID,
            begrunnelse = "en begrunnelse",
            dager = listOf(
                OverstyrDagDto(dato = 10.januar, type = "Arbeidsdag", fraType = "Sykedag", grad = null, fraGrad = 100, null)
            )
        )

        overstyrTidslinje(overstyring)

        assertSisteResponskode(HttpStatusCode.OK)
    }

    @Test
    fun `overstyr tidslinje fra arbeidsdag`() {
        val overstyring = OverstyrTidslinjeHandlingFraApi(
            vedtaksperiodeId = UUID.randomUUID(),
            organisasjonsnummer = ORGANISASJONSNUMMER,
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR_ID,
            begrunnelse = "en begrunnelse",
            dager = listOf(
                OverstyrDagDto(dato = 10.januar, type = "Sykedag", fraType = "Arbeidsdag", grad = null, fraGrad = 100, null)
            )
        )

        overstyrTidslinje(overstyring)

        assertSisteResponskode(HttpStatusCode.OK)
    }

    @Test
    fun `overstyr arbeidsforhold`() {
        val overstyring = OverstyrArbeidsforholdHandlingFraApi(
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR_ID,
            skjæringstidspunkt = 1.januar,
            overstyrteArbeidsforhold = listOf(
                ArbeidsforholdDto(
                    orgnummer = ORGANISASJONSNUMMER_GHOST,
                    deaktivert = true,
                    begrunnelse = "en begrunnelse",
                    forklaring = "en forklaring"
                )
            )
        )

        overstyrArbeidsforhold(overstyring)

        assertSisteResponskode(HttpStatusCode.OK)
    }

    @Test
    fun `overstyr inntekt og refusjon`() {
        val overstyring = OverstyrInntektOgRefusjonHandlingFraApi(
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR_ID,
            skjæringstidspunkt = 1.januar,
            arbeidsgivere = listOf(
                OverstyrInntektOgRefusjonHandlingFraApi.OverstyrArbeidsgiverDto(
                    organisasjonsnummer = ORGANISASJONSNUMMER,
                    månedligInntekt = 25000.0,
                    fraMånedligInntekt = 25001.0,
                    refusjonsopplysninger = listOf(
                        RefusjonselementDto(1.januar, 31.januar, 25000.0),
                        RefusjonselementDto(1.februar, null, 24000.0),
                    ),
                    fraRefusjonsopplysninger = listOf(
                        RefusjonselementDto(1.januar, 31.januar, 24000.0),
                        RefusjonselementDto(1.februar, null, 23000.0),
                    ),
                    subsumsjon = SubsumsjonDto("8-28", "3", null),
                    begrunnelse = "En begrunnelse",
                    forklaring = "En forklaring"
                ),
            )
        )

        overstyrInntektOgRefusjon(overstyring)

        assertSisteResponskode(HttpStatusCode.OK)
    }

    @Test
    fun `skjønnsfastsetting av sykepengegrunnlag`() {
        val skjonnsfastsetting = SkjønnsfastsettSykepengegrunnlagHandlingFraApi(
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR_ID,
            skjæringstidspunkt = 1.januar,
            arbeidsgivere = listOf(
                SkjønnsfastsettSykepengegrunnlagHandlingFraApi.SkjønnsfastsattArbeidsgiverDto(
                    organisasjonsnummer = ORGANISASJONSNUMMER,
                    årlig = 250000.0,
                    fraÅrlig = 260000.0,
                    årsak = "En årsak",
                    type = SkjønnsfastsettingstypeDto.OMREGNET_ÅRSINNTEKT,
                    begrunnelseMal = "En begrunnelsemal",
                    begrunnelseFritekst = "begrunnelsefritekst",
                    begrunnelseKonklusjon = "En begrunnelsekonklusjon",
                    subsumsjon = SubsumsjonDto("8-28", "3", null),
                    initierendeVedtaksperiodeId = UUID.randomUUID().toString(),
                ),
            )
        )

        skjønnsfastsettingSykepengegrunnlag(skjonnsfastsetting)

        assertSisteResponskode(HttpStatusCode.OK)
    }
}