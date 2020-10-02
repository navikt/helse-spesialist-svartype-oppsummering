package no.nav.helse.vedtaksperiode

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.measureAsHistogram
import no.nav.helse.modell.SnapshotDao
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.command.OppgaveDao
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.risiko.RisikovurderingDao
import no.nav.helse.modell.vedtak.snapshot.PersonFraSpleisDto
import no.nav.helse.objectMapper
import no.nav.helse.tildeling.TildelingDao
import java.util.*

internal class VedtaksperiodeMediator(
    private val vedtakDao: VedtakDao,
    private val personDao: PersonDao,
    private val arbeidsgiverDao: ArbeidsgiverDao,
    private val snapshotDao: SnapshotDao,
    private val overstyringDao: OverstyringDao,
    private val oppgaveDao: OppgaveDao,
    private val tildelingDao: TildelingDao,
    private val risikovurderingDao: RisikovurderingDao
) {
    fun byggSpeilSnapshotForFnr(fnr: String) =
        measureAsHistogram("byggSpeilSnapshotForFnr") {
            vedtakDao.findVedtakByFnr(fnr)?.let { byggSpeilSnapshot(it) }
        }

    fun byggSpeilSnapshotForAktørId(aktørId: String) =
        measureAsHistogram("byggSpeilSnapshotForAktørId") {
            vedtakDao.findVedtakByAktørId(aktørId)?.let { byggSpeilSnapshot(it) }
        }

    fun byggSpeilSnapshotForVedtaksperiodeId(vedtaksperiodeId: UUID) =
        measureAsHistogram("byggSpeilSnapshotForVedtaksperiodeId") {
            vedtakDao.findVedtakByVedtaksperiodeId(vedtaksperiodeId)?.let { byggSpeilSnapshot(it) }
        }

    private fun byggSpeilSnapshot(vedtak: VedtaksperiodeDto) =
        measureAsHistogram("byggSpeilSnapshot") {
            val arbeidsgiverDto = measureAsHistogram("byggSpeilSnapshot_findArbeidsgiver") {
                requireNotNull(arbeidsgiverDao.findArbeidsgiver(vedtak.arbeidsgiverRef)) { "Fant ikke arbeidsgiver" }
            }
            val infotrygdutbetalinger = measureAsHistogram("byggSpeilSnapshot_findInfotrygdutbetalinger") {
                personDao.findInfotrygdutbetalinger(vedtak.fødselsnummer)?.let { objectMapper.readTree(it) }
            }
            val speilSnapshot = measureAsHistogram("byggSpeilSnapshot_findSpeilSnapshot") {
                requireNotNull(snapshotDao.findSpeilSnapshot(vedtak.speilSnapshotRef)) { "Fant ikke speilSnapshot" }
                    .let { objectMapper.readValue<PersonFraSpleisDto>(it) }
            }
            val arbeidsgivere = speilSnapshot.arbeidsgivere.map {
                val arbeidsgivernavn =
                    if (it.organisasjonsnummer == arbeidsgiverDto.organisasjonsnummer)
                        arbeidsgiverDto.navn
                    else
                        "Ikke tilgjengelig"
                val overstyringer = overstyringDao.finnOverstyring(vedtak.fødselsnummer, it.organisasjonsnummer)
                    .map { overstyring ->
                        OverstyringForSpeilDto(
                            hendelseId = overstyring.hendelseId,
                            begrunnelse = overstyring.begrunnelse,
                            timestamp = overstyring.timestamp,
                            saksbehandlerNavn = overstyring.saksbehandlerNavn,
                            overstyrteDager = overstyring.overstyrteDager.map { dag ->
                                OverstyringDagForSpeilDto(
                                    dato = dag.dato,
                                    dagtype = dag.type,
                                    grad = dag.grad
                                )
                            }
                        )
                    }
                ArbeidsgiverForSpeilDto(
                    organisasjonsnummer = it.organisasjonsnummer,
                    navn = arbeidsgivernavn,
                    id = it.id,
                    overstyringer = overstyringer,
                    vedtaksperioder = it.vedtaksperioder
                )
            }
            measureAsHistogram("byggSpeilSnapshot_behovForVedtaksperiode_akkumulert") {
                speilSnapshot.arbeidsgivere.forEach { arbeidsgiver ->
                    arbeidsgiver.vedtaksperioder.forEach { vedtaksperiode ->
                        val vedtaksperiodeId = UUID.fromString(vedtaksperiode["id"].asText())
                        val oppgaveId = oppgaveDao.finnOppgaveId(vedtaksperiodeId)
                        val risikovurdering = risikovurderingDao.hentRisikovurdering(vedtaksperiodeId)

                        vedtaksperiode as ObjectNode
                        vedtaksperiode.put("oppgavereferanse", oppgaveId?.toString())
                        vedtaksperiode.put(
                            "risikovurdering",
                            objectMapper.writeValueAsString(risikovurdering?.speilVariant())
                        )
                    }
                }
            }

            val enhet = measureAsHistogram("byggSpeilSnapshot_findEnhet") {
                personDao.findEnhet(vedtak.fødselsnummer)
            }

            val saksbehandlerepost = tildelingDao.tildelingForPerson(vedtak.fødselsnummer)

            PersonForSpeilDto(
                aktørId = speilSnapshot.aktørId,
                fødselsnummer = speilSnapshot.fødselsnummer,
                personinfo = vedtak.personinfo,
                arbeidsgivere = arbeidsgivere,
                infotrygdutbetalinger = infotrygdutbetalinger,
                enhet = enhet,
                saksbehandlerepost = saksbehandlerepost
            )
        }

    fun harAktivOppgave(oppgaveId: Long) = oppgaveDao.harAktivOppgave(oppgaveId)
}
