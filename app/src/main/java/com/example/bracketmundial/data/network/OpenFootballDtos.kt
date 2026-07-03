package com.example.bracketmundial.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenFootballResponse(
    val matches: List<OpenFootballMatch> = emptyList(),
)

/** team1/team2 vienen a veces como String plano, a veces como objeto {name}; se guardan
 *  como JsonElement crudo y se resuelven de forma tolerante en OpenFootballProvider. */
@Serializable
data class OpenFootballMatch(
    val round: String? = null,
    val group: String? = null,
    val team1: JsonElement? = null,
    val team2: JsonElement? = null,
    val score: OpenFootballScore? = null,
)

@Serializable
data class OpenFootballScore(
    val ft: List<Int>? = null,
)
