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

/** Advances [winnerPos] one round (wins++) and eliminates [loserPos]; used both by
 *  manual recording (recordWinner) and by automatic sync (SyncRepository). */
suspend fun applyResult(dao: TeamDao, teams: List<Team>, winnerPos: Int, loserPos: Int) {
    val winner = teams.firstOrNull { it.position == winnerPos } ?: return
    if (winner.c == null) {
        dao.setColor(winnerPos, FALLBACK_COLORS[winnerPos % FALLBACK_COLORS.size].value.toLong())
    }
    dao.advance(winnerPos)
    dao.eliminate(loserPos)
}

/* ============================================================
 *  VIEWMODEL — results live in Room; the UI only renders them.
 *  recordWinner(winner, loser): advances the winner one round
 *  (wins++) and eliminates the loser, for any round of the tournament.
 * ============================================================ */
class BracketViewModel(
    private val context: Context,
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
        viewModelScope.launch { dao.seedIfEmpty(initialTeams(context)) }
        refresh()
    }

    fun refresh() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = syncRepository.sync()
            val base = when (result.applied.size) {
                0 -> context.getString(R.string.sync_no_new_results)
                1 -> context.getString(R.string.sync_one_new_result)
                else -> context.getString(R.string.sync_new_results_count, result.applied.size)
            }
            _syncMessage.value = result.error ?: "${result.source}: $base"
            _isSyncing.value = false
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun recordWinner(winnerPos: Int, loserPos: Int) {
        viewModelScope.launch { applyResult(dao, teams.value, winnerPos, loserPos) }
    }

    fun undoResult(pos: Int) {
        viewModelScope.launch {
            val byPos = teams.value.associateBy { it.position }
            val defeated = defeatedRival(byPos, pos) ?: return@launch
            dao.retreat(pos)
            dao.revive(defeated.position)
        }
    }

    fun saveTeam(team: Team) {
        viewModelScope.launch { dao.upsert(team.toEntity()) }
    }

    fun deleteTeam(team: Team) {
        viewModelScope.launch { dao.delete(team.toEntity()) }
    }

    fun resetBracket() {
        viewModelScope.launch {
            initialTeams(context).forEach { dao.upsert(it.toEntity()) }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val dao = AppDatabase.getInstance(appContext).teamDao()
                BracketViewModel(appContext, dao, SyncRepository(appContext, dao))
            }
        }
    }
}
