package com.shieldstream.tv.data.repository

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.tr
import android.content.Context
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.network.dto.TmdbContentRatingsDto
import com.shieldstream.tv.network.dto.TmdbImagesDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbReleaseDatesDto
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbTvDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Portada grande (destacados de la semana) y pantallas de categoría del Inicio.
 * Separado de CatalogRepositoryImpl para no engordarlo; este delega aquí.
 */
@Singleton
class HomeExtrasSource @Inject constructor(
    private val tmdb: TmdbApi,
    @ApplicationContext context: Context,
    private val store: TokenStore,
) {
    /** Clave de TMDB de Ajustes (ver CatalogRepositoryImpl). */
    private val key: String get() = store.tmdbKey.orEmpty()
    private val prefs = context.getSharedPreferences("featured", Context.MODE_PRIVATE)
    private val keywordIds = ConcurrentHashMap<String, Int>()

    // =====================================================================================
    // Portada grande
    // =====================================================================================

    private data class Pick(val type: MediaType, val id: Int, val badge: FeaturedBadge)

    /**
     * Destacados de la semana. Se eligen el primer día que se abre la app en la semana (lunes a domingo) y
     * se guardan: toda la semana sale lo mismo (con datos frescos: nota, logo…). La semana siguiente, nuevos.
     */
    suspend fun featured(filter: HomeFilter): List<FeaturedItem> {
        val week = weekKey()
        val prefKey = "week_${filter.name}"
        val picks = readPicks(prefs.getString(prefKey, null), week) ?: choosePicks(filter).also { picks ->
            if (picks.isNotEmpty()) prefs.edit().putString(prefKey, writePicks(week, picks)).apply()
        }
        return coroutineScope {
            picks.map { pick -> async { runCatching { loadFeatured(pick) }.getOrNull() } }.awaitAll().filterNotNull()
        }
    }

    /**
     * Portada del perfil infantil: lo más popular para niños (películas de animación/familia hasta PG en EE. UU.
     * y series del género Infantil), no las tendencias generales. Sin guardar por semanas: va en la caché del Inicio.
     */
    suspend fun kidsFeatured(filter: HomeFilter): List<FeaturedItem> = coroutineScope {
        val movieParams = mapOf(
            "with_genres" to "16|10751",
            "certification_country" to "US",
            "certification.lte" to "PG",
            "include_adult" to "false",
        )
        val tvParams = mapOf("with_genres" to "10762", "include_adult" to "false")
        val popular = mapOf("sort_by" to "popularity.desc")
        val acclaimed = mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300")
        val wantMovies = filter != HomeFilter.TV
        val wantTv = filter != HomeFilter.MOVIES
        val movies = async { if (wantMovies) runCatching { tmdb.discoverMovieQuery(key, movieParams + popular).results }.getOrDefault(emptyList()) else emptyList() }
        val shows = async { if (wantTv) runCatching { tmdb.discoverTvQuery(key, tvParams + popular).results }.getOrDefault(emptyList()) else emptyList() }
        val bestMovies = async { if (wantMovies) runCatching { tmdb.discoverMovieQuery(key, movieParams + acclaimed).results }.getOrDefault(emptyList()) else emptyList() }
        val m = movies.await().filter { it.backdrop_path != null }.map { Pick(MediaType.MOVIE, it.id, FeaturedBadge.POPULAR) }
        val s = shows.await().filter { it.backdrop_path != null }.map { Pick(MediaType.TV, it.id, FeaturedBadge.POPULAR) }
        val best = bestMovies.await().filter { it.backdrop_path != null }.map { Pick(MediaType.MOVIE, it.id, FeaturedBadge.ACCLAIMED) }
        val picks = ArrayList<Pick>()
        fun add(p: Pick?, badge: FeaturedBadge) {
            if (p != null && picks.none { it.type == p.type && it.id == p.id }) picks += p.copy(badge = badge)
        }
        fun MutableList<Pick>.next() = firstOrNull { p -> picks.none { it.type == p.type && it.id == p.id } }
        val mm = m.toMutableList(); val ss = s.toMutableList()
        when (filter) {
            HomeFilter.ALL -> {
                add(mm.next(), FeaturedBadge.POPULAR)
                add(ss.next(), FeaturedBadge.SERIES_NOW)
                add(mm.next(), FeaturedBadge.MOVIE_NOW)
                add(best.toMutableList().next(), FeaturedBadge.ACCLAIMED)
                add(ss.next(), FeaturedBadge.RISING)
            }
            HomeFilter.MOVIES -> {
                add(mm.next(), FeaturedBadge.POPULAR)
                add(mm.next(), FeaturedBadge.MOVIE_NOW)
                add(best.toMutableList().next(), FeaturedBadge.ACCLAIMED)
                add(mm.next(), FeaturedBadge.RISING)
            }
            HomeFilter.TV -> {
                add(ss.next(), FeaturedBadge.POPULAR)
                add(ss.next(), FeaturedBadge.SERIES_NOW)
                add(ss.next(), FeaturedBadge.RISING)
            }
        }
        picks.map { pick -> async { runCatching { loadFeatured(pick) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    /** Tendencias de la semana (TMDB) → hasta 5 destacados con su "motivo". */
    private suspend fun choosePicks(filter: HomeFilter): List<Pick> {
        val trending = (tmdb.trendingAllWeek(key, 1).results + runCatching { tmdb.trendingAllWeek(key, 2).results }.getOrDefault(emptyList()))
            .filter { it.media_type == "movie" || it.media_type == "tv" }
            .filter { it.backdrop_path != null && it.poster_path != null }
            .filter {
                when (filter) {
                    HomeFilter.ALL -> true
                    HomeFilter.MOVIES -> it.media_type == "movie"
                    HomeFilter.TV -> it.media_type == "tv"
                }
            }
            .distinctBy { it.media_type + it.id }
        if (trending.isEmpty()) return emptyList()

        val today = isoDate(Date())
        val recentLimit = isoDate(Date(System.currentTimeMillis() - 60L * 24 * 3600 * 1000))
        val used = HashSet<String>()
        val picks = ArrayList<Pick>()
        fun add(dto: TmdbSearchResultDto?, badge: FeaturedBadge) {
            if (dto == null || !used.add(dto.media_type + dto.id)) return
            picks += Pick(if (dto.media_type == "tv") MediaType.TV else MediaType.MOVIE, dto.id, badge)
        }
        fun dateOf(d: TmdbSearchResultDto) = (d.release_date ?: d.first_air_date).orEmpty()
        fun unused(predicate: (TmdbSearchResultDto) -> Boolean) =
            trending.firstOrNull { it.media_type + it.id !in used && predicate(it) }

        // 1. Recién llegada: lo más visto de la semana estrenado en los últimos 60 días.
        add(unused { dateOf(it) in recentLimit..today }, FeaturedBadge.NEW)
        // 2. La más popular: el número 1 de la semana.
        add(unused { true }, FeaturedBadge.POPULAR)
        // 3. Del momento: la serie / película más vista que aún no haya salido.
        when (filter) {
            HomeFilter.ALL -> {
                add(unused { it.media_type == "tv" }, FeaturedBadge.SERIES_NOW)
                add(unused { it.media_type == "movie" }, FeaturedBadge.MOVIE_NOW)
            }
            else -> add(unused { true }, FeaturedBadge.RISING)
        }
        // 4. Aclamada: la mejor nota entre las tendencias con suficientes votos.
        add(
            trending.filter { it.media_type + it.id !in used && it.vote_count >= 300 }.maxByOrNull { it.vote_average },
            FeaturedBadge.ACCLAIMED,
        )
        return picks.take(5)
    }

    private suspend fun loadFeatured(pick: Pick): FeaturedItem? {
        return if (pick.type == MediaType.MOVIE) loadFeaturedMovie(pick) else loadFeaturedSeries(pick)
    }

    private suspend fun loadFeaturedMovie(pick: Pick): FeaturedItem? {
        val d = tmdb.movieDetails(pick.id, key, append = "images,release_dates")
        return FeaturedItem(
            item = CatalogItem(
                id = pick.id.toString(),
                imdbId = d.imdb_id,
                type = MediaType.MOVIE,
                title = d.title,
                overview = d.overview,
                posterUrl = d.poster_path?.let { "$IMG/w342$it" },
                backdropUrl = d.backdrop_path?.let { "$IMG/w1280$it" } ?: return null,
                year = d.release_date?.take(4)?.toIntOrNull(),
                rating = d.vote_average.toFloat(),
                genres = d.genres.map { it.name },
            ),
            badge = pick.badge,
            logoUrl = pickLogo(d.images),
            genre = d.genres.firstOrNull()?.name,
            durationText = d.runtime?.takeIf { it > 0 }?.let { formatRuntime(it) },
            certification = movieCertification(d.release_dates),
        )
    }

    private suspend fun loadFeaturedSeries(pick: Pick): FeaturedItem? {
        val d = tmdb.tvDetails(pick.id, key, append = "images,content_ratings,external_ids")
        val seasons = d.seasons.count { it.season_number > 0 }.takeIf { it > 0 } ?: d.number_of_seasons
        return FeaturedItem(
            item = CatalogItem(
                id = pick.id.toString(),
                imdbId = d.external_ids?.imdb_id,
                type = MediaType.TV,
                title = d.name,
                overview = d.overview,
                posterUrl = d.poster_path?.let { "$IMG/w342$it" },
                backdropUrl = d.backdrop_path?.let { "$IMG/w1280$it" } ?: return null,
                year = d.first_air_date?.take(4)?.toIntOrNull(),
                rating = d.vote_average.toFloat(),
                genres = d.genres.map { it.name },
            ),
            badge = pick.badge,
            logoUrl = pickLogo(d.images),
            genre = d.genres.firstOrNull()?.name,
            durationText = seasons.takeIf { it > 0 }?.let { if (it == 1) tr("1 temporada", "1 season") else tr("$it temporadas", "$it seasons") },
            certification = tvCertification(d.content_ratings),
        )
    }

    private fun readPicks(raw: String?, week: String): List<Pick>? {
        if (raw == null) return null
        val parts = raw.split('|')
        if (parts.size < 2 || parts[0] != week) return null
        return parts.drop(1).mapNotNull { token ->
            val (type, id, badge) = token.split(':').takeIf { it.size == 3 } ?: return@mapNotNull null
            Pick(
                type = runCatching { MediaType.valueOf(type) }.getOrNull() ?: return@mapNotNull null,
                id = id.toIntOrNull() ?: return@mapNotNull null,
                badge = runCatching { FeaturedBadge.valueOf(badge) }.getOrNull() ?: return@mapNotNull null,
            )
        }.takeIf { it.isNotEmpty() }
    }

    private fun writePicks(week: String, picks: List<Pick>) =
        week + "|" + picks.joinToString("|") { "${it.type.name}:${it.id}:${it.badge.name}" }

    /** Año + número de semana (lunes a domingo). */
    private fun weekKey(): String {
        val c = Calendar.getInstance(Locale("es", "ES"))
        c.firstDayOfWeek = Calendar.MONDAY
        c.minimalDaysInFirstWeek = 4
        return "${c.get(Calendar.YEAR)}-W${c.get(Calendar.WEEK_OF_YEAR)}"
    }

    // =====================================================================================
    // Categorías
    // =====================================================================================

    /** Consulta de TMDB discover para películas o series. Las palabras clave se traducen a id al usarlas. */
    private data class Query(
        val genres: String? = null,
        val keyword: String? = null,
        val language: String? = null,
        /** País de origen (ES, KR…): with_origin_country. */
        val country: String? = null,
    )

    private data class Spec(val movie: Query?, val tv: Query?)

    private fun spec(category: Category): Spec = when (category) {
        Category.ACCLAIMED -> Spec(Query(), Query()) // filas propias: todas con nota alta y suficientes votos
        Category.DRAMAS -> Spec(Query("18"), Query("18"))
        Category.THRILLERS -> Spec(Query("53"), Query("9648")) // series: misterio (TMDB no tiene "thriller" en TV)
        Category.CRIME -> Spec(Query("80"), Query("80"))
        Category.ACTION -> Spec(Query("28"), Query("10759"))
        Category.BOOKS -> Spec(Query(keyword = "based on novel or book"), Query(keyword = "based on novel or book"))
        Category.COMEDY -> Spec(Query("35"), Query("35"))
        Category.HALLOWEEN -> Spec(Query(keyword = "halloween"), Query(keyword = "halloween"))
        Category.REALITY -> Spec(null, Query("10764"))
        Category.FANTASY -> Spec(Query("14"), Query("10765"))
        Category.SCIFI -> Spec(Query("878"), Query("10765"))
        Category.ANIMATION -> Spec(Query("16"), Query("16"))
        Category.ROMANCE -> Spec(Query("10749"), Query(keyword = "romance"))
        Category.FAMILY -> Spec(Query("10751"), Query("10751"))
        Category.ANIME -> Spec(Query("16", language = "ja"), Query("16", language = "ja"))
        Category.DOCUMENTARY -> Spec(Query("99"), Query("99"))
        Category.ASTROLOGY -> Spec(Query(keyword = "astrology"), Query(keyword = "astrology"))
        Category.STANDUP -> Spec(Query(keyword = "stand-up comedy"), Query(keyword = "stand-up comedy"))
        Category.MOODS -> Spec(null, null) // filas propias (ver moodRows)
        Category.HORROR -> Spec(Query("27"), Query(keyword = "horror")) // TMDB no tiene "terror" en series
        Category.SPANISH -> Spec(Query(country = "ES"), Query(country = "ES"))
        Category.ADVENTURE -> Spec(Query("12"), Query("10759"))
        Category.MYSTERY -> Spec(Query("9648"), Query("9648"))
        Category.KIDS -> Spec(Query("16,10751"), Query("10762"))
        Category.KOREAN -> Spec(Query(country = "KR"), Query(country = "KR"))
        Category.TRUE_CRIME -> Spec(Query("99,80"), Query("99,80"))
        Category.WAR_HISTORY -> Spec(Query("10752|36"), Query("10768"))
        Category.WESTERN -> Spec(Query("37"), Query("37"))
        Category.MUSIC -> Spec(Query("10402"), null)
    }

    suspend fun categoryRows(category: Category, filter: HomeFilter): List<CatalogRow> = coroutineScope {
        val loaders: List<Pair<String, suspend () -> List<CatalogItem>>> = when (category) {
            Category.MOODS -> moodRows(filter)
            Category.ACCLAIMED -> acclaimedRows(filter)
            else -> standardRows(category, filter)
        }
        loaders.mapIndexed { index, (title, load) ->
            async {
                runCatching {
                    val items = load().filter { it.posterUrl != null }.distinctBy { it.key }
                    CatalogRow(id = "cat_${category.name}_$index", title = title, items = items)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }

    /** Películas y series con nota ≥ 7,5 y bastantes votos; no usa géneros ni fechas. */
    private fun acclaimedRows(filter: HomeFilter): List<Pair<String, suspend () -> List<CatalogItem>>> = when (filter) {
        HomeFilter.ALL -> listOf(
            tr("Las mejor valoradas", "Top rated") to suspend {
                mix(movies(Query(), acclaimed(500)), series(Query(), acclaimed(150)))
            },
            tr("Películas aclamadas", "Acclaimed movies") to suspend { movies(Query(), acclaimed(500), page = 2) },
            tr("Series aclamadas", "Acclaimed TV shows") to suspend { series(Query(), acclaimed(150), page = 2) },
            tr("Más joyas por descubrir", "More gems to discover") to suspend {
                mix(movies(Query(), acclaimed(500), page = 3), series(Query(), acclaimed(150), page = 3))
            },
        )
        HomeFilter.MOVIES -> listOf(
            tr("Películas aclamadas", "Acclaimed movies") to suspend { movies(Query(), acclaimed(500)) },
            tr("Más películas aclamadas", "More acclaimed movies") to suspend { movies(Query(), acclaimed(500), page = 2) },
            tr("Joyas por descubrir", "Gems to discover") to suspend { movies(Query(), acclaimed(500), page = 3) },
        )
        HomeFilter.TV -> listOf(
            tr("Series aclamadas", "Acclaimed TV shows") to suspend { series(Query(), acclaimed(150)) },
            tr("Más series aclamadas", "More acclaimed TV shows") to suspend { series(Query(), acclaimed(150), page = 2) },
            tr("Joyas por descubrir", "Gems to discover") to suspend { series(Query(), acclaimed(150), page = 3) },
        )
    }

    private fun standardRows(category: Category, filter: HomeFilter): List<Pair<String, suspend () -> List<CatalogItem>>> {
        val s = spec(category)
        val movie = s.movie.takeIf { filter != HomeFilter.TV }
        val tv = s.tv.takeIf { filter != HomeFilter.MOVIES }
        val rows = ArrayList<Pair<String, suspend () -> List<CatalogItem>>>()
        val today = isoDate(Date())
        if (movie != null && tv != null) {
            rows += tr("Lo más popular", "Most popular") to suspend { mix(movies(movie, POPULAR), series(tv, POPULAR)) }
            rows += tr("Películas", "Movies") to suspend { movies(movie, POPULAR, page = 2) }
            rows += tr("Series", "TV shows") to suspend { series(tv, POPULAR, page = 2) }
            rows += tr("Las mejor valoradas", "Top rated") to suspend { mix(movies(movie, topRated(500)), series(tv, topRated(150))) }
            rows += tr("Novedades", "New releases") to suspend { mix(movies(movie, newest(today, movie = true)), series(tv, newest(today, movie = false))) }
            rows += tr("Más para descubrir", "More to discover") to suspend { mix(movies(movie, POPULAR, page = 3), series(tv, POPULAR, page = 3)) }
        } else if (movie != null) {
            rows += tr("Lo más popular", "Most popular") to suspend { movies(movie, POPULAR) }
            rows += tr("Las mejor valoradas", "Top rated") to suspend { movies(movie, topRated(500)) }
            rows += tr("Novedades", "New releases") to suspend { movies(movie, newest(today, movie = true)) }
            rows += tr("Más populares", "Most popular") to suspend { movies(movie, POPULAR, page = 2) }
            rows += tr("Más para descubrir", "More to discover") to suspend { movies(movie, POPULAR, page = 3) }
        } else if (tv != null) {
            rows += tr("Lo más popular", "Most popular") to suspend { series(tv, POPULAR) }
            rows += tr("Las mejor valoradas", "Top rated") to suspend { series(tv, topRated(150)) }
            rows += tr("Novedades", "New releases") to suspend { series(tv, newest(today, movie = false)) }
            rows += tr("Más populares", "Most popular") to suspend { series(tv, POPULAR, page = 2) }
            rows += tr("Más para descubrir", "More to discover") to suspend { series(tv, POPULAR, page = 3) }
        }
        return rows
    }

    /** "Estados de ánimo": una fila por estado. Géneros separados por | = cualquiera de ellos. */
    private fun moodRows(filter: HomeFilter): List<Pair<String, suspend () -> List<CatalogItem>>> {
        val moods = listOf(
            Triple(tr("Para reír", "For a laugh"), Query("35"), Query("35")),
            Triple(tr("Para emocionarse", "Feel the emotion"), Query("18,10749"), Query("18")),
            Triple(tr("Con el corazón en un puño", "On the edge of your seat"), Query("53"), Query("9648|80")),
            Triple(tr("Para pasar miedo", "For a scare"), Query("27"), Query("9648,10765")),
            Triple(tr("Para desconectar", "To unwind"), Query("16|10751"), Query("16|10751")),
            Triple(tr("Para soñar despierto", "To daydream"), Query("14|12"), Query("10765")),
            Triple(tr("Para pensar", "Food for thought"), Query("99|36"), Query("99")),
        )
        return moods.map { (title, movie, tv) ->
            title to suspend {
                when (filter) {
                    HomeFilter.MOVIES -> movies(movie, POPULAR)
                    HomeFilter.TV -> series(tv, POPULAR)
                    HomeFilter.ALL -> mix(movies(movie, POPULAR), series(tv, POPULAR))
                }
            }
        }
    }

    private suspend fun movies(q: Query, extra: Map<String, String>, page: Int = 1): List<CatalogItem> {
        val p = params(q, extra, page) ?: return emptyList()
        return tmdb.discoverMovieQuery(key, p).results.map { it.toItem() }
    }

    private suspend fun series(q: Query, extra: Map<String, String>, page: Int = 1): List<CatalogItem> {
        val p = params(q, extra, page) ?: return emptyList()
        return tmdb.discoverTvQuery(key, p).results.map { it.toItem() }
    }

    private suspend fun params(q: Query, extra: Map<String, String>, page: Int): Map<String, String>? {
        val p = HashMap<String, String>()
        p["include_adult"] = "false"
        p["page"] = page.toString()
        q.genres?.let { p["with_genres"] = it }
        q.language?.let { p["with_original_language"] = it }
        q.country?.let { p["with_origin_country"] = it }
        q.keyword?.let { p["with_keywords"] = (keywordId(it) ?: return null).toString() }
        p.putAll(extra)
        return p
    }

    /** Id de palabra clave de TMDB por su nombre ("halloween" → 3335). Se busca una vez y se recuerda. */
    private suspend fun keywordId(name: String): Int? {
        keywordIds[name]?.let { return it }
        val results = tmdb.searchKeyword(key, name).results
        val id = (results.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: results.firstOrNull())?.id ?: return null
        keywordIds[name] = id
        return id
    }

    /** Intercala películas y series: P, S, P, S… */
    private fun mix(a: List<CatalogItem>, b: List<CatalogItem>): List<CatalogItem> {
        val out = ArrayList<CatalogItem>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private fun topRated(minVotes: Int) = mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to minVotes.toString())

    private fun acclaimed(minVotes: Int) = topRated(minVotes) + ("vote_average.gte" to "7.5")

    private fun newest(today: String, movie: Boolean) = if (movie) {
        mapOf("sort_by" to "primary_release_date.desc", "primary_release_date.lte" to today, "vote_count.gte" to "20")
    } else {
        mapOf("sort_by" to "first_air_date.desc", "first_air_date.lte" to today, "vote_count.gte" to "10")
    }

    // =====================================================================================
    // Utilidades
    // =====================================================================================

    private fun TmdbMovieDto.toItem() = CatalogItem(
        id = id.toString(),
        imdbId = null,
        type = MediaType.MOVIE,
        title = title,
        overview = overview,
        posterUrl = poster_path?.let { "$IMG/w342$it" },
        backdropUrl = backdrop_path?.let { "$IMG/w1280$it" },
        year = release_date?.take(4)?.toIntOrNull(),
        rating = vote_average.toFloat(),
    )

    private fun TmdbTvDto.toItem() = CatalogItem(
        id = id.toString(),
        imdbId = null,
        type = MediaType.TV,
        title = name,
        overview = overview,
        posterUrl = poster_path?.let { "$IMG/w342$it" },
        backdropUrl = backdrop_path?.let { "$IMG/w1280$it" },
        year = first_air_date?.take(4)?.toIntOrNull(),
        rating = vote_average.toFloat(),
    )

    private fun pickLogo(images: TmdbImagesDto?): String? {
        val logos = images?.logos.orEmpty().filter { it.file_path.isNotBlank() && !it.file_path.endsWith(".svg") }
        val best = logos.sortedWith(
            compareBy<com.shieldstream.tv.network.dto.TmdbImageDto> {
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

    /** Edad recomendada: la del país del idioma (España / EE. UU.); si no hay, la del otro. Misma escala para ambos. */
    private fun movieCertification(dates: TmdbReleaseDatesDto?): String? {
        fun of(country: String) = dates?.results?.firstOrNull { it.iso_3166_1 == country }
            ?.release_dates?.map { it.certification.trim() }?.firstOrNull { it.isNotEmpty() }
        return certificationCountries().firstNotNullOfOrNull { normalizeCertification(of(it)) }
    }

    private fun tvCertification(ratings: TmdbContentRatingsDto?): String? {
        fun of(country: String) = ratings?.results?.firstOrNull { it.iso_3166_1 == country }?.rating?.trim()
        return certificationCountries().firstNotNullOfOrNull { normalizeCertification(of(it)) }
    }

    private fun certificationCountries() = if (Lang.isEnglish) listOf("US", "ES") else listOf("ES", "US")

    private fun normalizeCertification(raw: String?): String? {
        val c = raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            c == "A" || c == "TP" || c == "APTA" || c == "G" || c == "TV-G" || c == "TV-Y" || c == "ALL" -> tr("TP", "ALL")
            c.all { it.isDigit() } -> "$c+"
            c == "PG" || c == "TV-Y7" || c == "TV-PG" -> "7+"
            c == "PG-13" || c == "TV-14" -> "13+"
            c == "R" -> "16+"
            c == "NC-17" || c == "TV-MA" -> "18+"
            else -> null
        }
    }

    private fun formatRuntime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    private fun isoDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    private companion object {
        const val IMG = "https://image.tmdb.org/t/p"
        val POPULAR = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "30")
    }
}
