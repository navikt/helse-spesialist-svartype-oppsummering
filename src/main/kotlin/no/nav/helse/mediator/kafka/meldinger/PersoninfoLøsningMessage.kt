package no.nav.helse.mediator.kafka.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.mediator.kafka.HendelseMediator
import no.nav.helse.modell.person.HentEnhetLøsning
import no.nav.helse.modell.person.HentInfotrygdutbetalingerLøsning
import no.nav.helse.modell.person.HentPersoninfoLøsning
import no.nav.helse.modell.person.Kjønn
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

internal class PersoninfoLøsningMessage {
    internal class Factory(
        rapidsConnection: RapidsConnection,
        private val spleisbehovMediator: HendelseMediator
    ) : River.PacketListener {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

        init {
            River(rapidsConnection).apply {
                validate {
                    it.demandValue("@event_name", "behov")
                    it.demandValue("@final", true)
                    it.demandAllOrAny("@behov", listOf("HentEnhet", "HentPersoninfo", "HentInfotrygdutbetalinger"))
                    it.requireKey("@løsning", "spleisBehovId")
                }
            }.register(this)
        }

        override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
            sikkerLog.error("forstod ikke HentEnhet, HentPersoninfo eller HentInfotrygdutbetalinger:\n${problems.toExtendedReport()}")
        }

        override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
            val hentEnhet = packet["@løsning"].tilHentEnhetLøsning()
            val hentPersoninfo = packet["@løsning"].tilPersonInfoLøsning()
            val hentInfotrygdutbetalinger = packet["@løsning"].tilInfotrygdutbetalingerLøsning()

            val spleisbehovId = UUID.fromString(packet["spleisBehovId"].asText())

            spleisbehovMediator.håndter(spleisbehovId, hentEnhet, hentPersoninfo, hentInfotrygdutbetalinger)
        }

        private fun JsonNode.tilPersonInfoLøsning() =
            takeIf { hasNonNull("HentPersoninfo") }?.let {
                val hentPersoninfo = it["HentPersoninfo"]
                val fornavn = hentPersoninfo["fornavn"].asText()
                val mellomnavn = hentPersoninfo.takeIf { it.hasNonNull("mellomavn") }?.get("mellomnavn")?.asText()
                val etternavn = hentPersoninfo["etternavn"].asText()
                val fødselsdato = LocalDate.parse(hentPersoninfo["fødselsdato"].asText())
                val kjønn = Kjønn.valueOf(hentPersoninfo["kjønn"].textValue())
                HentPersoninfoLøsning(
                    fornavn,
                    mellomnavn,
                    etternavn,
                    fødselsdato,
                    kjønn
                )
            }

        private fun JsonNode.tilHentEnhetLøsning() =
            takeIf { hasNonNull("HentEnhet") }?.let { HentEnhetLøsning(it["HentEnhet"].asText()) }

        private fun JsonNode.tilInfotrygdutbetalingerLøsning() =
            takeIf { hasNonNull("HentInfotrygdutbetalinger") }?.let { HentInfotrygdutbetalingerLøsning(it["HentInfotrygdutbetalinger"]) }
    }
}
