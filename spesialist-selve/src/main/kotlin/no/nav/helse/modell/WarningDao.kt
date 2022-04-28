package no.nav.helse.modell

import java.time.LocalDateTime
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.modell.vedtak.Warning
import no.nav.helse.modell.vedtak.WarningKilde
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class WarningDao(private val dataSource: DataSource) {
    internal fun leggTilWarnings(vedtaksperiodeId: UUID, warnings: List<Warning>) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        Warning.lagre(this, warnings, vedtakRef)
    }

    internal fun fjernWarnings(vedtaksperiodeId: UUID) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        sessionOf(dataSource).use  { session ->
            @Language("PostgreSQL")
            val statement = "DELETE FROM warning WHERE vedtak_ref=?"
            session.run(queryOf(statement, vedtakRef).asExecute)
        }
    }

    private fun fjernWarnings(vedtakRef: Long, kilde: WarningKilde) {
        sessionOf(dataSource).use  { session ->
            @Language("PostgreSQL")
            val statement = "DELETE FROM warning WHERE vedtak_ref=? AND kilde=CAST(? as warning_kilde)"
            session.run(queryOf(statement, vedtakRef, kilde.name).asExecute)
        }
    }

    internal fun oppdaterSpleisWarnings(vedtaksperiodeId: UUID, warnings: List<Warning>) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        fjernWarnings(vedtakRef, WarningKilde.Spleis)
        Warning.lagre(this, warnings, vedtakRef)
    }

    internal fun leggTilWarning(vedtaksperiodeId: UUID, warning: Warning) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        warning.lagre(this, vedtakRef)
    }

    internal fun leggTilWarning(vedtakRef: Long, melding: String, kilde: WarningKilde, opprettet: LocalDateTime) =
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement =
                "INSERT INTO warning (melding, kilde, vedtak_ref, opprettet) VALUES (?, CAST(? as warning_kilde), ?, ?)"
            session.run(queryOf(statement, melding, kilde.name, vedtakRef, opprettet).asUpdate)
        }

    internal fun finnAktiveWarnings(vedtaksperiodeId: UUID): List<Warning> = sessionOf(dataSource).use { session ->
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return emptyList()

        @Language("PostgreSQL")
        val statement = """
            SELECT * FROM warning 
            WHERE vedtak_ref = ? 
            AND (inaktiv_fra IS NULL OR inaktiv_fra > now())
        """.trimIndent()

        session.run(queryOf(statement, vedtakRef).map {
            Warning(
                melding = it.string("melding"),
                kilde = WarningKilde.valueOf(it.string("kilde")),
                opprettet = it.localDateTime("opprettet"),
            )
        }.asList)
    }

    internal fun finnAktiveWarningsMedMelding(vedtaksperiodeId: UUID, melding: String): List<Warning> = sessionOf(dataSource).use { session ->
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return emptyList()

        @Language("PostgreSQL")
        val statement = """
            SELECT * FROM warning 
            WHERE vedtak_ref = :vedtak_ref
            AND melding = :melding
            AND (inaktiv_fra IS NULL OR inaktiv_fra > now())
        """.trimIndent()

        session.run(queryOf(statement, mapOf("vedtak_ref" to vedtakRef, "melding" to melding)).map {
            Warning(
                melding = it.string("melding"),
                kilde = WarningKilde.valueOf(it.string("kilde")),
                opprettet = it.localDateTime("opprettet"),
            )
        }.asList)
    }

    internal fun setWarningMedMeldingInaktiv(vedtaksperiodeId: UUID, melding: String, inaktiv_fra: LocalDateTime) =
        sessionOf(dataSource).use { session ->
            val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return@use

            @Language("PostgreSQL")
            val statement = """
                UPDATE warning 
                SET inaktiv_fra = :inaktiv_fra 
                WHERE vedtak_ref = :vedtak_ref
                AND melding = :melding
            """.trimIndent()
            session.run(
                queryOf(
                    statement,
                    mapOf("inaktiv_fra" to inaktiv_fra, "vedtak_ref" to vedtakRef, "melding" to melding)
                ).asUpdate
            )
        }

    private fun finnVedtakId(vedtaksperiodeId: UUID) = sessionOf(dataSource).use  { session ->
        @Language("PostgreSQL")
        val statement = "SELECT id FROM vedtak WHERE vedtaksperiode_id = ?"
        session.run(queryOf(statement, vedtaksperiodeId).map { it.long("id") }.asSingle)
    }
}
