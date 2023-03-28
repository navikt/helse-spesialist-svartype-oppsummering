package no.nav.helse.modell.vedtaksperiode

import java.time.LocalDate
import java.util.UUID

internal class Vedtaksperiode(
    private val vedtaksperiodeId: UUID,
    private val gjeldendeGenerasjon: Generasjon
) {

    private val observers = mutableListOf<IVedtaksperiodeObserver>()

    internal fun registrer(observer: IVedtaksperiodeObserver) {
        observers.add(observer)
        gjeldendeGenerasjon.registrer(observer)
    }

    internal fun håndterTidslinjeendring(fom: LocalDate, tom: LocalDate, skjæringstidspunkt: LocalDate) {
        gjeldendeGenerasjon.håndterTidslinjeendring(fom, tom, skjæringstidspunkt)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Vedtaksperiode
                && javaClass == other.javaClass
                && vedtaksperiodeId == other.vedtaksperiodeId
                && gjeldendeGenerasjon == other.gjeldendeGenerasjon
                )

    override fun hashCode(): Int {
        var result = vedtaksperiodeId.hashCode()
        result = 31 * result + gjeldendeGenerasjon.hashCode()
        return result
    }

    internal companion object {
        internal fun List<Vedtaksperiode>.håndterOppdateringer(vedtaksperiodeoppdateringer: List<VedtaksperiodeOppdatering>) {
            forEach { vedtaksperiode ->
                val oppdatering = vedtaksperiodeoppdateringer.find { it.vedtaksperiodeId == vedtaksperiode.vedtaksperiodeId } ?: return@forEach
                vedtaksperiode.håndterTidslinjeendring(oppdatering.fom, oppdatering.tom, oppdatering.skjæringstidspunkt)
            }
        }
    }
}