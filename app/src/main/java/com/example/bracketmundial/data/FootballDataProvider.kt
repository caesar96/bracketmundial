package com.example.bracketmundial.data

import com.example.bracketmundial.data.network.FootballDataClient
import com.example.bracketmundial.data.network.FootballDataMatch
import com.example.bracketmundial.data.network.FootballDataService

/** Proveedor principal: football-data.org v4, requiere token (ver FOOTBALLDATA_TOKEN). */
class FootballDataProvider(
    private val service: FootballDataService = FootballDataClient.service,
) : ResultsProvider {
    override val nombreFuente = "football-data.org"

    override suspend fun finishedKnockoutMatches(): List<MatchResult> =
        service.matches().matches
            .filter { it.status == "FINISHED" }
            .filter { esRondaEliminatoria(it.stage) }
            .mapNotNull { it.toMatchResult() }
}

/** football-data.org puede sumar stages nuevos en 2026 (p. ej. LAST_32); en vez de una
 *  lista cerrada de rondas, se excluye solo lo que sabemos que NO es eliminatoria. */
private fun esRondaEliminatoria(stage: String) =
    !stage.equals("GROUP_STAGE", ignoreCase = true) && !stage.contains("THIRD", ignoreCase = true)

private fun FootballDataMatch.toMatchResult(): MatchResult? {
    val homeName = homeTeam.name ?: return null
    val awayName = awayTeam.name ?: return null
    val marcador = "$homeName ${score.fullTime.home}-${score.fullTime.away} $awayName"
    return when (score.winner) {
        "HOME_TEAM" -> MatchResult(homeName, awayName, marcador)
        "AWAY_TEAM" -> MatchResult(awayName, homeName, marcador)
        else -> null // empate o dato ausente: se ignora, no adivinamos
    }
}
