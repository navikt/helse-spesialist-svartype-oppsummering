package no.nav.helse.vedtaksperiode

import no.nav.helse.HelseDao
import java.util.*
import javax.sql.DataSource

class VarselDao(dataSource: DataSource): HelseDao(dataSource) {
    fun finnAktiveVarsler(vedtaksperiodeId: UUID): List<String> =
        """SELECT melding FROM warning WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = :vedtaksperiodeId) and (inaktiv_fra is null or inaktiv_fra > now())"""
            .list(mapOf("vedtaksperiodeId" to vedtaksperiodeId)) { it.string("melding") }
}
