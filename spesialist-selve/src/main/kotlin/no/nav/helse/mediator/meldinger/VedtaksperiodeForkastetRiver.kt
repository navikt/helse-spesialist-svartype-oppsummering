package no.nav.helse.mediator.meldinger

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class VedtaksperiodeForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val mediator: HendelseMediator
) : River.PacketListener {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

    private companion object {
        private fun uuid(jsonNode: JsonNode): UUID = UUID.fromString(jsonNode.asText())
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_forkastet")
                it.require("@id", ::uuid)
                it.require("vedtaksperiodeId", ::uuid)
                it.requireKey("fødselsnummer")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Forstod ikke vedtaksperiode_forkastet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        val id = UUID.fromString(packet["@id"].asText())
        log.info(
            "Mottok vedtaksperiode forkastet {}, {}",
            StructuredArguments.keyValue("vedtaksperiodeId", vedtaksperiodeId),
            StructuredArguments.keyValue("eventId", id)
        )
        mediator.vedtaksperiodeForkastet(packet, id, vedtaksperiodeId, packet["fødselsnummer"].asText(), context)
    }
}