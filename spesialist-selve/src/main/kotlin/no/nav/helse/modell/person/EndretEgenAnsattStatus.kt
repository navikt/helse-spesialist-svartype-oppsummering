package no.nav.helse.modell.person

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.mediator.meldinger.PersonmeldingOld
import no.nav.helse.mediator.oppgave.OppgaveMediator
import no.nav.helse.modell.egenansatt.EgenAnsattDao
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.ikkesuspenderendeCommand
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime

internal class EndretEgenAnsattStatus private constructor(
    override val id: UUID,
    private val fødselsnummer: String,
    val erEgenAnsatt: Boolean,
    val opprettet: LocalDateTime,
    private val json: String,
) : PersonmeldingOld {

    internal constructor(packet: JsonMessage): this(
        id = UUID.fromString(packet["@id"].asText()),
        fødselsnummer = packet["fødselsnummer"].asText(),
        erEgenAnsatt = packet["skjermet"].asBoolean(),
        opprettet = packet["@opprettet"].asLocalDateTime(),
        json = packet.toJson()
    )

    override fun fødselsnummer(): String = fødselsnummer
    override fun toJson(): String = json
}

internal class EndretEgenAnsattStatusCommand(
    private val fødselsnummer: String,
    erEgenAnsatt: Boolean,
    opprettet: LocalDateTime,
    egenAnsattDao: EgenAnsattDao,
    oppgaveMediator: OppgaveMediator,
) : MacroCommand() {
    override val commands: List<Command> = listOf(
        ikkesuspenderendeCommand("lagreEgenAnsattStatus") {
            egenAnsattDao.lagre(fødselsnummer, erEgenAnsatt, opprettet)
        },
        ikkesuspenderendeCommand("endretEgenAnsattStatus") {
            oppgaveMediator.endretEgenAnsattStatus(erEgenAnsatt, fødselsnummer)
        },
    )
}
