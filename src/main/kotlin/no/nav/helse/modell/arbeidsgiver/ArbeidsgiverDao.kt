package no.nav.helse.modell.arbeidsgiver

import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

internal class ArbeidsgiverDao(private val dataSource: DataSource) {
    internal fun findArbeidsgiverByOrgnummer(orgnummer: Long) = using(sessionOf(dataSource)) {
        it.findArbeidsgiverByOrgnummer(orgnummer)
    }

    internal fun insertArbeidsgiver(orgnummer: Long, navn: String) = using(sessionOf(dataSource, returnGeneratedKey = true)) {
        it.insertArbeidsgiver(orgnummer, navn)
    }

    internal fun findNavnSistOppdatert(orgnummer: Long) = using(sessionOf(dataSource)) {
        it.findNavnSistOppdatert(orgnummer)
    }

    internal fun findArbeidsgiver(arbeidsgiverId: Int) = using(sessionOf(dataSource)) {
        it.findArbeidsgiver(arbeidsgiverId)
    }

    internal fun updateNavn(orgnummer: Long, navn: String) = using(sessionOf(dataSource)) {
        it.updateNavn(orgnummer, navn)
    }
}

internal fun Session.findArbeidsgiverByOrgnummer(orgnummer: Long): Int? = this.run(
    queryOf("SELECT id FROM arbeidsgiver WHERE orgnummer=?;", orgnummer)
        .map { it.int("id") }
        .asSingle
)

internal fun Session.insertArbeidsgiver(orgnummer: Long, navn: String): Long? {
    val navnRef = requireNotNull(
        run(
            queryOf(
                "INSERT INTO arbeidsgiver_navn(navn, navn_oppdatert) VALUES(?, ?);",
                navn,
                LocalDateTime.now()
            )
                .asUpdateAndReturnGeneratedKey
        )
    )
    return run(
        queryOf("INSERT INTO arbeidsgiver(orgnummer, navn_ref) VALUES(?, ?);", orgnummer, navnRef)
            .asUpdateAndReturnGeneratedKey
    )
}

internal fun Session.findNavnSistOppdatert(orgnummer: Long): LocalDate = requireNotNull(
    this.run(
        queryOf(
            "SELECT navn_oppdatert FROM arbeidsgiver_navn WHERE id=(SELECT navn_ref FROM arbeidsgiver WHERE orgnummer=?);",
            orgnummer
        ).map {
            it.localDate("navn_oppdatert")
        }.asSingle
    )
)


internal fun Session.findArbeidsgiver(arbeidsgiverId: Int): ArbeidsgiverDto? = this.run(
    queryOf(
        "SELECT an.navn, a.orgnummer FROM arbeidsgiver AS a JOIN arbeidsgiver_navn AS an ON a.navn_ref = an.id WHERE a.id=?;",
        arbeidsgiverId
    ).map {
        ArbeidsgiverDto(
            organisasjonsnummer = it.string("orgnummer"),
            navn = it.string("navn")
        )
    }.asSingle
)

internal fun Session.updateNavn(orgnummer: Long, navn: String) = this.run(
    queryOf(
        "UPDATE arbeidsgiver_navn SET navn=?, navn_oppdatert=? WHERE id=(SELECT navn_ref FROM arbeidsgiver WHERE orgnummer=?);",
        navn,
        LocalDateTime.now(),
        orgnummer
    ).asUpdate
)
