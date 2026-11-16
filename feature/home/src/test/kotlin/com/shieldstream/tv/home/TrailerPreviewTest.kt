package com.shieldstream.tv.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas del reproductor que no necesitan un TV: la URL del embed (muda, sin controles) y la caja 16:9
 * que recorta el vídeo como `ContentScale.Crop`.
 */
class TrailerPreviewTest {

    private val ratio = 16f / 9f

    @Test
    fun `embed autoplays muted without controls and loops the same video`() {
        assertEquals(
            "https://www.youtube-nocookie.com/embed/abc123?autoplay=1&mute=1&controls=0&modestbranding=1" +
                "&rel=0&iv_load_policy=3&playsinline=1&loop=1&playlist=abc123&disablekb=1&fs=0&cc_load_policy=0",
            embedUrl("abc123"),
        )
    }

    @Test
    fun `embed never asks for sound and hides the YouTube chrome`() {
        val url = embedUrl("xyz")
        assertTrue(url.contains("mute=1"))
        assertTrue(url.contains("loop=1&playlist=xyz"))
        assertTrue(url.contains("controls=0"))
        // Sin `enablejsapi` no hace falta cargar la API de IFrame: solo se lee el <video> por JS.
        assertFalse(url.contains("enablejsapi"))
        assertFalse(url.contains("cc_load_policy=1"))
    }

    @Test
    fun `wide billboard grows in height and crops top and bottom`() {
        val (width, height) = coverSize(1920.dp, 720.dp)
        assertEquals(1920f, width.value, 0.01f)
        assertEquals(1080f, height.value, 0.01f)
        assertCovers(1920.dp, 720.dp)
    }

    @Test
    fun `narrow billboard grows in width and crops the sides`() {
        val (width, height) = coverSize(800.dp, 900.dp)
        assertEquals(1600f, width.value, 0.01f)
        assertEquals(900f, height.value, 0.01f)
        assertCovers(800.dp, 900.dp)
    }

    /** El vídeo tiene que cubrir el hueco entero y mantener 16:9 exacto (nunca dejar bandas). */
    private fun assertCovers(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
        val (coverWidth, coverHeight) = coverSize(width, height)
        assertTrue("el vídeo deja hueco a los lados", coverWidth.value >= width.value - 0.01f)
        assertTrue("el vídeo deja hueco arriba/abajo", coverHeight.value >= height.value - 0.01f)
        assertEquals(ratio, coverWidth.value / coverHeight.value, 0.001f)
    }
}
