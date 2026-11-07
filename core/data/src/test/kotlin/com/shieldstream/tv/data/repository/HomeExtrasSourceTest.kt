package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbPaginated
import com.shieldstream.tv.strings.ResourceTexts
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
        val source = HomeExtrasSource(tmdb, context, store, ResourceTexts(context))

        val rows = source.categoryRows(Category.CLASSICS, HomeFilter.MOVIES)

        assertEquals(3, rows.size)
        assertEquals(listOf("1", "2", "3"), queries.map { it["page"] }.sorted())
        queries.forEach { query ->
            assertEquals("false", query["include_adult"])
            assertEquals("popularity.desc", query["sort_by"])
            assertEquals("1999-12-31", query["primary_release_date.lte"])
            assertEquals("8.0", query["vote_average.gte"])
            assertEquals("500", query["vote_count.gte"])
        }
        coVerify(exactly = 0) { tmdb.discoverTvQuery(any(), any()) }
    }
}
