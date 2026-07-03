package com.example.bracketmundial.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bracketmundial.R
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface ApiResultsUiState {
    data object Loading : ApiResultsUiState
    data object Empty : ApiResultsUiState
    data class Error(val message: String) : ApiResultsUiState
    data class Data(val source: String, val matches: List<MatchResult>) : ApiResultsUiState
}

/** Read-only: shows the knockout matches the active provider returns (football-data.org
 *  or openfootball), without cross-checking them against the local bracket — used to
 *  confirm the data source is responding. Extends AndroidViewModel so Compose's default
 *  viewModel() factory can supply the Application automatically (needed for getString). */
class ApiResultsViewModel @JvmOverloads constructor(
    application: Application,
    private val provider: ResultsProvider = defaultProvider(application),
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<ApiResultsUiState>(ApiResultsUiState.Loading)
    val state: StateFlow<ApiResultsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ApiResultsUiState.Loading
            val context = getApplication<Application>()
            _state.value = try {
                val matches = provider.finishedKnockoutMatches()
                if (matches.isEmpty()) ApiResultsUiState.Empty else ApiResultsUiState.Data(provider.sourceName, matches)
            } catch (e: IOException) {
                ApiResultsUiState.Error(context.getString(R.string.error_no_internet))
            } catch (e: HttpException) {
                ApiResultsUiState.Error(
                    when (e.code()) {
                        401, 403 -> context.getString(R.string.error_invalid_token)
                        429 -> context.getString(R.string.error_rate_limit)
                        else -> context.getString(R.string.error_server, e.code())
                    }
                )
            }
        }
    }
}
