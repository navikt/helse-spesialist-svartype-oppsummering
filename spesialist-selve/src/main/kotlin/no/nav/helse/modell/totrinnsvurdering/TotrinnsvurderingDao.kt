package no.nav.helse.modell.totrinnsvurdering

import java.util.*
import javax.sql.DataSource
import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class TotrinnsvurderingDao(private val dataSource: DataSource) {
    private fun TransactionalSession.opprett(vedtaksperiodeId: UUID): Totrinnsvurdering {
        @Language("PostgreSQL")
        val query = """
           INSERT INTO totrinnsvurdering (vedtaksperiode_id) 
           VALUES (:vedtaksperiodeId)
           RETURNING *
        """.trimIndent()

        return requireNotNull(run(queryOf(query, mapOf("vedtaksperiodeId" to vedtaksperiodeId)).tilTotrinnsvurdering()))
    }

    private fun TransactionalSession.hentAktiv(vedtaksperiodeId: UUID): Totrinnsvurdering? {
        @Language("PostgreSQL")
        val query = """
           SELECT * FROM totrinnsvurdering
           WHERE vedtaksperiode_id = :vedtaksperiodeId
           AND utbetaling_id_ref IS NULL
        """.trimIndent()

        return run(queryOf(query, mapOf("vedtaksperiodeId" to vedtaksperiodeId)).tilTotrinnsvurdering())
    }

    private fun TransactionalSession.hentAktiv(oppgaveId: Long): Totrinnsvurdering? {
        @Language("PostgreSQL")
        val query = """
           SELECT * FROM totrinnsvurdering
           INNER JOIN vedtak v on totrinnsvurdering.vedtaksperiode_id = v.vedtaksperiode_id
           INNER JOIN oppgave o on v.id = o.vedtak_ref
           WHERE o.id = :oppgaveId
           AND utbetaling_id_ref IS NULL
        """.trimIndent()

        return run(queryOf(query, mapOf("oppgaveId" to oppgaveId)).tilTotrinnsvurdering())
    }

    internal fun opprett(vedtaksperiodeId: UUID): Totrinnsvurdering = sessionOf(dataSource).use { session ->
        session.transaction { transaction ->
            transaction.run {
                hentAktiv(vedtaksperiodeId) ?: opprett(vedtaksperiodeId)
            }
        }
    }

    fun settSaksbehandler(vedtaksperiodeId: UUID, saksbehandlerOid: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET saksbehandler = :saksbehandlerOid, oppdatert = now()
               WHERE vedtaksperiode_id = :vedtaksperiodeId
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId, "saksbehandlerOid" to saksbehandlerOid)
                ).asExecute
            )
        }
    }


    fun settSaksbehandler(oppgaveId: Long, saksbehandlerOid: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET saksbehandler = :saksbehandlerOid, oppdatert = now()
               WHERE vedtaksperiode_id = (
                   SELECT ttv.vedtaksperiode_id 
                   FROM totrinnsvurdering ttv 
                   INNER JOIN vedtak v on ttv.vedtaksperiode_id = v.vedtaksperiode_id
                   INNER JOIN oppgave o on v.id = o.vedtak_ref
                   WHERE o.id = :oppgaveId
                   LIMIT 1
               )
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("oppgaveId" to oppgaveId, "saksbehandlerOid" to saksbehandlerOid)
                ).asExecute
            )
        }
    }

    fun settBeslutter(vedtaksperiodeId: UUID, saksbehandlerOid: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET beslutter = :saksbehandlerOid, oppdatert = now()
               WHERE vedtaksperiode_id = :vedtaksperiodeId
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId, "saksbehandlerOid" to saksbehandlerOid)
                ).asExecute
            )
        }
    }

    fun settBeslutter(oppgaveId: Long, saksbehandlerOid: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET beslutter = :saksbehandlerOid, oppdatert = now()
               WHERE vedtaksperiode_id = (
                   SELECT ttv.vedtaksperiode_id 
                   FROM totrinnsvurdering ttv 
                   INNER JOIN vedtak v on ttv.vedtaksperiode_id = v.vedtaksperiode_id
                   INNER JOIN oppgave o on v.id = o.vedtak_ref
                   WHERE o.id = :oppgaveId
                   LIMIT 1
               )
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("oppgaveId" to oppgaveId, "saksbehandlerOid" to saksbehandlerOid)
                ).asExecute
            )
        }
    }

    fun settErRetur(vedtaksperiodeId: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET er_retur = true, oppdatert = now()
               WHERE vedtaksperiode_id = :vedtaksperiodeId
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId)
                ).asExecute
            )
        }
    }

    fun settErRetur(oppgaveId: Long) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET er_retur = true, oppdatert = now()
               WHERE vedtaksperiode_id = (
                   SELECT ttv.vedtaksperiode_id 
                   FROM totrinnsvurdering ttv 
                   INNER JOIN vedtak v on ttv.vedtaksperiode_id = v.vedtaksperiode_id
                   INNER JOIN oppgave o on v.id = o.vedtak_ref
                   WHERE o.id = :oppgaveId
                   LIMIT 1
               )
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("oppgaveId" to oppgaveId)
                ).asExecute
            )
        }
    }

    fun settHåndtertRetur(vedtaksperiodeId: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET er_retur = false, oppdatert = now()
               WHERE vedtaksperiode_id = :vedtaksperiodeId
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId)
                ).asExecute
            )
        }
    }

    fun settHåndtertRetur(oppgaveId: Long) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET er_retur = false, oppdatert = now()
               WHERE vedtaksperiode_id = (
                   SELECT ttv.vedtaksperiode_id 
                   FROM totrinnsvurdering ttv 
                   INNER JOIN vedtak v on ttv.vedtaksperiode_id = v.vedtaksperiode_id
                   INNER JOIN oppgave o on v.id = o.vedtak_ref
                   WHERE o.id = :oppgaveId
                   LIMIT 1
               )
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("oppgaveId" to oppgaveId)
                ).asExecute
            )
        }
    }

    fun ferdigstill(vedtaksperiodeId: UUID) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
               UPDATE totrinnsvurdering SET utbetaling_id_ref = (
                   SELECT id FROM utbetaling_id ui
                   INNER JOIN vedtaksperiode_utbetaling_id vui ON vui.utbetaling_id = ui.utbetaling_id
                   WHERE vui.vedtaksperiode_id = :vedtaksperiodeId
                   ORDER BY ui.id DESC
                   LIMIT 1
               ), oppdatert = now()
               WHERE vedtaksperiode_id = :vedtaksperiodeId
               AND utbetaling_id_ref IS null
            """.trimIndent()

            session.run(
                queryOf(
                    query,
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId)
                ).asExecute
            )
        }
    }

    fun hentAktiv(vedtaksperiodeId: UUID): Totrinnsvurdering? = sessionOf(dataSource).use { session ->
        session.transaction {
            it.hentAktiv(vedtaksperiodeId)
        }
    }

    fun hentAktiv(oppgaveId: Long): Totrinnsvurdering? = sessionOf(dataSource).use { session ->
        session.transaction {
            it.hentAktiv(oppgaveId)
        }
    }

    fun opprettFraLegacy(totrinnsvurdering: Totrinnsvurdering): Totrinnsvurdering =
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
           INSERT INTO totrinnsvurdering (vedtaksperiode_id, er_retur, saksbehandler, beslutter) 
           VALUES (:vedtaksperiodeId, :erRetur, :saksbehandler, :beslutter)
           RETURNING *
        """.trimIndent()

            return requireNotNull(
                session.run(
                    queryOf(
                        query,
                        mapOf(
                            "vedtaksperiodeId" to totrinnsvurdering.vedtaksperiodeId,
                            "erRetur" to totrinnsvurdering.erRetur,
                            "saksbehandler" to totrinnsvurdering.saksbehandler,
                            "beslutter" to totrinnsvurdering.beslutter
                        )
                    ).tilTotrinnsvurdering()
                )
            )
        }


    private fun Query.tilTotrinnsvurdering() = map { row ->
        Totrinnsvurdering(
            vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
            erRetur = row.boolean("er_retur"),
            saksbehandler = row.uuidOrNull("saksbehandler"),
            beslutter = row.uuidOrNull("beslutter"),
            utbetalingIdRef = row.longOrNull("utbetaling_id_ref"),
            opprettet = row.localDateTime("opprettet"),
            oppdatert = row.localDateTimeOrNull("oppdatert")
        )
    }.asSingle
}
