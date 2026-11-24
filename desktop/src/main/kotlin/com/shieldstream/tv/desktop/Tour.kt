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

    /** Lo que se grabó desde que se abrió la ventana (modo foro3): tiempo, pantalla y % de blanco. */
    private val early = java.util.concurrent.ConcurrentLinkedQueue<Triple<Long, String, Int>>()
    private val earlyOn = java.util.concurrent.atomic.AtomicBoolean(false)

    suspend fun run(window: ComposeWindow, navigator: Navigator, repository: DesktopRepository, session: Session, profiles: ProfileStore, trakt: TraktManager? = null) {
        dir.mkdirs()
        // Arranque: % de blanco de la ventana en los primeros segundos (para ver si el punto negro destella al abrir).
        if (runCatching { File(dir, "tour-solo.txt").readText().trim() }.getOrNull()?.startsWith("foro3") == true) {
            earlyOn.set(true)
            val t0 = System.currentTimeMillis()
            Thread {
                val robot = Robot()
                while (earlyOn.get() && System.currentTimeMillis() - t0 < 4_000) {
                    runCatching {
                        val p = window.locationOnScreen
                        val img = robot.createScreenCapture(Rectangle(p.x + 8, p.y + 40, window.width - 16, window.height - 48))
                        var white = 0
                        var n = 0
                        for (y in 0 until img.height step 12) for (x in 0 until img.width step 12) {
                            val c = img.getRGB(x, y)
                            if ((c shr 16 and 255) > 215 && (c shr 8 and 255) > 215 && (c and 255) > 215) white++
                            n++
                        }
                        early += Triple(System.currentTimeMillis() - t0, navigator.current::class.simpleName.orEmpty(), white * 100 / n)
                    }
                    Thread.sleep(25)
                }
            }.apply { isDaemon = true; start() }
        }
        log("perfiles: ${profiles.profiles.size}, sesión: ${session.profile?.name}")
        delay(3_000)
        if (session.profile == null) {
            shot(window, "perfiles")
            return
        }
        // Recorrido corto, solo con lo nuevo (el archivo «tour-solo.txt» con «foro» en la carpeta de capturas).
        when (runCatching { File(dir, "tour-solo.txt").readText().trim() }.getOrNull()) {
            "foro" -> { forum(window, navigator, repository, session, profiles, trakt); return }
            "foro2" -> { forum2(window, navigator, repository); return }
            "foro3", "foro3dx" -> { forum3(window, navigator, repository, session, profiles); return }
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
     * Lo que pidieron en el foro: nota de IMDb y fecha entera en la ficha, fecha de cada temporada, ficha de un actor,
     * Estrenos, el tráiler dentro de Bambi, las etiquetas 4K/HDR al empezar un vídeo, el menú de «Seguir viendo», las
     * listas, Ajustes → Inicio y las listas de Trakt. No cambia nada del perfil.
     */
    private suspend fun forum(window: ComposeWindow, navigator: Navigator, repository: DesktopRepository, session: Session, profiles: ProfileStore, trakt: TraktManager?) {
        delay(5_000)
        shot(window, "inicio")
        val rows = runCatching { repository.homeRows() }.getOrNull().orEmpty()
        val movie = rows.flatMap { it.items }.firstOrNull { it.type == MediaType.MOVIE }
        val show = rows.flatMap { it.items }.firstOrNull { it.type == MediaType.TV }
        if (movie != null) {
            val d = runCatching { repository.details(movie) }.onFailure { log("ficha falló: ${it.message}") }.getOrNull()
            log("ficha «${movie.title}»: estreno ${d?.releaseDate} · TMDB ${d?.item?.rating} · IMDb ${d?.imdbRating} · tráiler ${d?.trailerKey != null} · reparto ${d?.cast?.size} (con id ${d?.cast?.count { it.id != null }})")
            navigator.section(Screen.Home)
            navigator.open(Screen.Details(movie))
            delay(7_000)
            shot(window, "ficha-notas-fecha")
            d?.cast?.firstOrNull { it.id != null }?.let { actor ->
                val p = runCatching { repository.person(actor.id!!) }.onFailure { log("persona falló: ${it.message}") }.getOrNull()
                log("actor ${actor.name}: ${p?.credits?.size} títulos (${p?.credits?.take(5)?.joinToString { it.title }}) · nacimiento ${p?.birthday} · bio ${p?.biography?.length} letras")
                navigator.open(Screen.Person(actor))
                delay(6_000)
                shot(window, "actor")
                navigator.back()
                delay(1_000)
            }
            // Tráiler dentro de Bambi (yt-dlp junto a mpv).
            val config = ConfigStore().current
            log("yt-dlp: ${MpvPlayer.ytDlp(config.mpvPath) ?: "no está junto a mpv"}")
            d?.trailerKey?.let { key ->
                navigator.open(
                    Screen.Player(
                        PlayRequest(
                            DesktopSource("Tráiler", "YouTube", com.shieldstream.tv.model.Quality.UNKNOWN, true, "https://www.youtube.com/watch?v=$key"),
                            d.item, null, "${d.item.title} · Tráiler", trailer = true,
                        ),
                    ),
                )
                delay(15_000)
                log("tráiler: ${navigator.current::class.simpleName} · mpv ${if (PlayerControl.session?.alive == true) "en marcha" else "parado"}")
                shot(window, "trailer")
                PlayerControl.session?.quit()
                delay(3_000)
                log("tras el tráiler: ${navigator.current::class.simpleName}")
            }
            // Una película de verdad: las etiquetas de arriba a la izquierda al empezar.
            runCatching {
                val state = repository.sources(movie, null).last()
                when (val pick = pickPlayable(state.sources, "debrid")) {
                    is Pick.Failed -> log("reproducir película: ${pick.message}")
                    is Pick.Ready -> {
                        log("fuente: ${pick.source.title} → etiquetas ${sourceBadges(pick.source)}")
                        navigator.open(Screen.Player(PlayRequest(pick.source, d?.item ?: movie, null, movie.title, startSec = 300.0)))
                        delay(7_000)
                        shot(window, "reproductor-etiquetas")
                        PlayerControl.session?.quit()
                        delay(3_000)
                        session.profile?.let { p -> profiles.library(p).clear(progressKey(movie.type.name, movie.id, null, null)) }
                    }
                }
            }.onFailure { log("reproducir película falló: ${it.message}") }
        }
        if (show != null) {
            val d = runCatching { repository.details(show) }.getOrNull()
            log("serie «${show.title}»: estreno ${d?.releaseDate} · IMDb ${d?.imdbRating} · temporadas " + d?.seasons?.joinToString { "${it.number}=${it.airDate}" })
            navigator.section(Screen.Home)
            navigator.open(Screen.Details(show))
            delay(7_000)
            shot(window, "serie-temporadas")
        }
        navigator.section(Screen.Releases)
        delay(8_000)
        shot(window, "estrenos")
        navigator.section(Screen.MyList)
        delay(2_000)
        shot(window, "listas")
        SettingsTourHook.tab = SettingsTab.HOME
        navigator.section(Screen.Settings)
        delay(1_500)
        SettingsTourHook.tab = null
        shot(window, "ajustes-inicio")
        SettingsTourHook.tab = SettingsTab.ACCOUNT
        navigator.section(Screen.Home)
        delay(300)
        navigator.section(Screen.Settings)
        delay(1_500)
        SettingsTourHook.tab = null
        shot(window, "ajustes-claves")
        // Listas públicas de Trakt (solo leer).
        if (trakt?.isConfigured == true) {
            runCatching { trakt.searchLists("marvel") }
                .onSuccess { found -> log("listas de Trakt «marvel»: ${found.size} · " + found.take(3).joinToString { "${it.name} (${it.count}) ${it.path}" }) }
                .onFailure { log("listas de Trakt falló: ${it.message}") }
        } else {
            log("listas de Trakt: sin Client ID")
        }
        // «Seguir viendo»: el menú ⋮ al pasar el ratón por la primera tarjeta.
        navigator.section(Screen.Home)
        delay(4_000)
        val library = session.profile?.let { profiles.library(it) }
        log("seguir viendo: ${library?.continueWatching()?.size} · listas: ${library?.lists?.size}")
        shot(window, "inicio-final")
        log("FORO hecho")
    }

    /**
     * Tercera tanda: «Reproducir» desde el Inicio (sin destello blanco: la zona del vídeo sale cuando hay imagen) y
     * Escape después: tiene que quedarse en la ficha, no volver a arrancar solo.
     */
    private suspend fun forum3(window: ComposeWindow, navigator: Navigator, repository: DesktopRepository, session: Session, profiles: ProfileStore) {
        delay(4_000)
        val rows = runCatching { repository.homeRows() }.getOrNull().orEmpty()
        val movie = rows.flatMap { it.items }.firstOrNull { it.type == MediaType.MOVIE } ?: return
        // Grabación rápida: cada ~30 ms, el brillo medio del centro de la ventana y en qué pantalla estaba, para cazar
        // cualquier destello claro al entrar en el reproductor (dura menos de lo que tarda una captura normal).
        val frames = java.util.concurrent.ConcurrentLinkedQueue<Triple<Long, String, Int>>()
        val recording = java.util.concurrent.atomic.AtomicBoolean(true)
        val t0 = System.currentTimeMillis()
        Thread {
            val robot = Robot()
            while (recording.get()) {
                runCatching {
                    // Toda la ventana (sin la barra de título): % de píxeles casi blancos.
                    val p = window.locationOnScreen
                    val r = Rectangle(p.x + 8, p.y + 40, window.width - 16, window.height - 48)
                    val img = robot.createScreenCapture(r)
                    var white = 0
                    var n = 0
                    for (y in 0 until img.height step 12) for (x in 0 until img.width step 12) {
                        val c = img.getRGB(x, y)
                        if ((c shr 16 and 255) > 215 && (c shr 8 and 255) > 215 && (c and 255) > 215) white++
                        n++
                    }
                    frames += Triple(System.currentTimeMillis() - t0, navigator.current::class.simpleName.orEmpty(), white * 100 / n)
                }
                Thread.sleep(25)
            }
        }.apply { isDaemon = true; start() }
        for (round in 1..2) {
            navigator.section(Screen.Home)
            delay(1_000)
            navigator.open(Screen.Details(movie, autoPlay = true))
            var waited = 0
            while (navigator.current !is Screen.Player && waited < 40) { delay(500); waited++ }
            log("ronda $round: reproductor a los ${waited / 2.0} s · ${navigator.current::class.simpleName}")
            delay(800)
            shot(window, "arrancando-$round")
            delay(8_000)
            shot(window, "reproduciendo-$round")
            if (round == 1) {
                // Un salto lejos: se queda cargando y sale el aro del reproductor.
                PlayerControl.session?.setProperty("time-pos", "2400")
                delay(700)
                shot(window, "cargando-salto")
                delay(4_000)
            }
            // Escape (lo mismo que hace la tecla).
            PlayerControl.escape()
            delay(2_000)
            val after = navigator.current::class.simpleName
            delay(10_000)
            log("ronda $round: tras Escape ${after}; 10 s después ${navigator.current::class.simpleName} (esperado Details las dos) · mpv ${if (PlayerControl.session?.alive == true) "en marcha" else "parado"}")
            PlayerControl.session?.quit()
            session.profile?.let { p -> profiles.library(p).clear(progressKey(movie.type.name, movie.id, null, null)) }
        }
        recording.set(false)
        // % de blanco en cada fotograma: los que pasan del 25 % son un destello (las pantallas de Bambi son oscuras).
        earlyOn.set(false)
        log("arranque (4 s): ${early.size} fotogramas · máx blanco ${early.maxOfOrNull { it.third }}% · " + early.filter { it.third > 25 }.joinToString { "${it.first}ms ${it.third}%" })
        val list = frames.toList()
        log("fotogramas grabados: ${list.size} · con más de 25 % blanco: " + list.filter { it.third > 25 }.joinToString { "${it.first}ms ${it.second} ${it.third}%" }.ifEmpty { "ninguno" })
        log("máximo de blanco por pantalla: " + list.groupBy { it.second }.mapValues { (_, v) -> v.maxOf { it.third } })
        var i = 0
        while (i < list.size) {
            if (list[i].second == "Player" && (i == 0 || list[i - 1].second != "Player")) {
                val start = list[i].first
                val window4 = list.drop(i).takeWhile { it.first - start < 4_000 && it.second == "Player" }
                val bright = window4.filter { it.third > 25 }
                log("reproductor desde ${start} ms: ${window4.size} fotogramas · brillo máx ${window4.maxOfOrNull { it.third }} · " +
                    "claros ${bright.size}: " + bright.take(8).joinToString { "+${it.first - start}ms=${it.third}" } +
                    " · brillo por tiempo: " + window4.filter { (it.first - start) < 2_500 }.chunked(4).joinToString(" ") { c -> "${c.first().first - start}:${c.maxOf { it.third }}" })
            }
            // Al salir del reproductor (vuelta a la ficha).
            if (i > 0 && list[i - 1].second == "Player" && list[i].second != "Player") {
                val start = list[i].first
                val after = list.drop(i).takeWhile { it.first - start < 1_000 }
                log("al salir: brillo ${after.joinToString(" ") { "${it.first - start}:${it.third}" }}")
            }
            i++
        }
        navigator.section(Screen.Home)
        log("FORO3 hecho")
    }

    /**
     * Segunda tanda del foro: pantalla completa (que ocupe justo el monitor, sin franjas, y que vuelva a la ventana tal
     * cual, también maximizada y saliendo del vídeo) y que abrir una ficha no congele la interfaz.
     */
    private suspend fun forum2(window: ComposeWindow, navigator: Navigator, repository: DesktopRepository) {
        delay(4_000)
        fun bounds(): String {
            val t = window.graphicsConfiguration.defaultTransform
            val b = window.bounds
            val m = window.graphicsConfiguration.bounds
            fun px(v: Int, s: Double) = Math.round(v * s)
            return "ventana ${px(b.x, t.scaleX)},${px(b.y, t.scaleY)} ${px(b.width, t.scaleX)}x${px(b.height, t.scaleY)} · monitor " +
                "${px(m.x, t.scaleX)},${px(m.y, t.scaleY)} ${px(m.width, t.scaleX)}x${px(m.height, t.scaleY)} · escala ${t.scaleX}"
        }
        fun covers(): Boolean {
            val b = window.bounds
            val m = window.graphicsConfiguration.bounds
            return b.x == m.x && b.y == m.y && b.width == m.width && b.height == m.height
        }
        log("antes: ${bounds()} · decorada ${window.isUndecorated.not()} · extendedState ${window.extendedState}")
        val before = window.bounds
        for (round in 1..3) {
            PlayerControl.applyFullscreen(true)
            delay(1_200)
            log("pantalla completa $round: ${PlayerControl.isFullscreen()} · ocupa el monitor ${covers()} · ${bounds()}")
            if (round == 1) shot(window, "pantalla-completa")
            PlayerControl.applyFullscreen(false)
            delay(1_200)
            log("vuelta $round: ${PlayerControl.isFullscreen()} · como antes ${window.bounds == before} · ${bounds()}")
        }
        shot(window, "vuelta-a-ventana")
        // Maximizada → pantalla completa → vuelve maximizada.
        window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
        delay(1_200)
        val maxed = window.bounds
        PlayerControl.applyFullscreen(true)
        delay(1_200)
        log("desde maximizada: ocupa el monitor ${covers()} · ${bounds()}")
        PlayerControl.applyFullscreen(false)
        delay(1_200)
        log("vuelve maximizada: ${window.extendedState and java.awt.Frame.MAXIMIZED_BOTH == java.awt.Frame.MAXIMIZED_BOTH} · igual ${window.bounds == maxed} · ${bounds()}")
        window.extendedState = java.awt.Frame.NORMAL
        delay(1_000)
        // Ficha sin congelarse: se mide cuánto tarda la interfaz en responder mientras se abre y busca fuentes.
        val rows = runCatching { repository.homeRows() }.getOrNull().orEmpty()
        for (item in listOfNotNull(rows.flatMap { it.items }.firstOrNull { it.type == MediaType.MOVIE }, rows.flatMap { it.items }.firstOrNull { it.type == MediaType.TV })) {
            val worst = java.util.concurrent.atomic.AtomicLong(0)
            val probe = Thread {
                repeat(240) {
                    val t0 = System.nanoTime()
                    runCatching { javax.swing.SwingUtilities.invokeAndWait {} }
                    worst.set(maxOf(worst.get(), (System.nanoTime() - t0) / 1_000_000))
                    Thread.sleep(40)
                }
            }.apply { isDaemon = true; start() }
            DetailsTourHook.openSources = true
            navigator.section(Screen.Home)
            navigator.open(Screen.Details(item))
            delay(1_000)
            DetailsTourHook.openSources = false
            delay(9_000)
            log("ficha «${item.title}» con fuentes: la interfaz tardó como mucho ${worst.get()} ms en responder")
            shot(window, "ficha-fuentes-" + item.type.name.lowercase())
            probe.interrupt()
        }
        navigator.section(Screen.Home)
        log("FORO2 hecho")
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
        val robot = Robot()
        fun grab(): BufferedImage {
            val bounds = Rectangle(window.locationOnScreen, window.size)
            val capture = robot.createMultiResolutionScreenCapture(bounds)
            return capture.resolutionVariants.maxByOrNull { it.getWidth(null) } as BufferedImage
        }
        fun black(img: BufferedImage) = (0 until 40).all { i ->
            img.getRGB(img.width * (i % 8 + 1) / 10, img.height * (i / 8 + 1) / 7) and 0xFFFFFF == 0
        }
        var image = grab()
        // Si la pantalla se ha apagado (salvapantallas), la captura sale negra: se mueve el ratón y se repite.
        var tries = 0
        while (black(image) && tries < 3) {
            tries++
            val p = java.awt.MouseInfo.getPointerInfo()?.location
            if (p != null) { robot.mouseMove(p.x + 1, p.y); robot.mouseMove(p.x, p.y) }
            robot.keyPress(java.awt.event.KeyEvent.VK_SHIFT)
            robot.keyRelease(java.awt.event.KeyEvent.VK_SHIFT)
            robot.delay(1_500)
            image = grab()
        }
        ImageIO.write(image, "png", file)
        log("captura ${file.name}" + if (tries > 0) " (negra; reintentos $tries, ${if (black(image)) "sigue negra" else "bien"})" else "")
    }

    fun log(text: String) {
        println(text)
        File(dir, "tour.txt").appendText(text + "\n")
    }
}
