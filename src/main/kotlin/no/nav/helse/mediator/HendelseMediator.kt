package no.nav.helse.mediator

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.annulleringsteller
import no.nav.helse.mediator.api.*
import no.nav.helse.mediator.api.modell.Saksbehandler
import no.nav.helse.mediator.meldinger.*
import no.nav.helse.modell.*
import no.nav.helse.modell.arbeidsforhold.Arbeidsforholdløsning
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.tildeling.ReservasjonDao
import no.nav.helse.modell.tildeling.TildelingDao
import no.nav.helse.modell.vedtak.Saksbehandleroppgavetype
import no.nav.helse.objectMapper
import no.nav.helse.overstyringsteller
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class HendelseMediator(
    private val rapidsConnection: RapidsConnection,
    private val oppgaveDao: OppgaveDao,
    private val vedtakDao: VedtakDao,
    private val personDao: PersonDao,
    private val commandContextDao: CommandContextDao,
    private val hendelseDao: HendelseDao,
    private val tildelingDao: TildelingDao,
    private val reservasjonDao: ReservasjonDao,
    private val oppgaveMediator: OppgaveMediator,
    private val hendelsefabrikk: IHendelsefabrikk
) : IHendelseMediator {
    private companion object {
        private val log = LoggerFactory.getLogger(HendelseMediator::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    private var shutdown = false
    private val behovMediator = BehovMediator(rapidsConnection, sikkerLogg)

    init {
        DelegatedRapid(rapidsConnection, ::forbered, ::fortsett, ::errorHandler).also {
            Godkjenningsbehov.GodkjenningsbehovRiver(it, this)
            Tilbakerulling.TilbakerullingRiver(it, this)
            HentPersoninfoløsning.PersoninfoRiver(it, this)
            HentEnhetløsning.HentEnhetRiver(it, this)
            HentInfotrygdutbetalingerløsning.InfotrygdutbetalingerRiver(it, this)
            Saksbehandlerløsning.SaksbehandlerløsningRiver(it, this)
            Arbeidsgiverinformasjonløsning.ArbeidsgiverRiver(it, this)
            Arbeidsforholdløsning.ArbeidsforholdRiver(it, this)
            VedtaksperiodeForkastet.VedtaksperiodeForkastetRiver(it, this)
            VedtaksperiodeEndret.VedtaksperiodeEndretRiver(it, this)
            Overstyring.OverstyringRiver(it, this)
            DigitalKontaktinformasjonløsning.DigitalKontaktinformasjonRiver(it, this)
            EgenAnsattløsning.EgenAnsattRiver(it, this)
            ÅpneGosysOppgaverløsning.ÅpneGosysOppgaverRiver(it, this)
            Risikovurderingløsning.V2River(it, this)
            UtbetalingAnnullert.River(it, this)
            OppdaterPersonsnapshot.River(it, this)
            UtbetalingEndret.River(it, this)
            OppgaveMakstidPåminnelse.River(it, this)
            AvbrytSaksbehandling.AvbrytSaksbehandlingRiver(it, this)
        }
    }

    private var løsninger: Løsninger? = null

    // samler opp løsninger
    override fun løsning(
        hendelseId: UUID,
        contextId: UUID,
        behovId: UUID,
        løsning: Any,
        context: RapidsConnection.MessageContext
    ) {
        withMDC(
            mapOf(
                "behovId" to "$behovId"
            )
        ) {
            løsninger(hendelseId, contextId)?.also { it.add(hendelseId, contextId, løsning) }
                ?: log.info(
                    "mottok løsning med behovId=$behovId som ikke kunne brukes fordi kommandoen ikke lengre er suspendert, " +
                        "eller fordi hendelsen $hendelseId er ukjent"
                )
        }
    }

    internal fun håndter(godkjenningDTO: GodkjenningDTO, epost: String, oid: UUID) {
        val contextId = oppgaveDao.finnContextId(godkjenningDTO.oppgavereferanse)
        val hendelseId = oppgaveDao.finnHendelseId(godkjenningDTO.oppgavereferanse)
        val fødselsnummer = hendelseDao.finnFødselsnummer(hendelseId)
        val godkjenningMessage = JsonMessage.newMessage(
            standardfelter("saksbehandler_løsning", fødselsnummer).apply {
                put("oppgaveId", godkjenningDTO.oppgavereferanse)
                put("hendelseId", hendelseId)
                put("godkjent", godkjenningDTO.godkjent)
                put("saksbehandlerident", godkjenningDTO.saksbehandlerIdent)
                put("saksbehandleroid", oid)
                put("saksbehandlerepost", epost)
                put("godkjenttidspunkt", LocalDateTime.now())
                godkjenningDTO.årsak?.let { put("årsak", it) }
                godkjenningDTO.begrunnelser?.let { put("begrunnelser", it) }
                godkjenningDTO.kommentar?.let { put("kommentar", it) }
            }).also {
            sikkerLogg.info("Publiserer saksbehandler-løsning: ${it.toJson()}")
        }
        log.info(
            "Publiserer saksbehandler-løsning for {}. {}. {}",
            keyValue("oppgaveId", godkjenningDTO.oppgavereferanse),
            keyValue("hendelseId", hendelseId)
        )
        rapidsConnection.publish(godkjenningMessage.toJson())

        val internOppgaveMediator = OppgaveMediator(oppgaveDao, vedtakDao, tildelingDao, reservasjonDao)
        internOppgaveMediator.avventerSystem(godkjenningDTO.oppgavereferanse, godkjenningDTO.saksbehandlerIdent, oid)
        internOppgaveMediator.lagreOppgaver(rapidsConnection, hendelseId, contextId)
    }

    internal fun sendMeldingPåTopic(melding: JsonNode) {
        val fnr = melding["fødselsnummer"].asText()
        val rawJson = objectMapper.writeValueAsString(melding)
        sikkerLogg.info("Manuell publisering av melding for fnr=${fnr}, melding=${rawJson}")
        rapidsConnection.publish(fnr, rawJson)
    }

    internal fun oppdaterPersonsnapshotForSisteUkesAnnullerteOgForkastedeHendelser() {
        val fodselsnummerListe = hendelseDao.finnSisteUkesAnnullerteOgForkastede()
        log.info("Initierer oppdatering av personSnapshot for ${fodselsnummerListe.size} personer")
        fodselsnummerListe.forEach { håndter(OppdaterPersonsnapshotDto(it)) }
        log.info("Ferdig oppdatert personSnapshots")
    }

    override fun vedtaksperiodeEndret(
        message: JsonMessage,
        id: UUID,
        vedtaksperiodeId: UUID,
        fødselsnummer: String,
        context: RapidsConnection.MessageContext
    ) {
        utfør(
            vedtaksperiodeId,
            hendelsefabrikk.vedtaksperiodeEndret(id, vedtaksperiodeId, fødselsnummer, message.toJson()),
            context
        )
    }

    override fun vedtaksperiodeForkastet(
        message: JsonMessage,
        id: UUID,
        vedtaksperiodeId: UUID,
        fødselsnummer: String,
        context: RapidsConnection.MessageContext
    ) {
        utfør(
            vedtaksperiodeId,
            hendelsefabrikk.vedtaksperiodeForkastet(id, vedtaksperiodeId, fødselsnummer, message.toJson()),
            context
        )
    }

    override fun godkjenningsbehov(
        message: JsonMessage,
        id: UUID,
        fødselsnummer: String,
        aktørId: String,
        organisasjonsnummer: String,
        periodeFom: LocalDate,
        periodeTom: LocalDate,
        vedtaksperiodeId: UUID,
        periodetype: Saksbehandleroppgavetype,
        context: RapidsConnection.MessageContext
    ) {
        if (oppgaveDao.harAktivOppgave(vedtaksperiodeId) || vedtakDao.erAutomatiskGodkjent(vedtaksperiodeId)) {
            sikkerLogg.info("vedtaksperiodeId=$vedtaksperiodeId har enten aktiv oppgave eller er automatisk godkjent. Ignorerer godkjenningsbehov med id=$id")
            return
        }
        utfør(
            hendelsefabrikk.godkjenning(
                id,
                fødselsnummer,
                aktørId,
                organisasjonsnummer,
                periodeFom,
                periodeTom,
                vedtaksperiodeId,
                periodetype,
                message.toJson()
            ), context
        )
    }

    override fun saksbehandlerløsning(
        message: JsonMessage,
        id: UUID,
        godkjenningsbehovhendelseId: UUID,
        fødselsnummer: String,
        godkjent: Boolean,
        saksbehandlerident: String,
        saksbehandleroid: UUID,
        saksbehandlerepost: String,
        godkjenttidspunkt: LocalDateTime,
        årsak: String?,
        begrunnelser: List<String>?,
        kommentar: String?,
        oppgaveId: Long,
        context: RapidsConnection.MessageContext
    ) {
        utfør(
            fødselsnummer, hendelsefabrikk.saksbehandlerløsning(
                id,
                godkjenningsbehovhendelseId,
                fødselsnummer,
                godkjent,
                saksbehandlerident,
                saksbehandleroid,
                saksbehandlerepost,
                godkjenttidspunkt,
                årsak,
                begrunnelser,
                kommentar,
                oppgaveId,
                message.toJson()
            ), context
        )
    }

    override fun overstyring(
        message: JsonMessage,
        id: UUID,
        fødselsnummer: String,
        context: RapidsConnection.MessageContext
    ) {
        utfør(fødselsnummer, hendelsefabrikk.overstyring(message.toJson()), context)
    }

    override fun tilbakerulling(
        message: JsonMessage,
        id: UUID,
        fødselsnummer: String,
        vedtaksperiodeIder: List<UUID>,
        context: RapidsConnection.MessageContext
    ) {
        utfør(hendelsefabrikk.tilbakerulling(message.toJson()), context)
    }

    override fun utbetalingAnnullert(
        message: JsonMessage,
        context: RapidsConnection.MessageContext
    ) {
        utfør(hendelsefabrikk.utbetalingAnnullert(message.toJson()), context)
    }

    override fun utbetalingEndret(
        fødselsnummer: String,
        organisasjonsnummer: String,
        message: JsonMessage,
        context: RapidsConnection.MessageContext
    ) {
        utfør(fødselsnummer, hendelsefabrikk.utbetalingEndret(message.toJson()), context)
    }

    override fun oppdaterPersonsnapshot(message: JsonMessage, context: RapidsConnection.MessageContext) {
        utfør(hendelsefabrikk.oppdaterPersonsnapshot(message.toJson()), context)
    }

    override fun påminnelseOppgaveMakstid(
        message: JsonMessage,
        context: RapidsConnection.MessageContext
    ) {
        utfør(hendelsefabrikk.oppgaveMakstidPåminnelse(message.toJson()), context)
    }

    override fun avbrytSaksbehandling(message: JsonMessage, context: RapidsConnection.MessageContext) {
        utfør(hendelsefabrikk.avbrytSaksbehandling(message.toJson()), context)
    }

    fun håndter(overstyringMessage: OverstyringRestDto) {
        overstyringsteller.inc()

        val overstyring = JsonMessage.newMessage(
            standardfelter("overstyr_tidslinje", overstyringMessage.fødselsnummer).apply {
                put("aktørId", overstyringMessage.aktørId)
                put("organisasjonsnummer", overstyringMessage.organisasjonsnummer)
                put("dager", overstyringMessage.dager)
                put("begrunnelse", overstyringMessage.begrunnelse)
                put("saksbehandlerOid", overstyringMessage.saksbehandlerOid)
                put("saksbehandlerNavn", overstyringMessage.saksbehandlerNavn)
                put("saksbehandlerEpost", overstyringMessage.saksbehandlerEpost)
            }
        ).also {
            sikkerLogg.info("Publiserer overstyring:\n${it.toJson()}")
        }

        rapidsConnection.publish(overstyringMessage.fødselsnummer, overstyring.toJson())
    }

    internal fun håndter(tilbakerullingMedSlettingDTO: TilbakerullingMedSlettingDTO) {

        val tilbakerulling = JsonMessage.newMessage(
            standardfelter("rollback_person_delete", tilbakerullingMedSlettingDTO.fødselsnummer).apply {
                put("aktørId", tilbakerullingMedSlettingDTO.aktørId)
            }
        ).also {
            sikkerLogg.info("Publiserer rollback_person_delete for ${tilbakerullingMedSlettingDTO.fødselsnummer}:\n${it.toJson()}")
        }
        rapidsConnection.publish(tilbakerulling.toJson())
    }

    internal fun håndter(tilbakerullingDTO: TilbakerullingDTO) {
        val tilbakerulling = JsonMessage.newMessage(
            standardfelter("rollback_person", tilbakerullingDTO.fødselsnummer).apply {
                put("aktørId", tilbakerullingDTO.aktørId)
                put("personVersjon", tilbakerullingDTO.personVersjon)
            }
        ).also {
            sikkerLogg.info("Publiserer rollback_person for ${tilbakerullingDTO.fødselsnummer}:\n${it.toJson()}")
        }
        rapidsConnection.publish(tilbakerulling.toJson())
    }

    internal fun håndter(annulleringDto: AnnulleringDto, saksbehandler: Saksbehandler) {
        annulleringsteller.inc()

        val annulleringMessage = annulleringDto.run {
            JsonMessage.newMessage(
                standardfelter("annullering", fødselsnummer).apply {
                    putAll(
                        mapOf(
                            "organisasjonsnummer" to organisasjonsnummer,
                            "aktørId" to aktørId,
                            "saksbehandler" to saksbehandler.json(),
                            "fagsystemId" to fagsystemId
                        )
                    )
                }
            )
        }

        rapidsConnection.publish(annulleringDto.fødselsnummer, annulleringMessage.toJson().also {
            sikkerLogg.info(
                "sender annullering for {}, {}\n\t$it",
                keyValue("fødselsnummer", annulleringDto.fødselsnummer),
                keyValue("organisasjonsnummer", annulleringDto.organisasjonsnummer)
            )
        })
    }

    fun håndter(oppdaterPersonsnapshotDto: OppdaterPersonsnapshotDto) {
        rapidsConnection.publish(
            oppdaterPersonsnapshotDto.fødselsnummer,
            JsonMessage.newMessage(
                standardfelter("oppdater_personsnapshot", oppdaterPersonsnapshotDto.fødselsnummer)
            ).toJson()
        )
        sikkerLogg.info("Publiserte event for å be om siste versjon av person: ${oppdaterPersonsnapshotDto.fødselsnummer}")
    }

    fun shutdown() {
        shutdown = true
    }

    private fun forbered() {
        løsninger = null
    }

    private fun løsninger(hendelseId: UUID, contextId: UUID): Løsninger? {
        return løsninger ?: run {
            val hendelse = hendelseDao.finn(hendelseId, hendelsefabrikk)
            val commandContext = commandContextDao.finnSuspendert(contextId)
            if (hendelse == null || commandContext == null) {
                log.info("finner ikke hendelse med id=$hendelseId eller command context med id=$contextId; ignorerer melding")
                return null
            }
            Løsninger(hendelse, contextId, commandContext).also { løsninger = it }
        }
    }

    // fortsetter en command (resume) med oppsamlet løsninger
    private fun fortsett(message: String, context: RapidsConnection.MessageContext) {
        løsninger?.fortsett(this, message, context)
    }

    private fun errorHandler(err: Exception, message: String) {
        log.error("alvorlig feil: ${err.message} (se sikkerlogg for melding)", err)
        sikkerLogg.error("alvorlig feil: ${err.message}\n\t$message", err)
    }

    private fun nyContext(hendelse: Hendelse, contextId: UUID) = CommandContext(contextId).apply {
        hendelseDao.opprett(hendelse)
        opprett(commandContextDao, hendelse)
    }

    private fun utfør(vedtaksperiodeId: UUID, hendelse: Hendelse, messageContext: RapidsConnection.MessageContext) {
        if (!hendelseDao.harKoblingTil(vedtaksperiodeId)) return log.debug("ignorerer hendelseId=${hendelse.id} fordi vi ikke kjenner til $vedtaksperiodeId")
        return utfør(hendelse, messageContext)
    }

    private fun utfør(fødselsnummer: String, hendelse: Hendelse, messageContext: RapidsConnection.MessageContext) {
        if (personDao.findPersonByFødselsnummer(fødselsnummer) == null) return log.debug("ignorerer hendelseId=${hendelse.id} fordi vi ikke kjenner til personen")
        return utfør(hendelse, messageContext)
    }

    private fun utfør(hendelse: Hendelse, messageContext: RapidsConnection.MessageContext) {
        val contextId = UUID.randomUUID()
        log.info("oppretter ny kommandokontekst med context_id=$contextId for hendelse_id=${hendelse.id}")
        utfør(hendelse, nyContext(hendelse, contextId), contextId, messageContext)
    }

    private fun utfør(
        hendelse: Hendelse,
        context: CommandContext,
        contextId: UUID,
        messageContext: RapidsConnection.MessageContext
    ) {
        withMDC(
            mapOf(
                "context_id" to "$contextId",
                "hendelse_id" to "${hendelse.id}",
                "vedtaksperiode_id" to "${hendelse.vedtaksperiodeId() ?: "N/A"}"
            )
        ) {
            try {
                log.info("utfører ${hendelse::class.simpleName} med context_id=$contextId for hendelse_id=${hendelse.id}")
                if (context.utfør(commandContextDao, hendelse)) log.info("kommando er utført ferdig")
                else log.info("${hendelse::class.simpleName} er suspendert")
                behovMediator.håndter(hendelse, context, contextId)
                oppgaveMediator.lagreOgTildelOppgaver(hendelse, messageContext, contextId)
            } catch (err: Exception) {
                log.warn(
                    "Feil ved kjøring av ${hendelse::class.simpleName}: contextId={}, message={}",
                    contextId,
                    err.message,
                    err
                )
                hendelse.undo(context)
                throw err
            } finally {
                log.info("utført ${hendelse::class.simpleName} med context_id=$contextId for hendelse_id=${hendelse.id}")
            }
        }
    }

    private class Løsninger(
        private val hendelse: Hendelse,
        private val contextId: UUID,
        private val commandContext: CommandContext
    ) {
        fun add(hendelseId: UUID, contextId: UUID, løsning: Any) {
            check(hendelseId == hendelse.id)
            check(contextId == this.contextId)
            commandContext.add(løsning)
        }

        fun fortsett(mediator: HendelseMediator, message: String, context: RapidsConnection.MessageContext) {
            log.info("fortsetter utførelse av kommandokontekst pga. behov_id=${hendelse.id} med context_id=$contextId for hendelse_id=${hendelse.id}")
            sikkerLogg.info(
                "fortsetter utførelse av kommandokontekst pga. behov_id=${hendelse.id} med context_id=$contextId for hendelse_id=${hendelse.id}.\n" +
                    "Innkommende melding:\n\t$message"
            )
            mediator.utfør(hendelse, commandContext, contextId, context)
        }
    }
}

internal fun standardfelter(hendelsetype: String, fødselsnummer: String) = mutableMapOf<String, Any>(
    "@event_name" to hendelsetype,
    "@opprettet" to LocalDateTime.now(),
    "@id" to UUID.randomUUID(),
    "fødselsnummer" to fødselsnummer
)
