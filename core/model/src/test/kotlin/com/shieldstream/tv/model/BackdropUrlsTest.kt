package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackdropUrlsTest {
    private val url = "https://image.tmdb.org/t/p/w1280/backdrop.jpg"

    @Test
    fun `standard keeps the catalog size`() {
        assertEquals(url, BackdropUrls.fullScreen(url, lightweight = false, highRes = false))
    }

    @Test
    fun `high resolution requests the original image`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/backdrop.jpg",
            BackdropUrls.fullScreen(url, lightweight = false, highRes = true),
        )
    }

    @Test
    fun `lightweight wins over high resolution`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/backdrop.jpg",
            BackdropUrls.fullScreen(url, lightweight = true, highRes = true),
        )
    }

    @Test
    fun `missing url stays missing`() {
        assertNull(BackdropUrls.fullScreen(null, lightweight = false, highRes = true))
    }
}
