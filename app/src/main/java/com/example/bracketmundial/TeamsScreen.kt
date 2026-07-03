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
import androidx.compose.ui.res.stringResource
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

@Composable
private fun teamStatusText(t: Team): String = when {
    t.eliminated -> stringResource(R.string.status_eliminated)
    t.wins >= 5 -> stringResource(R.string.round_champion)
    else -> stringResource(R.string.status_round_label, roundName(t.wins))
}

private fun statusColor(t: Team) = when {
    t.eliminated -> COL_DANGER
    t.wins >= 5 -> COL_GOLD
    else -> COL_TEXT_DIM
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = COL_TEXT_CREAM,
    unfocusedTextColor = COL_TEXT_CREAM,
    cursorColor = COL_GOLD,
    focusedBorderColor = COL_GOLD,
    unfocusedBorderColor = COL_CARD_BORDER,
    focusedLabelColor = COL_GOLD,
    unfocusedLabelColor = COL_TEXT_DIM,
)

@Composable
fun TeamsScreen(
    onBack: () -> Unit,
    vm: BracketViewModel = viewModel(factory = BracketViewModel.factory(LocalContext.current)),
) {
    val teams by vm.teams.collectAsState()

    var teamBeingEdited by remember { mutableStateOf<Team?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var teamToDelete by remember { mutableStateOf<Team?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noFreeSlotsMessage = stringResource(R.string.no_free_slots_snackbar)

    val freeSlot = remember(teams) {
        val used = teams.map { it.position }.toSet()
        (0..31).firstOrNull { it !in used }
    }

    Scaffold(
        containerColor = COL_BG,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_teams), color = COL_TEXT_CREAM) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = COL_GOLD)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = COL_CARD)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (freeSlot == null) {
                        scope.launch { snackbarHostState.showSnackbar(noFreeSlotsMessage) }
                    } else {
                        showAddDialog = true
                    }
                },
                containerColor = if (freeSlot == null) Color(0xFF453C30) else COL_GOLD,
                contentColor = if (freeSlot == null) COL_TEXT_DIM else Color(0xFF17130E)
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_team))
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
                                displayName(team),
                                color = COL_TEXT_CREAM,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            val color = statusColor(team)
                            AssistChip(
                                onClick = {},
                                label = { Text(teamStatusText(team), fontSize = 10.sp, color = color) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = color.copy(alpha = 0.15f),
                                    labelColor = color
                                ),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        IconButton(onClick = { teamBeingEdited = team }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit), tint = COL_GOLD)
                        }
                        IconButton(onClick = { teamToDelete = team }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), tint = COL_DANGER)
                        }
                    }
                }
            }
        }
    }

    teamBeingEdited?.let { team ->
        // The name the user would see right now for this team's recognized country (if any).
        // If they save that value unchanged, the countryKey (and its live translation) is kept;
        // if they type something else, it's treated as an explicit custom rename and cleared.
        val expectedName = team.countryKey?.let { COUNTRY_NAME_RES[it] }?.let { stringResource(it) }
        TeamDialog(
            title = stringResource(R.string.dialog_edit_team_title),
            teamBeingEdited = team,
            onDismiss = { teamBeingEdited = null },
            onConfirm = { name, flag, eliminated ->
                val newCountryKey = if (name == expectedName) team.countryKey else null
                vm.saveTeam(
                    team.copy(
                        n = name,
                        f = flag,
                        eliminated = eliminated,
                        matchTime = if (eliminated) null else team.matchTime,
                        countryKey = newCountryKey,
                    )
                )
                teamBeingEdited = null
            }
        )
    }

    if (showAddDialog && freeSlot != null) {
        TeamDialog(
            title = stringResource(R.string.dialog_add_team_title, freeSlot),
            teamBeingEdited = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, flag, eliminated ->
                vm.saveTeam(Team(n = name, f = flag, eliminated = eliminated, position = freeSlot))
                showAddDialog = false
            }
        )
    }

    teamToDelete?.let { team ->
        AlertDialog(
            onDismissRequest = { teamToDelete = null },
            containerColor = COL_CARD,
            title = { Text(stringResource(R.string.dialog_delete_team_title), color = COL_TEXT_CREAM, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.dialog_delete_team_message, team.f, displayName(team), team.position),
                    color = Color(0xFFBDA87E)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteTeam(team); teamToDelete = null }) {
                    Text(stringResource(R.string.action_delete), color = COL_DANGER)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = COL_TEXT_DIM)
                }
            }
        )
    }
}

@Composable
private fun TeamDialog(
    title: String,
    teamBeingEdited: Team?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, flag: String, eliminated: Boolean) -> Unit,
) {
    val initialName = teamBeingEdited?.let { displayName(it) } ?: ""
    var name by remember(teamBeingEdited) { mutableStateOf(initialName) }
    var flag by remember(teamBeingEdited) { mutableStateOf(teamBeingEdited?.f ?: "") }
    var eliminated by remember(teamBeingEdited) { mutableStateOf(teamBeingEdited?.eliminated ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = COL_CARD,
        title = { Text(title, color = COL_TEXT_CREAM, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name_label)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = flag,
                    onValueChange = { if (it.length <= 8) flag = it },
                    label = { Text(stringResource(R.string.field_flag_label)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.width(140.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.status_eliminated), color = COL_TEXT_CREAM, modifier = Modifier.weight(1f))
                    Switch(
                        checked = eliminated,
                        onCheckedChange = { eliminated = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = COL_GOLD,
                            checkedTrackColor = COL_GOLD.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), flag.trim(), eliminated) },
                enabled = name.isNotBlank() && flag.isNotBlank()
            ) { Text(stringResource(R.string.action_save), color = COL_GOLD) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = COL_TEXT_DIM) }
        }
    )
}
