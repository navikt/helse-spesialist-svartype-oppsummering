package no.nav.helse.modell.command

import java.util.*

data class SpleisbehovDBDto(
    val id: UUID,
    val spleisReferanse: UUID,
    val data: String
)
