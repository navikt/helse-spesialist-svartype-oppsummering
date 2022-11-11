package no.nav.helse.mediator.meldinger

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.modell.WarningDao
import no.nav.helse.modell.gosysoppgaver.ÅpneGosysOppgaverDao
import no.nav.helse.modell.gosysoppgaver.ÅpneGosysOppgaverDto
import no.nav.helse.modell.varsel.VarselRepository
import no.nav.helse.modell.varsel.Varselkode
import no.nav.helse.modell.varsel.Varselkode.SB_EX_1
import no.nav.helse.modell.varsel.Varselkode.SB_EX_4
import no.nav.helse.modell.vedtak.Warning
import no.nav.helse.modell.vedtak.WarningKilde
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.tellWarning
import no.nav.helse.tellWarningInaktiv
import org.slf4j.LoggerFactory

internal class ÅpneGosysOppgaverløsning(
    private val opprettet: LocalDateTime,
    private val fødselsnummer: String,
    private val antall: Int?,
    private val oppslagFeilet: Boolean,
) {
    internal fun lagre(åpneGosysOppgaverDao: ÅpneGosysOppgaverDao) {
        åpneGosysOppgaverDao.persisterÅpneGosysOppgaver(
            ÅpneGosysOppgaverDto(
                fødselsnummer = fødselsnummer,
                antall = antall,
                oppslagFeilet = oppslagFeilet,
                opprettet = opprettet
            )
        )
    }

    internal fun evaluer(warningDao: WarningDao, varselRepository: VarselRepository, vedtaksperiodeId: UUID) {
        warningsForOppslagFeilet(warningDao, varselRepository, vedtaksperiodeId)
        warningsForÅpneGosysOppgaver(warningDao, varselRepository, vedtaksperiodeId)
    }

    private fun warningsForOppslagFeilet(warningDao: WarningDao, varselRepository: VarselRepository, vedtaksperiodeId: UUID) {
        val melding = "Kunne ikke sjekke åpne oppgaver på sykepenger i Gosys"

        if (oppslagFeilet) {
            leggTilWarning(warningDao, vedtaksperiodeId, melding)
            leggTilVarsel(varselRepository, vedtaksperiodeId, SB_EX_4)
        } else {
            setEksisterendeWarningInaktive(warningDao, vedtaksperiodeId, melding)
            deaktiverVarsel(varselRepository, vedtaksperiodeId, SB_EX_4)
        }
    }

    private fun warningsForÅpneGosysOppgaver(warningDao: WarningDao, varselRepository: VarselRepository, vedtaksperiodeId: UUID) {
        val melding = "Det finnes åpne oppgaver på sykepenger i Gosys"

        antall?.also {
            if (it > 0) {
                leggTilWarning(warningDao, vedtaksperiodeId, melding)
                leggTilVarsel(varselRepository, vedtaksperiodeId, SB_EX_1)
            } else if (it == 0) {
                setEksisterendeWarningInaktive(warningDao, vedtaksperiodeId, melding)
                deaktiverVarsel(varselRepository, vedtaksperiodeId, SB_EX_1)
            }
        }
    }

    private fun leggTilWarning(warningDao: WarningDao, vedtaksperiodeId: UUID, melding: String) {
        val eksisterendeWarnings = warningDao.finnAktiveWarningsMedMelding(vedtaksperiodeId, melding)
        if (eksisterendeWarnings.isEmpty()) {
            warningDao.leggTilWarning(vedtaksperiodeId, Warning(melding, WarningKilde.Spesialist, LocalDateTime.now()))
            tellWarning(melding)
        }
    }

    private fun setEksisterendeWarningInaktive(warningDao: WarningDao, vedtaksperiodeId: UUID, melding: String) {
        val eksisterendeWarnings = warningDao.finnAktiveWarningsMedMelding(vedtaksperiodeId, melding)
        if (eksisterendeWarnings.isNotEmpty()) {
            warningDao.setWarningMedMeldingInaktiv(vedtaksperiodeId, melding, LocalDateTime.now())
            tellWarningInaktiv(melding)
        }
    }

    private fun leggTilVarsel(varselRepository: VarselRepository, vedtaksperiodeId: UUID, varselkode: Varselkode) {
        varselkode.nyttVarsel(vedtaksperiodeId, varselRepository)
    }

    private fun deaktiverVarsel(varselRepository: VarselRepository, vedtaksperiodeId: UUID, varselkode: Varselkode) {
        varselkode.deaktiverFor(vedtaksperiodeId, varselRepository)
    }

    internal class ÅpneGosysOppgaverRiver(
        rapidsConnection: RapidsConnection,
        private val hendelseMediator: HendelseMediator,
    ) : River.PacketListener {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        init {
            River(rapidsConnection).apply {
                validate {
                    it.demandValue("@event_name", "behov")
                    it.demandValue("@final", true)
                    it.demandAll("@behov", listOf("ÅpneOppgaver"))
                    it.require("@opprettet") { message -> message.asLocalDateTime() }
                    it.requireKey("@id", "contextId", "hendelseId", "fødselsnummer")
                    it.require("@løsning.ÅpneOppgaver.antall") {}
                    it.requireKey("@løsning.ÅpneOppgaver.oppslagFeilet")
                }
            }.register(this)
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            sikkerLogg.info("Mottok melding ÅpneOppgaverMessage:\n{}", packet.toJson())
            val opprettet = packet["@opprettet"].asLocalDateTime()
            val contextId = UUID.fromString(packet["contextId"].asText())
            val hendelseId = UUID.fromString(packet["hendelseId"].asText())
            val fødselsnummer = packet["fødselsnummer"].asText()

            val antall = packet["@løsning.ÅpneOppgaver.antall"].takeUnless { it.isMissingOrNull() }?.asInt()
            val oppslagFeilet = packet["@løsning.ÅpneOppgaver.oppslagFeilet"].asBoolean()

            val åpneGosysOppgaver = ÅpneGosysOppgaverløsning(
                opprettet = opprettet,
                fødselsnummer = fødselsnummer,
                antall = antall,
                oppslagFeilet = oppslagFeilet
            )

            hendelseMediator.løsning(
                hendelseId = hendelseId,
                contextId = contextId,
                behovId = UUID.fromString(packet["@id"].asText()),
                løsning = åpneGosysOppgaver,
                context = context
            )
        }
    }
}
