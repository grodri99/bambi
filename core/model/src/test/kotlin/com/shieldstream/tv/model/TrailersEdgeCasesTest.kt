package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectores de la elección del tráiler que también usa la portada (tráiler mudo). */
class TrailersEdgeCasesTest {

    private fun video(
        key: String = "abcdefghijk",
        site: String = "YouTube",
        type: String = "Trailer",
        official: Boolean = true,
        language: String? = "en",
    ) = VideoInfo(key = key, site = site, type = type, official = official, language = language)

    @Test
    fun `no candidates returns null and an empty ranking`() {
        assertNull(Trailers.pick(emptyList(), "es"))
        assertTrue(Trailers.rank(emptyList(), "es").isEmpty())
    }

    @Test
    fun `only Youtube with a key and a trailer or teaser type is usable`() {
        val videos = listOf(
            video(key = "vimeo", site = "Vimeo"),
            video(key = "  "),
            video(key = "clip", type = "Clip"),
            video(key = "featurette", type = "Featurette"),
            video(key = "behind", type = "Behind the Scenes"),
            video(key = "teaser", type = "Teaser"),
        )

        assertEquals(listOf("teaser"), Trailers.rank(videos, "es"))
    }

    @Test
    fun `site and type are compared without case`() {
        val videos = listOf(
            video(key = "trailer", site = "youtube", type = "trailer"),
            video(key = "teaser", site = "YOUTUBE", type = "Teaser"),
        )

        assertEquals(listOf("trailer", "teaser"), Trailers.rank(videos, "es"))
    }

    @Test
    fun `interface language wins, then English, then anything else`() {
        val videos = listOf(
            video(key = "french", language = "fr"),
            video(key = "english", language = "en"),
            video(key = "spanish", language = "es"),
        )

        assertEquals(listOf("spanish", "english", "french"), Trailers.rank(videos, "es"))
        // Sin inglés ni el idioma de la app, el resto empata y conserva el orden de TMDB.
        assertEquals(listOf("english", "french", "spanish"), Trailers.rank(videos, "en"))
    }

    @Test
    fun `regional and null language variants are normalised`() {
        val videos = listOf(
            video(key = "spain", language = "es-ES"),
            video(key = "unknown", language = null),
            video(key = "mexico", language = "ES_MX".replace('_', '-')),
        )

        val ranked = Trailers.rank(videos, "es-ES")

        assertEquals("spain", ranked.first())
        assertTrue(ranked.indexOf("mexico") < ranked.indexOf("unknown"))
    }

    @Test
    fun `within the same language a trailer beats a teaser`() {
        val videos = listOf(
            video(key = "teaser", type = "Teaser"),
            video(key = "trailer", type = "Trailer"),
        )

        assertEquals(listOf("trailer", "teaser"), Trailers.rank(videos, "es"))
    }

    @Test
    fun `language takes precedence over the type`() {
        val videos = listOf(
            video(key = "english-trailer", language = "en", type = "Trailer"),
            video(key = "spanish-teaser", language = "es", type = "Teaser"),
        )

        assertEquals("spanish-teaser", Trailers.pick(videos, "es"))
        assertEquals("english-trailer", Trailers.pick(videos, "en"))
    }

    @Test
    fun `official wins only when language and type are tied`() {
        val videos = listOf(
            video(key = "unofficial", official = false),
            video(key = "official", official = true),
        )

        assertEquals(listOf("official", "unofficial"), Trailers.rank(videos, "es"))
    }

    @Test
    fun `repeated keys are returned once`() {
        val videos = listOf(
            video(key = "same"),
            video(key = "same", official = false),
            video(key = "other"),
        )

        assertEquals(listOf("same", "other"), Trailers.rank(videos, "es"))
    }

    @Test
    fun `pick is always the first of rank`() {
        val videos = listOf(
            video(key = "english", language = "en"),
            video(key = "spanish", language = "es"),
            video(key = "teaser", language = "es", type = "Teaser"),
        )

        val ranked = Trailers.rank(videos, "es")

        assertEquals(ranked.first(), Trailers.pick(videos, "es"))
        assertEquals(listOf("spanish", "teaser", "english"), ranked)
    }
}
