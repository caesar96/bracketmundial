package com.example.bracketmundial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/* ============================================================
 *  MODELO — s = 'W' ganó su llave, 'L' eliminado, 'P' por jugar
 * ============================================================ */
data class Team(
    val n: String,
    val f: String,                 // bandera emoji
    val c: Color? = null,          // color de su línea si avanzó
    val s: Char,
    val hora: String? = null,
)

val INITIAL_TEAMS = listOf(
    Team("Brasil", "🇧🇷", Color(0xFFF2C200), 'W'),
    Team("Japón", "🇯🇵", s = 'L'),
    Team("C. de Marfil", "🇨🇮", s = 'L'),
    Team("Noruega", "🇳🇴", Color(0xFFD13A30), 'W'),
    Team("México", "🇲🇽", Color(0xFF1F9E4B), 'W'),
    Team("Ecuador", "🇪🇨", s = 'L'),
    Team("Inglaterra", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", Color(0xFFD13A30), 'W'),
    Team("RD Congo", "🇨🇩", s = 'L'),
    Team("Argentina", "🇦🇷", s = 'P'),
    Team("Cabo Verde", "🇨🇻", s = 'P'),
    Team("Australia", "🇦🇺", s = 'P'),
    Team("Egipto", "🇪🇬", s = 'P'),
    Team("Suiza", "🇨🇭", s = 'P'),
    Team("Argelia", "🇩🇿", s = 'P'),
    Team("Colombia", "🇨🇴", s = 'P'),
    Team("Ghana", "🇬🇭", s = 'P'),
    Team("Senegal", "🇸🇳", s = 'L'),
    Team("Bélgica", "🇧🇪", Color(0xFFF2C200), 'W'),
    Team("Bosnia", "🇧🇦", s = 'L'),
    Team("EE. UU.", "🇺🇸", Color(0xFF3F5FB5), 'W'),
    Team("Austria", "🇦🇹", s = 'L'),
    Team("España", "🇪🇸", Color(0xFFD13A30), 'W'),
    Team("Croacia", "🇭🇷", s = 'P', hora = "Hoy · 5:00 PM"),
    Team("Portugal", "🇵🇹", s = 'P', hora = "Hoy · 5:00 PM"),
    Team("Marruecos", "🇲🇦", Color(0xFF1F9E4B), 'W'),
    Team("Sudáfrica", "🇿🇦", s = 'L'),
    Team("Países Bajos", "🇳🇱", s = 'L'),
    Team("Canadá", "🇨🇦", Color(0xFFD13A30), 'W'),
    Team("Suecia", "🇸🇪", s = 'L'),
    Team("Francia", "🇫🇷", Color(0xFF3555C4), 'W'),
    Team("Paraguay", "🇵🇾", Color(0xFFD13A30), 'W'),
    Team("Alemania", "🇩🇪", s = 'L'),
)

/* Paleta de líneas para equipos que avanzan sin color asignado */
val FALLBACK_COLORS = listOf(
    Color(0xFFD13A30), Color(0xFFF2C200), Color(0xFF1F9E4B), Color(0xFF3F5FB5)
)

/* ============================================================
 *  VIEWMODEL — los resultados viven aquí; la UI solo los pinta.
 *  registrarGanador(i): marca al equipo i como ganador de su
 *  llave y elimina a su rival, sin recompilar nada.
 * ============================================================ */
class BracketViewModel : ViewModel() {
    private val _teams = MutableStateFlow(INITIAL_TEAMS)
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    fun registrarGanador(index: Int) {
        val current = _teams.value
        if (current[index].s != 'P') return          // solo llaves pendientes
        val rival = if (index % 2 == 0) index + 1 else index - 1
        _teams.value = current.mapIndexed { i, t ->
            when (i) {
                index -> t.copy(
                    s = 'W',
                    c = t.c ?: FALLBACK_COLORS[index % FALLBACK_COLORS.size],
                    hora = null
                )
                rival -> t.copy(s = 'L', hora = null)
                else -> t
            }
        }
    }

    fun reiniciar() { _teams.value = INITIAL_TEAMS }
}

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
 *  LÓGICA DE ESTADO
 * ============================================================ */
fun rivalDeOctavos(teams: List<Team>, i: Int): Team? {
    val k = i / 2
    val otherPair = (k / 2) * 2 + (1 - k % 2)
    val a = teams[otherPair * 2]
    val b = teams[otherPair * 2 + 1]
    return if (a.s == 'W') a else if (b.s == 'W') b else null
}

fun estado(teams: List<Team>, i: Int): String {
    val t = teams[i]
    return when (t.s) {
        'L' -> "Eliminado en dieciseisavos de final."
        'P' -> {
            val rival = teams[if (i % 2 == 0) i + 1 else i - 1]
            val cuando = t.hora?.let { " ($it)" } ?: ""
            "Por jugar vs ${rival.f} ${rival.n}$cuando. Mantén presionada su bandera para marcarlo ganador."
        }
        else -> rivalDeOctavos(teams, i)?.let { "Clasificado · Octavos vs ${it.f} ${it.n}." }
            ?: "Clasificado a octavos · rival por definir."
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
        setContent { MaterialTheme { BracketScreen() } }
    }
}

private data class FlagHit(val center: Offset, val radius: Float, val teamIndex: Int)

@Composable
fun BracketScreen(vm: BracketViewModel = viewModel()) {
    val teams by vm.teams.collectAsState()

    // ---- Pan & zoom animables ----
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(null) }

    val textMeasurer = rememberTextMeasurer()

    // Zonas tocables: dependen de qué equipos han avanzado
    val hits = remember(teams) {
        buildList {
            teams.forEachIndexed { i, t ->
                add(FlagHit(pt(angTeam(i), R_TEAM), 42f, i))
                if (t.s == 'W') add(FlagHit(pt(angPair(i / 2), R_N16 + 62f), 40f, i))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(COL_BG).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                // Tap: seleccionar · Long press: marcar ganador · Doble tap: zoom animado
                .pointerInput(hits) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            scope.launch {
                                if (scale.value > 1.05f) {
                                    // volver a la vista completa, con animación
                                    launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    launch { offset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow)) }
                                } else {
                                    // acercar 2.5x hacia donde se tocó
                                    val target = 2.5f
                                    val newOffset =
                                        (offset.value - tap) * (target / scale.value) + tap
                                    launch { scale.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    launch { offset.animateTo(newOffset, spring(stiffness = Spring.StiffnessMediumLow)) }
                                }
                            }
                        },
                        onTap = { tap ->
                            val idx = hitTest(tap, size.width, size.height, scale.value, offset.value, hits)
                            selected = if (idx == selected) null else idx
                        },
                        onLongPress = { tap ->
                            val idx = hitTest(tap, size.width, size.height, scale.value, offset.value, hits)
                            if (idx != null && teams[idx].s == 'P') {
                                vm.registrarGanador(idx)
                                selected = idx
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
                    drawBracket(textMeasurer, teams, selected)
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
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sel = selected
                if (sel == null) {
                    Text(
                        "Toca una bandera para ver su estado · pellizca para acercar · doble tap para zoom",
                        color = Color(0xFF7D7060),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(teams[sel].f, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            teams[sel].n,
                            color = Color(0xFFE9E2D4),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(estado(teams, sel), color = Color(0xFFBDA87E), fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { vm.reiniciar(); selected = null }) {
                    Text("Reiniciar", color = COL_GOLD, fontSize = 12.sp)
                }
            }
        }
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
    }?.teamIndex
}

/* ============================================================
 *  DIBUJO DEL TABLERO
 * ============================================================ */
private fun DrawScope.drawBracket(
    textMeasurer: TextMeasurer,
    teams: List<Team>,
    selected: Int?,
) {
    fun stroke(w: Float, dash: Boolean = false) = Stroke(
        width = w,
        cap = StrokeCap.Round,
        pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(2f, 18f)) else null
    )
    fun dotAt(a: Float, r: Float, size: Float, color: Color = COL_DOT) =
        drawCircle(color, size, pt(a, r))

    /* Rondas por venir: octavos → cuartos → semis → final */
    for (k in 0 until 8) {
        drawPath(elbowPath(angSect(k), angQF(k / 2), R_N16, (R_N16 + R_QF) / 2 + 18f, R_QF), COL_LINE, style = stroke(3.5f))
        dotAt(angSect(k), R_N16, 7f)
    }
    for (k in 0 until 4) {
        drawPath(elbowPath(angQF(k), angSF(k / 2), R_QF, (R_QF + R_SF) / 2 + 12f, R_SF), COL_LINE, style = stroke(3.5f))
        dotAt(angQF(k), R_QF, 7f)
    }
    for (k in 0 until 2) {
        drawLine(COL_LINE, pt(angSF(k), R_SF), pt(angSF(k), 78f), strokeWidth = 3.5f, cap = StrokeCap.Round)
        dotAt(angSF(k), R_SF, 7f)
    }

    /* Llaves de dieciseisavos */
    for (k in 0 until 16) {
        val iA = 2 * k; val iB = 2 * k + 1
        val a = teams[iA]; val b = teams[iB]
        val decided = a.s != 'P'
        for ((i, t) in listOf(iA to a, iB to b)) {
            val won = t.s == 'W'
            val color = when {
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

        val winner = if (a.s == 'W') iA else if (b.s == 'W') iB else null
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
    teams.forEachIndexed { i, t ->
        val out = t.s == 'L'
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

        if (t.s == 'W') {
            drawFlag(textMeasurer, pt(angPair(i / 2), R_N16 + 62f), 40f, t, false, selected == i)
        }
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
