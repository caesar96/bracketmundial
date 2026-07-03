@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bracketmundial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val COL_CARD = Color(0xFF211A11)
private val COL_CARD_BORDER = Color(0xFF453923)
private val COL_TEXT_CREAM = Color(0xFFE9E2D4)
private val COL_TEXT_DIM = Color(0xFF7D7060)
private val COL_DANGER = Color(0xFFD13A30)

private fun estadoTexto(s: Char) = when (s) {
    'W' -> "Clasificado"
    'L' -> "Eliminado"
    else -> "Por jugar"
}

private fun estadoColor(s: Char) = when (s) {
    'W' -> COL_GOLD
    'L' -> COL_DANGER
    else -> COL_TEXT_DIM
}

@Composable
private fun campoColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = COL_TEXT_CREAM,
    unfocusedTextColor = COL_TEXT_CREAM,
    cursorColor = COL_GOLD,
    focusedBorderColor = COL_GOLD,
    unfocusedBorderColor = COL_CARD_BORDER,
    focusedLabelColor = COL_GOLD,
    unfocusedLabelColor = COL_TEXT_DIM,
)

@Composable
fun EquiposScreen(
    onVolver: () -> Unit,
    vm: BracketViewModel = viewModel(factory = BracketViewModel.factory(LocalContext.current)),
) {
    val teams by vm.teams.collectAsState()

    var teamParaEditar by remember { mutableStateOf<Team?>(null) }
    var mostrandoAgregar by remember { mutableStateOf(false) }
    var teamParaEliminar by remember { mutableStateOf<Team?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val freeSlot = remember(teams) {
        val usadas = teams.map { it.position }.toSet()
        (0..31).firstOrNull { it !in usadas }
    }

    Scaffold(
        containerColor = COL_BG,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Selecciones", color = COL_TEXT_CREAM) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = COL_GOLD)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = COL_CARD)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (freeSlot == null) {
                        scope.launch { snackbarHostState.showSnackbar("No hay slots libres (0-31 ocupados)") }
                    } else {
                        mostrandoAgregar = true
                    }
                },
                containerColor = if (freeSlot == null) Color(0xFF453C30) else COL_GOLD,
                contentColor = if (freeSlot == null) COL_TEXT_DIM else Color(0xFF17130E)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar equipo")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(teams, key = { it.position }) { team ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = COL_CARD),
                    border = BorderStroke(1.dp, COL_CARD_BORDER),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${team.position}",
                            color = COL_TEXT_DIM,
                            fontSize = 12.sp,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(team.f, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                team.n,
                                color = COL_TEXT_CREAM,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            val color = estadoColor(team.s)
                            AssistChip(
                                onClick = {},
                                label = { Text(estadoTexto(team.s), fontSize = 10.sp, color = color) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = color.copy(alpha = 0.15f),
                                    labelColor = color
                                ),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        IconButton(onClick = { teamParaEditar = team }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = COL_GOLD)
                        }
                        IconButton(onClick = { teamParaEliminar = team }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = COL_DANGER)
                        }
                    }
                }
            }
        }
    }

    teamParaEditar?.let { team ->
        EquipoDialog(
            titulo = "Editar equipo",
            equipoEnEdicion = team,
            onDismiss = { teamParaEditar = null },
            onConfirmar = { nombre, bandera, estado ->
                vm.guardarEquipo(
                    team.copy(
                        n = nombre,
                        f = bandera,
                        s = estado,
                        hora = if (estado == 'P') team.hora else null
                    )
                )
                teamParaEditar = null
            }
        )
    }

    if (mostrandoAgregar && freeSlot != null) {
        EquipoDialog(
            titulo = "Agregar equipo (slot $freeSlot)",
            equipoEnEdicion = null,
            onDismiss = { mostrandoAgregar = false },
            onConfirmar = { nombre, bandera, estado ->
                vm.guardarEquipo(Team(n = nombre, f = bandera, s = estado, position = freeSlot))
                mostrandoAgregar = false
            }
        )
    }

    teamParaEliminar?.let { team ->
        AlertDialog(
            onDismissRequest = { teamParaEliminar = null },
            containerColor = COL_CARD,
            title = { Text("¿Eliminar equipo?", color = COL_TEXT_CREAM, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se eliminará ${team.f} ${team.n} del slot ${team.position}.",
                    color = Color(0xFFBDA87E)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.eliminarEquipo(team); teamParaEliminar = null }) {
                    Text("Eliminar", color = COL_DANGER)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamParaEliminar = null }) {
                    Text("Cancelar", color = COL_TEXT_DIM)
                }
            }
        )
    }
}

@Composable
private fun EquipoDialog(
    titulo: String,
    equipoEnEdicion: Team?,
    onDismiss: () -> Unit,
    onConfirmar: (nombre: String, bandera: String, estado: Char) -> Unit,
) {
    var nombre by remember(equipoEnEdicion) { mutableStateOf(equipoEnEdicion?.n ?: "") }
    var bandera by remember(equipoEnEdicion) { mutableStateOf(equipoEnEdicion?.f ?: "") }
    var estado by remember(equipoEnEdicion) { mutableStateOf(equipoEnEdicion?.s ?: 'P') }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = COL_CARD,
        title = { Text(titulo, color = COL_TEXT_CREAM, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    colors = campoColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bandera,
                    onValueChange = { if (it.length <= 8) bandera = it },
                    label = { Text("Bandera (emoji)") },
                    singleLine = true,
                    colors = campoColors(),
                    modifier = Modifier.width(140.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf('W' to "Clasificado", 'L' to "Eliminado", 'P' to "Por jugar").forEach { (valor, label) ->
                        FilterChip(
                            selected = estado == valor,
                            onClick = { estado = valor },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(nombre.trim(), bandera.trim(), estado) },
                enabled = nombre.isNotBlank() && bandera.isNotBlank()
            ) { Text("Guardar", color = COL_GOLD) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = COL_TEXT_DIM) }
        }
    )
}
