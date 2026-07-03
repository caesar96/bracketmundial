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
 *  registrarGanador(ganador, perdedor): avanza al ganador una ronda
 *  (wins++) y elimina al perdedor, para cualquier ronda del torneo.
 * ============================================================ */
class BracketViewModel(private val dao: TeamDao) : ViewModel() {

    val teams: StateFlow<List<Team>> = dao.observeAll()
        .map { entities -> entities.map { it.toTeam() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { dao.seedIfEmpty() }
    }

    fun registrarGanador(ganadorPos: Int, perdedorPos: Int) {
        viewModelScope.launch {
            val ganador = teams.value.firstOrNull { it.position == ganadorPos } ?: return@launch
            if (ganador.c == null) {
                dao.setColor(ganadorPos, FALLBACK_COLORS[ganadorPos % FALLBACK_COLORS.size].value.toLong())
            }
            dao.avanzar(ganadorPos)
            dao.eliminar(perdedorPos)
        }
    }

    fun deshacerResultado(pos: Int) {
        viewModelScope.launch {
            val byPos = teams.value.associateBy { it.position }
            val vencido = rivalVencido(byPos, pos) ?: return@launch
            dao.retroceder(pos)
            dao.revivir(vencido.position)
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
