package com.shieldstream.tv.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Quality
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Capturas de las pantallas sin ventana ni red, con datos de prueba (tarea :desktop:renderShots). */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/shots" }).apply { mkdirs() }
    val config = ConfigStore(File(out, "config.json")).apply {
        save(DesktopConfig(tmdbKey = "demo", debrid = DebridService.REAL_DEBRID.name, debridKey = "demo"))
    }
    val repository = FakeRepository()
    // Perfiles de prueba, como recién migrados (Principal) y con los de la Fase 1: PIN, infantil y temas.
    val profiles = ProfileStore(File(out, "perfiles").apply { deleteRecursively() }).apply {
        save(profiles.single().copy(name = "Gonza", image = "${BundledImages.ANDROID_ASSET}profile_icons_bambi/05_palomitas.webp").withPin("1234"))
        save(DesktopProfile(name = "Peque", avatar = 7, kids = true, theme = com.shieldstream.tv.model.ProfileTheme.ROSE.name,
            image = "${BundledImages.ANDROID_ASSET}profile_icons_bambi/06_jugando.webp"))
        save(DesktopProfile(name = "Invitado", avatar = 1, theme = com.shieldstream.tv.model.ProfileTheme.OCEAN.name))
    }
    val profile = profiles.profiles.first()
    profiles.library(profile).apply {
        if (progress.isEmpty()) {
            record(SavedProgress(SavedItem.of(FakeRepository.movie), positionSec = 2400.0, durationSec = 7680.0, updatedAt = 2))
            record(SavedProgress(SavedItem.of(FakeRepository.show), season = 1, episode = 3, positionSec = 900.0, durationSec = 3100.0, updatedAt = 1))
        }
    }
    val shots = listOf(
        "perfiles" to null,
        "home" to Screen.Home,
        "movie" to Screen.Details(FakeRepository.movie),
        "series" to Screen.Details(FakeRepository.show),
        "milista" to Screen.MyList,
        "settings" to Screen.Settings,
    )
    // La intro: unos fotogramas (a 60 por segundo, como en pantalla) en distintos momentos.
    for ((name, ms) in listOf("intro-1" to 900L, "intro-2" to 2_200L, "intro-3" to 3_600L, "intro-4" to 6_600L)) {
        val scene = ImageComposeScene(width = 1440, height = 880, density = Density(1f)) {
            IntroScreen(onStartPreload = {}, awaitReady = { true }, onFinished = {}, fadeFromBlack = false)
        }
        var t = 0L
        while (t < ms * 1_000_000L) {
            scene.render(t)
            t += 16_666_667L
        }
        File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("captura: $name.png")
    }
    // Fase 1: inicio con el tema de otro perfil, editor de perfil y PIN.
    fun shot(name: String, height: Int = 880, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(width = 1440, height = height, density = Density(1f)) { BambiTheme { content() } }
        var time = 0L
        repeat(12) { scene.render(time); time += 250_000_000L }
        File(out, "$name.png").writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("captura: $name.png")
    }
    val ocean = profiles.profiles.first { it.name == "Invitado" }
    shot("home-oceano") { BambiApp(repository, config, profiles, Navigator(Screen.Home), Session(ocean)) }
    shot("perfil-editor") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            ProfileEditor(profiles.profiles.first { it.kids }, isNew = false, canDelete = true, onSave = {}, onDelete = {}, onCancel = {})
        }
    }
    shot("perfil-pin") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            PinDialog("Gonza", "Introduce el PIN para entrar.", check = { false }, onSuccess = {}, onCancel = {})
        }
    }
    // Lo del foro: listas, Inicio a medida, ficha de un actor, Estrenos y Ajustes → Inicio.
    profiles.library(profile).apply {
        if (lists.isEmpty()) {
            val family = createList("Para ver en familia", first = FakeRepository.movie.copy(id = "f1", title = "Familia 1"))
            (2..6).forEach { toggleInList(family.id, FakeRepository.movie.copy(id = "f$it", title = "Familia $it")) }
            saveTraktList("/lists/1", "Lo mejor del terror", "juan", (1..7).map { FakeRepository.movie.copy(id = "tr$it", title = "Terror $it") })
        }
        // Un episodio de la temporada 2 ya visto: Estrenos cuenta solo los que faltan.
        record(SavedProgress(SavedItem.of(FakeRepository.show), season = 2, episode = 1, positionSec = 3000.0, durationSec = 3000.0, updatedAt = 3, finished = true))
    }
    val shaped = profiles.profiles.first().let { p ->
        val lists = profiles.library(p).lists
        p.copy(homeLayout = HomeLayout(billboard = HomeLayout.NONE, hidden = setOf(HomeLayout.PLAY_SOMETHING, HomeLayout.TRAKT)).move(HomeLayout.listRow(lists.first().id), -1, HomeLayout.available(listOf("Películas en tendencia"), lists)))
    }
    profiles.save(shaped)
    val forum = listOf(
        "foro-ficha" to Screen.Details(FakeRepository.movie),
        "foro-serie" to Screen.Details(FakeRepository.show),
        "foro-actor" to Screen.Person(CastMember("Actriz o actor 1", "Personaje 1", null, id = 1)),
        "foro-estrenos" to Screen.Releases,
        "foro-listas" to Screen.MyList,
        "foro-inicio-a-medida" to Screen.Home,
    )
    for ((name, screen) in forum) {
        val navigator = Navigator(Screen.Home).apply { if (screen != Screen.Home) open(screen) }
        shot(name) { BambiApp(repository, config, profiles, navigator, Session(profiles.profiles.first())) }
    }
    // Panel de fuentes rediseñado.
    DetailsTourHook.openSources = true
    shot("foro-fuentes") { BambiApp(repository, config, profiles, Navigator(Screen.Home).apply { open(Screen.Details(FakeRepository.movie)) }, Session(profiles.profiles.first())) }
    DetailsTourHook.openSources = false
    for ((name, tab) in listOf("foro-ajustes-inicio" to SettingsTab.HOME, "foro-ajustes-claves" to SettingsTab.ACCOUNT)) {
        SettingsTourHook.tab = tab
        shot(name, height = 1300) { BambiApp(repository, config, profiles, Navigator(Screen.Settings), Session(profiles.profiles.first())) }
        SettingsTourHook.tab = null
    }
    profiles.save(profiles.profiles.first().copy(homeLayout = HomeLayout()))
    for ((name, screen) in shots) {
        val navigator = Navigator(Screen.Home).apply { if (screen != null && screen != Screen.Home) open(screen) }
        val session = Session(if (screen == null) null else profile)
        // Ajustes, más alto: para que se vea también la parte de mpv.
        val scene = ImageComposeScene(width = 1440, height = if (name == "settings") 1400 else 880, density = Density(1f)) {
            BambiApp(repository, config, profiles, navigator, session)
        }
        var time = 0L
        repeat(12) {
            scene.render(time)
            time += 250_000_000L
        }
        val image = scene.render(time)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("captura: $name.png")
    }
}

private class FakeRepository : DesktopRepository {
    override suspend fun homeRows(catalog: Catalog, kids: Boolean) = listOf(
        HomeRow("Películas en tendencia", (1..9).map { movie.copy(id = "m$it", title = "Película de prueba $it") }),
        HomeRow("Series en tendencia", (1..9).map { show.copy(id = "s$it", title = "Serie de prueba $it") }),
        HomeRow("Películas populares", (1..9).map { movie.copy(id = "p$it", title = "Popular $it") }),
    )

    override suspend fun search(query: String, kids: Boolean) = emptyList<CatalogItem>()

    override suspend fun details(item: CatalogItem) = TitleDetails(
        item = item,
        tagline = "Una frase de lema de ejemplo.",
        runtimeMinutes = if (item.type == MediaType.MOVIE) 128 else 52,
        genres = listOf("Drama", "Ciencia ficción"),
        seasons = if (item.type == MediaType.TV) (1..3).map { SeasonInfo(it, "Temporada $it", 8, "202$it-0${it + 2}-1$it") } else emptyList(),
        ageRating = "13",
        cast = (1..8).map { CastMember("Actriz o actor $it", "Personaje $it", null, id = it) },
        releaseDate = if (item.type == MediaType.MOVIE) "2026-03-14" else "2021-03-11",
        imdbRating = "8.1",
        trailerKey = "demo",
        saga = if (item.type == MediaType.MOVIE) HomeRow("Saga de prueba", (1..4).map { movie.copy(id = "saga$it", title = "Parte $it") }) else null,
    )

    override suspend fun person(id: Int) = PersonDetails(
        id = id,
        name = "Actriz o actor $id",
        photoUrl = null,
        biography = "Biografía de ejemplo: nació en una ciudad cualquiera y empezó en el teatro antes de dar el salto al cine, " +
            "donde ha trabajado en dramas, comedias y alguna saga de ciencia ficción.",
        knownFor = "Interpretación",
        birthday = "1980-05-21",
        placeOfBirth = "Madrid, España",
        credits = (1..14).map { if (it % 3 == 0) show.copy(id = "ps$it", title = "Serie $it") else movie.copy(id = "pm$it", title = "Película $it") },
    )

    override suspend fun airingEpisodes(show: CatalogItem): List<ReleaseEpisode> {
        val today = java.time.LocalDate.now()
        return (1..5).map { ReleaseEpisode(show, 2, it, "Episodio $it", today.minusDays(14L - it * 4)) }
    }

    override suspend fun top10(catalog: Catalog, kids: Boolean) = (1..10).map { movie.copy(id = "t$it", title = "Top $it") }

    override suspend fun episodes(item: CatalogItem, season: Int) = (1..6).map {
        Episode(season = season, number = it, title = "Episodio $it", overview = "Resumen del episodio $it de ejemplo, con un par de frases para ver cómo se corta el texto.", stillUrl = null, runtimeMinutes = 52, airDate = "2026-0$season-1$it")
    }

    override fun sources(item: CatalogItem, episode: Episode?) = kotlinx.coroutines.flow.flowOf(SourcesState(listOf(
        DesktopSource("Pelicula.De.Prueba.2026.2160p.WEB-DL.DV.HDR.DDP5.1.Atmos.H.265", readableDetail("👤 120 💾 18.4 GB ⚙️ ThePirateBay"), Quality.UHD_2160, true, "https://example.com/1", "The Pirate Bay"),
        DesktopSource("Pelicula.De.Prueba.2026.1080p.WEB-DL.Castellano.AC3.5.1", readableDetail("👤 45 💾 4.1 GB ⚙️ MejorTorrent\n🇪🇸 / 🇬🇧"), Quality.FHD_1080, true, "https://example.com/2", "En castellano", spanish = true),
        DesktopSource("Pelicula.De.Prueba.2026.1080p.BluRay.x264", readableDetail("👤 12 💾 9.8 GB ⚙️ 1337x"), Quality.FHD_1080, false, "https://example.com/3", "Torrentio"),
        DesktopSource("Pelicula.De.Prueba.2026.720p.WEBRip", readableDetail("👤 3 💾 1.2 GB ⚙️ YTS"), Quality.HD_720, false, "https://example.com/4", "Torrentio"),
    ), pending = setOf("Comet"), unavailable = mapOf("Addon de Stremio" to "Sin fuentes para esto")))

    companion object {
        val movie = CatalogItem(
            id = "demo-movie",
            imdbId = "tt0000001",
            type = MediaType.MOVIE,
            title = "Película de prueba",
            overview = "Sinopsis de ejemplo para la ficha de escritorio: una historia larga para ver cómo se reparte el texto en varias líneas sobre el fondo.",
            posterUrl = null,
            backdropUrl = null,
            year = 2026,
            rating = 7.8f,
        )
        val show = movie.copy(id = "demo-show", type = MediaType.TV, title = "Serie de prueba", year = 2025, rating = 8.4f)
    }
}
