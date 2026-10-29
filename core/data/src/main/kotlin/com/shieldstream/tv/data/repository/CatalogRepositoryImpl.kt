package com.shieldstream.tv.data.repository

import kotlinx.coroutines.withTimeout
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.tr
import android.net.Uri
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.shieldstream.tv.domain.StreamResults
import com.shieldstream.tv.domain.ApiService
import android.util.Log
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SettingsRepository
import kotlinx.coroutines.flow.first
import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.model.Season
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.network.di.ApiConfig
import com.shieldstream.tv.network.dto.TmdbImageDto
import com.shieldstream.tv.network.dto.TmdbImagesDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbTranslationsDto
import com.shieldstream.tv.network.dto.TmdbTvDto
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val tmdb: TmdbApi,
    private val torrentio: TorrentioApi,
    private val extras: HomeExtrasSource,
    private val store: TokenStore,
    private val settings: SettingsRepository,
    private val profiles: com.shieldstream.tv.domain.ProfileRepository,
) : CatalogRepository {

    /** Perfil infantil activo: Inicio, portada y búsqueda solo con contenido para niños. */
    private val kids: Boolean get() = profiles.active.value.kids

    // --- Caché en memoria de Inicio / Series / Películas (portada + filas) ---
    // Cambiar de pestaña no vuelve a pedir nada a TMDB durante [HOME_CACHE_MS], y el Inicio precarga las otras
    // dos. El Mutex por clave hace que, si una precarga está en curso, la pestaña la espere en vez de repetirla.
    // Los resultados vacíos (error de red) no se guardan: "Reintentar" vuelve a pedirlos.
    private class Cached(val at: Long, val value: Any)
    private val homeCache = ConcurrentHashMap<String, Cached>()
    private val homeLocks = ConcurrentHashMap<String, Mutex>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : List<*>> cachedHome(cacheKey: String, load: suspend () -> T): T =
        homeLocks.getOrPut(cacheKey) { Mutex() }.withLock {
            val hit = homeCache[cacheKey]
            if (hit != null && System.currentTimeMillis() - hit.at < HOME_CACHE_MS) return@withLock hit.value as T
            load().also { if (it.isNotEmpty()) homeCache[cacheKey] = Cached(System.currentTimeMillis(), it) }
        }

    // Infantil: portada propia (la semanal se elige entre lo más visto de TMDB, que no es para niños).
    override suspend fun getFeatured(filter: HomeFilter): List<FeaturedItem> =
        if (kids) cachedHome("featured_kids_${filter.name}") { extras.kidsFeatured(filter) }
        else cachedHome("featured_${filter.name}") { extras.featured(filter) }

    // Infantil: solo sus categorías (la pantalla tampoco enseña otras; esto cubre cualquier otro camino).
    override suspend fun getCategoryRows(category: Category, filter: HomeFilter): List<CatalogRow> =
        if (kids && category !in Category.KIDS_SAFE) emptyList() else extras.categoryRows(category, filter)

    /** Clave de TMDB de Ajustes, leída en cada petición: si se cambia allí, vale al momento. */
    private val key: String get() = store.tmdbKey.orEmpty()

    /** Una fila de la Home: se cargan todas en paralelo y las que fallan se omiten. */
    private class RowSpec(
        val id: String,
        val title: String,
        val style: RowStyle = RowStyle.POSTER,
        val load: suspend () -> List<CatalogItem>,
    )

    override suspend fun getHome(filter: HomeFilter): List<CatalogRow> =
        if (kids) cachedHome("home_kids_${filter.name}") { loadHome(kidsRowsFor(filter)) }
        else cachedHome("home_${filter.name}") { loadHome(rowsFor(filter)) }

    private suspend fun loadHome(specs: List<RowSpec>): List<CatalogRow> = coroutineScope {
        if (key.isBlank()) throw IllegalStateException(MISSING_TMDB_KEY)
        specs
            .map { spec ->
                async {
                    runCatching {
                        val items = spec.load().filter { it.posterUrl != null }
                        val limited = if (spec.style == RowStyle.TOP10) items.take(10) else items
                        CatalogRow(spec.id, spec.title, limited, spec.style)
                    }.getOrNull()
                }
            }
            .awaitAll()
            .filterNotNull()
            .filter { it.items.isNotEmpty() }
    }

    private fun rowsFor(filter: HomeFilter): List<RowSpec> = when (filter) {
        HomeFilter.ALL -> listOf(
            RowSpec("trending", tr("Tendencias hoy", "Trending today")) { tmdb.trendingAll(key).results.mapNotNull { it.toCatalogItemOrNull() } },
            RowSpec("top10_movies", tr("Top 10 películas hoy", "Top 10 movies today"), RowStyle.TOP10) { tmdb.trendingMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("popular_movies", tr("Películas populares", "Popular movies")) { tmdb.popularMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("popular_tv", tr("Series populares", "Popular TV shows")) { tmdb.popularTv(key).results.map { it.toCatalogItem() } },
            RowSpec("top10_tv", tr("Top 10 series hoy", "Top 10 TV shows today"), RowStyle.TOP10) { tmdb.trendingTv(key).results.map { it.toCatalogItem() } },
            RowSpec("now_playing", tr("Estrenos en cines", "Now in theaters")) { tmdb.nowPlayingMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("action", tr("Acción", "Action")) { tmdb.discoverMovies(key, "28").results.map { it.toCatalogItem() } },
            RowSpec("scifi", tr("Ciencia ficción", "Sci-fi")) { tmdb.discoverMovies(key, "878").results.map { it.toCatalogItem() } },
            RowSpec("comedy", tr("Comedias", "Comedies")) { tmdb.discoverMovies(key, "35").results.map { it.toCatalogItem() } },
            RowSpec("top_movies", tr("Películas aclamadas", "Acclaimed movies")) { tmdb.topRatedMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("top_tv", tr("Series aclamadas", "Acclaimed TV shows")) { tmdb.topRatedTv(key).results.map { it.toCatalogItem() } },
        )
        HomeFilter.MOVIES -> listOf(
            RowSpec("m_top10", tr("Top 10 películas hoy", "Top 10 movies today"), RowStyle.TOP10) { tmdb.trendingMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("m_popular", tr("Populares", "Popular")) { tmdb.popularMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("m_now", tr("Estrenos en cines", "Now in theaters")) { tmdb.nowPlayingMovies(key).results.map { it.toCatalogItem() } },
            RowSpec("m_action", tr("Acción", "Action")) { tmdb.discoverMovies(key, "28").results.map { it.toCatalogItem() } },
            RowSpec("m_scifi", tr("Ciencia ficción", "Sci-fi")) { tmdb.discoverMovies(key, "878").results.map { it.toCatalogItem() } },
            RowSpec("m_thriller", "Thriller") { tmdb.discoverMovies(key, "53").results.map { it.toCatalogItem() } },
            RowSpec("m_horror", tr("Terror", "Horror")) { tmdb.discoverMovies(key, "27").results.map { it.toCatalogItem() } },
            RowSpec("m_comedy", tr("Comedias", "Comedies")) { tmdb.discoverMovies(key, "35").results.map { it.toCatalogItem() } },
            RowSpec("m_animation", tr("Animación", "Animation")) { tmdb.discoverMovies(key, "16").results.map { it.toCatalogItem() } },
            RowSpec("m_top", tr("Aclamadas por la crítica", "Critically acclaimed")) { tmdb.topRatedMovies(key).results.map { it.toCatalogItem() } },
        )
        HomeFilter.TV -> listOf(
            RowSpec("t_top10", tr("Top 10 series hoy", "Top 10 TV shows today"), RowStyle.TOP10) { tmdb.trendingTv(key).results.map { it.toCatalogItem() } },
            RowSpec("t_popular", tr("Populares", "Popular")) { tmdb.popularTv(key).results.map { it.toCatalogItem() } },
            RowSpec("t_drama", "Dramas") { tmdb.discoverTv(key, "18").results.map { it.toCatalogItem() } },
            RowSpec("t_crime", tr("Crimen", "Crime")) { tmdb.discoverTv(key, "80").results.map { it.toCatalogItem() } },
            RowSpec("t_scifi", tr("Ciencia ficción y fantasía", "Sci-fi & fantasy")) { tmdb.discoverTv(key, "10765").results.map { it.toCatalogItem() } },
            RowSpec("t_action", tr("Acción y aventura", "Action & adventure")) { tmdb.discoverTv(key, "10759").results.map { it.toCatalogItem() } },
            RowSpec("t_comedy", tr("Comedias", "Comedies")) { tmdb.discoverTv(key, "35").results.map { it.toCatalogItem() } },
            RowSpec("t_animation", tr("Animación", "Animation")) { tmdb.discoverTv(key, "16").results.map { it.toCatalogItem() } },
            RowSpec("t_top", tr("Aclamadas por la crítica", "Critically acclaimed")) { tmdb.topRatedTv(key).results.map { it.toCatalogItem() } },
        )
    }

    override suspend fun trending(): List<CatalogItem> =
        if (kids) kidsMovies(mapOf()).take(20)
        else tmdb.trendingAll(key).results.mapNotNull { it.toCatalogItemOrNull() }.filter { it.posterUrl != null }

    override suspend fun search(query: String): List<CatalogItem> =
        tmdb.search(key, query).results
            .filter { !kids || it.genre_ids.any { g -> g in KIDS_GENRES } } // infantil: solo animación/familia/infantil
            .mapNotNull { it.toCatalogItemOrNull() } // descarta personas
            .filter { it.posterUrl != null }

    // ------------------------------------------------------------------ perfil infantil
    // Películas: animación o familia con calificación de EE. UU. hasta PG (TMDB no tiene la española para todo).
    // Series: el género "Infantil" (Kids) de TMDB. Nada de "Animación" a secas en series: incluye series de adultos.

    private suspend fun kidsMovies(extra: Map<String, String>, genres: String = "16|10751"): List<CatalogItem> =
        tmdb.discoverMovieQuery(
            key,
            mapOf(
                "with_genres" to genres,
                "certification_country" to "US",
                "certification.lte" to "PG",
                "include_adult" to "false",
                "sort_by" to "popularity.desc",
            ) + extra,
        ).results.map { it.toCatalogItem() }

    private suspend fun kidsTv(extra: Map<String, String>, genres: String = "10762"): List<CatalogItem> =
        tmdb.discoverTvQuery(
            key,
            mapOf("with_genres" to genres, "include_adult" to "false", "sort_by" to "popularity.desc") + extra,
        ).results.map { it.toCatalogItem() }

    private fun kidsRowsFor(filter: HomeFilter): List<RowSpec> {
        val topRated = mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300")
        val movies = listOf(
            RowSpec("k_movies", tr("Películas infantiles", "Kids movies")) { kidsMovies(mapOf()) },
            RowSpec("k_animation", tr("Animación", "Animation")) { kidsMovies(mapOf(), genres = "16") },
            RowSpec("k_family", tr("Para toda la familia", "For the whole family")) { kidsMovies(mapOf("certification.lte" to "G"), genres = "10751") },
            RowSpec("k_top_movies", tr("Las mejor valoradas", "Top rated")) { kidsMovies(topRated) },
            RowSpec("k_more_movies", tr("Más películas", "More movies")) { kidsMovies(mapOf("page" to "2")) },
        )
        val shows = listOf(
            RowSpec("k_tv", tr("Series infantiles", "Kids shows")) { kidsTv(mapOf()) },
            RowSpec("k_cartoons", tr("Dibujos animados", "Cartoons")) { kidsTv(mapOf(), genres = "10762,16") },
            RowSpec("k_more_tv", tr("Más series", "More shows")) { kidsTv(mapOf("page" to "2")) },
        )
        return when (filter) {
            HomeFilter.ALL -> listOf(movies[0], shows[0], movies[1], shows[1], movies[2], movies[3], shows[2])
            HomeFilter.MOVIES -> movies
            HomeFilter.TV -> shows
        }
    }

    override suspend fun getDetails(item: CatalogItem): CatalogDetails {
        val id = item.id.toIntOrNull() ?: return CatalogDetails(item)
        return if (item.type == MediaType.TV) {
            val d = tmdb.tvDetails(id, key)
            CatalogDetails(
                item = item.copy(
                    imdbId = d.external_ids?.imdb_id ?: item.imdbId,
                    overview = d.overview.ifBlank { item.overview },
                    backdropUrl = d.backdrop_path?.let { backdrop(it) } ?: item.backdropUrl,
                ),
                logoUrl = pickLogo(d.images),
                originalTitle = englishTitle(d.translations) ?: d.original_name,
                runtimeMinutes = d.episode_run_time.firstOrNull(),
                genres = d.genres.map { it.name },
                tagline = d.tagline?.takeIf { it.isNotBlank() },
                seasons = d.seasons
                    .filter { it.season_number > 0 && it.episode_count > 0 }
                    .map { Season(it.season_number, it.name.ifBlank { tr("Temporada ${it.season_number}", "Season ${it.season_number}") }, it.episode_count) },
            )
        } else {
            val d = tmdb.movieDetails(id, key)
            CatalogDetails(
                item = item.copy(
                    imdbId = d.imdb_id ?: item.imdbId,
                    overview = d.overview.ifBlank { item.overview },
                    backdropUrl = d.backdrop_path?.let { backdrop(it) } ?: item.backdropUrl,
                ),
                logoUrl = pickLogo(d.images),
                originalTitle = englishTitle(d.translations) ?: d.original_title,
                runtimeMinutes = d.runtime?.takeIf { it > 0 },
                genres = d.genres.map { it.name },
                tagline = d.tagline?.takeIf { it.isNotBlank() },
            )
        }
    }

    private val itemCache = ConcurrentHashMap<String, CatalogItem>()

    override suspend fun getItem(type: MediaType, id: String): CatalogItem? {
        val cacheKey = "${type.name}_$id"
        itemCache[cacheKey]?.let { return it }
        val tmdbId = id.toIntOrNull() ?: return null
        // Llamada ligera (sin logos ni traducciones): solo lo que pinta la tarjeta.
        val item = if (type == MediaType.TV) {
            val d = tmdb.tvDetails(tmdbId, key, append = "external_ids")
            CatalogItem(
                id = id,
                imdbId = d.external_ids?.imdb_id,
                type = MediaType.TV,
                title = d.name,
                overview = d.overview,
                posterUrl = d.poster_path?.let { poster(it) },
                backdropUrl = d.backdrop_path?.let { backdrop(it) },
                year = d.first_air_date?.take(4)?.toIntOrNull(),
                rating = d.vote_average.toFloat(),
                genres = d.genres.map { it.name },
            )
        } else {
            val d = tmdb.movieDetails(tmdbId, key, append = "")
            CatalogItem(
                id = id,
                imdbId = d.imdb_id,
                type = MediaType.MOVIE,
                title = d.title,
                overview = d.overview,
                posterUrl = d.poster_path?.let { poster(it) },
                backdropUrl = d.backdrop_path?.let { backdrop(it) },
                year = d.release_date?.take(4)?.toIntOrNull(),
                rating = d.vote_average.toFloat(),
                genres = d.genres.map { it.name },
            )
        }
        itemCache[cacheKey] = item
        return item
    }

    override suspend fun getEpisodes(item: CatalogItem, season: Int): List<Episode> {
        val id = item.id.toIntOrNull() ?: return emptyList()
        return tmdb.tvSeason(id, season, key).episodes.map { e ->
            Episode(
                season = e.season_number.takeIf { it > 0 } ?: season,
                number = e.episode_number,
                title = e.name.ifBlank { tr("Episodio ${e.episode_number}", "Episode ${e.episode_number}") },
                overview = e.overview,
                stillUrl = e.still_path?.let { "$IMG/w300$it" },
                runtimeMinutes = e.runtime,
            )
        }
    }

    override fun getStreams(item: CatalogItem, episode: Episode?): Flow<StreamResults> = channelFlow {
        val imdbId = item.imdbId ?: resolveImdb(item) ?: error(tr("No se encontró el id de IMDb de «${item.title}»", "Couldn't find the IMDb id for “${item.title}”"))
        val (type, streamId) = if (item.type == MediaType.TV) {
            val ep = episode ?: error(tr("Elige un episodio", "Pick an episode"))
            "series" to "$imdbId:${ep.season}:${ep.number}"
        } else {
            "movie" to imdbId
        }
        // Todos los buscadores activados a la vez. Cada uno que responde se emite al momento (no se espera al
        // más lento); uno caído o lento (25 s máx.) no frena a los demás. AIOStreams solo si tiene URL.
        val aioConfigured = !store.getSecret(TokenStore.keyNameFor(ApiService.AIOSTREAMS)).isNullOrBlank()
        val enabled = settings.sourceProviders.first()
        val providers = SourceProvider.entries.filter { it in enabled && (it != SourceProvider.AIOSTREAMS || aioConfigured) }
        val done = LinkedHashMap<SourceProvider, Result<List<StreamSource>>>()
        val lock = Mutex()
        // Secciones: Torrentio, AIOStreams y "The Pirate Bay" (todos los buscadores propios juntos, sin repetidos).
        fun snapshot(): StreamResults {
            val sources = providers.flatMap { p ->
                done[p]?.getOrNull().orEmpty().map { it.copy(id = "${p.name}:${it.id}", provider = p.section) }
            }.distinctBy { it.provider + ":" + if (it.resolveUrl == null) it.infoHash.lowercase() else it.id }
            val sections = providers.groupBy { it.section }
            val withSources = sources.map { it.provider }.toSet()
            val pending = sections.filter { (_, ps) -> ps.any { it !in done } }.keys
            val unavailable = sections.mapNotNull { (section, ps) ->
                when {
                    section in pending || section in withSources -> null
                    ps.all { done[it]?.isFailure == true } -> section to tr("No responde ahora mismo", "Not responding right now")
                    else -> section to tr("Sin fuentes para esto", "No sources for this")
                }
            }.toMap()
            return StreamResults(sources, unavailable, pending)
        }
        send(snapshot())
        coroutineScope {
            providers.map { p ->
                launch {
                    val r = runCatching { withTimeout(PROVIDER_TIMEOUT_MS) { providerStreams(p, item, type, streamId) } }
                    r.exceptionOrNull()?.let { Log.w("Catalog", "${p.title} falló: ${it.message}") }
                    lock.withLock {
                        done[p] = r
                        send(snapshot())
                    }
                }
            }.joinAll()
        }
        // Nada en ninguno y alguno falló: un error de debrid (clave…) se enseña tal cual; si no, quién no respondió.
        if (snapshot().sources.isEmpty()) {
            val failed = done.filter { it.value.isFailure }
            failed.values.firstNotNullOfOrNull { r -> r.exceptionOrNull()?.takeIf { it is IllegalStateException && it !is kotlinx.coroutines.CancellationException } }?.let { throw it }
            if (failed.isNotEmpty()) {
                val names = failed.keys.joinToString { it.title }
                error(tr("$names no responde ahora mismo. Prueba en un rato.", "$names isn't responding right now. Try again later."))
            }
        }
    }

    private suspend fun providerStreams(p: SourceProvider, item: CatalogItem, type: String, streamId: String): List<StreamSource> =
        when (p) {
            // Uno o varios addons (URLs separadas por espacios): todos a la vez, cada fuente con el nombre de su addon.
            SourceProvider.AIOSTREAMS -> coroutineScope {
                val bases = aioStreamBases(store.getSecret(TokenStore.keyNameFor(ApiService.AIOSTREAMS)).orEmpty())
                bases.map { base ->
                    async {
                        runCatching { aioStreams(base, item, type, streamId, label = addonLabel(base, bases.size)) }
                            .onFailure { Log.w("Catalog", "Addon ${base.take(40)} falló: ${it.message}") }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
            SourceProvider.THEPIRATEBAY, SourceProvider.KNABEN,
            SourceProvider.NYAA,
            -> scraperStreams(p, item, streamId)
            SourceProvider.COMET -> cometStreams(item, type, streamId)
            else -> torrentioStreams(p, item, type, streamId)
        }

    /**
     * Buscadores propios (como los scrapers de Umbrella en Kodi): piden la lista de torrents a la web y la pasan
     * al mismo formato que Torrentio ("Release\n👤 seeds 💾 tamaño"), así el resto de Bambi no cambia.
     * Solo con Real-Debrid: los torrents se resuelven en la Shield. Series: solo el episodio exacto (sin packs,
     * que no sabrían qué archivo elegir).
     */
    private suspend fun scraperStreams(p: SourceProvider, item: CatalogItem, streamId: String): List<StreamSource> {
        // Los buscadores propios dan infoHash: vale Real-Debrid o AllDebrid, que resuelven con su API.
        val debrid = settings.debridService.first()
        if (debrid != DebridService.REAL_DEBRID && debrid != DebridService.ALLDEBRID) return emptyList()
        val parts = streamId.split(':')
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val episodeTag = if (season != null && episode != null) "S%02dE%02d".format(season, episode) else null
        val seasonTag = season?.let { "S%02d".format(it) }
        val base = checkNotNull(p.baseUrl)
        val title = releaseTitle(item)
        val wanted = normalize(title)
        // Series, como CocoScrapers: el episodio y también los packs de temporada (Real-Debrid elige dentro del
        // pack el archivo del episodio, ver ResolveStreamUseCase.pickFile). Dos búsquedas: "SxxEyy" y "Sxx".
        val queries = if (episodeTag != null) listOf("$title $episodeTag", "$title $seasonTag") else listOf("$title ${item.year ?: ""}".trim())
        // Anime: además "Título 1100" (numeración absoluta, la de casi todas sus releases). Va después de
        // releaseTitle, que es quien sabe si es anime.
        val absolute = if (season != null && episode != null) absoluteEpisode(item, season, episode) else null
        // Anime: Nyaa y la categoría Anime de Knaben. Los géneros no siempre vienen en el item (búsqueda): vale
        // también lo que sabe releaseTitle (japonés + Animación).
        val anime = item.genres.any { it.contains("anim", ignoreCase = true) } || animeSeasons.containsKey(item.key)
        val allQueries = if (absolute != null) queries + "$title ${"%02d".format(absolute)}" else queries
        val packRx = season?.let { Regex("(?i)(\\bS0*$it\\b(?!\\s*E\\d)|season\\s*0*$it\\b|temporada\\s*0*$it\\b|complete)") }
        var seen = 0
        // El título tiene que ir al principio (tras etiquetas [Grupo]) y seguido de año, temporada o calidad:
        // así "Lupin III - 01" no cuela como "Lupin", ni "Dune Messiah" como "Dune".
        val titleRx = Regex(
            "^(?:\\[[^\\]]*\\]\\s*)*" + title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
                .joinToString("[\\W_]+") { Regex.escape(it) } +
                "[\\W_]+(?:\\(?(?:19|20)\\d{2}|s\\d|season|temporada|complete|\\d{3,4}p|\\d+x\\d)",
            RegexOption.IGNORE_CASE,
        )
        // "[Grupo] One Piece - 1100 (1080p)" / "One Piece E1100": el título y justo después el número absoluto
        // (no un rango "1100-1110", que sería un pack sin forma de elegir el archivo).
        val absoluteRx = absolute?.let {
            Regex(
                "^(?:\\[[^\\]]*\\]\\s*)*" + title.split(Regex("[^\\p{L}\\p{N}]+")).filter { w -> w.isNotBlank() }
                    .joinToString("[\\W_]+") { w -> Regex.escape(w) } +
                    "[\\W_]+(?:e|ep|episode|episodio)?\\s*0*$it(?:v\\d)?(?!\\d|\\s*[-~]\\s*\\d)",
                RegexOption.IGNORE_CASE,
            )
        }
        fun accepts(name: String): Boolean {
            seen++
            if (!normalize(name).contains(wanted)) return false
            if (absoluteRx?.containsMatchIn(name) == true) return true
            if (!titleRx.containsMatchIn(name)) return false
            if (episodeTag == null) return item.year == null || name.contains(item.year.toString())
            return name.contains(episodeTag, ignoreCase = true) || packRx?.containsMatchIn(name) == true
        }
        val found = mutableListOf<TorrentioStreamDto>()
        when (p) {
            else -> for (query in allQueries) {
                if (p == SourceProvider.NYAA && !anime) break
                val enc = java.net.URLEncoder.encode(query, "UTF-8")
                when (p) {
                    SourceProvider.THEPIRATEBAY -> {
                        val q = java.net.URLEncoder.encode(query.replace(Regex("[^A-Za-z0-9\\s.-]+"), ""), "UTF-8")
                        val arr = org.json.JSONArray(torrentio.raw("$base/q.php?q=$q&cat=0").string())
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val name = o.optString("name")
                            val hash = o.optString("info_hash")
                            if (o.optString("id") == "0" || hash.isBlank() || hash.all { it == '0' } || !accepts(name)) continue
                            found += scraperDto(p, name, hash, o.optLong("size"), o.optInt("seeders"))
                        }
                    }
                    SourceProvider.KNABEN -> {
                        val body = org.json.JSONObject()
                            .put("search_type", "100%").put("search_field", "title").put("query", query)
                            .put("order_by", "seeders").put("order_direction", "desc").put("size", 150).put("hide_xxx", true)
                            .put("categories", org.json.JSONArray().put(if (episodeTag != null) 2000000 else 3000000).apply { if (anime) put(6000000) })
                            .toString().toRequestBody("application/json".toMediaType())
                        val hits = org.json.JSONObject(torrentio.postJson("$base/v1", body).string()).optJSONArray("hits")
                        for (i in 0 until (hits?.length() ?: 0)) {
                            val h = hits!!.getJSONObject(i)
                            val name = h.optString("title")
                            val hash = h.optString("hash").ifBlank { Regex("btih:([a-fA-F0-9]{40})").find(h.optString("magnetUrl"))?.groupValues?.get(1).orEmpty() }
                            if (hash.length != 40 || !accepts(name)) continue
                            found += scraperDto(p, name, hash, h.optLong("bytes"), h.optInt("seeders"))
                        }
                    }
                    else -> {
                        val url = when (p) {
                            else -> "$base/?f=0&c=0_0&q=$enc"
                        }
                        val html = torrentio.page(url, BROWSER_UA).string()
                        // Enlaces magnet de la página de resultados: hash + nombre (dn). Tamaño si viene (xl).
                        MAGNET.findAll(html).forEach { m ->
                            val params = m.groupValues[2].replace("&amp;", "&")
                            val name = Regex("[?&]dn=([^&]+)").find(params)?.groupValues?.get(1)
                                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: return@forEach
                            if (!accepts(name)) return@forEach
                            val size = Regex("[?&]xl=(\\d+)").find(params)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                            found += scraperDto(p, name, m.groupValues[1], size, 0)
                        }
                    }
                }
            }
        }
        Log.w("Catalog", "${p.title}: ${found.size} aceptadas de $seen (consultas: $allQueries)")
        return found.distinctBy { it.infoHash }.mapNotNull { it.toStreamSource(item) }
            .map { if (season != null) it.copy(season = season, episode = episode) else it }
            .sortedByDescending { it.seeders ?: 0 }
    }

    /**
     * Comet como en CocoScrapers: configuración en base64 con tu token de Real-Debrid. Da enlaces de reproducción
     * de Comet (redirigen al archivo de Real-Debrid): van por resolveUrl como los de AIOStreams.
     */
    private suspend fun cometStreams(item: CatalogItem, type: String, streamId: String): List<StreamSource> {
        if (settings.debridService.first().viaTorrentio) return emptyList()
        val token = store.getSecret(TokenStore.RD_TOKEN)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val params = org.json.JSONObject()
            .put("indexers", org.json.JSONArray()).put("maxResults", 0).put("maxSize", 0)
            .put("resultFormat", org.json.JSONArray().put("All")).put("resolutions", org.json.JSONArray().put("All"))
            .put("languages", org.json.JSONArray().put("All"))
            .put("debridService", "realdebrid").put("debridApiKey", token).put("debridStreamProxyPassword", "")
            .toString()
        val b64 = android.util.Base64.encodeToString(params.toByteArray(), android.util.Base64.NO_WRAP)
        return aioStreams("${SourceProvider.COMET.baseUrl}/$b64", item, type, streamId, label = "Comet")
    }

    /**
     * Título en inglés de TMDB (las releases van en inglés); si falla, el que se ve en la app.
     * Una sola petición por título: antes cada buscador propio (TPB, Knaben, Nyaa) la repetía a la vez.
     */
    private suspend fun releaseTitle(item: CatalogItem): String = releaseTitleLock.withLock {
        releaseTitles[item.key]?.let { return@withLock it }
        val title = runCatching {
            val id = item.id.toInt()
            if (item.type == MediaType.TV) {
                tmdb.tvDetails(id, key).also { d ->
                    // Anime: episodios por temporada, para la numeración absoluta (ver absoluteEpisode).
                    if (d.original_language == "ja" && d.genres.any { it.id == 16 }) {
                        animeSeasons[item.key] = d.seasons.filter { it.season_number > 0 }.associate { it.season_number to it.episode_count }
                    }
                }.let { englishTitle(it.translations) ?: it.original_name }
            } else {
                tmdb.movieDetails(id, key).let { englishTitle(it.translations) ?: it.original_title }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        title?.also { releaseTitles[item.key] = it } ?: item.title
    }

    /** Anime (TMDB: japonés + Animación) → episodios de cada temporada. Lo rellena [releaseTitle]. */
    private val animeSeasons = java.util.concurrent.ConcurrentHashMap<String, Map<Int, Int>>()

    /** Primer número de episodio de cada temporada de anime ("clave:temporada"): > 1 = TMDB ya numera absoluto. */
    private val seasonStarts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Número absoluto del episodio en un anime (T3E5 → todos los de T1 y T2 + 5), como lo numeran sus releases.
     * Si TMDB ya lo numera así (One Piece: la T21 empieza en el 892), es el mismo número.
     * null si no es anime o faltan datos de alguna temporada anterior.
     */
    private suspend fun absoluteEpisode(item: CatalogItem, season: Int, episode: Int): Int? {
        val counts = animeSeasons[item.key] ?: return null
        val start = seasonStarts.getOrPut("${item.key}:$season") {
            runCatching { tmdb.tvSeason(item.id.toInt(), season, key).episodes.minOfOrNull { it.episode_number } }
                .getOrNull() ?: 1
        }
        if (start > 1) return episode
        val before = (1 until season).map { counts[it] ?: return null }
        return before.sum() + episode
    }

    private val releaseTitleLock = Mutex()
    private val releaseTitles = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun scraperDto(p: SourceProvider, name: String, hash: String, size: Long, seeders: Int) = TorrentioStreamDto(
        name = "Bambi\n$name",
        title = "$name\n👤 $seeders 💾 ${"%.2f".format(java.util.Locale.US, size / 1_073_741_824.0)} GB ⚙️ ${p.title}",
        infoHash = hash.lowercase(),
    )

    /**
     * AIOStreams devuelve enlaces ya listos del debrid que tenga configurado (redirigen al archivo): van por
     * resolveUrl como los /resolve/ de Torrentio. No traen infoHash → se inventa uno estable con la URL.
     * Se lee mejor con el formateador \"Torrentio\" en la configuración de AIOStreams (calidad, 👤, 💾).
     */
    /** Nombre corto del addon para la lista de fuentes ("aiostreams.elfhosted.com" → "aiostreams"). */
    private fun addonLabel(base: String, count: Int): String =
        if (count <= 1) tr("el addon de Stremio", "the Stremio addon")
        else base.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('.')

    private suspend fun aioStreams(base: String, item: CatalogItem, type: String, streamId: String, label: String = tr("el addon de Stremio", "the Stremio addon")): List<StreamSource> =
        torrentio.streamsAt("$base/stream/$type/$streamId.json").streams.mapNotNull { s ->
            // Addon de torrents (sin url, con infoHash): como Torrentio, lo resuelve Real-Debrid.
            val link = s.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull s.toStreamSource(item)
            val hash = s.infoHash ?: "aio%08x".format(link.hashCode())
            s.copy(infoHash = hash).toStreamSource(item)?.copy(
                resolveUrl = link,
                debrid = label,
                isCached = s.name.contains("⚡") || s.name.contains("+]"),
            )
        }

    private suspend fun torrentioStreams(p: SourceProvider, item: CatalogItem, type: String, streamId: String): List<StreamSource> {
        val debrid = settings.debridService.first()
        val base = checkNotNull(p.baseUrl)
        // "Castellano primero": Torrentio también pone delante las releases en español (opción "language").
        // Peerflix usa el código ISO de la configuración de Torrentio; Torrentio, la abreviatura.
        val language = if (settings.spanishFirst.first()) {
            if (p == SourceProvider.PEERFLIX) "language=es-ES" else "language=spanish"
        } else null
        if (!debrid.viaTorrentio) {
            // Real-Debrid: el buscador sin clave (solo infoHash); la resolución es local con la API de RD.
            val streams = when {
                p != SourceProvider.TORRENTIO -> torrentio.streamsAt("$base/stream/$type/$streamId.json")
                language != null -> torrentio.streamsWithConfig(language, type, streamId)
                else -> torrentio.streams(type, streamId)
            }
            return streams.streams.mapNotNull { it.toStreamSource(item) }
        }
        // Otros debrids: solo los buscadores con la API de Torrentio saben pedir sus enlaces.
        if (!p.torrentioStyle) return emptyList()
        // Otros debrids: Torrentio con la clave, que marca la caché y da enlaces /resolve/.
        val key = store.getSecret(TokenStore.keyNameFor(debrid.apiService))?.takeIf { it.isNotBlank() }
            ?: error(tr("Falta la clave de ${debrid.title}. Añádela en Ajustes (rueda arriba a la derecha).", "${debrid.title} key missing. Add it in Settings (gear, top right)."))
        val config = listOfNotNull(language, torrentioConfig(debrid, key)).joinToString("|")
        val streams = if (p == SourceProvider.TORRENTIO) torrentio.streamsWithConfig(config, type, streamId).streams
        else {
            val res = torrentio.streamsAt("$base/$config/stream/$type/$streamId.json").streams
            if (res.isEmpty() && language != null) {
                // Fallback sin filtro de idioma por si el tracker no tenía la etiqueta es-ES
                val fallbackConfig = torrentioConfig(debrid, key)
                torrentio.streamsAt("$base/$fallbackConfig/stream/$type/$streamId.json").streams
            } else res
        }
        val sources = streams.mapNotNull { it.toDebridSource(item, debrid) }
        if (sources.isEmpty()) {
            streams.firstOrNull { it.isDebridError() }?.let { error(debridErrorMessage(it.title, debrid.title)) }
        }
        return sources
    }

    /** Fuente de Torrentio o Peerflix con debrid: hash y fileIdx salen del enlace; "[TB+]" o "[AD+]" = está en la caché. */
    private fun TorrentioStreamDto.toDebridSource(item: CatalogItem, debrid: DebridService): StreamSource? {
        val link = url?.takeIf { it.startsWith("http") } ?: return null
        val (hash, idx) = parseResolveUrl(link) ?: return null
        return copy(infoHash = hash, fileIdx = fileIdx ?: idx).toStreamSource(item)?.copy(
            resolveUrl = link,
            debrid = debrid.title,
            isCached = name.startsWith("[${debrid.shortName}+]") || name.contains("⚡") || name.contains("+]"),
        )
    }

    private suspend fun resolveImdb(item: CatalogItem): String? {
        val numericId = item.id.toIntOrNull() ?: return null
        return runCatching {
            if (item.type == MediaType.TV) {
                tmdb.tvExternalIds(numericId, key).imdb_id
            } else {
                tmdb.movieExternalIds(numericId, key).imdb_id
            }
        }.getOrNull()
    }

    /** Título en inglés (TMDB translations): es por el que indexan las webs de subtítulos. */
    private fun englishTitle(t: TmdbTranslationsDto?): String? =
        t?.translations?.firstOrNull { it.iso_639_1 == "en" }?.data
            ?.let { it.title ?: it.name }
            ?.takeIf { it.isNotBlank() }

    /** Logo del título (estilo Netflix): idioma de la app > inglés > sin idioma. Se descartan SVG (Coil sin decoder SVG). */
    private fun pickLogo(images: TmdbImagesDto?): String? {
        val logos = images?.logos.orEmpty().filter { it.file_path.isNotBlank() && !it.file_path.endsWith(".svg") }
        val best = logos.sortedWith(
            compareBy<TmdbImageDto> {
                when (it.iso_639_1) {
                    Lang.current.code -> 0
                    "en" -> 1
                    null -> 2
                    else -> 3
                }
            }.thenByDescending { it.vote_average },
        ).firstOrNull()
        return best?.let { "$IMG/w500${it.file_path}" }
    }

    // --- Mapeo TMDB -> CatalogItem ---
    // Pósters a w342: suficiente para tarjetas de ~124 dp y mucho más ligero que w500 para el Tegra.

    private fun poster(path: String) = "$IMG/w342$path"
    private fun backdrop(path: String) = "$IMG/w1280$path"

    private fun TmdbMovieDto.toCatalogItem(): CatalogItem = CatalogItem(
        id = id.toString(),
        imdbId = null,
        type = MediaType.MOVIE,
        title = title,
        overview = overview,
        posterUrl = poster_path?.let { poster(it) },
        backdropUrl = backdrop_path?.let { backdrop(it) },
        year = release_date?.take(4)?.toIntOrNull(),
        rating = vote_average.toFloat(),
    )

    private fun TmdbTvDto.toCatalogItem(): CatalogItem = CatalogItem(
        id = id.toString(),
        imdbId = null,
        type = MediaType.TV,
        title = name,
        overview = overview,
        posterUrl = poster_path?.let { poster(it) },
        backdropUrl = backdrop_path?.let { backdrop(it) },
        year = first_air_date?.take(4)?.toIntOrNull(),
        rating = vote_average.toFloat(),
    )

    private fun TmdbSearchResultDto.toCatalogItemOrNull(): CatalogItem? {
        val type = when (media_type) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.TV
            else -> return null
        }
        return CatalogItem(
            id = id.toString(),
            imdbId = null,
            type = type,
            title = title ?: name ?: "",
            overview = overview,
            posterUrl = poster_path?.let { poster(it) },
            backdropUrl = backdrop_path?.let { backdrop(it) },
            year = (release_date ?: first_air_date)?.take(4)?.toIntOrNull(),
            rating = vote_average.toFloat(),
        )
    }

    // --- Mapeo Torrentio stream -> StreamSource ---

    private fun TorrentioStreamDto.toStreamSource(item: CatalogItem): StreamSource? {
        val hash = infoHash ?: return null
        // Torrentio: `name` = "Torrentio\n4k DV | HDR", `title` = "Release\n👤 12 💾 4.5 GB ⚙️ Fuente".
        val text = title.ifBlank { description.orEmpty() }
        // VVC/x266: ningún decoder en la Shield → se descartan. AV1 va por CPU (dav1d) → se admite al final.
        if (UNSUPPORTED_CODEC.containsMatchIn("$name $text")) return null
        val isAv1 = AV1_CODEC.containsMatchIn("$name $text")
        // Medido en la Shield con dav1d: AV1 1080p 10 bits va fluido (<1 % de fotogramas perdidos); AV1 4K
        // satura los 4 núcleos y se queda en ~7 fps → fuera. Se mira el nombre de la release, no el `name`
        // de Torrentio (que marca como 4K los "DS4K"/"4KDS": 1080p reescalados desde 4K, que sí valen).
        if (isAv1 && AV1_4K.containsMatchIn(text) && !text.contains("1080p", ignoreCase = true)) return null
        val releaseName = text.lineSequence().firstOrNull { it.isNotBlank() } ?: item.title
        val fileName = behaviorHints?.filename
        val magnet = "magnet:?xt=urn:btih:${hash.lowercase()}&dn=${Uri.encode(fileName ?: releaseName)}"
        return StreamSource(
            id = "$hash:${fileIdx ?: 0}",
            title = releaseName,
            infoHash = hash,
            fileIdx = fileIdx,
            magnet = magnet,
            quality = Quality.fromName("$name $text"),
            hdrType = HdrType.fromName("$name $text"),
            sizeBytes = parseSize(text),
            seeders = availability ?: parseSeeders(text),
            isCached = false,
            fileName = fileName,
            // Torrentio pone las banderas en el texto; Peerflix, en el nombre ("Peerflix 🇪🇸 720p").
            languages = parseLanguages("$name\n$text"),
            softwareDecode = isAv1,
        )
    }

    /** Torrentio indica los idiomas con banderas emoji (🇪🇸 🇲🇽 🇬🇧…). Se convierten a códigos cortos. */
    private fun parseLanguages(text: String): List<String> {
        val codePoints = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            codePoints.add(cp)
            i += Character.charCount(cp)
        }
        val result = mutableListOf<String>()
        var j = 0
        while (j < codePoints.size - 1) {
            val a = codePoints[j]
            val b = codePoints[j + 1]
            if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) {
                val country = "${'A' + (a - 0x1F1E6)}${'A' + (b - 0x1F1E6)}"
                result += FLAG_TO_LANGUAGE[country] ?: country
                j += 2
            } else {
                j++
            }
        }
        if (result.isEmpty() && text.contains("multi", ignoreCase = true)) result += "MULTI"
        return result.distinct()
    }

    private fun parseSize(text: String): Long {
        val match = SIZE_REGEX.find(text) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase()
        val multiplier = when {
            unit.contains("g") -> 1024L * 1024L * 1024L
            unit.contains("m") -> 1024L * 1024L
            else -> 1024L
        }
        return (value * multiplier).toLong()
    }

    private fun parseSeeders(text: String): Int? =
        SEEDERS_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        /** Géneros TMDB que cuentan como infantiles en la búsqueda: animación, familia, infantil (series). */
        val KIDS_GENRES = setOf(16, 10751, 10762)
        const val PROVIDER_TIMEOUT_MS = 25_000L
        const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        val MAGNET = Regex("magnet:\\?xt=urn:btih:([a-fA-F0-9]{40})([^\"'<>\\s]*)")
        const val IMG = "https://image.tmdb.org/t/p"
        val SIZE_REGEX = Regex("([\\d.]+)\\s*(GB|GiB|MB|MiB)", RegexOption.IGNORE_CASE)
        val SEEDERS_REGEX = Regex("👤\\s*(\\d+)")
        val UNSUPPORTED_CODEC = Regex("(?<![a-z0-9])(x266|vvc|h\\.?266)(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val AV1_CODEC = Regex("(?<![a-z0-9])av1(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val AV1_4K = Regex("(?<![a-z0-9])(2160p?|4k|uhd)(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val FLAG_TO_LANGUAGE = mapOf(
            "ES" to "ES", "MX" to "LAT", "AR" to "LAT", "GB" to "EN", "US" to "EN",
            "IT" to "IT", "FR" to "FR", "DE" to "DE", "PT" to "PT", "BR" to "PT-BR",
            "RU" to "RU", "UA" to "UK", "JP" to "JA", "KR" to "KO", "CN" to "ZH",
            "IN" to "HI", "NL" to "NL", "PL" to "PL",
        )
    }
}

/** Validez de la caché de Inicio / Series / Películas. */
private const val HOME_CACHE_MS = 30 * 60 * 1000L

/** Se muestra en el Inicio (con "Reintentar") si no hay clave de TMDB. */
val MISSING_TMDB_KEY: String get() = tr("Falta la clave de TMDB. Añádela en Ajustes (rueda arriba a la derecha).", "TMDB key missing. Add it in Settings (gear, top right).")
