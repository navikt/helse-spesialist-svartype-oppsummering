package no.nav.helse.spesialist.api.utbetaling

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spesialist.api.saksbehandler.Saksbehandler

@JsonIgnoreProperties
internal data class AnnulleringDto(
    val aktørId: String,
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val fagsystemId: String,
    val begrunnelser: List<String> = emptyList(),
    val kommentar: String?
)

internal data class AnnulleringKafkaDto(
    val aktørId: String,
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val fagsystemId: String,
    val saksbehandler: Saksbehandler,
    val begrunnelser: List<String> = emptyList(),
    val kommentar: String?
) {
    internal fun somKafkaMessage(): JsonMessage {
        return JsonMessage.newMessage(
            "annullering", mutableMapOf(
                "fødselsnummer" to fødselsnummer,
                "organisasjonsnummer" to organisasjonsnummer,
                "aktørId" to aktørId,
                "saksbehandler" to saksbehandler.json().toMutableMap(),
                "fagsystemId" to fagsystemId,
                "begrunnelser" to begrunnelser,
            ).apply {
                compute("kommentar") { _, _ -> kommentar }
            }
        )
    }
}