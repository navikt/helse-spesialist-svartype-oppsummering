package no.nav.helse.modell.command

import kotliquery.*
import no.nav.helse.mediator.kafka.meldinger.*
import no.nav.helse.modell.IHendelsefabrikk
import no.nav.helse.modell.command.HendelseDao.Hendelsetype.*
import no.nav.helse.modell.vedtak.Saksbehandleroppgavetype
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class HendelseDao(
    private val dataSource: DataSource,
    private val hendelsefabrikk: IHendelsefabrikk
) {
    internal fun opprett(hendelse: Hendelse) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                transactionalSession.run {
                    opprettHendelse(hendelse)

                    hendelse.vedtaksperiodeId()?.let {
                        opprettKobling(it, hendelse.id)
                    }
                }
            }
        }
    }

    private fun TransactionalSession.opprettHendelse(hendelse: Hendelse) {
        @Language("PostgreSQL")
        val hendelseStatement =
            "INSERT INTO spleisbehov(id, spleis_referanse, data, original, type) VALUES(?, ?, CAST(? as json), CAST(? as json), ?)"
        run(queryOf(
            hendelseStatement,
            hendelse.id,
            hendelse.vedtaksperiodeId()?: UUID.randomUUID(),
            hendelse.toJson(),
            hendelse.toJson(),
            tilHendelsetype(hendelse).name
        ).asUpdate)
    }

    private fun TransactionalSession.opprettKobling(vedtaksperiodeId: UUID, hendelseId: UUID) {
        @Language("PostgreSQL")
        val vedtaksperiodeQuery = "SELECT id FROM vedtak WHERE vedtaksperiode_id = :vedtaksperiode_id"
        run(
            queryOf(vedtaksperiodeQuery, mapOf("vedtaksperiode_id" to vedtaksperiodeId))
                .map { it.long(1) }.asSingle
        )?.let { vedtaksperiodeRef ->
            @Language("PostgreSQL")
            val koblingStatement = "INSERT INTO vedtaksperiode_hendelse VALUES(?,?)"
            run(
                queryOf(
                    koblingStatement,
                    vedtaksperiodeRef,
                    hendelseId
                ).asUpdate
            )
        }
    }

    internal fun finn(id: UUID): Hendelse? =
        using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT type,data FROM spleisbehov WHERE id = ?", id).map { row ->
                fraHendelsetype(enumValueOf(row.string("type")), row.string("data"))
            }.asSingle)
        }

    private fun fraHendelsetype(hendelsetype: Hendelsetype, json: String): Hendelse? =
        when (hendelsetype) {
            VEDTAKSPERIODE_ENDRET -> hendelsefabrikk.nyNyVedtaksperiodeEndret(json)
            VEDTAKSPERIODE_FORKASTET -> hendelsefabrikk.nyNyVedtaksperiodeForkastet(json)
            GODKJENNING -> hendelsefabrikk.nyGodkjenning(json)
            OVERSTYRING -> hendelsefabrikk.overstyring(json)
        }

    private fun tilHendelsetype(hendelse: Hendelse) = when (hendelse) {
        is NyVedtaksperiodeEndretMessage -> VEDTAKSPERIODE_ENDRET
        is NyVedtaksperiodeForkastetMessage -> VEDTAKSPERIODE_FORKASTET
        is NyGodkjenningMessage -> GODKJENNING
        is OverstyringMessage -> OVERSTYRING
        else -> throw IllegalArgumentException("ukjent hendelsetype: ${hendelse::class.simpleName}")
    }

    private enum class Hendelsetype {
        VEDTAKSPERIODE_ENDRET, VEDTAKSPERIODE_FORKASTET, GODKJENNING, OVERSTYRING
    }
}

internal fun Session.insertBehov(id: UUID, spleisReferanse: UUID, behov: String, original: String, type: String) {
    this.run(
        queryOf(
            "INSERT INTO spleisbehov(id, spleis_referanse, data, original, type) VALUES(?, ?, CAST(? as json), CAST(? as json), ?)",
            id,
            spleisReferanse,
            behov,
            original,
            type
        ).asUpdate
    )
}

fun Session.insertWarning(melding: String, spleisbehovRef: UUID) = this.run(
    queryOf(
        "INSERT INTO warning (melding, spleisbehov_ref) VALUES (?, ?)",
        melding,
        spleisbehovRef
    ).asUpdate
)

fun Session.insertSaksbehandleroppgavetype(type: Saksbehandleroppgavetype, spleisbehovRef: UUID) =
    this.run(
        queryOf(
            "INSERT INTO saksbehandleroppgavetype (type, spleisbehov_ref) VALUES (?, ?)",
            type.name,
            spleisbehovRef
        ).asUpdate
    )

internal fun Session.updateBehov(id: UUID, behov: String) {
    this.run(
        queryOf(
            "UPDATE spleisbehov SET data=CAST(? as json) WHERE id=?", behov, id
        ).asUpdate
    )
}

internal fun Session.findBehov(id: UUID): SpleisbehovDBDto? =
    this.run(queryOf("SELECT spleis_referanse, data, type FROM spleisbehov WHERE id=?", id)
        .map {
            SpleisbehovDBDto(
                id = id,
                spleisReferanse = UUID.fromString(it.string("spleis_referanse")),
                data = it.string("data"),
                type = enumValueOf(it.string("type"))
            )
        }
        .asSingle
    )

internal fun Session.findBehovMedSpleisReferanse(spleisReferanse: UUID): SpleisbehovDBDto? =
    this.run(queryOf("SELECT id, data, type FROM spleisbehov WHERE spleis_referanse=? AND type IN(${
        MacroCommandType.values().joinToString { "?" }
    })", spleisReferanse, *MacroCommandType.values().map(Enum<*>::name).toTypedArray())
        .map {
            SpleisbehovDBDto(
                id = UUID.fromString(it.string("id")),
                spleisReferanse = spleisReferanse,
                data = it.string("data"),
                type = enumValueOf(it.string("type"))
            )
        }
        .asSingle
    )

internal fun Session.findOriginalBehov(id: UUID): String? =
    this.run(queryOf("SELECT original FROM spleisbehov WHERE id=?", id)
        .map { it.string("original") }
        .asSingle)
