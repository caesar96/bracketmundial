@file:OptIn(ExperimentalLayoutApi::class)

package com.example.bracketmundial

import android.Manifest
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
 *  MODELO — wins = rondas ganadas (0..5; 5 = campeón), eliminated = cayó
 * ============================================================ */
data class Team(
    val n: String,
    val f: String,                 // bandera emoji
    val c: Color? = null,          // color de su línea si avanzó
    val wins: Int = 0,
    val eliminated: Boolean = false,
    val hora: String? = null,
    val position: Int = -1,        // slot 0..31 que determina su llave en el bracket
)

val INITIAL_TEAMS = listOf(
    Team("Brasil", "🇧🇷", c = Color(0xFFF2C200), wins = 1),
    Team("Japón", "🇯🇵", eliminated = true),
    Team("C. de Marfil", "🇨🇮", eliminated = true),
    Team("Noruega", "🇳🇴", c = Color(0xFFD13A30), wins = 1),
    Team("México", "🇲🇽", c = Color(0xFF1F9E4B), wins = 1),
    Team("Ecuador", "🇪🇨", eliminated = true),
    Team("Inglaterra", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", c = Color(0xFFD13A30), wins = 1),
    Team("RD Congo", "🇨🇩", eliminated = true),
    Team("Argentina", "🇦🇷"),
    Team("Cabo Verde", "🇨🇻"),
    Team("Australia", "🇦🇺"),
    Team("Egipto", "🇪🇬"),
    Team("Suiza", "🇨🇭"),
    Team("Argelia", "🇩🇿"),
    Team("Colombia", "🇨🇴"),
    Team("Ghana", "🇬🇭"),
    Team("Senegal", "🇸🇳", eliminated = true),
    Team("Bélgica", "🇧🇪", c = Color(0xFFF2C200), wins = 1),
    Team("Bosnia", "🇧🇦", eliminated = true),
    Team("EE. UU.", "🇺🇸", c = Color(0xFF3F5FB5), wins = 1),
    Team("Austria", "🇦🇹", eliminated = true),
    Team("España", "🇪🇸", c = Color(0xFFD13A30), wins = 1),
    Team("Croacia", "🇭🇷", hora = "Hoy · 5:00 PM"),
    Team("Portugal", "🇵🇹", hora = "Hoy · 5:00 PM"),
    Team("Marruecos", "🇲🇦", c = Color(0xFF1F9E4B), wins = 1),
    Team("Sudáfrica", "🇿🇦", eliminated = true),
    Team("Países Bajos", "🇳🇱", eliminated = true),
    Team("Canadá", "🇨🇦", c = Color(0xFFD13A30), wins = 1),
    Team("Suecia", "🇸🇪", eliminated = true),
    Team("Francia", "🇫🇷", c = Color(0xFF3555C4), wins = 1),
    Team("Paraguay", "🇵🇾", c = Color(0xFFD13A30), wins = 1),
    Team("Alemania", "🇩🇪", eliminated = true),
).mapIndexed { i, t -> t.copy(position = i) }

/* Paleta de líneas para equipos que avanzan sin color asignado */
val FALLBACK_COLORS = listOf(
    Color(0xFFD13A30), Color(0xFFF2C200), Color(0xFF1F9E4B), Color(0xFF3F5FB5)
)

/* ============================================================
 *  GEOMETRÍA — espacio virtual de 1700 x 1700, como el SVG
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

/** Trazo radial + arco + radial (el "codo" característico del diseño). */
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
        arcTo(oval, a1 - 90f, sweep, forceMoveTo = false)  // 0° Android = las 3 en punto
        lineTo(p4.x, p4.y)
    }
}

/* ============================================================
 *  LÓGICA DE ESTADO — rondas: 0=dieciseisavos 1=octavos 2=cuartos
 *  3=semis 4=final. wins de un equipo == número de la ronda que le
 *  toca jugar a continuación (5 = ya es campeón).
 * ============================================================ */
private val NOMBRES_RONDA = listOf("Dieciseisavos", "Octavos", "Cuartos", "Semis", "Final")

fun nombreRonda(wins: Int) = NOMBRES_RONDA.getOrElse(wins) { "Campeón" }

/** La otra mitad del grupo de [pos] en la ronda [ronda] (grupo = pos / 2^(ronda+1),
 *  partido en dos mitades de 2^ronda posiciones cada una). */
private fun otraMitadDeGrupo(pos: Int, ronda: Int): IntRange {
    val groupSize = 1 shl (ronda + 1)
    val halfSize = 1 shl ronda
    val groupStart = (pos / groupSize) * groupSize
    val ownHalfStart = (pos / halfSize) * halfSize
    val otherHalfStart = if (ownHalfStart == groupStart) groupStart + halfSize else groupStart
    return otherHalfStart until otherHalfStart + halfSize
}

/** Rival potencial de [pos] en su próxima ronda (r = wins): el único
 *  sobreviviente con wins == r en la otra mitad de su grupo de ronda r.
 *  Null si esa mitad aún no tiene sobreviviente. */
fun rivalActual(byPos: Map<Int, Team>, pos: Int): Team? {
    val t = byPos[pos] ?: return null
    val r = t.wins
    if (t.eliminated || r >= 5) return null
    return otraMitadDeGrupo(pos, r)
        .asSequence()
        .mapNotNull { byPos[it] }
        .firstOrNull { it.wins == r && !it.eliminated }
}

/** Rival que [pos] venció en su última ronda jugada (r-1, con r = wins):
 *  el único equipo eliminado con wins == r-1 en la otra mitad de ese grupo.
 *  Null si wins == 0 o si los datos fueron editados a mano y no calzan. */
fun rivalVencido(byPos: Map<Int, Team>, pos: Int): Team? {
    val t = byPos[pos] ?: return null
    val r = t.wins
    if (r < 1) return null
    return otraMitadDeGrupo(pos, r - 1)
        .asSequence()
        .mapNotNull { byPos[it] }
        .firstOrNull { it.wins == r - 1 && it.eliminated }
}

fun estado(byPos: Map<Int, Team>, position: Int): String {
    val t = byPos[position] ?: return "Slot vacío."
    return when {
        t.eliminated -> "Eliminado en ${nombreRonda(t.wins)}."
        t.wins >= 5 -> "¡CAMPEÓN DEL MUNDO!"
        else -> {
            val rival = rivalActual(byPos, position)
            val cuando = t.hora?.let { " ($it)" } ?: ""
            if (rival != null) "${nombreRonda(t.wins)} vs ${rival.f} ${rival.n}$cuando."
            else "${nombreRonda(t.wins)} · rival por definir$cuando."
        }
    }
}

/* ============================================================
 *  PANTALLA
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
                ) { /* si se niega, el worker simplemente no podrá notificar */ }
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
                            onAbrirEquipos = { navController.navigate("equipos") },
                            onAbrirResultadosApi = { navController.navigate("resultadosApi") },
                        )
                    }
                    composable("equipos") {
                        EquiposScreen(onVolver = { navController.popBackStack() })
                    }
                    composable("resultadosApi") {
                        ResultadosApiScreen(onVolver = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

private data class FlagHit(val center: Offset, val radius: Float, val position: Int)

@Composable
fun BracketScreen(
    onAbrirEquipos: () -> Unit,
    onAbrirResultadosApi: () -> Unit,
    vm: BracketViewModel = viewModel(factory = BracketViewModel.factory(LocalContext.current)),
) {
    val teams by vm.teams.collectAsState()
    val byPos = remember(teams) { teams.associateBy { it.position } }
    val isSyncing by vm.isSyncing.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Pan & zoom animables ----
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showUndoConfirm by remember { mutableStateOf(false) }
    var menuExpandido by remember { mutableStateOf(false) }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.limpiarMensajeSync()
        }
    }

    val textMeasurer = rememberTextMeasurer()

    // Zonas tocables: solo slots con equipo asignado, incluidas las banderas
    // avanzadas de cada ronda que ya ganó
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
                    "CAMINO AL TÍTULO",
                    color = Color(0xFFE9DCC0),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    fontSize = 18.sp
                )
                Text(
                    "EN JUEGO · OCTAVOS DE FINAL",
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
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar resultados", tint = Color(0xFF8D7F66))
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuExpandido = true }) {
                        Icon(Icons.Filled.List, contentDescription = "Menú", tint = Color(0xFF8D7F66))
                    }
                    DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                        DropdownMenuItem(
                            text = { Text("Selecciones") },
                            onClick = { menuExpandido = false; onAbrirEquipos() }
                        )
                        DropdownMenuItem(
                            text = { Text("Resultados (API)") },
                            onClick = { menuExpandido = false; onAbrirResultadosApi() }
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Pinch-zoom + arrastre, anclado al punto del gesto
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
                // Tap: seleccionar · Doble tap: zoom animado
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
                                lastTapTime = 0L  // evita que un triple tap cuente doble
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
                                // selección instantánea, sin esperar timeout de doble tap
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
                        // Leer scale/offset AQUÍ (dentro de la lambda) es la clave:
                        // Compose solo re-transforma la capa en GPU, sin redibujar
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
                    drawBracket(textMeasurer, byPos, selected)
                }
            }
        }

        // Panel inferior
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
                val rivalTeam = selTeam?.let { rivalActual(byPos, it.position) }
                val vencidoTeam = selTeam?.let { rivalVencido(byPos, it.position) }

                // Fila 1: info del equipo seleccionado (o instrucciones)
                if (selTeam == null) {
                    Text(
                        "Toca una bandera para ver su estado · pellizca para acercar · doble tap para zoom",
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
                                selTeam.n,
                                color = Color(0xFFE9E2D4),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(estado(byPos, selTeam.position), color = Color(0xFFBDA87E), fontSize = 12.sp)
                        }
                    }
                }

                // Fila 2: acciones — solo las que apliquen, envuelven si no caben
                val hayAcciones = (selTeam != null && !selTeam.eliminated && selTeam.wins < 5 && rivalTeam != null) ||
                    (selTeam != null && !selTeam.eliminated && selTeam.wins >= 1 && vencidoTeam != null)
                if (hayAcciones) Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (selTeam != null && !selTeam.eliminated && selTeam.wins >= 1 && vencidoTeam != null) {
                        TextButton(onClick = { showUndoConfirm = true }) {
                            Text("Deshacer", color = Color(0xFF7D7060), fontSize = 12.sp)
                        }
                    }
                    if (selTeam != null && !selTeam.eliminated && selTeam.wins < 5 && rivalTeam != null) {
                        TextButton(onClick = { showResultDialog = true }) {
                            Text("Registrar resultado", color = COL_GOLD, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text("Reiniciar", color = Color(0xFF7D7060), fontSize = 12.sp)
                    }
                }
            }
        }
    }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showResultDialog) {
        val selTeam = selected?.let { byPos[it] }
        val rivalTeam = selTeam?.let { rivalActual(byPos, it.position) }
        if (selTeam != null && rivalTeam != null) {
            AlertDialog(
                onDismissRequest = { showResultDialog = false },
                containerColor = Color(0xFF211A11),
                title = { Text("¿Quién ganó?", color = Color(0xFFE9E2D4), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(selTeam, rivalTeam).forEach { candidato ->
                            val perdedor = if (candidato.position == selTeam.position) rivalTeam else selTeam
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.registrarGanador(candidato.position, perdedor.position)
                                        selected = candidato.position
                                        showResultDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(candidato.f, fontSize = 28.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    candidato.n,
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
                        Text("Cancelar", color = COL_GOLD)
                    }
                }
            )
        } else {
            showResultDialog = false
        }
    }

    if (showUndoConfirm) {
        val selTeam = selected?.let { byPos[it] }
        val vencidoTeam = selTeam?.let { rivalVencido(byPos, it.position) }
        if (selTeam != null && vencidoTeam != null) {
            AlertDialog(
                onDismissRequest = { showUndoConfirm = false },
                containerColor = Color(0xFF211A11),
                title = { Text("¿Deshacer resultado?", color = Color(0xFFE9E2D4), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "¿Deshacer la victoria de ${selTeam.n} sobre ${vencidoTeam.f} ${vencidoTeam.n}?",
                        color = Color(0xFFBDA87E)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deshacerResultado(selTeam.position)
                        selected = selTeam.position
                        showUndoConfirm = false
                    }) {
                        Text("Confirmar", color = COL_GOLD)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUndoConfirm = false }) {
                        Text("Cancelar", color = Color(0xFF7D7060))
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
            title = { Text("¿Reiniciar el bracket?", color = Color(0xFFE9E2D4), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se restaurarán los 32 equipos y resultados iniciales.",
                    color = Color(0xFFBDA87E)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reiniciar()
                    selected = null
                    showResetConfirm = false
                }) {
                    Text("Reiniciar", color = COL_GOLD)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancelar", color = Color(0xFF7D7060))
                }
            }
        )
    }
}

/** Convierte un toque en pantalla a coordenadas del tablero y busca la bandera. */
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
 *  DIBUJO DEL TABLERO
 * ============================================================ */
private fun DrawScope.drawBracket(
    textMeasurer: TextMeasurer,
    byPos: Map<Int, Team>,
    selected: Int?,
) {
    fun stroke(w: Float, dash: Boolean = false) = Stroke(
        width = w,
        cap = StrokeCap.Round,
        pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(2f, 18f)) else null
    )
    fun dotAt(a: Float, r: Float, size: Float, color: Color = COL_DOT) =
        drawCircle(color, size, pt(a, r))

    /* Rondas por venir: octavos → cuartos → semis → final.
     * Cada tramo se enciende en dorado si algún equipo ya lo recorrió
     * (wins >= ronda de destino) — el resto se ve como recorrido futuro. */
    fun avanceEn(nivel: Int, k: Int) = byPos.values.firstOrNull { it.wins >= nivel && it.position / (1 shl nivel) == k }

    for (k in 0 until 8) {
        val path = elbowPath(angSect(k), angQF(k / 2), R_N16, (R_N16 + R_QF) / 2 + 18f, R_QF)
        val advancer = avanceEn(2, k)
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
        val advancer = avanceEn(3, k)
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
        val advancer = avanceEn(4, k)
        if (advancer != null) {
            drawLine(COL_GOLD_SOFT, start, end, strokeWidth = if (selected == advancer.position) 8f else 5f, cap = StrokeCap.Round)
            drawLine(COL_GOLD, start, end, strokeWidth = 2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 18f)))
        } else {
            drawLine(COL_LINE, start, end, strokeWidth = 3.5f, cap = StrokeCap.Round)
        }
        dotAt(angSF(k), R_SF, 7f)
    }

    /* Llaves de dieciseisavos */
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

    /* Resplandor y trofeo central */
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xD9F6C05A), 0.35f to Color(0x59C98A2E), 0.7f to Color.Transparent,
            center = Offset(CX, CY), radius = 260f
        ),
        radius = 260f, center = Offset(CX, CY)
    )
    drawTrophy()

    /* Banderas y etiquetas */
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
            AnnotatedString(t.n.uppercase()),
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
    // fondo del círculo
    drawCircle(Color(0xFF0F0C08), radius, center)

    // recorte circular (equivalente a border-radius + overflow:hidden)
    val clip = Path().apply {
        addOval(Rect(center = center, radius = radius - 1.5f))
    }
    clipPath(clip) {
        // el emoji se dibuja más grande que el círculo para llenarlo;
        // el clip lo deja perfectamente redondo
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

    // anillo exterior
    val ring = when {
        isSelected -> Color(0xFFFFD76E)
        out -> Color(0xFF453C30)
        else -> team.c ?: Color(0xFF6B5C45)
    }
    drawCircle(ring, radius, center, style = Stroke(if (isSelected) 6f else if (out) 2f else 3f))
}

/** Slot de bracket sin equipo asignado: círculo punteado gris con "?". */
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

/** Trofeo dorado estilizado en el centro. */
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
