package no.nav.helse.mediator.oppgave

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.db.OppgaveFraDatabase
import no.nav.helse.db.SaksbehandlerFraDatabase
import no.nav.helse.db.TotrinnsvurderingFraDatabase
import no.nav.helse.modell.oppgave.OppgaveVisitor
import no.nav.helse.modell.totrinnsvurdering.Totrinnsvurdering
import no.nav.helse.spesialist.api.modell.Saksbehandler
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import no.nav.helse.spesialist.api.oppgave.Oppgavetype

class Oppgavelagrer : OppgaveVisitor {
    private lateinit var oppgaveForLagring: OppgaveFraDatabase
    private var totrinnsvurderingForLagring: TotrinnsvurderingFraDatabase? = null

    internal fun lagre(oppgaveMediator: OppgaveMediator, hendelseId: UUID, contextId: UUID) {
        val oppgave = oppgaveForLagring
        oppgaveMediator.opprett(
            id = oppgave.id,
            contextId = contextId,
            vedtaksperiodeId = oppgave.vedtaksperiodeId,
            utbetalingId = oppgave.utbetalingId,
            navn = enumValueOf(oppgave.type),
            hendelseId = hendelseId
        )
        if (oppgave.tildelt != null) oppgaveMediator.tildel(oppgave.id, oppgave.tildelt.oid, oppgave.påVent)
        else oppgaveMediator.avmeld(oppgave.id)

        val totrinnsvurdering = totrinnsvurderingForLagring
        if (totrinnsvurdering != null) oppgaveMediator.lagreTotrinnsvurdering(totrinnsvurdering)
    }

    internal fun oppdater(oppgaveMediator: OppgaveMediator) {
        val oppgave = oppgaveForLagring
        oppgaveMediator.oppdater(
            oppgaveId = oppgave.id,
            status = enumValueOf(oppgave.status),
            ferdigstiltAvIdent = oppgave.ferdigstiltAvIdent,
            ferdigstiltAvOid = oppgave.ferdigstiltAvOid
        )
        if (oppgave.tildelt != null) oppgaveMediator.tildel(oppgave.id, oppgave.tildelt.oid, oppgave.påVent)
        else oppgaveMediator.avmeld(oppgave.id)

        val totrinnsvurdering = totrinnsvurderingForLagring
        if (totrinnsvurdering != null) oppgaveMediator.lagreTotrinnsvurdering(totrinnsvurdering)
    }

    override fun visitOppgave(
        id: Long,
        type: Oppgavetype,
        status: Oppgavestatus,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        ferdigstiltAvOid: UUID?,
        ferdigstiltAvIdent: String?,
        egenskaper: List<Oppgavetype>,
        tildelt: Saksbehandler?,
        påVent: Boolean,
        totrinnsvurdering: Totrinnsvurdering?
    ) {
        oppgaveForLagring = OppgaveFraDatabase(
            id = id,
            type = type.toString(),
            status = status.toString(),
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            ferdigstiltAvIdent = ferdigstiltAvIdent,
            ferdigstiltAvOid = ferdigstiltAvOid,
            tildelt = tildelt?.toDto()?.let {
               SaksbehandlerFraDatabase(it.epost, it.oid, it.navn, it.ident)
            },
            påVent = påVent
        )
    }

    override fun visitTotrinnsvurdering(
        vedtaksperiodeId: UUID,
        erRetur: Boolean,
        saksbehandler: Saksbehandler?,
        beslutter: Saksbehandler?,
        utbetalingIdRef: Long?,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime?
    ) {
        totrinnsvurderingForLagring = TotrinnsvurderingFraDatabase(
            vedtaksperiodeId = vedtaksperiodeId,
            erRetur = erRetur,
            saksbehandler = saksbehandler?.oid(),
            beslutter = beslutter?.oid(),
            utbetalingIdRef = utbetalingIdRef,
            opprettet = opprettet,
            oppdatert = oppdatert
        )
    }
}