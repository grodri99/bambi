package com.shieldstream.tv.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TMDB sirve `videos` dentro de los detalles: el tráiler de la portada depende de que se decodifique. */
class TmdbVideosDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `movie details decode the video list with language and official flag`() {
        val dto = json.decodeFromString<TmdbMovieDetailDto>(
            """
            {"id":42,"title":"Peli","videos":{"results":[
              {"id":"v1","key":"abc123","name":"Tráiler ES","site":"YouTube","type":"Trailer","official":true,"iso_639_1":"es"},
              {"id":"v2","key":"def456","name":"Teaser","site":"YouTube","type":"Teaser","official":false}
            ]}}
            """.trimIndent(),
        )

        val videos = dto.videos?.results.orEmpty()
        assertEquals(2, videos.size)
        assertEquals("abc123", videos[0].key)
        assertEquals("YouTube", videos[0].site)
        assertEquals("Trailer", videos[0].type)
        assertTrue(videos[0].official)
        assertEquals("es", videos[0].iso_639_1)
        assertNull(videos[1].iso_639_1)
    }

    @Test
    fun `TV details decode the video list too`() {
        val dto = json.decodeFromString<TmdbTvDetailDto>(
            """
            {"id":7,"name":"Serie","videos":{"results":[
              {"key":"tv123","site":"YouTube","type":"Trailer","official":true,"iso_639_1":"en"}
            ]}}
            """.trimIndent(),
        )

        assertEquals(listOf("tv123"), dto.videos?.results?.map { it.key })
    }

    @Test
    fun `details without videos produce a null list and empty results produce an empty list`() {
        val withoutVideos = json.decodeFromString<TmdbMovieDetailDto>("""{"id":1,"title":"Sin vídeos"}""")
        val emptyVideos = json.decodeFromString<TmdbMovieDetailDto>("""{"id":2,"title":"Vacía","videos":{"results":[]}}""")

        assertNull(withoutVideos.videos)
        assertTrue(emptyVideos.videos?.results.isNullOrEmpty())
    }
}
