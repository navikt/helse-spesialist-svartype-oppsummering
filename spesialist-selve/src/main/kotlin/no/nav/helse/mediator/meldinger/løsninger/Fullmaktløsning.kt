package no.nav.helse.mediator.meldinger.løsninger

import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.SpesialistRiver
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class Fullmaktløsning(
    val harFullmakt: Boolean,
) {
    internal class FullmaktRiver(
        private val meldingMediator: MeldingMediator,
    ) : SpesialistRiver {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        override fun validations() =
            River.PacketValidation {
                it.demandValue("@event_name", "behov")
                it.demandValue("@final", true)
                it.demandAll("@behov", listOf("Fullmakt"))
                it.demandKey("contextId")
                it.demandKey("hendelseId")
                it.demandKey("fødselsnummer")
                it.requireKey("@id")
                it.require("@opprettet") { node -> node.asLocalDateTime() }
                it.requireArray("@løsning") {
                    require("fullmakt") {
                        interestedIn("gyldigFraOgMed", "gyldigTilOgMed")
                    }
                }
            }

        override fun onPacket(
            packet: JsonMessage,
            context: MessageContext,
        ) {
            sikkerLogg.info("Mottok melding Fullmakt:\n{}", packet.toJson())
            val contextId = UUID.fromString(packet["contextId"].asText())
            val hendelseId = UUID.fromString(packet["hendelseId"].asText())

            val nå = LocalDate.now()
            val harFullmakt = packet["@løsning"].filter { fullmaktNode ->
                val fullmakt = fullmaktNode.path("fullmakt")
                fullmakt.size() > 0 &&
                        fullmakt["gyldigFraOgMed"].asLocalDate().isSameOrBefore(nå) &&
                        fullmakt["gyldigTilOgMed"].asLocalDate().isSameOrAfter(nå)
            }.isNotEmpty()

            val fullmaktløsning = Fullmaktløsning(
                harFullmakt = harFullmakt,
            )

            meldingMediator.løsning(
                hendelseId = hendelseId,
                contextId = contextId,
                behovId = UUID.fromString(packet["@id"].asText()),
                løsning = fullmaktløsning,
                context = context,
            )
        }
    }
}

fun LocalDate.isSameOrBefore(other: LocalDate) = this.isEqual(other) || this.isBefore(other)
fun LocalDate.isSameOrAfter(other: LocalDate) = this.isEqual(other) || this.isAfter(other)
