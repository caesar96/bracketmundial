package com.example.bracketmundial.data

/** Result of a knockout match, already resolved by the provider (winner/loser). */
data class MatchResult(
    val winnerName: String,
    val loserName: String,
    val summary: String, // "Mexico 2-1 England"
)

/** Source of real World Cup results; SyncRepository doesn't know (or care) where they come from. */
interface ResultsProvider {
    /** Readable source name, shown in the sync message. */
    val sourceName: String

    suspend fun finishedKnockoutMatches(): List<MatchResult>
}
