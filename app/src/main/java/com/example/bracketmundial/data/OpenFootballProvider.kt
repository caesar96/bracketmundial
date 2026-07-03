package com.example.bracketmundial.data

import com.example.bracketmundial.data.network.OpenFootballClient
import com.example.bracketmundial.data.network.OpenFootballMatch
import com.example.bracketmundial.data.network.OpenFootballService
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Proveedor fallback sin key: JSON estático de openfootball, se actualiza ~1 vez al día. */
class OpenFootballProvider(
    private val service: OpenFootballService = OpenFootballClient.service,
) : ResultsProvider {
    override val nombreFuente = "openfootball (datos ~1 vez al día)"

    override suspend fun finishedKnockoutMatches(): List<MatchResult> =
        service.worldCup2026().matches
            .filter { it.score?.ft?.size == 2 }
            .filter { esEliminatoria(it) }
            .mapNotNull { it.toMatchResult() }
}

private fun esEliminatoria(m: OpenFootballMatch): Boolean {
    if (m.group != null) return false
    val round = m.round.orEmpty()
    if (round.contains("Third", ignoreCase = true)) return false
    if (round.contains("Matchday", ignoreCase = true)) return false
    return true
}

/** team1/team2 pueden venir como "Mexico" o como {"name": "Mexico"}. */
private fun JsonElement?.nombreEquipo(): String? = when (this) {
    null -> null
    is JsonObject -> this["name"]?.jsonPrimitive?.contentOrNull
    is JsonPrimitive -> contentOrNull
    else -> null
}

private fun OpenFootballMatch.toMatchResult(): MatchResult? {
    val ft = score?.ft ?: return null
    val nombre1 = team1.nombreEquipo() ?: return null
    val nombre2 = team2.nombreEquipo() ?: return null
    val marcador = "$nombre1 ${ft[0]}-${ft[1]} $nombre2"
    return when {
        ft[0] > ft[1] -> MatchResult(nombre1, nombre2, marcador)
        ft[1] > ft[0] -> MatchResult(nombre2, nombre1, marcador)
        else -> null // empate: sin datos de penales confiables, mejor omitir que adivinar
    }
}
