package no.nav.helse.modell.oppgave

import java.util.Objects
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.helse.Gruppe
import no.nav.helse.Tilgangskontroll
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import no.nav.helse.spesialist.api.oppgave.Oppgavetype
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkType
import org.slf4j.LoggerFactory

class Oppgave private constructor(
    private val id: Long,
    private val type: Oppgavetype,
    private var status: Oppgavestatus,
    private val vedtaksperiodeId: UUID,
    private val utbetalingId: UUID
) {

    private var ferdigstiltAvIdent: String? = null
    private var ferdigstiltAvOid: UUID? = null
    private val egenskaper = mutableListOf<Oppgavetype>()

    constructor(
        id: Long,
        type: Oppgavetype,
        status: Oppgavestatus,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        ferdigstiltAvIdent: String? = null,
        ferdigstiltAvOid: UUID? = null
    ) : this(id, type, status, vedtaksperiodeId, utbetalingId) {
        this.ferdigstiltAvIdent = ferdigstiltAvIdent
        this.ferdigstiltAvOid = ferdigstiltAvOid
    }

    companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)

        fun oppgaveMedEgenskaper(id: Long, vedtaksperiodeId: UUID, utbetalingId: UUID, egenskaper: List<Oppgavetype>): Oppgave {
            val hovedegenskap = egenskaper.firstOrNull() ?: Oppgavetype.SØKNAD
            return Oppgave(id, hovedegenskap, Oppgavestatus.AvventerSaksbehandler, vedtaksperiodeId, utbetalingId).also {
                it.egenskaper.addAll(egenskaper)
            }
        }

        fun lagMelding(
            oppgaveId: Long,
            eventName: String,
            påVent: Boolean? = null,
            oppgaveDao: OppgaveDao
        ): Pair<String, JsonMessage> {
            val hendelseId = oppgaveDao.finnHendelseId(oppgaveId)
            val oppgave = requireNotNull(oppgaveDao.finn(oppgaveId))
            val fødselsnummer = oppgaveDao.finnFødselsnummer(oppgaveId)
            // @TODO bruke ny totrinnsvurderingtabell eller fjerne?
            val erBeslutterOppgave = false
            val erReturOppgave = false

            return fødselsnummer to lagMelding(
                eventName = eventName,
                hendelseId = hendelseId,
                oppgaveId = oppgaveId,
                status = oppgave.status,
                type = oppgave.type,
                fødselsnummer = fødselsnummer,
                erBeslutterOppgave = erBeslutterOppgave,
                erReturOppgave = erReturOppgave,
                ferdigstiltAvIdent = oppgave.ferdigstiltAvIdent,
                ferdigstiltAvOid = oppgave.ferdigstiltAvOid,
                påVent = påVent,
            )
        }

        fun lagMelding(
            fødselsnummer: String,
            hendelseId: UUID,
            eventName: String,
            oppgave: Oppgave,
            påVent: Boolean? = null
        ): Pair<String, JsonMessage> {
            // @TODO bruke ny totrinnsvurderingtabell eller fjerne?
            val erBeslutterOppgave = false
            val erReturOppgave = false

            return fødselsnummer to lagMelding(
                eventName = eventName,
                hendelseId = hendelseId,
                oppgaveId = oppgave.id,
                status = oppgave.status,
                type = oppgave.type,
                fødselsnummer = fødselsnummer,
                erBeslutterOppgave = erBeslutterOppgave,
                erReturOppgave = erReturOppgave,
                ferdigstiltAvIdent = oppgave.ferdigstiltAvIdent,
                ferdigstiltAvOid = oppgave.ferdigstiltAvOid,
                påVent = påVent,
            )
        }

        private fun lagMelding(
            eventName: String,
            hendelseId: UUID,
            oppgaveId: Long,
            status: Oppgavestatus,
            type: Oppgavetype,
            fødselsnummer: String,
            erBeslutterOppgave: Boolean,
            erReturOppgave: Boolean,
            ferdigstiltAvIdent: String? = null,
            ferdigstiltAvOid: UUID? = null,
            påVent: Boolean? = null,
        ): JsonMessage {
            return JsonMessage.newMessage(eventName, mutableMapOf(
                "@forårsaket_av" to mapOf(
                    "id" to hendelseId
                ),
                "hendelseId" to hendelseId,
                "oppgaveId" to oppgaveId,
                "status" to status.name,
                "type" to type.name,
                "fødselsnummer" to fødselsnummer,
                "erBeslutterOppgave" to erBeslutterOppgave,
                "erReturOppgave" to erReturOppgave,
            ).apply {
                ferdigstiltAvIdent?.also { put("ferdigstiltAvIdent", it) }
                ferdigstiltAvOid?.also { put("ferdigstiltAvOid", it) }
                påVent?.also { put("påVent", it) }
            })
        }
    }

    fun accept(visitor: OppgaveVisitor) {
        visitor.visitOppgave(id, type, status, vedtaksperiodeId, utbetalingId, ferdigstiltAvOid, ferdigstiltAvIdent, egenskaper)
    }

    fun ferdigstill(ident: String, oid: UUID) {
        status = Oppgavestatus.Ferdigstilt
        ferdigstiltAvIdent = ident
        ferdigstiltAvOid = oid
    }

    fun ferdigstill() {
        status = Oppgavestatus.Ferdigstilt
    }

    fun avventerSystem(ident: String, oid: UUID) {
        status = Oppgavestatus.AvventerSystem
        ferdigstiltAvIdent = ident
        ferdigstiltAvOid = oid
    }

    fun lagMelding(eventName: String, fødselsnummer: String, hendelseId: UUID): JsonMessage {
        return lagMelding(fødselsnummer, hendelseId, eventName, this, false).second
    }

    fun loggOppgaverAvbrutt(vedtaksperiodeId: UUID) {
        logg.info("Har avbrutt oppgave $id for vedtaksperiode $vedtaksperiodeId")
    }

    fun avbryt() {
        status = Oppgavestatus.Invalidert
    }

    fun forsøkTildeling(
        oppgaveMediator: OppgaveMediator,
        saksbehandleroid: UUID,
        påVent: Boolean = false,
        harTilgangTil: Tilgangskontroll,
    ) {
        if (type == Oppgavetype.STIKKPRØVE) {
            logg.info("OppgaveId $id er stikkprøve og tildeles ikke på tross av reservasjon.")
            return
        }
        if (type == Oppgavetype.RISK_QA) {
            val harTilgangTilRisk = runBlocking { harTilgangTil(saksbehandleroid, Gruppe.RISK_QA) }
            if (!harTilgangTilRisk) logg.info("OppgaveId $id er RISK_QA og saksbehandler har ikke tilgang, tildeles ikke på tross av reservasjon.")
            return
        }
        oppgaveMediator.tildel(id, saksbehandleroid, påVent)
        logg.info("Oppgave $id tildeles $saksbehandleroid grunnet reservasjon.")
    }

    fun lagrePeriodehistorikk(
        periodehistorikkDao: PeriodehistorikkDao,
        saksbehandleroid: UUID?,
        type: PeriodehistorikkType,
        notatId: Int?
    ) {
        periodehistorikkDao.lagre(type, saksbehandleroid, utbetalingId, notatId)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Oppgave) return false
        if (this.id != other.id) return false
        return this.type == other.type && this.vedtaksperiodeId == other.vedtaksperiodeId
    }

    override fun hashCode(): Int {
        return Objects.hash(id, type, vedtaksperiodeId)
    }

    override fun toString(): String {
        return "Oppgave(type=$type, status=$status, vedtaksperiodeId=$vedtaksperiodeId, utbetalingId=$utbetalingId, id=$id)"
    }
}
