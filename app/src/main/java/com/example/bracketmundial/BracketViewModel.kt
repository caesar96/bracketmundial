package com.example.bracketmundial

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.example.bracketmundial.data.AppDatabase
import com.example.bracketmundial.data.SyncRepository
import com.example.bracketmundial.data.TeamDao
import com.example.bracketmundial.data.seedIfEmpty
import com.example.bracketmundial.data.toEntity
import com.example.bracketmundial.data.toTeam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Avanza al [ganadorPos] una ronda (wins++) y elimina al [perdedorPos]; usado tanto por
 *  registro manual (registrarGanador) como por la sincronización automática (SyncRepository). */
suspend fun aplicarResultado(dao: TeamDao, teams: List<Team>, ganadorPos: Int, perdedorPos: Int) {
    val ganador = teams.firstOrNull { it.position == ganadorPos } ?: return
    if (ganador.c == null) {
        dao.setColor(ganadorPos, FALLBACK_COLORS[ganadorPos % FALLBACK_COLORS.size].value.toLong())
    }
    dao.avanzar(ganadorPos)
    dao.eliminar(perdedorPos)
}

/* ============================================================
 *  VIEWMODEL — los resultados viven en Room; la UI solo los pinta.
 *  registrarGanador(ganador, perdedor): avanza al ganador una ronda
 *  (wins++) y elimina al perdedor, para cualquier ronda del torneo.
 * ============================================================ */
class BracketViewModel(
    private val dao: TeamDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val teams: StateFlow<List<Team>> = dao.observeAll()
        .map { entities -> entities.map { it.toTeam() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        viewModelScope.launch { dao.seedIfEmpty() }
        refresh()
    }

    fun refresh() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val resultado = syncRepository.sync()
            val base = when (resultado.aplicados.size) {
                0 -> "sin resultados nuevos"
                1 -> "1 resultado nuevo"
                else -> "${resultado.aplicados.size} resultados nuevos"
            }
            _syncMessage.value = resultado.error ?: "${resultado.fuente}: $base"
            _isSyncing.value = false
        }
    }

    fun limpiarMensajeSync() {
        _syncMessage.value = null
    }

    fun registrarGanador(ganadorPos: Int, perdedorPos: Int) {
        viewModelScope.launch { aplicarResultado(dao, teams.value, ganadorPos, perdedorPos) }
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
            initializer {
                val dao = AppDatabase.getInstance(context).teamDao()
                BracketViewModel(dao, SyncRepository(dao))
            }
        }
    }
}
