package com.example.bracketmundial.data

import android.content.Context
import com.example.bracketmundial.R
import com.example.bracketmundial.Team
import com.example.bracketmundial.applyResult
import com.example.bracketmundial.currentRival
import java.io.IOException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

data class SyncResult(
    val applied: List<String> = emptyList(),
    val error: String? = null,
    val retryable: Boolean = false,
    val source: String = "",
)

/** English API team name -> the canonical countryKey (see COUNTRY_NAME_RES) it refers to.
 *  Matching by this stable key — instead of by display-name string — keeps working
 *  regardless of device locale and is automatically safe against renames: if a team's
 *  countryKey was cleared (because its name was manually overridden), it simply stops
 *  matching that API name rather than mismatching against the wrong team. */
private val API_NAME_TO_COUNTRY_KEY: Map<String, String> = mapOf(
    "mexico" to "mexico",
    "england" to "england",
    "japan" to "japan",
    "ivory coast" to "ivory_coast",
    "norway" to "norway",
    "germany" to "germany",
    "brazil" to "brazil",
    "ecuador" to "ecuador",
    "dr congo" to "dr_congo",
    "congo dr" to "dr_congo", // football-data.org returns it in this order
    "argentina" to "argentina",
    "cape verde" to "cape_verde",
    "cape verde islands" to "cape_verde", // football-data.org
    "australia" to "australia",
    "egypt" to "egypt",
    "switzerland" to "switzerland",
    "algeria" to "algeria",
    "colombia" to "colombia",
    "ghana" to "ghana",
    "senegal" to "senegal",
    "belgium" to "belgium",
    "bosnia and herzegovina" to "bosnia",
    "bosnia-herzegovina" to "bosnia", // football-data.org
    "usa" to "usa",
    "united states" to "usa", // football-data.org
    "austria" to "austria",
    "spain" to "spain",
    "croatia" to "croatia",
    "portugal" to "portugal",
    "morocco" to "morocco",
    "south africa" to "south_africa",
    "netherlands" to "netherlands",
    "canada" to "canada",
    "sweden" to "sweden",
    "france" to "france",
    "paraguay" to "paraguay",
)

private fun normalize(s: String) = s.trim().lowercase()

/** football-data.org if a token is configured; otherwise the keyless openfootball fallback. */
internal fun defaultProvider(context: Context): ResultsProvider =
    if (com.example.bracketmundial.BuildConfig.FOOTBALLDATA_TOKEN.isNotBlank()) FootballDataProvider()
    else OpenFootballProvider(sourceName = context.getString(R.string.provider_name_openfootball))

/** Syncs real World Cup results into the local bracket, through a [ResultsProvider]. */
class SyncRepository(
    private val context: Context,
    private val dao: TeamDao,
    private val provider: ResultsProvider = defaultProvider(context),
) {
    suspend fun sync(): SyncResult {
        val matches = try {
            provider.finishedKnockoutMatches()
        } catch (e: IOException) {
            return SyncResult(error = context.getString(R.string.error_no_internet), retryable = true, source = provider.sourceName)
        } catch (e: HttpException) {
            val message = when (e.code()) {
                401, 403 -> context.getString(R.string.error_invalid_token)
                429 -> context.getString(R.string.error_rate_limit)
                else -> context.getString(R.string.error_server, e.code())
            }
            return SyncResult(error = message, source = provider.sourceName)
        }

        val teamsByPosition = dao.observeAll().first().map { it.toTeam() }.associateBy { it.position }.toMutableMap()
        val applied = mutableListOf<String>()
        matches.forEach { match -> applyIfValid(match, teamsByPosition)?.let { applied += it } }

        return SyncResult(applied = applied, source = provider.sourceName)
    }

    /** Applies [match] if, and only if, it matches our local bracket's prediction
     *  (neither eliminated, same wins, valid rivals per currentRival). Idempotent:
     *  a result already applied stops meeting these conditions. */
    private suspend fun applyIfValid(match: MatchResult, teamsByPosition: MutableMap<Int, Team>): String? {
        val winnerKey = API_NAME_TO_COUNTRY_KEY[normalize(match.winnerName)] ?: return null
        val loserKey = API_NAME_TO_COUNTRY_KEY[normalize(match.loserName)] ?: return null
        val winner = teamsByPosition.values.firstOrNull { it.countryKey == winnerKey } ?: return null
        val loser = teamsByPosition.values.firstOrNull { it.countryKey == loserKey } ?: return null

        if (winner.eliminated || loser.eliminated) return null
        if (loser.wins != winner.wins) return null
        if (currentRival(teamsByPosition, winner.position)?.position != loser.position) return null

        applyResult(dao, teamsByPosition.values.toList(), winner.position, loser.position)
        teamsByPosition[winner.position] = winner.copy(wins = winner.wins + 1, matchTime = null)
        teamsByPosition[loser.position] = loser.copy(eliminated = true, matchTime = null)

        return match.summary
    }
}
