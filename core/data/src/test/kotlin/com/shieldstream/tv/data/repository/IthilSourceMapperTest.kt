package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.IthilLibraryInfo
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.IthilSource
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IthilSourceMapperTest {
    private val movie = CatalogItem("424242", null, MediaType.MOVIE, "Fixture Movie", "", null, null, 2001, null)
    private val show = movie.copy(id = "515151", type = MediaType.TV, title = "Fixture Show")
    private val episode = Episode(1, 1, "Episode", "", null, null, null)

    @Test
    fun mapsExactMovieAndEpisodeSourcesWithAudioMetadata() {
        val library = FakeLibrary()

        val movies = ithilSources(library, movie, null)
        val episodes = ithilSources(library, show, episode)

        assertEquals(3, movies.size)
        assertEquals(2, episodes.size)
        assertTrue(ithilSources(library, movie.copy(id = "0"), null).isEmpty())
        assertTrue(ithilSources(library, show, episode.copy(season = 99)).isEmpty())
        val source = episodes.first()
        assertEquals(Quality.FHD_1080, source.quality)
        assertEquals(listOf("ES", "EN"), source.languages)
        assertEquals(setOf(AudioCodec.DOLBY_DIGITAL), source.audioCodecs)
        assertTrue(source.advertisesSpanishAudio)
        assertEquals(1, source.season)
        assertEquals(1, source.episode)
        assertEquals(40, source.infoHash.length)
        assertEquals("https://hoster.test/?episode0", source.resolveUrl)
    }

    @Test
    fun missingIndexAndHdrMetadataAreHandled() {
        val library = FakeLibrary().apply { ready = false }
        assertTrue(ithilSources(library, movie, null).isEmpty())

        val hdr = FakeLibrary().movie.first().copy(quality = "DV HDR10", audio = "eng,lat")
        val mapped = ithilSources(FakeLibrary().apply { movie = listOf(hdr) }, movie, null).single()
        assertEquals(HdrType.DOLBY_VISION, mapped.hdrType)
        assertEquals(listOf("EN", "LAT"), mapped.languages)
    }

    private class FakeLibrary : IthilLibraryRepository {
        var ready = true
        var movie = List(3) { source("https://hoster.test/?movie$it", "1080p", "esp") }
        var episode = List(2) { source("https://hoster.test/?episode$it", "1080p", "esp,eng") }

        override fun isReady(): Boolean = ready
        override fun movieSources(tmdb: Int): List<IthilSource> = if (ready && tmdb == 424242) movie else emptyList()
        override fun episodeSources(tmdb: Int, season: Int, episode: Int): List<IthilSource> =
            if (ready && tmdb == 515151 && season == 1 && episode == 1) this.episode else emptyList()

        override suspend fun install(downloadUrl: String, sourceSha: String, onProgress: (Float) -> Unit) = error("unused")
        override fun info(): IthilLibraryInfo? = null
        override suspend fun delete() = Unit
    }

    companion object {
        private fun source(url: String, quality: String, audio: String) =
            IthilSource(url, quality, audio, "AC3", "2026-01-01")
    }
}
