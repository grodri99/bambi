package com.shieldstream.tv.data.repository

import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.network.dto.TmdbImageDto
import com.shieldstream.tv.network.dto.TmdbImagesDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbTvDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMappersTest {

    @Test
    fun `movie and TV mappings share image URL and date rules`() {
        val movie = TmdbMovieDto(
            id = 1,
            title = "Movie",
            overview = "Overview",
            poster_path = "/poster.jpg",
            backdrop_path = "/backdrop.jpg",
            release_date = "2026-02-03",
            vote_average = 8.5,
        ).toCatalogItem()
        val tv = TmdbTvDto(
            id = 2,
            name = "Show",
            first_air_date = "2025-01-01",
        ).toCatalogItem()

        assertEquals(MediaType.MOVIE, movie.type)
        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", movie.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop.jpg", movie.backdropUrl)
        assertEquals(2026, movie.year)
        assertEquals(8.5f, movie.rating)
        assertEquals(MediaType.TV, tv.type)
        assertEquals(2025, tv.year)
        assertNull(tv.posterUrl)
    }

    @Test
    fun `search mapping rejects people and falls back to TV metadata`() {
        val show = TmdbSearchResultDto(
            id = 3,
            media_type = "tv",
            name = "Show",
            first_air_date = "2024-01-01",
        ).toCatalogItemOrNull()

        assertEquals(MediaType.TV, show?.type)
        assertEquals("Show", show?.title)
        assertEquals(2024, show?.year)
        assertNull(TmdbSearchResultDto(media_type = "person").toCatalogItemOrNull())
    }

    @Test
    fun `logo selection prefers the UI language and ignores SVG files`() {
        val images = TmdbImagesDto(
            logos = listOf(
                TmdbImageDto("/english.svg", "en", 100.0),
                TmdbImageDto("/english.png", "en", 10.0),
                TmdbImageDto("/spanish-low.png", "es", 2.0),
                TmdbImageDto("/spanish-high.png", "es", 8.0),
                TmdbImageDto("/none.png", null, 100.0),
            ),
        )

        assertEquals(
            "https://image.tmdb.org/t/p/w500/spanish-high.png",
            TmdbImages.logo(images, "es"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/english.png",
            TmdbImages.logo(images, "fr"),
        )
    }
}
