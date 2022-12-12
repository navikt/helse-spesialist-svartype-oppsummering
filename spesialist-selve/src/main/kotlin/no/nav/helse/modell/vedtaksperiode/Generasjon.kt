package no.nav.helse.modell.vedtaksperiode

import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.modell.varsel.Varsel
import no.nav.helse.modell.varsel.Varsel.Companion.avvisAlleFor
import no.nav.helse.modell.varsel.Varsel.Companion.deaktiverFor
import no.nav.helse.modell.varsel.Varsel.Companion.godkjennAlleFor
import no.nav.helse.modell.varsel.Varsel.Companion.godkjennFor
import no.nav.helse.modell.varsel.VarselRepository
import no.nav.helse.modell.varsel.varselkodeformat
import no.nav.helse.tellVarsel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class Generasjon private constructor(
    private val id: UUID,
    private val vedtaksperiodeId: UUID,
    private var utbetalingId: UUID?,
    private val låst: Boolean,
    varsler: Set<Varsel>,
    private val generasjonRepository: GenerasjonRepository
) {
    internal constructor(
        id: UUID,
        vedtaksperiodeId: UUID,
        generasjonRepository: GenerasjonRepository
    ): this(id, vedtaksperiodeId, null, false, emptySet(), generasjonRepository)

    internal constructor(
        id: UUID,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID?,
        låst: Boolean,
        varsler: Set<Varsel>,
        dataSource: DataSource
    ): this(id, vedtaksperiodeId, utbetalingId, låst, varsler, ActualGenerasjonRepository(dataSource))

    private val varsler: MutableList<Varsel> = varsler.toMutableList()

    private companion object {
        private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun forsøkOpprettNeste(
        hendelseId: UUID,
        id: UUID,
        opprettBlock: (vedtaksperiodeId: UUID, hendelseId: UUID, id: UUID) -> Generasjon,
    ): Generasjon? {
        if (!låst) {
            sikkerlogg.info(
                "Oppretter ikke ny generasjon for {} da nåværende generasjon med {} er ulåst",
                keyValue("vedtaksperiodeId", vedtaksperiodeId),
                keyValue("id", id)
            )
            return null
        }
        return opprettBlock(vedtaksperiodeId, hendelseId, id)
    }

    internal fun håndterNyUtbetaling(utbetalingId: UUID) {
        if (låst) return sikkerlogg.error(
            "Kan ikke legge til ny utbetaling med {} for generasjon med {}, da generasjonen er låst",
            keyValue("utbetalingId", utbetalingId),
            keyValue("generasjonId", id)
        )
        this.utbetalingId = utbetalingId
        generasjonRepository.utbetalingFor(id, utbetalingId)
    }

    internal fun lagreVarsel(
        varselId: UUID,
        varselkode: String,
        opprettet: LocalDateTime,
        opprettBlock: (varselId: UUID, varselkode: String, opprettet: LocalDateTime, vedtaksperiodeId: UUID, generasjonId: UUID) -> Unit,
    ) {
        if (låst) return sikkerlogg.info(
            "Kan ikke lagre varsel {} på låst generasjon {}",
            keyValue("varselId", varselId),
            keyValue("generasjon", this)
        )
        opprettBlock(varselId, varselkode, opprettet, vedtaksperiodeId, id)
        if (varselkode.matches(varselkodeformat.toRegex())) tellVarsel(varselkode)
    }

    internal fun håndterNyttVarsel(varselId: UUID, varselkode: String, opprettet: LocalDateTime, varselRepository: VarselRepository) {
        if (låst) return sikkerlogg.info(
            "Kan ikke lagre varsel {} på låst generasjon {}",
            keyValue("varselId", varselId),
            keyValue("generasjon", this)
        )
        varsler.add(Varsel(varselId, varselkode, opprettet, vedtaksperiodeId))
        varselRepository.lagreVarsel(varselId, id, varselkode, opprettet, vedtaksperiodeId)
    }

    internal fun håndterGodkjentVarsel(varselkode: String, ident: String, varselRepository: VarselRepository) {
        varsler.godkjennFor(id, varselkode, ident, varselRepository)
    }

    internal fun håndterDeaktivertVarsel(varselkode: String, varselRepository: VarselRepository) {
        varsler.deaktiverFor(id, varselkode, varselRepository)
    }

    internal fun håndterGodkjent(ident: String, varselRepository: VarselRepository) {
        varsler.godkjennAlleFor(id, ident, varselRepository)
    }

    internal fun håndterAvvist(ident: String, varselRepository: VarselRepository) {
        varsler.avvisAlleFor(id, ident, varselRepository)
    }

    override fun toString(): String = "generasjonId=$id, vedtaksperiodeId=$vedtaksperiodeId, låst=$låst"

    override fun equals(other: Any?): Boolean =
        this === other || (other is Generasjon
                && javaClass == other.javaClass
                && id == other.id
                && vedtaksperiodeId == other.vedtaksperiodeId
                && låst == other.låst)

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vedtaksperiodeId.hashCode()
        result = 31 * result + låst.hashCode()
        return result
    }

}