package com.example.bracketmundial.data

import com.example.bracketmundial.data.network.FootballDataClient
import com.example.bracketmundial.data.network.FootballDataMatch
import com.example.bracketmundial.data.network.FootballDataService

/** Main provider: football-data.org v4, requires a token (see FOOTBALLDATA_TOKEN). */
class FootballDataProvider(
    private val service: FootballDataService = FootballDataClient.service,
) : ResultsProvider {
    override val sourceName = "football-data.org"

    override suspend fun finishedKnockoutMatches(): List<MatchResult> =
        service.matches().matches
            .filter { it.status == "FINISHED" }
            .filter { isKnockoutStage(it.stage) }
            .mapNotNull { it.toMatchResult() }
}

/** football-data.org may add new stages in 2026 (e.g. LAST_32); instead of a
 *  closed list of rounds, we only exclude what we know is NOT a knockout stage. */
private fun isKnockoutStage(stage: String) =
    !stage.equals("GROUP_STAGE", ignoreCase = true) && !stage.contains("THIRD", ignoreCase = true)

private fun FootballDataMatch.toMatchResult(): MatchResult? {
    val homeName = homeTeam.name ?: return null
    val awayName = awayTeam.name ?: return null
    val scoreline = "$homeName ${score.fullTime.home}-${score.fullTime.away} $awayName"
    return when (score.winner) {
        "HOME_TEAM" -> MatchResult(homeName, awayName, scoreline)
        "AWAY_TEAM" -> MatchResult(awayName, homeName, scoreline)
        else -> null // draw or missing data: ignored, we don't guess
    }
}
