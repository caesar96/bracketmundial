@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bracketmundial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bracketmundial.data.ApiResultsUiState
import com.example.bracketmundial.data.ApiResultsViewModel
import com.example.bracketmundial.data.MatchResult

private val COL_CARD = Color(0xFF211A11)
private val COL_CARD_BORDER = Color(0xFF453923)
private val COL_TEXT_CREAM = Color(0xFFE9E2D4)
private val COL_TEXT_DIM = Color(0xFF7D7060)
private val COL_DANGER = Color(0xFFD13A30)

@Composable
fun ApiResultsScreen(
    onBack: () -> Unit,
    vm: ApiResultsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = COL_BG,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_api_results), color = COL_TEXT_CREAM) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = COL_GOLD)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_reload), tint = COL_GOLD)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = COL_CARD)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ApiResultsUiState.Loading -> CircularProgressIndicator(
                    color = COL_GOLD,
                    modifier = Modifier.align(Alignment.Center)
                )
                is ApiResultsUiState.Error -> Text(
                    s.message,
                    color = COL_DANGER,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                is ApiResultsUiState.Empty -> Text(
                    stringResource(R.string.api_results_empty),
                    color = COL_TEXT_DIM,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                is ApiResultsUiState.Data -> MatchList(s.source, s.matches)
            }
        }
    }
}

@Composable
private fun MatchList(source: String, matches: List<MatchResult>) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                stringResource(R.string.api_results_source, source),
                color = COL_GOLD,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        items(matches, key = { it.summary }) { match ->
            Card(
                colors = CardDefaults.cardColors(containerColor = COL_CARD),
                border = BorderStroke(1.dp, COL_CARD_BORDER),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    match.summary,
                    color = COL_TEXT_CREAM,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
