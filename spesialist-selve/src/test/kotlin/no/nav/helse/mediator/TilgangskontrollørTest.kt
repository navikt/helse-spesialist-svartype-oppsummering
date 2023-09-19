package no.nav.helse.mediator

import java.util.UUID
import no.nav.helse.Gruppe
import no.nav.helse.Gruppe.KODE7
import no.nav.helse.Gruppe.SKJERMEDE
import no.nav.helse.Tilgangsgrupper
import no.nav.helse.idForGruppe
import no.nav.helse.modell.oppgave.EGEN_ANSATT
import no.nav.helse.modell.oppgave.FORTROLIG_ADRESSE
import no.nav.helse.modell.oppgave.RISK_QA
import no.nav.helse.testEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TilgangskontrollørTest {

    private val tilgangsgrupper = Tilgangsgrupper(testEnv)

    private val forespørsler = mutableMapOf<UUID, String>()

    private val gruppekontroll = object : Gruppekontroll {
        override suspend fun erIGruppe(oid: UUID, groupId: UUID): Boolean {
            forespørsler[oid] = groupId.toString()
            return true
        }
    }

    private val tilgangskontrollør = Tilgangskontrollør(gruppekontroll, tilgangsgrupper)

    @Test
    fun `Mapper EGEN_ANSATT til gruppeId for egenansatt`() {
        val saksbehandlerOid = UUID.randomUUID()
        tilgangskontrollør.harTilgangTil(saksbehandlerOid, EGEN_ANSATT)
        assertEquals(idForGruppe(SKJERMEDE), forespørsler[saksbehandlerOid])
    }

    @Test
    fun `Mapper RISK_QA til gruppeId for risk QA`() {
        val saksbehandlerOid = UUID.randomUUID()
        tilgangskontrollør.harTilgangTil(saksbehandlerOid, RISK_QA)
        assertEquals(idForGruppe(Gruppe.RISK_QA), forespørsler[saksbehandlerOid])
    }

    @Test
    fun `Mapper FORTROLIG_ADRESSE til gruppeId for kode 7`() {
        val saksbehandlerOid = UUID.randomUUID()
        tilgangskontrollør.harTilgangTil(saksbehandlerOid, FORTROLIG_ADRESSE)
        assertEquals(idForGruppe(KODE7), forespørsler[saksbehandlerOid])
    }
}