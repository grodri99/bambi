package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.network.ApiConfig
import com.shieldstream.tv.network.ApiFactory
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.network.tmdb.TmdbImages
import com.shieldstream.tv.network.tmdb.toCatalogItem
import com.shieldstream.tv.network.tmdb.toCatalogItemOrNull
import com.shieldstream.tv.data.repository.streams.StreamSearch
import com.shieldstream.tv.data.repository.uriEncode
import com.shieldstream.tv.data.repository.debridErrorMessage
import com.shieldstream.tv.data.repository.isDebridError
import com.shieldstream.tv.data.repository.torrentioConfig
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.model.HdrType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Catálogo de TMDB y fuentes de Torrentio configurado con el servicio debrid del usuario: Torrentio devuelve enlaces
 * /resolve/ que el servicio convierte en el vídeo al abrirlos, así que vale cualquier debrid que Torrentio soporte.
 */
class TmdbTorrentioRepository(
    private val config: ConfigStore,
    client: OkHttpClient = DesktopHttp.client,
    private val broken: BrokenSources = BrokenSources.default,
) : DesktopRepository {
    private val prioritize = com.shieldstream.tv.domain.PrioritizeSourcesUseCase()
    private val json = ApiFactory.json()
    private val tmdb = ApiFactory.retrofit(ApiConfig.TMDB_BASE_URL, ApiFactory.tmdbClient(client), json).create(TmdbApi::class.java)
    private val torrentio = ApiFactory.retrofit(ApiConfig.TORRENTIO_BASE_URL, client, json).create(TorrentioApi::class.java)

    private fun key(): String = config.current.tmdbKey.trim().ifBlank { error(NO_TMDB_KEY) }

    private val releases = ReleaseGate(fetch = { id -> tmdb.movieReleaseDates(id, key()) })

    /** «Ocultar lo que aún no se puede ver en España», si está activado (solo en el Inicio y las categorías). */
    private suspend fun released(items: List<CatalogItem>): List<CatalogItem> =
        if (config.current.hideUnreleased) releases.filter(items) else items

    private fun row(title: String, fetch: suspend () -> List<CatalogItem>) = title to fetch

    override suspend fun homeRows(catalog: Catalog, kids: Boolean): List<HomeRow> = coroutineScope {
        val k = key()
        if (kids) return@coroutineScope kidsRows(k, catalog)
        val movies: List<Pair<String, suspend () -> List<CatalogItem>>> = listOf(
            row("Películas en tendencia") { tmdb.trendingMovies(k).results.map { it.toCatalogItem() } },
            row("Populares en Bambi") { tmdb.popularMovies(k).results.map { it.toCatalogItem() } },
            row("En cines") { tmdb.nowPlayingMovies(k).results.map { it.toCatalogItem() } },
            row("Películas mejor valoradas") { tmdb.topRatedMovies(k).results.map { it.toCatalogItem() } },
            row("Acción") { tmdb.discoverMovies(k, "28").results.map { it.toCatalogItem() } },
            row("Comedias") { tmdb.discoverMovies(k, "35").results.map { it.toCatalogItem() } },
            row("Terror") { tmdb.discoverMovies(k, "27").results.map { it.toCatalogItem() } },
            row("Ciencia ficción") { tmdb.discoverMovies(k, "878").results.map { it.toCatalogItem() } },
            row("Animación") { tmdb.discoverMovies(k, "16").results.map { it.toCatalogItem() } },
        )
        val shows: List<Pair<String, suspend () -> List<CatalogItem>>> = listOf(
            row("Series en tendencia") { tmdb.trendingTv(k).results.map { it.toCatalogItem() } },
            row("Series populares") { tmdb.popularTv(k).results.map { it.toCatalogItem() } },
            row("Series mejor valoradas") { tmdb.topRatedTv(k).results.map { it.toCatalogItem() } },
            row("Dramas") { tmdb.discoverTv(k, "18").results.map { it.toCatalogItem() } },
            row("Comedias") { tmdb.discoverTv(k, "35").results.map { it.toCatalogItem() } },
            row("Crimen") { tmdb.discoverTv(k, "80").results.map { it.toCatalogItem() } },
            row("Ciencia ficción y fantasía") { tmdb.discoverTv(k, "10765").results.map { it.toCatalogItem() } },
            row("Animación") { tmdb.discoverTv(k, "16").results.map { it.toCatalogItem() } },
        )
        val sources = when (catalog) {
            Catalog.MOVIES -> movies
            Catalog.SHOWS -> shows
            // Inicio: tendencias de las dos y luego alternando, como en la tele.
            Catalog.ALL -> listOf(movies[0], shows[0], movies[1], shows[1], movies[3], shows[2], movies[4], shows[3])
        }
        sources.map { (title, fetch) -> async { runCatching { HomeRow(title, released(fetch())) }.getOrNull() } }
            .awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
            .ifEmpty { error("No se pudo cargar el catálogo de TMDB. Revisa la clave en Ajustes y la conexión.") }
    }

    override suspend fun search(query: String, kids: Boolean): List<CatalogItem> =
        if (query.isBlank()) emptyList()
        else tmdb.search(key(), query.trim()).results
            .filter { !kids || it.genre_ids.any { g -> g in KIDS_GENRES } }
            .mapNotNull { it.toCatalogItemOrNull() }

    private val introDb = com.shieldstream.tv.data.skip.IntroDbRepository(DesktopHttp.client, ApiFactory.json())

    override suspend fun skipSegments(item: CatalogItem, episode: Episode?): com.shieldstream.tv.domain.SkipSegments? {
        val imdb = item.imdbId ?: return null
        val ep = episode ?: return null
        return runCatching { introDb.episode(imdb, ep.season, ep.number) }.getOrNull()
    }

    override suspend fun airingEpisodes(show: CatalogItem): List<ReleaseEpisode> {
        val id = show.id.toIntOrNull() ?: return emptyList()
        val d = tmdb.tvDetails(id, key(), append = "")
        val seasons = listOfNotNull(d.last_episode_to_air?.season_number, d.next_episode_to_air?.season_number).distinct()
        val item = show.copy(title = show.title.ifBlank { d.name }, posterUrl = show.posterUrl ?: d.poster_path?.let(TmdbImages::poster))
        return seasons.flatMap { s ->
            runCatching { tmdb.tvSeason(id, s, key()).episodes }.getOrDefault(emptyList()).mapNotNull { e ->
                val date = e.air_date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                ReleaseEpisode(item, e.season_number.takeIf { it > 0 } ?: s, e.episode_number, e.name, date)
            }
        }
    }

    override suspend fun addonInfo(manifestUrl: String): AddonInfo? {
        val base = com.shieldstream.tv.domain.StremioAddons.baseUrl(manifestUrl) ?: return null
        val body = torrentio.raw("$base/manifest.json").use { it.string() }
        val m = kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return null
        fun str(o: kotlinx.serialization.json.JsonObject?, k: String) = (o?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        val hints = m["behaviorHints"] as? kotlinx.serialization.json.JsonObject
        val required = (hints?.get("configurationRequired") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
        val resources = (m["resources"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { r ->
            (r as? kotlinx.serialization.json.JsonPrimitive)?.content ?: str(r as? kotlinx.serialization.json.JsonObject, "name").orEmpty()
        }
        val logo = str(m, "logo")?.let { runCatching { java.net.URI("$base/").resolve(it).toString() }.getOrNull() }?.takeIf { it.startsWith("http") }
        return AddonInfo(str(m, "name")?.trim()?.ifBlank { null }, logo, needsConfiguration = required, hasStreams = "stream" in resources)
    }

    override suspend fun testAddon(manifestUrl: String): Int {
        val base = com.shieldstream.tv.domain.StremioAddons.baseUrl(manifestUrl) ?: return 0
        return torrentio.streamsAt("$base/stream/movie/tt0111161.json").streams.size
    }

    override suspend fun searchOutsideKids(query: String): List<CatalogItem> =
        if (query.isBlank()) emptyList()
        else tmdb.search(key(), query.trim()).results
            .filter { it.genre_ids.none { g -> g in KIDS_GENRES } }
            .mapNotNull { it.toCatalogItemOrNull() }
            .filter { it.posterUrl != null }

    /**
     * Perfil infantil, con las mismas consultas que la tele (CatalogRepositoryImpl): animación y familia hasta PG en
     * películas, y series infantiles y de dibujos.
     */
    private suspend fun kidsRows(k: String, catalog: Catalog): List<HomeRow> = coroutineScope {
        fun movies(genres: String, extra: Map<String, String> = emptyMap()): suspend () -> List<CatalogItem> = {
            tmdb.discoverMovieQuery(
                k,
                mapOf(
                    "with_genres" to genres,
                    "certification_country" to "US",
                    "certification.lte" to "PG",
                    "include_adult" to "false",
                    "sort_by" to "popularity.desc",
                ) + extra,
            ).results.map { it.toCatalogItem() }
        }
        fun shows(genres: String, extra: Map<String, String> = emptyMap()): suspend () -> List<CatalogItem> = {
            tmdb.discoverTvQuery(k, mapOf("with_genres" to genres, "include_adult" to "false", "sort_by" to "popularity.desc") + extra)
                .results.map { it.toCatalogItem() }
        }
        val top = mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300")
        val movieRows = listOf(
            row("Películas infantiles", movies("16|10751")),
            row("Animación", movies("16")),
            row("Para toda la familia", movies("10751", mapOf("certification.lte" to "G"))),
            row("Las mejor valoradas", movies("16|10751", top)),
        )
        val showRows = listOf(
            row("Series infantiles", shows("10762")),
            row("Dibujos animados", shows("10762,16")),
            row("Más series", shows("10762", mapOf("page" to "2"))),
        )
        val sources = when (catalog) {
            Catalog.MOVIES -> movieRows
            Catalog.SHOWS -> showRows
            Catalog.ALL -> listOf(movieRows[0], showRows[0], movieRows[1], showRows[1], movieRows[2], movieRows[3])
        }
        sources.map { (title, fetch) -> async { runCatching { HomeRow(title, released(fetch())) }.getOrNull() } }
            .awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
            .ifEmpty { error("No se pudo cargar el catálogo de TMDB. Revisa la clave en Ajustes y la conexión.") }
    }

    override suspend fun details(item: CatalogItem): TitleDetails = coroutineScope {
        val id = item.id.toIntOrNull() ?: return@coroutineScope TitleDetails(item)
        val language = if (Lang.isEnglish) "en" else "es"
        if (item.type == MediaType.MOVIE) {
            val d = tmdb.movieDetails(id, key(), append = "images,translations,release_dates,credits,videos")
            val saga = d.belongs_to_collection?.let { ref ->
                async {
                    runCatching {
                        val parts = tmdb.collection(ref.id, key()).parts
                            .mapNotNull { it.toCatalogItemOrNull() }
                            .sortedBy { it.year ?: Int.MAX_VALUE }
                        HomeRow(ref.name, parts).takeIf { parts.size > 1 }
                    }.getOrNull()
                }
            }
            val imdb = (d.imdb_id ?: item.imdbId)?.let { async { imdbRating("movie", it) } }
            TitleDetails(
                // Lo que llega solo con el id (de Trakt) se completa aquí: título, póster, año y nota.
                item = item.copy(
                    title = item.title.ifBlank { d.title },
                    imdbId = d.imdb_id ?: item.imdbId,
                    overview = d.overview.ifBlank { item.overview },
                    posterUrl = item.posterUrl ?: d.poster_path?.let(TmdbImages::poster),
                    backdropUrl = d.backdrop_path?.let(TmdbImages::backdrop) ?: item.backdropUrl,
                    year = item.year ?: d.release_date?.take(4)?.toIntOrNull(),
                    rating = item.rating ?: d.vote_average.takeIf { it > 0 }?.toFloat(),
                ),
                logoUrl = TmdbImages.logo(d.images, language),
                tagline = d.tagline?.takeIf { it.isNotBlank() },
                runtimeMinutes = d.runtime,
                genres = d.genres.map { it.name },
                ageRating = movieAge(d.release_dates),
                cast = cast(d.credits),
                trailerKey = trailer(d.videos),
                saga = saga?.await(),
                releaseDate = d.release_date?.takeIf { it.isNotBlank() },
                imdbRating = imdb?.await(),
            )
        } else {
            val d = tmdb.tvDetails(id, key(), append = "images,external_ids,translations,content_ratings,aggregate_credits,videos")
            val imdb = (d.external_ids?.imdb_id ?: item.imdbId)?.let { async { imdbRating("series", it) } }
            TitleDetails(
                item = item.copy(
                    title = item.title.ifBlank { d.name },
                    imdbId = d.external_ids?.imdb_id ?: item.imdbId,
                    overview = d.overview.ifBlank { item.overview },
                    posterUrl = item.posterUrl ?: d.poster_path?.let(TmdbImages::poster),
                    backdropUrl = d.backdrop_path?.let(TmdbImages::backdrop) ?: item.backdropUrl,
                    year = item.year ?: d.first_air_date?.take(4)?.toIntOrNull(),
                    rating = item.rating ?: d.vote_average.takeIf { it > 0 }?.toFloat(),
                ),
                logoUrl = TmdbImages.logo(d.images, language),
                tagline = d.tagline?.takeIf { it.isNotBlank() },
                runtimeMinutes = d.episode_run_time.firstOrNull(),
                genres = d.genres.map { TvGenres.localized(it.name) },
                seasons = d.seasons.filter { it.season_number > 0 && it.episode_count > 0 }
                    .map { SeasonInfo(it.season_number, it.name, it.episode_count, it.air_date?.takeIf { d -> d.isNotBlank() }) },
                ageRating = d.content_ratings?.results?.let { list ->
                    (list.firstOrNull { it.iso_3166_1 == "ES" } ?: list.firstOrNull { it.iso_3166_1 == "US" })?.rating?.takeIf { it.isNotBlank() }
                },
                cast = cast(d.aggregate_credits ?: d.credits),
                trailerKey = trailer(d.videos),
                releaseDate = d.first_air_date?.takeIf { it.isNotBlank() },
                imdbRating = imdb?.await(),
            )
        }
    }

    private fun movieAge(dates: com.shieldstream.tv.network.dto.TmdbReleaseDatesDto?): String? {
        val list = dates?.results.orEmpty()
        for (country in listOf("ES", "US")) {
            list.firstOrNull { it.iso_3166_1 == country }?.release_dates
                ?.map { it.certification.trim() }?.firstOrNull { it.isNotEmpty() }
                ?.let { return it }
        }
        return null
    }

    private fun cast(credits: com.shieldstream.tv.network.dto.TmdbCreditsDto?): List<CastMember> =
        credits?.cast.orEmpty().sortedBy { it.order }.take(20).map { c ->
            CastMember(
                c.name,
                c.character?.takeIf { it.isNotBlank() } ?: c.roles.firstOrNull()?.character,
                c.profile_path?.let(TmdbImages::profile),
                c.id.takeIf { it > 0 },
            )
        }

    /**
     * Nota de IMDb desde Cinemeta (el catálogo de Stremio, sin clave). Si tarda o falla, la ficha sale sin ella: no
     * se espera más de unos segundos.
     */
    private suspend fun imdbRating(type: String, imdbId: String): String? = runCatching {
        kotlinx.coroutines.withTimeout(4_000) {
            val body = torrentio.raw("https://v3-cinemeta.strem.io/meta/$type/$imdbId.json").use { it.string() }
            val meta = (kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject)
                ?.get("meta") as? kotlinx.serialization.json.JsonObject
            (meta?.get("imdbRating") as? kotlinx.serialization.json.JsonPrimitive)?.content
        }
    }.getOrNull()?.trim()?.takeIf { it.toDoubleOrNull()?.let { r -> r > 0 } == true }

    override suspend fun person(id: Int): PersonDetails {
        val p = tmdb.person(id, key())
        val credits = p.combined_credits
        // Lo que ha hecho como actor y, para directores y guionistas, lo que ha dirigido o escrito.
        val talk = setOf(10763, 10767) // noticias y programas de entrevistas: sale de invitado, no es su filmografía
        val acted = credits?.cast.orEmpty().filter { r -> r.genre_ids.none { it in talk } }.mapNotNull { r -> r.toCatalogItemOrNull()?.let { it to (r.vote_count to r.popularity) } }
        val made = credits?.crew.orEmpty()
            .filter { it.job in setOf("Director", "Creator", "Screenplay", "Writer", "Novel") && it.genre_ids.none { g -> g in talk } }
            .mapNotNull { c ->
                val type = when (c.media_type) { "movie" -> MediaType.MOVIE; "tv" -> MediaType.TV; else -> return@mapNotNull null }
                CatalogItem(
                    id = c.id.toString(),
                    imdbId = null,
                    type = type,
                    title = c.title ?: c.name ?: return@mapNotNull null,
                    overview = c.overview,
                    posterUrl = c.poster_path?.let(TmdbImages::poster),
                    backdropUrl = c.backdrop_path?.let(TmdbImages::backdrop),
                    year = (c.release_date ?: c.first_air_date)?.take(4)?.toIntOrNull(),
                    rating = c.vote_average.takeIf { it > 0 }?.toFloat(),
                ) to (c.vote_count to c.popularity)
            }
        val directing = p.known_for_department == "Directing" || p.known_for_department == "Writing"
        val all = (if (directing) made + acted else acted + made)
            .filter { it.first.posterUrl != null }
            // Lo más conocido primero (votos), y no los programas de entrevistas donde sale de invitado.
            .sortedByDescending { (_, score) -> score.first * 10.0 + score.second }
            .map { it.first }
            .distinctBy { it.key }
        return PersonDetails(
            id = p.id,
            name = p.name,
            photoUrl = p.profile_path?.let(TmdbImages::profile),
            biography = p.biography.trim(),
            knownFor = when (p.known_for_department) {
                "Acting" -> "Interpretación"
                "Directing" -> "Dirección"
                "Writing" -> "Guion"
                "Production" -> "Producción"
                null, "" -> null
                else -> p.known_for_department
            },
            birthday = p.birthday?.takeIf { it.isNotBlank() },
            placeOfBirth = p.place_of_birth?.takeIf { it.isNotBlank() },
            credits = all,
        )
    }

    /** Tráiler de YouTube: oficial y en el idioma de la app primero, como en la tele. */
    private fun trailer(videos: com.shieldstream.tv.network.dto.TmdbVideosDto?): String? {
        val list = videos?.results.orEmpty().filter { it.site == "YouTube" && it.key.isNotBlank() }
        val lang = if (Lang.isEnglish) "en" else "es"
        return list.sortedWith(
            compareByDescending<com.shieldstream.tv.network.dto.TmdbVideoDto> { it.type == "Trailer" }
                .thenByDescending { it.iso_639_1 == lang }
                .thenByDescending { it.official },
        ).firstOrNull()?.key
    }

    override suspend fun top10(catalog: Catalog, kids: Boolean): List<CatalogItem> {
        val k = key()
        if (kids) return emptyList()
        val items = when (catalog) {
            Catalog.MOVIES -> tmdb.trendingMovies(k).results.map { it.toCatalogItem() }
            Catalog.SHOWS -> tmdb.trendingTv(k).results.map { it.toCatalogItem() }
            Catalog.ALL -> tmdb.trendingAll(k).results.mapNotNull { it.toCatalogItemOrNull() }
        }
        return released(items.filter { it.posterUrl != null }.take(20)).take(10)
    }

    /**
     * Categorías como en la tele (HomeExtrasSource): cada una con su género, palabra clave o país de TMDB; la fila de
     * populares y la de mejor valoradas.
     */
    override suspend fun categoryRows(category: com.shieldstream.tv.model.Category, catalog: Catalog): List<HomeRow> = coroutineScope {
        val k = key()
        val spec = CategorySpecs.of(category)
        val movie = spec.movie.takeIf { catalog != Catalog.SHOWS }
        val tv = spec.tv.takeIf { catalog != Catalog.MOVIES }
        suspend fun keywordId(text: String): String? =
            runCatching { tmdb.searchKeyword(k, text).results.firstOrNull()?.id?.toString() }.getOrNull()
        suspend fun params(q: CategorySpecs.Query, sort: Map<String, String>): Map<String, String>? {
            val base = HashMap<String, String>(sort)
            q.genres?.let { base["with_genres"] = it }
            q.country?.let { base["with_origin_country"] = it }
            q.language?.let { base["with_original_language"] = it }
            q.keyword?.let { base["with_keywords"] = keywordId(it) ?: return null }
            return base
        }
        suspend fun movies(sort: Map<String, String>) = movie?.let { q ->
            params(q, sort)?.let { tmdb.discoverMovieQuery(k, it).results.map { r -> r.toCatalogItem() } }
        }.orEmpty()
        suspend fun shows(sort: Map<String, String>) = tv?.let { q ->
            params(q, sort)?.let { tmdb.discoverTvQuery(k, it).results.map { r -> r.toCatalogItem() } }
        }.orEmpty()
        val popular = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "50")
        val acclaimed = mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300")
        val name = CategorySpecs.title(category)
        listOf(
            "$name: lo más popular" to suspend { mix(movies(popular), shows(popular)) },
            "$name: lo mejor valorado" to suspend { mix(movies(acclaimed), shows(acclaimed)) },
        ).map { (title, load) -> async { runCatching { HomeRow(title, released(load().filter { it.posterUrl != null }.distinctBy { it.key })) }.getOrNull() } }
            .awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }

    private fun mix(a: List<CatalogItem>, b: List<CatalogItem>): List<CatalogItem> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

    override suspend fun surprise(kids: Boolean): CatalogItem? {
        val k = key()
        val page = (1..5).random()
        val list = if (kids) {
            tmdb.discoverMovieQuery(k, mapOf("with_genres" to "16|10751", "certification_country" to "US", "certification.lte" to "PG", "page" to "$page"))
                .results.map { it.toCatalogItem() }
        } else {
            val params = mapOf("sort_by" to "popularity.desc", "vote_average.gte" to "7", "vote_count.gte" to "500", "page" to "$page")
            mix(tmdb.discoverMovieQuery(k, params).results.map { it.toCatalogItem() }, tmdb.discoverTvQuery(k, params).results.map { it.toCatalogItem() })
        }
        return list.filter { it.posterUrl != null && it.backdropUrl != null }.randomOrNull()
    }

    override suspend fun episodes(item: CatalogItem, season: Int): List<Episode> =
        tmdb.tvSeason(item.id.toInt(), season, key()).episodes.map {
            Episode(
                season = it.season_number,
                number = it.episode_number,
                title = it.name,
                overview = it.overview,
                stillUrl = it.still_path?.let(TmdbImages::still),
                runtimeMinutes = it.runtime,
                airDate = it.air_date,
            )
        }

    private val platform = DesktopStreamPlatform(config)
    private val streamSearch = StreamSearch(tmdb, torrentio, platform, platform, platform) { config.current.tmdbKey.trim() }

    /** Los mismos buscadores que la Shield (castellano, Torrentio, The Pirate Bay, Knaben, Comet, addons…). */
    override suspend fun checkKeys(config: DesktopConfig): KeysCheck = coroutineScope {
        val tmdbCheck = async { checkTmdb(config.tmdbKey.trim()) }
        val debridCheck = async { checkDebrid(config.debridService, config.debridKey.trim()) }
        KeysCheck(tmdbCheck.await(), debridCheck.await())
    }

    private suspend fun checkTmdb(key: String): KeyStatus {
        if (key.isBlank()) return KeyStatus(false, "Falta la clave de TMDB")
        return runCatching { tmdb.trendingMovies(key) }.fold(
            onSuccess = { KeyStatus(true, "TMDB: clave correcta") },
            onFailure = { e ->
                val code = (e as? retrofit2.HttpException)?.code()
                if (code == 401 || code == 403) KeyStatus(false, "TMDB: clave no válida. Copia la «Clave de la API» (v3).")
                else KeyStatus(false, "TMDB no responde ahora mismo. Revisa la conexión.")
            },
        )
    }

    /** Igual que la tele para los servicios que van por Torrentio: una peli conocida y mirar qué enlaces devuelve. */
    private suspend fun checkDebrid(service: DebridService, key: String): KeyStatus {
        if (key.isBlank()) return KeyStatus(false, "Falta la clave de ${service.title}")
        val streams = runCatching { torrentio.streamsWithConfig(torrentioConfig(service, key), "movie", "tt0137523").streams }
            .getOrElse { return KeyStatus(false, "Torrentio no responde ahora mismo. Prueba en un rato.") }
        streams.firstOrNull { it.isDebridError() }?.let { return KeyStatus(false, debridErrorMessage(it.title, service.title, platform)) }
        if (streams.none { it.url?.contains("/resolve/") == true }) {
            return KeyStatus(false, "Torrentio no ha dado enlaces de ${service.title}. Revisa la clave.")
        }
        return KeyStatus(true, "${service.title}: clave correcta (conectado a través de Torrentio)")
    }

    override fun sources(item: CatalogItem, episode: Episode?): Flow<SourcesState> {
        val cfg = config.current
        val debrid = cfg.debridService
        val debridKey = cfg.debridKey.trim()
        return streamSearch.streams(item, episode).map { results ->
            if (debridKey.isBlank()) error("Pon la clave de ${debrid.title} en Ajustes para ver fuentes.")
            // El mismo orden que la tele (caché > calidad > HDR > castellano, seeds y tamaño; castellano primero si
            // está activado). mpv decodifica todo y convierte el HDR a la pantalla, así que no se descarta nada por eso.
            val ordered = prioritize(
                cfg.exclusions.filter(results.sources).filterNot { broken.isBroken(it.title) },
                supportedHdr = HdrType.entries.toSet(),
                hardwareVideoCodecs = com.shieldstream.tv.model.VideoCodec.entries.toSet(),
                preferSpanish = cfg.spanishFirst,
            )
            SourcesState(
                sources = ordered.map { s ->
                    DesktopSource(
                        title = s.title,
                        detail = listOfNotNull(
                            s.hdrType.takeIf { it != HdrType.SDR }?.label,
                            s.sizeBytes.takeIf { it > 0 }?.let { "%.1f GB".format(it / 1_073_741_824.0) },
                            s.seeders?.takeIf { it > 0 }?.let { "Seeds $it" },
                            s.languages.takeIf { it.isNotEmpty() }?.joinToString("/"),
                        ).joinToString(" · "),
                        quality = s.quality,
                        cached = s.isCached,
                        url = s.resolveUrl ?: torrentioResolveUrl(debrid, debridKey, s.infoHash, s.fileIdx, s.fileName ?: s.title),
                        section = s.provider,
                        spanish = s.advertisesSpanishAudio,
                        sizeBytes = s.sizeBytes,
                    )
                },
                pending = results.pending,
                // Lo de las webs españolas sale con el nombre de cada web: si alguna dio algo, «En castellano» no está vacío.
                unavailable = results.unavailable.filterKeys { key ->
                    !(key == platform.providerSection(com.shieldstream.tv.domain.SourceProvider.CASTELLANO) &&
                        results.sources.any { it.id.startsWith("CASTELLANO:") })
                },
            )
        // Leer, ordenar y clasificar cientos de fuentes (idioma, calidad, HDR…) fuera del hilo de la interfaz: antes
        // se hacía en él y la ficha se quedaba congelada unos segundos al abrirla (lo contaron en el foro).
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    /** Enlace /resolve/ de Torrentio para un torrent: el servicio debrid lo convierte en el vídeo al abrirlo. */
    private fun torrentioResolveUrl(debrid: DebridService, key: String, hash: String, fileIdx: Int?, fileName: String) =
        "${ApiConfig.TORRENTIO_BASE_URL}resolve/${debrid.torrentioKey}/${uriEncode(key)}/${hash.lowercase()}/null/" +
            "${fileIdx ?: "null"}/${uriEncode(fileName)}"

    companion object {
        /** Animación, familia e infantil (TMDB), como KIDS_GENRES en la tele. */
        val KIDS_GENRES = setOf(16, 10751, 10762)
        const val NO_TMDB_KEY = "Falta la clave de TMDB: ponla en Ajustes (es gratis en themoviedb.org)."
    }
}

/**
 * Los datos de Torrentio vienen con emojis (👤 seeds, 💾 tamaño, ⚙️ web, banderas de idioma) que no todas las fuentes
 * de Windows pintan: se pasan a texto ("Seeds 120 · 18.4 GB · ThePirateBay · ES/EN").
 */
internal fun readableDetail(text: String): String {
    val out = StringBuilder()
    val flags = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val next = i + Character.charCount(cp)
        val cp2 = if (next < text.length) text.codePointAt(next) else -1
        when {
            cp in 0x1F1E6..0x1F1FF && cp2 in 0x1F1E6..0x1F1FF -> {
                flags += "${'A' + (cp - 0x1F1E6)}${'A' + (cp2 - 0x1F1E6)}".let { if (it == "GB" || it == "US") "EN" else if (it == "MX") "LAT" else it }
                i = next + Character.charCount(cp2)
                continue
            }
            cp == 0x1F464 -> out.append("Seeds")
            cp == 0x1F4BE || cp == 0x2699 -> out.append("·")
            cp == 0xFE0F || cp == 0x200D || cp == 0x2F -> Unit
            cp >= 0x1F000 -> Unit
            else -> out.appendCodePoint(cp)
        }
        i = next
    }
    val base = out.toString().replace(Regex("\\s+"), " ").trim().trim('·').trim()
    return listOfNotNull(base.takeIf { it.isNotBlank() }, flags.distinct().joinToString("/").takeIf { it.isNotBlank() })
        .joinToString(" · ")
}

/** TMDB devuelve en inglés los géneros propios de las series («Action & Adventure»…) aunque se pida en español. */
object TvGenres {
    private val spanish = mapOf(
        "Action & Adventure" to "Acción y aventura",
        "Sci-Fi & Fantasy" to "Ciencia ficción y fantasía",
        "War & Politics" to "Bélica y política",
        "Kids" to "Infantil",
        "News" to "Noticias",
        "Soap" to "Telenovela",
        "Talk" to "Entrevistas",
        "Reality" to "Telerrealidad",
    )

    fun localized(name: String): String = if (Lang.isEnglish) name else spanish[name] ?: name
}
