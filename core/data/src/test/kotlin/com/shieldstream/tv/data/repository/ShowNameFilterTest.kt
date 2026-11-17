package com.shieldstream.tv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowNameFilterTest {
    private val gameOfThrones = listOf("Juego de tronos", "Game of Thrones")
    private val dragonBall = listOf("Dragon Ball", "ドラゴンボール")

    @Test
    fun `releases of the requested show are kept`() {
        // Nombres reales de MejorTorrent / Wolfmax4k en Torrentio (14-nov-2026).
        listOf(
            "Juego de Tronos temporada 1 [BluRay 1080p][AC3 5.1 Castellano AC3 5.1-Ingles+Subs][ES-EN]",
            "Juego De Tronos - Temporada 1 [BluRay Rip 1080p][Cap.101_110][AC3 5.1 Castellano][www.pctnew.com]",
            "Juego de tronos Temporada 1 completa [BDremux 1080p][DTS 5.1 Castellano-DTS 5.1 Ingles+Subs][ES-EN]",
            "Juego de Tronos - Temp.1 [HDTV][Cap.101][Spanish_English]",
            "Game.of.Thrones.S01E01.1080p.BluRay.x264",
            "Game of Thrones (2011) 1 Temporada 720p (Esp.Latino)",
        ).forEach { assertTrue(it, ShowNameFilter.matches(it, gameOfThrones)) }
    }

    @Test
    fun `releases of a different show with a similar name are dropped`() {
        listOf(
            "El juego de la pirmide [HDTV 1080p][Cap.101]",
            "El juego de la piramide [HDTV 720p][Cap.101]",
            "El juego del calamar - Temporada 1 [HDTV 720p][Cap.101]",
        ).forEach { assertFalse(it, ShowNameFilter.matches(it, gameOfThrones)) }
        listOf(
            "Dragon Ball Z - Temporada 1 [DVDRip][Cap.101][Castellano]",
            "Dragon Ball Super 1x05 [HDTV 720p][Castellano]",
            "Dragon Ball GT [Cap.01][DVDRip]",
        ).forEach { assertFalse(it, ShowNameFilter.matches(it, dragonBall)) }
    }

    @Test
    fun `articles and accents do not matter`() {
        assertTrue(ShowNameFilter.matches("Simpsons - Temporada 5 [DVDRip]", listOf("Los Simpson", "The Simpsons")))
        assertTrue(ShowNameFilter.matches("La Casa De Papel AKA Money Heist S01E01 720p", listOf("Money Heist")))
        assertTrue(ShowNameFilter.matches("Lupin - Temporada 1 [HDTV][Cap.101]", listOf("Lupin", "Lupin")))
    }

    @Test
    fun `movies are matched by title too`() {
        val beast = listOf("En el corazón de la bestia", "Beast of War")
        assertTrue(ShowNameFilter.matches("En el corazon de la bestia (2026) [4k 2160p][Esp]", beast, movie = true))
        assertFalse(ShowNameFilter.matches("La bestia en la jungla (2026) [Bluray 1080p][Esp]", beast, movie = true))
        // Títulos con un número: el nombre sale cortado y aun así vale.
        assertTrue(ShowNameFilter.matches("Blade Runner 2049 (2017) [BluRay 1080p][Castellano]", listOf("Blade Runner 2049"), movie = true))
        assertTrue(ShowNameFilter.matches("1917 (2019) [BluRay 1080p][Castellano]", listOf("1917"), movie = true))
        assertFalse(ShowNameFilter.matches("Avatar 2 El sentido del agua [BluRay 1080p]", listOf("Avatar"), movie = true))
    }

    @Test
    fun `releases without a clear show name are kept`() {
        assertTrue(ShowNameFilter.matches("101 - The One Where Monica Gets a Roommate.mkv", listOf("Friends")))
        assertTrue(ShowNameFilter.matches("[Cap.101] 720p", listOf("Friends")))
    }
}
