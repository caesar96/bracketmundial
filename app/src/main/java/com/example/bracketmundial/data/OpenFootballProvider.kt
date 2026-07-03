package com.example.bracketmundial.data

import com.example.bracketmundial.data.network.OpenFootballClient
import com.example.bracketmundial.data.network.OpenFootballMatch
import com.example.bracketmundial.data.network.OpenFootballService
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Keyless fallback provider: openfootball's static JSON, updated ~once a day.
 *  [sourceName] is passed in already localized (it's descriptive text, not a brand name). */
class OpenFootballProvider(
    override val sourceName: String,
    private val service: OpenFootballService = OpenFootballClient.service,
) : ResultsProvider {

    override suspend fun finishedKnockoutMatches(): List<MatchResult> =
        service.worldCup2026().matches
            .filter { it.score?.ft?.size == 2 }
            .filter { isKnockoutMatch(it) }
            .mapNotNull { it.toMatchResult() }
}

private fun isKnockoutMatch(m: OpenFootballMatch): Boolean {
    if (m.group != null) return false
    val round = m.round.orEmpty()
    if (round.contains("Third", ignoreCase = true)) return false
    if (round.contains("Matchday", ignoreCase = true)) return false
    return true
}

/** team1/team2 can come as "Mexico" or as {"name": "Mexico"}. */
private fun JsonElement?.teamName(): String? = when (this) {
    null -> null
    is JsonObject -> this["name"]?.jsonPrimitive?.contentOrNull
    is JsonPrimitive -> contentOrNull
    else -> null
}

private fun OpenFootballMatch.toMatchResult(): MatchResult? {
    val ft = score?.ft ?: return null
    val name1 = team1.teamName() ?: return null
    val name2 = team2.teamName() ?: return null
    val scoreline = "$name1 ${ft[0]}-${ft[1]} $name2"
    return when {
        ft[0] > ft[1] -> MatchResult(name1, name2, scoreline)
        ft[1] > ft[0] -> MatchResult(name2, name1, scoreline)
        else -> null // draw: no reliable penalty data, better to omit than guess
    }
}
