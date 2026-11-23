package com.shieldstream.tv.desktop

import androidx.compose.ui.awt.ComposeWindow
import com.shieldstream.tv.model.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Recorrido de prueba (-Dbambi.tour=CARPETA): abre la app de verdad en Windows, pasa por las pantallas con los datos
 * reales del usuario y guarda una captura de la propia ventana en cada una; luego se cierra. Sirve para revisar la
 * app de escritorio desde el bucle de trabajo sin tocar el PC. No reproduce nada ni cambia ajustes.
 */
class Tour(private val dir: File) {
    private var step = 0

    suspend fun run(window: ComposeWindow, navigator: Navigator, repository: DesktopRepository, session: Session, profiles: ProfileStore) {
        dir.mkdirs()
        log("perfiles: ${profiles.profiles.size}, sesión: ${session.profile?.name}")
        delay(3_000)
        if (session.profile == null) {
            shot(window, "perfiles")
            return
        }
        delay(5_000)
        shot(window, "inicio")
        val rows = runCatching { repository.homeRows() }.onFailure { log("Inicio falló: ${it.message}") }.getOrNull().orEmpty()
        log("Inicio: ${rows.size} filas")
        // «Ocultar lo que aún no se puede ver en España», con fechas reales de TMDB (sin tocar los ajustes).
        runCatching {
            val key = ConfigStore().current.tmdbKey.trim()
            val tmdb = com.shieldstream.tv.network.ApiFactory.retrofit(
                com.shieldstream.tv.network.ApiConfig.TMDB_BASE_URL,
                com.shieldstream.tv.network.ApiFactory.tmdbClient(DesktopHttp.client),
                com.shieldstream.tv.network.ApiFactory.json(),
            ).create(com.shieldstream.tv.network.api.TmdbApi::class.java)
            val gate = ReleaseGate(fetch = { id -> tmdb.movieReleaseDates(id, key) }, file = null)
            val movies = rows.flatMap { it.items }.filter { it.type == MediaType.MOVIE }.distinctBy { it.id }
            val kept = gate.filter(movies).map { it.id }.toSet()
            val hidden = movies.filter { it.id !in kept }
            log("estreno en España: ${movies.size} películas, se ocultarían ${hidden.size}: " + hidden.joinToString { "${it.title} (${it.year})" })
        }.onFailure { log("estreno en España falló: ${it.message}") }
        rows.flatMap { it.items }.firstOrNull { it.type == MediaType.MOVIE }?.let { movie ->
            navigator.open(Screen.Details(movie))
            delay(10_000)
            shot(window, "pelicula")
            navigator.back()
            delay(1_500)
            DetailsTourHook.openSources = true
            navigator.open(Screen.Details(movie))
            delay(4_000)
            DetailsTourHook.openSources = false
            shot(window, "fuentes")
            log("fuentes de «${movie.title}»: " + runCatching {
                repository.sources(movie, null).last().let { st -> "${st.sources.size} (" + st.sources.groupingBy { it.section }.eachCount() + ") sin respuesta: ${st.unavailable}" }
            }.getOrElse { "error ${it.message}" })
            navigator.back()
        }
        rows.flatMap { it.items }.firstOrNull { it.type == MediaType.TV }?.let { show ->
            navigator.open(Screen.Details(show))
            delay(6_000)
            shot(window, "serie")
            navigator.back()
            // Prueba real: el primer episodio con una fuente de verdad (la que elegiría «Reproducir»), en silencio.
            runCatching {
                val full = repository.details(show).item
                val ep = repository.episodes(full, 1).first()
                val state = repository.sources(full, ep).last()
                log("fuentes de ${full.title} T1E1: ${state.sources.size}")
                when (val pick = pickPlayable(state.sources, "debrid")) {
                    is Pick.Failed -> log("reproducir real: ${pick.message}")
                    is Pick.Ready -> {
                        val segments = repository.skipSegments(full, ep)
                        log("reproducir real: ${pick.source.section} ${pick.source.quality.label} · segmentos: $segments")
                        navigator.open(
                            Screen.Player(
                                PlayRequest(pick.source, full, ep, "${full.title} · T1 E1", startSec = 90.0, alternatives = pick.rest.take(3), segments = segments, autoNext = true),
                            ),
                        )
                        repeat(2) {
                            delay(8_000)
                            val now = (navigator.current as? Screen.Player)?.request?.source
                            log("a los ${(it + 1) * 10} s: ${navigator.current::class.simpleName} · ${now?.section} ${now?.quality?.label} " +
                                "${now?.sizeBytes?.div(1_000_000)} MB · mpv ${if (PlayerControl.session?.alive == true) "en marcha" else "parado"}")
                        }
                        log("reproductor real: ${if (PlayerControl.session?.alive == true) "mpv en marcha" else "sin mpv"}")
                        shot(window, "reproductor-real")
                        // Los controles salen al mover el ratón por encima del vídeo.
                        runCatching {
                            val p = window.locationOnScreen
                            val robot = Robot()
                            robot.mouseMove(p.x + window.width / 2, p.y + window.height / 2)
                            delay(200)
                            robot.mouseMove(p.x + window.width / 2 + 40, p.y + window.height * 3 / 4)
                        }
                        delay(800)
                        shot(window, "reproductor-controles")
                        PlayerControl.session?.key("c")
                        delay(800)
                        shot(window, "reproductor-pistas")
                        // Subtítulos de internet: la misma petición que el botón «Buscar en internet» del panel.
                        PlayerControl.session?.setProperty("user-data/bambi/request", "\"subs#900\"")
                        delay(8_000)
                        shot(window, "reproductor-subs-encontrados")
                        PlayerControl.session?.setProperty("user-data/bambi/request", "\"sub1#901\"")
                        delay(5_000)
                        PlayerControl.session?.key("c")
                        delay(1_500)
                        shot(window, "reproductor-subs-puesto")
                        PlayerControl.session?.key("c")
                        // Editor de subtítulos (como en la tele): se abre con E, se ve la muestra, y se cierra.
                        PlayerControl.session?.key("e")
                        delay(1_500)
                        shot(window, "reproductor-editor-subs")
                        PlayerControl.session?.key("e")
                        delay(800)
                        PlayerControl.toggleFullscreen()
                        delay(2_000)
                        log("pantalla completa: ${PlayerControl.isFullscreen()}")
                        shot(window, "reproductor-pantalla-completa")
                        PlayerControl.toggleFullscreen()
                        delay(1_500)
                        log("tras salir de pantalla completa: ${PlayerControl.isFullscreen()}")
                        // El botón de la barra, con un clic como el del ratón (abajo a la derecha).
                        val (vw, vh) = window.contentPane.let { c ->
                            val t = c.graphicsConfiguration?.defaultTransform
                            Math.round(c.width * (t?.scaleX ?: 1.0)).toInt() to Math.round(c.height * (t?.scaleY ?: 1.0)).toInt()
                        }
                        val sc = (vh / 1080.0).coerceIn(0.55, 2.2)
                        val bx = (vw - 72 * sc).toInt()
                        val by = (vh - 60 * sc).toInt()
                        PlayerControl.mouseMove(bx, by)
                        delay(300)
                        PlayerControl.mouseButton(bx, by, down = true)
                        delay(80)
                        PlayerControl.mouseButton(bx, by, down = false)
                        delay(2_000)
                        log("botón pantalla completa ($bx,$by de ${vw}x$vh): ${PlayerControl.isFullscreen()}")
                        if (PlayerControl.isFullscreen()) PlayerControl.setFullscreen(false)
                        delay(1_000)
                        // Dentro de la intro (IntroDB): S la salta.
                        PlayerControl.session?.key("s")
                        delay(4_000)
                        shot(window, "reproductor-tras-omitir")
                        PlayerControl.session?.key("t")
                        delay(1_500)
                        shot(window, "reproductor-temporizador")
                        PlayerControl.session?.quit()
                        delay(3_000)
                        log("tras el vídeo real: pantalla ${navigator.current::class.simpleName}")
                        keyTests(window, navigator, PlayRequest(pick.source, full, ep, "${full.title} · T1 E1", startSec = 90.0, alternatives = pick.rest.take(3), segments = segments, autoNext = true))
                        // La prueba no debe quedarse en «Seguir viendo» del usuario.
                        session.profile?.let { p -> profiles.library(p).clear(progressKey(full.type.name, full.id, ep.season, ep.number)) }
                        navigator.section(Screen.Home)
                    }
                }
            }.onFailure { log("reproducir real falló: ${it.message}") }
        }
        for ((name, screen) in listOf("series" to Screen.Shows, "peliculas" to Screen.Movies, "milista" to Screen.MyList, "buscar" to Screen.Search, "ajustes" to Screen.Settings)) {
            navigator.section(screen)
            delay(if (screen == Screen.Shows || screen == Screen.Movies) 6_000 else 2_500)
            shot(window, name)
        }
        // Las otras secciones de Ajustes.
        for (tab in listOf(SettingsTab.PLAYBACK, SettingsTab.SOURCES, SettingsTab.ABOUT)) {
            navigator.section(Screen.Home)
            delay(500)
            SettingsTourHook.tab = tab
            navigator.section(Screen.Settings)
            delay(1_500)
            SettingsTourHook.tab = null
            shot(window, "ajustes-" + tab.name.lowercase())
        }
        // Arrastrar una fila del Inicio con el ratón (clic mantenido).
        navigator.section(Screen.Home)
        delay(3_000)
        session.profile?.let { navigator.listState("home-ALL-${it.id}").scrollToItem(4) }
        delay(1_000)
        runCatching {
            val p = window.locationOnScreen
            val robot = Robot()
            val y = p.y + window.height / 2 + 60
            robot.mouseMove(p.x + window.width - 250, y)
            delay(400)
            shot(window, "inicio-antes-de-arrastrar")
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            for (i in 1..20) { robot.mouseMove(p.x + window.width - 250 - i * 35, y); delay(16) }
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            delay(1_200)
            log("tras arrastrar: pantalla ${navigator.current::class.simpleName}")
            shot(window, "inicio-arrastrado")
            robot.mouseMove(p.x + 10, p.y + 200)
        }
        // Lo mismo que enseña Ajustes, en el registro: mpv y si las claves valen (sin mostrarlas).
        val config = ConfigStore().current
        log("mpv: ${MpvPlayer.find(config.mpvPath) ?: "no instalado"}")
        runCatching { repository.checkKeys(config) }
            .onSuccess { log("claves: ${it.tmdb.message} · ${it.debrid.message}") }
            .onFailure { log("claves: error ${it.message}") }
        // Reproductor dentro de la ventana, con un vídeo libre de 10 s (Big Buck Bunny), en silencio.
        navigator.section(Screen.Home)
        val sample = com.shieldstream.tv.model.CatalogItem("tour", null, MediaType.MOVIE, "Big Buck Bunny", "", null, null, 2008, null)
        navigator.open(
            Screen.Player(
                PlayRequest(
                    DesktopSource("Big Buck Bunny", "", com.shieldstream.tv.model.Quality.HD_720, false,
                        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"),
                    sample, null, "Big Buck Bunny",
                ),
            ),
        )
        delay(7_000)
        log("reproductor: ${if (PlayerControl.session?.alive == true) "mpv en marcha" else "sin mpv"} · pantalla ${navigator.current::class.simpleName}")
        shot(window, "reproductor")
        PlayerControl.session?.quit()
        delay(2_500)
        log("tras salir: pantalla ${navigator.current::class.simpleName}")
        session.profile = null
        delay(2_500)
        shot(window, "perfiles")
    }

    /**
     * Teclas de verdad (Robot), como alguien con prisas: Escape en la ficha, Escape con el vídeo aún arrancando,
     * Escape reproduciendo, teclas a lo loco, Escape doble y saltar de un vídeo a otro. Nunca debe quedar más de un
     * mpv ni la app sin responder.
     */
    private suspend fun keyTests(window: ComposeWindow, navigator: Navigator, req: PlayRequest) {
        val robot = Robot()
        // Las teclas del Robot van a la ventana activa: un clic en la barra de título activa la de Bambi.
        fun focus() {
            val p = window.locationOnScreen
            robot.mouseMove(p.x + window.width / 2, p.y + 12)
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            robot.delay(150)
        }
        fun press(code: Int) {
            if (!window.isFocused) focus()
            robot.keyPress(code)
            robot.delay(40)
            robot.keyRelease(code)
        }
        fun mpvCount() = ProcessHandle.current().descendants()
            .filter { it.isAlive && it.info().command().orElse("").endsWith("mpv.exe", ignoreCase = true) }.count()
        fun screen() = navigator.current::class.simpleName
        val K = java.awt.event.KeyEvent::class.java
        fun code(name: String) = K.getField("VK_$name").getInt(null)
        window.toFront()
        focus()
        log("teclas: ventana activa ${window.isFocused}")
        val details = Screen.Details(req.item)
        // 1) Ficha + Escape: vuelve una sola pantalla.
        navigator.section(Screen.Home)
        navigator.open(details)
        delay(2_500)
        press(code("ESCAPE"))
        delay(1_200)
        log("teclas 1 · ficha + Escape: ${screen()} (esperado Home)")
        // 2) Escape con mpv aún arrancando.
        navigator.open(details)
        delay(1_500)
        navigator.open(Screen.Player(req))
        delay(300)
        press(code("ESCAPE"))
        delay(4_000)
        log("teclas 2 · Escape al arrancar: ${screen()} (esperado Details) · mpv ${mpvCount()} · sesión ${PlayerControl.session != null}")
        // 3) Reproduciendo, Escape: a la ficha (no al Inicio).
        navigator.open(Screen.Player(req))
        delay(9_000)
        press(code("ESCAPE"))
        delay(4_000)
        log("teclas 3 · Escape reproduciendo: ${screen()} (esperado Details) · mpv ${mpvCount()}")
        // 4) Teclas a lo loco y Escape doble.
        navigator.open(Screen.Player(req))
        delay(9_000)
        for (k in listOf("SPACE", "SPACE", "RIGHT", "RIGHT", "LEFT", "UP", "DOWN", "C", "E", "RIGHT", "ESCAPE", "C", "ESCAPE", "M", "M", "F", "F")) {
            press(code(k))
            delay(120)
        }
        delay(1_500)
        log("teclas 4 · tras teclas: ${screen()} · mpv ${mpvCount()} · pantalla completa ${PlayerControl.isFullscreen()}")
        shot(window, "teclas-a-lo-loco")
        press(code("ESCAPE"))
        delay(450)
        press(code("ESCAPE"))
        delay(4_000)
        log("teclas 4b · Escape doble (cierra el panel y sale de pantalla completa): ${screen()} · mpv ${mpvCount()} · pantalla completa ${PlayerControl.isFullscreen()}")
        // 5) De un vídeo a otro sin esperar: nunca dos mpv.
        navigator.open(Screen.Player(req))
        delay(6_000)
        navigator.back()
        navigator.open(Screen.Player(req.copy(startSec = 300.0)))
        delay(7_000)
        log("teclas 5 · cambio de vídeo: mpv ${mpvCount()} (esperado 1) · ${screen()}")
        PlayerControl.session?.let { PlayerControl.quit(it) }
        delay(5_000)
        log("teclas fin: mpv ${mpvCount()} · ${screen()} · sesión ${PlayerControl.session != null}")
        navigator.section(Screen.Home)
    }

    private fun shot(window: ComposeWindow, name: String) {
        step++
        val file = File(dir, "tour-%02d-%s.png".format(step, name))
        // Ya estamos en el hilo de la interfaz (el de las corrutinas de Compose).
        val bounds = Rectangle(window.locationOnScreen, window.size)
        val capture = Robot().createMultiResolutionScreenCapture(bounds)
        val image = capture.resolutionVariants.maxByOrNull { it.getWidth(null) } as BufferedImage
        ImageIO.write(image, "png", file)
        log("captura ${file.name}")
    }

    fun log(text: String) {
        println(text)
        File(dir, "tour.txt").appendText(text + "\n")
    }
}
