package com.example.bracketmundial.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface ResultadosUiState {
    data object Cargando : ResultadosUiState
    data object Vacio : ResultadosUiState
    data class Error(val mensaje: String) : ResultadosUiState
    data class Datos(val fuente: String, val partidos: List<MatchResult>) : ResultadosUiState
}

/** Solo lectura: muestra los partidos de eliminatorias que devuelve el proveedor activo
 *  (football-data.org u openfootball), sin cruzarlos contra el bracket local — sirve
 *  para confirmar que la fuente de datos responde. */
class ResultadosApiViewModel @JvmOverloads constructor(
    private val provider: ResultsProvider = proveedorPorDefecto(),
) : ViewModel() {
    private val _estado = MutableStateFlow<ResultadosUiState>(ResultadosUiState.Cargando)
    val estado: StateFlow<ResultadosUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = ResultadosUiState.Cargando
            _estado.value = try {
                val partidos = provider.finishedKnockoutMatches()
                if (partidos.isEmpty()) ResultadosUiState.Vacio else ResultadosUiState.Datos(provider.nombreFuente, partidos)
            } catch (e: IOException) {
                ResultadosUiState.Error("Sin conexión a internet")
            } catch (e: HttpException) {
                ResultadosUiState.Error(
                    when (e.code()) {
                        401, 403 -> "Token inválido"
                        429 -> "Límite de solicitudes alcanzado"
                        else -> "Error del servidor (${e.code()})"
                    }
                )
            }
        }
    }
}
