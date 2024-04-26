package no.nav.helse.modell.person

import no.nav.helse.modell.vedtak.SkjønnsfastsattSykepengegrunnlagDto
import no.nav.helse.modell.vedtaksperiode.VedtaksperiodeDto

data class PersonDto(
    val aktørId: String,
    val fødselsnummer: String,
    val vedtaksperioder: List<VedtaksperiodeDto>,
    val skjønnsfastsatteSykepengegrunnlag: List<SkjønnsfastsattSykepengegrunnlagDto>,
)
