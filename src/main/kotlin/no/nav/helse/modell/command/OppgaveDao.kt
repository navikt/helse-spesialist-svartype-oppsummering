package no.nav.helse.modell.command

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.Oppgavestatus
import no.nav.helse.modell.vedtak.NavnDto
import no.nav.helse.modell.vedtak.SaksbehandleroppgaveDto
import no.nav.helse.objectMapper
import java.util.*

fun Session.insertOppgave(
    behovId: UUID,
    oppgavetype: String,
    oppgavestatus: Oppgavestatus,
    ferdigstiltAv: String?,
    oid: UUID?,
    vedtakRef: Long?
) =
    this.run(
        queryOf(
            """
                INSERT INTO oppgave(behov_id, oppdatert, type, status, ferdigstilt_av, ferdigstilt_av_oid, vedtak_ref)
                VALUES (?, now(), ?, CAST(? as oppgavestatus), ?, ?, ?);
            """,
            behovId,
            oppgavetype,
            oppgavestatus.name,
            ferdigstiltAv,
            oid,
            vedtakRef
        ).asUpdate
    )

fun Session.updateOppgave(
    behovId: UUID,
    oppgavetype: String,
    oppgavestatus: Oppgavestatus,
    ferdigstiltAv: String?,
    oid: UUID?
) =
    this.run(
        queryOf(
            """
                UPDATE oppgave SET oppdatert=now(), ferdigstilt_av=?, ferdigstilt_av_oid=?, status=?::oppgavestatus
                WHERE behov_id=? AND type=?;
            """,
            ferdigstiltAv,
            oid,
            oppgavestatus.name,
            behovId,
            oppgavetype
        ).asUpdate
    )


fun Session.findNåværendeOppgave(behovId: UUID): OppgaveDto? = this.run(
    queryOf(
        """
            SELECT *
            FROM oppgave
            WHERE behov_id=?
              AND status IN('AvventerSystem'::oppgavestatus, 'AvventerSaksbehandler'::oppgavestatus, 'Invalidert'::oppgavestatus)
        """,
        behovId
    )
        .map(::oppgaveDto)
        .asSingle
)

fun Session.findSaksbehandlerOppgaver(): List<SaksbehandleroppgaveDto> = this.run(
    queryOf(
        """
            SELECT *, (SELECT json_agg(melding) meldinger FROM warning where spleisbehov_ref=o.behov_id)
            FROM oppgave o
                   INNER JOIN vedtak v on o.vedtak_ref = v.id
                   INNER JOIN person p on v.person_ref = p.id
                   INNER JOIN person_info pi on p.info_ref = pi.id
            WHERE status = 'AvventerSaksbehandler'::oppgavestatus
            ORDER BY opprettet DESC
            LIMIT 500
        """
    )
        .map(::saksbehandleroppgaveDto)
        .asList
)

fun Session.behovForVedtaksperide(vedtaksperiodeId: UUID) = this.run(
    queryOf(
        """
            SELECT behov_id
            FROM oppgave o
                INNER JOIN vedtak v on o.vedtak_ref = v.id
            WHERE v.vedtaksperiode_id = ? AND status = 'AvventerSaksbehandler'::oppgavestatus
        """,
        vedtaksperiodeId
    )
        .map { UUID.fromString(it.stringOrNull("behov_id")) }
        .asSingle
)

private fun saksbehandleroppgaveDto(it: Row): SaksbehandleroppgaveDto = SaksbehandleroppgaveDto(
    spleisbehovId = UUID.fromString(it.string("behov_id")),
    opprettet = it.localDateTime("opprettet"),
    vedtaksperiodeId = UUID.fromString(it.string("vedtaksperiode_id")),
    periodeFom = it.localDate("fom"),
    periodeTom = it.localDate("tom"),
    navn = NavnDto(
        it.string("fornavn"),
        it.stringOrNull("mellomnavn"),
        it.string("etternavn")
    ),
    aktørId = it.long("aktor_id").toString(),
    fødselsnummer = it.long("fodselsnummer").toFødselsnummer(),
    antallVarsler = objectMapper.readTree(it.stringOrNull("meldinger") ?: "[]").count()
)

private fun oppgaveDto(it: Row): OppgaveDto = OppgaveDto(
    id = it.long("id"),
    opprettet = it.localDateTime("opprettet"),
    oppdatert = it.localDateTimeOrNull("oppdatert"),
    oppgaveType = it.string("type"),
    behovId = UUID.fromString(it.string("behov_id")),
    status = Oppgavestatus.valueOf(it.string("status")),
    vedtaksref = it.longOrNull("vedtak_ref")
)

private fun Long.toFødselsnummer() = if (this < 10000000000) "0$this" else this.toString()
