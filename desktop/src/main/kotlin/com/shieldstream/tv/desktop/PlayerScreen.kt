package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

/** Ajustes que usa el reproductor (los copia BambiApp de los ajustes al abrir un vídeo). */
object PlayerPrefs {
    @Volatile
    var autoSubtitles = false

    @Volatile
    var subtitleScale = 1f

    @Volatile
    var subtitleStyle = SubtitleStyle()

    /** Al cambiar el estilo en el editor del reproductor: se guarda en los ajustes. */
    @Volatile
    var onStyleChanged: (SubtitleStyle) -> Unit = {}
}

/** El reproductor en marcha (para que la ventana le pase las teclas). */
object PlayerControl {
    @Volatile
    var session: MpvSession? = null

    /** Si la ventana está a pantalla completa y cómo cambiarlo (lo pone la ventana de Bambi). */
    @Volatile
    var isFullscreen: () -> Boolean = { false }

    @Volatile
    var applyFullscreen: (Boolean) -> Unit = {}

    @Volatile
    private var lastToggle = 0L

    @Volatile
    private var lastEscape = 0L

    @Volatile
    private var lastUiKey = 0L

    /** El script tiene abierto el panel de audio y subtítulos o el editor: Escape es para cerrarlo. */
    @Volatile
    var overlayOpen = false

    /** Tamaño del vídeo en píxeles de la pantalla (para la prueba con datos reales). */
    @Volatile
    var videoSize = 0 to 0

    /** F, F11, doble clic o el botón: pantalla completa sí/no, como en Netflix. */
    fun toggleFullscreen() {
        val now = System.currentTimeMillis()
        if (now - lastToggle < 400) return
        lastToggle = now
        setFullscreen(!isFullscreen())
    }

    fun setFullscreen(full: Boolean) {
        applyFullscreen(full)
        send { it.setProperty("user-data/bambi/fs", full.toString()) }
    }

    // El vídeo de mpv va dentro de la ventana de Bambi y Windows le da el ratón a Bambi, no a mpv: se le pasa por la
    // tubería (los movimientos, como mucho 30 por segundo; los clics, en orden).
    private val mouseIo = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mpv-mouse").apply { isDaemon = true } }
    private val moveQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var lastMouse = 0 to 0

    fun mouseMove(x: Int, y: Int) {
        lastMouse = x to y
        if (moveQueued.compareAndSet(false, true)) {
            mouseIo.schedule({
                moveQueued.set(false)
                val (mx, my) = lastMouse
                session?.commands("[\"mouse\",$mx,$my]")
            }, 33, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    fun mouseButton(x: Int, y: Int, down: Boolean) {
        lastMouse = x to y
        mouseIo.execute { session?.commands("[\"mouse\",$x,$y]", "[\"${if (down) "keydown" else "keyup"}\",\"MBTN_LEFT\"]") }
    }

    fun mouseWheel(up: Boolean) = mouseIo.execute { session?.key(if (up) "WHEEL_UP" else "WHEEL_DOWN") }

    fun mouseLeft() = mouseIo.execute { session?.commands("[\"script-message\",\"bambi-mouse-leave\"]") }

    /**
     * Todo lo que se le manda a mpv va por este hilo, en orden y sin parar nunca la ventana (si mpv tarda en
     * contestar o se está cerrando, la interfaz sigue respondiendo).
     */
    private fun send(action: (MpvSession) -> Unit) {
        val s = session ?: return
        mouseIo.execute { if (s.alive) runCatching { action(s) } }
    }

    /** Cerrar un mpv concreto (guardando por dónde iba) sin parar la ventana. */
    fun quit(s: MpvSession) = mouseIo.execute { runCatching { s.quit() } }

    /** Escape: primero sale de pantalla completa; si no lo está, sale del vídeo (guardando por dónde vas). */
    fun escape() {
        if (isFullscreen()) setFullscreen(false) else session?.let { quit(it) }
    }

    /** Teclas de la ventana → mpv. Escape sale (guardando por dónde vas). */
    fun handleKey(code: Int, char: Char): Boolean {
        val s = session ?: return false
        val name = when (code) {
            // Escape lo decide el script (cierra el editor o el panel); si mpv aún no contesta, sale Bambi.
            KeyEvent.VK_ESCAPE -> {
                // Escape mantenido o pulsado muy seguido cuenta una vez.
                val now = System.currentTimeMillis()
                if (now - lastEscape < 150) return true
                lastEscape = now
                // Se decide en orden con las teclas anteriores: si justo se ha pulsado C o E (abren panel o editor),
                // se espera a que el script diga si está abierto.
                send {
                    val sinceUiKey = System.currentTimeMillis() - lastUiKey
                    if (sinceUiKey < 400) Thread.sleep(400 - sinceUiKey)
                    if (overlayOpen) it.key("ESC")
                    else javax.swing.SwingUtilities.invokeLater { if (session === it) escape() }
                }
                return true
            }
            KeyEvent.VK_BACK_SPACE -> { quit(s); return true }
            KeyEvent.VK_F, KeyEvent.VK_F11 -> { toggleFullscreen(); return true }
            KeyEvent.VK_SPACE -> "SPACE"
            KeyEvent.VK_ENTER -> "SPACE"
            KeyEvent.VK_LEFT -> "LEFT"
            KeyEvent.VK_RIGHT -> "RIGHT"
            KeyEvent.VK_UP -> "UP"
            KeyEvent.VK_DOWN -> "DOWN"
            KeyEvent.VK_PAGE_UP -> "PGUP"
            KeyEvent.VK_PAGE_DOWN -> "PGDWN"
            else -> char.takeIf { it.isLetterOrDigit() || it in "#[]{}<>+-=*/." }?.toString() ?: return false
        }
        if (name == "c" || name == "e") lastUiKey = System.currentTimeMillis()
        send { it.key(name) }
        return true
    }
}

/**
 * Reproductor dentro de la ventana de Bambi: mpv se dibuja en una zona negra de la propia ventana (--wid) con los
 * controles de Bambi encima (como Netflix en el PC). No se pone solo a pantalla completa: F, F11, doble clic o el
 * botón. Escape sale de pantalla completa y, si no lo está, vuelve a la ficha guardando por dónde vas.
 */
@Composable
fun PlayerScreen(
    request: PlayRequest,
    mpvPath: String,
    onError: (String) -> Unit,
    onExit: (PlaybackResult) -> Unit,
) {
    val canvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
            // Si se hace clic en el vídeo, las teclas siguen llegando a mpv.
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (PlayerControl.handleKey(e.keyCode, e.keyChar)) e.consume()
                }
            })
            // El ratón, a mpv (con los píxeles reales de la pantalla), y el puntero se esconde a los 3 s sin moverse.
            val canvas = this
            val blank = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "bambi-sin-puntero",
            )
            val hide = javax.swing.Timer(3_000) { canvas.cursor = blank }.apply { isRepeats = false }
            fun px(e: java.awt.event.MouseEvent): Pair<Int, Int> {
                val t = canvas.graphicsConfiguration?.defaultTransform
                PlayerControl.videoSize = Math.round(canvas.width * (t?.scaleX ?: 1.0)).toInt() to Math.round(canvas.height * (t?.scaleY ?: 1.0)).toInt()
                return Math.round(e.x * (t?.scaleX ?: 1.0)).toInt() to Math.round(e.y * (t?.scaleY ?: 1.0)).toInt()
            }
            fun activity() {
                canvas.cursor = java.awt.Cursor.getDefaultCursor()
                hide.restart()
            }
            val mouse = object : java.awt.event.MouseAdapter() {
                override fun mouseMoved(e: java.awt.event.MouseEvent) { activity(); px(e).let { PlayerControl.mouseMove(it.first, it.second) } }
                override fun mouseDragged(e: java.awt.event.MouseEvent) { activity(); px(e).let { PlayerControl.mouseMove(it.first, it.second) } }
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    canvas.requestFocus()
                    activity()
                    if (e.button == java.awt.event.MouseEvent.BUTTON1) px(e).let { PlayerControl.mouseButton(it.first, it.second, down = true) }
                }
                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    if (e.button == java.awt.event.MouseEvent.BUTTON1) px(e).let { PlayerControl.mouseButton(it.first, it.second, down = false) }
                }
                override fun mouseExited(e: java.awt.event.MouseEvent) = PlayerControl.mouseLeft()
                override fun mouseWheelMoved(e: java.awt.event.MouseWheelEvent) { activity(); PlayerControl.mouseWheel(e.wheelRotation < 0) }
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
            addMouseWheelListener(mouse)
        }
    }
    // La zona del vídeo es una ventana de Windows: se coloca con la densidad real de la pantalla, no con la escalada
    // de la interfaz (si no, mpv se quedaba en una esquina).
    val density = LocalBaseDensity.current ?: androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
            SwingPanel(factory = { canvas }, modifier = Modifier.fillMaxSize(), background = Color.Black)
        }
    }
    val subs = androidx.compose.runtime.rememberCoroutineScope { Dispatchers.IO }
    // El mpv de ESTE vídeo y si ya se ha salido de él: así un mpv que arranca tarde, o el aviso de uno que se cierra,
    // no se mezclan con el vídeo siguiente ni dejan la app sin teclado.
    val mine = remember(request) { java.util.concurrent.atomic.AtomicReference<MpvSession?>(null) }
    val gone = remember(request) { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(request) {
        // Hace falta que la zona ya exista en pantalla para tener su identificador de ventana.
        while (!canvas.isDisplayable || canvas.width == 0) delay(30)
        if (gone.get()) return@LaunchedEffect
        // Si queda un mpv de otro vídeo, fuera (nunca dos sonando a la vez).
        PlayerControl.session?.takeIf { it.alive }?.let { PlayerControl.quit(it) }
        val wid = runCatching { com.sun.jna.Native.getComponentID(canvas) }.getOrNull()?.takeIf { it != 0L }
        MpvPlayer.start(mpvPath, request, wid) { result ->
            javax.swing.SwingUtilities.invokeLater {
                val s = mine.get()
                if (s != null && PlayerControl.session === s) PlayerControl.session = null
                // Si ya se había salido de este vídeo, no se reintenta otra fuente ni se navega.
                if (gone.get() && (result.failed || result.report.slate)) return@invokeLater
                onExit(result)
            }
        }.onSuccess { s ->
            mine.set(s)
            if (gone.get()) {
                PlayerControl.quit(s)
                return@onSuccess
            }
            PlayerControl.session = s
            PlayerControl.overlayOpen = false
            val online = OnlineSubtitles(s, ExternalSubs.Target(request.item, request.episode, request.source.title))
            s.listen(
                onConnected = { s.setProperty("user-data/bambi/fs", PlayerControl.isFullscreen().toString()) },
                onRequest = { what ->
                    System.err.println("mpv pide: $what")
                    when {
                        what == "ui1" || what == "ui0" -> if (PlayerControl.session === s) PlayerControl.overlayOpen = what == "ui1"
                        what.startsWith("st:") -> SubtitleStyle.decode(what.removePrefix("st:"))?.let {
                            PlayerPrefs.subtitleStyle = it
                            subs.launch { PlayerPrefs.onStyleChanged(it) }
                        }
                        what == "subs" -> subs.launch { online.search(auto = false) }
                        what == "subsauto" -> subs.launch { online.search(auto = true) }
                        what.startsWith("sub") -> what.removePrefix("sub").toIntOrNull()?.let { n -> subs.launch { online.add(n - 1) } }
                    }
                    javax.swing.SwingUtilities.invokeLater {
                        // Solo si sigue siendo el vídeo que se está viendo.
                        if (PlayerControl.session === s) {
                            when (what) {
                                "fs" -> PlayerControl.toggleFullscreen()
                                "esc" -> PlayerControl.escape()
                            }
                        }
                    }
                },
            )
        }
            .onFailure { System.err.println("mpv no se pudo abrir: ${it.message}"); onError(it.message ?: "No se pudo reproducir") }
    }
    DisposableEffect(request) {
        onDispose {
            gone.set(true)
            mine.get()?.let { s ->
                PlayerControl.quit(s)
                // Las teclas vuelven a la app al momento, aunque mpv tarde en cerrarse.
                if (PlayerControl.session === s) PlayerControl.session = null
            }
        }
    }
}


/**
 * Subtítulos de internet durante la reproducción: busca (español primero, y si es a mano también inglés), se los
 * enseña al script en el panel de audio y subtítulos y añade a mpv el que se elija. En automático pone el mejor en
 * español sin preguntar.
 */
private class OnlineSubtitles(private val session: MpvSession, private val target: ExternalSubs.Target) {
    private var found: List<com.shieldstream.tv.model.ExternalSubtitle> = emptyList()

    /** Texto en JSON (entre comillas y con las barras de Windows escapadas). */
    private fun text(value: String) = kotlinx.serialization.json.JsonPrimitive(value).toString()

    private fun state(value: String) = session.setProperty("user-data/bambi/subs-state", text(value))

    suspend fun search(auto: Boolean) {
        state("searching")
        val first = if (com.shieldstream.tv.model.Lang.isEnglish) "en" else "es"
        found = runCatching {
            val main = ExternalSubs.search(target, first).take(if (auto) 1 else 7)
            if (auto) main else main + ExternalSubs.search(target, if (first == "es") "en" else "es").take(3)
        }.getOrElse {
            System.err.println("subtítulos: ${it.message}")
            state("error")
            return
        }
        val labels = kotlinx.serialization.json.JsonArray(
            found.map { kotlinx.serialization.json.JsonObject(mapOf("label" to kotlinx.serialization.json.JsonPrimitive(ExternalSubs.label(it)))) },
        )
        session.setProperty("user-data/bambi/subs", labels.toString())
        when {
            found.isEmpty() -> state("none")
            auto -> add(0)
            else -> state("done")
        }
    }

    suspend fun add(index: Int) {
        val sub = found.getOrNull(index) ?: return
        runCatching { ExternalSubs.download(sub, target) }
            .onSuccess { file ->
                val label = ExternalSubs.label(sub)
                session.commands("[\"sub-add\",${text(file.absolutePath)},\"select\",${text(label)},${text(sub.language)}]")
                val lang = when (sub.language) { "es" -> "español"; "en" -> "inglés"; else -> sub.language }
                state("added:$lang (${sub.provider})")
            }
            .onFailure {
                System.err.println("subtítulo: ${it.message}")
                state("error")
            }
    }
}
