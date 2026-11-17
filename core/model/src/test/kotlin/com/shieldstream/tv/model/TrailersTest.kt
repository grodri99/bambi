package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailersTest {
    private fun v(key: String, type: String = "Trailer", lang: String? = "en", official: Boolean = true, site: String = "YouTube") =
        VideoInfo(key, site, type, official, lang)

    @Test
    fun `prefers a trailer in the interface language`() {
        val videos = listOf(v("en1"), v("es1", lang = "es"), v("teaser", type = "Teaser", lang = "es"))
        assertEquals("es1", Trailers.pick(videos, "es"))
        assertEquals("en1", Trailers.pick(videos, "en"))
    }

    @Test
    fun `falls back to English, then any trailer, then a teaser`() {
        assertEquals("en1", Trailers.pick(listOf(v("fr", lang = "fr"), v("en1")), "es"))
        assertEquals("fr", Trailers.pick(listOf(v("fr", lang = "fr")), "es"))
        assertEquals("t", Trailers.pick(listOf(v("t", type = "Teaser"), v("clip", type = "Clip")), "es"))
    }

    @Test
    fun `official beats unofficial and only YouTube counts`() {
        assertEquals("off", Trailers.pick(listOf(v("fan", official = false), v("off")), "en"))
        assertNull(Trailers.pick(listOf(v("vimeo", site = "Vimeo"), v("featurette", type = "Featurette")), "en"))
    }
}
