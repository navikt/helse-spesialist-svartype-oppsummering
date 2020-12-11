package no.nav.helse.modell.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.modell.overstyring.Dagtype
import no.nav.helse.modell.vedtak.EnhetDto
import no.nav.helse.modell.vedtak.PersoninfoDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


data class PersonForSpeilDto(
    val utbetalinger: List<UtbetalingForSpeilDto>,
    val aktørId: String,
    val fødselsnummer: String,
    val personinfo: PersoninfoDto,
    val arbeidsgivere: List<ArbeidsgiverForSpeilDto>,
    val infotrygdutbetalinger: JsonNode?,
    val enhet: EnhetDto,
    val saksbehandlerepost: String?
)

data class ArbeidsgiverForSpeilDto(
    val organisasjonsnummer: String,
    val navn: String,
    val id: UUID,
    val vedtaksperioder: List<JsonNode>,
    val overstyringer: List<OverstyringForSpeilDto>
)

data class OverstyringForSpeilDto(
    val hendelseId: UUID,
    val begrunnelse: String,
    val timestamp: LocalDateTime,
    val overstyrteDager: List<OverstyringDagForSpeilDto>,
    val saksbehandlerNavn: String
)

data class OverstyringDagForSpeilDto(
    val dato: LocalDate,
    val dagtype: Dagtype,
    val grad: Int?
)

data class RisikovurderingForSpeilDto(
    val arbeidsuførhetvurdering: List<String>,
    val ufullstendig: Boolean
)

data class UtbetalingForSpeilDto(
    val status: String,
    val arbeidsgiverOppdrag: OppdragForSpeilDto
)

data class OppdragForSpeilDto(
    val fagsystemId: String,
    val utbetalingslinjer: List<UtbetalingslinjeForSpeilDto>
)

data class UtbetalingslinjeForSpeilDto(
    val fom: LocalDate,
    val tom: LocalDate
)
