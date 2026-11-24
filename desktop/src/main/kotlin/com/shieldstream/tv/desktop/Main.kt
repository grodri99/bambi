package com.shieldstream.tv.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Lang
import java.io.File

fun main(args: Array<String>) {
    // Desde Configuración → Aplicaciones de Windows: «Bambi.exe --desinstalar».
    // Comprobación del paquete (Bambi.exe --comprobar fichero): qué Java y qué mpv encuentra, sin abrir ventana.
    args.indexOf("--comprobar").takeIf { it >= 0 }?.let { i ->
        File(args.getOrElse(i + 1) { "bambi-comprobar.txt" }).writeText(
            "java=${System.getProperty("java.version")} (${System.getProperty("java.home")})\n" +
                "mpv=${MpvPlayer.find("")}\n" +
                "carpeta=${Installer.appRoot()}\ninstalado=${Installer.isInstalled()}\n",
        )
        kotlin.system.exitProcess(0)
    }
    if ("--desinstalar" in args) {
        Installer.uninstallFromWindows()
        kotlin.system.exitProcess(0)
    }
    // Si algo revienta, que quede escrito (Bambi se abre sin consola).
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        runCatching { File(System.getProperty("java.io.tmpdir"), "bambi-error.log").appendText(e.stackTraceToString() + "\n") }
    }
    val tour = System.getProperty("bambi.tour")?.let { Tour(File(it)) }
    // En el recorrido de prueba, dibujo por software: las capturas de pantalla (Robot) a veces salen negras con DirectX.
    // («…dx» en tour-solo.txt: con la tarjeta gráfica, como lo usa la gente, para medir destellos.)
    val tourMode = System.getProperty("bambi.tour")?.let { runCatching { File(it, "tour-solo.txt").readText().trim() }.getOrNull() }.orEmpty()
    if (tour != null && !tourMode.endsWith("dx") && System.getProperty("skiko.renderApi") == null) System.setProperty("skiko.renderApi", "SOFTWARE")
    application {
        val config = remember { ConfigStore() }
        Lang.current = AppLanguage.fromName(config.current.language) ?: AppLanguage.default()
        val profiles = remember { ProfileStore() }
        val repository = remember { TmdbTorrentioRepository(config) }
        val navigator = remember { Navigator(if (config.current.tmdbKey.isBlank()) Screen.Setup else Screen.Home) }
        // Con un solo perfil (sin PIN) se entra directamente; con varios, «¿Quién está viendo?» (en el recorrido, el primero).
        val session = remember {
            Session(
                profiles.profiles.singleOrNull()?.takeUnless { it.locked }
                    ?: if (tour != null) profiles.profiles.firstOrNull() ?: DesktopProfile(name = "Prueba") else null,
            )
        }
        val notice = remember { Notice() }
        val trakt = remember { TraktManager(config, profiles, session, repository) }
        // yt-dlp (tráileres): se pone al día en segundo plano si tiene más de dos semanas. Y se deja cargado lo que usa
        // el reproductor la primera vez (JNA, el script), para que dar a Reproducir no frene la ventana.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    com.sun.jna.Native.POINTER_SIZE
                    com.sun.jna.platform.win32.User32.INSTANCE
                    BambiScript.options(null, false)
                    BambiScript.parse("")
                    MpvPlayer.find(config.current.mpvPath)
                    // Guardar el progreso al salir del vídeo (la primera vez cargaba el serializador en el hilo de la
                    // ventana y se veía un fotograma en blanco al volver a la ficha).
                    kotlinx.serialization.json.Json.encodeToString(LibraryFile.serializer(), LibraryFile())
                    kotlinx.serialization.json.Json.encodeToString(DesktopConfig.serializer(), DesktopConfig())
                }
                MpvPlayer.refreshYtDlp(config.current.mpvPath)
            }
        }
        // La ventana vuelve como la dejaste (tamaño, posición y monitor, maximizada o a pantalla completa).
        val saved = remember { WindowPrefs.load() }
        val windowState = rememberWindowState(
            width = saved?.width?.dp ?: 1440.dp,
            height = saved?.height?.dp ?: 880.dp,
            position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition(Alignment.Center),
            // Si se cerró a pantalla completa, se abre en ventana y se pone a pantalla completa ya abierta (abrir
            // directamente así dejaba franjas blancas abajo y a la derecha con la pantalla escalada).
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        val fullscreen = remember { WindowFullscreen(windowState) }
        var userFullscreen by remember { mutableStateOf(saved?.fullscreen == true) }
        // El Escape que cerró el vídeo no debe, al soltarlo, cerrar también la ficha.
        var escapeForPlayer by remember { mutableStateOf(false) }
        fun applyFullscreen(full: Boolean) = fullscreen.set(full)
        PlayerControl.isFullscreen = { fullscreen.active }
        PlayerControl.applyFullscreen = { full ->
            if (!full) userFullscreen = false
            applyFullscreen(full)
        }
        Window(
            onCloseRequest = {
                PlayerControl.session?.quit()
                if (tour == null) WindowPrefs.save(windowState, fullscreen.active, fullscreen.windowed)
                exitApplication()
            },
            title = "Bambi",
            icon = BundledImages.get("bambi_logo.png")?.let { BitmapPainter(it) },
            alwaysOnTop = tour != null,
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (!InputMode.keyboard) InputMode.keyboard = true
                // Durante la intro, cualquier tecla la salta (y no llega a la pantalla de debajo).
                val gate = IntroKeyGate.onKey
                val native = event.nativeKeyEvent as? java.awt.event.KeyEvent
                if (PlayerControl.session != null && navigator.current is Screen.Player) {
                    // Reproduciendo: las teclas son para mpv (Escape sale y vuelve a la ficha).
                    if (event.type == KeyEventType.KeyDown && native != null) {
                        // Escape mantenido (repetición del teclado) cuenta una sola vez.
                        if (event.key != Key.Escape || !escapeForPlayer) PlayerControl.handleKey(native.keyCode, native.keyChar)
                        if (event.key == Key.Escape) escapeForPlayer = true
                    }
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) escapeForPlayer = false
                    true
                } else if (navigator.current is Screen.Player) {
                    // Vídeo abriéndose o cerrándose: Escape sale de él; el resto de teclas, nada (para no tocar la ficha).
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && !escapeForPlayer) {
                        escapeForPlayer = true
                        navigator.back()
                    }
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) escapeForPlayer = false
                    true
                } else if (gate != null) {
                    if (event.type == KeyEventType.KeyUp) gate()
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.F11) {
                    // Modo «10 pies»: pantalla completa con F11, como una tele. Siempre según cómo está de verdad (antes,
                    // tras salir del vídeo a pantalla completa, había que pulsar F11 varias veces).
                    userFullscreen = !fullscreen.active
                    applyFullscreen(userFullscreen)
                    true
                } else if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    if (escapeForPlayer) escapeForPlayer = false else navigator.back()
                    true
                } else {
                    false
                }
            },
        ) {
            // Teclado del reproductor: se recoge para toda la ventana, tenga el foco quien lo tenga. Al abrir el
            // vídeo, Windows deja el foco en la zona del vídeo (o en ninguna parte) y las teclas no llegaban: Escape
            // no salía y parecía que la app se había colgado.
            androidx.compose.runtime.DisposableEffect(Unit) {
                val dispatcher = java.awt.KeyEventDispatcher { e ->
                    if (navigator.current !is Screen.Player) return@KeyEventDispatcher false
                    val esc = e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE
                    when (e.id) {
                        java.awt.event.KeyEvent.KEY_PRESSED -> {
                            if (esc) {
                                // Mantenido (repetición del teclado): cuenta una vez.
                                if (!escapeForPlayer) {
                                    escapeForPlayer = true
                                    if (PlayerControl.session != null) PlayerControl.handleKey(e.keyCode, e.keyChar)
                                    else navigator.back() // aún arrancando: se sale sin esperar a mpv
                                }
                            } else if (e.keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE && PlayerControl.session == null) {
                                navigator.back()
                            } else {
                                PlayerControl.handleKey(e.keyCode, e.keyChar)
                            }
                        }
                        java.awt.event.KeyEvent.KEY_RELEASED -> if (esc) escapeForPlayer = false
                    }
                    true
                }
                val manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                manager.addKeyEventDispatcher(dispatcher)
                onDispose { manager.removeKeyEventDispatcher(dispatcher) }
            }
            // Como en la tele: la intro va encima y, al salir, la app es el fotograma siguiente de la tira de película.
            var introDone by remember { mutableStateOf(tour != null || System.getProperty("bambi.nointro") != null) }
            var introRunning by remember { mutableStateOf(false) }
            var reveal by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    if (introDone || !introRunning) return@graphicsLayer
                    val s = IntroReveal.scale(reveal)
                    scaleX = s
                    scaleY = s
                    translationY = size.height * IntroReveal.appOffset(reveal)
                },
            ) {
            AdaptiveScale {
            BambiApp(
                repository = repository,
                config = config,
                profiles = profiles,
                navigator = navigator,
                session = session,
                notice = notice,
                trakt = trakt,
                onFullscreen = { full ->
                    applyFullscreen(full || userFullscreen)
                },
            )
            }
            }
            if (!introDone) {
                IntroScreen(
                    onStartPreload = {},
                    awaitReady = { true },
                    onFinished = { introDone = true },
                    onReveal = { reveal = it },
                    fadeFromBlack = false,
                    onAnimationStart = { introRunning = true },
                )
                // Un clic también la salta.
                Box(
                    Modifier.fillMaxSize().clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { IntroKeyGate.onKey?.invoke() },
                )
            }
            }
            androidx.compose.runtime.SideEffect {
                fullscreen.window = window
                // Debajo de la interfaz, negro: por el hueco que deja para el vídeo se veía el gris claro de Windows
                // un instante al dar a Reproducir (el «destello blanco»).
                val black = java.awt.Color.BLACK
                if (window.background != black) {
                    window.background = black
                    window.contentPane.background = black
                    window.rootPane.background = black
                    window.layeredPane.background = black
                }
            }
            // Se cerró a pantalla completa: se vuelve a poner, con la ventana ya abierta y colocada.
            if (saved?.fullscreen == true && tour == null) LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(300)
                fullscreen.set(true)
            }
            if (tour == null) LaunchedEffect(Unit) { Gamepad.start(window) }
            if (tour != null) {
                LaunchedEffect(Unit) {
                    runCatching { tour.run(window, navigator, repository, session, profiles, trakt) }
                        .onFailure { tour.log("ERROR: ${it.stackTraceToString()}") }
                    exitApplication()
                }
            }
        }
    }
}

/** Guarda por dónde vas al cerrar mpv (y avisa si no se pudo reproducir). */
fun recordPlayback(library: LibraryStore, request: PlayRequest, result: PlaybackResult, notice: Notice) {
    if (result.failed) {
        notice.text = "No se pudo reproducir esta fuente. Prueba con otra."
        return
    }
    val previous = library.progressFor(request.item, request.episode?.season, request.episode?.number)
    val duration = result.durationSec ?: previous?.durationSec ?: 0.0
    library.record(
        SavedProgress(
            item = SavedItem.of(request.item),
            season = request.episode?.season,
            episode = request.episode?.number,
            positionSec = if (result.finished) duration else result.positionSec ?: request.startSec,
            durationSec = duration,
            updatedAt = System.currentTimeMillis(),
            finished = result.finished || (duration > 0 && (result.positionSec ?: 0.0) / duration >= 0.93),
        ),
    )
}

/** Tamaño, posición y modo de la ventana (window.json), para abrir como se cerró. */
@kotlinx.serialization.Serializable
data class WindowPrefs(val x: Float, val y: Float, val width: Float, val height: Float, val maximized: Boolean, val fullscreen: Boolean) {
    companion object {
        private val file get() = File(ConfigStore.defaultFile().parentFile, "window.json")

        fun load(): WindowPrefs? = runCatching {
            kotlinx.serialization.json.Json.decodeFromString(serializer(), file.readText())
        }.getOrNull()?.takeIf { it.width >= 640 && it.height >= 400 }

        /** [windowed]: cómo estaba la ventana antes de la pantalla completa (si lo está), que es lo que se guarda. */
        fun save(
            state: androidx.compose.ui.window.WindowState,
            fullscreen: Boolean,
            windowed: Triple<androidx.compose.ui.unit.DpSize, WindowPosition, WindowPlacement>? = null,
        ) {
            runCatching {
                val pos = windowed?.second ?: state.position
                val size = windowed?.first ?: state.size
                val placement = windowed?.third ?: state.placement
                val prefs = WindowPrefs(
                    x = if (pos is WindowPosition.Absolute) pos.x.value else 0f,
                    y = if (pos is WindowPosition.Absolute) pos.y.value else 0f,
                    width = size.width.value,
                    height = size.height.value,
                    maximized = placement == WindowPlacement.Maximized,
                    fullscreen = fullscreen,
                )
                file.parentFile?.mkdirs()
                file.writeText(kotlinx.serialization.json.Json.encodeToString(serializer(), prefs))
            }
        }
    }
}
