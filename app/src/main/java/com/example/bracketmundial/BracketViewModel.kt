package com.example.bracketmundial

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.example.bracketmundial.data.AppDatabase
import com.example.bracketmundial.data.TeamDao
import com.example.bracketmundial.data.seedIfEmpty
import com.example.bracketmundial.data.toEntity
import com.example.bracketmundial.data.toTeam
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* ============================================================
 *  VIEWMODEL — los resultados viven en Room; la UI solo los pinta.
 *  registrarGanador(position): marca al equipo de ese slot como
 *  ganador de su llave y elimina a su rival adyacente.
 * ============================================================ */
class BracketViewModel(private val dao: TeamDao) : ViewModel() {

    val teams: StateFlow<List<Team>> = dao.observeAll()
        .map { entities -> entities.map { it.toTeam() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { dao.seedIfEmpty() }
    }

    fun registrarGanador(position: Int) {
        viewModelScope.launch {
            val byPos = teams.value.associateBy { it.position }
            val t = byPos[position] ?: return@launch
            if (t.s != 'P') return@launch          // solo llaves pendientes
            val rivalPos = if (position % 2 == 0) position + 1 else position - 1
            byPos[rivalPos] ?: return@launch        // sin rival definido, no se puede resolver
            if (t.c == null) {
                dao.setColor(position, FALLBACK_COLORS[position % FALLBACK_COLORS.size].value.toLong())
            }
            dao.setStatus(position, "W")
            dao.setStatus(rivalPos, "L")
        }
    }

    fun guardarEquipo(team: Team) {
        viewModelScope.launch { dao.upsert(team.toEntity()) }
    }

    fun eliminarEquipo(team: Team) {
        viewModelScope.launch { dao.delete(team.toEntity()) }
    }

    fun reiniciar() {
        viewModelScope.launch {
            INITIAL_TEAMS.forEach { dao.upsert(it.toEntity()) }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { BracketViewModel(AppDatabase.getInstance(context).teamDao()) }
        }
    }
}
