package com.shieldstream.tv.data.repository

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.strings.titleResource
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.Lang
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
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.IthilAccessRepository
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SettingsRepository
import kotlinx.coroutines.flow.first
import com.shieldstream.tv.model.AudioCodec
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
import com.shieldstream.tv.model.ShowAiring
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.network.di.ApiConfig
import com.shieldstream.tv.network.dto.TmdbImagesDto
import com.shieldstream.tv.network.dto.TmdbTranslationsDto
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
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
    private val texts: Texts,
    private val ithil: IthilLibraryRepository,
    private val ithilAccess: IthilAccessRepository,
    /** Copia en disco del último Inicio (null en tests). */
    private val diskCache: HomeDiskCache? = null,
) : CatalogRepository {

    private val kids: Boolean get() = profiles.active.value.kids

    private class Cached(val at: Long, val value: Any)
    private val homeCache = ConcurrentHashMap<String, Cached>()
    private val homeLocks = ConcurrentHashMap<String, Mutex>()

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var invalidatedAt = 0L

    override fun invalidateHome() {
        invalidatedAt = System.currentTimeMillis()
    }

    /** Guardado antes de [invalidateHome] y ya caducado: hay que esperar a la red. */
    private fun mustReload(savedAt: Long, now: Long) = savedAt < invalidatedAt && now - savedAt >= HOME_CACHE_MS
    // newKeySet() es de API 24 y la app admite desde la 21.
    private val refreshing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * Memoria → disco → red.
     * - Menos de [HOME_CACHE_MS]: se usa tal cual.
     * - Hasta [HOME_STALE_MS]: se enseña al instante lo guardado y se actualiza por detrás (para la próxima vez).
     * - Más antiguo, o nada guardado: se espera a la red; si falla, se tira de lo guardado aunque sea viejo.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : List<*>> cachedHome(
        baseKey: String,
        readDisk: (HomeDiskCache, String) -> HomeDiskCache.Entry<T>?,
        writeDisk: (HomeDiskCache, String, T) -> Unit,
        load: suspend () -> T,
    ): T {
        // Los títulos y sinopsis dependen del idioma de la app.
        val cacheKey = "${baseKey}_${Lang.current.name}"
        return homeLocks.getOrPut(cacheKey) { Mutex() }.withLock {
            val now = System.currentTimeMillis()
            val hit = homeCache[cacheKey]
            if (hit != null && now - hit.at < HOME_CACHE_MS) return@withLock hit.value as T
            if (hit != null && now - hit.at < HOME_STALE_MS && !mustReload(hit.at, now)) {
                refreshInBackground(cacheKey, writeDisk, load)
                return@withLock hit.value as T
            }
            val disk = diskCache?.takeIf { hit == null }?.let { cache -> withContext(Dispatchers.IO) { readDisk(cache, cacheKey) } }
                ?.takeIf { it.value.isNotEmpty() }
            if (disk != null && now - disk.savedAt < HOME_STALE_MS && !mustReload(disk.savedAt, now)) {
                homeCache[cacheKey] = Cached(disk.savedAt, disk.value)
                if (now - disk.savedAt >= HOME_CACHE_MS) refreshInBackground(cacheKey, writeDisk, load)
                return@withLock disk.value
            }
            val fresh = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fallback = (hit?.value as T?) ?: disk?.value
                if (fallback != null && fallback.isNotEmpty()) return@withLock fallback
                throw e
            }
            if (fresh.isNotEmpty()) {
                homeCache[cacheKey] = Cached(System.currentTimeMillis(), fresh)
                diskCache?.let { cache -> withContext(Dispatchers.IO) { writeDisk(cache, cacheKey, fresh) } }
            }
            fresh
        }
    }

    private fun <T : List<*>> refreshInBackground(
        cacheKey: String,
        writeDisk: (HomeDiskCache, String, T) -> Unit,
        load: suspend () -> T,
    ) {
        if (!refreshing.add(cacheKey)) return
        refreshScope.launch {
            try {
                val fresh = load()
                if (fresh.isNotEmpty()) {
                    homeCache[cacheKey] = Cached(System.currentTimeMillis(), fresh)
                    diskCache?.let { writeDisk(it, cacheKey, fresh) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("CatalogRepository", "Actualización en segundo plano de $cacheKey fallida: ${e.message}")
            } finally {
                refreshing.remove(cacheKey)
            }
        }
    }

    private suspend fun cachedRows(baseKey: String, load: suspend () -> List<CatalogRow>): List<CatalogRow> =
        cachedHome(baseKey, { c, k -> c.readRows(k) }, { c, k, v -> c.writeRows(k, v) }, load)

    private suspend fun cachedFeatured(baseKey: String, load: suspend () -> List<FeaturedItem>): List<FeaturedItem> =
        cachedHome(baseKey, { c, k -> c.readFeatured(k) }, { c, k, v -> c.writeFeatured(k, v) }, load)

    override suspend fun getFeatured(filter: HomeFilter): List<FeaturedItem> =
        if (kids) cachedFeatured("featured_kids_${filter.name}") { extras.kidsFeatured(filter) }
        else cachedFeatured("featured_${filter.name}") { extras.featured(filter) }

    override suspend fun getCategoryRows(category: Category, filter: HomeFilter): List<CatalogRow> =
        if (kids && category !in Category.KIDS_SAFE) emptyList() else extras.categoryRows(category, filter)

    private val key: String get() = store.tmdbKey.orEmpty()

    private class RowSpec(
        val id: String,
        val title: String,
        val style: RowStyle = RowStyle.POSTER,
        /** Carga una página (1, 2, 3…) de la fila; TMDB devuelve 20 títulos por página. */
        val load: suspend (page: Int) -> List<CatalogItem>,
    )

    override suspend fun getHome(filter: HomeFilter, maxItems: Int): List<CatalogRow> {
        // Las páginas que hacen falta para llenar cada fila con el máximo elegido en Ajustes (20 por página).
        val pages = ((maxItems + TMDB_PAGE_SIZE - 1) / TMDB_PAGE_SIZE).coerceIn(1, MAX_HOME_PAGES)
        return if (kids) cachedRows("home_kids_${filter.name}_p$pages") { loadHome(kidsRowsFor(filter, pages), pages) }
        else cachedRows("home_${filter.name}_p$pages") { loadHome(rowsFor(filter), pages) }
    }

    private suspend fun loadHome(specs: List<RowSpec>, pages: Int): List<CatalogRow> = coroutineScope {
        if (key.isBlank()) throw IllegalStateException(texts.get(StringsR.string.data_tmdb_key_missing_add_it_in_settings))
        specs
            .map { spec ->
                async {
                    runCatching {
                        val pageCount = if (spec.style == RowStyle.TOP10) 1 else pages
                        val items = (1..pageCount)
                            .map { page ->
                                // La primera página es obligatoria; si falla una de las siguientes, la fila se queda con lo que haya.
                                async { if (page == 1) spec.load(page) else runCatching { spec.load(page) }.getOrDefault(emptyList()) }
                            }
                            .awaitAll()
                            .flatten()
                            .distinctBy { it.type to it.id }
                            .filter { it.posterUrl != null }
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
            RowSpec("top10_movies", texts.get(StringsR.string.data_top_10_movies_week), RowStyle.TOP10) { page -> tmdb.trendingMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("popular_movies", texts.get(StringsR.string.data_popular_movies)) { page -> tmdb.popularMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("popular_tv", texts.get(StringsR.string.data_popular_tv_shows)) { page -> tmdb.popularTv(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("top10_tv", texts.get(StringsR.string.data_top_10_tv_shows_week), RowStyle.TOP10) { page -> tmdb.trendingTv(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("now_playing", texts.get(StringsR.string.data_now_in_theaters)) { page -> tmdb.nowPlayingMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("action", texts.get(StringsR.string.data_action)) { page -> tmdb.discoverMovies(key, "28", page = page).results.map { it.toCatalogItem() } },
            RowSpec("scifi", texts.get(StringsR.string.data_sci_fi)) { page -> tmdb.discoverMovies(key, "878", page = page).results.map { it.toCatalogItem() } },
            RowSpec("comedy", texts.get(StringsR.string.data_comedies)) { page -> tmdb.discoverMovies(key, "35", page = page).results.map { it.toCatalogItem() } },
            RowSpec("top_movies", texts.get(StringsR.string.data_acclaimed_movies)) { page -> tmdb.topRatedMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("top_tv", texts.get(StringsR.string.data_acclaimed_tv_shows)) { page -> tmdb.topRatedTv(key, page = page).results.map { it.toCatalogItem() } },
        )
        HomeFilter.MOVIES -> listOf(
            RowSpec("m_top10", texts.get(StringsR.string.data_top_10_movies_week), RowStyle.TOP10) { page -> tmdb.trendingMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_popular", texts.get(StringsR.string.data_popular)) { page -> tmdb.popularMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_now", texts.get(StringsR.string.data_now_in_theaters)) { page -> tmdb.nowPlayingMovies(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_action", texts.get(StringsR.string.data_action)) { page -> tmdb.discoverMovies(key, "28", page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_scifi", texts.get(StringsR.string.data_sci_fi)) { page -> tmdb.discoverMovies(key, "878", page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_thriller", "Thriller") { page -> tmdb.discoverMovies(key, "53", page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_horror", texts.get(StringsR.string.data_horror)) { page -> tmdb.discoverMovies(key, "27", page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_comedy", texts.get(StringsR.string.data_comedies)) { page -> tmdb.discoverMovies(key, "35", page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_animation", texts.get(StringsR.string.data_animation)) { page -> tmdb.discoverMovies(key, "16", page = page).results.map { it.toCatalogItem() } },
            RowSpec("m_top", texts.get(StringsR.string.data_critically_acclaimed)) { page -> tmdb.topRatedMovies(key, page = page).results.map { it.toCatalogItem() } },
        )
        HomeFilter.TV -> listOf(
            RowSpec("t_top10", texts.get(StringsR.string.data_top_10_tv_shows_week), RowStyle.TOP10) { page -> tmdb.trendingTv(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_popular", texts.get(StringsR.string.data_popular)) { page -> tmdb.popularTv(key, page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_drama", "Dramas") { page -> tmdb.discoverTv(key, "18", page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_crime", texts.get(StringsR.string.data_crime)) { page -> tmdb.discoverTv(key, "80", page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_scifi", texts.get(StringsR.string.data_sci_fi_fantasy)) { page -> tmdb.discoverTv(key, "10765", page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_action", texts.get(StringsR.string.data_action_adventure)) { page -> tmdb.discoverTv(key, "10759", page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_comedy", texts.get(StringsR.string.data_comedies)) { page -> tmdb.discoverTv(key, "35", page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_animation", texts.get(StringsR.string.data_animation)) { page -> tmdb.discoverTv(key, "16", page = page).results.map { it.toCatalogItem() } },
            RowSpec("t_top", texts.get(StringsR.string.data_critically_acclaimed)) { page -> tmdb.topRatedTv(key, page = page).results.map { it.toCatalogItem() } },
        )
    }

    override suspend fun trending(): List<CatalogItem> =
        if (kids) kidsMovies(mapOf()).take(20)
        else tmdb.trendingAll(key).results.mapNotNull { it.toCatalogItemOrNull() }.filter { it.posterUrl != null }

    override suspend fun search(query: String): List<CatalogItem> =
        tmdb.search(key, query).results
            .filter { !kids || it.genre_ids.any { g -> g in KIDS_GENRES } }
            .mapNotNull { it.toCatalogItemOrNull() } // descarta personas
            .filter { it.posterUrl != null }

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

    private fun kidsRowsFor(filter: HomeFilter, pages: Int): List<RowSpec> {
        // Las filas "Más…" siguen donde acaban las otras para no repetir títulos.
        fun p(page: Int) = mapOf("page" to page.toString())
        fun more(page: Int) = mapOf("page" to (pages + page).toString())
        val topRated = mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300")
        val movies = listOf(
            RowSpec("k_movies", texts.get(StringsR.string.data_kids_movies)) { page -> kidsMovies(p(page)) },
            RowSpec("k_animation", texts.get(StringsR.string.data_animation)) { page -> kidsMovies(p(page), genres = "16") },
            RowSpec("k_family", texts.get(StringsR.string.data_for_the_whole_family)) { page -> kidsMovies(mapOf("certification.lte" to "G") + p(page), genres = "10751") },
            RowSpec("k_top_movies", texts.get(StringsR.string.data_top_rated)) { page -> kidsMovies(topRated + p(page)) },
            RowSpec("k_more_movies", texts.get(StringsR.string.data_more_movies)) { page -> kidsMovies(more(page)) },
        )
        val shows = listOf(
            RowSpec("k_tv", texts.get(StringsR.string.data_kids_shows)) { page -> kidsTv(p(page)) },
            RowSpec("k_cartoons", texts.get(StringsR.string.data_cartoons)) { page -> kidsTv(p(page), genres = "10762,16") },
            RowSpec("k_more_tv", texts.get(StringsR.string.data_more_shows)) { page -> kidsTv(more(page)) },
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
                    .map { Season(it.season_number, it.name.ifBlank { texts.get(StringsR.string.data_season_name, it.season_number) }, it.episode_count) },
                latestEpisode = d.last_episode_to_air?.toEpisode(),
                nextEpisode = d.next_episode_to_air?.toEpisode(),
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

    private class CachedAiring(val at: Long, val value: ShowAiring)
    private val airingCache = ConcurrentHashMap<String, CachedAiring>()
    private val airingLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun getShowAiring(id: String): ShowAiring? {
        val tmdbId = id.toIntOrNull() ?: return null
        return airingLocks.getOrPut(id) { Mutex() }.withLock {
            airingCache[id]?.takeIf { System.currentTimeMillis() - it.at < AIRING_CACHE_MS }?.let { return@withLock it.value }
            val d = tmdb.tvDetails(tmdbId, key, append = "external_ids")
            val item = CatalogItem(
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
            ShowAiring(
                item = item,
                latestEpisode = d.last_episode_to_air?.toEpisode(),
                nextEpisode = d.next_episode_to_air?.toEpisode(),
                seasons = d.seasons
                    .filter { it.season_number > 0 && it.episode_count > 0 }
                    .map { Season(it.season_number, it.name, it.episode_count) },
            ).also {
                itemCache["TV_$id"] = item
                airingCache[id] = CachedAiring(System.currentTimeMillis(), it)
            }
        }
    }

    private class CachedEpisodes(val at: Long, val value: List<Episode>)
    private val episodesCache = ConcurrentHashMap<String, CachedEpisodes>()
    private val episodeLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun getEpisodes(item: CatalogItem, season: Int): List<Episode> {
        val id = item.id.toIntOrNull() ?: return emptyList()
        val cacheKey = "$id:$season"
        return episodeLocks.getOrPut(cacheKey) { Mutex() }.withLock {
            episodesCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.at < AIRING_CACHE_MS }?.let { return@withLock it.value }
            tmdb.tvSeason(id, season, key).episodes.mapNotNull { it.toEpisode(defaultSeason = season) }
                .also { episodes -> if (episodes.isNotEmpty()) episodesCache[cacheKey] = CachedEpisodes(System.currentTimeMillis(), episodes) }
        }
    }

    private fun com.shieldstream.tv.network.dto.TmdbEpisodeDto.toEpisode(defaultSeason: Int? = null): Episode? {
        val seasonNumber = season_number.takeIf { it > 0 } ?: defaultSeason ?: return null
        if (episode_number <= 0) return null
        return Episode(
            season = seasonNumber,
            number = episode_number,
            title = name.ifBlank { texts.get(StringsR.string.data_episode_name, episode_number) },
            overview = overview,
            stillUrl = still_path?.let { TmdbImages.still(it) },
            runtimeMinutes = runtime,
            airDate = air_date,
        )
    }

    override fun getStreams(item: CatalogItem, episode: Episode?): Flow<StreamResults> = channelFlow {
        val aioConfigured = !store.getSecret(TokenStore.keyNameFor(ApiService.AIOSTREAMS)).isNullOrBlank()
        val enabled = settings.sourceProviders.first()
        val providers = SourceProvider.entries.filter {
            it in enabled &&
                (it != SourceProvider.AIOSTREAMS || aioConfigured) &&
                (it != SourceProvider.ITHIL || (ithilAccess.isUnlocked && ithil.isReady()))
        }
        val requiresImdb = providers.any { it != SourceProvider.ITHIL }
        val imdbId = if (requiresImdb) {
            item.imdbId ?: resolveImdb(item) ?: error(texts.get(StringsR.string.data_imdb_id_missing, item.title))
        } else {
            null
        }
        val (type, streamId) = if (item.type == MediaType.TV) {
            val ep = episode ?: error(texts.get(StringsR.string.data_pick_an_episode))
            "series" to if (requiresImdb) "$imdbId:${ep.season}:${ep.number}" else ""
        } else {
            "movie" to imdbId.orEmpty()
        }
        val done = LinkedHashMap<SourceProvider, Result<List<StreamSource>>>()
        val lock = Mutex()
        fun snapshot(): StreamResults {
            val sources = providers.flatMap { p ->
                done[p]?.getOrNull().orEmpty().map { it.copy(id = "${p.name}:${it.id}", provider = texts.get(p.sectionResource)) }
            }.distinctBy { it.provider + ":" + if (it.resolveUrl == null) it.infoHash.lowercase() else it.id }
            val sections = providers.groupBy { texts.get(it.sectionResource) }
            val withSources = sources.map { it.provider }.toSet()
            val pending = sections.filter { (_, ps) -> ps.any { it !in done } }.keys
            val unavailable = sections.mapNotNull { (section, ps) ->
                when {
                    section in pending || section in withSources -> null
                    ps.all { done[it]?.isFailure == true } -> section to texts.get(StringsR.string.data_not_responding_right_now)
                    else -> section to texts.get(StringsR.string.data_no_sources_for_this)
                }
            }.toMap()
            return StreamResults(sources, unavailable, pending)
        }
        send(snapshot())
        coroutineScope {
            providers.map { p ->
                launch {
                    val r = try {
                        Result.success(withTimeout(PROVIDER_TIMEOUT_MS) { providerStreams(p, item, type, streamId, episode) })
                    } catch (e: TimeoutCancellationException) {
                        Result.failure(e)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    r.exceptionOrNull()?.let { Log.w("Catalog", "${texts.get(p.titleResource)} falló: ${it.message}") }
                    lock.withLock {
                        done[p] = r
                        send(snapshot())
                    }
                }
            }.joinAll()
        }
        if (snapshot().sources.isEmpty()) {
            val failed = done.filter { it.value.isFailure }
            failed.values.firstNotNullOfOrNull { r -> r.exceptionOrNull()?.takeIf { it is IllegalStateException && it !is kotlinx.coroutines.CancellationException } }?.let { throw it }
            if (failed.isNotEmpty()) {
                val names = failed.keys.joinToString { texts.get(it.titleResource) }
                error(texts.get(StringsR.string.data_provider_unavailable, names))
            }
        }
    }

    private suspend fun providerStreams(p: SourceProvider, item: CatalogItem, type: String, streamId: String, episode: Episode?): List<StreamSource> =
        when (p) {
            SourceProvider.ITHIL -> ithilSources(ithil, item, episode)
            SourceProvider.AIOSTREAMS -> coroutineScope {
                val bases = aioStreamBases(store.getSecret(TokenStore.keyNameFor(ApiService.AIOSTREAMS)).orEmpty())
                bases.map { base ->
                    val label = addonLabel(base, bases.size)
                    async {
                        runCatching { aioStreams(base, item, type, streamId, label = label) }
                            .onFailure { Log.w("Catalog", "Addon $label falló (${it.javaClass.simpleName})") }
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

    private suspend fun scraperStreams(p: SourceProvider, item: CatalogItem, streamId: String): List<StreamSource> {
        if (!settings.debridService.first().supportsDirectMagnetResolution) return emptyList()
        val parts = streamId.split(':')
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val episodeTag = if (season != null && episode != null) "S%02dE%02d".format(season, episode) else null
        val seasonTag = season?.let { "S%02d".format(it) }
        val base = checkNotNull(p.baseUrl)
        val title = releaseTitle(item)
        val wanted = normalize(title)
        val queries = if (episodeTag != null) listOf("$title $episodeTag", "$title $seasonTag") else listOf("$title ${item.year ?: ""}".trim())
        val absolute = if (season != null && episode != null) absoluteEpisode(item, season, episode) else null
        val anime = item.genres.any { it.contains("anim", ignoreCase = true) } || animeSeasons.containsKey(item.key)
        val allQueries = if (absolute != null) queries + "$title ${"%02d".format(absolute)}" else queries
        val packRx = season?.let { Regex("(?i)(\\bS0*$it\\b(?!\\s*E\\d)|season\\s*0*$it\\b|temporada\\s*0*$it\\b|complete)") }
        var seen = 0
        val titleRx = Regex(
            "^(?:\\[[^\\]]*\\]\\s*)*" + title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
                .joinToString("[\\W_]+") { Regex.escape(it) } +
                "[\\W_]+(?:\\(?(?:19|20)\\d{2}|s\\d|season|temporada|complete|\\d{3,4}p|\\d+x\\d)",
            RegexOption.IGNORE_CASE,
        )
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
        Log.w("Catalog", "${texts.get(p.titleResource)}: ${found.size} aceptadas de $seen (consultas: $allQueries)")
        return found.distinctBy { it.infoHash }.mapNotNull { it.toStreamSource(item) }
            .map { if (season != null) it.copy(season = season, episode = episode) else it }
            .sortedByDescending { it.seeders ?: 0 }
    }

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

    private suspend fun releaseTitle(item: CatalogItem): String = releaseTitleLock.withLock {
        releaseTitles[item.key]?.let { return@withLock it }
        val title = runCatching {
            val id = item.id.toInt()
            if (item.type == MediaType.TV) {
                tmdb.tvDetails(id, key).also { d ->
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

    private val animeSeasons = java.util.concurrent.ConcurrentHashMap<String, Map<Int, Int>>()

    private val seasonStarts = java.util.concurrent.ConcurrentHashMap<String, Int>()

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
        title = "$name\n👤 $seeders 💾 ${"%.2f".format(java.util.Locale.US, size / 1_073_741_824.0)} GB ⚙️ ${texts.get(p.titleResource)}",
        infoHash = hash.lowercase(),
    )

    private fun addonLabel(base: String, count: Int): String =
        if (count <= 1) texts.get(StringsR.string.data_the_stremio_addon)
        else base.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('.')

    private suspend fun aioStreams(base: String, item: CatalogItem, type: String, streamId: String, label: String = texts.get(StringsR.string.data_the_stremio_addon)): List<StreamSource> {
        val streams = torrentio.streamsAt("$base/stream/$type/$streamId.json").streams
        streams.firstOrNull { it.isDebridError() }?.let { error(debridErrorMessage(it.title, label, texts)) }
        return streams.mapNotNull { s ->
            val link = s.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull s.toStreamSource(item)
            val hash = s.infoHash ?: "aio%08x".format(link.hashCode())
            s.copy(infoHash = hash).toStreamSource(item)?.copy(
                resolveUrl = link,
                debrid = label,
                isCached = s.name.contains("⚡") || s.name.contains("+]"),
            )
        }
    }

    private suspend fun torrentioStreams(p: SourceProvider, item: CatalogItem, type: String, streamId: String): List<StreamSource> {
        val debrid = settings.debridService.first()
        val base = checkNotNull(p.baseUrl)
        val language = if (settings.spanishFirst.first()) "language=spanish" else null
        if (!debrid.viaTorrentio) {
            val streams = when {
                p != SourceProvider.TORRENTIO -> torrentio.streamsAt("$base/stream/$type/$streamId.json")
                language != null -> torrentio.streamsWithConfig(language, type, streamId)
                else -> torrentio.streams(type, streamId)
            }
            return streams.streams.mapNotNull { it.toStreamSource(item) }
        }
        if (p == SourceProvider.PEERFLIX) {
            val key = store.getSecret(TokenStore.keyNameFor(debrid.apiService))?.takeIf { it.isNotBlank() }
                ?: error(texts.get(StringsR.string.data_debrid_key_missing, debrid.title))
            return aioStreams("$base/${torrentioConfig(debrid, key)}", item, type, streamId, label = debrid.title)
        }
        if (!p.torrentioStyle) return emptyList()
        val key = store.getSecret(TokenStore.keyNameFor(debrid.apiService))?.takeIf { it.isNotBlank() }
            ?: error(texts.get(StringsR.string.data_debrid_key_missing, debrid.title))
        val config = listOfNotNull(language, torrentioConfig(debrid, key)).joinToString("|")
        val streams = if (p == SourceProvider.TORRENTIO) torrentio.streamsWithConfig(config, type, streamId).streams
        else torrentio.streamsAt("$base/$config/stream/$type/$streamId.json").streams
        val sources = streams.mapNotNull { it.toDebridSource(item, debrid) }
        if (sources.isEmpty()) {
            streams.firstOrNull { it.isDebridError() }?.let { error(debridErrorMessage(it.title, debrid.title, texts)) }
        }
        return sources
    }

    private fun TorrentioStreamDto.toDebridSource(item: CatalogItem, debrid: DebridService): StreamSource? {
        val link = url?.takeIf { it.contains("/resolve/") } ?: return null
        val (hash, idx) = parseResolveUrl(link) ?: return null
        return copy(infoHash = hash, fileIdx = fileIdx ?: idx).toStreamSource(item)?.copy(
            resolveUrl = link,
            debrid = debrid.title,
            isCached = name.startsWith("[${debrid.shortName}+]"),
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

    private fun englishTitle(t: TmdbTranslationsDto?): String? =
        t?.translations?.firstOrNull { it.iso_639_1 == "en" }?.data
            ?.let { it.title ?: it.name }
            ?.takeIf { it.isNotBlank() }

    private fun pickLogo(images: TmdbImagesDto?): String? = TmdbImages.logo(images, Lang.current.code)

    private fun poster(path: String) = TmdbImages.poster(path)
    private fun backdrop(path: String) = TmdbImages.backdrop(path)

    // --- Mapeo Torrentio stream -> StreamSource ---

    private fun TorrentioStreamDto.toStreamSource(item: CatalogItem): StreamSource? {
        val hash = infoHash ?: return null
        val text = title.ifBlank { description.orEmpty() }
        if (UNSUPPORTED_CODEC.containsMatchIn("$name $text")) return null
        val isAv1 = AV1_CODEC.containsMatchIn("$name $text")
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
            languages = parseLanguages("$name\n$text"),
            videoCodec = if (isAv1) VideoCodec.AV1 else null,
            audioCodecs = AudioCodec.parse("$name $text ${fileName.orEmpty()}"),
            softwareDecode = isAv1,
        )
    }

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
        val KIDS_GENRES = setOf(16, 10751, 10762)
        const val PROVIDER_TIMEOUT_MS = 25_000L
        const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        val MAGNET = Regex("magnet:\\?xt=urn:btih:([a-fA-F0-9]{40})([^\"'<>\\s]*)")
        val SIZE_REGEX = Regex("([\\d.]+)\\s*(GB|GiB|MB|MiB)", RegexOption.IGNORE_CASE)
        val SEEDERS_REGEX = Regex("👤\\s*(\\d+)")
        val UNSUPPORTED_CODEC = Regex("(?<![a-z0-9])(x266|vvc|h\\.?266)(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val AV1_CODEC = Regex("(?<![a-z0-9])av1(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val FLAG_TO_LANGUAGE = mapOf(
            "ES" to "ES", "MX" to "LAT", "AR" to "LAT", "GB" to "EN", "US" to "EN",
            "IT" to "IT", "FR" to "FR", "DE" to "DE", "PT" to "PT", "BR" to "PT-BR",
            "RU" to "RU", "UA" to "UK", "JP" to "JA", "KR" to "KO", "CN" to "ZH",
            "IN" to "HI", "NL" to "NL", "PL" to "PL",
        )
    }
}

private const val HOME_CACHE_MS = 30 * 60 * 1000L
/** Hasta cuándo lo guardado en disco se enseña al abrir mientras se actualiza por detrás. */
private const val HOME_STALE_MS = 24 * 60 * 60 * 1000L
private const val TMDB_PAGE_SIZE = 20
/** 5 páginas = 100 títulos, el máximo de "Elementos por fila de Inicio". */
private const val MAX_HOME_PAGES = 5

private const val AIRING_CACHE_MS = 6 * 60 * 60 * 1000L
