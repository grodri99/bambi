package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerPickTest {

    private fun video(
        key: String = "abcdefghijk",
        site: String = "YouTube",
        type: String = "Trailer",
        official: Boolean = true,
        language: String? = "en",
    ) = TrailerVideo(key = key, site = site, type = type, official = official, language = language)

    @Test
    fun prefersTrailerOverTeaser() {
        val videos = listOf(video(key = "teaser", type = "Teaser"), video(key = "trailer"))
        assertEquals("trailer", pickTrailer(videos, "es"))
    }

    @Test
    fun ignoresClipsFeaturettesAndOtherSites() {
        val videos = listOf(
            video(key = "clip", type = "Clip"),
            video(key = "featurette", type = "Featurette"),
            video(key = "vimeo", site = "Vimeo"),
            video(key = ""),
            video(key = "teaser", type = "Teaser"),
        )
        assertEquals("teaser", pickTrailer(videos, "es"))
    }

    @Test
    fun appLanguageWinsTiesBecausePreviewIsMuted() {
        val videos = listOf(video(key = "english", language = "en"), video(key = "spanish", language = "es-ES"))
        assertEquals("spanish", pickTrailer(videos, "es"))
        assertEquals("english", pickTrailer(videos, "en"))
    }

    @Test
    fun officialWinsWhenEverythingElseMatches() {
        val videos = listOf(video(key = "unofficial", official = false), video(key = "official", official = true))
        assertEquals("official", pickTrailer(videos, "es"))
    }

    @Test
    fun returnsNullWhenThereIsNoUsableVideo() {
        assertNull(pickTrailer(emptyList(), "es"))
        assertNull(pickTrailer(listOf(video(type = "Behind the Scenes")), "es"))
    }
}
