package com.example.bracketmundial.data.network

import kotlinx.serialization.Serializable

@Serializable
data class FootballDataResponse(
    val matches: List<FootballDataMatch> = emptyList(),
)

@Serializable
data class FootballDataMatch(
    val status: String = "",
    val stage: String = "",
    val homeTeam: FootballDataTeam = FootballDataTeam(),
    val awayTeam: FootballDataTeam = FootballDataTeam(),
    val score: FootballDataScore = FootballDataScore(),
)

@Serializable
data class FootballDataTeam(
    val name: String? = null,
)

@Serializable
data class FootballDataScore(
    val winner: String? = null,
    val fullTime: FootballDataFullTime = FootballDataFullTime(),
)

@Serializable
data class FootballDataFullTime(
    val home: Int? = null,
    val away: Int? = null,
)
