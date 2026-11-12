package com.shieldstream.tv.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.R
import com.shieldstream.tv.ui.component.ProfileAvatar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val BambiRed = Color(0xFFE50914)
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Accelerate = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)

/**
 * Entrada de Bambi al abrir la app (solo en frío). Mientras se ve, [onStartPreload] prepara el Inicio y sus carátulas.
 *
 * 1. El gato aparece con un pequeño zoom sobre un halo rojo, y un destello lo recorre.
 * 2. Debajo se forma "Bambi".
 * 3. Se espera a que el Inicio esté listo (como mínimo [minMs], como máximo [maxMs]); si tarda, aparece una línea fina.
 * 4. Salida: la cámara "atraviesa" el logo entre haces de luz roja y se funde a negro.
 *
 * OK o Atrás la saltan.
 */
@Composable
fun IntroScreen(
    onStartPreload: () -> Unit,
    awaitReady: suspend (timeoutMs: Long) -> Boolean,
    onFinished: () -> Unit,
    quick: Boolean = false,
) {
    val minMs = if (quick) 900L else 2_100L
    val maxMs = if (quick) 1_500L else 4_500L

    val logoIn = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val wordIn = remember { Animatable(0f) }
    val waiting = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val proceed = remember { CompletableDeferred<Unit>() }
    var exiting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val startedAt = remember { System.currentTimeMillis() }

    fun skip() {
        // Un margen para que la pulsación que abrió la app no la salte de golpe.
        if (System.currentTimeMillis() - startedAt > 350) proceed.complete(Unit)
    }
    DisposableEffect(Unit) {
        IntroKeyGate.onKey = { skip() }
        onDispose { IntroKeyGate.onKey = null }
    }

    LaunchedEffect(Unit) {
        onStartPreload()
        // Entrada
        launch { delay(120); logoIn.animateTo(1f, tween(900, easing = Emphasized)) }
        launch { delay(480); shine.animateTo(1f, tween(1_050, easing = FastOutSlowInEasing)) }
        launch { delay(720); wordIn.animateTo(1f, tween(700, easing = Emphasized)) }
        // Espera: mínimo para que se vea la animación; máximo para no retener a nadie.
        launch {
            val loaded = async { awaitReady(maxMs) }
            delay(minMs)
            val stillLoading = launch {
                delay(max(0L, 2_600L - minMs))
                waiting.animateTo(1f, tween(400))
            }
            loaded.await()
            stillLoading.cancel()
            proceed.complete(Unit)
        }
    }
    LaunchedEffect(Unit) {
        proceed.await()
        exiting = true
        listOf(
            async { waiting.animateTo(0f, tween(150)) },
            async { exit.animateTo(1f, tween(if (quick) 450 else 700, easing = Accelerate)) },
        ).awaitAll()
        onFinished()
    }

    val breathing = rememberInfiniteTransition(label = "breathing")
    val pulse by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val loaderSpin by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "loader",
    )
    val streaks = remember { Streak.make(Random(7)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val e = exit.value
        // Halo rojo detrás del logo: crece al entrar, respira mientras espera y estalla al salir.
        Canvas(Modifier.fillMaxSize()) {
            val breath = 1f + 0.06f * sin(pulse * 2f * PI.toFloat())
            val radius = size.minDimension * (0.18f + 0.30f * logoIn.value) * breath * (1f + 2.2f * e)
            val alpha = (0.42f * logoIn.value) * (1f - e)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to BambiRed.copy(alpha = alpha),
                    0.45f to BambiRed.copy(alpha = alpha * 0.35f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            // Haces de luz que salen disparados hacia los lados al "atravesar" el logo.
            if (e > 0f) {
                streaks.forEach { s -> s.draw(this, e) }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val l = logoIn.value
            Box(
                Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        val inScale = 0.82f + 0.18f * l
                        val outScale = 1f + 5.5f * e * e
                        scaleX = inScale * outScale
                        scaleY = inScale * outScale
                        alpha = l * (1f - ((e - 0.35f) / 0.65f).coerceIn(0f, 1f))
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .clip(RoundedCornerShape(36.dp))
                    .drawWithContent {
                        drawContent()
                        // Destello diagonal que recorre el logo una vez.
                        val p = shine.value
                        if (p in 0.001f..0.999f) {
                            val w = size.width
                            val x = -w + p * 3f * w
                            drawRect(
                                brush = Brush.linearGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.White.copy(alpha = 0.55f),
                                    1f to Color.Transparent,
                                    start = Offset(x, 0f),
                                    end = Offset(x + w * 0.6f, size.height),
                                ),
                                blendMode = BlendMode.Plus,
                            )
                        }
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.bambi_logo),
                    contentDescription = "Bambi",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(26.dp))
            val w = wordIn.value
            Image(
                painter = painterResource(R.drawable.bambi_wordmark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(46.dp)
                    .graphicsLayer {
                        alpha = w * (1f - (e * 4f).coerceIn(0f, 1f))
                        translationY = (1f - w) * 14.dp.toPx()
                        val sc = 0.94f + 0.06f * w
                        scaleX = sc
                        scaleY = sc
                    },
            )
            Spacer(Modifier.height(30.dp))
            // Solo si tarda: línea fina que barre de lado a lado.
            Canvas(
                Modifier
                    .width(140.dp)
                    .height(3.dp)
                    .graphicsLayer { alpha = waiting.value * (1f - e) },
            ) {
                drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                val seg = size.width * 0.35f
                val x = -seg + (size.width + seg) * loaderSpin
                drawRoundRect(
                    BambiRed,
                    topLeft = Offset(x.coerceAtLeast(0f), 0f),
                    size = Size((seg - max(0f, -x)).coerceAtMost(size.width - x.coerceAtLeast(0f)).coerceAtLeast(0f), size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
            }
        }

        // Fundido final a negro.
        if (exiting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((e - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                    .background(Color.Black),
            )
        }
    }
}

/** Un haz de luz vertical de la salida. */
private class Streak(
    private val side: Float,
    private val distance: Float,
    private val width: Float,
    private val color: Color,
    private val delay: Float,
) {
    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, exit: Float) = with(scope) {
        val t = ((exit - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (t <= 0f) return@with
        val eased = t * t
        val x = center.x + side * eased * distance * size.width
        val w = width * size.width * (0.4f + 1.6f * t)
        val alpha = (1f - t) * 0.9f
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to color.copy(alpha = alpha),
                1f to Color.Transparent,
            ),
            topLeft = Offset(x - w / 2f, 0f),
            size = Size(w, size.height),
        )
    }

    companion object {
        private val palette = listOf(
            Color(0xFFE50914), Color(0xFFB20710), Color(0xFFFF3D47), Color(0xFF8C0A12), Color(0xFFFF7A82), Color(0xFF5E060C),
        )

        fun make(random: Random): List<Streak> = List(34) { i ->
            Streak(
                side = if (i % 2 == 0) -1f else 1f,
                distance = 0.05f + random.nextFloat() * 0.6f,
                width = 0.004f + random.nextFloat() * 0.03f,
                color = palette[random.nextInt(palette.size)],
                delay = random.nextFloat() * 0.35f,
            )
        }
    }
}

/**
 * Tras elegir perfil, si el Inicio aún no está listo: el avatar en el centro con un anillo rojo girando, y al terminar
 * se funde con el Inicio. Si ya estaba listo, esta pantalla ni se ve.
 */
@Composable
fun ProfileLoadingScreen(
    initial: String,
    avatar: Int,
    awaitReady: suspend (timeoutMs: Long) -> Boolean,
    onReady: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, tween(420, easing = Emphasized)) }
        val started = System.currentTimeMillis()
        awaitReady(10_000)
        // Que no parpadee si se resuelve al momento.
        val shown = System.currentTimeMillis() - started
        if (shown < 600) delay(600 - shown)
        leave.animateTo(1f, tween(320, easing = Accelerate))
        onReady()
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing)),
        label = "angle",
    )
    val sweep by spin.animateFloat(
        initialValue = 40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 1_400
                40f at 0
                250f at 700 using FastOutSlowInEasing
                40f at 1_400 using FastOutSlowInEasing
            },
        ),
        label = "sweep",
    )
    BackHandler { /* se espera a que cargue */ }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                val a = appear.value
                val l = leave.value
                alpha = a * (1f - l)
                val sc = (0.86f + 0.14f * a) * (1f + 0.12f * l)
                scaleX = sc
                scaleY = sc
            },
        ) {
            Canvas(Modifier.size(232.dp)) {
                val stroke = 4.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.08f), style = Stroke(stroke))
                drawArc(
                    color = BambiRed,
                    startAngle = angle - 90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            ProfileAvatar(initial = initial, avatar = avatar, size = 140.dp, shape = RoundedCornerShape(12.dp))
        }
    }
}
