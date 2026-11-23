// Entrada animada de Bambi: la misma de la tele (app/intro/IntroScreen.kt), con las piezas como recursos.
package com.shieldstream.tv.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val BambiRed = Color(0xFFE50914)
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Accelerate = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)

// Fondo del logo: rojo intenso en el centro que se oscurece hacia los bordes.
private val RedCore = Color(0xFFDD0302)
private val RedMid = Color(0xFFB30201)
private val RedEdge = Color(0xFF7A0100)

// Todas las posiciones están en unidades del logo original (1254 × 1254): cada pieza se dibuja donde estaba.
private val BodyBox = Rect(310f, 238f, 978f, 764f)
private val TailBox = Rect(837f, 300f, 978f, 532f)
private val TailPivot = Offset(950f, 515f)
private val BodyCenter = Offset(644f, 500f)
private val RockPivot = Offset(640f, 755f)

private val EyeCenter = Offset(517f, 646f)
private const val EYE_RADIUS = 48f
private val Fur = Color(0xFF2C2B2B)
private val FurInk = Color(0xFF111111)

// Tira de película de la salida: base casi negra con perforaciones claras (la luz del proyector pasa por ellas).
private val StripBase = Color(0xFF151518)
private val StripHole = Color(0xFFD9DDE2)
private val Ground = Offset(640f, 765f)
private val LetterBoxes = listOf(
    Rect(320f, 838f, 428f, 1099f),
    Rect(452f, 840f, 572f, 1090f),
    Rect(599f, 840f, 738f, 1089f),
    Rect(770f, 840f, 874f, 1095f),
    Rect(902f, 840f, 944f, 1101f),
)
private val PopBoxes = listOf(
    Rect(242f, 620f, 324f, 707f),
    Rect(273f, 734f, 353f, 812f),
    Rect(581f, 747f, 667f, 826f),
    Rect(717f, 756f, 801f, 831f),
    Rect(844f, 734f, 925f, 813f),
    Rect(914f, 648f, 1015f, 742f),
)

/** En qué orden saltan las palomitas (las del centro primero). */
private val PopOrder = intArrayOf(4, 1, 0, 3, 2, 5)
private val CompositionCenter = Offset(628f, 670f)
private const val COMPOSITION_HEIGHT = 865f

/**
 * Entrada de Bambi al abrir la app (solo en frío). Mientras se ve, [onStartPreload] prepara el Inicio y sus carátulas.
 *
 * 1. Sobre el rojo del logo crece una sombra en el suelo y el gatito cae de espaldas desde arriba: se aplasta,
 *    rebota y da un latigazo con la cola.
 * 2. Del golpe saltan las palomitas, el gato se balancea jugando con la bobina y lanza la cinta, que se desenrolla
 *    ondeando hacia la derecha.
 * 3. Caen las letras de BAMBI una a una. Mientras se espera (como mínimo [minMs], como máximo [maxMs]) el gato
 *    mueve la cola, parpadea, la cinta ondea y, si tarda, aparece una línea fina abajo.
 * 4. Salida: la pantalla se convierte en un fotograma de una tira de película (se aleja y aparecen las perforaciones),
 *    la película avanza y el fotograma siguiente es la app, que ya estaba debajo; al encajar vuelve a ocupar toda la
 *    pantalla. [onReveal] le pasa al anfitrión el avance (0–1) para que coloque su pantalla con [IntroReveal].
 *
 * [holdStart]: mientras sea true la animación espera quieta en el rojo (el anfitrión aún está montando lo de debajo),
 * para que ese trabajo no le robe fotogramas al gato.
 *
 * OK o Atrás la saltan.
 */
@Composable
fun IntroScreen(
    onStartPreload: () -> Unit,
    awaitReady: suspend (timeoutMs: Long) -> Boolean,
    onFinished: () -> Unit,
    quick: Boolean = false,
    onReveal: (Float) -> Unit = {},
    fadeFromBlack: Boolean = true,
    holdStart: Boolean = false,
    onAnimationStart: () -> Unit = {},
) {
    val minMs = if (quick) 1_100L else 2_600L
    val maxMs = if (quick) 1_700L else 4_800L
    val speed = if (quick) 2.2f else 1f

    var clock by remember { mutableFloatStateOf(0f) }
    val waiting = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val started = remember { CompletableDeferred<Unit>() }
    val proceed = remember { CompletableDeferred<Unit>() }
    val startedAt = remember { System.currentTimeMillis() }
    val hold by rememberUpdatedState(holdStart)

    // Las piezas se decodifican fuera del hilo principal: el primer fotograma (el rojo) sale sin esperar a nada.
    val images by produceState<IntroImages?>(null) {
        value = withContext(Dispatchers.IO) { IntroImages.load().also { warmUp() } }
    }

    fun skip() {
        // Un margen para que la pulsación que abrió la app no la salte de golpe.
        if (System.currentTimeMillis() - startedAt > 350) proceed.complete(Unit)
    }
    DisposableEffect(Unit) {
        IntroKeyGate.onKey = { skip() }
        onDispose { IntroKeyGate.onKey = null }
    }

    // Reloj de la animación (ms): todo lo que se dibuja sale de él, así que solo se repinta, no se recompone.
    // Arranca cuando están las piezas y el anfitrión ha terminado; un fotograma lento no hace saltar la animación
    // (como mucho avanza 50 ms), solo la frena un instante.
    // Además espera a que el hilo principal esté tranquilo (unos fotogramas seguidos a tiempo), porque justo tras
    // montarse la pantalla de debajo aún queda trabajo pendiente; como mucho 1,5 s.
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        var calm = 0
        var readySince = -1L
        while (true) {
            withFrameMillis { now ->
                val raw = now - last
                val dt = raw.coerceIn(0L, 50L)
                last = now
                if (images != null && !hold) {
                    if (!started.isCompleted) {
                        if (readySince < 0) readySince = now
                        calm = if (raw <= 22L) calm + 1 else 0
                        if (calm >= CALM_FRAMES || now - readySince > 1_500L) {
                            started.complete(Unit)
                            onAnimationStart()
                        }
                    } else {
                        clock += dt * speed
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        onReveal(0f)
        onStartPreload()
        // Espera: mínimo para que se vea la animación; máximo para no retener a nadie (contados desde que arranca).
        launch {
            val loaded = async { awaitReady(maxMs + 2_000L) }
            started.await()
            delay(minMs)
            val stillLoading = launch {
                delay(max(0L, 3_000L - minMs))
                waiting.animateTo(1f, tween(400))
            }
            withTimeoutOrNull(maxMs - minMs) { loaded.await() }
            stillLoading.cancel()
            proceed.complete(Unit)
        }
    }
    LaunchedEffect(Unit) {
        proceed.await()
        listOf(
            async { waiting.animateTo(0f, tween(150)) },
            async {
                exit.animateTo(1f, tween(if (quick) 750 else 1_250, easing = LinearEasing)) { onReveal(value) }
            },
        ).awaitAll()
        onReveal(1f)
        onFinished()
    }

    val ribbon = remember { FilmRibbon() }
    val strip = remember { Path() }
    Canvas(
        Modifier
            .fillMaxSize()
    ) {
        drawIntro(clock, exit.value, waiting.value, images, ribbon, fadeFromBlack, strip)
    }
}

/**
 * Ensaya el dibujo completo (todas las fases, salida incluida) en un lienzo diminuto fuera del hilo principal: así las
 * clases y el código de dibujo ya están cargados cuando empieza la animación de verdad, y el primer fotograma del gato
 * no da un tirón. Usa imágenes de 1×1 normales (las de verdad son "hardware" y no se pueden pintar aquí).
 */
private fun warmUp() {
    runCatching {
        val dot = ImageBitmap(1, 1)
        val fake = IntroImages(dot, dot, List(5) { dot }, List(6) { dot })
        val target = ImageBitmap(96, 54)
        val ribbon = FilmRibbon()
        val strip = Path()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(target), Size(96f, 54f)) {
            for (t in floatArrayOf(100f, 400f, 700f, 1_200f, 1_800f, 2_600f, 4_000f)) {
                drawIntro(t, 0f, 0.5f, fake, ribbon, false, strip)
            }
            for (e in floatArrayOf(0.1f, 0.5f, 0.9f)) drawIntro(4_000f, e, 0f, fake, ribbon, false, strip)
        }
    }
}

/**
 * La salida "de película", de 0 a 1: la pantalla se aleja hasta ser un fotograma, la tira avanza al siguiente (la app)
 * y este vuelve a acercarse hasta ocupar la pantalla. La usan la intro (para dibujar) y su anfitrión (para colocar la
 * app justo en el hueco del fotograma siguiente).
 */
object IntroReveal {
    /** Separación entre fotogramas, en fracción de la altura de un fotograma. */
    private const val GAP = 0.07f

    /** Escala de los fotogramas (1 = pantalla completa). */
    fun scale(p: Float): Float {
        val away = easeInOutCubic(segment(p, 0f, 0.30f))
        val back = easeInOutCubic(segment(p, 0.72f, 1f))
        return 1f - 0.2f * away + 0.2f * back
    }

    /** Avance de la tira (0 = la intro en el centro, 1 = la app en el centro). */
    fun advance(p: Float): Float = easeInOutCubic(segment(p, 0.20f, 0.78f))

    /** Distancia entre centros de fotogramas, en fracción de la altura de la pantalla. */
    fun pitch(p: Float): Float = scale(p) * (1f + GAP)

    /** Desplazamiento vertical (fracción de la altura) del fotograma de la app respecto al centro. */
    fun appOffset(p: Float): Float = pitch(p) * (1f - advance(p))
}

/** Fotogramas seguidos "a tiempo" que hacen falta para dar por tranquilo el hilo principal. */
private const val CALM_FRAMES = 8

private class IntroImages(
    val body: ImageBitmap,
    val tail: ImageBitmap,
    val letters: List<ImageBitmap>,
    val pops: List<ImageBitmap>,
) {
    companion object {
        /** Las piezas del logo, las mismas PNG que en la tele (recursos de core/ui). */
        fun load(): IntroImages {
            fun img(name: String) = checkNotNull(BundledImages.get("$name.png")) { "Falta $name.png" }
            return IntroImages(
                body = img("intro_body"),
                tail = img("intro_tail"),
                letters = listOf("intro_b1", "intro_a", "intro_m", "intro_b2", "intro_i").map(::img),
                pops = listOf("intro_pop1", "intro_pop2", "intro_pop3", "intro_pop4", "intro_pop5", "intro_pop6").map(::img),
            )
        }
    }
}

private fun DrawScope.drawIntro(
    t: Float,
    exit: Float,
    waiting: Float,
    images: IntroImages?,
    ribbon: FilmRibbon,
    fadeFromBlack: Boolean,
    strip: Path,
) {
    if (exit <= 0f) {
        drawScene(t, waiting, images, ribbon, fadeFromBlack)
        return
    }
    // Salida: la pantalla es un fotograma de una tira vertical que avanza hasta el siguiente, la app.
    val s = IntroReveal.scale(exit)
    val pitch = IntroReveal.pitch(exit) * size.height
    val offset = -pitch * IntroReveal.advance(exit)
    val frame = Size(size.width * s, size.height * s)
    val corner = CornerRadius(size.width * 0.018f * s)
    val left = (size.width - frame.width) / 2f
    val introTop = center.y - frame.height / 2f + offset

    // Fotograma 0: la intro, que sigue moviéndose, a escala (recorte rectangular, barato).
    if (introTop + frame.height > 0f) {
        clipRect(left, introTop, left + frame.width, introTop + frame.height) {
            withTransform({
                translate(0f, offset)
                scale(s, s, center)
            }) {
                drawScene(t, waiting, images, ribbon, fadeFromBlack)
            }
        }
    }
    // La tira: toda la pantalla menos los dos fotogramas (con esquinas redondeadas). El de la app queda vacío y por
    // ahí se ve la pantalla de debajo, que el anfitrión coloca justo en ese sitio. Sin capas ni modos de mezcla.
    strip.reset()
    strip.fillType = PathFillType.EvenOdd
    strip.addRect(Rect(Offset.Zero, size))
    strip.addRoundRect(RoundRect(Rect(Offset(left, introTop), frame), corner))
    strip.addRoundRect(RoundRect(Rect(Offset(left, introTop + pitch), frame), corner))
    drawPath(strip, StripBase)
    // Mientras la película corre se oscurece un poco, como con el obturador del proyector.
    val shutter = 0.22f * sin(PI.toFloat() * segment(exit, 0.20f, 0.78f))
    if (shutter > 0.005f) drawRect(Color.Black.copy(alpha = shutter))
    // Perforaciones en los márgenes: cuatro por fotograma y se mueven con la tira.
    if (left > 2f) {
        val step = pitch / 4f
        val holeW = left * 0.30f
        val holeH = step * 0.46f
        val holeCorner = CornerRadius(holeW * 0.22f)
        val firstY = introTop - pitch + step / 2f
        val skip = floor(-firstY / step).toInt().coerceAtLeast(0)
        for (side in 0..1) {
            val cx = if (side == 0) left / 2f else size.width - left / 2f
            var y = firstY + skip * step
            while (y - holeH < size.height) {
                drawRoundRect(StripHole, Offset(cx - holeW / 2f, y - holeH / 2f), Size(holeW, holeH), holeCorner)
                y += step
            }
        }
    }
}

/** La escena de la intro a pantalla completa: fondo, gato, cinta, palomitas, letras y (si tarda) la línea de espera. */
private fun DrawScope.drawScene(
    t: Float,
    waiting: Float,
    images: IntroImages?,
    ribbon: FilmRibbon,
    fadeFromBlack: Boolean,
) {
    // Fondo: el rojo del logo. En frío ya está puesto desde que se abre la app (fondo de la ventana), así que sale
    // de golpe y solo se suma la viñeta; si no, aparece desde negro.
    drawRect(Color.Black)
    val bgAlpha = if (fadeFromBlack) easeOutCubic(segment(t, 0f, 520f)) else 1f
    val vignette = if (fadeFromBlack) bgAlpha else easeOutCubic(segment(t, 0f, 700f))
    if (bgAlpha > 0f) {
        drawRect(
            Brush.radialGradient(
                0f to RedCore,
                0.5f to RedMid,
                1f to RedEdge,
                center = Offset(size.width / 2f, size.height * 0.46f),
                radius = size.maxDimension * 0.62f,
            ),
            alpha = bgAlpha,
        )
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.5f),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
            alpha = vignette,
        )
    }
    if (images == null) return

    val unit = size.height * 0.82f / COMPOSITION_HEIGHT
    withTransform({
        translate(center.x - CompositionCenter.x * unit, center.y - CompositionCenter.y * unit)
        scale(unit, unit, Offset.Zero)
    }) {
        drawCat(t, CatPose.at(t), images, ribbon)
        drawLetters(t, images)
    }

    // Solo si tarda: línea fina que barre de lado a lado.
    if (waiting > 0f) {
        val w = 140.dp.toPx()
        val h = 3.dp.toPx()
        val left = (size.width - w) / 2f
        val top = size.height * 0.955f
        drawRoundRect(Color.White.copy(alpha = 0.18f * waiting), Offset(left, top), Size(w, h), CornerRadius(h, h))
        val seg = w * 0.35f
        val x = -seg + (w + seg) * ((t % 1_100f) / 1_100f)
        val from = x.coerceAtLeast(0f)
        val to = (x + seg).coerceAtMost(w)
        if (to > from) {
            drawRoundRect(Color.White.copy(alpha = waiting), Offset(left + from, top), Size(to - from, h), CornerRadius(h, h))
        }
    }
}

/** Cómo está el gato en cada momento: cae de espaldas, se aplasta, rebota y luego se mece jugando. */
private class CatPose(
    val dx: Float,
    val dy: Float,
    val tilt: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rock: Float,
    val breath: Float,
    val visible: Boolean,
    val shadow: Float,
    val landedAt: Float,
) {
    companion object {
        private const val FALL_FROM = 60f
        const val LAND = 460f

        fun at(t: Float): CatPose {
            // 1. Su sombra crece en el suelo y el gato cae desde arriba, algo girado y estirado por la velocidad.
            val fall = segment(t, FALL_FROM, LAND)
            val falling = fall.pow(3)
            val stretch = if (t < LAND) 0.10f * falling else 0f
            // 2. Aterriza de espaldas: se aplasta, rebota un poco y se asienta.
            val landSec = max(0f, t - LAND) / 1_000f
            val squash = if (t < LAND) 0f else 0.20f * exp(-7f * landSec) * cos(19f * landSec)
            val hop = if (t < LAND + 80f) 0f else -38f * sin(PI.toFloat() * segment(t, LAND + 80f, LAND + 340f))
            // 3. Se balancea jugando con la bobina (y lanza la cinta) y luego se mece suave mientras espera.
            val rockSec = max(0f, t - (LAND + 250f)) / 1_000f
            val playful = if (t < LAND + 250f) 0f else -10f * sin(2f * PI.toFloat() * rockSec / 0.6f) * exp(-1.7f * rockSec)
            val idle = segment(t, LAND + 920f, LAND + 1_820f)
            return CatPose(
                dx = -120f * (1f - fall),
                dy = -900f * (1f - falling) + hop,
                tilt = -28f * (1f - falling),
                scaleX = (1f - stretch * 0.6f) * (1f + squash * 0.8f),
                scaleY = (1f + stretch) * (1f - squash),
                rock = playful + 1.6f * sin(2f * PI.toFloat() * t / 2_800f) * idle,
                breath = 1f + 0.012f * sin(2f * PI.toFloat() * t / 1_900f) * idle,
                visible = t >= FALL_FROM,
                shadow = segment(t, 0f, LAND),
                landedAt = LAND,
            )
        }
    }
}

/** Sombra, cinta y gato, en unidades del logo. */
private fun DrawScope.drawCat(t: Float, pose: CatPose, images: IntroImages, ribbon: FilmRibbon) {
    val land = pose.landedAt
    // La cola no para quieta; al caer da un latigazo.
    val wagIn = segment(t, land - 50f, land + 300f)
    val whip = if (t < land) 0f else 26f * exp(-4f * (t - land) / 1_000f) * sin(2f * PI.toFloat() * (t - land) / 380f)
    val wag = (11f * sin(2f * PI.toFloat() * t / 950f) + 4f * sin(2f * PI.toFloat() * t / 420f)) * wagIn + whip

    // Sombra en el suelo: aparece antes que el gato y crece según se acerca.
    if (pose.shadow > 0f) {
        val grow = 0.45f + 0.55f * pose.shadow
        val c = Offset(Ground.x + pose.dx * 0.5f, Ground.y + 30f)
        withTransform({ scale(pose.scaleX * grow, 0.12f * grow, c) }) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.42f * pose.shadow),
                    1f to Color.Transparent,
                    center = c,
                    radius = 330f,
                ),
                radius = 330f,
                center = c,
            )
        }
    }

    // La cinta: el gato la lanza en el primer balanceo y se desenrolla ondeando.
    val throwP = segment(t, land + 530f, land + 1_450f)
    if (throwP > 0f) {
        val start = rotateAround(FilmRibbon.START, RockPivot, pose.rock)
        ribbon.draw(
            scope = this,
            visible = ribbon.length * easeOutCubic(throwP),
            timeMs = t,
            startShift = start - FilmRibbon.START,
            whip = (1f - throwP).pow(2),
            alpha = 1f,
        )
    }

    // Las palomitas, detrás del gato: salen despedidas de debajo de él.
    drawPopcorn(t, land, images)
    if (!pose.visible) return
    withTransform({
        translate(pose.dx, pose.dy)
        rotate(pose.tilt, BodyCenter)
        scale(pose.scaleX, pose.scaleY, Ground)
        rotate(pose.rock, RockPivot)
        scale(pose.breath, pose.breath, BodyCenter)
    }) {
        rotate(wag, TailPivot) { drawPiece(images.tail, TailBox, 1f) }
        drawPiece(images.body, BodyBox, 1f)
        drawBlink(blinkAt(t))
    }
}

/** Parpadeo cada pocos segundos mientras espera (0 = abierto, 1 = cerrado). */
private fun blinkAt(t: Float): Float {
    val from = CatPose.LAND + 1_520f
    if (t < from) return 0f
    val phase = (t - from) % 3_400f
    val single = if (phase < 190f) sin(PI.toFloat() * phase / 190f) else 0f
    // De vez en cuando, doble.
    val second = if (((t - from) / 3_400f).toInt() % 2 == 1 && phase in 260f..450f) sin(PI.toFloat() * (phase - 260f) / 190f) else 0f
    return max(single, second)
}

private fun DrawScope.drawBlink(amount: Float) {
    if (amount <= 0.01f) return
    val eye = Path().apply { addOval(Rect(EyeCenter, EYE_RADIUS)) }
    val lid = EYE_RADIUS * amount
    clipPath(eye) {
        drawRect(Fur, Offset(EyeCenter.x - EYE_RADIUS, EyeCenter.y - EYE_RADIUS), Size(EYE_RADIUS * 2f, lid))
        drawRect(Fur, Offset(EyeCenter.x - EYE_RADIUS, EyeCenter.y + EYE_RADIUS - lid), Size(EYE_RADIUS * 2f, lid))
        if (amount < 0.9f) {
            drawLine(FurInk, Offset(EyeCenter.x - EYE_RADIUS, EyeCenter.y - EYE_RADIUS + lid), Offset(EyeCenter.x + EYE_RADIUS, EyeCenter.y - EYE_RADIUS + lid), 5f)
            drawLine(FurInk, Offset(EyeCenter.x - EYE_RADIUS, EyeCenter.y + EYE_RADIUS - lid), Offset(EyeCenter.x + EYE_RADIUS, EyeCenter.y + EYE_RADIUS - lid), 5f)
        }
    }
    if (amount >= 0.9f) {
        // Ojo cerrado: una curva (la cabeza está boca abajo).
        val closed = Path().apply {
            moveTo(EyeCenter.x - 32f, EyeCenter.y - 2f)
            quadraticTo(EyeCenter.x, EyeCenter.y + 16f, EyeCenter.x + 32f, EyeCenter.y - 2f)
        }
        drawPath(closed, FurInk, style = Stroke(width = 7f, cap = StrokeCap.Round))
    }
}

/** Palomitas: salen despedidas de debajo del gato al aterrizar. */
private fun DrawScope.drawPopcorn(t: Float, land: Float, images: IntroImages) {
    val popOrigin = Offset(650f, 740f)
    PopOrder.forEachIndexed { order, i ->
        val start = land + 10f + order * 60f
        val p = segment(t, start, start + 560f)
        if (p <= 0f) return@forEachIndexed
        val box = PopBoxes[i]
        val e = easeOutCubic(p)
        val arc = (150f + 25f * (i % 3)) * 4f * p * (1f - p)
        val bob = 4f * sin(2f * PI.toFloat() * t / 1_300f + i) * segment(t, start + 560f, start + 1_100f)
        val c = Offset(lerp(popOrigin.x, box.center.x, e), lerp(popOrigin.y, box.center.y, e) - arc + bob)
        val sc = 0.35f + 0.65f * easeOutBack(p)
        val spin = (1f - e) * (if (box.center.x < popOrigin.x) -320f else 320f)
        withTransform({
            translate(c.x - box.center.x, c.y - box.center.y)
            rotate(spin, box.center)
            scale(sc, sc, box.center)
        }) {
            drawPiece(images.pops[i], box, min(1f, p * 5f))
        }
    }
}

/** BAMBI: las letras caen una a una y rebotan. */
private fun DrawScope.drawLetters(t: Float, images: IntroImages) {
    LetterBoxes.forEachIndexed { i, box ->
        val start = CatPose.LAND + 770f + i * 85f
        val p = segment(t, start, start + 680f)
        if (p <= 0f) return@forEachIndexed
        val dy = -340f * (1f - bounceOut(p))
        val tilt = (1f - easeOutCubic(p)) * (if (i % 2 == 0) -14f else 12f)
        val bottom = Offset(box.center.x, box.bottom)
        withTransform({
            translate(0f, dy)
            rotate(tilt, bottom)
        }) {
            drawPiece(images.letters[i], box, min(1f, p * 3.5f))
        }
    }
}

private fun DrawScope.drawPiece(image: ImageBitmap, box: Rect, alpha: Float) {
    drawImage(
        image = image,
        dstOffset = IntOffset(box.left.toInt(), box.top.toInt()),
        dstSize = IntSize(box.width.toInt(), box.height.toInt()),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = FilterQuality.High,
    )
}

/**
 * La cinta de película que el gato lanza: sale del extremo de la tira que rodea al gato y se desenrolla hacia la derecha
 * ondeando, con sus fotogramas y perforaciones como en el logo. Se dibuja con seis trazados reutilizados (nada de crear
 * objetos en cada fotograma).
 */
private class FilmRibbon {
    private val baseX: FloatArray
    private val baseY: FloatArray
    private val baseNx: FloatArray
    private val baseNy: FloatArray
    val length: Float

    private val px: FloatArray
    private val py: FloatArray
    private val nx: FloatArray
    private val ny: FloatArray
    private val band = Path()
    private val panels = Path()
    private val shine = Path()
    private val bars = Path()
    private val holes = Path()
    private val outline = Stroke(width = 5.5f, join = StrokeJoin.Round)
    private val shineStroke = Stroke(width = 4f, cap = StrokeCap.Round)
    private val barStroke = Stroke(width = 4.5f)

    init {
        // Curva base (Bézier cúbica) remuestreada a pasos de igual longitud.
        val raw = List(401) { i -> bezier(i / 400f) }
        val cumulative = FloatArray(raw.size)
        for (i in 1 until raw.size) cumulative[i] = cumulative[i - 1] + (raw[i] - raw[i - 1]).getDistance()
        length = cumulative.last()
        val out = ArrayList<Offset>()
        var j = 0
        var s = 0f
        while (s <= length) {
            while (j < raw.size - 2 && cumulative[j + 1] < s) j++
            val f = ((s - cumulative[j]) / (cumulative[j + 1] - cumulative[j])).coerceIn(0f, 1f)
            out += raw[j] + (raw[j + 1] - raw[j]) * f
            s += STEP
        }
        baseX = FloatArray(out.size) { out[it].x }
        baseY = FloatArray(out.size) { out[it].y }
        baseNx = FloatArray(out.size)
        baseNy = FloatArray(out.size)
        normals(baseX, baseY, out.size, baseNx, baseNy)
        px = FloatArray(out.size)
        py = FloatArray(out.size)
        nx = FloatArray(out.size)
        ny = FloatArray(out.size)
    }

    fun draw(scope: DrawScope, visible: Float, timeMs: Float, startShift: Offset, whip: Float, alpha: Float) = with(scope) {
        val n = min((visible / STEP).toInt(), baseX.size - 1)
        if (n < 2) return@with
        val phase = 2f * PI.toFloat() * timeMs / 1_500f
        val amp = 24f + 80f * whip
        for (i in 0..n) {
            val s = i * STEP
            val k = s / length
            val wave = amp * k * sin(2f * PI.toFloat() * s / WAVELENGTH - phase)
            val pull = (1f - k) * (1f - k)
            px[i] = baseX[i] + baseNx[i] * wave + startShift.x * pull
            py[i] = baseY[i] + baseNy[i] * wave + startShift.y * pull
        }
        normals(px, py, n + 1, nx, ny)

        band.reset()
        band.moveTo(ex(0, HALF), ey(0, HALF))
        for (i in 1..n) band.lineTo(ex(i, HALF), ey(i, HALF))
        for (i in n downTo 0) band.lineTo(ex(i, -HALF), ey(i, -HALF))
        band.close()

        // Fotogramas: paneles algo más oscuros separados por barras, con un brillo arriba.
        panels.reset()
        shine.reset()
        bars.reset()
        val perFrame = (FRAME / STEP).toInt()
        var f = 0
        while (f * perFrame + 2 < n) {
            val a = f * perFrame + 1
            val b = min(n, (f + 1) * perFrame - 1)
            if (b - a >= 2) {
                panels.moveTo(ex(a, INNER), ey(a, INNER))
                for (i in a + 1..b) panels.lineTo(ex(i, INNER), ey(i, INNER))
                for (i in b downTo a) panels.lineTo(ex(i, -INNER), ey(i, -INNER))
                panels.close()
                val m = (a + b) / 2
                shine.moveTo(ex(a + 1, INNER * 0.55f), ey(a + 1, INNER * 0.55f))
                shine.lineTo(ex(m, INNER * 0.55f), ey(m, INNER * 0.55f))
            }
            if (a > 1) {
                bars.moveTo(ex(a - 1, HALF), ey(a - 1, HALF))
                bars.lineTo(ex(a - 1, -HALF), ey(a - 1, -HALF))
            }
            f++
        }
        // Perforaciones a ambos lados.
        holes.reset()
        val perHole = (HOLE / STEP).toInt().coerceAtLeast(1)
        var i = perHole
        while (i < n) {
            holes.addOval(Rect(Offset(ex(i, HALF - 6.5f), ey(i, HALF - 6.5f)), 3.4f))
            holes.addOval(Rect(Offset(ex(i, -(HALF - 6.5f)), ey(i, -(HALF - 6.5f))), 3.4f))
            i += perHole
        }

        drawPath(band, FilmBase, alpha = alpha)
        drawPath(panels, FilmPanel, alpha = alpha)
        drawPath(shine, Color.White, alpha = 0.55f * alpha, style = shineStroke)
        drawPath(bars, FilmInk, alpha = alpha, style = barStroke)
        drawPath(holes, FilmInk, alpha = alpha)
        drawPath(band, FilmInk, alpha = alpha, style = outline)
    }

    private fun ex(i: Int, d: Float) = px[i] + nx[i] * d

    private fun ey(i: Int, d: Float) = py[i] + ny[i] * d

    companion object {
        /** Extremo libre de la tira en el logo (queda escondido bajo el gato). */
        val START = Offset(758f, 668f)
        private val C1 = Offset(1_010f, 720f)
        private val C2 = Offset(1_110f, 420f)
        private val END = Offset(1_560f, 500f)
        private const val STEP = 6f
        private const val HALF = 36f
        private const val INNER = 23f
        private const val FRAME = 45f
        private const val HOLE = 15f
        private const val WAVELENGTH = 430f
        private val FilmBase = Color(0xFFE3E7EB)
        private val FilmPanel = Color(0xFFB9C4CE)
        private val FilmInk = Color(0xFF15161A)

        private fun bezier(t: Float): Offset {
            val u = 1f - t
            return START * (u * u * u) + C1 * (3f * u * u * t) + C2 * (3f * u * t * t) + END * (t * t * t)
        }

        private fun normals(x: FloatArray, y: FloatArray, count: Int, outX: FloatArray, outY: FloatArray) {
            for (i in 0 until count) {
                val a = max(i - 1, 0)
                val b = min(i + 1, count - 1)
                val dx = x[b] - x[a]
                val dy = y[b] - y[a]
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
                outX[i] = -dy / len
                outY[i] = dx / len
            }
        }
    }
}

private fun rotateAround(p: Offset, pivot: Offset, degrees: Float): Offset {
    val r = degrees * PI.toFloat() / 180f
    val dx = p.x - pivot.x
    val dy = p.y - pivot.y
    return Offset(pivot.x + dx * cos(r) - dy * sin(r), pivot.y + dx * sin(r) + dy * cos(r))
}

private fun segment(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

private fun easeInOutCubic(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

private fun bounceOut(x: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val y = x - 1.5f / d1; n1 * y * y + 0.75f }
        x < 2.5f / d1 -> { val y = x - 2.25f / d1; n1 * y * y + 0.9375f }
        else -> { val y = x - 2.625f / d1; n1 * y * y + 0.984375f }
    }
}
