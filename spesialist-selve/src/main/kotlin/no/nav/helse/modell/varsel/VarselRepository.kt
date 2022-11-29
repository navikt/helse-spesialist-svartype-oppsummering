package no.nav.helse.modell.varsel

import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.modell.varsel.Varsel.Status.AKTIV
import no.nav.helse.modell.varsel.Varsel.Status.AVVIST
import no.nav.helse.modell.varsel.Varsel.Status.GODKJENT
import no.nav.helse.modell.varsel.Varsel.Status.INAKTIV
import no.nav.helse.modell.vedtaksperiode.GenerasjonDao
import no.nav.helse.tellInaktivtVarsel
import org.slf4j.LoggerFactory

internal interface VarselRepository {
    fun finnVarslerFor(vedtaksperiodeId: UUID): List<Varsel>
    fun deaktiverFor(vedtaksperiodeId: UUID, varselkode: String, definisjonId: UUID?)
    fun godkjennFor(vedtaksperiodeId: UUID, varselkode: String, ident: String, definisjonId: UUID?)
    fun avvisFor(vedtaksperiodeId: UUID, varselkode: String, ident: String, definisjonId: UUID?)
    fun godkjennAlleFor(vedtaksperiodeId: UUID, ident: String)
    fun avvisAlleFor(vedtaksperiodeId: UUID, ident: String)
    fun lagreVarsel(id: UUID, varselkode: String, opprettet: LocalDateTime, vedtaksperiodeId: UUID)
    fun lagreDefinisjon(
        id: UUID,
        varselkode: String,
        tittel: String,
        forklaring: String?,
        handling: String?,
        avviklet: Boolean,
        opprettet: LocalDateTime,
    )
}

internal class ActualVarselRepository(dataSource: DataSource) : VarselRepository {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    private val varselDao = VarselDao(dataSource)
    private val definisjonDao = DefinisjonDao(dataSource)
    private val generasjonDao = GenerasjonDao(dataSource)

    override fun finnVarslerFor(vedtaksperiodeId: UUID): List<Varsel> {
        return varselDao.alleVarslerFor(vedtaksperiodeId)
    }

    override fun deaktiverFor(vedtaksperiodeId: UUID, varselkode: String, definisjonId: UUID?) {
        if (!erAktivFor(vedtaksperiodeId, varselkode)) return
        val definisjon = definisjonId?.let(definisjonDao::definisjonFor) ?: definisjonDao.sisteDefinisjonFor(varselkode)
        definisjon.oppdaterVarsel(vedtaksperiodeId, INAKTIV, "Spesialist", varselDao::oppdaterVarsel)
        if (varselkode.matches(varselkodeformat.toRegex())) tellInaktivtVarsel(varselkode)
    }

    override fun godkjennFor(vedtaksperiodeId: UUID, varselkode: String, ident: String, definisjonId: UUID?) {
        if (!erAktivFor(vedtaksperiodeId, varselkode)) return
        val definisjon = definisjonId?.let(definisjonDao::definisjonFor) ?: definisjonDao.sisteDefinisjonFor(varselkode)
        definisjon.oppdaterVarsel(vedtaksperiodeId, GODKJENT, ident, varselDao::oppdaterVarsel)
    }

    override fun avvisFor(vedtaksperiodeId: UUID, varselkode: String, ident: String, definisjonId: UUID?) {
        if (!erAktivFor(vedtaksperiodeId, varselkode)) return
        val definisjon = definisjonId?.let(definisjonDao::definisjonFor) ?: definisjonDao.sisteDefinisjonFor(varselkode)
        definisjon.oppdaterVarsel(vedtaksperiodeId, AVVIST, ident, varselDao::oppdaterVarsel)
    }

    override fun godkjennAlleFor(vedtaksperiodeId: UUID, ident: String) {
        val varsler = varselDao.alleVarslerFor(vedtaksperiodeId)
        varsler.forEach { it.godkjennHvisAktiv(vedtaksperiodeId, ident, this) }
    }

    override fun avvisAlleFor(vedtaksperiodeId: UUID, ident: String) {
        val varsler = varselDao.alleVarslerFor(vedtaksperiodeId)
        varsler.forEach { it.avvisHvisAktiv(vedtaksperiodeId, ident, this) }
    }

    override fun lagreVarsel(id: UUID, varselkode: String, opprettet: LocalDateTime, vedtaksperiodeId: UUID) {
        generasjonDao.finnSisteFor(vedtaksperiodeId)
            ?.run {
                if (erAktivFor(vedtaksperiodeId, varselkode)) return
                this.lagreVarsel(id, varselkode, opprettet, varselDao::lagreVarsel)
            }
            ?: sikkerlogg.info(
                "Lagrer ikke {} for {} fordi det ikke finnes noen generasjon for perioden.",
                keyValue("varselkode", varselkode),
                keyValue("vedtaksperiodeId", vedtaksperiodeId)
            )
    }

    override fun lagreDefinisjon(
        id: UUID,
        varselkode: String,
        tittel: String,
        forklaring: String?,
        handling: String?,
        avviklet: Boolean,
        opprettet: LocalDateTime,
    ) {
        definisjonDao.lagreDefinisjon(id, varselkode, tittel, forklaring, handling, avviklet, opprettet)
    }

    private fun erAktivFor(vedtaksperiodeId: UUID, varselkode: String): Boolean {
        return varselDao.finnVarselstatus(vedtaksperiodeId, varselkode) == AKTIV
    }
}