package com.example.bracketmundial.data

import com.example.bracketmundial.BuildConfig
import com.example.bracketmundial.Team
import com.example.bracketmundial.aplicarResultado
import com.example.bracketmundial.rivalActual
import java.io.IOException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

data class SyncResult(
    val aplicados: List<String> = emptyList(),
    val error: String? = null,
    val reintentable: Boolean = false,
    val fuente: String = "",
)

/** Nombre en inglés (tal como lo devuelven football-data.org / openfootball) -> nombre local (español). */
private val NOMBRE_API_A_LOCAL: Map<String, String> = mapOf(
    "mexico" to "México",
    "england" to "Inglaterra",
    "japan" to "Japón",
    "ivory coast" to "C. de Marfil",
    "norway" to "Noruega",
    "germany" to "Alemania",
    "brazil" to "Brasil",
    "ecuador" to "Ecuador",
    "dr congo" to "RD Congo",
    "congo dr" to "RD Congo", // football-data.org lo devuelve en este orden
    "argentina" to "Argentina",
    "cape verde" to "Cabo Verde",
    "cape verde islands" to "Cabo Verde", // football-data.org
    "australia" to "Australia",
    "egypt" to "Egipto",
    "switzerland" to "Suiza",
    "algeria" to "Argelia",
    "colombia" to "Colombia",
    "ghana" to "Ghana",
    "senegal" to "Senegal",
    "belgium" to "Bélgica",
    "bosnia and herzegovina" to "Bosnia",
    "bosnia-herzegovina" to "Bosnia", // football-data.org
    "usa" to "EE. UU.",
    "united states" to "EE. UU.", // football-data.org
    "austria" to "Austria",
    "spain" to "España",
    "croatia" to "Croacia",
    "portugal" to "Portugal",
    "morocco" to "Marruecos",
    "south africa" to "Sudáfrica",
    "netherlands" to "Países Bajos",
    "canada" to "Canadá",
    "sweden" to "Suecia",
    "france" to "Francia",
    "paraguay" to "Paraguay",
)

private fun normalizar(s: String) = s.trim().lowercase()

/** football-data.org si hay token configurado; si no, el fallback openfootball (sin key). */
internal fun proveedorPorDefecto(): ResultsProvider =
    if (BuildConfig.FOOTBALLDATA_TOKEN.isNotBlank()) FootballDataProvider() else OpenFootballProvider()

/** Sincroniza resultados reales del Mundial con el bracket local, a través de un [ResultsProvider]. */
class SyncRepository(
    private val dao: TeamDao,
    private val provider: ResultsProvider = proveedorPorDefecto(),
) {
    suspend fun sync(): SyncResult {
        val matches = try {
            provider.finishedKnockoutMatches()
        } catch (e: IOException) {
            return SyncResult(error = "Sin conexión a internet", reintentable = true, fuente = provider.nombreFuente)
        } catch (e: HttpException) {
            val mensaje = when (e.code()) {
                401, 403 -> "Token inválido"
                429 -> "Límite de solicitudes alcanzado"
                else -> "Error del servidor (${e.code()})"
            }
            return SyncResult(error = mensaje, fuente = provider.nombreFuente)
        }

        val equipos = dao.observeAll().first().map { it.toTeam() }.associateBy { it.position }.toMutableMap()
        val aplicados = mutableListOf<String>()
        matches.forEach { match -> aplicarSiValido(match, equipos)?.let { aplicados += it } }

        return SyncResult(aplicados = aplicados, fuente = provider.nombreFuente)
    }

    /** Aplica [match] si, y solo si, coincide con nuestra predicción del bracket local
     *  (ninguno eliminado, mismas wins, rivales válidos según rivalActual). Idempotente:
     *  un resultado ya aplicado deja de cumplir estas condiciones. */
    private suspend fun aplicarSiValido(match: MatchResult, equipos: MutableMap<Int, Team>): String? {
        val nombreGanador = NOMBRE_API_A_LOCAL[normalizar(match.winnerName)] ?: return null
        val nombrePerdedor = NOMBRE_API_A_LOCAL[normalizar(match.loserName)] ?: return null
        val ganador = equipos.values.firstOrNull { normalizar(it.n) == normalizar(nombreGanador) } ?: return null
        val perdedor = equipos.values.firstOrNull { normalizar(it.n) == normalizar(nombrePerdedor) } ?: return null

        if (ganador.eliminated || perdedor.eliminated) return null
        if (perdedor.wins != ganador.wins) return null
        if (rivalActual(equipos, ganador.position)?.position != perdedor.position) return null

        aplicarResultado(dao, equipos.values.toList(), ganador.position, perdedor.position)
        equipos[ganador.position] = ganador.copy(wins = ganador.wins + 1, hora = null)
        equipos[perdedor.position] = perdedor.copy(eliminated = true, hora = null)

        return match.summary
    }
}
