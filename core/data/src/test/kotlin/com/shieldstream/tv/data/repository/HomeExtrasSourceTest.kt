package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.dto.TmdbMovieDetailDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbPaginated
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbTvDetailDto
import com.shieldstream.tv.network.dto.TmdbVideoDto
import com.shieldstream.tv.network.dto.TmdbVideosDto
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeExtrasSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearWeeklyPicks() {
        // Las elecciones de la semana se guardan en disco: sin limpiar, un test decidiría por otro.
        context.getSharedPreferences("featured", Context.MODE_PRIVATE).edit().clear().commit()
        Lang.current = AppLanguage.ES
    }

    @After
    fun restoreLanguage() {
        Lang.current = AppLanguage.ES
    }

    @Test
    fun `classic category uses pre-2000 highly rated movie filters for every row`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val store = mockk<TokenStore>()
        val queries = mutableListOf<Map<String, String>>()
        every { store.tmdbKey } returns "tmdb-key"
        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(queries)) } returns TmdbPaginated(
            results = listOf(TmdbMovieDto(id = 1, poster_path = "/poster.jpg")),
        )
        val source = HomeExtrasSource(tmdb, context, store, mockk<Texts>(relaxed = true))

        val rows = source.categoryRows(Category.CLASSICS, HomeFilter.MOVIES)

        assertEquals(3, rows.size)
        assertEquals(listOf("1", "2", "3"), queries.map { it.getValue("page") }.sorted())
        queries.forEach { query ->
            assertEquals("false", query["include_adult"])
            assertEquals("popularity.desc", query["sort_by"])
            assertEquals("1999-12-31", query["primary_release_date.lte"])
            assertEquals("8.0", query["vote_average.gte"])
            assertEquals("500", query["vote_count.gte"])
        }
        coVerify(exactly = 0) { tmdb.discoverTvQuery(any(), any()) }
    }

    @Test
    fun `featured billboard carries the best trailer key from TMDB videos`() = runTest {
        val tmdb = mockk<TmdbApi>()
        stubTrending(tmdb, movieId = 1, tvId = 2)
        stubMovie(tmdb, id = 1, videos = videos(video("movie-trailer")))
        stubTv(
            tmdb,
            id = 2,
            videos = videos(video("show-teaser", type = "Teaser"), video("show-trailer")),
        )

        val featured = source(tmdb).featured(HomeFilter.ALL)

        assertEquals(listOf("movie-trailer", "show-trailer"), featured.map { it.trailerKey })
    }

    @Test
    fun `featured asks TMDB for videos on movie and TV details`() = runTest {
        val tmdb = mockk<TmdbApi>()
        stubTrending(tmdb, movieId = 1, tvId = 2)
        stubMovie(tmdb, id = 1, videos = null)
        stubTv(tmdb, id = 2, videos = null)

        source(tmdb).featured(HomeFilter.ALL)

        coVerify(exactly = 1) {
            tmdb.movieDetails(
                1,
                "tmdb-key",
                append = "images,release_dates,videos",
                imageLanguages = any(),
                videoLanguages = any(),
            )
        }
        coVerify(exactly = 1) {
            tmdb.tvDetails(
                2,
                "tmdb-key",
                append = "images,content_ratings,external_ids,videos",
                imageLanguages = any(),
                videoLanguages = any(),
            )
        }
    }

    @Test
    fun `featured without a videos field has no trailer`() = runTest {
        val tmdb = mockk<TmdbApi>()
        stubTrending(tmdb, movieId = 1, tvId = null)
        stubMovie(tmdb, id = 1, videos = null)

        val featured = source(tmdb).featured(HomeFilter.MOVIES)

        assertNull(featured.single().trailerKey)
    }

    @Test
    fun `featured with an empty videos list has no trailer`() = runTest {
        val tmdb = mockk<TmdbApi>()
        stubTrending(tmdb, movieId = 1, tvId = null)
        stubMovie(tmdb, id = 1, videos = videos())

        assertNull(source(tmdb).featured(HomeFilter.MOVIES).single().trailerKey)
    }

    @Test
    fun `featured ignores videos that are not embeddable trailers`() = runTest {
        val tmdb = mockk<TmdbApi>()
        stubTrending(tmdb, movieId = 1, tvId = null)
        stubMovie(
            tmdb,
            id = 1,
            videos = videos(
                video("vimeo", site = "Vimeo"),
                video("clip", type = "Clip"),
                video("featurette", type = "Featurette"),
                video(""),
            ),
        )

        val featured = source(tmdb).featured(HomeFilter.MOVIES)

        assertNull(featured.single().trailerKey)
    }

    @Test
    fun `featured picks the trailer in the interface language`() = runTest {
        val tmdb = mockk<TmdbApi>()
        stubTrending(tmdb, movieId = 1, tvId = null)
        stubMovie(
            tmdb,
            id = 1,
            videos = videos(
                video("es-teaser", type = "Teaser", language = "es"),
                video("en-trailer", language = "en"),
            ),
        )
        val source = source(tmdb)

        assertEquals("es-teaser", source.featured(HomeFilter.MOVIES).single().trailerKey)

        Lang.current = AppLanguage.EN
        assertEquals("en-trailer", source.featured(HomeFilter.MOVIES).single().trailerKey)
    }

    @Test
    fun `kids featured also carries the trailer`() = runTest {
        val tmdb = mockk<TmdbApi>()
        coEvery { tmdb.discoverMovieQuery("tmdb-key", any()) } returns TmdbPaginated(
            results = listOf(TmdbMovieDto(id = 7, backdrop_path = "/kids.jpg", poster_path = "/kids-p.jpg")),
        )
        stubMovie(tmdb, id = 7, videos = videos(video("kids-trailer")))

        val featured = source(tmdb).kidsFeatured(HomeFilter.MOVIES)

        assertEquals(listOf("kids-trailer"), featured.map { it.trailerKey })
    }

    private fun source(tmdb: TmdbApi) = HomeExtrasSource(
        tmdb,
        context,
        mockk<TokenStore> { every { tmdbKey } returns "tmdb-key" },
        mockk<Texts>(relaxed = true),
    )

    private fun stubTrending(tmdb: TmdbApi, movieId: Int?, tvId: Int?) {
        val results = listOfNotNull(
            movieId?.let { TmdbSearchResultDto(id = it, media_type = "movie", backdrop_path = "/b$it.jpg", poster_path = "/p$it.jpg") },
            tvId?.let { TmdbSearchResultDto(id = it, media_type = "tv", backdrop_path = "/b$it.jpg", poster_path = "/p$it.jpg") },
        )
        coEvery { tmdb.trendingAllWeek("tmdb-key", 1) } returns TmdbPaginated(results = results)
        coEvery { tmdb.trendingAllWeek("tmdb-key", 2) } returns TmdbPaginated()
    }

    private fun stubMovie(tmdb: TmdbApi, id: Int, videos: TmdbVideosDto?) {
        coEvery {
            // Los idiomas por defecto cambian con Lang.current: se aceptan todos para probar ES y EN.
            tmdb.movieDetails(
                id,
                "tmdb-key",
                append = "images,release_dates,videos",
                imageLanguages = any(),
                videoLanguages = any(),
            )
        } returns TmdbMovieDetailDto(
            id = id,
            title = "Movie $id",
            backdrop_path = "/b$id.jpg",
            videos = videos,
        )
    }

    private fun stubTv(tmdb: TmdbApi, id: Int, videos: TmdbVideosDto?) {
        coEvery {
            tmdb.tvDetails(
                id,
                "tmdb-key",
                append = "images,content_ratings,external_ids,videos",
                imageLanguages = any(),
                videoLanguages = any(),
            )
        } returns TmdbTvDetailDto(
            id = id,
            name = "Show $id",
            backdrop_path = "/b$id.jpg",
            videos = videos,
        )
    }

    private fun video(
        key: String,
        type: String = "Trailer",
        site: String = "YouTube",
        language: String? = null,
    ) = TmdbVideoDto(key = key, site = site, type = type, iso_639_1 = language)

    private fun videos(vararg videos: TmdbVideoDto) = TmdbVideosDto(videos.toList())
}
