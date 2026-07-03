package com.example.bracketmundial.data

/** Resultado de un partido de eliminatorias, ya resuelto por el proveedor (ganador/perdedor). */
data class MatchResult(
    val winnerName: String,
    val loserName: String,
    val summary: String, // "Mexico 2-1 England"
)

/** Fuente de resultados reales del Mundial; SyncRepository no sabe (ni le importa) de dónde vienen. */
interface ResultsProvider {
    /** Nombre legible de la fuente, para mostrarlo en el mensaje de sincronización. */
    val nombreFuente: String

    suspend fun finishedKnockoutMatches(): List<MatchResult>
}
