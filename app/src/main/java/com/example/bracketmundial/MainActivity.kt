@file:OptIn(ExperimentalLayoutApi::class)

package com.example.bracketmundial

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bracketmundial.work.SyncScheduler
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/* ============================================================
 *  MODEL — wins = rounds won (0..5; 5 = champion), eliminated = knocked out
 * ============================================================ */
data class Team(
    val n: String,
    val f: String,                 // flag emoji
    val c: Color? = null,          // line color once it advances
    val wins: Int = 0,
    val eliminated: Boolean = false,
    val matchTime: String? = null,
    val position: Int = -1,        // slot 0..31 that determines its bracket key
    val countryKey: String? = null, // stable id into COUNTRY_NAME_RES; null once the name is manually overridden
)

/** Canonical id -> string resource, for the 32 recognized World Cup countries. Team.countryKey
 *  references these keys so display names keep tracking the device's current locale (via
 *  [displayName]) and result-matching in SyncRepository stays correct regardless of locale —
 *  until a team's name is edited away from the resolved value, which clears the key. */
val COUNTRY_NAME_RES: Map<String, Int> = mapOf(
    "brazil" to R.string.team_brazil,
    "japan" to R.string.team_japan,
    "ivory_coast" to R.string.team_ivory_coast,
    "norway" to R.string.team_norway,
    "mexico" to R.string.team_mexico,
    "ecuador" to R.string.team_ecuador,
    "england" to R.string.team_england,
    "dr_congo" to R.string.team_dr_congo,
    "argentina" to R.string.team_argentina,
    "cape_verde" to R.string.team_cape_verde,
    "australia" to R.string.team_australia,
    "egypt" to R.string.team_egypt,
    "switzerland" to R.string.team_switzerland,
    "algeria" to R.string.team_algeria,
    "colombia" to R.string.team_colombia,
    "ghana" to R.string.team_ghana,
    "senegal" to R.string.team_senegal,
    "belgium" to R.string.team_belgium,
    "bosnia" to R.string.team_bosnia,
    "usa" to R.string.team_usa,
    "austria" to R.string.team_austria,
    "spain" to R.string.team_spain,
    "croatia" to R.string.team_croatia,
    "portugal" to R.string.team_portugal,
    "morocco" to R.string.team_morocco,
    "south_africa" to R.string.team_south_africa,
    "netherlands" to R.string.team_netherlands,
    "canada" to R.string.team_canada,
    "sweden" to R.string.team_sweden,
    "france" to R.string.team_france,
    "paraguay" to R.string.team_paraguay,
    "germany" to R.string.team_germany,
)

/** Live-localized display name: resolves through the current locale for recognized
 *  countries, falls back to the stored (possibly custom/stale) name otherwise. */
@Composable
fun displayName(team: Team): String =
    team.countryKey?.let { COUNTRY_NAME_RES[it] }?.let { stringResource(it) } ?: team.n

/** Builds the 32 initial teams with names resolved from string resources,
 *  so they render in whichever locale (English/Spanish) is active. */
fun initialTeams(context: Context): List<Team> = listOf(
    Team(context.getString(R.string.team_brazil), "🇧🇷", c = Color(0xFFF2C200), wins = 1, countryKey = "brazil"),
    Team(context.getString(R.string.team_japan), "🇯🇵", eliminated = true, countryKey = "japan"),
    Team(context.getString(R.string.team_ivory_coast), "🇨🇮", eliminated = true, countryKey = "ivory_coast"),
    Team(context.getString(R.string.team_norway), "🇳🇴", c = Color(0xFFD13A30), wins = 1, countryKey = "norway"),
    Team(context.getString(R.string.team_mexico), "🇲🇽", c = Color(0xFF1F9E4B), wins = 1, countryKey = "mexico"),
    Team(context.getString(R.string.team_ecuador), "🇪🇨", eliminated = true, countryKey = "ecuador"),
    Team(context.getString(R.string.team_england), "🏴󠁧󠁢󠁥󠁮󠁧󠁿", c = Color(0xFFD13A30), wins = 1, countryKey = "england"),
    Team(context.getString(R.string.team_dr_congo), "🇨🇩", eliminated = true, countryKey = "dr_congo"),
    Team(context.getString(R.string.team_argentina), "🇦🇷", countryKey = "argentina"),
    Team(context.getString(R.string.team_cape_verde), "🇨🇻", countryKey = "cape_verde"),
    Team(context.getString(R.string.team_australia), "🇦🇺", countryKey = "australia"),
    Team(context.getString(R.string.team_egypt), "🇪🇬", countryKey = "egypt"),
    Team(context.getString(R.string.team_switzerland), "🇨🇭", countryKey = "switzerland"),
    Team(context.getString(R.string.team_algeria), "🇩🇿", countryKey = "algeria"),
    Team(context.getString(R.string.team_colombia), "🇨🇴", countryKey = "colombia"),
    Team(context.getString(R.string.team_ghana), "🇬🇭", countryKey = "ghana"),
    Team(context.getString(R.string.team_senegal), "🇸🇳", eliminated = true, countryKey = "senegal"),
    Team(context.getString(R.string.team_belgium), "🇧🇪", c = Color(0xFFF2C200), wins = 1, countryKey = "belgium"),
    Team(context.getString(R.string.team_bosnia), "🇧🇦", eliminated = true, countryKey = "bosnia"),
    Team(context.getString(R.string.team_usa), "🇺🇸", c = Color(0xFF3F5FB5), wins = 1, countryKey = "usa"),
    Team(context.getString(R.string.team_austria), "🇦🇹", eliminated = true, countryKey = "austria"),
    Team(context.getString(R.string.team_spain), "🇪🇸", c = Color(0xFFD13A30), wins = 1, countryKey = "spain"),
    Team(context.getString(R.string.team_croatia), "🇭🇷", matchTime = "5:00 PM", countryKey = "croatia"),
    Team(context.getString(R.string.team_portugal), "🇵🇹", matchTime = "5:00 PM", countryKey = "portugal"),
    Team(context.getString(R.string.team_morocco), "🇲🇦", c = Color(0xFF1F9E4B), wins = 1, countryKey = "morocco"),
    Team(context.getString(R.string.team_south_africa), "🇿🇦", eliminated = true, countryKey = "south_africa"),
    Team(context.getString(R.string.team_netherlands), "🇳🇱", eliminated = true, countryKey = "netherlands"),
    Team(context.getString(R.string.team_canada), "🇨🇦", c = Color(0xFFD13A30), wins = 1, countryKey = "canada"),
    Team(context.getString(R.string.team_sweden), "🇸🇪", eliminated = true, countryKey = "sweden"),
    Team(context.getString(R.string.team_france), "🇫🇷", c = Color(0xFF3555C4), wins = 1, countryKey = "france"),
    Team(context.getString(R.string.team_paraguay), "🇵🇾", c = Color(0xFFD13A30), wins = 1, countryKey = "paraguay"),
    Team(context.getString(R.string.team_germany), "🇩🇪", eliminated = true, countryKey = "germany"),
).mapIndexed { i, t -> t.copy(position = i) }

/* Line palette for teams that advance without an assigned color */
val FALLBACK_COLORS = listOf(
    Color(0xFFD13A30), Color(0xFFF2C200), Color(0xFF1F9E4B), Color(0xFF3F5FB5)
)

/* ============================================================
 *  GEOMETRY — virtual 1700 x 1700 space, like the SVG
 * ============================================================ */
const val BOARD = 1700f
const val CX = 850f
const val CY = 850f

const val R_LABEL = 745f
const val R_TEAM = 655f
const val R_ARC1 = 578f
const val R_N32 = 505f
const val R_ARC2 = 440f
const val R_N16 = 345f
const val R_QF = 240f
const val R_SF = 148f
const val STEP = 360f / 32f

val COL_LINE = Color(0xFF4A4034)
val COL_LINE_SOFT = Color(0xFF352D24)
val COL_GOLD = Color(0xFFE8A83C)
val COL_GOLD_SOFT = Color(0xFFA97A2E)
val COL_DOT = Color(0xFF5C5142)
val COL_BG = Color(0xFF17130E)

fun pt(angleDeg: Float, r: Float): Offset {
    val rad = Math.toRadians((angleDeg - 90).toDouble())
    return Offset(CX + r * cos(rad).toFloat(), CY + r * sin(rad).toFloat())
}

fun angTeam(i: Int) = i * STEP
fun angPair(k: Int) = (2 * k + 0.5f) * STEP
fun angSect(k: Int) = (4 * k + 1.5f) * STEP
fun angQF(k: Int) = (8 * k + 3.5f) * STEP
fun angSF(k: Int) = (16 * k + 7.5f) * STEP

/** Radial + arc + radial stroke (the design's signature "elbow"). */
fun elbowPath(a1: Float, a2: Float, rA: Float, rB: Float, rC: Float): Path {
    val p1 = pt(a1, rA)
    val p2 = pt(a1, rB)
    val p4 = pt(a2, rC)
    var sweep = (a2 - a1) % 360f
    if (sweep > 180f) sweep -= 360f
    if (sweep < -180f) sweep += 360f
    val oval = Rect(Offset(CX - rB, CY - rB), Size(rB * 2, rB * 2))
    return Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        arcTo(oval, a1 - 90f, sweep, forceMoveTo = false)  // Android's 0° = 3 o'clock
        lineTo(p4.x, p4.y)
    }
}

/* ============================================================
 *  STATE LOGIC — rounds: 0=round of 32, 1=round of 16, 2=quarterfinals,
 *  3=semifinals, 4=final. A team's wins == the index of the round it's
 *  about to play next (5 = already champion).
 * ============================================================ */

/** The other half of [pos]'s group at [round] (group = pos / 2^(round+1),
 *  split into two halves of 2^round positions each). */
private fun otherHalfOfGroup(pos: Int, round: Int): IntRange {
    val groupSize = 1 shl (round + 1)
    val halfSize = 1 shl round
    val groupStart = (pos / groupSize) * groupSize
    val ownHalfStart = (pos / halfSize) * halfSize
    val otherHalfStart = if (ownHalfStart == groupStart) groupStart + halfSize else groupStart
    return otherHalfStart until otherHalfStart + halfSize
}

/** Potential rival of [pos] in its next round (r = wins): the only survivor
 *  with wins == r in the other half of its round-r group.
 *  Null if that half doesn't have a survivor yet. */
fun currentRival(byPos: Map<Int, Team>, pos: Int): Team? {
    val t = byPos[pos] ?: return null
    val r = t.wins
    if (t.eliminated || r >= 5) return null
    return otherHalfOfGroup(pos, r)
        .asSequence()
        .mapNotNull { byPos[it] }
        .firstOrNull { it.wins == r && !it.eliminated }
}

/** Rival that [pos] defeated in its last played round (r-1, with r = wins):
 *  the only eliminated team with wins == r-1 in the other half of that group.
 *  Null if wins == 0 or the data was hand-edited and doesn't line up. */
fun defeatedRival(byPos: Map<Int, Team>, pos: Int): Team? {
    val t = byPos[pos] ?: return null
    val r = t.wins
    if (r < 1) return null
    return otherHalfOfGroup(pos, r - 1)
        .asSequence()
        .mapNotNull { byPos[it] }
        .firstOrNull { it.wins == r - 1 && it.eliminated }
}

@Composable
fun roundName(wins: Int): String = when (wins) {
    0 -> stringResource(R.string.round_of_32)
    1 -> stringResource(R.string.round_of_16)
    2 -> stringResource(R.string.round_quarterfinals)
    3 -> stringResource(R.string.round_semifinals)
    4 -> stringResource(R.string.round_final)
    else -> stringResource(R.string.round_champion)
}

@Composable
fun statusText(byPos: Map<Int, Team>, position: Int): String {
    val t = byPos[position] ?: return stringResource(R.string.status_empty_slot)
    return when {
        t.eliminated -> stringResource(R.string.status_eliminated_in, roundName(t.wins))
        t.wins >= 5 -> stringResource(R.string.status_champion)
        else -> {
            val rival = currentRival(byPos, position)
            val whenSuffix = t.matchTime?.let { " (" + stringResource(R.string.status_schedule_today, it) + ")" } ?: ""
            if (rival != null) stringResource(R.string.status_vs, roundName(t.wins), rival.f, displayName(rival), whenSuffix)
            else stringResource(R.string.status_rival_tbd, roundName(t.wins), whenSuffix)
        }
    }
}

/* ============================================================
 *  SCREEN
 * ============================================================ */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* if denied, the worker simply won't be able to notify */ }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    SyncScheduler.schedule(context)
                }

                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "bracket") {
                    composable("bracket") {
                        BracketScreen(
                            onOpenTeams = { navController.navigate("teams") },
                            onOpenApiResults = { navController.navigate("apiResults") },
                        )
                    }
                    composable("teams") {
                        TeamsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("apiResults") {
                        ApiResultsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

private data class FlagHit(val center: Offset, val radius: Float, val position: Int)

@Composable
fun BracketScreen(
    onOpenTeams: () -> Unit,
    onOpenApiResults: () -> Unit,
    vm: BracketViewModel = viewModel(factory = BracketViewModel.factory(LocalContext.current)),
) {
    val teams by vm.teams.collectAsState()
    val byPos = remember(teams) { teams.associateBy { it.position } }
    // Canvas drawing isn't @Composable, so display names (locale-aware for recognized
    // countries) are resolved here and passed into drawBracket as plain strings.
    val displayNames = teams.associate { it.position to displayName(it) }
    val isSyncing by vm.isSyncing.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Animatable pan & zoom ----
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showUndoConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSyncMessage()
        }
    }

    val textMeasurer = rememberTextMeasurer()

    // Tappable zones: only slots with an assigned team, including the
    // advanced-round flags for each round it has already won
    val hits = remember(teams) {
        buildList {
            teams.forEach { t ->
                val p = t.position
                add(FlagHit(pt(angTeam(p), R_TEAM), 42f, p))
                if (t.wins >= 1) add(FlagHit(pt(angPair(p / 2), R_N16 + 62f), 40f, p))
                if (t.wins >= 2) add(FlagHit(pt(angSect(p / 4), R_QF + 55f), 36f, p))
                if (t.wins >= 3) add(FlagHit(pt(angQF(p / 8), R_SF + 50f), 32f, p))
                if (t.wins >= 4) add(FlagHit(pt(angSF(p / 16), 100f), 30f, p))
                if (t.wins >= 5) add(FlagHit(Offset(CX, CY - 200f), 34f, p))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(COL_BG).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.header_title),
                    color = Color(0xFFE9DCC0),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    fontSize = 18.sp
                )
                Text(
                    stringResource(R.string.header_subtitle),
                    color = COL_GOLD,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = COL_GOLD,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_results), tint = Color(0xFF8D7F66))
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.List, contentDescription = stringResource(R.string.cd_menu), tint = Color(0xFF8D7F66))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_teams)) },
                            onClick = { menuExpanded = false; onOpenTeams() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_api_results)) },
                            onClick = { menuExpanded = false; onOpenApiResults() }
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Pinch-zoom + drag, anchored to the gesture point
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val old = scale.value
                        val new = (old * zoom).coerceIn(0.6f, 6f)
                        val newOffset =
                            (offset.value - centroid) * (new / old) + centroid + pan
                        scope.launch {
                            scale.snapTo(new)
                            offset.snapTo(newOffset)
                        }
                    }
                }
                // Tap: select · Double tap: animated zoom
                .pointerInput(hits) {
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero
                    detectTapGestures(
                        onTap = { tap ->
                            val now = System.currentTimeMillis()
                            val isDoubleTap =
                                now - lastTapTime < 300L && (tap - lastTapPos).getDistance() < 100f
                            lastTapTime = now
                            lastTapPos = tap

                            if (isDoubleTap) {
                                lastTapTime = 0L  // prevents a triple tap from counting as double
                                scope.launch {
                                    if (scale.value > 1.05f) {
                                        launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        launch { offset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    } else {
                                        val target = 2.5f
                                        val newOffset =
                                            (offset.value - tap) * (target / scale.value) + tap
                                        launch { scale.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        launch { offset.animateTo(newOffset, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                }
                            } else {
                                // instant selection, no waiting for the double-tap timeout
                                val idx = hitTest(tap, size.width, size.height, scale.value, offset.value, hits)
                                selected = if (idx == selected) null else idx
                            }
                        }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Reading scale/offset HERE (inside the lambda) is the key:
                        // Compose only re-transforms the GPU layer, without redrawing
                        translationX = offset.value.x
                        translationY = offset.value.y
                        scaleX = scale.value
                        scaleY = scale.value
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                val fit = min(size.width, size.height) / BOARD
                val fitOffset = Offset(
                    (size.width - BOARD * fit) / 2f,
                    (size.height - BOARD * fit) / 2f
                )
                withTransform({
                    translate(fitOffset.x, fitOffset.y)
                    scale(fit, fit, pivot = Offset.Zero)
                }) {
                    drawBracket(textMeasurer, byPos, selected, displayNames)
                }
            }
        }

        // Bottom panel
        Surface(
            color = Color(0xFF211A11),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, Color(0xFF453923)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val selTeam = selected?.let { byPos[it] }
                val rivalTeam = selTeam?.let { currentRival(byPos, it.position) }
                val defeatedTeam = selTeam?.let { defeatedRival(byPos, it.position) }

                // Row 1: info of the selected team (or instructions)
                if (selTeam == null) {
                    Text(
                        stringResource(R.string.bracket_hint),
                        color = Color(0xFF7D7060),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selTeam.f, fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                displayName(selTeam),
                                color = Color(0xFFE9E2D4),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(statusText(byPos, selTeam.position), color = Color(0xFFBDA87E), fontSize = 12.sp)
                        }
                    }
                }

                // Row 2: actions — only the ones that apply, wraps if they don't fit
                val hasActions = (selTeam != null && !selTeam.eliminated && selTeam.wins < 5 && rivalTeam != null) ||
                    (selTeam != null && !selTeam.eliminated && selTeam.wins >= 1 && defeatedTeam != null)
                if (hasActions) Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (selTeam != null && !selTeam.eliminated && selTeam.wins >= 1 && defeatedTeam != null) {
                        TextButton(onClick = { showUndoConfirm = true }) {
                            Text(stringResource(R.string.action_undo), color = Color(0xFF7D7060), fontSize = 12.sp)
                        }
                    }
                    if (selTeam != null && !selTeam.eliminated && selTeam.wins < 5 && rivalTeam != null) {
                        TextButton(onClick = { showResultDialog = true }) {
                            Text(stringResource(R.string.action_record_result), color = COL_GOLD, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text(stringResource(R.string.action_reset), color = Color(0xFF7D7060), fontSize = 12.sp)
                    }
                }
            }
        }
    }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showResultDialog) {
        val selTeam = selected?.let { byPos[it] }
        val rivalTeam = selTeam?.let { currentRival(byPos, it.position) }
        if (selTeam != null && rivalTeam != null) {
            AlertDialog(
                onDismissRequest = { showResultDialog = false },
                containerColor = Color(0xFF211A11),
                title = { Text(stringResource(R.string.dialog_who_won_title), color = Color(0xFFE9E2D4), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(selTeam, rivalTeam).forEach { candidate ->
                            val loser = if (candidate.position == selTeam.position) rivalTeam else selTeam
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.recordWinner(candidate.position, loser.position)
                                        selected = candidate.position
                                        showResultDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(candidate.f, fontSize = 28.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    displayName(candidate),
                                    color = Color(0xFFE9E2D4),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showResultDialog = false }) {
                        Text(stringResource(R.string.action_cancel), color = COL_GOLD)
                    }
                }
            )
        } else {
            showResultDialog = false
        }
    }

    if (showUndoConfirm) {
        val selTeam = selected?.let { byPos[it] }
        val defeatedTeam = selTeam?.let { defeatedRival(byPos, it.position) }
        if (selTeam != null && defeatedTeam != null) {
            AlertDialog(
                onDismissRequest = { showUndoConfirm = false },
                containerColor = Color(0xFF211A11),
                title = { Text(stringResource(R.string.dialog_undo_title), color = Color(0xFFE9E2D4), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        stringResource(R.string.dialog_undo_message, displayName(selTeam), defeatedTeam.f, displayName(defeatedTeam)),
                        color = Color(0xFFBDA87E)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.undoResult(selTeam.position)
                        selected = selTeam.position
                        showUndoConfirm = false
                    }) {
                        Text(stringResource(R.string.action_confirm), color = COL_GOLD)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUndoConfirm = false }) {
                        Text(stringResource(R.string.action_cancel), color = Color(0xFF7D7060))
                    }
                }
            )
        } else {
            showUndoConfirm = false
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Color(0xFF211A11),
            title = { Text(stringResource(R.string.dialog_reset_title), color = Color(0xFFE9E2D4), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.dialog_reset_message),
                    color = Color(0xFFBDA87E)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetBracket()
                    selected = null
                    showResetConfirm = false
                }) {
                    Text(stringResource(R.string.action_reset), color = COL_GOLD)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = Color(0xFF7D7060))
                }
            }
        )
    }
}

/** Converts a screen tap into board coordinates and looks up the flag. */
private fun hitTest(
    tap: Offset, w: Int, h: Int, scale: Float, offset: Offset, hits: List<FlagHit>,
): Int? {
    val fit = min(w, h) / BOARD
    val fitOffset = Offset((w - BOARD * fit) / 2f, (h - BOARD * fit) / 2f)
    val p = ((tap - offset) / scale - fitOffset) / fit
    return hits.firstOrNull {
        hypot(p.x - it.center.x, p.y - it.center.y) <= it.radius + 14f
    }?.position
}

/* ============================================================
 *  BOARD DRAWING
 * ============================================================ */
private fun DrawScope.drawBracket(
    textMeasurer: TextMeasurer,
    byPos: Map<Int, Team>,
    selected: Int?,
    displayNames: Map<Int, String>,
) {
    fun stroke(w: Float, dash: Boolean = false) = Stroke(
        width = w,
        cap = StrokeCap.Round,
        pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(2f, 18f)) else null
    )
    fun dotAt(a: Float, r: Float, size: Float, color: Color = COL_DOT) =
        drawCircle(color, size, pt(a, r))

    /* Upcoming rounds: round of 16 → quarterfinals → semifinals → final.
     * Each segment lights up gold if some team has already traveled through it
     * (wins >= target round) — the rest reads as a future path. */
    fun advancerAt(level: Int, k: Int) = byPos.values.firstOrNull { it.wins >= level && it.position / (1 shl level) == k }

    for (k in 0 until 8) {
        val path = elbowPath(angSect(k), angQF(k / 2), R_N16, (R_N16 + R_QF) / 2 + 18f, R_QF)
        val advancer = advancerAt(2, k)
        if (advancer != null) {
            drawPath(path, COL_GOLD_SOFT, style = stroke(if (selected == advancer.position) 8f else 5f))
            drawPath(path, COL_GOLD, style = stroke(2f, dash = true))
        } else {
            drawPath(path, COL_LINE, style = stroke(3.5f))
        }
        dotAt(angSect(k), R_N16, 7f)
    }
    for (k in 0 until 4) {
        val path = elbowPath(angQF(k), angSF(k / 2), R_QF, (R_QF + R_SF) / 2 + 12f, R_SF)
        val advancer = advancerAt(3, k)
        if (advancer != null) {
            drawPath(path, COL_GOLD_SOFT, style = stroke(if (selected == advancer.position) 8f else 5f))
            drawPath(path, COL_GOLD, style = stroke(2f, dash = true))
        } else {
            drawPath(path, COL_LINE, style = stroke(3.5f))
        }
        dotAt(angQF(k), R_QF, 7f)
    }
    for (k in 0 until 2) {
        val start = pt(angSF(k), R_SF); val end = pt(angSF(k), 78f)
        val advancer = advancerAt(4, k)
        if (advancer != null) {
            drawLine(COL_GOLD_SOFT, start, end, strokeWidth = if (selected == advancer.position) 8f else 5f, cap = StrokeCap.Round)
            drawLine(COL_GOLD, start, end, strokeWidth = 2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 18f)))
        } else {
            drawLine(COL_LINE, start, end, strokeWidth = 3.5f, cap = StrokeCap.Round)
        }
        dotAt(angSF(k), R_SF, 7f)
    }

    /* Round-of-32 brackets */
    for (k in 0 until 16) {
        val iA = 2 * k; val iB = 2 * k + 1
        val a = byPos[iA]; val b = byPos[iB]
        val decided = (a != null && (a.wins >= 1 || a.eliminated)) || (b != null && (b.wins >= 1 || b.eliminated))
        for ((i, t) in listOf(iA to a, iB to b)) {
            val won = t != null && t.wins >= 1
            val color = when {
                t == null -> COL_LINE
                won -> t.c ?: COL_GOLD
                decided -> COL_LINE_SOFT
                else -> COL_LINE
            }
            drawPath(
                elbowPath(angTeam(i), angPair(k), R_TEAM - 44f, R_ARC1, R_N32),
                color,
                style = stroke(if (won) (if (selected == i) 8f else 5f) else 3.5f)
            )
        }
        dotAt(angPair(k), R_N32, 7f, if (decided) Color(0xFF7A6A50) else COL_DOT)

        val winner = when {
            a != null && a.wins >= 1 -> iA
            b != null && b.wins >= 1 -> iB
            else -> null
        }
        val advance = elbowPath(angPair(k), angSect(k / 2), R_N32, R_ARC2, R_N16)
        if (winner != null) {
            drawPath(advance, COL_GOLD_SOFT, style = stroke(if (selected == winner) 8f else 5f))
            drawPath(advance, COL_GOLD, style = stroke(2f, dash = true))
        } else {
            drawPath(advance, COL_LINE, style = stroke(3.5f))
        }
    }

    /* Glow and center trophy */
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xD9F6C05A), 0.35f to Color(0x59C98A2E), 0.7f to Color.Transparent,
            center = Offset(CX, CY), radius = 260f
        ),
        radius = 260f, center = Offset(CX, CY)
    )
    drawTrophy()

    /* Flags and labels */
    for (i in 0 until 32) {
        val t = byPos[i]
        if (t == null) {
            drawEmptySlot(textMeasurer, pt(angTeam(i), R_TEAM), 42f)
            continue
        }
        val out = t.eliminated
        drawFlag(textMeasurer, pt(angTeam(i), R_TEAM), 42f, t, out, selected == i)

        val lp = pt(angTeam(i), R_LABEL)
        val label = textMeasurer.measure(
            AnnotatedString((displayNames[i] ?: t.n).uppercase()),
            style = TextStyle(
                color = if (out) Color(0xFF5B5347) else Color(0xFFCFC4AE),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
            )
        )
        drawText(label, topLeft = Offset(lp.x - label.size.width / 2f, lp.y - label.size.height / 2f))

        if (t.wins >= 1) drawFlag(textMeasurer, pt(angPair(i / 2), R_N16 + 62f), 40f, t, out, selected == i)
        if (t.wins >= 2) drawFlag(textMeasurer, pt(angSect(i / 4), R_QF + 55f), 36f, t, out, selected == i)
        if (t.wins >= 3) drawFlag(textMeasurer, pt(angQF(i / 8), R_SF + 50f), 32f, t, out, selected == i)
        if (t.wins >= 4) drawFlag(textMeasurer, pt(angSF(i / 16), 100f), 30f, t, out, selected == i)
        if (t.wins >= 5) drawFlag(textMeasurer, Offset(CX, CY - 200f), 34f, t, out, true)
    }
}

private fun DrawScope.drawFlag(
    textMeasurer: TextMeasurer,
    center: Offset, radius: Float, team: Team, out: Boolean, isSelected: Boolean,
) {
    // circle background
    drawCircle(Color(0xFF0F0C08), radius, center)

    // circular clip (equivalent to border-radius + overflow:hidden)
    val clip = Path().apply {
        addOval(Rect(center = center, radius = radius - 1.5f))
    }
    clipPath(clip) {
        // the emoji is drawn larger than the circle to fill it;
        // the clip keeps it perfectly round
        val emoji = textMeasurer.measure(
            AnnotatedString(team.f),
            style = TextStyle(fontSize = (radius * 2.2f).sp)
        )
        drawText(
            emoji,
            topLeft = Offset(
                center.x - emoji.size.width / 2f,
                center.y - emoji.size.height / 2f
            ),
            alpha = if (out) 0.35f else 1f
        )
        if (out) drawCircle(Color(0x66000000), radius, center)
    }

    // outer ring
    val ring = when {
        isSelected -> Color(0xFFFFD76E)
        out -> Color(0xFF453C30)
        else -> team.c ?: Color(0xFF6B5C45)
    }
    drawCircle(ring, radius, center, style = Stroke(if (isSelected) 6f else if (out) 2f else 3f))
}

/** Bracket slot without an assigned team: dashed gray circle with a "?". */
private fun DrawScope.drawEmptySlot(textMeasurer: TextMeasurer, center: Offset, radius: Float) {
    drawCircle(Color(0xFF0F0C08), radius, center)
    drawCircle(
        COL_DOT,
        radius,
        center,
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    )
    val q = textMeasurer.measure(
        AnnotatedString("?"),
        style = TextStyle(color = COL_DOT, fontSize = (radius * 0.9f).sp, fontWeight = FontWeight.Bold)
    )
    drawText(q, topLeft = Offset(center.x - q.size.width / 2f, center.y - q.size.height / 2f))
}

/** Stylized golden trophy at the center. */
private fun DrawScope.drawTrophy() {
    val gold = Brush.verticalGradient(
        0f to Color(0xFFFFE9A8), 0.45f to Color(0xFFE3A83F), 1f to Color(0xFF8A5A17),
        startY = CY - 130f, endY = CY + 120f
    )
    fun p(build: Path.() -> Unit) = Path().apply(build)
    val s = 1.15f
    fun x(v: Float) = CX + v * s
    fun y(v: Float) = CY + 10f + v * s

    drawPath(p {
        moveTo(x(-46f), y(96f)); quadraticBezierTo(x(0f), y(110f), x(46f), y(96f))
        lineTo(x(40f), y(66f)); quadraticBezierTo(x(0f), y(76f), x(-40f), y(66f)); close()
    }, gold)
    drawRoundRect(Color(0xFF1F6B3A), Offset(x(-42f), y(62f)), Size(84f * s, 12f * s), cornerRadius = CornerRadius(5f))
    drawPath(p {
        moveTo(x(-20f), y(62f))
        cubicTo(x(-14f), y(34f), x(-30f), y(18f), x(-34f), y(-6f))
        cubicTo(x(-37f), y(-24f), x(-26f), y(-38f), x(-12f), y(-46f))
        lineTo(x(12f), y(-46f))
        cubicTo(x(26f), y(-38f), x(37f), y(-24f), x(34f), y(-6f))
        cubicTo(x(30f), y(18f), x(14f), y(34f), x(20f), y(62f)); close()
    }, gold)
    drawPath(p {
        moveTo(x(-12f), y(-44f))
        cubicTo(x(-34f), y(-56f), x(-46f), y(-74f), x(-40f), y(-92f))
        cubicTo(x(-30f), y(-84f), x(-12f), y(-78f), x(0f), y(-78f))
        cubicTo(x(12f), y(-78f), x(30f), y(-84f), x(40f), y(-92f))
        cubicTo(x(46f), y(-74f), x(34f), y(-56f), x(12f), y(-44f)); close()
    }, gold)
    drawCircle(gold, 26f * s, Offset(x(0f), y(-96f)))
    drawOval(Color(0xD9FFF6D8), Offset(x(-16f), y(-109f)), Size(16f * s, 10f * s))
}
