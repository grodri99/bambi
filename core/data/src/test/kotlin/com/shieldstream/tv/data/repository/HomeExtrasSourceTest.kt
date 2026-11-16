package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.HomeFilter
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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeExtrasSourceTest {

    @Test
    fun `classic category uses pre-2000 highly rated movie filters for every row`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val store = mockk<TokenStore>()
        val queries = mutableListOf<Map<String, String>>()
        every { store.tmdbKey } returns "tmdb-key"
        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(queries)) } returns TmdbPaginated(
            results = listOf(TmdbMovieDto(id = 1, poster_path = "/poster.jpg")),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Las elecciones de la semana se guardan en disco: sin limpiar, otro test decidiría por este.
        context.getSharedPreferences("featured", Context.MODE_PRIVATE).edit().clear().commit()

        val tmdb = mockk<TmdbApi>()
        val store = mockk<TokenStore> { every { tmdbKey } returns "tmdb-key" }
        coEvery { tmdb.trendingAllWeek("tmdb-key", 1) } returns TmdbPaginated(
            results = listOf(
                TmdbSearchResultDto(id = 1, media_type = "movie", backdrop_path = "/b1.jpg", poster_path = "/p1.jpg"),
                TmdbSearchResultDto(id = 2, media_type = "tv", backdrop_path = "/b2.jpg", poster_path = "/p2.jpg"),
            ),
        )
        coEvery { tmdb.trendingAllWeek("tmdb-key", 2) } returns TmdbPaginated()
        coEvery {
            tmdb.movieDetails(1, "tmdb-key", append = "images,release_dates,videos")
        } returns TmdbMovieDetailDto(
            id = 1,
            title = "Movie",
            backdrop_path = "/b1.jpg",
            videos = TmdbVideosDto(listOf(TmdbVideoDto(key = "movie-trailer", site = "YouTube", type = "Trailer"))),
        )
        coEvery {
            tmdb.tvDetails(2, "tmdb-key", append = "images,content_ratings,external_ids,videos")
        } returns TmdbTvDetailDto(
            id = 2,
            name = "Show",
            backdrop_path = "/b2.jpg",
            videos = TmdbVideosDto(
                listOf(
                    TmdbVideoDto(key = "show-teaser", site = "YouTube", type = "Teaser"),
                    TmdbVideoDto(key = "show-trailer", site = "YouTube", type = "Trailer"),
                ),
            ),
        )
        val source = HomeExtrasSource(tmdb, context, store, mockk<Texts>(relaxed = true))

        val featured = source.featured(HomeFilter.ALL)

        assertEquals(listOf("movie-trailer", "show-trailer"), featured.map { it.trailerKey })
    }
}
